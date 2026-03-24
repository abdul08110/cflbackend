#!/usr/bin/env python3

import argparse
import base64
import html
import json
import os
import random
import re
import shutil
import socket
import struct
import subprocess
import sys
import tempfile
import time
import urllib.parse
import urllib.request
import zipfile
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path


WORKBOOK_DEFAULT = Path(r"C:\Users\abdul\Downloads\Store List_with_gmb.xlsx")
CHROME_CANDIDATES = [
    Path(r"C:\Program Files\Google\Chrome\Application\chrome.exe"),
    Path(r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe"),
]
NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
SEARCH_POLL_DELAY_SECONDS = 1.0
ROW_DELAY_SECONDS = 2.4
ROW_DELAY_JITTER_SECONDS = 0.7


@dataclass
class RowData:
    row_number: int
    store_id: str
    store_name: str
    city: str
    full_address: str
    gmb_name: str
    gmb_address: str
    location_url: str
    existing_locality: str
    existing_business_profile_id: str


class WebSocketClient:
    def __init__(self, ws_url: str, timeout: float = 30.0) -> None:
        parsed = urllib.parse.urlparse(ws_url)
        if parsed.scheme != "ws":
            raise ValueError(f"Only ws:// URLs are supported, got {ws_url}")
        port = parsed.port or 80
        host = parsed.hostname or "127.0.0.1"
        path = parsed.path or "/"
        if parsed.query:
            path = f"{path}?{parsed.query}"
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self.sock.settimeout(timeout)
        key = base64.b64encode(os.urandom(16)).decode("ascii")
        request = (
            f"GET {path} HTTP/1.1\r\n"
            f"Host: {host}:{port}\r\n"
            "Upgrade: websocket\r\n"
            "Connection: Upgrade\r\n"
            f"Sec-WebSocket-Key: {key}\r\n"
            "Sec-WebSocket-Version: 13\r\n"
            "\r\n"
        )
        self.sock.sendall(request.encode("ascii"))
        response = self._read_http_headers()
        if " 101 " not in response.splitlines()[0]:
            raise RuntimeError(f"WebSocket upgrade failed: {response}")

    def close(self) -> None:
        try:
            self._send_frame(b"", opcode=0x8)
        except Exception:
            pass
        try:
            self.sock.close()
        except Exception:
            pass

    def send_text(self, text: str) -> None:
        self._send_frame(text.encode("utf-8"), opcode=0x1)

    def recv_text(self) -> str:
        fragments = []
        while True:
            fin, opcode, payload = self._recv_frame()
            if opcode == 0x8:
                raise ConnectionError("WebSocket closed by peer")
            if opcode == 0x9:
                self._send_frame(payload, opcode=0xA)
                continue
            if opcode not in (0x0, 0x1):
                continue
            fragments.append(payload)
            if fin:
                return b"".join(fragments).decode("utf-8", errors="replace")

    def _read_http_headers(self) -> str:
        data = b""
        while b"\r\n\r\n" not in data:
            chunk = self.sock.recv(4096)
            if not chunk:
                break
            data += chunk
        return data.decode("utf-8", errors="replace")

    def _recv_exact(self, length: int) -> bytes:
        parts = []
        remaining = length
        while remaining > 0:
            chunk = self.sock.recv(remaining)
            if not chunk:
                raise ConnectionError("Unexpected EOF while reading WebSocket frame")
            parts.append(chunk)
            remaining -= len(chunk)
        return b"".join(parts)

    def _recv_frame(self):
        first_two = self._recv_exact(2)
        first, second = first_two[0], first_two[1]
        fin = bool(first & 0x80)
        opcode = first & 0x0F
        masked = bool(second & 0x80)
        length = second & 0x7F
        if length == 126:
            length = struct.unpack("!H", self._recv_exact(2))[0]
        elif length == 127:
            length = struct.unpack("!Q", self._recv_exact(8))[0]
        mask_key = self._recv_exact(4) if masked else None
        payload = self._recv_exact(length)
        if mask_key:
            payload = bytes(b ^ mask_key[i % 4] for i, b in enumerate(payload))
        return fin, opcode, payload

    def _send_frame(self, payload: bytes, opcode: int) -> None:
        first = 0x80 | (opcode & 0x0F)
        length = len(payload)
        if length < 126:
            header = bytes([first, 0x80 | length])
        elif length < 65536:
            header = bytes([first, 0x80 | 126]) + struct.pack("!H", length)
        else:
            header = bytes([first, 0x80 | 127]) + struct.pack("!Q", length)
        mask_key = os.urandom(4)
        masked = bytes(b ^ mask_key[i % 4] for i, b in enumerate(payload))
        self.sock.sendall(header + mask_key + masked)


class CdpClient:
    def __init__(self, ws_url: str) -> None:
        self.ws = WebSocketClient(ws_url)
        self.next_id = 1

    def close(self) -> None:
        self.ws.close()

    def call(self, method: str, params=None):
        if params is None:
            params = {}
        msg_id = self.next_id
        self.next_id += 1
        self.ws.send_text(json.dumps({"id": msg_id, "method": method, "params": params}))
        while True:
            message = json.loads(self.ws.recv_text())
            if message.get("id") == msg_id:
                if "error" in message:
                    raise RuntimeError(f"CDP error for {method}: {message['error']}")
                return message.get("result", {})

    def evaluate(self, expression: str):
        result = self.call(
            "Runtime.evaluate",
            {"expression": expression, "returnByValue": True},
        )
        remote = result.get("result", {})
        if "value" in remote:
            return remote["value"]
        return None


def find_browser_path() -> Path:
    for candidate in CHROME_CANDIDATES:
        if candidate.exists():
            return candidate
    raise FileNotFoundError("Chrome was not found in the expected locations.")


def wait_for_json_list(port: int, timeout_seconds: float = 30.0):
    deadline = time.time() + timeout_seconds
    url = f"http://127.0.0.1:{port}/json/list"
    last_error = None
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(url, timeout=5) as response:
                return json.loads(response.read().decode("utf-8"))
        except Exception as exc:
            last_error = exc
            time.sleep(0.5)
    raise RuntimeError(f"DevTools endpoint on port {port} did not come up: {last_error}")


def start_browser_debug_session(port: int):
    browser_path = find_browser_path()
    profile_dir = Path(tempfile.mkdtemp(prefix="chrome-google-data-"))
    args = [
        str(browser_path),
        f"--remote-debugging-port={port}",
        f"--user-data-dir={profile_dir}",
        "https://www.google.com",
    ]
    process = subprocess.Popen(args)
    pages = wait_for_json_list(port)
    ws_url = None
    for page in pages:
        if page.get("type") == "page" and page.get("webSocketDebuggerUrl"):
            ws_url = page["webSocketDebuggerUrl"]
            break
    if not ws_url:
        raise RuntimeError("Could not find a debuggable page target in Chrome.")
    return process, profile_dir, ws_url


def shutdown_browser(process: subprocess.Popen, profile_dir: Path) -> None:
    try:
        process.terminate()
        process.wait(timeout=10)
    except Exception:
        pass
    try:
        shutil.rmtree(profile_dir, ignore_errors=True)
    except Exception:
        pass


def http_get_text(url: str, referer: str | None = None) -> str:
    headers = {"User-Agent": "Mozilla/5.0"}
    if referer:
        headers["Referer"] = referer
    request = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read().decode("utf-8", errors="replace")


def fetch_preview_place(location_url: str):
    page_html = http_get_text(location_url)
    match = re.search(
        r'<link href="([^"]+)" as="fetch" crossorigin="" rel="preload">',
        page_html,
    )
    if not match:
        raise RuntimeError(f"Could not find preview-place preload link for {location_url}")
    preview_url = urllib.parse.urljoin("https://www.google.com", html.unescape(match.group(1)))
    preview_text = http_get_text(preview_url, referer="https://www.google.com/maps")
    if preview_text.startswith(")]}'"):
        preview_text = preview_text[5:]
    return json.loads(preview_text)


def safe_get(value, *path):
    current = value
    for key in path:
        if isinstance(current, list) and isinstance(key, int) and 0 <= key < len(current):
            current = current[key]
        else:
            return None
    return current


def extract_locality(preview_data) -> str:
    candidates = [
        safe_get(preview_data, 6, 14),
        safe_get(preview_data, 6, 82, 0),
        safe_get(preview_data, 6, 183, 1, 0),
    ]
    for candidate in candidates:
        if isinstance(candidate, str) and candidate.strip():
            return candidate.strip()
    return ""


def extract_rating_from_preview(preview_data) -> str:
    rating = safe_get(preview_data, 6, 4, 7)
    if rating is None:
        return ""
    return str(rating)


def normalize_name(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", " ", text.lower()).strip()


def score_name_match(target_name: str, candidate_name: str) -> int:
    target = normalize_name(target_name)
    candidate = normalize_name(candidate_name)
    if not target or not candidate:
        return -10**6
    if target == candidate:
        return 1000

    score = 0
    if target in candidate:
        score += 700 - max(0, len(candidate) - len(target))
    if candidate in target:
        score += 500 - max(0, len(target) - len(candidate))

    target_tokens = target.split()
    candidate_tokens = set(candidate.split())
    for token in target_tokens:
        if token in candidate_tokens:
            score += 25
    return score


def navigate_search_and_get_page(cdp: CdpClient, query: str):
    search_url = "https://www.google.com/search?hl=en&gl=in&q=" + urllib.parse.quote_plus(query)
    cdp.call("Page.navigate", {"url": search_url})
    last_text = ""
    last_html = ""
    for _ in range(12):
        time.sleep(SEARCH_POLL_DELAY_SECONDS)
        text = cdp.evaluate("document.body ? document.body.innerText : ''") or ""
        page_html = cdp.evaluate("document.documentElement ? document.documentElement.outerHTML : ''") or ""
        if text:
            last_text = text
        if page_html:
            last_html = page_html
        lowered = text.lower()
        if "unusual traffic" in lowered:
            raise RuntimeError("Google search started returning unusual-traffic protection.")
        if "google reviews" in lowered or "/local/business/" in page_html:
            return text, page_html
        if "did not match any documents" in lowered:
            return text, page_html
    return last_text, last_html


def extract_rating_and_review_count(search_text: str, target_name: str):
    pattern = re.compile(r"(\d\.\d)\s*([\d,]+)\s+Google reviews?", re.IGNORECASE)
    matches = list(pattern.finditer(search_text))
    if not matches:
        return "", ""

    target_name_lower = target_name.lower().strip()
    best_match = None
    best_score = None

    for match in matches:
        before = search_text[max(0, match.start() - 900): match.start()].lower()
        after = search_text[match.end(): match.end() + 1400].lower()
        score = 0
        if target_name_lower and target_name_lower in before:
            score += 5
            score -= len(before) - before.rfind(target_name_lower)
        if "address:" in after:
            score += 4
        if "website" in after and "directions" in after:
            score += 2
        if "review from a google user" in before:
            score -= 8
        if "show all" in before:
            score -= 1
        if best_match is None or score > best_score:
            best_match = match
            best_score = score

    if best_match is None:
        best_match = matches[-1]

    rating = best_match.group(1)
    review_count = best_match.group(2).replace(",", "")
    return rating, review_count


def extract_business_profile_id(search_html: str, target_name: str):
    pattern = re.compile(
        r'\["0x[0-9a-f]+:0x[0-9a-f]+","\d+",null,null,null,"(\d+)"\],\["([^"]+)"',
        re.IGNORECASE,
    )
    best_id = ""
    best_score = None
    for match in pattern.finditer(search_html):
        business_profile_id = match.group(1)
        candidate_name = html.unescape(match.group(2))
        score = score_name_match(target_name, candidate_name)
        if best_score is None or score > best_score:
            best_score = score
            best_id = business_profile_id

    if best_id:
        return best_id

    ids = re.findall(r"/local/business/(\d+)/", search_html)
    if ids:
        counts = {}
        for business_profile_id in ids:
            counts[business_profile_id] = counts.get(business_profile_id, 0) + 1
        return max(counts.items(), key=lambda item: item[1])[0]
    return ""


def load_workbook_entries(path: Path):
    with zipfile.ZipFile(path, "r") as workbook_zip:
        return {name: workbook_zip.read(name) for name in workbook_zip.namelist()}


def resolve_sheet_target(entries, sheet_name: str) -> str:
    workbook = ET.fromstring(entries["xl/workbook.xml"])
    relationships = ET.fromstring(entries["xl/_rels/workbook.xml.rels"])
    rel_map = {rel.attrib["Id"]: rel.attrib["Target"].lstrip("/") for rel in relationships}
    sheets_node = workbook.find(f"{{{NS_MAIN}}}sheets")
    for sheet in sheets_node:
        if sheet.attrib.get("name") == sheet_name:
            rel_id = sheet.attrib.get(f"{{{NS_REL}}}id")
            target = rel_map[rel_id]
            if not target.startswith("xl/"):
                target = "xl/" + target
            return target
    raise KeyError(f"Sheet {sheet_name} was not found in the workbook.")


def cell_value(cell):
    if cell is None:
        return ""
    cell_type = cell.attrib.get("t")
    if cell_type == "inlineStr":
        texts = [node.text or "" for node in cell.iter(f"{{{NS_MAIN}}}t")]
        return "".join(texts)
    value = cell.find(f"{{{NS_MAIN}}}v")
    return value.text if value is not None else ""


def column_to_number(column_name: str) -> int:
    value = 0
    for char in column_name:
        value = value * 26 + (ord(char.upper()) - 64)
    return value


def number_to_column(number: int) -> str:
    letters = []
    while number:
        number, remainder = divmod(number - 1, 26)
        letters.append(chr(65 + remainder))
    return "".join(reversed(letters))


def row_cell_map(row_element):
    cells = {}
    for cell in row_element.findall(f"{{{NS_MAIN}}}c"):
        ref = cell.attrib.get("r", "")
        match = re.match(r"([A-Z]+)\d+", ref)
        if match:
            cells[match.group(1)] = cell
    return cells


def read_sheet1_rows(entries):
    sheet_target = resolve_sheet_target(entries, "Sheet1")
    sheet_root = ET.fromstring(entries[sheet_target])
    sheet_data = sheet_root.find(f"{{{NS_MAIN}}}sheetData")
    rows = []
    row_lookup = {}
    for row_element in sheet_data.findall(f"{{{NS_MAIN}}}row"):
        row_number = int(row_element.attrib["r"])
        row_lookup[row_number] = row_element
        if row_number == 1:
            continue
        cells = row_cell_map(row_element)
        rows.append(
            RowData(
                row_number=row_number,
                store_id=cell_value(cells.get("A")),
                store_name=cell_value(cells.get("B")),
                city=cell_value(cells.get("C")),
                full_address=cell_value(cells.get("E")),
                gmb_name=cell_value(cells.get("L")),
                gmb_address=cell_value(cells.get("M")),
                location_url=cell_value(cells.get("P")),
                existing_locality=cell_value(cells.get("D")),
                existing_business_profile_id=cell_value(cells.get("K")),
            )
        )
    return sheet_target, sheet_root, row_lookup, rows


def set_inline_string(row_element, column_name: str, row_number: int, text_value: str) -> None:
    ref = f"{column_name}{row_number}"
    cells = row_cell_map(row_element)
    cell = cells.get(column_name)
    if cell is None:
        cell = ET.Element(f"{{{NS_MAIN}}}c", {"r": ref, "t": "inlineStr"})
        target_column = column_to_number(column_name)
        inserted = False
        children = list(row_element)
        for index, existing in enumerate(children):
            existing_ref = existing.attrib.get("r", "")
            match = re.match(r"([A-Z]+)\d+", existing_ref)
            if match and column_to_number(match.group(1)) > target_column:
                row_element.insert(index, cell)
                inserted = True
                break
        if not inserted:
            row_element.append(cell)
    cell.attrib["r"] = ref
    cell.attrib["t"] = "inlineStr"
    for child in list(cell):
        cell.remove(child)
    inline = ET.SubElement(cell, f"{{{NS_MAIN}}}is")
    text = ET.SubElement(inline, f"{{{NS_MAIN}}}t")
    text.text = text_value


def save_workbook(entries, destination: Path) -> None:
    with zipfile.ZipFile(destination, "w", zipfile.ZIP_DEFLATED) as workbook_zip:
        for name, data in entries.items():
            workbook_zip.writestr(name, data)


def backup_path_for(workbook_path: Path) -> Path:
    timestamp = time.strftime("%Y%m%d-%H%M%S")
    return workbook_path.with_name(f"{workbook_path.stem}.backup-{timestamp}{workbook_path.suffix}")


def build_query(row: RowData) -> str:
    if row.gmb_name:
        return row.gmb_name
    parts = [row.store_name, row.city, row.full_address]
    return " ".join(part for part in parts if part)


def enrich_rows(rows, cdp: CdpClient, limit: int | None = None):
    enriched = []
    for index, row in enumerate(rows, start=1):
        if limit is not None and index > limit:
            break
        print(f"[{index}/{len(rows)}] Row {row.row_number}: {row.gmb_name or row.store_name}", flush=True)

        locality = row.existing_locality
        preview_rating = ""
        if row.location_url:
            try:
                preview_data = fetch_preview_place(row.location_url)
                locality = extract_locality(preview_data) or locality
                preview_rating = extract_rating_from_preview(preview_data)
            except Exception as exc:
                print(f"  preview lookup failed: {exc}", flush=True)

        rating = ""
        review_count = ""
        business_profile_id = row.existing_business_profile_id
        query = build_query(row)
        if query:
            try:
                search_text, search_html = navigate_search_and_get_page(cdp, query)
                rating, review_count = extract_rating_and_review_count(search_text, row.gmb_name or row.store_name)
                business_profile_id = extract_business_profile_id(search_html, row.gmb_name or row.store_name)
                if (not review_count or not business_profile_id) and row.gmb_address:
                    fallback_query = f"{query} {row.gmb_address}"
                    search_text, search_html = navigate_search_and_get_page(cdp, fallback_query)
                    rating, review_count = extract_rating_and_review_count(search_text, row.gmb_name or row.store_name)
                    business_profile_id = business_profile_id or extract_business_profile_id(
                        search_html,
                        row.gmb_name or row.store_name,
                    )
            except Exception as exc:
                print(f"  search lookup failed: {exc}", flush=True)

        if not rating:
            rating = preview_rating

        print(
            f"  locality={locality or '-'} business_profile_id={business_profile_id or '-'} "
            f"rating={rating or '-'} reviews={review_count or '-'}",
            flush=True,
        )
        enriched.append(
            {
                "row_number": row.row_number,
                "locality": locality or "",
                "business_profile_id": business_profile_id or "",
                "rating": rating or "",
                "review_count": review_count or "",
            }
        )
        time.sleep(ROW_DELAY_SECONDS + random.uniform(0.0, ROW_DELAY_JITTER_SECONDS))
    return enriched


def apply_updates_to_sheet(sheet_root, row_lookup, updates):
    for update in updates:
        row_number = update["row_number"]
        row_element = row_lookup[row_number]
        set_inline_string(row_element, "D", row_number, update["locality"])
        set_inline_string(row_element, "K", row_number, update["business_profile_id"])
        set_inline_string(row_element, "N", row_number, update["rating"])
        set_inline_string(row_element, "O", row_number, update["review_count"])


def verify_rows(entries, row_numbers):
    sheet_target = resolve_sheet_target(entries, "Sheet1")
    sheet_root = ET.fromstring(entries[sheet_target])
    sheet_data = sheet_root.find(f"{{{NS_MAIN}}}sheetData")
    verification = []
    wanted = set(row_numbers)
    for row_element in sheet_data.findall(f"{{{NS_MAIN}}}row"):
        row_number = int(row_element.attrib["r"])
        if row_number not in wanted:
            continue
        cells = row_cell_map(row_element)
        verification.append(
            {
                "row": row_number,
                "store": cell_value(cells.get("L")) or cell_value(cells.get("B")),
                "locality": cell_value(cells.get("D")),
                "business_profile_id": cell_value(cells.get("K")),
                "rating": cell_value(cells.get("N")),
                "review_count": cell_value(cells.get("O")),
            }
        )
    return verification


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--workbook", type=Path, default=WORKBOOK_DEFAULT)
    parser.add_argument("--output", type=Path, default=None)
    parser.add_argument("--limit", type=int, default=None)
    parser.add_argument("--port", type=int, default=9223)
    return parser.parse_args()


def progress_path_for(destination: Path) -> Path:
    return destination.with_name(f"{destination.stem}.progress.json")


def load_progress(progress_path: Path):
    if not progress_path.exists():
        return {}
    with progress_path.open("r", encoding="utf-8") as handle:
        data = json.load(handle)
    return {int(key): value for key, value in data.items()}


def save_progress(progress_path: Path, progress: dict) -> None:
    serializable = {str(key): value for key, value in sorted(progress.items())}
    with progress_path.open("w", encoding="utf-8") as handle:
        json.dump(serializable, handle, ensure_ascii=False, indent=2)


def updated_copy_path_for(workbook_path: Path) -> Path:
    timestamp = time.strftime("%Y%m%d-%H%M%S")
    return workbook_path.with_name(f"{workbook_path.stem}.updated-{timestamp}{workbook_path.suffix}")


def progress_entry_is_current(saved: dict) -> bool:
    required_keys = {"row_number", "locality", "business_profile_id", "rating", "review_count"}
    return required_keys.issubset(saved)


def create_search_session(port: int):
    browser_process, browser_profile, ws_url = start_browser_debug_session(port)
    cdp = CdpClient(ws_url)
    cdp.call("Page.enable", {})
    cdp.call("Runtime.enable", {})
    return browser_process, browser_profile, cdp


def enrich_single_row(row: RowData, cdp: CdpClient):
    locality = row.existing_locality
    preview_rating = ""
    if row.location_url:
        try:
            preview_data = fetch_preview_place(row.location_url)
            locality = extract_locality(preview_data) or locality
            preview_rating = extract_rating_from_preview(preview_data)
        except Exception as exc:
            print(f"  preview lookup failed: {exc}", flush=True)

    rating = ""
    review_count = ""
    business_profile_id = row.existing_business_profile_id
    query = build_query(row)
    if query:
        search_text, search_html = navigate_search_and_get_page(cdp, query)
        rating, review_count = extract_rating_and_review_count(search_text, row.gmb_name or row.store_name)
        business_profile_id = extract_business_profile_id(search_html, row.gmb_name or row.store_name)
        if (not review_count or not business_profile_id) and row.gmb_address:
            fallback_query = f"{query} {row.gmb_address}"
            search_text, search_html = navigate_search_and_get_page(cdp, fallback_query)
            rating, review_count = extract_rating_and_review_count(search_text, row.gmb_name or row.store_name)
            business_profile_id = business_profile_id or extract_business_profile_id(
                search_html,
                row.gmb_name or row.store_name,
            )

    if not rating:
        rating = preview_rating

    return {
        "row_number": row.row_number,
        "locality": locality or "",
        "business_profile_id": business_profile_id or "",
        "rating": rating or "",
        "review_count": review_count or "",
    }


def main():
    args = parse_args()
    workbook_path = args.workbook
    output_path = args.output or workbook_path
    if not workbook_path.exists():
        raise FileNotFoundError(f"Workbook was not found: {workbook_path}")

    entries = load_workbook_entries(workbook_path)
    sheet_target, sheet_root, row_lookup, rows = read_sheet1_rows(entries)
    print(f"Loaded {len(rows)} store rows from {workbook_path}")

    progress_path = progress_path_for(output_path)
    progress = load_progress(progress_path)
    if progress:
        print(f"Loaded existing progress for {len(progress)} rows from {progress_path}")

    browser_process = None
    browser_profile = None
    cdp = None
    restart_count = 0
    cooldown_seconds = 120
    try:
        browser_process, browser_profile, cdp = create_search_session(args.port)
        for index, row in enumerate(rows, start=1):
            if args.limit is not None and index > args.limit:
                break
            saved = progress.get(row.row_number)
            if saved and progress_entry_is_current(saved):
                saved = progress[row.row_number]
                print(
                    f"[{index}/{len(rows)}] Row {row.row_number}: "
                    f"already captured locality={saved.get('locality') or '-'} "
                    f"business_profile_id={saved.get('business_profile_id') or '-'} "
                    f"rating={saved.get('rating') or '-'} reviews={saved.get('review_count') or '-'}",
                    flush=True,
                )
                continue

            print(f"[{index}/{len(rows)}] Row {row.row_number}: {row.gmb_name or row.store_name}", flush=True)
            while True:
                try:
                    update = enrich_single_row(row, cdp)
                    print(
                        f"  locality={update['locality'] or '-'} "
                        f"business_profile_id={update['business_profile_id'] or '-'} "
                        f"rating={update['rating'] or '-'} reviews={update['review_count'] or '-'}",
                        flush=True,
                    )
                    progress[row.row_number] = update
                    save_progress(progress_path, progress)
                    break
                except Exception as exc:
                    message = str(exc)
                    if "unusual-traffic" not in message and "unusual traffic" not in message:
                        print(f"  search lookup failed: {exc}", flush=True)
                        update = {
                            "row_number": row.row_number,
                            "locality": progress.get(row.row_number, {}).get("locality", row.existing_locality),
                            "business_profile_id": progress.get(row.row_number, {}).get(
                                "business_profile_id",
                                row.existing_business_profile_id,
                            ),
                            "rating": progress.get(row.row_number, {}).get("rating", ""),
                            "review_count": "",
                        }
                        progress[row.row_number] = update
                        save_progress(progress_path, progress)
                        break

                    restart_count += 1
                    if restart_count > 6:
                        raise RuntimeError(
                            "Google search kept returning unusual-traffic protection after several restarts."
                        ) from exc

                    print(
                        f"  search lookup hit Google unusual-traffic protection; "
                        f"cooling down for {cooldown_seconds}s and restarting Chrome",
                        flush=True,
                    )
                    if cdp is not None:
                        cdp.close()
                    if browser_process is not None and browser_profile is not None:
                        shutdown_browser(browser_process, browser_profile)
                    time.sleep(cooldown_seconds)
                    browser_process, browser_profile, cdp = create_search_session(args.port)
                    continue

            time.sleep(ROW_DELAY_SECONDS + random.uniform(0.0, ROW_DELAY_JITTER_SECONDS))
    finally:
        if cdp is not None:
            cdp.close()
        if browser_process is not None and browser_profile is not None:
            shutdown_browser(browser_process, browser_profile)

    updates = []
    for row in rows:
        if row.row_number in progress:
            updates.append(progress[row.row_number])
        elif args.limit is None:
            updates.append(
                {
                    "row_number": row.row_number,
                    "locality": "",
                    "business_profile_id": "",
                    "rating": "",
                    "review_count": "",
                }
            )

    apply_updates_to_sheet(sheet_root, row_lookup, updates)
    entries[sheet_target] = ET.tostring(sheet_root, encoding="utf-8", xml_declaration=False)

    backup_path = None
    final_output_path = output_path
    if output_path == workbook_path:
        backup_path = backup_path_for(workbook_path)
        shutil.copy2(workbook_path, backup_path)
    try:
        save_workbook(entries, output_path)
    except PermissionError:
        final_output_path = updated_copy_path_for(workbook_path)
        save_workbook(entries, final_output_path)

    verification_entries = load_workbook_entries(final_output_path)
    sample_rows = [update["row_number"] for update in updates[:5]]
    verification = verify_rows(verification_entries, sample_rows)

    if backup_path:
        print(f"Backup created at {backup_path}")
    print(f"Workbook updated at {final_output_path}")
    print(f"Progress file: {progress_path}")
    print("Verification sample:")
    for item in verification:
        print(
            f"  row={item['row']} store={item['store']} locality={item['locality']} "
            f"business_profile_id={item['business_profile_id']} "
            f"rating={item['rating']} reviews={item['review_count']}"
        )


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise

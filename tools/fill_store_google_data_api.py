#!/usr/bin/env python3

import argparse
import json
import secrets
import sys
import threading
import time
import urllib.parse
import urllib.request
import webbrowser
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

from tools.fill_store_google_data import (
    WORKBOOK_DEFAULT,
    ET,
    apply_updates_to_sheet,
    backup_path_for,
    load_workbook_entries,
    read_sheet1_rows,
    save_workbook,
    updated_copy_path_for,
    verify_rows,
)


AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
TOKEN_URL = "https://oauth2.googleapis.com/token"
ACCOUNTS_URL = "https://mybusinessaccountmanagement.googleapis.com/v1/accounts"
LOCATIONS_URL_TEMPLATE = "https://mybusiness.googleapis.com/v4/accounts/{account_id}/locations?pageSize=100"
REVIEWS_URL_TEMPLATE = "https://mybusiness.googleapis.com/v4/accounts/{account_id}/locations/{location_id}/reviews?pageSize=1"
SCOPE = "https://www.googleapis.com/auth/business.manage"


class OAuthCallbackHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        query = urllib.parse.parse_qs(parsed.query)
        self.server.oauth_code = query.get("code", [""])[0]
        self.server.oauth_state = query.get("state", [""])[0]
        self.server.oauth_error = query.get("error", [""])[0]

        if self.server.oauth_code:
            body = "Authorization received. You can return to Codex."
        elif self.server.oauth_error:
            body = f"Authorization failed: {self.server.oauth_error}"
        else:
            body = "No authorization code was provided."

        body_bytes = body.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(body_bytes)))
        self.end_headers()
        self.wfile.write(body_bytes)

    def log_message(self, format, *args):
        return


def exchange_code_for_tokens(client_id: str, client_secret: str, redirect_uri: str, code: str):
    payload = urllib.parse.urlencode(
        {
            "client_id": client_id,
            "client_secret": client_secret,
            "code": code,
            "grant_type": "authorization_code",
            "redirect_uri": redirect_uri,
        }
    ).encode("utf-8")
    request = urllib.request.Request(
        TOKEN_URL,
        data=payload,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read().decode("utf-8"))


def authorize(client_id: str, client_secret: str, redirect_uri: str):
    parsed_redirect = urllib.parse.urlparse(redirect_uri)
    if parsed_redirect.scheme != "http" or parsed_redirect.hostname not in {"127.0.0.1", "localhost"}:
        raise ValueError("This script currently supports only loopback redirect URIs such as http://127.0.0.1:8765/oauth2/callback")

    state = secrets.token_urlsafe(24)
    server = HTTPServer((parsed_redirect.hostname, parsed_redirect.port), OAuthCallbackHandler)
    server.oauth_code = ""
    server.oauth_state = ""
    server.oauth_error = ""

    thread = threading.Thread(target=server.handle_request, daemon=True)
    thread.start()

    auth_url = AUTH_URL + "?" + urllib.parse.urlencode(
        {
            "client_id": client_id,
            "redirect_uri": redirect_uri,
            "response_type": "code",
            "scope": SCOPE,
            "access_type": "offline",
            "prompt": "consent",
            "include_granted_scopes": "true",
            "state": state,
        }
    )

    print("Open this URL and approve access if the browser does not open automatically:")
    print(auth_url)
    try:
        webbrowser.open(auth_url, new=1, autoraise=True)
    except Exception:
        pass

    deadline = time.time() + 300
    while time.time() < deadline:
        if server.oauth_code or server.oauth_error:
            break
        time.sleep(0.25)

    server.server_close()

    if server.oauth_error:
        raise RuntimeError(f"Google OAuth failed: {server.oauth_error}")
    if not server.oauth_code:
        raise RuntimeError("Timed out waiting for the Google OAuth callback.")
    if server.oauth_state != state:
        raise RuntimeError("OAuth state verification failed.")

    return exchange_code_for_tokens(client_id, client_secret, redirect_uri, server.oauth_code)


def api_get_json(url: str, access_token: str):
    request = urllib.request.Request(
        url,
        headers={
            "Authorization": f"Bearer {access_token}",
            "Accept": "application/json",
            "User-Agent": "Codex GBP workbook updater",
        },
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.loads(response.read().decode("utf-8"))


def list_accounts(access_token: str):
    return api_get_json(ACCOUNTS_URL, access_token).get("accounts", [])


def choose_account_id(accounts, requested_account_id: str | None):
    if requested_account_id:
        return requested_account_id
    if not accounts:
        raise RuntimeError("No Business Profile accounts were returned for this Google user.")
    if len(accounts) > 1:
        print("Multiple accounts were returned; using the first one. Pass --account-id to override.", flush=True)
    account_name = accounts[0].get("name", "")
    account_id = account_name.split("/")[-1]
    if not account_id:
        raise RuntimeError(f"Could not parse account ID from account name: {account_name}")
    return account_id


def list_locations(access_token: str, account_id: str):
    locations = []
    next_page_token = ""
    while True:
        url = LOCATIONS_URL_TEMPLATE.format(account_id=account_id)
        if next_page_token:
            url += "&pageToken=" + urllib.parse.quote_plus(next_page_token)
        payload = api_get_json(url, access_token)
        locations.extend(payload.get("locations", []))
        next_page_token = payload.get("nextPageToken", "")
        if not next_page_token:
            return locations


def get_review_summary(access_token: str, account_id: str, location_id: str):
    url = REVIEWS_URL_TEMPLATE.format(account_id=account_id, location_id=location_id)
    payload = api_get_json(url, access_token)
    return str(payload.get("averageRating", "")), str(payload.get("totalReviewCount", ""))


def location_id_from_name(location_name: str) -> str:
    parts = location_name.split("/")
    return parts[-1] if parts else ""


def build_storecode_map(access_token: str, account_id: str, locations):
    results = {}
    duplicates = set()
    for index, location in enumerate(locations, start=1):
        location_name = location.get("name", "")
        location_id = location_id_from_name(location_name)
        store_code = (location.get("storeCode") or "").strip()
        title = location.get("locationName") or ""
        print(f"[{index}/{len(locations)}] storeCode={store_code or '-'} title={title or '-'}", flush=True)

        if not location_id:
            print("  skipped: missing location ID", flush=True)
            continue

        rating, review_count = get_review_summary(access_token, account_id, location_id)
        record = {
            "business_profile_id": location_id,
            "rating": rating,
            "review_count": review_count,
            "title": title,
        }
        if store_code:
            if store_code in results:
                duplicates.add(store_code)
            results[store_code] = record

        print(
            f"  business_profile_id={location_id} rating={rating or '-'} reviews={review_count or '-'}",
            flush=True,
        )

    if duplicates:
        print(f"Duplicate store codes returned by the API: {sorted(duplicates)}", flush=True)
    return results


def apply_api_updates(workbook_path: Path, output_path: Path, storecode_map: dict):
    entries = load_workbook_entries(workbook_path)
    sheet_target, sheet_root, row_lookup, rows = read_sheet1_rows(entries)

    updates = []
    matched = 0
    for row in rows:
        api_row = storecode_map.get(row.store_id)
        if api_row:
            matched += 1
            updates.append(
                {
                    "row_number": row.row_number,
                    "locality": row.existing_locality,
                    "business_profile_id": api_row["business_profile_id"],
                    "rating": api_row["rating"],
                    "review_count": api_row["review_count"],
                }
            )
        else:
            updates.append(
                {
                    "row_number": row.row_number,
                    "locality": row.existing_locality,
                    "business_profile_id": row.existing_business_profile_id,
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
        backup_path.write_bytes(workbook_path.read_bytes())

    try:
        save_workbook(entries, output_path)
    except PermissionError:
        final_output_path = updated_copy_path_for(workbook_path)
        save_workbook(entries, final_output_path)

    verification_entries = load_workbook_entries(final_output_path)
    sample_rows = [update["row_number"] for update in updates[:5]]
    verification = verify_rows(verification_entries, sample_rows)
    return matched, len(rows), final_output_path, backup_path, verification


def export_csv(csv_output: Path, storecode_map: dict):
    csv_output.parent.mkdir(parents=True, exist_ok=True)
    rows = []
    for store_code, payload in sorted(storecode_map.items()):
        rows.append(
            {
                "storeCode": store_code,
                "business_profile_id": payload["business_profile_id"],
                "rating": payload["rating"],
                "total_review_count": payload["review_count"],
                "title": payload["title"],
            }
        )

    import csv

    with csv_output.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=["storeCode", "business_profile_id", "rating", "total_review_count", "title"],
        )
        writer.writeheader()
        writer.writerows(rows)


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--client-id", required=True)
    parser.add_argument("--client-secret", required=True)
    parser.add_argument("--redirect-uri", default="http://127.0.0.1:8765/oauth2/callback")
    parser.add_argument("--account-id", default=None)
    parser.add_argument("--workbook", type=Path, default=WORKBOOK_DEFAULT)
    parser.add_argument("--output", type=Path, default=None)
    parser.add_argument("--csv-output", type=Path, default=None)
    return parser.parse_args()


def main():
    args = parse_args()
    output_path = args.output or args.workbook

    if not args.workbook.exists():
        raise FileNotFoundError(f"Workbook was not found: {args.workbook}")

    tokens = authorize(args.client_id, args.client_secret, args.redirect_uri)
    access_token = tokens.get("access_token", "")
    if not access_token:
        raise RuntimeError("Google OAuth completed but no access token was returned.")

    accounts = list_accounts(access_token)
    account_id = choose_account_id(accounts, args.account_id)
    print(f"Using account_id={account_id}", flush=True)

    locations = list_locations(access_token, account_id)
    print(f"Fetched {len(locations)} locations", flush=True)

    storecode_map = build_storecode_map(access_token, account_id, locations)
    print(f"Locations with storeCode={len(storecode_map)}", flush=True)

    if args.csv_output:
        export_csv(args.csv_output, storecode_map)
        print(f"CSV exported to {args.csv_output}", flush=True)

    matched, total_rows, final_output_path, backup_path, verification = apply_api_updates(
        args.workbook,
        output_path,
        storecode_map,
    )

    if backup_path:
        print(f"Backup created at {backup_path}", flush=True)
    print(f"Matched {matched} of {total_rows} workbook rows by store code", flush=True)
    print(f"Workbook updated at {final_output_path}", flush=True)
    print("Verification sample:", flush=True)
    for item in verification:
        print(
            f"  row={item['row']} store={item['store']} business_profile_id={item['business_profile_id']} "
            f"rating={item['rating']} reviews={item['review_count']}",
            flush=True,
        )


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise

import 'dart:convert';

import 'package:dio/dio.dart';

Map<String, dynamic> unwrapMap(dynamic raw) {
  if (raw is Map<String, dynamic>) {
    if (raw['data'] is Map<String, dynamic>) return raw['data'] as Map<String, dynamic>;
    if (raw['data'] is Map) return Map<String, dynamic>.from(raw['data'] as Map);
    return raw;
  }
  if (raw is Map) {
    final mapped = Map<String, dynamic>.from(raw);
    if (mapped['data'] is Map) return Map<String, dynamic>.from(mapped['data'] as Map);
    return mapped;
  }
  throw const FormatException('Invalid map response');
}

List<dynamic> unwrapList(dynamic raw) {
  if (raw is Map<String, dynamic>) {
    if (raw['data'] is List) return raw['data'] as List<dynamic>;
    if (raw['content'] is List) return raw['content'] as List<dynamic>;
  }
  if (raw is Map) {
    final mapped = Map<String, dynamic>.from(raw);
    if (mapped['data'] is List) return mapped['data'] as List<dynamic>;
    if (mapped['content'] is List) return mapped['content'] as List<dynamic>;
  }
  if (raw is List) return raw;
  return const [];
}

String extractErrorMessage(Object error) {
  if (error is DioException) {
    final data = _normalizeErrorPayload(error.response?.data);
    final nestedMessage = _extractNestedMessage(data);
    if (nestedMessage != null && nestedMessage.isNotEmpty) {
      return nestedMessage;
    }
    return error.message ?? 'Something went wrong';
  }
  return error.toString().replaceFirst('Exception: ', '');
}

dynamic _normalizeErrorPayload(dynamic data) {
  if (data is String) {
    final trimmed = data.trim();
    if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
      try {
        return jsonDecode(trimmed);
      } catch (_) {
        return data;
      }
    }
  }
  return data;
}

String? _extractNestedMessage(dynamic data) {
  if (data is Map<String, dynamic>) {
    final message = data['message'];
    if (message is String && message.trim().isNotEmpty) {
      return message.trim();
    }
    if (message is Map || message is List) {
      return _extractNestedMessage(message);
    }

    final error = data['error'];
    if (error is String && error.trim().isNotEmpty) {
      return error.trim();
    }
    if (error is Map || error is List) {
      return _extractNestedMessage(error);
    }
  }

  if (data is Map) {
    return _extractNestedMessage(Map<String, dynamic>.from(data));
  }

  if (data is List) {
    for (final item in data) {
      final message = _extractNestedMessage(item);
      if (message != null && message.isNotEmpty) {
        return message;
      }
    }
  }

  if (data is String && data.trim().isNotEmpty) {
    return data.trim();
  }

  return null;
}

String asString(dynamic value, {String fallback = ''}) {
  if (value == null) return fallback;
  final s = value.toString().trim();
  return s.isEmpty ? fallback : s;
}

String? asStringOrNull(dynamic value) {
  if (value == null) return null;
  final s = value.toString().trim();
  return s.isEmpty ? null : s;
}

int asInt(dynamic value, {int fallback = 0}) {
  if (value is int) return value;
  if (value is double) return value.toInt();
  if (value is String) return int.tryParse(value) ?? fallback;
  return fallback;
}

double asDouble(dynamic value, {double fallback = 0}) {
  if (value is double) return value;
  if (value is int) return value.toDouble();
  if (value is String) return double.tryParse(value) ?? fallback;
  return fallback;
}

bool asBool(dynamic value, {bool fallback = false}) {
  if (value is bool) return value;
  if (value is int) return value == 1;
  if (value is String) {
    final s = value.toLowerCase();
    if (s == 'true' || s == '1' || s == 'yes') return true;
    if (s == 'false' || s == '0' || s == 'no') return false;
  }
  return fallback;
}

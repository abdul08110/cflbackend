import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import '../storage/secure_storage_service.dart';

final deviceSessionServiceProvider = Provider<DeviceSessionService>((ref) {
  return DeviceSessionService(ref.watch(secureStorageServiceProvider));
});

class DeviceSessionInfo {
  const DeviceSessionInfo({
    required this.deviceId,
    required this.deviceName,
    required this.platform,
  });

  final String deviceId;
  final String? deviceName;
  final String platform;
}

class DeviceSessionService {
  DeviceSessionService(this._storage);

  final SecureStorageService _storage;
  final DeviceInfoPlugin _deviceInfo = DeviceInfoPlugin();
  final Uuid _uuid = const Uuid();

  Future<DeviceSessionInfo> getSessionInfo() async {
    final deviceId = await _readOrCreateDeviceId();
    return DeviceSessionInfo(
      deviceId: deviceId,
      deviceName: await _readDeviceName(),
      platform: _platformName(),
    );
  }

  Future<String> _readOrCreateDeviceId() async {
    final existing = await _storage.readDeviceId();
    if (existing != null && existing.isNotEmpty) {
      return existing;
    }

    final deviceId = _uuid.v4();
    await _storage.writeDeviceId(deviceId);
    return deviceId;
  }

  Future<String?> _readDeviceName() async {
    try {
      if (kIsWeb) {
        return 'Web Browser';
      }

      switch (defaultTargetPlatform) {
        case TargetPlatform.android:
          final info = await _deviceInfo.androidInfo;
          return _joinParts([info.manufacturer, info.model]);
        case TargetPlatform.iOS:
          final info = await _deviceInfo.iosInfo;
          return _joinParts([info.name, info.model]);
        default:
          return 'Mobile Device';
      }
    } catch (_) {
      return null;
    }
  }

  String _platformName() {
    if (kIsWeb) {
      return 'WEB';
    }

    switch (defaultTargetPlatform) {
      case TargetPlatform.iOS:
        return 'IOS';
      case TargetPlatform.android:
        return 'ANDROID';
      default:
        return 'ANDROID';
    }
  }

  String _joinParts(List<String?> parts) {
    final items = parts
        .whereType<String>()
        .map((part) => part.trim())
        .where((part) => part.isNotEmpty)
        .toList();

    return items.isEmpty ? 'Mobile Device' : items.join(' ');
  }
}

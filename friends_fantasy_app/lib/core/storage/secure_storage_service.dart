import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../../features/auth/domain/models/auth_models.dart';

final secureStorageServiceProvider = Provider<SecureStorageService>(
      (ref) => const SecureStorageService(),
);

class SecureStorageService {
  const SecureStorageService();

  static const FlutterSecureStorage _storage = FlutterSecureStorage();

  static const String _accessTokenKey = 'access_token';
  static const String _refreshTokenKey = 'refresh_token';
  static const String _deviceIdKey = 'device_id';

  static const String _adminAccessTokenKey = 'admin_access_token';
  static const String _adminUsernameKey = 'admin_username';

  Future<void> writeTokens(AuthTokens tokens) async {
    await _storage.write(key: _accessTokenKey, value: tokens.accessToken);
    await _storage.write(key: _refreshTokenKey, value: tokens.refreshToken);
  }

  Future<String?> readAccessToken() async {
    return _storage.read(key: _accessTokenKey);
  }

  Future<String?> readRefreshToken() async {
    return _storage.read(key: _refreshTokenKey);
  }

  Future<bool> hasTokens() async {
    final access = await readAccessToken();
    final refresh = await readRefreshToken();
    return (access?.isNotEmpty ?? false) && (refresh?.isNotEmpty ?? false);
  }

  Future<void> clearAll() async {
    await _storage.delete(key: _accessTokenKey);
    await _storage.delete(key: _refreshTokenKey);
  }

  Future<void> writeDeviceId(String deviceId) async {
    await _storage.write(key: _deviceIdKey, value: deviceId);
  }

  Future<String?> readDeviceId() async {
    return _storage.read(key: _deviceIdKey);
  }

  Future<void> writeAdminAccessToken(String token) async {
    await _storage.write(key: _adminAccessTokenKey, value: token);
  }

  Future<String?> readAdminAccessToken() async {
    return _storage.read(key: _adminAccessTokenKey);
  }

  Future<void> writeAdminUsername(String username) async {
    await _storage.write(key: _adminUsernameKey, value: username);
  }

  Future<String?> readAdminUsername() async {
    return _storage.read(key: _adminUsernameKey);
  }

  Future<void> clearAdminSession() async {
    await _storage.delete(key: _adminAccessTokenKey);
    await _storage.delete(key: _adminUsernameKey);
  }
}

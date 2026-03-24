import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/device/device_session_service.dart';
import '../../../core/network/api_client.dart';
import '../../../core/network/api_endpoints.dart';
import '../../../core/network/api_helpers.dart';
import '../../../core/notifications/push_notification_service.dart';
import '../../../core/storage/secure_storage_service.dart';
import '../domain/models/auth_models.dart';

final authRepositoryProvider = Provider<AuthRepository>((ref) {
  return AuthRepository(
    dio: ref.watch(dioProvider),
    storage: ref.watch(secureStorageServiceProvider),
    deviceSessionService: ref.watch(deviceSessionServiceProvider),
    pushNotificationService: ref.watch(pushNotificationServiceProvider),
  );
});

class AuthRepository {
  AuthRepository({
    required Dio dio,
    required SecureStorageService storage,
    required DeviceSessionService deviceSessionService,
    required PushNotificationService pushNotificationService,
  })  : _dio = dio,
        _storage = storage,
        _deviceSessionService = deviceSessionService,
        _pushNotificationService = pushNotificationService;

  final Dio _dio;
  final SecureStorageService _storage;
  final DeviceSessionService _deviceSessionService;
  final PushNotificationService _pushNotificationService;

  Future<bool> hasSession() async {
    return _storage.hasTokens();
  }

  Future<AuthSession> login({
    required String credential,
    required String password,
  }) async {
    try {
      final sessionInfo = await _deviceSessionService.getSessionInfo();
      final pushToken = _pushNotificationService.currentPushToken;
      final response = await _dio.post(
        ApiEndpoints.login,
        data: {
          'mobileOrUsername': credential,
          'password': password,
          'deviceId': sessionInfo.deviceId,
          'deviceName': sessionInfo.deviceName,
          'platform': sessionInfo.platform,
          if (pushToken != null && pushToken.isNotEmpty) 'pushToken': pushToken,
        },
        options: Options(
          extra: {'authRequired': false},
        ),
      );

      final data = unwrapMap(response.data);
      final session = AuthSession.fromJson(data);

      await _storage.writeTokens(session.tokens);
      await _pushNotificationService.syncTokenWithBackend(
        pushTokenOverride: pushToken,
      );
      return session;
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<void> sendRegisterOtp({
    required String email,
    required String mobile,
  }) async {
    try {
      await _dio.post(
        ApiEndpoints.sendOtp,
        data: {
          'mobile': mobile,
          'email': email,
          'purpose': 'REGISTER',
        },
        options: Options(
          extra: {'authRequired': false},
        ),
      );

    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<void> verifyOtp({
    required String mobile,
    required String email,
    required String otp,
  }) async {
    try {
      await _dio.post(
        ApiEndpoints.verifyOtp,
        data: {
          'mobile': mobile,
          'email': email,
          'purpose': 'REGISTER',
          'otp': otp,
        },
        options: Options(
          extra: {'authRequired': false},
        ),
      );
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<void> registerAfterOtp({
    required RegisterDraft draft,
    required String otp,
  }) async {
    await verifyOtp(
      mobile: draft.mobile,
      email: draft.email,
      otp: otp,
    );

    await register(
      draft: draft,
      otp: otp,
    );
  }

  Future<void> register({
    required RegisterDraft draft,
    required String otp,
  }) async {
    try {
      await _dio.post(
        ApiEndpoints.register,
        data: draft.toJson(otp),
        options: Options(
          extra: {'authRequired': false},
        ),
      );
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }


  Future<AppUser> me() async {
    try {
      final response = await _dio.get(ApiEndpoints.me);
      return AppUser.fromJson(unwrapMap(response.data));
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<void> logout() async {
    try {
      final refreshToken = await _storage.readRefreshToken();

      await _dio.post(
        ApiEndpoints.logout,
        data: {
          'refreshToken': refreshToken,
        },
      );
    } catch (_) {
      // ignore API logout failure
    } finally {
      await _storage.clearAll();
    }
  }

  Future<String> requestForgotPasswordOtp(String identifier) async {
    try {
      final response = await _dio.post(
        ApiEndpoints.forgotPasswordRequest,
        data: {'identifier': identifier},
        options: Options(extra: {'authRequired': false}),
      );
      final data = unwrapMap(response.data);
      final email = asString(data['email']);
      return email.isEmpty ? 'registered email' : email;
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<void> confirmForgotPassword({
    required String identifier,
    required String otp,
    required String newPassword,
    required String confirmPassword,
  }) async {
    try {
      await _dio.post(
        ApiEndpoints.forgotPasswordConfirm,
        data: {
          'identifier': identifier,
          'otp': otp,
          'newPassword': newPassword,
          'confirmPassword': confirmPassword,
        },
        options: Options(extra: {'authRequired': false}),
      );
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<void> changePassword({
    required String oldPassword,
    required String newPassword,
    required String confirmPassword,
  }) async {
    try {
      await _dio.post(
        ApiEndpoints.changePassword,
        data: {
          'oldPassword': oldPassword,
          'newPassword': newPassword,
          'confirmPassword': confirmPassword,
        },
      );
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<String> requestChangePasswordOtp() async {
    try {
      final response = await _dio.post(ApiEndpoints.changePasswordRequestOtp);
      final data = unwrapMap(response.data);
      final email = asString(data['email']);
      return email.isEmpty ? 'registered email' : email;
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<void> confirmChangePasswordWithOtp({
    required String otp,
    required String newPassword,
    required String confirmPassword,
  }) async {
    try {
      await _dio.post(
        ApiEndpoints.changePasswordConfirmOtp,
        data: {
          'otp': otp,
          'newPassword': newPassword,
          'confirmPassword': confirmPassword,
        },
      );
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }
}

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/storage/secure_storage_service.dart';
import '../../data/admin_auth_repository.dart';
import 'admin_auth_state.dart';

final adminAuthControllerProvider =
StateNotifierProvider<AdminAuthController, AdminAuthState>((ref) {
  return AdminAuthController(
    ref.watch(adminAuthRepositoryProvider),
    ref.watch(secureStorageServiceProvider),
  );
});

class AdminAuthController extends StateNotifier<AdminAuthState> {
  AdminAuthController(
      this._repository,
      this._storage,
      ) : super(const AdminAuthState()) {
    restoreSession();
  }

  final AdminAuthRepository _repository;
  final SecureStorageService _storage;

  Future<void> restoreSession() async {
    try {
      final token = await _storage.readAdminAccessToken();
      final username = await _storage.readAdminUsername();

      if (token != null && token.isNotEmpty) {
        state = state.copyWith(
          status: AdminAuthStatus.authenticated,
          accessToken: token,
          username: username,
          clearError: true,
        );
      } else {
        state = state.copyWith(
          status: AdminAuthStatus.unauthenticated,
          clearToken: true,
          clearUsername: true,
          clearError: true,
        );
      }
    } catch (_) {
      state = state.copyWith(
        status: AdminAuthStatus.unauthenticated,
        clearToken: true,
        clearUsername: true,
        clearError: true,
      );
    }
  }

  Future<bool> login({
    required String username,
    required String password,
  }) async {
    state = state.copyWith(
      isBusy: true,
      clearError: true,
    );

    try {
      final response = await _repository.login(
        username: username.trim(),
        password: password.trim(),
      );

      await _storage.writeAdminAccessToken(response.accessToken);
      await _storage.writeAdminUsername(response.username);

      state = state.copyWith(
        status: AdminAuthStatus.authenticated,
        isBusy: false,
        accessToken: response.accessToken,
        username: response.username,
        clearError: true,
      );

      return true;
    } catch (e) {
      state = state.copyWith(
        status: AdminAuthStatus.unauthenticated,
        isBusy: false,
        errorMessage: e.toString().replaceFirst('Exception: ', ''),
        clearToken: true,
        clearUsername: true,
      );
      return false;
    }
  }

  Future<void> logout() async {
    await _storage.clearAdminSession();

    state = state.copyWith(
      status: AdminAuthStatus.unauthenticated,
      isBusy: false,
      clearToken: true,
      clearUsername: true,
      clearError: true,
    );
  }
}

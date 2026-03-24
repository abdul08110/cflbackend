import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/auth_repository.dart';
import '../../domain/models/auth_models.dart';
import 'auth_state.dart';

final authControllerProvider =
StateNotifierProvider<AuthController, AuthState>((ref) {
  final controller = AuthController(ref.read(authRepositoryProvider));
  Future.microtask(controller.bootstrap);
  return controller;
});

class AuthController extends StateNotifier<AuthState> {
  AuthController(this._repository) : super(AuthState.initial());

  final AuthRepository _repository;
  bool _bootstrapped = false;

  Future<void> bootstrap() async {
    if (_bootstrapped) return;
    _bootstrapped = true;

    try {
      final hasSession = await _repository.hasSession();

      // If user has already started register flow, do not overwrite state
      if (state.pendingRegister != null) return;

      if (!hasSession) {
        state = state.copyWith(
          status: AppAuthStatus.unauthenticated,
          isBusy: false,
          clearError: true,
        );
        return;
      }

      final me = await _repository.me();

      // If register flow started while waiting, do not overwrite it
      if (state.pendingRegister != null) return;

      state = AuthState(
        status: AppAuthStatus.authenticated,
        isBusy: false,
        user: me,
      );
    } catch (_) {
      await _repository.logout();

      if (state.pendingRegister != null) return;

      state = const AuthState(
        status: AppAuthStatus.unauthenticated,
        isBusy: false,
      );
    }
  }

  Future<bool> login({
    required String credential,
    required String password,
  }) async {
    state = state.copyWith(isBusy: true, clearError: true);

    try {
      final session = await _repository.login(
        credential: credential,
        password: password,
      );

      state = AuthState(
        status: AppAuthStatus.authenticated,
        isBusy: false,
        user: session.user,
      );

      return true;
    } catch (e) {
      state = state.copyWith(
        isBusy: false,
        errorMessage: e.toString().replaceFirst('Exception: ', ''),
      );
      return false;
    }
  }

  Future<bool> sendRegisterOtp(RegisterDraft draft) async {
    state = state.copyWith(
      status: AppAuthStatus.unauthenticated,
      isBusy: true,
      clearError: true,
      pendingRegister: draft,
    );

    try {
      await _repository.sendRegisterOtp(mobile: draft.mobile, email: draft.email);

      state = state.copyWith(
        status: AppAuthStatus.unauthenticated,
        isBusy: false,
        pendingRegister: draft,
        clearError: true,
      );

      return true;
    } catch (e) {
      state = state.copyWith(
        isBusy: false,
        errorMessage: e.toString().replaceFirst('Exception: ', ''),
        pendingRegister: draft,
      );
      return false;
    }
  }

  Future<bool> verifyOtpAndRegister(String otp) async {
    final draft = state.pendingRegister;

    if (draft == null) {
      state = state.copyWith(
        errorMessage: 'Registration data missing. Please register again.',
      );
      return false;
    }

    state = state.copyWith(isBusy: true, clearError: true);

    try {
      await _repository.registerAfterOtp(
        draft: draft,
        otp: otp,
      );

      state = state.copyWith(
        status: AppAuthStatus.unauthenticated,
        isBusy: false,
        clearPendingRegister: true,
        clearError: true,
      );

      return true;
    } catch (e) {
      state = state.copyWith(
        isBusy: false,
        errorMessage: e.toString().replaceFirst('Exception: ', ''),
      );
      return false;
    }
  }

  Future<void> resendOtp() async {
    final draft = state.pendingRegister;
    if (draft == null) return;

    state = state.copyWith(isBusy: true, clearError: true);

    try {
      await _repository.sendRegisterOtp(mobile: draft.mobile, email: draft.email);

      state = state.copyWith(
        isBusy: false,
        pendingRegister: draft,
        clearError: true,
      );
    } catch (e) {
      state = state.copyWith(
        isBusy: false,
        errorMessage: e.toString().replaceFirst('Exception: ', ''),
        pendingRegister: draft,
      );
    }
  }

  Future<void> logout() async {
    state = state.copyWith(isBusy: true, clearError: true);
    await _repository.logout();

    state = const AuthState(
      status: AppAuthStatus.unauthenticated,
      isBusy: false,
    );
  }
}

import 'package:equatable/equatable.dart';

import '../../domain/models/auth_models.dart';

enum AppAuthStatus {
  unknown,
  unauthenticated,
  authenticated,
}

class AuthState extends Equatable {
  final AppAuthStatus status;
  final bool isBusy;
  final String? errorMessage;
  final AppUser? user;
  final RegisterDraft? pendingRegister;

  const AuthState({
    required this.status,
    required this.isBusy,
    this.errorMessage,
    this.user,
    this.pendingRegister,
  });

  factory AuthState.initial() {
    return const AuthState(
      status: AppAuthStatus.unknown,
      isBusy: false,
    );
  }

  AuthState copyWith({
    AppAuthStatus? status,
    bool? isBusy,
    String? errorMessage,
    AppUser? user,
    RegisterDraft? pendingRegister,
    bool clearError = false,
    bool clearPendingRegister = false,
  }) {
    return AuthState(
      status: status ?? this.status,
      isBusy: isBusy ?? this.isBusy,
      errorMessage: clearError ? null : (errorMessage ?? this.errorMessage),
      user: user ?? this.user,
      pendingRegister:
      clearPendingRegister ? null : (pendingRegister ?? this.pendingRegister),
    );
  }

  @override
  List<Object?> get props => [
    status,
    isBusy,
    errorMessage,
    user,
    pendingRegister,
  ];
}
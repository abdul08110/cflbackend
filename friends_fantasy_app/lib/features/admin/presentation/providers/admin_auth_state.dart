enum AdminAuthStatus {
  unknown,
  unauthenticated,
  authenticated,
}

class AdminAuthState {
  final AdminAuthStatus status;
  final bool isBusy;
  final String? accessToken;
  final String? username;
  final String? errorMessage;

  const AdminAuthState({
    this.status = AdminAuthStatus.unknown,
    this.isBusy = false,
    this.accessToken,
    this.username,
    this.errorMessage,
  });

  AdminAuthState copyWith({
    AdminAuthStatus? status,
    bool? isBusy,
    String? accessToken,
    String? username,
    String? errorMessage,
    bool clearError = false,
    bool clearToken = false,
    bool clearUsername = false,
  }) {
    return AdminAuthState(
      status: status ?? this.status,
      isBusy: isBusy ?? this.isBusy,
      accessToken: clearToken ? null : (accessToken ?? this.accessToken),
      username: clearUsername ? null : (username ?? this.username),
      errorMessage: clearError ? null : (errorMessage ?? this.errorMessage),
    );
  }
}
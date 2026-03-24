import 'package:equatable/equatable.dart';

Map<String, dynamic> _map(dynamic value) {
  if (value is Map<String, dynamic>) return value;
  if (value is Map) return Map<String, dynamic>.from(value);
  return <String, dynamic>{};
}

String _pickString(Map<String, dynamic> json, List<String> keys, {String fallback = ''}) {
  for (final key in keys) {
    final value = json[key];
    if (value != null && value.toString().trim().isNotEmpty) {
      return value.toString().trim();
    }
  }
  return fallback;
}

String? _pickNullableString(Map<String, dynamic> json, List<String> keys) {
  for (final key in keys) {
    final value = json[key];
    if (value != null && value.toString().trim().isNotEmpty) {
      return value.toString().trim();
    }
  }
  return null;
}

class AuthTokens extends Equatable {
  final String accessToken;
  final String refreshToken;

  const AuthTokens({required this.accessToken, required this.refreshToken});

  factory AuthTokens.fromJson(Map<String, dynamic> json) {
    final tokenSource = json['tokens'] is Map ? _map(json['tokens']) : json;
    return AuthTokens(
      accessToken: _pickString(tokenSource, ['accessToken', 'access_token', 'jwt', 'token']),
      refreshToken: _pickString(tokenSource, ['refreshToken', 'refresh_token']),
    );
  }

  @override
  List<Object?> get props => [accessToken, refreshToken];
}

class AppUser extends Equatable {
  final String id;
  final String username;
  final String fullName;
  final String mobile;
  final String? email;

  const AppUser({
    required this.id,
    required this.username,
    required this.fullName,
    required this.mobile,
    this.email,
  });

  factory AppUser.fromJson(Map<String, dynamic> json) {
    return AppUser(
      id: _pickString(json, ['id', 'userId']),
      username: _pickString(json, ['username', 'userName', 'loginId']),
      fullName: _pickString(json, ['fullName', 'name', 'displayName']),
      mobile: _pickString(json, ['mobile', 'mobileNumber', 'phone']),
      email: _pickNullableString(json, ['email']),
    );
  }

  @override
  List<Object?> get props => [id, username, fullName, mobile, email];
}

class AuthSession extends Equatable {
  final AuthTokens tokens;
  final AppUser user;

  const AuthSession({required this.tokens, required this.user});

  factory AuthSession.fromJson(Map<String, dynamic> json) {
    final userJson = json['user'] is Map
        ? _map(json['user'])
        : (json['me'] is Map ? _map(json['me']) : json);

    return AuthSession(
      tokens: AuthTokens.fromJson(json),
      user: AppUser.fromJson(userJson),
    );
  }

  @override
  List<Object?> get props => [tokens, user];
}

class RegisterDraft extends Equatable {
  final String fullName;
  final String username;
  final String mobile;
  final String email;
  final String password;

  const RegisterDraft({
    required this.fullName,
    required this.username,
    required this.mobile,
    required this.email,
    required this.password,
  });

  Map<String, dynamic> toJson(String otp) {
    return {
      'fullName': fullName,
      'username': username,
      'mobile': mobile,
      'email': email,
      'password': password,
      'otp': otp,
    };
  }

  @override
  List<Object?> get props => [fullName, username, mobile, email, password];
}

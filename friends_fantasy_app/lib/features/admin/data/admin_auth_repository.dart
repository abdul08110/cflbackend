import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_client.dart';
import '../../../core/network/api_endpoints.dart';
import '../../../core/network/api_helpers.dart';

final adminAuthRepositoryProvider = Provider<AdminAuthRepository>(
      (ref) => AdminAuthRepository(ref.watch(dioProvider)),
);

class AdminLoginResponse {
  final String accessToken;
  final String username;
  final String tokenType;
  final String adminId;

  const AdminLoginResponse({
    required this.accessToken,
    required this.username,
    required this.tokenType,
    required this.adminId,
  });

  factory AdminLoginResponse.fromJson(Map<String, dynamic> json) {
    return AdminLoginResponse(
      accessToken: asString(json['accessToken']),
      username: asString(json['username']),
      tokenType: asString(json['tokenType'], fallback: 'Bearer'),
      adminId: asString(json['adminId']),
    );
  }
}

class AdminAuthRepository {
  AdminAuthRepository(this._dio);

  final Dio _dio;

  Future<AdminLoginResponse> login({
    required String username,
    required String password,
  }) async {
    try {
      final response = await _dio.post(
        ApiEndpoints.adminLogin,
        data: {
          'username': username,
          'password': password,
        },
      );

      return AdminLoginResponse.fromJson(unwrapMap(response.data));
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }
}
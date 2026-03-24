import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:pretty_dio_logger/pretty_dio_logger.dart';

import '../../features/auth/domain/models/auth_models.dart';
import '../config/app_config.dart';
import '../storage/secure_storage_service.dart';
import 'api_endpoints.dart';
import 'api_helpers.dart';

final apiClientProvider = Provider<ApiClient>(
      (ref) => ApiClient(storage: ref.read(secureStorageServiceProvider)),
);

final dioProvider = Provider<Dio>((ref) => ref.watch(apiClientProvider).dio);

class ApiClient {
  ApiClient({required this.storage})
      : dio = Dio(
    BaseOptions(
      baseUrl: AppConfig.baseUrl,
      connectTimeout: const Duration(seconds: 20),
      receiveTimeout: const Duration(seconds: 20),
      sendTimeout: kIsWeb ? null : const Duration(seconds: 20),
      contentType: Headers.jsonContentType,
      responseType: ResponseType.json,
    ),
  ) {
    _configure();
  }

  final Dio dio;
  final SecureStorageService storage;
  Future<String?>? _refreshFuture;

  void _configure() {
    dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) async {
          final requiresAuth = options.extra['authRequired'] != false;

          if (requiresAuth) {
            final path = options.path;

            // ADMIN APIs => use admin token
            if (path.startsWith('/api/v1/admin/')) {
              final adminToken = await storage.readAdminAccessToken();
              if (adminToken != null && adminToken.isNotEmpty) {
                options.headers['Authorization'] = 'Bearer $adminToken';
              }
            } else {
              // USER APIs => use user token
              final userToken = await storage.readAccessToken();
              if (userToken != null && userToken.isNotEmpty) {
                options.headers['Authorization'] = 'Bearer $userToken';
              }
            }
          }

          handler.next(options);
        },
        onError: (error, handler) async {
          final request = error.requestOptions;
          final isUnauthorized = error.response?.statusCode == 401;
          final alreadyRetried = request.extra['retried'] == true;
          final isRefreshCall = request.path.contains(ApiEndpoints.refresh);

          // Do NOT try user refresh flow for admin APIs
          final isAdminApi = request.path.startsWith('/api/v1/admin/');

          if (isUnauthorized &&
              !alreadyRetried &&
              !isRefreshCall &&
              !isAdminApi) {
            try {
              final newAccessToken = await _refreshAccessToken();
              request.headers['Authorization'] = 'Bearer $newAccessToken';
              request.extra['retried'] = true;

              final clonedResponse = await dio.fetch(request);
              return handler.resolve(clonedResponse);
            } catch (_) {
              await storage.clearAll();
            }
          }

          handler.next(error);
        },
      ),
    );

    if (!kReleaseMode) {
      dio.interceptors.add(
        PrettyDioLogger(
          requestHeader: true,
          requestBody: true,
          responseBody: true,
          responseHeader: false,
          error: true,
          compact: true,
          maxWidth: 120,
        ),
      );
    }
  }

  Future<String> _refreshAccessToken() async {
    if (_refreshFuture != null) {
      final cached = await _refreshFuture;
      if (cached == null || cached.isEmpty) {
        throw Exception('Failed to refresh token');
      }
      return cached;
    }

    final completer = Completer<String?>();
    _refreshFuture = completer.future;

    try {
      final refreshToken = await storage.readRefreshToken();
      if (refreshToken == null || refreshToken.isEmpty) {
        throw Exception('Refresh token missing');
      }

      final refreshDio = Dio(
        BaseOptions(
          baseUrl: AppConfig.baseUrl,
          connectTimeout: const Duration(seconds: 20),
          receiveTimeout: const Duration(seconds: 20),
          sendTimeout: kIsWeb ? null : const Duration(seconds: 20),
          contentType: Headers.jsonContentType,
          responseType: ResponseType.json,
        ),
      );

      final response = await refreshDio.post(
        ApiEndpoints.refresh,
        data: {'refreshToken': refreshToken},
      );

      final data = unwrapMap(response.data);
      final tokens = AuthTokens.fromJson(data);
      await storage.writeTokens(tokens);

      completer.complete(tokens.accessToken);
      return tokens.accessToken;
    } catch (e) {
      completer.completeError(e);
      rethrow;
    } finally {
      _refreshFuture = null;
    }
  }
}

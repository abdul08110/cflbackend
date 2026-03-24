import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_client.dart';
import '../../../core/network/api_endpoints.dart';
import '../../../core/network/api_helpers.dart';
import '../../auth/presentation/providers/auth_controller.dart';
import '../domain/models/notification_models.dart';

final notificationRepositoryProvider = Provider<NotificationRepository>(
  (ref) => NotificationRepository(ref.watch(dioProvider)),
);

final myNotificationsProvider =
    FutureProvider.autoDispose<List<AppNotification>>((ref) async {
      final currentUserId = ref.watch(
        authControllerProvider.select((state) => state.user?.id),
      );
      if (currentUserId == null) {
        return const <AppNotification>[];
      }

      final link = ref.keepAlive();
      final timer = Timer(const Duration(seconds: 45), link.close);
      ref.onDispose(timer.cancel);

      return ref.watch(notificationRepositoryProvider).getMyNotifications();
    });

final notificationUnreadCountProvider = FutureProvider.autoDispose<int>(
  (ref) async {
    final currentUserId = ref.watch(
      authControllerProvider.select((state) => state.user?.id),
    );
    if (currentUserId == null) {
      return 0;
    }

    return ref.watch(notificationRepositoryProvider).getUnreadCount();
  },
);

class NotificationRepository {
  NotificationRepository(this._dio);

  final Dio _dio;

  Future<List<AppNotification>> getMyNotifications() async {
    try {
      final response = await _dio.get(ApiEndpoints.notifications);
      return unwrapList(response.data)
          .map(
            (e) => AppNotification.fromJson(Map<String, dynamic>.from(e as Map)),
          )
          .toList();
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<int> getUnreadCount() async {
    try {
      final response = await _dio.get(ApiEndpoints.notificationUnreadCount);
      final data = unwrapMap(response.data);
      return asInt(data['unreadCount']);
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<void> markAsRead(String notificationId) async {
    try {
      await _dio.post(ApiEndpoints.markNotificationRead(notificationId));
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<void> markAllAsRead() async {
    try {
      await _dio.post(ApiEndpoints.markAllNotificationsRead);
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<void> registerDeviceToken({
    required String deviceId,
    required String platform,
    String? deviceName,
    String? pushToken,
  }) async {
    try {
      await _dio.post(
        ApiEndpoints.notificationDeviceToken,
        data: {
          'deviceId': deviceId,
          'platform': platform,
          if (deviceName != null && deviceName.isNotEmpty)
            'deviceName': deviceName,
          'pushToken': pushToken,
        },
      );
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }
}

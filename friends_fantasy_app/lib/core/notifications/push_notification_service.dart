import 'dart:async';
import 'dart:convert';

import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/material.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../app/router/app_router.dart';
import '../../features/notification/data/notification_repository.dart';
import '../device/device_session_service.dart';
import '../storage/secure_storage_service.dart';
import 'app_firebase_options.dart';

final pushNotificationServiceProvider = Provider<PushNotificationService>((ref) {
  return PushNotificationService(
    ref: ref,
    notificationRepository: ref.watch(notificationRepositoryProvider),
    deviceSessionService: ref.watch(deviceSessionServiceProvider),
    storage: ref.watch(secureStorageServiceProvider),
  );
});

class PushNotificationService {
  PushNotificationService({
    required Ref ref,
    required NotificationRepository notificationRepository,
    required DeviceSessionService deviceSessionService,
    required SecureStorageService storage,
  }) : _ref = ref,
       _notificationRepository = notificationRepository,
       _deviceSessionService = deviceSessionService,
       _storage = storage;

  static const String _channelId = 'fantasy_general_notifications';
  static const String _channelName = 'Fantasy Notifications';
  static const String _channelDescription =
      'Friend requests, community invites, and fantasy updates';

  final Ref _ref;
  final NotificationRepository _notificationRepository;
  final DeviceSessionService _deviceSessionService;
  final SecureStorageService _storage;
  final FlutterLocalNotificationsPlugin _localNotifications =
      FlutterLocalNotificationsPlugin();

  bool _initialized = false;
  String? _currentPushToken;

  String? get currentPushToken => _currentPushToken;

  Future<void> initialize() async {
    if (_initialized) {
      return;
    }
    _initialized = true;

    try {
      await _initializeLocalNotifications();
    } catch (_) {
      // Local notification plugins are not always available in widget tests.
    }

    final options = AppFirebaseOptions.currentPlatform;
    if (options == null) {
      return;
    }

    if (Firebase.apps.isEmpty) {
      await Firebase.initializeApp(options: options);
    }

    await FirebaseMessaging.instance.setForegroundNotificationPresentationOptions(
      alert: true,
      badge: true,
      sound: true,
    );
    await FirebaseMessaging.instance.requestPermission(
      alert: true,
      badge: true,
      sound: true,
    );

    _currentPushToken = await FirebaseMessaging.instance.getToken();
    await syncTokenWithBackend();

    FirebaseMessaging.onMessage.listen((message) {
      _refreshNotificationProviders();
      unawaited(_showForegroundNotification(message));
    });

    FirebaseMessaging.onMessageOpenedApp.listen(_handleOpenedMessage);

    FirebaseMessaging.instance.onTokenRefresh.listen(
      (token) {
        _currentPushToken = token;
        unawaited(syncTokenWithBackend(pushTokenOverride: token));
      },
    );

    final initialMessage = await FirebaseMessaging.instance.getInitialMessage();
    if (initialMessage != null) {
      _handleOpenedMessage(initialMessage);
    }
  }

  Future<void> syncTokenWithBackend({String? pushTokenOverride}) async {
    try {
      final hasSession = await _storage.hasTokens();
      if (!hasSession) {
        return;
      }

      final sessionInfo = await _deviceSessionService.getSessionInfo();
      await _notificationRepository.registerDeviceToken(
        deviceId: sessionInfo.deviceId,
        deviceName: sessionInfo.deviceName,
        platform: sessionInfo.platform,
        pushToken: pushTokenOverride ?? _currentPushToken,
      );
      _refreshNotificationProviders();
    } catch (_) {
      // Ignore best-effort push registration failures.
    }
  }

  Future<void> _initializeLocalNotifications() async {
    const initializationSettings = InitializationSettings(
      android: AndroidInitializationSettings('@mipmap/ic_launcher'),
      iOS: DarwinInitializationSettings(),
    );

    await _localNotifications.initialize(
      initializationSettings,
      onDidReceiveNotificationResponse: (response) {
        _handlePayloadResponse(response.payload);
      },
    );

    const channel = AndroidNotificationChannel(
      _channelId,
      _channelName,
      description: _channelDescription,
      importance: Importance.high,
    );

    await _localNotifications
        .resolvePlatformSpecificImplementation<
          AndroidFlutterLocalNotificationsPlugin
        >()
        ?.createNotificationChannel(channel);
  }

  Future<void> _showForegroundNotification(RemoteMessage message) async {
    final remoteNotification = message.notification;
    final title = remoteNotification?.title ?? message.data['title'];
    final body = remoteNotification?.body ?? message.data['body'];

    if ((title == null || title.isEmpty) && (body == null || body.isEmpty)) {
      return;
    }

    await _localNotifications.show(
      _notificationIdFor(message),
      title,
      body,
      const NotificationDetails(
        android: AndroidNotificationDetails(
          _channelId,
          _channelName,
          channelDescription: _channelDescription,
          importance: Importance.high,
          priority: Priority.high,
        ),
        iOS: DarwinNotificationDetails(),
      ),
      payload: jsonEncode(message.data),
    );
  }

  void _handleOpenedMessage(RemoteMessage message) {
    _refreshNotificationProviders();
    _openRoute(message.data['route'] ?? '/notifications');
  }

  void _handlePayloadResponse(String? payload) {
    if (payload == null || payload.isEmpty) {
      return;
    }

    try {
      final decoded = jsonDecode(payload);
      if (decoded is Map<String, dynamic>) {
        _openRoute(decoded['route'] as String? ?? '/notifications');
      } else {
        _openRoute('/notifications');
      }
    } catch (_) {
      _openRoute('/notifications');
    }
  }

  void _openRoute(String route) {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final context = appNavigatorKey.currentContext;
      if (context != null) {
        GoRouter.of(context).go(route);
      }
    });
  }

  void _refreshNotificationProviders() {
    _ref.invalidate(myNotificationsProvider);
    _ref.invalidate(notificationUnreadCountProvider);
  }

  int _notificationIdFor(RemoteMessage message) {
    final messageId = message.messageId ?? DateTime.now().millisecondsSinceEpoch.toString();
    return messageId.hashCode & 0x7fffffff;
  }
}

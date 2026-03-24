import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/notifications/push_notification_service.dart';
import '../features/auth/presentation/providers/auth_controller.dart';
import '../features/auth/presentation/providers/auth_state.dart';
import '../features/contest/data/contest_repository.dart';
import '../features/notification/data/notification_repository.dart';
import '../features/room/data/room_repository.dart';
import '../features/team/data/team_repository.dart';
import 'router/app_router.dart';
import 'theme/app_theme.dart';

class FantasyApp extends ConsumerStatefulWidget {
  const FantasyApp({super.key});

  @override
  ConsumerState<FantasyApp> createState() => _FantasyAppState();
}

class _FantasyAppState extends ConsumerState<FantasyApp>
    with WidgetsBindingObserver {
  static const Duration _idleTimeout = Duration(minutes: 10);

  ProviderSubscription<AuthState>? _authSubscription;
  Timer? _idleTimer;
  DateTime? _lastInteractionAt;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);

    Future.microtask(() {
      ref.read(pushNotificationServiceProvider).initialize();
    });

    _authSubscription = ref.listenManual<AuthState>(
      authControllerProvider,
      (previous, next) {
        final previousUserId = previous?.user?.id;
        final nextUserId = next.user?.id;
        final authChanged = previous?.status != next.status;
        final userChanged = previousUserId != nextUserId;

        if (authChanged || userChanged) {
          ref.invalidate(myRoomsProvider);
          ref.invalidate(allRoomsProvider);
          ref.invalidate(incomingRoomInvitesProvider);
          ref.invalidate(roomDetailProvider);
          ref.invalidate(roomMembersProvider);
          ref.invalidate(contestDetailProvider);
          ref.invalidate(leaderboardProvider);
          ref.invalidate(myContestEntriesProvider);
          ref.invalidate(myTeamsProvider);
          ref.invalidate(myNotificationsProvider);
          ref.invalidate(notificationUnreadCountProvider);
        }

        if (next.status == AppAuthStatus.authenticated
            && (previous?.status != AppAuthStatus.authenticated || userChanged)) {
          ref.read(pushNotificationServiceProvider).syncTokenWithBackend();
          _recordActivity();
        } else if (next.status != AppAuthStatus.authenticated) {
          _cancelIdleTimer();
        }
      },
    );
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _authSubscription?.close();
    _cancelIdleTimer();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    final authState = ref.read(authControllerProvider);

    if (state == AppLifecycleState.resumed) {
      if (authState.status == AppAuthStatus.authenticated) {
        if (_lastInteractionAt != null) {
          final idleDuration = DateTime.now().difference(_lastInteractionAt!);
          if (idleDuration >= _idleTimeout) {
            unawaited(ref.read(authControllerProvider.notifier).logout());
            return;
          }
          _startIdleTimer(_idleTimeout - idleDuration);
        } else {
          _recordActivity();
        }
      }

      ref.invalidate(myNotificationsProvider);
      ref.invalidate(notificationUnreadCountProvider);
      ref.read(pushNotificationServiceProvider).syncTokenWithBackend();
      return;
    }

    if (state == AppLifecycleState.inactive ||
        state == AppLifecycleState.hidden ||
        state == AppLifecycleState.paused) {
      _cancelIdleTimer();
    }
  }

  void _recordActivity() {
    if (ref.read(authControllerProvider).status != AppAuthStatus.authenticated) {
      return;
    }

    _lastInteractionAt = DateTime.now();
    _startIdleTimer(_idleTimeout);
  }

  void _startIdleTimer(Duration duration) {
    _cancelIdleTimer();
    _idleTimer = Timer(duration, () {
      if (!mounted) return;
      if (ref.read(authControllerProvider).status != AppAuthStatus.authenticated) {
        return;
      }
      unawaited(ref.read(authControllerProvider.notifier).logout());
    });
  }

  void _cancelIdleTimer() {
    _idleTimer?.cancel();
    _idleTimer = null;
  }

  @override
  Widget build(BuildContext context) {
    final router = ref.watch(appRouterProvider);

    return MaterialApp.router(
      title: 'Community Fantasy League',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light,
      darkTheme: AppTheme.dark,
      themeMode: ThemeMode.dark,
      routerConfig: router,
      builder: (context, child) => Listener(
        behavior: HitTestBehavior.translucent,
        onPointerDown: (_) => _recordActivity(),
        child: child ?? const SizedBox.shrink(),
      ),
    );
  }
}

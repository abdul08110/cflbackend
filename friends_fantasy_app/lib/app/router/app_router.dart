import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../features/admin/presentation/providers/admin_auth_controller.dart';
import '../../features/admin/presentation/providers/admin_auth_state.dart';
import '../../features/admin/presentation/screens/admin_home_screen.dart';
import '../../features/admin/presentation/screens/admin_login_screen.dart';
import '../../features/admin/presentation/screens/admin_users_screen.dart';
import '../../features/auth/presentation/providers/auth_controller.dart';
import '../../features/auth/presentation/providers/auth_state.dart';
import '../../features/auth/presentation/screens/login_screen.dart';
import '../../features/auth/presentation/screens/otp_verify_screen.dart';
import '../../features/auth/presentation/screens/register_screen.dart';
import '../../features/auth/presentation/screens/forgot_password_screen.dart';
import '../../features/auth/presentation/screens/change_password_screen.dart';
import '../../features/auth/presentation/screens/splash_screen.dart';
import '../../features/contest/presentation/screens/contest_detail_screen.dart';
import '../../features/dashboard/presentation/screens/home_screen.dart';
import '../../features/fixture/presentation/screens/fixture_detail_screen.dart';
import '../../features/fixture/presentation/screens/fixtures_screen.dart';
import '../../features/friend/presentation/screens/friends_screen.dart';
import '../../features/notification/presentation/screens/notifications_screen.dart';
import '../../features/room/presentation/screens/create_room_screen.dart';
import '../../features/room/presentation/screens/join_room_screen.dart';
import '../../features/room/presentation/screens/room_detail_screen.dart';
import '../../features/room/presentation/screens/rooms_screen.dart';
import '../../features/stats/presentation/screens/profile_screen.dart';
import '../../features/team/presentation/screens/my_teams_screen.dart';
import '../../features/team/presentation/screens/team_builder_screen.dart';
import '../../features/wallet/presentation/screens/wallet_screen.dart';

final appNavigatorKey = GlobalKey<NavigatorState>();

class RouterRefreshNotifier extends ChangeNotifier {
  RouterRefreshNotifier(this.ref) {
    ref.listen<AuthState>(authControllerProvider, (_, _) {
      notifyListeners();
    });
    ref.listen<AdminAuthState>(adminAuthControllerProvider, (_, _) {
      notifyListeners();
    });
  }

  final Ref ref;
}

final routerRefreshProvider = Provider<RouterRefreshNotifier>((ref) {
  return RouterRefreshNotifier(ref);
});

final appRouterProvider = Provider<GoRouter>((ref) {
  final refreshNotifier = ref.read(routerRefreshProvider);

  return GoRouter(
    navigatorKey: appNavigatorKey,
    initialLocation: '/',
    refreshListenable: refreshNotifier,
    routes: [
      GoRoute(path: '/', builder: (context, state) => const SplashScreen()),
      GoRoute(path: '/login', builder: (context, state) => const LoginScreen()),
      GoRoute(
        path: '/forgot-password',
        builder: (context, state) => const ForgotPasswordScreen(),
      ),
      GoRoute(
        path: '/register',
        builder: (context, state) => const RegisterScreen(),
      ),
      GoRoute(
        path: '/otp-verify',
        builder: (context, state) => const OtpVerifyScreen(),
      ),
      GoRoute(path: '/home', builder: (context, state) => const HomeScreen()),
      GoRoute(
        path: '/notifications',
        builder: (context, state) => const NotificationsScreen(),
      ),
      GoRoute(
        path: '/wallet',
        builder: (context, state) =>
            WalletScreen(initialSection: state.uri.queryParameters['section']),
      ),
      GoRoute(
        path: '/fixtures',
        builder: (context, state) => const FixturesScreen(),
      ),
      GoRoute(
        path: '/fixtures/:fixtureId',
        builder: (context, state) =>
            FixtureDetailScreen(fixtureId: state.pathParameters['fixtureId']!),
      ),
      GoRoute(
        path: '/fixtures/:fixtureId/my-teams',
        builder: (context, state) =>
            MyTeamsScreen(fixtureId: state.pathParameters['fixtureId']!),
      ),
      GoRoute(
        path: '/fixtures/:fixtureId/team',
        builder: (context, state) => TeamBuilderScreen(
          fixtureId: state.pathParameters['fixtureId']!,
          teamId: state.uri.queryParameters['teamId'],
          contestId: state.uri.queryParameters['contestId'],
          communityId: state.uri.queryParameters['communityId'],
        ),
      ),
      GoRoute(
        path: '/fixtures/:fixtureId/contests/:contestId',
        builder: (context, state) => ContestDetailScreen(
          fixtureId: state.pathParameters['fixtureId']!,
          contestId: state.pathParameters['contestId']!,
        ),
      ),
      GoRoute(
        path: '/communities',
        builder: (context, state) => const RoomsScreen(),
      ),
      GoRoute(
        path: '/communities/create',
        builder: (context, state) => CreateRoomScreen(
          preselectedFixtureId: state.uri.queryParameters['fixtureId'],
        ),
      ),
      GoRoute(
        path: '/communities/join',
        builder: (context, state) => const JoinRoomScreen(),
      ),
      GoRoute(
        path: '/communities/:roomId',
        builder: (context, state) =>
            RoomDetailScreen(roomId: state.pathParameters['roomId']!),
      ),
      GoRoute(path: '/rooms', redirect: (context, state) => '/communities'),
      GoRoute(
        path: '/rooms/create',
        redirect: (context, state) {
          final fixtureId = state.uri.queryParameters['fixtureId'];
          if (fixtureId == null || fixtureId.isEmpty) {
            return '/communities/create';
          }
          return '/communities/create?fixtureId=$fixtureId';
        },
      ),
      GoRoute(
        path: '/rooms/join',
        redirect: (context, state) => '/communities/join',
      ),
      GoRoute(
        path: '/rooms/:roomId',
        redirect: (context, state) =>
            '/communities/${state.pathParameters['roomId']}',
      ),
      GoRoute(
        path: '/friends',
        builder: (context, state) => const FriendsScreen(),
      ),
      GoRoute(
        path: '/profile',
        builder: (context, state) => const ProfileScreen(),
      ),
      GoRoute(
        path: '/profile/change-password',
        builder: (context, state) => const ChangePasswordScreen(),
      ),

      // ADMIN
      GoRoute(
        path: '/admin/login',
        builder: (context, state) => const AdminLoginScreen(),
      ),
      GoRoute(
        path: '/admin/home',
        builder: (context, state) => const AdminHomeScreen(),
      ),
      GoRoute(
        path: '/admin/users',
        builder: (context, state) => const AdminUsersScreen(),
      ),
    ],
    redirect: (context, state) {
      final auth = ref.read(authControllerProvider);
      final adminAuth = ref.read(adminAuthControllerProvider);

      final location = state.matchedLocation;
      final isLoggedIn = auth.status == AppAuthStatus.authenticated;
      final isBooting = auth.status == AppAuthStatus.unknown;
      final isAdminLoggedIn = adminAuth.status == AdminAuthStatus.authenticated;

      final isLoginRoute = location == '/login';
      final isForgotPasswordRoute = location == '/forgot-password';
      final isRegisterRoute = location == '/register';
      final isOtpRoute = location == '/otp-verify';
      final isAuthRoute =
          isLoginRoute || isForgotPasswordRoute || isRegisterRoute || isOtpRoute;

      // ADMIN ROUTES
      final isAdminLoginRoute = location == '/admin/login';
      final isAdminHomeRoute = location == '/admin/home';
      final isAdminUsersRoute = location == '/admin/users';
      final isAdminRoute =
          isAdminLoginRoute || isAdminHomeRoute || isAdminUsersRoute;

      if (isAdminRoute) {
        if (!isAdminLoggedIn && !isAdminLoginRoute) {
          return '/admin/login';
        }
        if (isAdminLoggedIn && isAdminLoginRoute) {
          return '/admin/home';
        }
        return null;
      }

      if (isBooting) {
        return location == '/' ? null : '/';
      }

      if (!isLoggedIn && location == '/') {
        return '/login';
      }

      if (!isLoggedIn && isOtpRoute) {
        return auth.pendingRegister == null ? '/register' : null;
      }

      if (!isLoggedIn && !isAuthRoute) {
        return '/login';
      }

      if (isLoggedIn && (location == '/' || isAuthRoute)) {
        return '/home';
      }

      return null;
    },
  );
});

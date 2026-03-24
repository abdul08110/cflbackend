import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../features/auth/presentation/providers/auth_controller.dart';
import '../theme/app_theme.dart';

class PrimaryScaffold extends ConsumerWidget {
  const PrimaryScaffold({
    super.key,
    required this.currentIndex,
    required this.title,
    required this.body,
    this.actions,
    this.floatingActionButton,
    this.navItems,
    this.showLogoutAction,
    this.showBottomNav,
  });

  final int currentIndex;
  final String title;
  final Widget body;
  final List<Widget>? actions;
  final Widget? floatingActionButton;
  final List<NavItem>? navItems;
  final bool? showLogoutAction;
  final bool? showBottomNav;

  static const defaultNavItems = <NavItem>[
    NavItem(label: 'Home', icon: Icons.home_rounded, route: '/home'),
    NavItem(
      label: 'Communities',
      icon: Icons.groups_rounded,
      route: '/communities',
    ),
    NavItem(
      label: 'Wallet',
      icon: Icons.account_balance_wallet_rounded,
      route: '/wallet',
    ),
    NavItem(
      label: 'Friends',
      icon: Icons.people_alt_rounded,
      route: '/friends',
    ),
  ];

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final items = navItems ?? defaultNavItems;
    final resolvedShowLogoutAction = showLogoutAction ?? currentIndex >= 0;
    final resolvedShowBottomNav = showBottomNav ?? currentIndex >= 0;

    return Scaffold(
      extendBody: true,
      appBar: AppBar(
        title: Text(
          title,
          style: const TextStyle(
            fontSize: 16,
            fontWeight: FontWeight.w800,
            letterSpacing: 0.3,
          ),
        ),
        actions: [
          ...?actions,
          if (resolvedShowLogoutAction)
            TextButton.icon(
              onPressed: () async {
                final confirmed = await showDialog<bool>(
                  context: context,
                  builder: (context) => AlertDialog(
                    title: const Text('Logout'),
                    content: const Text('Are you sure you want to logout?'),
                    actions: [
                      TextButton(
                        onPressed: () => Navigator.pop(context, false),
                        child: const Text('Cancel'),
                      ),
                      TextButton(
                        onPressed: () => Navigator.pop(context, true),
                        style: TextButton.styleFrom(
                          foregroundColor: Colors.red,
                        ),
                        child: const Text('Logout'),
                      ),
                    ],
                  ),
                );

                if (confirmed == true) {
                  await ref.read(authControllerProvider.notifier).logout();
                }
              },
              icon: const Icon(
                Icons.logout_rounded,
                color: Colors.white,
                size: 18,
              ),
              label: const Text(
                'LOGOUT',
                style: TextStyle(
                  color: Colors.white,
                  fontWeight: FontWeight.w800,
                ),
              ),
            ),
        ],
      ),
      floatingActionButton: floatingActionButton,
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            colors: [AppTheme.darkBg, AppTheme.darkBg2],
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
          ),
        ),
        child: SafeArea(top: false, child: body),
      ),
      bottomNavigationBar: resolvedShowBottomNav
          ? _DreamBottomNav(
              currentIndex: currentIndex,
              items: items,
              onTap: (index) {
                if (index == currentIndex) return;
                context.go(items[index].route);
              },
            )
          : null,
    );
  }
}

class NavItem {
  const NavItem({required this.label, required this.icon, required this.route});
  final String label;
  final IconData icon;
  final String route;
}

class _DreamBottomNav extends StatelessWidget {
  const _DreamBottomNav({
    required this.currentIndex,
    required this.onTap,
    required this.items,
  });

  final int currentIndex;
  final ValueChanged<int> onTap;
  final List<NavItem> items;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.fromLTRB(12, 0, 12, 12),
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
      decoration: BoxDecoration(
        color: const Color(0xFF0C1223),
        borderRadius: BorderRadius.circular(28),
        border: Border.all(color: Colors.white.withValues(alpha: 0.06)),
        boxShadow: const [
          BoxShadow(
            color: Colors.black54,
            blurRadius: 18,
            offset: Offset(0, 10),
          ),
        ],
      ),
      child: SafeArea(
        top: false,
        child: Row(
          children: List.generate(items.length, (index) {
            final selected = currentIndex == index;
            final item = items[index];

            return Expanded(
              child: InkWell(
                borderRadius: BorderRadius.circular(22),
                onTap: () => onTap(index),
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 220),
                  padding: const EdgeInsets.symmetric(vertical: 10),
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(22),
                    gradient: selected
                        ? const LinearGradient(
                            colors: [Color(0xFF18C8FF), Color(0xFF933FFE)],
                          )
                        : null,
                    color: selected ? null : Colors.transparent,
                  ),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(item.icon, color: Colors.white, size: 22),
                      const SizedBox(height: 4),
                      Text(
                        item.label,
                        style: TextStyle(
                          color: Colors.white.withValues(alpha: selected ? 1 : 0.72),
                          fontSize: 11,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            );
          }),
        ),
      ),
    );
  }
}

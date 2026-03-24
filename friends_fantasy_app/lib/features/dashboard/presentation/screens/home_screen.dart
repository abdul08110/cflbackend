import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../../app/widgets/primary_scaffold.dart';
import '../../../auth/presentation/providers/auth_controller.dart';
import '../../../fixture/data/fixture_repository.dart';
import '../../../fixture/domain/models/fixture_models.dart';
import '../../../friend/data/friend_repository.dart';
import '../../../notification/data/notification_repository.dart';
import '../../../notification/domain/models/notification_models.dart';
import '../../../wallet/data/wallet_repository.dart';

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  final Set<String> _shownNotificationIds = <String>{};

  void _showNotificationPopup(List<AppNotification> notifications) {
    final friendCount = notifications
        .where((item) => item.type == 'FRIEND_REQUEST')
        .length;
    final communityCount =
        notifications.where((item) => item.type.startsWith('COMMUNITY_')).length;

    final parts = <String>[];
    if (friendCount > 0) {
      parts.add(
        friendCount == 1 ? '1 friend request' : '$friendCount friend requests',
      );
    }
    if (communityCount > 0) {
      parts.add(
        communityCount == 1
            ? '1 community update'
            : '$communityCount community updates',
      );
    }

    showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('New updates waiting'),
        content: Text(
          'You have ${parts.join(' and ')} waiting for you. Open notifications to review them now.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Later'),
          ),
          ElevatedButton(
            onPressed: () {
              Navigator.of(context).pop();
              context.push('/notifications');
            },
            child: const Text('Open'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final user = ref.watch(authControllerProvider).user;
    final notifications = ref.watch(myNotificationsProvider);

    ref.listen<AsyncValue<List<AppNotification>>>(myNotificationsProvider, (
      _,
      next,
    ) {
      next.whenData((items) {
        final actionableUnread = items
            .where(
              (item) =>
                  !item.isRead &&
                  item.isActionable &&
                  !_shownNotificationIds.contains(item.id),
            )
            .toList();

        if (actionableUnread.isEmpty) return;

        for (final item in actionableUnread) {
          _shownNotificationIds.add(item.id);
        }
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (!mounted) return;
          _showNotificationPopup(actionableUnread);
        });
      });
    });

    final rawUsername = (user?.username ?? '').trim();
    final greetingName = rawUsername.isNotEmpty ? rawUsername : 'Player';
    final unreadCount =
        notifications.asData?.value.where((item) => !item.isRead).length ?? 0;

    return PrimaryScaffold(
      currentIndex: 0,
      title: 'COMMUNITY FANTASY LEAGUE',
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 14, 16, 28),
        children: [
          _HomeHeader(greetingName: greetingName, unreadCount: unreadCount),
          const SizedBox(height: 18),
          const _UpcomingMatchesStrip(),
          const SizedBox(height: 18),
          const _CompactPointsCard(),
          const SizedBox(height: 16),
          const _FriendsAccordion(),
          const SizedBox(height: 16),
          const _CommunitiesAccordion(),
          const SizedBox(height: 16),
          const _TournamentSection(),
        ],
      ),
    );
  }
}

class _HomeHeader extends StatelessWidget {
  const _HomeHeader({
    required this.greetingName,
    required this.unreadCount,
  });

  final String greetingName;
  final int unreadCount;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(28),
        gradient: const LinearGradient(
          colors: [Color(0xFF17110E), Color(0xFF0B0F1D)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        border: Border.all(color: const Color(0x33F26A5E)),
        boxShadow: [
          BoxShadow(
            color: const Color(0x55B82025).withValues(alpha: 0.16),
            blurRadius: 28,
            offset: const Offset(0, 12),
          ),
        ],
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'HEY $greetingName',
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 22,
                    fontWeight: FontWeight.w900,
                    height: 1.15,
                  ),
                ),
                const SizedBox(height: 8),
                const Text(
                  'WELCOME TO CFL',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 16,
                    fontWeight: FontWeight.w900,
                    letterSpacing: 0.3,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  "India's only closed Community fantasy league.",
                  style: TextStyle(
                    color: Colors.white.withValues(alpha: 0.68),
                    fontSize: 13,
                    height: 1.4,
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 12),
          Column(
            children: [
              _HeaderActionButton(
                icon: Icons.notifications_rounded,
                badgeCount: unreadCount,
                onTap: () => context.push('/notifications'),
              ),
              const SizedBox(height: 10),
              _HeaderActionButton(
                icon: Icons.person_rounded,
                onTap: () => context.push('/profile'),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _HeaderActionButton extends StatelessWidget {
  const _HeaderActionButton({
    required this.icon,
    required this.onTap,
    this.badgeCount = 0,
  });

  final IconData icon;
  final VoidCallback onTap;
  final int badgeCount;

  @override
  Widget build(BuildContext context) {
    return Stack(
      clipBehavior: Clip.none,
      children: [
        Container(
          width: 46,
          height: 46,
          decoration: BoxDecoration(
            color: Colors.white.withValues(alpha: 0.06),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
          ),
          child: IconButton(
            onPressed: onTap,
            icon: Icon(icon, color: Colors.white),
          ),
        ),
        if (badgeCount > 0)
          Positioned(
            right: -3,
            top: -4,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
              decoration: BoxDecoration(
                color: const Color(0xFFF5535B),
                borderRadius: BorderRadius.circular(999),
                border: Border.all(color: const Color(0xFF0B0F1D), width: 2),
              ),
              child: Text(
                badgeCount > 9 ? '9+' : '$badgeCount',
                style: const TextStyle(
                  color: Colors.white,
                  fontWeight: FontWeight.w800,
                  fontSize: 10,
                ),
              ),
            ),
          ),
      ],
    );
  }
}

class _UpcomingMatchesStrip extends ConsumerStatefulWidget {
  const _UpcomingMatchesStrip();

  @override
  ConsumerState<_UpcomingMatchesStrip> createState() =>
      _UpcomingMatchesStripState();
}

class _UpcomingMatchesStripState extends ConsumerState<_UpcomingMatchesStrip> {
  int _activeIndex = 0;

  @override
  Widget build(BuildContext context) {
    final upcomingFixtures = ref.watch(homeUpcomingFixturesProvider);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _SectionHeader(
          title: 'Upcoming Matches',
          actionLabel: 'See all',
          onTap: () => context.push('/fixtures'),
        ),
        const SizedBox(height: 12),
        upcomingFixtures.when(
          loading: () => const _InlineInfoCard(
            title: 'Loading upcoming matches',
            subtitle: 'Fetching fixture cards for your home screen.',
          ),
          error: (error, _) => _InlineInfoCard(
            title: 'Could not load upcoming matches',
            subtitle: error.toString().replaceFirst('Exception: ', ''),
            actionLabel: 'Retry',
            onTap: () => ref.invalidate(homeUpcomingFixturesProvider),
          ),
          data: (items) {
            final sortedItems = List<FixtureSummary>.from(items)
              ..sort(_compareFixturesByStartTime);
            final visibleFixtures = sortedItems.take(3).toList();
            if (visibleFixtures.isEmpty) {
              return const _InlineInfoCard(
                title: 'No upcoming matches right now',
                subtitle: 'Fresh fixtures will appear here as soon as they sync.',
              );
            }

            if (_activeIndex >= visibleFixtures.length) {
              _activeIndex = 0;
            }

            return SizedBox(
              height: 150,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                itemCount: visibleFixtures.length,
                separatorBuilder: (_, _) => const SizedBox(width: 12),
                itemBuilder: (context, index) {
                  final fixture = visibleFixtures[index];
                  return _UpcomingMatchCard(
                    fixture: fixture,
                    active: index == _activeIndex,
                    onTap: () {
                      setState(() => _activeIndex = index);
                      context.push('/fixtures/${fixture.id}');
                    },
                  );
                },
              ),
            );
          },
        ),
      ],
    );
  }
}

class _UpcomingMatchCard extends StatelessWidget {
  const _UpcomingMatchCard({
    required this.fixture,
    required this.active,
    required this.onTap,
  });

  final FixtureSummary fixture;
  final bool active;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final home = fixture.homeTeam;
    final away = fixture.awayTeam;
    final accent = active ? const Color(0xFFF15B63) : const Color(0x22FFFFFF);

    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        width: 252,
        padding: const EdgeInsets.fromLTRB(14, 12, 14, 10),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(26),
          gradient: LinearGradient(
            colors: active
                ? const [Color(0xFF231714), Color(0xFF10111B)]
                : const [Color(0xFF11131A), Color(0xFF0A0C14)],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          border: Border.all(
            color: active ? const Color(0x66F15B63) : const Color(0x22FFFFFF),
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              fixture.title,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                color: Colors.white.withValues(alpha: 0.78),
                fontSize: 12,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 10),
            Row(
              children: [
                Expanded(
                  child: _TeamDisplay(
                    shortName: fixture.teamAShort,
                    logoUrl: home?.logoUrl,
                    reversed: false,
                  ),
                ),
                Text(
                  'v',
                  style: TextStyle(
                    color: Colors.white.withValues(alpha: 0.42),
                    fontSize: 20,
                    fontWeight: FontWeight.w900,
                  ),
                ),
                Expanded(
                  child: _TeamDisplay(
                    shortName: fixture.teamBShort,
                    logoUrl: away?.logoUrl,
                    reversed: true,
                  ),
                ),
              ],
            ),
            const Spacer(),
            Text(
              _fixtureCountdownText(fixture),
              style: TextStyle(
                color: active
                    ? const Color(0xFFF15B63)
                    : Colors.white.withValues(alpha: 0.72),
                fontSize: 13,
                fontWeight: FontWeight.w800,
              ),
            ),
            const SizedBox(height: 8),
            AnimatedContainer(
              duration: const Duration(milliseconds: 180),
              height: 4,
              width: active ? 72 : 36,
              decoration: BoxDecoration(
                color: accent,
                borderRadius: BorderRadius.circular(999),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _TeamDisplay extends StatelessWidget {
  const _TeamDisplay({
    required this.shortName,
    required this.logoUrl,
    required this.reversed,
  });

  final String shortName;
  final String? logoUrl;
  final bool reversed;

  @override
  Widget build(BuildContext context) {
    final children = <Widget>[
      _TeamLogo(logoUrl: logoUrl, shortName: shortName),
      const SizedBox(width: 8),
      Flexible(
        child: Text(
          shortName,
          textAlign: reversed ? TextAlign.right : TextAlign.left,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 18,
            fontWeight: FontWeight.w900,
            letterSpacing: -0.8,
          ),
        ),
      ),
    ];

    return Row(
      mainAxisAlignment: reversed
          ? MainAxisAlignment.end
          : MainAxisAlignment.start,
      children: reversed ? children.reversed.toList() : children,
    );
  }
}

class _TeamLogo extends StatelessWidget {
  const _TeamLogo({
    required this.logoUrl,
    required this.shortName,
  });

  final String? logoUrl;
  final String shortName;

  @override
  Widget build(BuildContext context) {
    final initials = shortName.trim().isEmpty
        ? '?'
        : shortName.trim().split(RegExp(r'\s+')).take(2).map((part) {
            return part.substring(0, 1).toUpperCase();
          }).join();

    return Container(
      width: 38,
      height: 38,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        border: Border.all(color: Colors.white.withValues(alpha: 0.15)),
        gradient: const LinearGradient(
          colors: [Color(0xFF29344E), Color(0xFF111827)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
      ),
      clipBehavior: Clip.antiAlias,
      child: (logoUrl ?? '').trim().isEmpty
          ? Center(
              child: Text(
                initials,
                style: const TextStyle(
                  color: Colors.white,
                  fontWeight: FontWeight.w800,
                  fontSize: 12,
                ),
              ),
            )
          : Image.network(
              logoUrl!,
              fit: BoxFit.cover,
              errorBuilder: (_, _, _) => Center(
                child: Text(
                  initials,
                  style: const TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.w800,
                    fontSize: 12,
                  ),
                ),
              ),
            ),
    );
  }
}

class _CompactPointsCard extends ConsumerWidget {
  const _CompactPointsCard();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final wallet = ref.watch(myWalletProvider);

    return GestureDetector(
      onTap: () => context.push('/wallet?section=points'),
      child: Container(
        padding: const EdgeInsets.all(18),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(26),
          gradient: const LinearGradient(
            colors: [Color(0xFF151823), Color(0xFF0D1018)],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          border: Border.all(color: const Color(0x1FFFFFFF)),
        ),
        child: Row(
          children: [
            Container(
              width: 42,
              height: 42,
              decoration: BoxDecoration(
                color: const Color(0x14F15B63),
                borderRadius: BorderRadius.circular(14),
              ),
              child: const Icon(
                Icons.stars_rounded,
                color: Color(0xFFF9D36A),
              ),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: wallet.when(
                loading: () => const Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Points Balance',
                      style: TextStyle(
                        color: Colors.white70,
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    SizedBox(height: 4),
                    Text(
                      'Loading your balance...',
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 17,
                        fontWeight: FontWeight.w900,
                      ),
                    ),
                  ],
                ),
                error: (error, _) => Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Points Balance',
                      style: TextStyle(
                        color: Colors.white70,
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      error.toString().replaceFirst('Exception: ', ''),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 14,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ],
                ),
                data: (summary) => Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Points Balance',
                      style: TextStyle(
                        color: Colors.white70,
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '${summary.balance.toStringAsFixed(summary.balance % 1 == 0 ? 0 : 1)} ${summary.currency}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 24,
                        fontWeight: FontWeight.w900,
                        letterSpacing: -0.8,
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(width: 12),
            const Icon(
              Icons.chevron_right_rounded,
              color: Colors.white70,
              size: 28,
            ),
          ],
        ),
      ),
    );
  }
}

class _FriendsAccordion extends ConsumerWidget {
  const _FriendsAccordion();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final incoming = ref.watch(incomingFriendRequestsProvider);

    return _HomeAccordionCard(
      title: 'FRIENDS',
      subtitle: '',
      icon: Icons.people_alt_rounded,
      child: Column(
        children: [
          Row(
            children: [
              Expanded(
                child: _SimpleActionRow(
                  icon: Icons.person_add_alt_1_rounded,
                  label: 'Add friends',
                  onTap: () => context.push('/friends'),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: incoming.when(
                  loading: () => const _SimpleActionRow(
                    icon: Icons.mark_email_unread_rounded,
                    label: 'Requests...',
                  ),
                  error: (_, _) => _SimpleActionRow(
                    icon: Icons.refresh_rounded,
                    label: 'Retry requests',
                    onTap: () => ref.invalidate(incomingFriendRequestsProvider),
                  ),
                  data: (requests) => _SimpleActionRow(
                    icon: Icons.mark_email_unread_rounded,
                    label: requests.isEmpty
                        ? 'No requests'
                        : '${requests.length} request${requests.length == 1 ? '' : 's'}',
                    onTap: () => context.push('/friends'),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          _SimpleActionRow(
            icon: Icons.tune_rounded,
            label: 'Manage',
            onTap: () => context.push('/friends'),
          ),
        ],
      ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  const _SectionHeader({
    required this.title,
    this.actionLabel,
    this.onTap,
  });

  final String title;
  final String? actionLabel;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Text(
            title.toUpperCase(),
            style: const TextStyle(
              color: Colors.white,
              fontSize: 19,
              fontWeight: FontWeight.w900,
              letterSpacing: -0.4,
            ),
          ),
        ),
        if (actionLabel != null && onTap != null)
          TextButton(
            onPressed: onTap,
            child: Text(actionLabel!.toUpperCase()),
          ),
      ],
    );
  }
}

class _InlineInfoCard extends StatelessWidget {
  const _InlineInfoCard({
    required this.title,
    required this.subtitle,
    this.actionLabel,
    this.onTap,
  });

  final String title;
  final String subtitle;
  final String? actionLabel;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.04),
        borderRadius: BorderRadius.circular(24),
        border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 14,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  subtitle,
                  style: TextStyle(
                    color: Colors.white.withValues(alpha: 0.68),
                    fontSize: 12,
                    height: 1.45,
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ],
            ),
          ),
          if (actionLabel != null && onTap != null) ...[
            const SizedBox(width: 10),
            OutlinedButton(
              onPressed: onTap,
              child: Text(actionLabel!),
            ),
          ],
        ],
      ),
    );
  }
}

class _HomeAccordionCard extends StatelessWidget {
  const _HomeAccordionCard({
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.child,
  });

  final String title;
  final String subtitle;
  final IconData icon;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Theme(
      data: Theme.of(context).copyWith(dividerColor: Colors.transparent),
      child: Container(
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(28),
          gradient: const LinearGradient(
            colors: [Color(0xFF151823), Color(0xFF0E111A)],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          border: Border.all(color: const Color(0x1FFFFFFF)),
        ),
        child: ExpansionTile(
          initiallyExpanded: true,
          collapsedShape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(28),
          ),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(28),
          ),
          leading: Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              color: const Color(0x14F15B63),
              borderRadius: BorderRadius.circular(14),
            ),
            child: Icon(icon, color: const Color(0xFFF15B63)),
          ),
          title: Text(
            title.toUpperCase(),
            style: const TextStyle(
              color: Colors.white,
              fontSize: 17,
              fontWeight: FontWeight.w900,
            ),
          ),
          subtitle: subtitle.trim().isEmpty
              ? null
              : Text(
                  subtitle,
                  style: TextStyle(
                    color: Colors.white.withValues(alpha: 0.62),
                    fontSize: 12,
                    fontWeight: FontWeight.w500,
                  ),
                ),
          trailing: const Icon(
            Icons.expand_more_rounded,
            color: Colors.white70,
          ),
          childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
          children: [child],
        ),
      ),
    );
  }
}

class _SimpleActionRow extends StatelessWidget {
  const _SimpleActionRow({
    required this.icon,
    required this.label,
    this.onTap,
  });

  final IconData icon;
  final String label;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
        decoration: BoxDecoration(
          color: Colors.white.withValues(alpha: 0.04),
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
        ),
        child: Row(
          children: [
            Icon(icon, color: const Color(0xFFF15B63), size: 18),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                label,
                textAlign: TextAlign.center,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 12,
                  fontWeight: FontWeight.w800,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _CommunitiesAccordion extends ConsumerWidget {
  const _CommunitiesAccordion();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return _HomeAccordionCard(
      title: 'COMMUNITIES',
      subtitle: '',
      icon: Icons.groups_rounded,
      child: Column(
        children: [
          Row(
            children: [
              Expanded(
                child: _SimpleActionRow(
                  icon: Icons.add_circle_outline_rounded,
                  label: 'Create',
                  onTap: () => context.push('/communities/create'),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: _SimpleActionRow(
                  icon: Icons.group_add_rounded,
                  label: 'Join',
                  onTap: () => context.push('/communities/join'),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          _SimpleActionRow(
            icon: Icons.tune_rounded,
            label: 'Manage',
            onTap: () => context.push('/communities'),
          ),
        ],
      ),
    );
  }
}

class _TournamentSection extends ConsumerWidget {
  const _TournamentSection();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final upcomingFixtures = ref.watch(homeUpcomingFixturesProvider);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _SectionHeader(
          title: 'Tournaments',
          actionLabel: 'Fixtures',
          onTap: () => context.push('/fixtures'),
        ),
        const SizedBox(height: 12),
        upcomingFixtures.when(
          loading: () => const _InlineInfoCard(
            title: 'Loading tournaments',
            subtitle: 'Organising leagues from your upcoming fixtures.',
          ),
          error: (error, _) => _InlineInfoCard(
            title: 'Could not load tournaments',
            subtitle: error.toString().replaceFirst('Exception: ', ''),
            actionLabel: 'Retry',
            onTap: () => ref.invalidate(homeUpcomingFixturesProvider),
          ),
          data: (fixtures) {
            final items = _buildTournamentItems(fixtures);
            if (items.isEmpty) {
              return const _InlineInfoCard(
                title: 'No tournament cards yet',
                subtitle:
                    'As soon as fixtures are synced, your IPL and other tournaments will show here.',
              );
            }

            return Column(
              children: [
                for (var index = 0; index < items.length; index++) ...[
                  _TournamentTile(item: items[index]),
                  if (index != items.length - 1) const SizedBox(height: 12),
                ],
              ],
            );
          },
        ),
      ],
    );
  }
}

class _TournamentTile extends StatelessWidget {
  const _TournamentTile({
    required this.item,
  });

  final _TournamentItem item;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () => context.push('/fixtures'),
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(24),
          gradient: const LinearGradient(
            colors: [Color(0xFF151823), Color(0xFF0D1018)],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
          border: Border.all(color: const Color(0x1FFFFFFF)),
        ),
        child: Row(
          children: [
            Container(
              width: 52,
              height: 52,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(18),
                gradient: const LinearGradient(
                  colors: [Color(0xFFF15B63), Color(0xFF43252A)],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
              ),
              alignment: Alignment.center,
              child: Text(
                item.badge,
                style: const TextStyle(
                  color: Colors.white,
                  fontWeight: FontWeight.w900,
                  fontSize: 16,
                ),
              ),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    item.name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 16,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    '${item.fixtureCount} upcoming match${item.fixtureCount == 1 ? '' : 'es'} | ${item.nextText}',
                    style: TextStyle(
                      color: Colors.white.withValues(alpha: 0.62),
                      fontSize: 12,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 12),
            const Icon(
              Icons.arrow_forward_ios_rounded,
              color: Colors.white70,
              size: 18,
            ),
          ],
        ),
      ),
    );
  }
}

String _fixtureCountdownText(FixtureSummary fixture) {
  final start = fixture.deadlineTime ?? fixture.startTime;
  if (start == null) return fixture.statusLabel;

  final now = DateTime.now();
  final difference = start.difference(now);

  if (difference.isNegative) {
    return fixture.statusLabel == 'LIVE' ? 'Live now' : 'Started';
  }

  if (difference.inMinutes < 1) {
    return 'Starting now';
  }

  if (difference.inHours < 1) {
    return '${difference.inMinutes}m left';
  }

  if (difference.inHours < 24) {
    final hours = difference.inHours;
    final minutes = difference.inMinutes.remainder(60);
    return '${hours}h ${minutes}m';
  }

  final tomorrow = DateTime(now.year, now.month, now.day + 1);
  final startDay = DateTime(start.year, start.month, start.day);
  if (startDay == tomorrow) {
    return 'Tomorrow, ${DateFormat('h:mm a').format(start)}';
  }

  return DateFormat('dd MMM, h:mm a').format(start);
}

List<_TournamentItem> _buildTournamentItems(List<FixtureSummary> fixtures) {
  final grouped = <String, List<FixtureSummary>>{};

  for (final fixture in fixtures) {
    final league = fixture.league.trim().isEmpty ? 'Cricket' : fixture.league;
    grouped.putIfAbsent(league, () => <FixtureSummary>[]).add(fixture);
  }

  final items = grouped.entries.map((entry) {
    final sorted = List<FixtureSummary>.from(entry.value)
      ..sort(_compareFixturesByStartTime);
    final nextFixture = sorted.first;
    final badgeSource = entry.key.trim().split(RegExp(r'\s+'));
    final badge = badgeSource
        .take(3)
        .map((part) => part.substring(0, 1).toUpperCase())
        .join();

    return _TournamentItem(
      name: entry.key,
      badge: badge,
      fixtureCount: entry.value.length,
      nextText: _fixtureCountdownText(nextFixture),
    );
  }).toList()
    ..sort((a, b) => a.name.compareTo(b.name));

  items.sort((a, b) {
    if (a.name.toUpperCase().contains('IPL') &&
        !b.name.toUpperCase().contains('IPL')) {
      return -1;
    }
    if (!a.name.toUpperCase().contains('IPL') &&
        b.name.toUpperCase().contains('IPL')) {
      return 1;
    }
    return b.fixtureCount.compareTo(a.fixtureCount);
  });

  return items.take(6).toList();
}

int _compareFixturesByStartTime(FixtureSummary a, FixtureSummary b) {
  final left = a.deadlineTime ?? a.startTime;
  final right = b.deadlineTime ?? b.startTime;

  if (left == null && right == null) return a.title.compareTo(b.title);
  if (left == null) return 1;
  if (right == null) return -1;
  return left.compareTo(right);
}

class _TournamentItem {
  const _TournamentItem({
    required this.name,
    required this.badge,
    required this.fixtureCount,
    required this.nextText,
  });

  final String name;
  final String badge;
  final int fixtureCount;
  final String nextText;
}

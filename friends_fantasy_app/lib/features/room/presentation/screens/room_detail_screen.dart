import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../app/widgets/primary_scaffold.dart';
import '../../../../core/widgets/async_value_view.dart';
import '../../../../core/widgets/section_card.dart';
import '../../../friend/data/friend_repository.dart';
import '../../../friend/domain/models/friend_models.dart';
import '../../../fixture/data/fixture_repository.dart';
import '../../../fixture/domain/models/fixture_models.dart';
import '../../../wallet/data/wallet_repository.dart';
import '../../data/room_repository.dart';
import '../../domain/models/room_models.dart';

enum _RoomContestFilter { upcoming, closed }

class RoomDetailScreen extends ConsumerStatefulWidget {
  const RoomDetailScreen({super.key, required this.roomId});

  final String roomId;

  @override
  ConsumerState<RoomDetailScreen> createState() => _RoomDetailScreenState();
}

class _RoomDetailScreenState extends ConsumerState<RoomDetailScreen> {
  bool _communityContestsExpanded = true;
  bool _membersExpanded = true;
  _RoomContestFilter _contestFilter = _RoomContestFilter.upcoming;

  Future<void> _refresh() async {
    ref.invalidate(roomDetailProvider(widget.roomId));
    ref.invalidate(myRoomsProvider);
    ref.invalidate(allRoomsProvider);
    await ref.read(roomDetailProvider(widget.roomId).future);
  }

  void _copyCode(String code) {
    Clipboard.setData(ClipboardData(text: code));
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(const SnackBar(content: Text('Community code copied')));
  }

  Future<void> _deleteCommunity(Room room) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Delete Community'),
        content: Text(
          'Delete ${room.name}? Any active community contests will be cancelled and joined points will be refunded.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );

    if (confirmed != true || !mounted) return;

    try {
      await ref.read(roomRepositoryProvider).deleteRoom(room.id);
      ref.invalidate(myRoomsProvider);
      ref.invalidate(allRoomsProvider);
      if (!mounted) return;
      context.go('/communities');
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Community deleted successfully')),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.toString().replaceFirst('Exception: ', ''))),
      );
    }
  }

  Future<void> _showMemberInviteSheet(RoomDetail detail) async {
    final existingMemberIds = detail.members.map((member) => member.id).toSet();
    final invited = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (sheetContext) => _FriendInviteSheet(
        title: 'Invite To Community',
        description: 'Choose from your friends list. Existing members are hidden automatically.',
        emptyMessage: 'No invite-ready friends found for this community yet.',
        excludedUserIds: existingMemberIds,
        onInvite: (friend) => ref.read(roomRepositoryProvider).invite(
              roomId: widget.roomId,
              username: friend.username,
            ),
      ),
    );

    if (invited == true && mounted) {
      ref.invalidate(roomDetailProvider(widget.roomId));
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Community invitation sent')),
      );
    }
  }

  Future<void> _showContestInviteSheet(
    RoomDetail detail,
    RoomContest contest,
  ) async {
    final communityMemberIds = detail.members.map((member) => member.id).toSet();
    final invited = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (sheetContext) => _FriendInviteSheet(
        title: 'Invite To ${contest.contestName}',
        description: 'Only friends who are already inside this community are shown here.',
        emptyMessage: 'No community friends are available to invite into this contest right now.',
        eligibleUserIds: communityMemberIds,
        onInvite: (friend) =>
            ref.read(roomRepositoryProvider).inviteToCommunityContest(
                  roomId: widget.roomId,
                  contestId: contest.id,
                  username: friend.username,
                ),
      ),
    );

    if (invited == true && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Contest invitation sent')),
      );
    }
  }

  Future<void> _showEditCommunitySheet(Room room) async {
    final updated = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (sheetContext) => _EditCommunitySheet(room: room),
    );

    if (updated == true && mounted) {
      await _refresh();
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Community updated successfully')),
      );
    }
  }

  Future<void> _showCreateContestSheet(int communityMaxMembers) async {
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (context) => _CreateContestSheet(
        roomId: widget.roomId,
        communityMaxMembers: communityMaxMembers,
      ),
    );
  }

  bool _isClosedContest(RoomContest contest) {
    final status = contest.contestStatus.trim().toUpperCase();
    return status == 'COMPLETED' || status == 'CANCELLED';
  }

  @override
  Widget build(BuildContext context) {
    final detailValue = ref.watch(roomDetailProvider(widget.roomId));

    return PrimaryScaffold(
      currentIndex: 1,
      title: 'Community',
      actions: [
        IconButton(
          onPressed: _refresh,
          icon: const Icon(Icons.refresh_rounded),
        ),
      ],
      body: AsyncValueView(
        value: detailValue,
        onRetry: _refresh,
        data: (detail) {
          final room = detail.community;
          final isOwner = room.myRole.toUpperCase() == 'OWNER';
          final upcomingContests = detail.contests
              .where((contest) => !_isClosedContest(contest))
              .toList();
          final closedContests = detail.contests
              .where(_isClosedContest)
              .toList();
          final visibleContests = _contestFilter == _RoomContestFilter.upcoming
              ? upcomingContests
              : closedContests;
          final contestEmptyMessage =
              _contestFilter == _RoomContestFilter.upcoming
              ? 'No upcoming community contests yet. Create the first contest for an upcoming match and invite your members.'
              : 'No closed community contests yet.';

          return ListView(
            padding: const EdgeInsets.only(top: 8, bottom: 32),
            children: [
              SectionCard(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                room.name,
                                style: const TextStyle(
                                  fontSize: 24,
                                  fontWeight: FontWeight.w800,
                                ),
                              ),
                              const SizedBox(height: 8),
                              Text(
                                room.createdByUsername.isEmpty
                                    ? 'Private invite-only community'
                                    : 'Owner: ${room.createdByUsername}',
                                style: const TextStyle(
                                  color: Color(0xFFA9B4D0),
                                  height: 1.5,
                                ),
                              ),
                            ],
                          ),
                        ),
                        _StatusBadge(
                          label: isOwner ? 'Owner' : 'Member',
                          color: isOwner
                              ? const Color(0xFFFFD36A)
                              : const Color(0xFF63D9FF),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: [
                        if (isOwner && room.code.isNotEmpty)
                          _InfoChip(
                            icon: Icons.vpn_key_rounded,
                            label: room.code,
                            onTap: () => _copyCode(room.code),
                          ),
                        _InfoChip(
                          icon: Icons.groups_rounded,
                          label: '${room.memberCount}/${room.maxMembers} members',
                        ),
                        _InfoChip(
                          icon: Icons.sports_cricket_rounded,
                          label: '${detail.contests.length} contests',
                        ),
                      ],
                    ),
                    const SizedBox(height: 18),
                    Wrap(
                      spacing: 10,
                      runSpacing: 10,
                      children: [
                        if (room.canInvite)
                          ElevatedButton.icon(
                            onPressed: () => _showMemberInviteSheet(detail),
                            icon: const Icon(Icons.person_add_alt_1_rounded),
                            label: const Text(
                              'Invite Member',
                              textAlign: TextAlign.center,
                            ),
                          ),
                        if (room.isMember)
                          OutlinedButton.icon(
                            onPressed: () => _showCreateContestSheet(room.maxMembers),
                            icon: const Icon(Icons.add_circle_outline_rounded),
                            label: const Text(
                              'Create Contest',
                              textAlign: TextAlign.center,
                            ),
                          ),
                        if (room.canEdit)
                          OutlinedButton.icon(
                            onPressed: () => _showEditCommunitySheet(room),
                            icon: const Icon(Icons.edit_outlined),
                            label: const Text(
                              'Edit Community',
                              textAlign: TextAlign.center,
                            ),
                          ),
                        if (room.canDelete)
                          OutlinedButton.icon(
                            onPressed: () => _deleteCommunity(room),
                            icon: const Icon(Icons.delete_outline_rounded),
                            label: const Text(
                              'Delete Community',
                              textAlign: TextAlign.center,
                            ),
                          ),
                      ],
                    ),
                  ],
                ),
              ),
              _CollapsibleSectionCard(
                title: 'Community Contests',
                subtitle:
                    '${upcomingContests.length} upcoming | ${closedContests.length} closed',
                expanded: _communityContestsExpanded,
                onExpansionChanged: (expanded) {
                  setState(() => _communityContestsExpanded = expanded);
                },
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Wrap(
                      spacing: 10,
                      runSpacing: 10,
                      children: [
                        ChoiceChip(
                          label: Text(
                            'Upcoming (${upcomingContests.length})',
                          ),
                          labelStyle: TextStyle(
                            color:
                                _contestFilter == _RoomContestFilter.upcoming
                                ? const Color(0xFF06111D)
                                : Colors.white,
                            fontWeight: FontWeight.w800,
                          ),
                          selectedColor: const Color(0xFF63D9FF),
                          backgroundColor: Colors.white.withValues(alpha: 0.05),
                          side: BorderSide(
                            color: Colors.white.withValues(alpha: 0.08),
                          ),
                          showCheckmark: false,
                          selected:
                              _contestFilter == _RoomContestFilter.upcoming,
                          onSelected: (_) {
                            setState(() {
                              _contestFilter = _RoomContestFilter.upcoming;
                            });
                          },
                        ),
                        ChoiceChip(
                          label: Text('Closed (${closedContests.length})'),
                          labelStyle: TextStyle(
                            color: _contestFilter == _RoomContestFilter.closed
                                ? const Color(0xFF06111D)
                                : Colors.white,
                            fontWeight: FontWeight.w800,
                          ),
                          selectedColor: const Color(0xFFFFD36A),
                          backgroundColor: Colors.white.withValues(alpha: 0.05),
                          side: BorderSide(
                            color: Colors.white.withValues(alpha: 0.08),
                          ),
                          showCheckmark: false,
                          selected: _contestFilter == _RoomContestFilter.closed,
                          onSelected: (_) {
                            setState(() {
                              _contestFilter = _RoomContestFilter.closed;
                            });
                          },
                        ),
                      ],
                    ),
                    const SizedBox(height: 14),
                    if (visibleContests.isEmpty)
                      Text(
                        contestEmptyMessage,
                        style: const TextStyle(
                          color: Color(0xFFA9B4D0),
                          height: 1.5,
                        ),
                      )
                    else
                      ...visibleContests.map(
                        (contest) => Padding(
                          padding: const EdgeInsets.only(bottom: 12),
                          child: _ContestCard(
                            contest: contest,
                            onOpen: () => context.push(
                              '/fixtures/${contest.fixtureId}/contests/${contest.id}',
                            ),
                            onInvite: contest.canInvite
                                ? () => _showContestInviteSheet(detail, contest)
                                : null,
                          ),
                        ),
                      ),
                  ],
                ),
              ),
              _CollapsibleSectionCard(
                title: 'Members',
                subtitle: '${detail.members.length} inside this community',
                expanded: _membersExpanded,
                onExpansionChanged: (expanded) {
                  setState(() => _membersExpanded = expanded);
                },
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: detail.members
                      .map(
                        (member) => Padding(
                          padding: const EdgeInsets.only(bottom: 12),
                          child: ListTile(
                            contentPadding: EdgeInsets.zero,
                            leading: CircleAvatar(
                              backgroundColor: member.role == 'OWNER'
                                  ? Colors.amber
                                  : Colors.white12,
                              child: Icon(
                                member.role == 'OWNER'
                                    ? Icons.star_rounded
                                    : Icons.person_rounded,
                                color: member.role == 'OWNER'
                                    ? Colors.black
                                    : Colors.white,
                              ),
                            ),
                            title: Text(
                              member.fullName,
                              style: const TextStyle(
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            subtitle: Text(
                              '${member.username} | ${member.role}',
                            ),
                          ),
                        ),
                      )
                      .toList(),
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}

class _CollapsibleSectionCard extends StatelessWidget {
  const _CollapsibleSectionCard({
    required this.title,
    required this.subtitle,
    required this.expanded,
    required this.onExpansionChanged,
    required this.child,
  });

  final String title;
  final String subtitle;
  final bool expanded;
  final ValueChanged<bool> onExpansionChanged;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return SectionCard(
      child: Theme(
        data: Theme.of(context).copyWith(dividerColor: Colors.transparent),
        child: ExpansionTile(
          initiallyExpanded: expanded,
          onExpansionChanged: onExpansionChanged,
          tilePadding: EdgeInsets.zero,
          childrenPadding: const EdgeInsets.only(top: 12),
          title: Text(
            title,
            style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
          ),
          subtitle: subtitle.trim().isEmpty
              ? null
              : Padding(
                  padding: const EdgeInsets.only(top: 6),
                  child: Text(
                    subtitle,
                    style: const TextStyle(
                      color: Color(0xFFA9B4D0),
                      height: 1.4,
                    ),
                  ),
                ),
          trailing: Icon(
            expanded ? Icons.expand_less_rounded : Icons.expand_more_rounded,
            color: Colors.white70,
          ),
          children: [child],
        ),
      ),
    );
  }
}

class _ContestCard extends StatelessWidget {
  const _ContestCard({
    required this.contest,
    required this.onOpen,
    this.onInvite,
  });

  final RoomContest contest;
  final VoidCallback onOpen;
  final VoidCallback? onInvite;

  @override
  Widget build(BuildContext context) {
    final matchLabel = contest.participants.length >= 2
        ? '${contest.participants.first.shortName} vs ${contest.participants[1].shortName}'
        : contest.fixtureTitle;

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.04),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      contest.contestName,
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      contest.fixtureLeague.isEmpty
                          ? matchLabel
                          : '$matchLabel | ${contest.fixtureLeague}',
                      style: const TextStyle(color: Color(0xFFA9B4D0)),
                    ),
                  ],
                ),
              ),
              _StatusBadge(
                label: contest.contestStatus,
                color: switch (contest.contestStatus) {
                  'LIVE' => const Color(0xFFFF7A59),
                  'COMPLETED' => const Color(0xFF39E48A),
                  'CANCELLED' => const Color(0xFFFFD36A),
                  _ => const Color(0xFF63D9FF),
                },
              ),
            ],
          ),
          const SizedBox(height: 14),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              _InfoChip(
                icon: Icons.stars_rounded,
                label: '${contest.entryFeePoints} pts',
              ),
              _InfoChip(
                icon: Icons.emoji_events_rounded,
                label: '${contest.firstPrizePoints} win',
              ),
              _InfoChip(
                icon: Icons.groups_rounded,
                label: '${contest.spotsFilled}/${contest.maxSpots} joined',
              ),
              _InfoChip(
                icon: Icons.person_rounded,
                label: 'By ${contest.createdByUsername}',
              ),
            ],
          ),
          const SizedBox(height: 12),
          Text(
            contest.joinedByMe
                ? 'You already reserved ${contest.myEntriesCount} spot${contest.myEntriesCount == 1 ? '' : 's'} in this contest. Open it to select or update your team.'
                : 'Open the contest to create a team or join with one of your existing teams.',
            style: const TextStyle(color: Color(0xFFA9B4D0), height: 1.45),
          ),
          const SizedBox(height: 14),
          Row(
            children: [
              Expanded(
                child: ElevatedButton(
                  onPressed: onOpen,
                  child: Text(
                    contest.joinedByMe ? 'Open Contest' : 'Join Contest',
                    textAlign: TextAlign.center,
                  ),
                ),
              ),
              if (onInvite != null) ...[
                const SizedBox(width: 12),
                Expanded(
                  child: OutlinedButton(
                    onPressed: onInvite,
                    child: const Text('Invite', textAlign: TextAlign.center),
                  ),
                ),
              ],
            ],
          ),
        ],
      ),
    );
  }
}

class _CreateContestSheet extends ConsumerStatefulWidget {
  const _CreateContestSheet({
    required this.roomId,
    required this.communityMaxMembers,
  });

  final String roomId;
  final int communityMaxMembers;

  @override
  ConsumerState<_CreateContestSheet> createState() => _CreateContestSheetState();
}

class _CreateContestSheetState extends ConsumerState<_CreateContestSheet> {
  static const _joiningPointOptions = <int>[
    500,
    1000,
    2000,
    5000,
    10000,
    20000,
    50000,
  ];

  String? _selectedLeagueKey;
  String? _selectedFixtureId;
  int _selectedJoiningPoints = _joiningPointOptions.first;
  int _selectedWinnerCount = 1;
  int? _selectedMaxSpots;
  bool _loading = false;

  int _minimumSpotsForWinners(int winnerCount) {
    switch (winnerCount) {
      case 2:
        return 5;
      case 3:
        return 10;
      default:
        return 2;
    }
  }

  int _maximumSpotsForWinners(int winnerCount) {
    switch (winnerCount) {
      case 2:
        return 20;
      case 3:
        return 30;
      default:
        return 10;
    }
  }

  List<int> _maxSpotOptions() {
    final minSpots = _minimumSpotsForWinners(_selectedWinnerCount);
    final maxSpots = _maximumSpotsForWinners(_selectedWinnerCount);
    final cappedMax = maxSpots < widget.communityMaxMembers
        ? maxSpots
        : widget.communityMaxMembers;
    if (cappedMax < minSpots) {
      return const <int>[];
    }

    return List<int>.generate(
      cappedMax - minSpots + 1,
      (index) => minSpots + index,
    );
  }

  String _winnerDistributionLabel() {
    switch (_selectedWinnerCount) {
      case 2:
        return '1ST 60% OF 75% | 2ND 40% OF 75%';
      case 3:
        return '1ST 50% OF 75% | 2ND 30% OF 75% | 3RD 20% OF 75%';
      default:
        return 'WINNER TAKES 75% | CFL FEE 25%';
    }
  }

  FixtureSummary? _findFixture(List<FixtureSummary> items, String? fixtureId) {
    for (final fixture in items) {
      if (fixture.id == fixtureId) {
        return fixture;
      }
    }
    return null;
  }

  bool _hasFixtureStarted(FixtureSummary? fixture) {
    if (fixture == null) {
      return false;
    }

    final status = fixture.statusLabel.toUpperCase();
    if (status == 'LIVE' || status == 'COMPLETED' || status == 'CANCELLED') {
      return true;
    }

    final referenceTime = fixture.deadlineTime ?? fixture.startTime;
    return referenceTime != null && !referenceTime.isAfter(DateTime.now());
  }

  @override
  Widget build(BuildContext context) {
    final fixturesValue = ref.watch(upcomingFixturesProvider);

    return Padding(
      padding: EdgeInsets.only(
        left: 16,
        right: 16,
        bottom: MediaQuery.of(context).viewInsets.bottom + 16,
      ),
      child: fixturesValue.when(
        loading: () => const Padding(
          padding: EdgeInsets.all(24),
          child: Center(child: CircularProgressIndicator()),
        ),
        error: (error, _) => Padding(
          padding: const EdgeInsets.all(24),
          child: Text(error.toString().replaceFirst('Exception: ', '')),
        ),
        data: (items) {
          final leagueOptions = _leagueOptions(items);
          if (_selectedLeagueKey == null && leagueOptions.isNotEmpty) {
            _selectedLeagueKey = leagueOptions.first.key;
          }

          final filteredFixtures = items
              .where((fixture) => _leagueKey(fixture) == _selectedLeagueKey)
              .toList();
          if (_selectedFixtureId == null && filteredFixtures.isNotEmpty) {
            _selectedFixtureId = filteredFixtures.first.id;
          }
          final maxSpotOptions = _maxSpotOptions();
          if (_selectedMaxSpots == null ||
              !maxSpotOptions.contains(_selectedMaxSpots)) {
            _selectedMaxSpots =
                maxSpotOptions.isEmpty ? null : maxSpotOptions.first;
          }
          final selectedFixture = _findFixture(items, _selectedFixtureId);

          return Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Create Community Contest',
                style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800),
              ),
              const SizedBox(height: 16),
              DropdownButtonFormField<String>(
                isExpanded: true,
                initialValue: _selectedLeagueKey,
                decoration: const InputDecoration(
                  labelText: 'League',
                  prefixIcon: Icon(Icons.emoji_events_rounded),
                ),
                items: leagueOptions
                    .map(
                      (league) => DropdownMenuItem<String>(
                        value: league.key,
                        child: Text(
                          league.label,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    )
                    .toList(),
                onChanged: _loading
                    ? null
                    : (value) {
                        if (value == null) return;
                        final nextFixtures = items
                            .where((fixture) => _leagueKey(fixture) == value)
                            .toList();
                        setState(() {
                          _selectedLeagueKey = value;
                          _selectedFixtureId =
                              nextFixtures.isEmpty ? null : nextFixtures.first.id;
                        });
                      },
              ),
              const SizedBox(height: 16),
              DropdownButtonFormField<String>(
                isExpanded: true,
                initialValue: _selectedFixtureId,
                decoration: const InputDecoration(
                  labelText: 'Match',
                  prefixIcon: Icon(Icons.sports_cricket_rounded),
                ),
                items: filteredFixtures
                    .map(
                      (fixture) => DropdownMenuItem<String>(
                        value: fixture.id,
                        child: Text(
                          _fixtureLabel(fixture),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    )
                    .toList(),
                onChanged: _loading
                    ? null
                    : (value) => setState(() => _selectedFixtureId = value),
              ),
              const SizedBox(height: 16),
              DropdownButtonFormField<int>(
                initialValue: _selectedJoiningPoints,
                decoration: const InputDecoration(
                  labelText: 'Joining points',
                  prefixIcon: Icon(Icons.stars_rounded),
                ),
                items: _joiningPointOptions
                    .map(
                      (value) => DropdownMenuItem<int>(
                        value: value,
                        child: Text('$value points'),
                      ),
                    )
                    .toList(),
                onChanged: _loading
                    ? null
                    : (value) {
                        if (value != null) {
                          setState(() => _selectedJoiningPoints = value);
                        }
                      },
              ),
              const SizedBox(height: 16),
              DropdownButtonFormField<int>(
                initialValue: _selectedWinnerCount,
                decoration: const InputDecoration(
                  labelText: 'Winner count',
                  prefixIcon: Icon(Icons.emoji_events_outlined),
                ),
                items: const [
                  DropdownMenuItem<int>(value: 1, child: Text('1 winner')),
                  DropdownMenuItem<int>(value: 2, child: Text('2 winners')),
                  DropdownMenuItem<int>(value: 3, child: Text('3 winners')),
                ],
                onChanged: _loading
                    ? null
                    : (value) {
                        if (value == null) return;
                        final nextMin = _minimumSpotsForWinners(value);
                        final nextMax = _maximumSpotsForWinners(value);
                        final cappedMax = nextMax < widget.communityMaxMembers
                            ? nextMax
                            : widget.communityMaxMembers;
                        setState(() {
                          _selectedWinnerCount = value;
                          _selectedMaxSpots =
                              cappedMax < nextMin ? null : nextMin;
                        });
                      },
              ),
              const SizedBox(height: 16),
              DropdownButtonFormField<int>(
                initialValue: _selectedMaxSpots,
                decoration: const InputDecoration(
                  labelText: 'Max spots',
                  prefixIcon: Icon(Icons.groups_rounded),
                ),
                items: maxSpotOptions
                    .map(
                      (value) => DropdownMenuItem<int>(
                        value: value,
                        child: Text('$value spots'),
                      ),
                    )
                    .toList(),
                onChanged: _loading || maxSpotOptions.isEmpty
                    ? null
                    : (value) {
                        if (value != null) {
                          setState(() => _selectedMaxSpots = value);
                        }
                      },
              ),
              const SizedBox(height: 12),
              Text(
                _winnerDistributionLabel(),
                style: const TextStyle(
                  color: Color(0xFFA9B4D0),
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                ),
              ),
              if (maxSpotOptions.isEmpty) ...[
                const SizedBox(height: 8),
                Text(
                  'Increase the community member limit to use this winner format.',
                  style: TextStyle(
                    color: Colors.red.shade300,
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ],
              const SizedBox(height: 20),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: _loading ||
                          _selectedFixtureId == null ||
                          _selectedMaxSpots == null
                      ? null
                      : () async {
                          if (_hasFixtureStarted(selectedFixture)) {
                            ScaffoldMessenger.of(this.context).showSnackBar(
                              const SnackBar(
                                content: Text(
                                  'Match has already started. Contest creation is locked.',
                                ),
                              ),
                            );
                            return;
                          }

                          setState(() => _loading = true);
                          try {
                            await ref
                                .read(roomRepositoryProvider)
                                .createCommunityContest(
                                  roomId: widget.roomId,
                                  fixtureId: _selectedFixtureId!,
                                  joiningPoints: _selectedJoiningPoints,
                                  winnerCount: _selectedWinnerCount,
                                  maxSpots: _selectedMaxSpots!,
                                );
                            ref.invalidate(roomDetailProvider(widget.roomId));
                            ref.invalidate(myWalletProvider);
                            if (!mounted) return;
                            Navigator.of(this.context).pop();
                            ScaffoldMessenger.of(this.context).showSnackBar(
                              SnackBar(
                                content: Text(
                                  'Community contest created. $_selectedJoiningPoints points were deducted and your first spot is reserved.',
                                ),
                              ),
                            );
                          } catch (e) {
                            if (!mounted) return;
                            ScaffoldMessenger.of(this.context).showSnackBar(
                              SnackBar(
                                content: Text(
                                  e.toString().replaceFirst('Exception: ', ''),
                                ),
                              ),
                            );
                          } finally {
                            if (mounted) setState(() => _loading = false);
                          }
                        },
                  child: _loading
                      ? const SizedBox(
                          height: 22,
                          width: 22,
                          child: CircularProgressIndicator(
                            strokeWidth: 2.2,
                            color: Colors.white,
                          ),
                        )
                      : const Text(
                          'Create Contest',
                          textAlign: TextAlign.center,
                        ),
                ),
              ),
            ],
          );
        },
      ),
    );
  }

  List<_LeagueOption> _leagueOptions(List<FixtureSummary> fixtures) {
    final options = <String, _LeagueOption>{};
    for (final fixture in fixtures) {
      final key = _leagueKey(fixture);
      options.putIfAbsent(
        key,
        () => _LeagueOption(key: key, label: _leagueLabel(fixture)),
      );
    }
    return options.values.toList();
  }

  String _leagueKey(FixtureSummary fixture) {
    final externalLeagueId = fixture.externalLeagueId.trim();
    if (externalLeagueId.isNotEmpty) return externalLeagueId;

    final league = fixture.league.trim();
    if (league.isNotEmpty) return league.toLowerCase();

    return 'cricket';
  }

  String _leagueLabel(FixtureSummary fixture) {
    final league = fixture.league.trim();
    if (league.isNotEmpty) return league;
    return 'Cricket League';
  }

  String _fixtureLabel(FixtureSummary fixture) {
    final parts = <String>[fixture.title];
    if (fixture.timeText != '-') {
      parts.add(fixture.timeText);
    }
    if (fixture.venue.trim().isNotEmpty) {
      parts.add(fixture.venue.trim());
    }
    return parts.join(' | ');
  }
}

class _LeagueOption {
  const _LeagueOption({required this.key, required this.label});

  final String key;
  final String label;
}

class _StatusBadge extends StatelessWidget {
  const _StatusBadge({required this.label, required this.color});

  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.14),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: color.withValues(alpha: 0.28)),
      ),
      child: Text(
        label,
        style: TextStyle(color: color, fontWeight: FontWeight.w800),
      ),
    );
  }
}

class _InfoChip extends StatelessWidget {
  const _InfoChip({required this.icon, required this.label, this.onTap});

  final IconData icon;
  final String label;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      borderRadius: BorderRadius.circular(999),
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
        decoration: BoxDecoration(
          color: Colors.white.withValues(alpha: 0.05),
          borderRadius: BorderRadius.circular(999),
          border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 16, color: Colors.white),
            const SizedBox(width: 6),
            Text(label, style: const TextStyle(fontWeight: FontWeight.w700)),
          ],
        ),
      ),
    );
  }
}

class _EditCommunitySheet extends ConsumerStatefulWidget {
  const _EditCommunitySheet({required this.room});

  final Room room;

  @override
  ConsumerState<_EditCommunitySheet> createState() => _EditCommunitySheetState();
}

class _EditCommunitySheetState extends ConsumerState<_EditCommunitySheet> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _nameController;
  late final TextEditingController _maxSpotsController;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    _nameController = TextEditingController(text: widget.room.name);
    _maxSpotsController = TextEditingController(
      text: widget.room.maxMembers.toString(),
    );
  }

  @override
  void dispose() {
    _nameController.dispose();
    _maxSpotsController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();
    if (!_formKey.currentState!.validate()) return;

    final maxSpots = int.tryParse(_maxSpotsController.text.trim());
    if (maxSpots == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Enter a valid max spots value')),
      );
      return;
    }

    setState(() => _saving = true);
    try {
      await ref.read(roomRepositoryProvider).updateRoom(
            roomId: widget.room.id,
            name: _nameController.text.trim(),
            maxMembers: maxSpots,
          );
      if (!mounted) return;
      Navigator.of(context).pop(true);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.toString().replaceFirst('Exception: ', ''))),
      );
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(
        left: 16,
        right: 16,
        bottom: MediaQuery.of(context).viewInsets.bottom + 16,
      ),
      child: Form(
        key: _formKey,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Edit Community',
              style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800),
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: _nameController,
              decoration: const InputDecoration(
                labelText: 'Community name',
                prefixIcon: Icon(Icons.groups_rounded),
              ),
              validator: (value) {
                final trimmed = value?.trim() ?? '';
                if (trimmed.isEmpty) return 'Community name is required';
                return null;
              },
            ),
            const SizedBox(height: 16),
            TextFormField(
              controller: _maxSpotsController,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(
                labelText: 'Max spots',
                prefixIcon: Icon(Icons.confirmation_number_outlined),
              ),
              validator: (value) {
                final number = int.tryParse(value?.trim() ?? '');
                if (number == null) return 'Enter a valid max spots number';
                if (number < 2 || number > 30) {
                  return 'Max spots must be between 2 and 30';
                }
                return null;
              },
            ),
            const SizedBox(height: 20),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: _saving ? null : _submit,
                child: _saving
                    ? const SizedBox(
                        height: 22,
                        width: 22,
                        child: CircularProgressIndicator(
                          strokeWidth: 2.2,
                          color: Colors.white,
                        ),
                      )
                    : const Text(
                        'Save Changes',
                        textAlign: TextAlign.center,
                      ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _FriendInviteSheet extends ConsumerStatefulWidget {
  const _FriendInviteSheet({
    required this.title,
    required this.description,
    required this.emptyMessage,
    required this.onInvite,
    this.eligibleUserIds,
    this.excludedUserIds = const <String>{},
  });

  final String title;
  final String description;
  final String emptyMessage;
  final Future<void> Function(FriendUser friend) onInvite;
  final Set<String>? eligibleUserIds;
  final Set<String> excludedUserIds;

  @override
  ConsumerState<_FriendInviteSheet> createState() => _FriendInviteSheetState();
}

class _FriendInviteSheetState extends ConsumerState<_FriendInviteSheet> {
  final TextEditingController _searchController = TextEditingController();
  String _query = '';
  String? _sendingFriendId;

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _invite(FriendUser friend) async {
    setState(() => _sendingFriendId = friend.id);
    try {
      await widget.onInvite(friend);
      if (!mounted) return;
      Navigator.of(context).pop(true);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.toString().replaceFirst('Exception: ', ''))),
      );
    } finally {
      if (mounted) {
        setState(() => _sendingFriendId = null);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final friendsValue = ref.watch(friendsListProvider);

    return Padding(
      padding: EdgeInsets.only(
        left: 16,
        right: 16,
        bottom: MediaQuery.of(context).viewInsets.bottom + 16,
      ),
      child: friendsValue.when(
        loading: () => const Padding(
          padding: EdgeInsets.all(24),
          child: Center(child: CircularProgressIndicator()),
        ),
        error: (error, _) => Padding(
          padding: const EdgeInsets.all(8),
          child: Text(error.toString().replaceFirst('Exception: ', '')),
        ),
        data: (friends) {
          final filteredFriends = friends.where((friend) {
            if (widget.excludedUserIds.contains(friend.id)) {
              return false;
            }
            if (widget.eligibleUserIds != null &&
                !widget.eligibleUserIds!.contains(friend.id)) {
              return false;
            }
            final query = _query.trim().toLowerCase();
            if (query.isEmpty) {
              return true;
            }
            return friend.username.toLowerCase().contains(query) ||
                friend.fullName.toLowerCase().contains(query);
          }).toList()
            ..sort((a, b) => a.username.toLowerCase().compareTo(b.username.toLowerCase()));

          return Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                widget.title,
                style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800),
              ),
              const SizedBox(height: 8),
              Text(
                widget.description,
                style: const TextStyle(color: Color(0xFFA9B4D0), height: 1.45),
              ),
              const SizedBox(height: 16),
              TextField(
                controller: _searchController,
                onChanged: (value) => setState(() => _query = value),
                decoration: const InputDecoration(
                  labelText: 'Search friend',
                  prefixIcon: Icon(Icons.search_rounded),
                ),
              ),
              const SizedBox(height: 16),
              if (filteredFriends.isEmpty)
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: Text(
                    widget.emptyMessage,
                    style: const TextStyle(
                      color: Color(0xFFA9B4D0),
                      height: 1.45,
                    ),
                  ),
                )
              else
                ConstrainedBox(
                  constraints: const BoxConstraints(maxHeight: 360),
                  child: ListView.separated(
                    shrinkWrap: true,
                    itemCount: filteredFriends.length,
                    separatorBuilder: (_, _) => const SizedBox(height: 10),
                    itemBuilder: (context, index) {
                      final friend = filteredFriends[index];
                      final sending = _sendingFriendId == friend.id;
                      return Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: Colors.white.withValues(alpha: 0.04),
                          borderRadius: BorderRadius.circular(16),
                          border: Border.all(
                            color: Colors.white.withValues(alpha: 0.08),
                          ),
                        ),
                        child: Row(
                          children: [
                            CircleAvatar(
                              backgroundColor: Colors.white12,
                              child: Text(
                                friend.username.isEmpty
                                    ? '?'
                                    : friend.username.substring(0, 1).toUpperCase(),
                              ),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    friend.fullName,
                                    style: const TextStyle(
                                      fontWeight: FontWeight.w700,
                                    ),
                                  ),
                                  const SizedBox(height: 2),
                                  Text(
                                    '@${friend.username}',
                                    style: const TextStyle(
                                      color: Color(0xFFA9B4D0),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            const SizedBox(width: 12),
                            SizedBox(
                              width: 104,
                              child: ElevatedButton(
                                onPressed: sending ? null : () => _invite(friend),
                                child: sending
                                    ? const SizedBox(
                                        height: 20,
                                        width: 20,
                                        child: CircularProgressIndicator(
                                          strokeWidth: 2.2,
                                          color: Colors.white,
                                        ),
                                      )
                                    : const Text(
                                        'Invite',
                                        textAlign: TextAlign.center,
                                      ),
                              ),
                            ),
                          ],
                        ),
                      );
                    },
                  ),
                ),
            ],
          );
        },
      ),
    );
  }
}

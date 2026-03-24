import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../app/widgets/primary_scaffold.dart';
import '../../../../core/widgets/async_value_view.dart';
import '../../../../core/widgets/live_match_scorecard.dart';
import '../../../../core/widgets/section_card.dart';
import '../../../fixture/data/fixture_repository.dart';
import '../../../fixture/domain/models/fixture_models.dart';
import '../../../team/data/team_repository.dart';
import '../../../team/domain/models/team_models.dart';
import '../../../wallet/data/wallet_repository.dart';
import '../../data/contest_repository.dart';
import '../../domain/models/cricket_rules.dart';
import '../../domain/models/contest_models.dart';

class ContestDetailScreen extends ConsumerStatefulWidget {
  const ContestDetailScreen({
    super.key,
    required this.fixtureId,
    required this.contestId,
  });
  final String fixtureId;
  final String contestId;

  @override
  ConsumerState<ContestDetailScreen> createState() =>
      _ContestDetailScreenState();
}

class _ContestDetailScreenState extends ConsumerState<ContestDetailScreen> {
  static const _liveRefreshInterval = Duration(seconds: 30);

  Timer? _autoRefreshTimer;

  @override
  void initState() {
    super.initState();
    _autoRefreshTimer = Timer.periodic(_liveRefreshInterval, (_) {
      if (!mounted) return;

      final contest = ref.read(contestDetailProvider(widget.contestId));
      final shouldRefresh = contest.maybeWhen(
        data: (value) => _shouldAutoRefresh(value.contest),
        orElse: () => true,
      );

      if (shouldRefresh) {
        ref.invalidate(contestDetailProvider(widget.contestId));
        ref.invalidate(leaderboardProvider(widget.contestId));
        ref.invalidate(myContestEntriesProvider(widget.contestId));
        ref.invalidate(fixtureDetailProvider(widget.fixtureId));
      }
    });
  }

  @override
  void dispose() {
    _autoRefreshTimer?.cancel();
    super.dispose();
  }

  bool _shouldAutoRefresh(ContestSummary contest) =>
      contest.status != 'COMPLETED' && contest.status != 'CANCELLED';

  bool _contestCanAcceptNewEntry(ContestSummary contest) {
    final full = contest.spots > 0 && contest.joined >= contest.spots;
    return contest.status == 'OPEN' && !full;
  }

  bool _hasFixtureStarted(FixtureDetail? fixture) {
    if (fixture == null) return false;

    final status = fixture.summary.statusLabel.toUpperCase();
    if (status == 'LIVE' || status == 'COMPLETED' || status == 'CANCELLED') {
      return true;
    }

    final referenceTime =
        fixture.summary.deadlineTime ?? fixture.summary.startTime;
    return referenceTime != null && !referenceTime.isAfter(DateTime.now());
  }

  Future<void> _refreshContestData() async {
    ref.invalidate(contestDetailProvider(widget.contestId));
    ref.invalidate(leaderboardProvider(widget.contestId));
    ref.invalidate(myContestEntriesProvider(widget.contestId));
    ref.invalidate(fixtureDetailProvider(widget.fixtureId));
    ref.invalidate(myTeamsProvider(widget.fixtureId));
    await Future.wait([
      ref.read(contestDetailProvider(widget.contestId).future),
      ref.read(leaderboardProvider(widget.contestId).future),
      ref.read(myContestEntriesProvider(widget.contestId).future),
      ref.read(myTeamsProvider(widget.fixtureId).future),
    ]);
  }

  void _showMessage(String message) {
    if (!mounted) return;
    final messenger = ScaffoldMessenger.of(context);
    messenger.hideCurrentSnackBar();
    messenger.showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _deleteTeam(String teamId) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Delete team?'),
        content: const Text(
          'This removes the team permanently. Joined teams cannot be deleted.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );

    if (confirmed != true) {
      return;
    }

    try {
      await ref.read(teamRepositoryProvider).deleteTeam(teamId);
      await _refreshContestData();
      _showMessage('Team deleted successfully');
    } catch (e) {
      _showMessage(e.toString().replaceFirst('Exception: ', ''));
    }
  }

  String _teamBuilderRoute({
    String? teamId,
    bool attachToContest = true,
  }) {
    final queryParameters = <String, String>{};
    if (teamId != null && teamId.isNotEmpty) {
      queryParameters['teamId'] = teamId;
    }
    if (attachToContest) {
      queryParameters['contestId'] = widget.contestId;
    }

    return Uri(
      path: '/fixtures/${widget.fixtureId}/team',
      queryParameters: queryParameters.isEmpty ? null : queryParameters,
    ).toString();
  }

  Set<String> _joinedTeamIds(List<ContestEntrySummary> entries) => entries
      .where((entry) => entry.hasSelectedTeam)
      .map((entry) => entry.teamId!)
      .toSet();

  bool _hasReservedSpot(List<ContestEntrySummary> entries) =>
      entries.isNotEmpty && !entries.any((entry) => entry.hasSelectedTeam);

  String _entryFeeLabel(double points) => '${points.toStringAsFixed(0)} pts';

  String _joinUnavailableMessage(ContestSummary contest) {
    final isFull = contest.spots > 0 && contest.joined >= contest.spots;
    if (isFull) {
      return 'Contest is full.';
    }

    return switch (contest.status) {
      'LIVE' => 'Match is live. You can no longer join this contest.',
      'COMPLETED' => 'Contest is already completed.',
      'CANCELLED' => 'Contest has been cancelled.',
      _ => 'Contest is not open for joining.',
    };
  }

  Future<void> _openTeamBuilder(
    BuildContext parentContext, {
    String? teamId,
    bool attachToContest = true,
  }) async {
    await GoRouter.of(parentContext).push<bool>(
      _teamBuilderRoute(teamId: teamId, attachToContest: attachToContest),
    );
    if (!mounted) return;
    ref.invalidate(myTeamsProvider(widget.fixtureId));
    await _refreshContestData();
  }

  Future<void> _onJoinClick(
    ContestDetail detail, {
    required List<FantasyTeam> teams,
    required List<ContestEntrySummary> myEntries,
  }) async {
    final fixtureValue = await ref.read(
      fixtureDetailProvider(widget.fixtureId).future,
    );
    final now = DateTime.now();
    final referenceTime =
        fixtureValue.summary.deadlineTime ?? fixtureValue.summary.startTime;
    if (referenceTime != null && referenceTime.isBefore(now)) {
      if (!mounted) return;
      _showMessage('Match has already started. Cannot join contest.');
      return;
    }

    final hasReservedSpot = _hasReservedSpot(myEntries);
    if (!hasReservedSpot && !_contestCanAcceptNewEntry(detail.contest)) {
      _showMessage(_joinUnavailableMessage(detail.contest));
      return;
    }

    if (!mounted) return;

    if (teams.isEmpty) {
      _showCreateTeamDialog();
      return;
    }

    final joinedTeamIds = _joinedTeamIds(myEntries);
    final availableTeams = teams
        .where((team) => !joinedTeamIds.contains(team.id))
        .toList();
    if (availableTeams.isEmpty) {
      _showNoJoinableTeamDialog();
      return;
    }

    if (!hasReservedSpot) {
      final wallet = await ref.read(myWalletProvider.future);
      if (!mounted) return;
      if (wallet.balance < detail.contest.entryFee) {
        _showLowBalanceDialog();
        return;
      }
    }

    if (!mounted) return;
    await _showJoinDialog(
      teams: availableTeams,
      entryFee: detail.contest.entryFee,
      hasReservedSpot: hasReservedSpot,
    );
  }

  void _showLowBalanceDialog() {
    final parentContext = context;
    showDialog(
      context: parentContext,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Insufficient Balance'),
        content: const Text(
          'You do not have enough points to join this contest. Add more points to your wallet before trying again.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: const Text('Close'),
          ),
          ElevatedButton(
            onPressed: () {
              Navigator.pop(dialogContext);
              if (!mounted) return;
              GoRouter.of(parentContext).go('/wallet');
            },
            child: const Text('Open Wallet'),
          ),
        ],
      ),
    );
  }

  void _showCreateTeamDialog() {
    final parentContext = context;
    showDialog(
      context: parentContext,
      builder: (dialogContext) => AlertDialog(
        title: const Text('No Team Found'),
        content: const Text(
          'You need to create a team before joining a contest.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () async {
              Navigator.pop(dialogContext);
              if (!mounted) return;
              await _openTeamBuilder(parentContext, attachToContest: false);
            },
            child: const Text('Create Team'),
          ),
        ],
      ),
    );
  }

  void _showNoJoinableTeamDialog() {
    final parentContext = context;
    showDialog(
      context: parentContext,
      builder: (dialogContext) => AlertDialog(
        title: const Text('All Teams Already Joined'),
        content: const Text(
          'Each of your current teams is already joined in this contest. Create one more team to join again.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: const Text('Close'),
          ),
          ElevatedButton(
            onPressed: () async {
              Navigator.pop(dialogContext);
              if (!mounted) return;
              await _openTeamBuilder(parentContext, attachToContest: false);
            },
            child: const Text('Create More Team'),
          ),
        ],
      ),
    );
  }

  Future<void> _showJoinDialog({
    required List<FantasyTeam> teams,
    required double entryFee,
    required bool hasReservedSpot,
  }) async {
    final parentContext = context;
    var selectedTeamId = teams.first.id;
    var submitting = false;

    await showDialog<void>(
      context: parentContext,
      builder: (dialogContext) => StatefulBuilder(
        builder: (dialogContext, setDialogState) => AlertDialog(
          title: const Text('Join Contest'),
          content: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 420),
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('Select the team you want to use for this contest.'),
                  const SizedBox(height: 12),
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: const Color(0xFF0F1A2D),
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            const Expanded(
                              child: Text(
                                'Entry fee',
                                style: TextStyle(
                                  fontWeight: FontWeight.w700,
                                  color: Color(0xFFA9B4D0),
                                ),
                              ),
                            ),
                            Text(
                              _entryFeeLabel(entryFee),
                              style: const TextStyle(
                                fontWeight: FontWeight.w900,
                                fontSize: 16,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 6),
                        Text(
                          hasReservedSpot
                              ? 'Your contest spot is already reserved, so no extra points will be deducted.'
                              : 'This amount will be deducted from your wallet when you join.',
                          style: const TextStyle(
                            color: Color(0xFFA9B4D0),
                            height: 1.35,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 12),
                  RadioGroup<String>(
                    groupValue: selectedTeamId,
                    onChanged: (value) {
                      if (submitting || value == null) return;
                      setDialogState(() => selectedTeamId = value);
                    },
                    child: Column(
                      children: teams
                          .map(
                            (team) => Container(
                              margin: const EdgeInsets.only(bottom: 10),
                              decoration: BoxDecoration(
                                borderRadius: BorderRadius.circular(16),
                                border: Border.all(
                                  color: selectedTeamId == team.id
                                      ? const Color(0xFF63D9FF)
                                      : Colors.white.withValues(alpha: 0.08),
                                ),
                                color: Colors.white.withValues(alpha: 0.04),
                              ),
                              child: RadioListTile<String>(
                                value: team.id,
                                activeColor: const Color(0xFF63D9FF),
                                contentPadding: const EdgeInsets.symmetric(
                                  horizontal: 12,
                                  vertical: 4,
                                ),
                                title: Text(
                                  team.name,
                                  style: const TextStyle(
                                    fontWeight: FontWeight.w800,
                                  ),
                                ),
                                subtitle: Text(
                                  '${team.playerIds.length} players',
                                ),
                              ),
                            ),
                          )
                          .toList(),
                    ),
                  ),
                ],
              ),
            ),
          ),
          actions: [
            TextButton(
              onPressed: submitting
                  ? null
                  : () => Navigator.of(dialogContext).pop(),
              child: const Text('Cancel'),
            ),
            ElevatedButton(
              onPressed: submitting
                  ? null
                  : () async {
                      setDialogState(() => submitting = true);
                      try {
                        await ref
                            .read(contestRepositoryProvider)
                            .joinContest(
                              contestId: widget.contestId,
                              teamId: selectedTeamId,
                            );
                        if (!mounted || !dialogContext.mounted) return;
                        Navigator.of(dialogContext).pop();
                        await _refreshContestData();
                        ref.invalidate(myWalletProvider);
                        _showMessage('Contest joined successfully');
                      } catch (e) {
                        if (!mounted) return;
                        setDialogState(() => submitting = false);
                        _showMessage(
                          e.toString().replaceFirst('Exception: ', ''),
                        );
                      }
                    },
              child: submitting
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Text('Join Contest'),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _showTeamPreview(LeaderboardEntry entry) async {
    if (entry.teamId == null || entry.teamId!.isEmpty) return;

    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      backgroundColor: Colors.transparent,
      builder: (_) => FractionallySizedBox(
        heightFactor: 0.94,
        child: _ContestTeamPreviewSheet(
          contestId: widget.contestId,
          teamId: entry.teamId!,
          ownerName: entry.username,
          teamName: entry.teamName,
        ),
      ),
    );
  }

  void _showRulesSheet(FixtureDetail? fixture) {
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      backgroundColor: Colors.transparent,
      builder: (_) => FractionallySizedBox(
        heightFactor: 0.93,
        child: _ContestRulesSheet(
          initialRuleSet: resolveCricketRuleSet(fixture?.format ?? 'T20'),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final contestValue = ref.watch(contestDetailProvider(widget.contestId));
    final leaderboardValue = ref.watch(leaderboardProvider(widget.contestId));
    final myEntriesValue = ref.watch(myContestEntriesProvider(widget.contestId));
    final fixtureValue = ref.watch(fixtureDetailProvider(widget.fixtureId));
    final myTeamsValue = ref.watch(myTeamsProvider(widget.fixtureId));

    return PrimaryScaffold(
      currentIndex: -1,
      title: 'Contest Detail',
      actions: [
        IconButton(
          onPressed: () => _showRulesSheet(fixtureValue.asData?.value),
          icon: Container(
            width: 36,
            height: 36,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: Colors.white.withValues(alpha: 0.08),
              border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
            ),
            alignment: Alignment.center,
            child: const Icon(Icons.info_outline_rounded, size: 19),
          ),
          tooltip: 'Scoring Rules',
        ),
        IconButton(
          onPressed: _refreshContestData,
          icon: const Icon(Icons.refresh_rounded),
        ),
      ],
      body: AsyncValueView(
        value: contestValue,
        onRetry: _refreshContestData,
        data: (data) {
          final contest = data.contest;
          final fixture = fixtureValue.asData?.value;
          final fixtureStarted = _hasFixtureStarted(fixture);
          final myEntries =
              myEntriesValue.asData?.value ?? const <ContestEntrySummary>[];
          final myTeams = myTeamsValue.asData?.value ?? const <FantasyTeam>[];
          final selectedEntries =
              myEntries.where((entry) => entry.hasSelectedTeam).toList();
          final joinedTeamIds = _joinedTeamIds(myEntries);
          final hasReservedSpot = _hasReservedSpot(myEntries);
          final canCreateTeam = fixture == null || !fixtureStarted;
          final contestStarted =
              contest.status == 'LIVE' ||
              contest.status == 'COMPLETED' ||
              fixtureStarted;
          final canJoinContest =
              myTeams.isNotEmpty &&
              (hasReservedSpot || _contestCanAcceptNewEntry(contest));
          final canViewTeams =
              myEntries.isNotEmpty &&
              (contestStarted || fixtureStarted);

          return RefreshIndicator(
            onRefresh: _refreshContestData,
            child: ListView(
              padding: const EdgeInsets.only(top: 8, bottom: 100),
              children: [
                _ContestHeroCard(
                  contest: contest,
                  detail: data,
                  fixture: fixture,
                  myTeamsCount: myTeamsValue.hasValue
                      ? myTeams.length
                      : selectedEntries.length,
                ),
                if (!fixtureStarted)
                  SectionCard(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'Your Teams',
                          style: TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          myTeams.isEmpty
                              ? 'Create your first team for this fixture, then use Join Contest to enter with the team you want.'
                              : 'Edit any team here, create more teams, and join this contest with the team you choose.',
                          style: const TextStyle(
                            color: Color(0xFFA9B4D0),
                            height: 1.45,
                          ),
                        ),
                        const SizedBox(height: 14),
                        if (myTeamsValue.isLoading && !myTeamsValue.hasValue)
                          const Center(child: CircularProgressIndicator())
                        else if (myTeamsValue.hasError && !myTeamsValue.hasValue)
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                myTeamsValue.asError?.error
                                        .toString()
                                        .replaceFirst('Exception: ', '') ??
                                    'Unable to load teams.',
                              ),
                              const SizedBox(height: 12),
                              OutlinedButton(
                                onPressed: () => ref.invalidate(
                                  myTeamsProvider(widget.fixtureId),
                                ),
                                child: const Text('Retry'),
                              ),
                            ],
                          )
                        else if (myTeams.isEmpty)
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Text(
                                'No team created for this fixture yet.',
                                style: TextStyle(fontWeight: FontWeight.w700),
                              ),
                              const SizedBox(height: 12),
                              SizedBox(
                                width: double.infinity,
                                child: ElevatedButton(
                                  onPressed: canCreateTeam
                                      ? () => _openTeamBuilder(
                                            context,
                                            attachToContest: false,
                                          )
                                      : null,
                                  style: ElevatedButton.styleFrom(
                                    backgroundColor: const Color(0xFFE95858),
                                    foregroundColor: Colors.white,
                                    padding: const EdgeInsets.symmetric(
                                      vertical: 14,
                                    ),
                                  ),
                                  child: const Text(
                                    'CREATE TEAM',
                                    style: TextStyle(
                                      fontWeight: FontWeight.w900,
                                    ),
                                  ),
                                ),
                              ),
                            ],
                          )
                        else
                          ...myTeams.map(
                            (team) => Padding(
                              padding: const EdgeInsets.only(bottom: 10),
                              child: _MyTeamTile(
                                team: team,
                                isJoined: joinedTeamIds.contains(team.id),
                                canEdit: canCreateTeam && !team.isLocked,
                                canDelete: team.canDelete,
                                onEdit: () => _openTeamBuilder(
                                  context,
                                  teamId: team.id,
                                  attachToContest: false,
                                ),
                                onDelete: () => _deleteTeam(team.id),
                              ),
                            ),
                          ),
                        if (myTeams.isNotEmpty) ...[
                          const SizedBox(height: 10),
                          Row(
                            children: [
                              Expanded(
                                child: OutlinedButton(
                                  onPressed: canCreateTeam
                                      ? () => _openTeamBuilder(
                                            context,
                                            attachToContest: false,
                                          )
                                      : null,
                                  child: const Text('CREATE MORE TEAM'),
                                ),
                              ),
                              const SizedBox(width: 12),
                              Expanded(
                                child: ElevatedButton(
                                  onPressed: myEntriesValue.isLoading &&
                                          !myEntriesValue.hasValue
                                      ? null
                                      : canJoinContest
                                      ? () => _onJoinClick(
                                            data,
                                            teams: myTeams,
                                            myEntries: myEntries,
                                          )
                                      : null,
                                  child: const Text('JOIN CONTEST'),
                                ),
                              ),
                            ],
                          ),
                          if (!canJoinContest && !hasReservedSpot)
                            Padding(
                              padding: const EdgeInsets.only(top: 10),
                              child: Text(
                                _joinUnavailableMessage(contest),
                                style: const TextStyle(
                                  color: Color(0xFFA9B4D0),
                                  height: 1.35,
                                ),
                              ),
                            ),
                        ],
                      ],
                    ),
                  ),
                if (fixture?.liveData != null)
                  LiveMatchScorecard(
                    liveData: fixture!.liveData!,
                    title: 'Scoreboard',
                  ),
                if (data.prizes.isNotEmpty)
                  SectionCard(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'Prize Map',
                          style: TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.w800,
                          ),
                        ),
                        const SizedBox(height: 14),
                        ...data.prizes.map(
                          (prize) => Padding(
                            padding: const EdgeInsets.only(bottom: 10),
                            child: _PrizeTile(prize: prize),
                          ),
                        ),
                      ],
                    ),
                  ),
                SectionCard(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          const Expanded(
                            child: Text(
                              'Contest Scoreboard',
                              style: TextStyle(
                                fontSize: 18,
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                          ),
                          if (_shouldAutoRefresh(contest))
                            const _MiniPill(label: 'Live 30s'),
                        ],
                      ),
                      const SizedBox(height: 8),
                      Text(
                        canViewTeams
                            ? 'The match has started, so you can open any listed team in playground view.'
                            : 'Join this contest to unlock participant team views after the match starts.',
                        style: const TextStyle(
                          color: Color(0xFFA9B4D0),
                          height: 1.45,
                        ),
                      ),
                      const SizedBox(height: 14),
                      leaderboardValue.when(
                        data: (items) {
                          if (items.isEmpty) {
                            return const Text('No scoreboard entries yet.');
                          }
                          return Column(
                            children: items
                                .map(
                                  (entry) => Padding(
                                    padding: const EdgeInsets.only(bottom: 10),
                                    child: _LeaderboardTile(
                                      entry: entry,
                                      canViewTeam: canViewTeams &&
                                          entry.teamId != null &&
                                          entry.teamId!.isNotEmpty,
                                      onViewTeam: () => _showTeamPreview(entry),
                                    ),
                                  ),
                                )
                                .toList(),
                          );
                        },
                        loading: () =>
                            const Center(child: CircularProgressIndicator()),
                        error: (e, _) {
                          final message = e.toString().replaceFirst(
                            'Exception: ',
                            '',
                          );
                          final friendlyMessage = message.contains(
                                'Too Many Attempts',
                              )
                              ? 'Live scoreboard is syncing right now. Please refresh again in a few seconds.'
                              : message;
                          return Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                friendlyMessage,
                                style: const TextStyle(
                                  color: Color(0xFFA9B4D0),
                                  height: 1.45,
                                ),
                              ),
                              const SizedBox(height: 12),
                              OutlinedButton.icon(
                                onPressed: _refreshContestData,
                                icon: const Icon(Icons.refresh_rounded),
                                label: const Text('Refresh Scoreboard'),
                              ),
                            ],
                          );
                        },
                      ),
                    ],
                  ),
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}

class _ContestStatusPill extends StatelessWidget {
  const _ContestStatusPill({required this.status});

  final String status;

  @override
  Widget build(BuildContext context) {
    final color = switch (status) {
      'LIVE' => const Color(0xFFFF7A59),
      'COMPLETED' => const Color(0xFF39E48A),
      'CANCELLED' => const Color(0xFFFFD36A),
      _ => const Color(0xFF63D9FF),
    };

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.14),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: color.withValues(alpha: 0.28)),
      ),
      child: Text(
        status,
        style: TextStyle(color: color, fontWeight: FontWeight.w800),
      ),
    );
  }
}

class _ContestHeroCard extends StatelessWidget {
  const _ContestHeroCard({
    required this.contest,
    required this.detail,
    required this.fixture,
    required this.myTeamsCount,
  });

  final ContestSummary contest;
  final ContestDetail detail;
  final FixtureDetail? fixture;
  final int myTeamsCount;

  @override
  Widget build(BuildContext context) {
    final summary = fixture?.summary;
    final innings = fixture?.liveData?.innings ?? const <FixtureInningsScore>[];
    final topPrize = detail.firstPrize.isEmpty
        ? '${contest.prizePool.toStringAsFixed(0)} pts'
        : '${detail.firstPrize} pts';

    return Container(
      margin: const EdgeInsets.fromLTRB(16, 8, 16, 8),
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(28),
        gradient: const LinearGradient(
          colors: [Color(0xFF17305C), Color(0xFF101E3A), Color(0xFF09111F)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
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
                      contest.name,
                      style: const TextStyle(
                        fontSize: 25,
                        fontWeight: FontWeight.w900,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      summary == null
                          ? 'Fixture details are loading...'
                          : '${summary.teamAShort} vs ${summary.teamBShort} - ${summary.timeText}',
                      style: const TextStyle(
                        color: Color(0xFFA9B4D0),
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    if (innings.isNotEmpty) ...[
                      const SizedBox(height: 10),
                      ...innings.map(
                        (item) => Padding(
                          padding: const EdgeInsets.only(bottom: 4),
                          child: Text(
                            '${item.shortName} - ${item.scoreline}${item.oversText.isEmpty ? '' : ' ${item.oversText}'}',
                            style: TextStyle(
                              color: item.current
                                  ? const Color(0xFF39E48A)
                                  : const Color(0xFFE7EDF8),
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                        ),
                      ),
                    ],
                  ],
                ),
              ),
              _ContestStatusPill(status: contest.status),
            ],
          ),
          const SizedBox(height: 16),
          Wrap(
            spacing: 10,
            runSpacing: 10,
            children: [
              _MiniPill(label: '$myTeamsCount teams'),
              _MiniPill(label: '${contest.joined}/${contest.spots} members'),
              _MiniPill(label: 'Top prize $topPrize'),
            ],
          ),
          const SizedBox(height: 16),
          Wrap(
            spacing: 12,
            runSpacing: 12,
            children: [
              _MetricTile(
                label: 'Entry fee',
                value: '${contest.entryFee.toStringAsFixed(0)} pts',
              ),
              _MetricTile(
                label: 'Prize pool',
                value: '${contest.prizePool.toStringAsFixed(0)} pts',
              ),
              _MetricTile(
                label: 'Winners',
                value: '${contest.winnersCount}',
              ),
              _MetricTile(label: 'Joined', value: '${contest.joined}'),
            ],
          ),
        ],
      ),
    );
  }
}

class _MetricTile extends StatelessWidget {
  const _MetricTile({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 144,
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(18),
        color: Colors.white.withValues(alpha: 0.06),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: const TextStyle(color: Color(0xFFA9B4D0), fontSize: 12),
          ),
          const SizedBox(height: 6),
          Text(
            value,
            style: const TextStyle(fontWeight: FontWeight.w900, fontSize: 17),
          ),
        ],
      ),
    );
  }
}

class _MiniPill extends StatelessWidget {
  const _MiniPill({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(999),
        color: Colors.white.withValues(alpha: 0.08),
      ),
      child: Text(
        label,
        style: const TextStyle(fontWeight: FontWeight.w700),
      ),
    );
  }
}

class _MyTeamTile extends StatelessWidget {
  const _MyTeamTile({
    required this.team,
    required this.isJoined,
    required this.canEdit,
    required this.canDelete,
    required this.onEdit,
    required this.onDelete,
  });

  final FantasyTeam team;
  final bool isJoined;
  final bool canEdit;
  final bool canDelete;
  final VoidCallback onEdit;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    final statusColor = isJoined
        ? const Color(0xFF39E48A)
        : team.isLocked
        ? const Color(0xFFFFD36A)
        : const Color(0xFF63D9FF);
    final statusLabel = isJoined
        ? 'Joined'
        : team.isLocked
        ? 'Locked'
        : 'Ready';
    final subtitle = isJoined
        ? 'Already joined in this contest.'
        : team.isLocked
        ? 'This team is locked for edits.'
        : '${team.playerIds.length} players selected.';
    final captainMatches = team.players.where((player) => player.isCaptain);
    final viceCaptainMatches = team.players.where(
      (player) => player.isViceCaptain,
    );
    final captain = captainMatches.isEmpty ? null : captainMatches.first;
    final viceCaptain = viceCaptainMatches.isEmpty
        ? null
        : viceCaptainMatches.first;
    final wkCount = team.players.where((player) => player.roleCode == 'WK').length;
    final batCount = team.players
        .where((player) => player.roleCode == 'BAT')
        .length;
    final arCount = team.players.where((player) => player.roleCode == 'AR').length;
    final bowlCount = team.players
        .where((player) => player.roleCode == 'BOWL')
        .length;

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(24),
        color: Colors.white.withValues(alpha: 0.04),
        border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  team.name,
                  style: const TextStyle(
                    fontWeight: FontWeight.w900,
                    fontSize: 18,
                  ),
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 10,
                  vertical: 6,
                ),
                decoration: BoxDecoration(
                  color: statusColor.withValues(alpha: 0.14),
                  borderRadius: BorderRadius.circular(999),
                ),
                child: Text(
                  statusLabel,
                  style: TextStyle(
                    color: statusColor,
                    fontWeight: FontWeight.w800,
                    fontSize: 12,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          Text(
            subtitle,
            style: const TextStyle(
              color: Color(0xFFA9B4D0),
              height: 1.35,
            ),
          ),
          const SizedBox(height: 12),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(20),
              gradient: const LinearGradient(
                colors: [Color(0xFF27703E), Color(0xFF17522D), Color(0xFF0E3520)],
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
              ),
              border: Border.all(color: Colors.white.withValues(alpha: 0.12)),
            ),
            child: Column(
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '${team.players.length}/${team.playerIds.isEmpty ? 11 : team.playerIds.length}',
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 28,
                              fontWeight: FontWeight.w900,
                            ),
                          ),
                          Text(
                            'Players ready',
                            style: TextStyle(
                              color: Colors.white.withValues(alpha: 0.76),
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ],
                      ),
                    ),
                    if (captain != null)
                      _TeamSpotlightPlayer(
                        player: captain,
                        badge: 'C',
                        title: 'Captain',
                        badgeColor: const Color(0xFF2F3443),
                      ),
                    if (captain != null && viceCaptain != null)
                      const SizedBox(width: 10),
                    if (viceCaptain != null)
                      _TeamSpotlightPlayer(
                        player: viceCaptain,
                        badge: 'VC',
                        title: 'Vice Captain',
                        badgeColor: const Color(0xFF2F3443),
                      ),
                  ],
                ),
                const SizedBox(height: 14),
                Row(
                  children: [
                    Expanded(child: _TeamRoleStat(label: 'WK', count: wkCount)),
                    Expanded(child: _TeamRoleStat(label: 'BAT', count: batCount)),
                    Expanded(child: _TeamRoleStat(label: 'AR', count: arCount)),
                    Expanded(
                      child: _TeamRoleStat(label: 'BOWL', count: bowlCount),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: SizedBox(
                  height: 44,
                  child: OutlinedButton(
                    onPressed: canDelete ? onDelete : null,
                    style: OutlinedButton.styleFrom(
                      foregroundColor: const Color(0xFFFF8A6B),
                      side: BorderSide(
                        color: canDelete
                            ? const Color(0x66FF8A6B)
                            : Colors.white.withValues(alpha: 0.08),
                      ),
                    ),
                    child: const Text('DELETE TEAM'),
                  ),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: SizedBox(
                  height: 44,
                  child: OutlinedButton(
                    onPressed: canEdit ? onEdit : null,
                    child: const Text('EDIT TEAM'),
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _TeamRoleStat extends StatelessWidget {
  const _TeamRoleStat({
    required this.label,
    required this.count,
  });

  final String label;
  final int count;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Text(
          '$count',
          style: const TextStyle(
            color: Colors.white,
            fontSize: 20,
            fontWeight: FontWeight.w900,
          ),
        ),
        const SizedBox(height: 2),
        Text(
          label,
          style: TextStyle(
            color: Colors.white.withValues(alpha: 0.78),
            fontSize: 12,
            fontWeight: FontWeight.w700,
          ),
        ),
      ],
    );
  }
}

class _TeamSpotlightPlayer extends StatelessWidget {
  const _TeamSpotlightPlayer({
    required this.player,
    required this.badge,
    required this.title,
    required this.badgeColor,
  });

  final FantasyTeamPlayer player;
  final String badge;
  final String title;
  final Color badgeColor;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 88,
      child: Column(
        children: [
          Stack(
            clipBehavior: Clip.none,
            children: [
              _TeamAvatarThumb(player: player),
              Positioned(
                top: -5,
                right: -4,
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 7,
                    vertical: 4,
                  ),
                  decoration: BoxDecoration(
                    color: badgeColor,
                    borderRadius: BorderRadius.circular(999),
                    border: Border.all(color: Colors.white, width: 1.1),
                  ),
                  child: Text(
                    badge,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 10,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
            decoration: BoxDecoration(
              color: const Color(0xFF2F3443),
              borderRadius: BorderRadius.circular(8),
            ),
            child: Text(
              player.playerName,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              textAlign: TextAlign.center,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 11,
                fontWeight: FontWeight.w800,
              ),
            ),
          ),
          const SizedBox(height: 5),
          Text(
            title,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            textAlign: TextAlign.center,
            style: TextStyle(
              color: Colors.white.withValues(alpha: 0.76),
              fontSize: 11,
              fontWeight: FontWeight.w700,
            ),
          ),
        ],
      ),
    );
  }
}

class _TeamAvatarThumb extends StatelessWidget {
  const _TeamAvatarThumb({
    required this.player,
  });

  final FantasyTeamPlayer player;

  @override
  Widget build(BuildContext context) {
    final name = player.playerName.trim();
    final initial = name.isEmpty ? '?' : name.substring(0, 1).toUpperCase();

    return CircleAvatar(
      radius: 28,
      backgroundColor: const Color(0xFF17202F),
      backgroundImage: (player.imageUrl ?? '').trim().isEmpty
          ? null
          : NetworkImage(player.imageUrl!),
      child: (player.imageUrl ?? '').trim().isEmpty
          ? Text(
              initial,
              style: const TextStyle(
                color: Colors.white,
                fontWeight: FontWeight.w900,
              ),
            )
          : null,
    );
  }
}

class _PrizeTile extends StatelessWidget {
  const _PrizeTile({required this.prize});

  final PrizeBreakdown prize;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(18),
        color: Colors.white.withValues(alpha: 0.05),
      ),
      child: Row(
        children: [
          const Icon(Icons.workspace_premium_rounded, color: Color(0xFFFFD36A)),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              prize.rank,
              style: const TextStyle(fontWeight: FontWeight.w800),
            ),
          ),
          Text(
            prize.prize,
            style: const TextStyle(
              color: Color(0xFF39E48A),
              fontWeight: FontWeight.w900,
            ),
          ),
        ],
      ),
    );
  }
}

class _LeaderboardTile extends StatelessWidget {
  const _LeaderboardTile({
    required this.entry,
    required this.canViewTeam,
    required this.onViewTeam,
  });

  final LeaderboardEntry entry;
  final bool canViewTeam;
  final VoidCallback onViewTeam;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(18),
        color: Colors.white.withValues(alpha: 0.04),
      ),
      child: Row(
        children: [
          CircleAvatar(
            radius: 20,
            backgroundColor: entry.rank == 1
                ? const Color(0xFFFFD36A).withValues(alpha: 0.18)
                : Colors.white.withValues(alpha: 0.08),
            child: Text(
              '${entry.rank}',
              style: TextStyle(
                color: entry.rank == 1
                    ? const Color(0xFFFFD36A)
                    : Colors.white,
                fontWeight: FontWeight.w900,
              ),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  entry.username,
                  style: const TextStyle(
                    fontWeight: FontWeight.w800,
                    fontSize: 16,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  entry.teamName,
                  style: const TextStyle(color: Color(0xFFA9B4D0)),
                ),
              ],
            ),
          ),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(
                '${entry.points.toStringAsFixed(1)} pts',
                style: const TextStyle(fontWeight: FontWeight.w900),
              ),
              const SizedBox(height: 4),
              Text(
                entry.winnings > 0
                    ? '${entry.winnings.toStringAsFixed(0)} won'
                    : entry.status,
                style: TextStyle(
                  color: entry.winnings > 0
                      ? const Color(0xFF39E48A)
                      : const Color(0xFFA9B4D0),
                  fontSize: 12,
                  fontWeight: FontWeight.w800,
                ),
              ),
              if (canViewTeam) ...[
                const SizedBox(height: 8),
                TextButton(
                  onPressed: onViewTeam,
                  child: const Text('View Team'),
                ),
              ],
            ],
          ),
        ],
      ),
    );
  }
}

class _ContestTeamPreviewSheet extends ConsumerStatefulWidget {
  const _ContestTeamPreviewSheet({
    required this.contestId,
    required this.teamId,
    required this.ownerName,
    required this.teamName,
  });

  final String contestId;
  final String teamId;
  final String ownerName;
  final String teamName;

  @override
  ConsumerState<_ContestTeamPreviewSheet> createState() =>
      _ContestTeamPreviewSheetState();
}

class _ContestTeamPreviewSheetState
    extends ConsumerState<_ContestTeamPreviewSheet> {
  late Future<FantasyTeam> _future;

  @override
  void initState() {
    super.initState();
    _future = ref.read(contestRepositoryProvider).getContestTeamView(
          contestId: widget.contestId,
          teamId: widget.teamId,
        );
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: Color(0xFF07121F),
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
      ),
      child: FutureBuilder<FantasyTeam>(
        future: _future,
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snapshot.hasError) {
            return Center(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Text(
                  snapshot.error.toString().replaceFirst('Exception: ', ''),
                  textAlign: TextAlign.center,
                ),
              ),
            );
          }

          final team = snapshot.data!;
          final roles = const ['WK', 'BAT', 'AR', 'BOWL'];

          List<FantasyTeamPlayer> playersFor(String role) => team.players
              .where((player) => !player.isSubstitute && player.roleCode == role)
              .toList();

          return SafeArea(
            child: ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            widget.ownerName,
                            style: const TextStyle(
                              color: Color(0xFFA9B4D0),
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            team.name.isEmpty ? widget.teamName : team.name,
                            style: const TextStyle(
                              fontSize: 24,
                              fontWeight: FontWeight.w900,
                            ),
                          ),
                        ],
                      ),
                    ),
                    TextButton(
                      onPressed: () => Navigator.of(context).pop(),
                      child: const Text('Close'),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(28),
                    gradient: const LinearGradient(
                      colors: [
                        Color(0xFF123F2A),
                        Color(0xFF0E5A34),
                        Color(0xFF134727),
                      ],
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                    ),
                  ),
                  child: Column(
                    children: [
                      for (final role in roles) ...[
                        if (playersFor(role).isNotEmpty) ...[
                          _PitchDivider(label: role),
                          const SizedBox(height: 12),
                          Wrap(
                            alignment: WrapAlignment.center,
                            spacing: 10,
                            runSpacing: 10,
                            children: playersFor(role)
                                .map((player) => _PitchPlayerChip(player: player))
                                .toList(),
                          ),
                          const SizedBox(height: 18),
                        ],
                      ],
                    ],
                  ),
                ),
                if (team.substitutes.isNotEmpty) ...[
                  const SizedBox(height: 16),
                  const Text(
                    'Bench',
                    style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
                  ),
                  const SizedBox(height: 10),
                  ...team.substitutes.map(
                    (player) => Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(16),
                          color: Colors.white.withValues(alpha: 0.04),
                        ),
                        child: Row(
                          children: [
                            Text('#${player.substitutePriority}'),
                            const SizedBox(width: 10),
                            Expanded(child: Text(player.playerName)),
                            if (player.fantasyPoints != null) ...[
                              Text(
                                '${player.fantasyPoints!.toStringAsFixed(1)} pts',
                                style: const TextStyle(
                                  color: Color(0xFFFFD36A),
                                  fontWeight: FontWeight.w800,
                                ),
                              ),
                              const SizedBox(width: 12),
                            ],
                            Text(player.roleCode),
                          ],
                        ),
                      ),
                    ),
                  ),
                ],
              ],
            ),
          );
        },
      ),
    );
  }
}

class _PitchDivider extends StatelessWidget {
  const _PitchDivider({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Container(
            height: 1,
            color: Colors.white.withValues(alpha: 0.16),
          ),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12),
          child: Text(
            label,
            style: const TextStyle(fontWeight: FontWeight.w800),
          ),
        ),
        Expanded(
          child: Container(
            height: 1,
            color: Colors.white.withValues(alpha: 0.16),
          ),
        ),
      ],
    );
  }
}

class _PitchPlayerChip extends StatelessWidget {
  const _PitchPlayerChip({required this.player});

  final FantasyTeamPlayer player;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 102,
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(18),
        color: Colors.white.withValues(alpha: 0.12),
      ),
      child: Column(
        children: [
          CircleAvatar(
            radius: 20,
            backgroundColor: const Color(0xFF0D1C14),
            backgroundImage: (player.imageUrl ?? '').trim().isEmpty
                ? null
                : NetworkImage(player.imageUrl!),
            child: (player.imageUrl ?? '').trim().isEmpty
                ? Text(
                    player.playerName.isEmpty
                        ? '?'
                        : player.playerName.substring(0, 1).toUpperCase(),
                    style: const TextStyle(fontWeight: FontWeight.w900),
                  )
                : null,
          ),
          const SizedBox(height: 8),
          Text(
            player.playerName,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            textAlign: TextAlign.center,
            style: const TextStyle(fontWeight: FontWeight.w800, height: 1.2),
          ),
          if (player.fantasyPoints != null) ...[
            const SizedBox(height: 6),
            Text(
              '${player.fantasyPoints!.toStringAsFixed(1)} pts',
              style: const TextStyle(
                color: Color(0xFFFFD36A),
                fontSize: 12,
                fontWeight: FontWeight.w900,
              ),
            ),
          ],
          const SizedBox(height: 6),
          Wrap(
            alignment: WrapAlignment.center,
            spacing: 4,
            runSpacing: 4,
            children: [
              _MiniPill(label: player.roleCode),
              if (player.isCaptain) const _MiniPill(label: 'C'),
              if (player.isViceCaptain) const _MiniPill(label: 'VC'),
            ],
          ),
        ],
      ),
    );
  }
}

class _ContestRulesSheet extends StatefulWidget {
  const _ContestRulesSheet({
    required this.initialRuleSet,
  });

  final CricketRuleSet initialRuleSet;

  @override
  State<_ContestRulesSheet> createState() => _ContestRulesSheetState();
}

class _ContestRulesSheetState extends State<_ContestRulesSheet> {
  late CricketRuleSet _selectedRuleSet;

  @override
  void initState() {
    super.initState();
    _selectedRuleSet = widget.initialRuleSet;
  }

  @override
  Widget build(BuildContext context) {
    final ruleSet = _selectedRuleSet;

    return Container(
      decoration: const BoxDecoration(
        color: Color(0xFF08101D),
        borderRadius: BorderRadius.vertical(top: Radius.circular(30)),
      ),
      child: Column(
        children: [
          Container(
            width: 54,
            height: 5,
            margin: const EdgeInsets.only(top: 12, bottom: 10),
            decoration: BoxDecoration(
              color: Colors.white.withValues(alpha: 0.18),
              borderRadius: BorderRadius.circular(999),
            ),
          ),
          Expanded(
            child: ListView(
              padding: const EdgeInsets.fromLTRB(18, 8, 18, 28),
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text(
                            'How Points Work',
                            style: TextStyle(
                              color: Colors.white,
                              fontSize: 24,
                              fontWeight: FontWeight.w900,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            'Scoring is aligned by match format, so users can check the exact fantasy rules before joining.',
                            style: TextStyle(
                              color: Colors.white.withValues(alpha: 0.7),
                              fontSize: 13,
                              height: 1.4,
                            ),
                          ),
                        ],
                      ),
                    ),
                    IconButton(
                      onPressed: () => Navigator.of(context).pop(),
                      icon: const Icon(Icons.close_rounded, color: Colors.white),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: Row(
                    children: [
                      for (final item in allCricketRuleSets) ...[
                        _RuleSetChip(
                          label: item.label,
                          selected: item.key == ruleSet.key,
                          onTap: () => setState(() => _selectedRuleSet = item),
                        ),
                        const SizedBox(width: 10),
                      ],
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                Container(
                  padding: const EdgeInsets.all(18),
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(26),
                    gradient: const LinearGradient(
                      colors: [Color(0xFF101A2C), Color(0xFF0B1524)],
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    ),
                    border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Container(
                            width: 42,
                            height: 42,
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              color: const Color(0x14F15B63),
                              border: Border.all(
                                color: const Color(0x33F15B63),
                              ),
                            ),
                            alignment: Alignment.center,
                            child: Text(
                              ruleSet.label,
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 12,
                                fontWeight: FontWeight.w900,
                              ),
                            ),
                          ),
                          const SizedBox(width: 12),
                          const Expanded(
                            child: Text(
                              'Important Fantasy Points',
                              style: TextStyle(
                                color: Colors.white,
                                fontSize: 18,
                                fontWeight: FontWeight.w900,
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 16),
                      ...ruleSet.highlights.map(
                        (item) => _RuleValueRow(item: item),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                ...ruleSet.categories.map(
                  (category) => Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: _RuleCategoryCard(category: category),
                  ),
                ),
                if (ruleSet.notes.isNotEmpty) ...[
                  const SizedBox(height: 4),
                  Container(
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: Colors.white.withValues(alpha: 0.04),
                      borderRadius: BorderRadius.circular(22),
                      border: Border.all(
                        color: Colors.white.withValues(alpha: 0.08),
                      ),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'Other Important Points',
                          style: TextStyle(
                            color: Colors.white,
                            fontSize: 16,
                            fontWeight: FontWeight.w900,
                          ),
                        ),
                        const SizedBox(height: 12),
                        ...ruleSet.notes.map(
                          (note) => Padding(
                            padding: const EdgeInsets.only(bottom: 8),
                            child: Row(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                const Padding(
                                  padding: EdgeInsets.only(top: 6),
                                  child: Icon(
                                    Icons.brightness_1_rounded,
                                    size: 7,
                                    color: Color(0xFFF15B63),
                                  ),
                                ),
                                const SizedBox(width: 10),
                                Expanded(
                                  child: Text(
                                    note,
                                    style: TextStyle(
                                      color: Colors.white.withValues(alpha: 0.72),
                                      fontSize: 13,
                                      height: 1.4,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _RuleSetChip extends StatelessWidget {
  const _RuleSetChip({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(999),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 11),
        decoration: BoxDecoration(
          color: selected
              ? Colors.white
              : Colors.white.withValues(alpha: 0.05),
          borderRadius: BorderRadius.circular(999),
          border: Border.all(
            color: selected
                ? Colors.white
                : Colors.white.withValues(alpha: 0.08),
          ),
        ),
        child: Text(
          label,
          style: TextStyle(
            color: selected ? const Color(0xFF08101D) : Colors.white,
            fontSize: 13,
            fontWeight: FontWeight.w800,
          ),
        ),
      ),
    );
  }
}

class _RuleCategoryCard extends StatelessWidget {
  const _RuleCategoryCard({
    required this.category,
  });

  final CricketRuleCategory category;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.04),
        borderRadius: BorderRadius.circular(22),
        border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            category.title,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 17,
              fontWeight: FontWeight.w900,
            ),
          ),
          if (category.subtitle != null) ...[
            const SizedBox(height: 4),
            Text(
              category.subtitle!,
              style: TextStyle(
                color: Colors.white.withValues(alpha: 0.6),
                fontSize: 12,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
          const SizedBox(height: 12),
          ...category.items.map((item) => _RuleValueRow(item: item)),
        ],
      ),
    );
  }
}

class _RuleValueRow extends StatelessWidget {
  const _RuleValueRow({
    required this.item,
  });

  final CricketRuleItem item;

  @override
  Widget build(BuildContext context) {
    final isNegative = item.value.trim().startsWith('-');

    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
        decoration: BoxDecoration(
          color: Colors.white.withValues(alpha: 0.03),
          borderRadius: BorderRadius.circular(18),
        ),
        child: Row(
          children: [
            Expanded(
              child: Text(
                item.label,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 14,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
            const SizedBox(width: 12),
            Text(
              item.value,
              style: TextStyle(
                color: isNegative
                    ? const Color(0xFFFF9C6B)
                    : const Color(0xFF39E48A),
                fontSize: 15,
                fontWeight: FontWeight.w900,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

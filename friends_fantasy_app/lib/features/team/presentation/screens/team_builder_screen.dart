import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/services.dart';

import '../../../../core/widgets/async_value_view.dart';
import '../../../contest/data/contest_repository.dart';
import '../../../fixture/data/fixture_repository.dart';
import '../../../fixture/domain/models/fixture_models.dart';
import '../../../player/domain/models/player_models.dart';
import '../../../room/data/room_repository.dart';
import '../../data/team_repository.dart';
import '../../domain/models/team_models.dart';

enum _BuilderStep { players, substitutes, leadership }
enum _PlayerSortOption { nameAsc, nameDesc, teamAsc, teamDesc }

class TeamBuilderScreen extends ConsumerStatefulWidget {
  const TeamBuilderScreen({
    super.key,
    required this.fixtureId,
    this.teamId,
    this.contestId,
    this.communityId,
  });

  final String fixtureId;
  final String? teamId;
  final String? contestId;
  final String? communityId;

  @override
  ConsumerState<TeamBuilderScreen> createState() => _TeamBuilderScreenState();
}

class _TeamBuilderScreenState extends ConsumerState<TeamBuilderScreen> {
  static const Duration _autoRefreshInterval = Duration(seconds: 45);
  static const int _teamSize = 11;
  static const int _maxSubstitutes = 4;
  static const int _maxPlayersPerRole = 8;
  static const List<String> _preferredRoleOrder = <String>[
    'WK',
    'BAT',
    'AR',
    'BOWL',
  ];

  final TextEditingController _nameController = TextEditingController();
  final Set<String> _selectedIds = <String>{};
  final Set<String> _substituteIds = <String>{};

  String? _captainId;
  String? _viceCaptainId;
  String _activeRole = 'WK';
  _PlayerSortOption _sortOption = _PlayerSortOption.nameAsc;

  bool _submitting = false;
  bool _initialized = false;
  bool? _hasAnnouncedLineup;
  bool _interactionLocked = false;
  _BuilderStep _step = _BuilderStep.players;
  Timer? _autoRefreshTimer;

  @override
  void initState() {
    super.initState();
    _autoRefreshTimer = Timer.periodic(_autoRefreshInterval, (_) {
      if (!mounted || _interactionLocked) {
        return;
      }
      ref.invalidate(playerPoolProvider(widget.fixtureId));
    });
  }

  @override
  void dispose() {
    _autoRefreshTimer?.cancel();
    _nameController.dispose();
    super.dispose();
  }

  void _initializeIfNeeded(List<FantasyTeam> teams) {
    if (_initialized) return;
    _initialized = true;

    if (widget.teamId == null) {
      _nameController.text = teams.isEmpty
          ? 'My Team'
          : 'My Team ${teams.length + 1}';
      return;
    }

    FantasyTeam? existing;
    for (final team in teams) {
      if (team.id == widget.teamId) {
        existing = team;
        break;
      }
    }

    if (existing == null) return;

    _nameController.text = existing.name;
    _selectedIds.addAll(existing.playerIds);
    _substituteIds.addAll(existing.substitutePlayerIds);
    _captainId = existing.captainPlayerId;
    _viceCaptainId = existing.viceCaptainPlayerId;
    _step = _BuilderStep.players;
  }

  void _showMessage(String text) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
  }

  void _trackLineupAnnouncement(List<PlayerPoolItem> players) {
    final hasAnnouncement = players.any(
      (player) => player.isAnnounced || player.isPlaying,
    );
    final previous = _hasAnnouncedLineup;
    _hasAnnouncedLineup = hasAnnouncement;

    if (previous == false && hasAnnouncement) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        unawaited(SystemSound.play(SystemSoundType.alert));
        _showMessage('Playing XI announced. Review your team now.');
      });
    }
  }

  Future<void> _refreshPlayerPool({bool showConfirmation = false}) async {
    try {
      await ref
          .read(teamRepositoryProvider)
          .getPlayerPool(widget.fixtureId, forceSync: true);
      final refreshedPlayers = await ref.refresh(
        playerPoolProvider(widget.fixtureId).future,
      );
      if (showConfirmation && mounted) {
        _showMessage(
          refreshedPlayers.any((player) => player.isPlaying)
              ? 'Playing XI synced'
              : 'Latest player pool synced',
        );
      }
    } catch (e) {
      if (showConfirmation && mounted) {
        _showMessage(e.toString().replaceFirst('Exception: ', ''));
      }
    }
  }

  bool _hasFixtureLocked(FixtureDetail? fixture) {
    if (fixture == null) {
      return false;
    }

    final status = fixture.summary.statusLabel.toUpperCase();
    if (status == 'LIVE' || status == 'COMPLETED' || status == 'CANCELLED') {
      return true;
    }

    final referenceTime =
        fixture.summary.deadlineTime ?? fixture.summary.startTime;
    return referenceTime != null && !referenceTime.isAfter(DateTime.now());
  }

  Future<bool> _ensureFixtureUnlocked() async {
    try {
      final fixture = await ref.read(fixtureDetailProvider(widget.fixtureId).future);
      if (_hasFixtureLocked(fixture)) {
        if (mounted) {
          _showMessage('Match has already started. Team editing is locked.');
        }
        return false;
      }
    } catch (_) {
      // Keep backend validation as the final guard if fixture refresh is unavailable.
    }

    return true;
  }

  String _normalizeRole(String role) {
    final value = role.toLowerCase().replaceAll(RegExp(r'[^a-z]'), '');
    if (value.contains('keeper') || value == 'wk') return 'WK';
    if (value.contains('bat')) return 'BAT';
    if (value.contains('allround') || value == 'ar') return 'AR';
    if (value.contains('bowl')) return 'BOWL';
    return role.toUpperCase();
  }

  String _roleLabel(String role) {
    switch (role) {
      case 'WK':
        return 'Wicket Keepers';
      case 'BAT':
        return 'Batters';
      case 'AR':
        return 'All-Rounders';
      case 'BOWL':
        return 'Bowlers';
      default:
        return role;
    }
  }

  String _sortLabel(_PlayerSortOption option) {
    switch (option) {
      case _PlayerSortOption.nameDesc:
        return 'Name Z-A';
      case _PlayerSortOption.teamAsc:
        return 'Team A-Z';
      case _PlayerSortOption.teamDesc:
        return 'Team Z-A';
      case _PlayerSortOption.nameAsc:
        return 'Name A-Z';
    }
  }

  int _roleSortIndex(String role) {
    final index = _preferredRoleOrder.indexOf(role);
    return index == -1 ? 99 : index;
  }

  List<String> _availableRoles(List<PlayerPoolItem> players) {
    final discovered = <String>{};
    for (final player in players) {
      discovered.add(_normalizeRole(player.role));
    }

    final ordered = <String>[];
    for (final role in _preferredRoleOrder) {
      if (discovered.remove(role)) {
        ordered.add(role);
      }
    }

    final extras = discovered.toList()..sort();
    ordered.addAll(extras);
    return ordered;
  }

  List<String> _orderedTeamNames(List<PlayerPoolItem> players) {
    final seen = <String>{};
    final teams = <String>[];
    for (final player in players) {
      if (seen.add(player.teamName)) {
        teams.add(player.teamName);
      }
    }
    return teams;
  }

  Map<String, _TeamPalette> _teamPalettes(List<PlayerPoolItem> players) {
    const palettes = <_TeamPalette>[
      _TeamPalette(
        primary: Color(0xFFE95858),
        secondary: Color(0xFFFFB36B),
        soft: Color(0x33E95858),
      ),
      _TeamPalette(
        primary: Color(0xFF4CA8FF),
        secondary: Color(0xFF6FE7FF),
        soft: Color(0x334CA8FF),
      ),
      _TeamPalette(
        primary: Color(0xFF8B6BFF),
        secondary: Color(0xFFC08FFF),
        soft: Color(0x338B6BFF),
      ),
    ];

    final map = <String, _TeamPalette>{};
    final teams = _orderedTeamNames(players);
    for (var i = 0; i < teams.length; i++) {
      map[teams[i]] = palettes[i % palettes.length];
    }
    return map;
  }

  _TeamPalette _paletteForTeam(
    Map<String, _TeamPalette> palettes,
    String teamName,
  ) {
    return palettes[teamName] ??
        const _TeamPalette(
          primary: Color(0xFF8D98A6),
          secondary: Color(0xFFC4CEDB),
          soft: Color(0x338D98A6),
        );
  }

  String _teamBadge(String name) {
    final trimmed = name.trim().toUpperCase();
    if (trimmed.isEmpty) return '--';
    if (trimmed.length <= 3) return trimmed;

    final words = trimmed.split(RegExp(r'\s+'));
    if (words.length >= 2) {
      return '${words[0][0]}${words[1][0]}';
    }
    return trimmed.substring(0, 3);
  }

  String _shortName(String name) {
    final parts = name.trim().split(RegExp(r'\s+'));
    if (parts.isEmpty) return name;
    if (parts.length == 1) return parts.first;
    return '${parts.first} ${parts.last}';
  }

  String _playerInitials(String name) {
    final parts = name.trim().split(RegExp(r'\s+'));
    if (parts.isEmpty || parts.first.isEmpty) return '?';
    if (parts.length == 1) return parts.first.substring(0, 1).toUpperCase();
    return '${parts.first.substring(0, 1)}${parts.last.substring(0, 1)}'
        .toUpperCase();
  }

  String _availabilityLabel(PlayerPoolItem player) {
    if (player.isPlaying) return 'Playing XI';
    if (player.isAnnounced) return 'Not in XI';
    return 'Expected';
  }

  Color _availabilityColor(PlayerPoolItem player) {
    if (player.isPlaying) return const Color(0xFF39E48A);
    if (player.isAnnounced) return const Color(0xFFFF8A6B);
    return const Color(0xFFFFD36A);
  }

  String? _nonEmptyString(dynamic value) {
    if (value is String && value.trim().isNotEmpty) {
      return value.trim();
    }
    return null;
  }

  String? _playerImageUrl(PlayerPoolItem player) {
    final dynamic data = player;

    try {
      final value = _nonEmptyString(data.imageUrl);
      if (value != null) return value;
    } catch (_) {}

    try {
      final value = _nonEmptyString(data.photoUrl);
      if (value != null) return value;
    } catch (_) {}

    try {
      final value = _nonEmptyString(data.avatarUrl);
      if (value != null) return value;
    } catch (_) {}

    try {
      final value = _nonEmptyString(data.profileImageUrl);
      if (value != null) return value;
    } catch (_) {}

    try {
      final value = _nonEmptyString(data.image);
      if (value != null) return value;
    } catch (_) {}

    try {
      final value = _nonEmptyString(data.playerImage);
      if (value != null) return value;
    } catch (_) {}

    try {
      final value = _nonEmptyString(data.playerImageUrl);
      if (value != null) return value;
    } catch (_) {}

    try {
      final json = data.toJson();
      if (json is Map) {
        const keys = <String>[
          'imageUrl',
          'photoUrl',
          'avatarUrl',
          'profileImageUrl',
          'image',
          'playerImage',
          'playerImageUrl',
        ];
        for (final key in keys) {
          final value = _nonEmptyString(json[key]);
          if (value != null) return value;
        }
      }
    } catch (_) {}

    return null;
  }

  List<PlayerPoolItem> _selectedPlayerItems(List<PlayerPoolItem> allPlayers) {
    return allPlayers
        .where((player) => _selectedIds.contains(player.id))
        .toList();
  }

  List<PlayerPoolItem> _playersForRole(
    List<PlayerPoolItem> players,
    String role,
  ) {
    return players
        .where((player) => _normalizeRole(player.role) == role)
        .toList()
      ..sort((a, b) => a.name.toLowerCase().compareTo(b.name.toLowerCase()));
  }

  Map<String, int> _teamDistribution(List<PlayerPoolItem> selectedPlayers) {
    final counts = <String, int>{};
    for (final player in selectedPlayers) {
      counts.update(player.teamName, (value) => value + 1, ifAbsent: () => 1);
    }
    return counts;
  }

  Map<String, int> _roleDistribution(List<PlayerPoolItem> selectedPlayers) {
    final counts = <String, int>{'WK': 0, 'BAT': 0, 'AR': 0, 'BOWL': 0};

    for (final player in selectedPlayers) {
      final role = _normalizeRole(player.role);
      counts.update(role, (value) => value + 1, ifAbsent: () => 1);
    }

    return counts;
  }

  int _selectedCountForRole(List<PlayerPoolItem> allPlayers, String role) {
    return _selectedPlayerItems(
      allPlayers,
    ).where((player) => _normalizeRole(player.role) == role).length;
  }

  List<String> _missingRequiredRoles(List<PlayerPoolItem> allPlayers) {
    final selectedPlayers = _selectedPlayerItems(allPlayers);
    final roleCounts = _roleDistribution(selectedPlayers);

    final missing = <String>[];
    for (final role in _preferredRoleOrder) {
      if ((roleCounts[role] ?? 0) < 1) {
        missing.add(role);
      }
    }
    return missing;
  }

  bool _hasPlayersFromBothTeams(List<PlayerPoolItem> allPlayers) {
    final selectedPlayers = _selectedPlayerItems(allPlayers);
    final teamCounts = _teamDistribution(selectedPlayers);
    return teamCounts.length >= 2;
  }

  String? _teamValidationError(List<PlayerPoolItem> allPlayers) {
    if (_selectedIds.length != _teamSize) {
      return 'Select exactly $_teamSize players';
    }

    final missingRoles = _missingRequiredRoles(allPlayers);
    if (missingRoles.isNotEmpty) {
      return 'Select at least 1 player in each role: ${missingRoles.join(', ')}';
    }

    if (!_hasPlayersFromBothTeams(allPlayers)) {
      return 'Select at least 1 player from both teams';
    }

    return null;
  }

  String? _substituteValidationError(List<PlayerPoolItem> allPlayers) {
    if (_substituteIds.length > _maxSubstitutes) {
      return 'Select up to $_maxSubstitutes substitutes';
    }

    final selectedPlayerIds = _selectedPlayerItems(
      allPlayers,
    ).map((player) => player.id).toSet();
    for (final substituteId in _substituteIds) {
      if (selectedPlayerIds.contains(substituteId)) {
        return 'A playing XI player cannot be selected as a substitute';
      }
    }

    return null;
  }

  String? _selectionErrorFor(
    PlayerPoolItem player,
    List<PlayerPoolItem> allPlayers,
  ) {
    if (_selectedIds.length >= _teamSize) {
      return 'You can only select $_teamSize players';
    }

    final normalizedRole = _normalizeRole(player.role);
    final roleCount = _selectedCountForRole(allPlayers, normalizedRole);
    if (roleCount >= _maxPlayersPerRole) {
      return '$normalizedRole can have maximum $_maxPlayersPerRole players';
    }

    final hypotheticalSelected = <PlayerPoolItem>[
      ..._selectedPlayerItems(allPlayers),
      player,
    ];

    final hypotheticalRoleCounts = _roleDistribution(hypotheticalSelected);
    final missingRolesAfterPick = _preferredRoleOrder
        .where((role) => (hypotheticalRoleCounts[role] ?? 0) < 1)
        .toList();

    final remainingSlotsAfterPick = _teamSize - (_selectedIds.length + 1);
    if (missingRolesAfterPick.length > remainingSlotsAfterPick) {
      return 'Keep space for: ${missingRolesAfterPick.join(', ')}';
    }

    final hypotheticalTeamCounts = _teamDistribution(hypotheticalSelected);
    if (remainingSlotsAfterPick == 0 && hypotheticalTeamCounts.length < 2) {
      return 'Select at least 1 player from both teams';
    }

    return null;
  }

  String? _substituteSelectionErrorFor(PlayerPoolItem player) {
    if (_selectedIds.contains(player.id)) {
      return 'A playing XI player cannot be selected as a substitute';
    }

    if (_substituteIds.length >= _maxSubstitutes) {
      return 'You can only select up to $_maxSubstitutes substitutes';
    }

    return null;
  }

  int _comparePlayers(
    PlayerPoolItem a,
    PlayerPoolItem b,
    Set<String> selectedIds,
  ) {
    final aSelected = selectedIds.contains(a.id);
    final bSelected = selectedIds.contains(b.id);
    if (aSelected != bSelected) {
      return aSelected ? -1 : 1;
    }

    final sortCompare = _comparePlayersBySortOption(a, b);
    if (sortCompare != 0) return sortCompare;

    final aAvailability = (a.isPlaying ? 2 : 0) + (a.isAnnounced ? 1 : 0);
    final bAvailability = (b.isPlaying ? 2 : 0) + (b.isAnnounced ? 1 : 0);
    final availabilityCompare = bAvailability.compareTo(aAvailability);
    if (availabilityCompare != 0) return availabilityCompare;

    final selectionCompare = b.selectionPercent.compareTo(a.selectionPercent);
    if (selectionCompare != 0) return selectionCompare;

    return b.credit.compareTo(a.credit);
  }

  int _comparePlayersBySortOption(PlayerPoolItem a, PlayerPoolItem b) {
    final aName = a.name.toLowerCase();
    final bName = b.name.toLowerCase();
    final aTeam = a.teamName.toLowerCase();
    final bTeam = b.teamName.toLowerCase();

    switch (_sortOption) {
      case _PlayerSortOption.nameDesc:
        final nameDesc = bName.compareTo(aName);
        if (nameDesc != 0) return nameDesc;
        return aTeam.compareTo(bTeam);
      case _PlayerSortOption.teamAsc:
        final teamAsc = aTeam.compareTo(bTeam);
        if (teamAsc != 0) return teamAsc;
        return aName.compareTo(bName);
      case _PlayerSortOption.teamDesc:
        final teamDesc = bTeam.compareTo(aTeam);
        if (teamDesc != 0) return teamDesc;
        return aName.compareTo(bName);
      case _PlayerSortOption.nameAsc:
        final nameAsc = aName.compareTo(bName);
        if (nameAsc != 0) return nameAsc;
        return aTeam.compareTo(bTeam);
    }
  }

  void _togglePlayer(
    PlayerPoolItem player,
    bool shouldSelect,
    List<PlayerPoolItem> allPlayers,
  ) {
    if (_interactionLocked) {
      _showMessage('Match has already started. Team editing is locked.');
      return;
    }

    if (shouldSelect) {
      final error = _selectionErrorFor(player, allPlayers);
      if (error != null) {
        _showMessage(error);
        return;
      }
    }

    setState(() {
      if (shouldSelect) {
        _selectedIds.add(player.id);
        _substituteIds.remove(player.id);
      } else {
        _selectedIds.remove(player.id);
        if (_captainId == player.playerId) _captainId = null;
        if (_viceCaptainId == player.playerId) _viceCaptainId = null;
        if (_step == _BuilderStep.leadership ||
            _step == _BuilderStep.substitutes) {
          _step = _BuilderStep.players;
        }
      }
    });
  }

  void _toggleSubstitute(PlayerPoolItem player, bool shouldSelect) {
    if (_interactionLocked) {
      _showMessage('Match has already started. Team editing is locked.');
      return;
    }

    if (shouldSelect) {
      final error = _substituteSelectionErrorFor(player);
      if (error != null) {
        _showMessage(error);
        return;
      }
    }

    setState(() {
      if (shouldSelect) {
        _substituteIds.add(player.id);
      } else {
        _substituteIds.remove(player.id);
      }
    });
  }

  void _continueToSubstitutes(List<PlayerPoolItem> allPlayers) {
    FocusScope.of(context).unfocus();

    final error = _teamValidationError(allPlayers);
    if (error != null) {
      _showMessage(error);
      return;
    }

    setState(() => _step = _BuilderStep.substitutes);
  }

  void _continueToLeadership(List<PlayerPoolItem> allPlayers) {
    FocusScope.of(context).unfocus();

    final error = _substituteValidationError(allPlayers);
    if (error != null) {
      _showMessage(error);
      return;
    }

    setState(() => _step = _BuilderStep.leadership);
  }

  Future<void> _submit(List<PlayerPoolItem> allPlayers) async {
    if (!await _ensureFixtureUnlocked()) {
      return;
    }

    final teamName = _nameController.text.trim();

    if (teamName.isEmpty) {
      _showMessage('Enter team name');
      return;
    }

    final error = _teamValidationError(allPlayers);
    if (error != null) {
      _showMessage(error);
      return;
    }

    final substituteError = _substituteValidationError(allPlayers);
    if (substituteError != null) {
      _showMessage(substituteError);
      return;
    }

    if (_captainId == null || _viceCaptainId == null) {
      _showMessage('Select captain and vice-captain');
      return;
    }

    if (_captainId == _viceCaptainId) {
      _showMessage('Captain and vice-captain must be different');
      return;
    }

    setState(() => _submitting = true);

    try {
      if (widget.teamId == null) {
        if (widget.communityId != null) {
          await ref
              .read(roomRepositoryProvider)
              .createCommunityTeam(
                roomId: widget.communityId!,
                teamName: teamName,
                fixturePlayerPoolIds: _selectedIds.toList(),
                substituteFixturePlayerPoolIds: _substituteIds.toList(),
                captainPlayerId: _captainId!,
                viceCaptainPlayerId: _viceCaptainId!,
              );
          ref.invalidate(roomDetailProvider(widget.communityId!));
          ref.invalidate(myRoomsProvider);
        } else {
          await ref
              .read(teamRepositoryProvider)
              .createTeam(
                fixtureId: widget.fixtureId,
                teamName: teamName,
                fixturePlayerPoolIds: _selectedIds.toList(),
                substituteFixturePlayerPoolIds: _substituteIds.toList(),
                captainPlayerId: _captainId!,
                viceCaptainPlayerId: _viceCaptainId!,
              );
        }

        ref.invalidate(myTeamsProvider(widget.fixtureId));

        if (widget.communityId == null && widget.contestId != null) {
          final updatedTeams = await ref
              .read(teamRepositoryProvider)
              .getMyTeams(widget.fixtureId);

          FantasyTeam? createdTeam;
          for (final team in updatedTeams) {
            final sameName = team.name.trim() == teamName;
            final samePlayers =
                team.playerIds.length == _selectedIds.length &&
                team.playerIds.toSet().containsAll(_selectedIds);
            final sameSubstitutes =
                team.substitutePlayerIds.length == _substituteIds.length &&
                team.substitutePlayerIds.toSet().containsAll(_substituteIds);
            if (sameName && samePlayers && sameSubstitutes) {
              createdTeam = team;
              break;
            }
          }

          createdTeam =
              createdTeam ??
              (updatedTeams.isNotEmpty ? updatedTeams.first : null);

          if (createdTeam == null) {
            throw Exception(
              'Team created, but it could not be identified for contest joining.',
            );
          }

          await ref
              .read(contestRepositoryProvider)
              .joinContest(
                contestId: widget.contestId!,
                teamId: createdTeam.id,
              );
          ref.invalidate(contestDetailProvider(widget.contestId!));
          ref.invalidate(myContestEntriesProvider(widget.contestId!));
          ref.invalidate(leaderboardProvider(widget.contestId!));
        }
      } else {
        await ref
            .read(teamRepositoryProvider)
            .updateTeam(
              teamId: widget.teamId!,
              teamName: teamName,
              fixturePlayerPoolIds: _selectedIds.toList(),
              substituteFixturePlayerPoolIds: _substituteIds.toList(),
              captainPlayerId: _captainId!,
              viceCaptainPlayerId: _viceCaptainId!,
            );

        ref.invalidate(myTeamsProvider(widget.fixtureId));
        if (widget.communityId != null) {
          ref.invalidate(roomDetailProvider(widget.communityId!));
          ref.invalidate(myRoomsProvider);
        }
      }

      if (!mounted) return;

      _showMessage(
        widget.teamId != null
            ? widget.communityId != null
                  ? 'Team updated for community'
                  : 'Team updated'
            : widget.communityId != null
            ? 'Team created and selected for community'
            : widget.contestId != null
            ? 'Team created and contest joined!'
            : 'Team created',
      );
      Navigator.of(context).pop(true);
    } catch (e) {
      if (!mounted) return;
      _showMessage(e.toString().replaceFirst('Exception: ', ''));
    } finally {
      if (mounted) {
        setState(() => _submitting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final playersValue = ref.watch(playerPoolProvider(widget.fixtureId));
    final teamsValue = ref.watch(myTeamsProvider(widget.fixtureId));
    final fixtureValue = ref.watch(fixtureDetailProvider(widget.fixtureId));
    _interactionLocked = _hasFixtureLocked(fixtureValue.asData?.value);

    return Scaffold(
      backgroundColor: const Color(0xFF050914),
      appBar: AppBar(
        elevation: 0,
        backgroundColor: const Color(0xFF050914),
        titleSpacing: 20,
        title: Text(widget.teamId == null ? 'Create Team' : 'Edit Team'),
        actions: [
          TextButton(
            onPressed: _refreshPlayerPool,
            child: const Text(
              'Refresh Live Team',
              style: TextStyle(color: Colors.white),
            ),
          ),
        ],
      ),
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            colors: [Color(0xFF111A2C), Color(0xFF09111F), Color(0xFF050914)],
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
          ),
        ),
        child: AsyncValueView(
          value: playersValue,
          onRetry: () {
            ref.invalidate(playerPoolProvider(widget.fixtureId));
            ref.invalidate(myTeamsProvider(widget.fixtureId));
          },
          data: (players) {
            if (widget.teamId != null &&
                teamsValue.isLoading &&
                !_initialized) {
              return const Center(
                child: CircularProgressIndicator(color: Colors.white),
              );
            }

            if (widget.teamId != null && teamsValue.hasError && !_initialized) {
              return Center(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Text(
                        'Unable to load your team details',
                        style: TextStyle(color: Colors.white, fontSize: 16),
                      ),
                      const SizedBox(height: 12),
                      OutlinedButton(
                        onPressed: () =>
                            ref.invalidate(myTeamsProvider(widget.fixtureId)),
                        child: const Text('Retry'),
                      ),
                    ],
                  ),
                ),
              );
            }

            if (!_initialized) {
              if (widget.teamId == null) {
                _initializeIfNeeded(
                  teamsValue.asData?.value ?? const <FantasyTeam>[],
                );
              } else if (teamsValue.hasValue) {
                _initializeIfNeeded(teamsValue.value ?? const <FantasyTeam>[]);
              }
            }

            _trackLineupAnnouncement(players);

            final selectedPlayers =
                players
                    .where((player) => _selectedIds.contains(player.id))
                    .toList()
                  ..sort((a, b) {
                    final roleCompare = _roleSortIndex(
                      _normalizeRole(a.role),
                    ).compareTo(_roleSortIndex(_normalizeRole(b.role)));
                    if (roleCompare != 0) return roleCompare;
                    return a.name.toLowerCase().compareTo(b.name.toLowerCase());
                  });
            final substitutePlayers =
                players
                    .where((player) => _substituteIds.contains(player.id))
                    .toList()
                  ..sort((a, b) {
                    final aIndex = _substituteIds.toList().indexOf(a.id);
                    final bIndex = _substituteIds.toList().indexOf(b.id);
                    if (aIndex != bIndex) {
                      return aIndex.compareTo(bIndex);
                    }
                    return a.name.toLowerCase().compareTo(b.name.toLowerCase());
                  });

            final roles = _availableRoles(players);
            final activeRole = roles.contains(_activeRole)
                ? _activeRole
                : (roles.isEmpty ? 'ALL' : roles.first);
            final isSubstituteStep = _step == _BuilderStep.substitutes;
            final visiblePlayers =
                players.where((player) {
                    if (isSubstituteStep && _selectedIds.contains(player.id)) {
                      return false;
                    }
                    if (activeRole != 'ALL' &&
                        _normalizeRole(player.role) != activeRole) {
                      return false;
                    }
                    return true;
                  }).toList()
                  ..sort(
                    (a, b) => _comparePlayers(
                      a,
                      b,
                      isSubstituteStep ? _substituteIds : _selectedIds,
                    ),
                  );

            return Column(
              children: [
                Expanded(
                  child: AnimatedSwitcher(
                    duration: const Duration(milliseconds: 220),
                    child: _step == _BuilderStep.players
                        ? KeyedSubtree(
                            key: const ValueKey('players-step'),
                            child: _buildPlayerStep(
                              players: players,
                              fixture: fixtureValue.asData?.value,
                              visiblePlayers: visiblePlayers,
                              selectedPlayers: selectedPlayers,
                              roles: roles,
                              activeRole: activeRole,
                            ),
                          )
                        : _step == _BuilderStep.substitutes
                        ? KeyedSubtree(
                            key: const ValueKey('substitutes-step'),
                            child: _buildSubstituteStep(
                              players: players,
                              fixture: fixtureValue.asData?.value,
                              visiblePlayers: visiblePlayers,
                              selectedPlayers: selectedPlayers,
                              substitutePlayers: substitutePlayers,
                              roles: roles,
                              activeRole: activeRole,
                            ),
                          )
                        : KeyedSubtree(
                            key: const ValueKey('leadership-step'),
                            child: _buildLeadershipStep(
                              fixtureValue.asData?.value,
                              players,
                              selectedPlayers,
                            ),
                          ),
                  ),
                ),
                _buildBottomBar(players, selectedPlayers, substitutePlayers),
              ],
            );
          },
        ),
      ),
    );
  }

  Widget _buildPlayerStep({
    required List<PlayerPoolItem> players,
    required FixtureDetail? fixture,
    required List<PlayerPoolItem> visiblePlayers,
    required List<PlayerPoolItem> selectedPlayers,
    required List<String> roles,
    required String activeRole,
  }) {
    return RefreshIndicator(
      color: const Color(0xFF63D9FF),
      backgroundColor: const Color(0xFF0B1524),
      onRefresh: _refreshPlayerPool,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 170),
        children: [
          _buildMatchHeader(
            fixture,
            players,
            selectedPlayers,
            showTeamNameInput: true,
            helperText: _interactionLocked
                ? 'Match started. Team editing is locked.'
                : 'Pick your playing XI first.',
          ),
          const SizedBox(height: 18),
          if (roles.isNotEmpty) ...[
            _buildRoleTabs(
              roles: roles,
              activeRole: activeRole,
            ),
            const SizedBox(height: 14),
          ],
          _buildSortBar(),
          const SizedBox(height: 14),
          if (visiblePlayers.isEmpty)
            _buildEmptyState()
          else
            ...visiblePlayers.map(
              (player) => _buildPlayerCard(player, players),
            ),
        ],
      ),
    );
  }

  Widget _buildSubstituteStep({
    required List<PlayerPoolItem> players,
    required FixtureDetail? fixture,
    required List<PlayerPoolItem> visiblePlayers,
    required List<PlayerPoolItem> selectedPlayers,
    required List<PlayerPoolItem> substitutePlayers,
    required List<String> roles,
    required String activeRole,
  }) {
    return RefreshIndicator(
      color: const Color(0xFF63D9FF),
      backgroundColor: const Color(0xFF0B1524),
      onRefresh: _refreshPlayerPool,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 170),
        children: [
          _buildMatchHeader(
            fixture,
            players,
            selectedPlayers,
            helperText: 'Choose up to 4 backups for toss or lineup changes.',
          ),
          const SizedBox(height: 18),
          _buildBackupSlotsCompact(substitutePlayers),
          const SizedBox(height: 18),
          if (roles.isNotEmpty) ...[
            _buildRoleTabs(
              roles: roles,
              activeRole: activeRole,
            ),
            const SizedBox(height: 14),
          ],
          _buildSortBar(),
          const SizedBox(height: 14),
          if (visiblePlayers.isEmpty)
            _buildEmptyState(
              message:
                  'No extra players are available here. Pull to refresh after toss for the latest playing XI.',
            )
          else
            ...visiblePlayers.map(
              (player) => _buildSubstituteCard(player, players),
            ),
        ],
      ),
    );
  }

  Widget _buildLeadershipStep(
    FixtureDetail? fixture,
    List<PlayerPoolItem> allPlayers,
    List<PlayerPoolItem> selectedPlayers,
  ) {
    final orderedPlayers = selectedPlayers.toList()
      ..sort((a, b) {
        final aPriority = _captainId == a.playerId
            ? 0
            : _viceCaptainId == a.playerId
            ? 1
            : 2;
        final bPriority = _captainId == b.playerId
            ? 0
            : _viceCaptainId == b.playerId
            ? 1
            : 2;

        if (aPriority != bPriority) return aPriority.compareTo(bPriority);
        return a.name.toLowerCase().compareTo(b.name.toLowerCase());
      });

    return RefreshIndicator(
      color: const Color(0xFF63D9FF),
      backgroundColor: const Color(0xFF0B1524),
      onRefresh: _refreshPlayerPool,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 170),
        children: [
          _buildMatchHeader(
            fixture,
            allPlayers,
            selectedPlayers,
            helperText: 'Select captain (2x points) and vice captain (1.5x points).',
          ),
          const SizedBox(height: 18),
          ...orderedPlayers.map(
            (player) => _buildLeadershipCard(player, allPlayers),
          ),
        ],
      ),
    );
  }

  Widget _buildMatchHeader(
    FixtureDetail? fixture,
    List<PlayerPoolItem> allPlayers,
    List<PlayerPoolItem> selectedPlayers, {
    bool showTeamNameInput = false,
    String? helperText,
  }) {
    final teams = _buildHeaderTeams(fixture, allPlayers);
    if (teams.isEmpty) {
      return const SizedBox.shrink();
    }

    final teamCounts = _teamDistribution(selectedPlayers);
    final fullTitle = teams.length > 1
        ? '${teams.first.teamName} vs ${teams[1].teamName}'
        : teams.first.teamName;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (showTeamNameInput) ...[
          _buildTeamNameInput(),
          const SizedBox(height: 18),
        ],
        _buildMatchupCard(teams, teamCounts),
        const SizedBox(height: 12),
        Text(
          fullTitle,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 20,
            fontWeight: FontWeight.w900,
          ),
        ),
        const SizedBox(height: 8),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            _HeaderInfoChip(
              icon: Icons.groups_rounded,
              label: '${selectedPlayers.length}/$_teamSize selected',
            ),
            if (_hasAnnouncedLineup == true)
              const _HeaderInfoChip(
                icon: Icons.history_rounded,
                label: 'Past Lineup',
              ),
          ],
        ),
        if ((helperText ?? '').trim().isNotEmpty) ...[
          const SizedBox(height: 10),
          Text(
            helperText!,
            style: TextStyle(
              color: Colors.white.withValues(alpha: 0.62),
              fontSize: 13,
              height: 1.35,
            ),
          ),
        ],
      ],
    );
  }

  Widget _buildSortBar() {
    return Align(
      alignment: Alignment.centerRight,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
        decoration: BoxDecoration(
          color: Colors.white.withValues(alpha: 0.05),
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
        ),
        child: PopupMenuButton<_PlayerSortOption>(
          initialValue: _sortOption,
          onSelected: (value) => setState(() => _sortOption = value),
          color: const Color(0xFF0B1524),
          position: PopupMenuPosition.under,
          itemBuilder: (context) => _PlayerSortOption.values
              .map(
                (option) => PopupMenuItem<_PlayerSortOption>(
                  value: option,
                  child: Text(
                    _sortLabel(option),
                    style: const TextStyle(color: Colors.white),
                  ),
                ),
              )
              .toList(),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(
                Icons.sort_rounded,
                color: Color(0xFF63D9FF),
                size: 18,
              ),
              const SizedBox(width: 8),
              Text(
                _sortLabel(_sortOption),
                style: const TextStyle(
                  color: Colors.white,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(width: 4),
              const Icon(Icons.expand_more_rounded, color: Colors.white70),
            ],
          ),
        ),
      ),
    );
  }

  List<_MatchHeaderTeam> _buildHeaderTeams(
    FixtureDetail? fixture,
    List<PlayerPoolItem> allPlayers,
  ) {
    final participants = fixture?.summary.participants ?? const <FixtureParticipant>[];
    if (participants.isNotEmpty) {
      return participants
          .take(2)
          .map(
            (participant) => _MatchHeaderTeam(
              teamName: participant.teamName,
              shortName: participant.displayShort,
              logoUrl: participant.logoUrl,
            ),
          )
          .toList();
    }

    return _orderedTeamNames(allPlayers)
        .take(2)
        .map(
          (teamName) => _MatchHeaderTeam(
            teamName: teamName,
            shortName: _teamBadge(teamName),
            logoUrl: null,
          ),
        )
        .toList();
  }

  Widget _buildMatchupCard(
    List<_MatchHeaderTeam> teams,
    Map<String, int> teamCounts,
  ) {
    final leftTeam = teams.first;
    final rightTeam = teams.length > 1 ? teams[1] : teams.first;
    final leftCount = _teamSelectionCount(leftTeam, teamCounts);
    final rightCount = _teamSelectionCount(rightTeam, teamCounts);

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.04),
        borderRadius: BorderRadius.circular(22),
        border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
      ),
      child: Row(
        children: [
          Expanded(
            child: _MatchupTeamStat(
              team: leftTeam,
              count: leftCount,
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 10),
            child: Text(
              'VS',
              style: TextStyle(
                color: Colors.white.withValues(alpha: 0.72),
                fontSize: 15,
                fontWeight: FontWeight.w900,
              ),
            ),
          ),
          Expanded(
            child: _MatchupTeamStat(
              team: rightTeam,
              count: rightCount,
              reversed: true,
            ),
          ),
        ],
      ),
    );
  }

  int _teamSelectionCount(
    _MatchHeaderTeam team,
    Map<String, int> teamCounts,
  ) {
    for (final entry in teamCounts.entries) {
      if (_matchesTeam(entry.key, team)) {
        return entry.value;
      }
    }
    return 0;
  }

  bool _matchesTeam(String playerTeamName, _MatchHeaderTeam team) {
    final normalizedPlayerTeam = _normalizeTeamKey(playerTeamName);
    return normalizedPlayerTeam == _normalizeTeamKey(team.teamName) ||
        normalizedPlayerTeam == _normalizeTeamKey(team.shortName);
  }

  String _normalizeTeamKey(String value) {
    return value.toUpperCase().replaceAll(RegExp(r'[^A-Z0-9]'), '');
  }

  Widget _buildBackupSlotsCompact(List<PlayerPoolItem> substitutePlayers) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'Backup priority',
          style: TextStyle(
            color: Colors.white,
            fontSize: 15,
            fontWeight: FontWeight.w800,
          ),
        ),
        const SizedBox(height: 6),
        Text(
          'B1 replaces first, then B2, B3, and B4.',
          style: TextStyle(
            color: Colors.white.withValues(alpha: 0.62),
            fontSize: 12,
          ),
        ),
        const SizedBox(height: 14),
        Row(
          children: List.generate(_maxSubstitutes, (index) {
            final player = index < substitutePlayers.length
                ? substitutePlayers[index]
                : null;
            final selected = player != null;
            return Expanded(
              child: Padding(
                padding: EdgeInsets.only(
                  right: index == _maxSubstitutes - 1 ? 0 : 10,
                ),
                child: Container(
                  height: 82,
                  decoration: BoxDecoration(
                    color: selected
                        ? const Color(0xFF17365A)
                        : Colors.white.withValues(alpha: 0.03),
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(
                      color: selected
                          ? const Color(0xFF63D9FF)
                          : Colors.white.withValues(alpha: 0.08),
                    ),
                  ),
                  padding: const EdgeInsets.all(10),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text(
                        'B${index + 1}',
                        style: TextStyle(
                          color: selected
                              ? Colors.white
                              : Colors.white.withValues(alpha: 0.55),
                          fontSize: 20,
                          fontWeight: FontWeight.w900,
                        ),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        player == null ? 'Empty' : _shortName(player.name),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          color: Colors.white.withValues(alpha: 0.72),
                          fontSize: 11,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            );
          }),
        ),
      ],
    );
  }

  Widget _buildRoleTabs({
    required List<String> roles,
    required String activeRole,
  }) {
    return SizedBox(
      height: 58,
      child: ListView.separated(
        padding: const EdgeInsets.symmetric(vertical: 2),
        scrollDirection: Axis.horizontal,
        itemCount: roles.length,
        separatorBuilder: (_, _) => const SizedBox(width: 10),
        itemBuilder: (context, index) {
          final role = roles[index];
          final isActive = role == activeRole;

          return Center(
            child: InkWell(
              borderRadius: BorderRadius.circular(18),
              onTap: () => setState(() => _activeRole = role),
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 180),
                constraints: const BoxConstraints(minHeight: 56),
                padding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 8,
                ),
                decoration: BoxDecoration(
                  color: isActive
                      ? const Color(0xFF17365A)
                      : Colors.white.withValues(alpha: 0.05),
                  borderRadius: BorderRadius.circular(18),
                  border: Border.all(
                    color: isActive
                        ? const Color(0xFF63D9FF)
                        : Colors.white.withValues(alpha: 0.08),
                  ),
                ),
                child: Center(
                  child: Text(
                    role,
                    style: TextStyle(
                      color: isActive
                          ? Colors.white
                          : Colors.white.withValues(alpha: 0.72),
                      fontSize: 14,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
              ),
            ),
          );
        },
      ),
    );
  }

  void _showGroundPreviewSheet(
    List<PlayerPoolItem> allPlayers,
    List<PlayerPoolItem> selectedPlayers,
  ) {
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) {
        return FractionallySizedBox(
          heightFactor: 0.92,
          child: Container(
            decoration: const BoxDecoration(
              color: Color(0xFF06101D),
              borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
            ),
            child: SafeArea(
              top: false,
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
                  Padding(
                    padding: const EdgeInsets.fromLTRB(18, 4, 18, 12),
                    child: Row(
                      children: [
                        const Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'GROUND PREVIEW',
                                style: TextStyle(
                                  color: Colors.white,
                                  fontSize: 20,
                                  fontWeight: FontWeight.w900,
                                ),
                              ),
                              SizedBox(height: 4),
                              Text(
                                'Dream11-style field view of your selected XI',
                                style: TextStyle(
                                  color: Colors.white70,
                                  fontSize: 13,
                                ),
                              ),
                            ],
                          ),
                        ),
                        IconButton(
                          onPressed: () => Navigator.of(context).pop(),
                          icon: const Icon(
                            Icons.close_rounded,
                            color: Colors.white,
                          ),
                        ),
                      ],
                    ),
                  ),
                  Expanded(
                    child: SingleChildScrollView(
                      padding: const EdgeInsets.fromLTRB(16, 0, 16, 20),
                      child: Column(
                        children: [
                          _buildGroundPreviewCard(allPlayers, selectedPlayers),
                          const SizedBox(height: 16),
                          _buildPreviewLegend(allPlayers),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }

  Widget _buildGroundPreviewCard(
    List<PlayerPoolItem> allPlayers,
    List<PlayerPoolItem> selectedPlayers,
  ) {
    final wicketKeepers = _playersForRole(selectedPlayers, 'WK');
    final batters = _playersForRole(selectedPlayers, 'BAT');
    final allRounders = _playersForRole(selectedPlayers, 'AR');
    final bowlers = _playersForRole(selectedPlayers, 'BOWL');

    return Container(
      padding: const EdgeInsets.fromLTRB(14, 18, 14, 18),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(28),
        gradient: const LinearGradient(
          colors: [Color(0xFF1E7A43), Color(0xFF126239), Color(0xFF0A4425)],
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
        ),
        border: Border.all(color: Colors.white.withValues(alpha: 0.14)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.28),
            blurRadius: 24,
            offset: const Offset(0, 12),
          ),
        ],
      ),
      child: Stack(
        children: [
          Positioned.fill(child: _buildGroundStripeOverlay()),
          Column(
            children: [
              _buildGroundRoleRow('WK', wicketKeepers, allPlayers),
              const SizedBox(height: 18),
              _buildPitchDivider(),
              const SizedBox(height: 18),
              _buildGroundRoleRow('BAT', batters, allPlayers),
              const SizedBox(height: 18),
              _buildPitchDivider(),
              const SizedBox(height: 18),
              _buildGroundRoleRow('AR', allRounders, allPlayers),
              const SizedBox(height: 18),
              _buildPitchDivider(),
              const SizedBox(height: 18),
              _buildGroundRoleRow('BOWL', bowlers, allPlayers),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildGroundStripeOverlay() {
    return Opacity(
      opacity: 0.10,
      child: Column(
        children: List.generate(
          12,
          (index) => Expanded(
            child: Container(
              decoration: BoxDecoration(
                border: Border(
                  bottom: BorderSide(
                    color: index.isEven ? Colors.white : Colors.transparent,
                    width: 1,
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildPitchDivider() {
    return Row(
      children: [
        Expanded(
          child: Divider(color: Colors.white.withValues(alpha: 0.18), thickness: 1),
        ),
        Container(
          width: 44,
          height: 44,
          margin: const EdgeInsets.symmetric(horizontal: 14),
          decoration: BoxDecoration(
            color: Colors.white.withValues(alpha: 0.08),
            shape: BoxShape.circle,
            border: Border.all(color: Colors.white.withValues(alpha: 0.12)),
          ),
          alignment: Alignment.center,
          child: const Text(
            'P',
            style: TextStyle(color: Colors.white, fontWeight: FontWeight.w900),
          ),
        ),
        Expanded(
          child: Divider(color: Colors.white.withValues(alpha: 0.18), thickness: 1),
        ),
      ],
    );
  }

  Widget _buildGroundRoleRow(
    String role,
    List<PlayerPoolItem> players,
    List<PlayerPoolItem> allPlayers,
  ) {
    final title = switch (role) {
      'WK' => 'WICKET-KEEPERS',
      'BAT' => 'BATTERS',
      'AR' => 'ALL-ROUNDERS',
      'BOWL' => 'BOWLERS',
      _ => role,
    };

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(
          title,
          style: TextStyle(
            color: Colors.white.withValues(alpha: 0.9),
            fontSize: 12,
            fontWeight: FontWeight.w900,
            letterSpacing: 0.6,
          ),
        ),
        const SizedBox(height: 10),
        if (players.isEmpty)
          _buildEmptyRoleSlot(role)
        else
          Wrap(
            alignment: WrapAlignment.center,
            spacing: 10,
            runSpacing: 12,
            children: players
                .map((player) => _buildGroundPlayer(player, allPlayers))
                .toList(),
          ),
      ],
    );
  }

  Widget _buildEmptyRoleSlot(String role) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: Colors.black.withValues(alpha: 0.16),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.white.withValues(alpha: 0.10)),
      ),
      child: Text(
        'Choose $role',
        style: TextStyle(
          color: Colors.white.withValues(alpha: 0.8),
          fontSize: 12,
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }

  Widget _buildPreviewLegend(List<PlayerPoolItem> allPlayers) {
    final teams = _orderedTeamNames(allPlayers).take(2).toList();
    final palettes = _teamPalettes(allPlayers);

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.04),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
      ),
      child: Wrap(
        spacing: 16,
        runSpacing: 12,
        children: [
          const _PreviewLegendItem(color: Color(0xFF39E48A), label: 'Captain'),
          const _PreviewLegendItem(
            color: Color(0xFF63D9FF),
            label: 'Vice Captain',
          ),
          for (final team in teams)
            _PreviewLegendItem(
              color: _paletteForTeam(palettes, team).primary,
              label: team,
            ),
        ],
      ),
    );
  }

  Widget _buildGroundPlayer(
    PlayerPoolItem player,
    List<PlayerPoolItem> allPlayers,
  ) {
    final palette = _paletteForTeam(_teamPalettes(allPlayers), player.teamName);
    final isCaptain = _captainId == player.playerId;
    final isViceCaptain = _viceCaptainId == player.playerId;

    return SizedBox(
      width: 88,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Stack(
            clipBehavior: Clip.none,
            children: [
              _buildPlayerAvatar(player, size: 56, palette: palette),
              if (isCaptain || isViceCaptain)
                Positioned(
                  top: -4,
                  right: -8,
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 6,
                      vertical: 3,
                    ),
                    decoration: BoxDecoration(
                      color: isCaptain
                          ? const Color(0xFF39E48A)
                          : const Color(0xFF63D9FF),
                      borderRadius: BorderRadius.circular(999),
                    ),
                    child: Text(
                      isCaptain ? 'C' : 'VC',
                      style: const TextStyle(
                        color: Color(0xFF06111D),
                        fontSize: 10,
                        fontWeight: FontWeight.w900,
                      ),
                    ),
                  ),
                ),
            ],
          ),
          const SizedBox(height: 6),
          Text(
            _shortName(player.name),
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            textAlign: TextAlign.center,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 11,
              fontWeight: FontWeight.w800,
              height: 1.2,
            ),
          ),
          const SizedBox(height: 2),
          Text(
            _teamBadge(player.teamName),
            style: TextStyle(
              color: Colors.white.withValues(alpha: 0.78),
              fontSize: 10,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPlayerAvatar(
    PlayerPoolItem player, {
    required double size,
    required _TeamPalette palette,
  }) {
    final imageUrl = _playerImageUrl(player);

    return Container(
      width: size,
      height: size,
      padding: const EdgeInsets.all(2.5),
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        gradient: LinearGradient(
          colors: [palette.primary, palette.secondary],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        boxShadow: [
          BoxShadow(
            color: palette.primary.withValues(alpha: 0.22),
            blurRadius: 12,
            offset: const Offset(0, 5),
          ),
        ],
      ),
      child: Container(
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          color: const Color(0xFF0D1625),
          border: Border.all(color: Colors.black.withValues(alpha: 0.18)),
        ),
        child: ClipOval(
          child: imageUrl != null
              ? Image.network(
                  imageUrl,
                  fit: BoxFit.cover,
                  errorBuilder: (_, _, _) =>
                      _buildAvatarFallback(player, palette),
                )
              : _buildAvatarFallback(player, palette),
        ),
      ),
    );
  }

  Widget _buildAvatarFallback(PlayerPoolItem player, _TeamPalette palette) {
    return Container(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: [
            palette.primary.withValues(alpha: 0.9),
            palette.secondary.withValues(alpha: 0.75),
          ],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
      ),
      alignment: Alignment.center,
      child: Text(
        _playerInitials(player.name),
        style: const TextStyle(
          color: Colors.white,
          fontSize: 13,
          fontWeight: FontWeight.w900,
        ),
      ),
    );
  }

  Widget _buildEmptyState({
    String message = 'No players available in this section yet.',
  }) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.04),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
      ),
      child: Text(
        message,
        style: TextStyle(color: Colors.white.withValues(alpha: 0.7), fontSize: 14),
      ),
    );
  }

  Widget _buildTeamNameInput() {
    return TextField(
      controller: _nameController,
      style: const TextStyle(color: Colors.white),
      decoration: InputDecoration(
        labelText: 'Team Name',
        hintText: 'My Team',
        labelStyle: const TextStyle(color: Colors.white70),
        hintStyle: TextStyle(color: Colors.white.withValues(alpha: 0.35)),
        prefixIcon: const Icon(Icons.edit_rounded, color: Colors.white70),
        filled: true,
        fillColor: Colors.white.withValues(alpha: 0.07),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: BorderSide.none,
        ),
      ),
    );
  }

  Widget _buildPlayerCard(
    PlayerPoolItem player,
    List<PlayerPoolItem> allPlayers,
  ) {
    final isSelected = _selectedIds.contains(player.id);
    final isCaptain = _captainId == player.playerId;
    final isViceCaptain = _viceCaptainId == player.playerId;
    final palette = _paletteForTeam(_teamPalettes(allPlayers), player.teamName);

    return AnimatedContainer(
      duration: const Duration(milliseconds: 180),
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: isSelected ? palette.soft : Colors.white.withValues(alpha: 0.045),
        borderRadius: BorderRadius.circular(22),
        border: Border.all(
          color: isSelected
              ? palette.primary.withValues(alpha: 0.48)
              : Colors.white.withValues(alpha: 0.08),
        ),
      ),
      child: Row(
        children: [
          Container(
            width: 4,
            height: 74,
            decoration: BoxDecoration(
              color: palette.primary,
              borderRadius: BorderRadius.circular(999),
            ),
          ),
          const SizedBox(width: 12),
          _buildPlayerAvatar(player, size: 58, palette: palette),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  player.name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 15,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 5),
                Text(
                  player.teamName,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    color: palette.secondary,
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  _roleLabel(_normalizeRole(player.role)),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    color: Colors.white.withValues(alpha: 0.62),
                    fontSize: 12,
                  ),
                ),
                if (isCaptain || isViceCaptain) ...[
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    children: [
                      if (isCaptain)
                        const _SelectionPill(
                          label: 'Captain',
                          color: Color(0xFF39E48A),
                        ),
                      if (isViceCaptain)
                        const _SelectionPill(
                          label: 'Vice Captain',
                          color: Color(0xFF63D9FF),
                        ),
                    ],
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(width: 10),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(
                _availabilityLabel(player),
                style: TextStyle(
                  color: _availabilityColor(player),
                  fontSize: 13,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 12),
              InkWell(
                borderRadius: BorderRadius.circular(16),
                onTap: () => _togglePlayer(player, !isSelected, allPlayers),
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 180),
                  width: 46,
                  height: 46,
                  decoration: BoxDecoration(
                    color: isSelected ? palette.primary : Colors.transparent,
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(
                      color: isSelected
                          ? palette.primary
                          : Colors.white.withValues(alpha: 0.12),
                    ),
                  ),
                  child: Icon(
                    isSelected ? Icons.check_rounded : Icons.add_rounded,
                    color: isSelected ? const Color(0xFF06111D) : Colors.white,
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildSubstituteCard(
    PlayerPoolItem player,
    List<PlayerPoolItem> allPlayers,
  ) {
    final isSelected = _substituteIds.contains(player.id);
    final palette = _paletteForTeam(_teamPalettes(allPlayers), player.teamName);
    final substituteOrder = _substituteIds.toList().indexOf(player.id) + 1;

    return AnimatedContainer(
      duration: const Duration(milliseconds: 180),
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: isSelected ? palette.soft : Colors.white.withValues(alpha: 0.045),
        borderRadius: BorderRadius.circular(22),
        border: Border.all(
          color: isSelected
              ? palette.primary.withValues(alpha: 0.48)
              : Colors.white.withValues(alpha: 0.08),
        ),
      ),
      child: Row(
        children: [
          Container(
            width: 4,
            height: 74,
            decoration: BoxDecoration(
              color: palette.secondary,
              borderRadius: BorderRadius.circular(999),
            ),
          ),
          const SizedBox(width: 12),
          _buildPlayerAvatar(player, size: 58, palette: palette),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  player.name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 15,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 5),
                Text(
                  player.teamName,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    color: palette.secondary,
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  _roleLabel(_normalizeRole(player.role)),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    color: Colors.white.withValues(alpha: 0.62),
                    fontSize: 12,
                  ),
                ),
                if (isSelected) ...[
                  const SizedBox(height: 8),
                  _SelectionPill(
                    label: 'Priority #$substituteOrder',
                    color: const Color(0xFF63D9FF),
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(width: 10),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(
                _availabilityLabel(player),
                style: TextStyle(
                  color: _availabilityColor(player),
                  fontSize: 13,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 12),
              InkWell(
                borderRadius: BorderRadius.circular(16),
                onTap: () => _toggleSubstitute(player, !isSelected),
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 180),
                  width: 46,
                  height: 46,
                  decoration: BoxDecoration(
                    color: isSelected ? palette.secondary : Colors.transparent,
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(
                      color: isSelected
                          ? palette.secondary
                          : Colors.white.withValues(alpha: 0.12),
                    ),
                  ),
                  child: Icon(
                    isSelected ? Icons.check_rounded : Icons.add_rounded,
                    color: isSelected ? const Color(0xFF06111D) : Colors.white,
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildLeadershipCard(
    PlayerPoolItem player,
    List<PlayerPoolItem> allPlayers,
  ) {
    final palette = _paletteForTeam(_teamPalettes(allPlayers), player.teamName);
    final isCaptain = _captainId == player.playerId;
    final isViceCaptain = _viceCaptainId == player.playerId;

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: isCaptain
            ? const Color(0xFF11261A)
            : isViceCaptain
            ? const Color(0xFF102536)
            : Colors.white.withValues(alpha: 0.045),
        borderRadius: BorderRadius.circular(22),
        border: Border.all(
          color: isCaptain
              ? const Color(0xFF39E48A).withValues(alpha: 0.36)
              : isViceCaptain
              ? const Color(0xFF63D9FF).withValues(alpha: 0.36)
              : palette.primary.withValues(alpha: 0.26),
        ),
      ),
      child: Row(
        children: [
          _buildPlayerAvatar(player, size: 56, palette: palette),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  player.name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 15,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  player.teamName,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    color: palette.secondary,
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  '${_roleLabel(_normalizeRole(player.role))} | ${player.teamName}',
                  style: TextStyle(
                    color: Colors.white.withValues(alpha: 0.62),
                    fontSize: 12,
                  ),
                ),
              ],
            ),
          ),
          _LeadershipChoiceButton(
            label: 'C',
            selected: isCaptain,
            color: const Color(0xFF39E48A),
            onTap: () {
              setState(() {
                _captainId = isCaptain ? null : player.playerId;
                if (_viceCaptainId == player.playerId) {
                  _viceCaptainId = null;
                }
              });
            },
          ),
          const SizedBox(width: 8),
          _LeadershipChoiceButton(
            label: 'VC',
            selected: isViceCaptain,
            color: const Color(0xFF63D9FF),
            onTap: () {
              setState(() {
                _viceCaptainId = isViceCaptain ? null : player.playerId;
                if (_captainId == player.playerId) {
                  _captainId = null;
                }
              });
            },
          ),
        ],
      ),
    );
  }

  Widget _buildBottomBar(
    List<PlayerPoolItem> allPlayers,
    List<PlayerPoolItem> selectedPlayers,
    List<PlayerPoolItem> substitutePlayers,
  ) {
    final playersLeft = _teamSize - _selectedIds.length;
    final substitutesLeft = _maxSubstitutes - _substituteIds.length;
    final footerPlayers = _step == _BuilderStep.substitutes
        ? substitutePlayers
        : selectedPlayers;
    final footerCountLabel = _step == _BuilderStep.substitutes
        ? '${substitutePlayers.length}/$_maxSubstitutes backups'
        : '${selectedPlayers.length}/$_teamSize selected';

    return Container(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
      decoration: BoxDecoration(
        color: const Color(0xFF08101D),
        border: Border(top: BorderSide(color: Colors.white.withValues(alpha: 0.06))),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.28),
            blurRadius: 20,
            offset: const Offset(0, -8),
          ),
        ],
      ),
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    _interactionLocked
                        ? 'Match started. Team editing is now locked.'
                        : _step == _BuilderStep.players
                        ? playersLeft == 0
                              ? 'Perfect XI ready. Add substitutes or move ahead.'
                              : '$playersLeft players left to complete your XI.'
                        : _step == _BuilderStep.substitutes
                        ? substitutesLeft == 0
                              ? 'Backup bench ready. Move to captain and vice-captain.'
                              : 'Add up to $substitutesLeft more substitutes, or continue now.'
                        : (_captainId == null || _viceCaptainId == null)
                        ? 'Select captain and vice-captain to finish your team.'
                        : 'Leadership locked. Your team is ready.',
                    style: TextStyle(
                      color: Colors.white.withValues(alpha: 0.78),
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Text(
                  footerCountLabel,
                  style: const TextStyle(
                    color: Color(0xFFFFD36A),
                    fontSize: 13,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            _buildSelectedFooterPills(footerPlayers),
            const SizedBox(height: 12),
            if (_step == _BuilderStep.players)
              Row(
                children: [
                  _buildPreviewButton(allPlayers, selectedPlayers),
                  const SizedBox(width: 12),
                  Expanded(
                    flex: 2,
                    child: SizedBox(
                      height: 54,
                      child: ElevatedButton(
                        onPressed:
                            !_interactionLocked && _selectedIds.length == _teamSize
                            ? () => _continueToSubstitutes(allPlayers)
                            : null,
                        style: ElevatedButton.styleFrom(
                          backgroundColor: const Color(0xFF39E48A),
                          foregroundColor: const Color(0xFF06111D),
                          disabledBackgroundColor: Colors.white.withValues(
                            alpha: 0.08,
                          ),
                          disabledForegroundColor: Colors.white.withValues(
                            alpha: 0.35,
                          ),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(18),
                          ),
                        ),
                        child: Text(
                          _selectedIds.length == _teamSize
                              ? 'Next'
                              : 'Select $playersLeft More',
                          style: const TextStyle(
                            fontSize: 15,
                            fontWeight: FontWeight.w900,
                          ),
                        ),
                      ),
                    ),
                  ),
                ],
              )
            else if (_step == _BuilderStep.substitutes)
              Row(
                children: [
                  _buildPreviewButton(allPlayers, selectedPlayers),
                  const SizedBox(width: 12),
                  Expanded(
                    child: SizedBox(
                      height: 54,
                      child: OutlinedButton(
                        onPressed: _submitting || _interactionLocked
                            ? null
                            : () =>
                                  setState(() => _step = _BuilderStep.players),
                        style: OutlinedButton.styleFrom(
                          foregroundColor: Colors.white,
                          side: BorderSide(
                            color: Colors.white.withValues(alpha: 0.16),
                          ),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(18),
                          ),
                        ),
                        child: const Text(
                          'Back',
                          style: TextStyle(fontWeight: FontWeight.w800),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    flex: 2,
                    child: SizedBox(
                      height: 54,
                      child: ElevatedButton(
                        onPressed: _submitting || _interactionLocked
                            ? null
                            : () => _continueToLeadership(allPlayers),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: const Color(0xFF63D9FF),
                          foregroundColor: const Color(0xFF06111D),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(18),
                          ),
                        ),
                        child: const Text(
                          'Next',
                          style: TextStyle(
                            fontSize: 15,
                            fontWeight: FontWeight.w900,
                          ),
                        ),
                      ),
                    ),
                  ),
                ],
              )
            else
              Row(
                children: [
                  _buildPreviewButton(allPlayers, selectedPlayers),
                  const SizedBox(width: 12),
                  Expanded(
                    child: SizedBox(
                      height: 54,
                      child: OutlinedButton(
                        onPressed: _submitting || _interactionLocked
                            ? null
                            : () => setState(
                                () => _step = _BuilderStep.substitutes,
                              ),
                        style: OutlinedButton.styleFrom(
                          foregroundColor: Colors.white,
                          side: BorderSide(
                            color: Colors.white.withValues(alpha: 0.16),
                          ),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(18),
                          ),
                        ),
                        child: const Text(
                          'Back',
                          style: TextStyle(fontWeight: FontWeight.w800),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    flex: 2,
                    child: SizedBox(
                      height: 54,
                      child: ElevatedButton(
                        onPressed: _submitting || _interactionLocked
                            ? null
                            : () => _submit(allPlayers),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: const Color(0xFF63D9FF),
                          foregroundColor: const Color(0xFF06111D),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(18),
                          ),
                        ),
                        child: _submitting
                            ? const SizedBox(
                                width: 22,
                                height: 22,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  color: Color(0xFF06111D),
                                ),
                              )
                            : Text(
                                widget.contestId != null
                                    ? 'Create Team & Join'
                                    : widget.teamId == null
                                    ? 'Create Team'
                                    : 'Update Team',
                                style: const TextStyle(
                                  fontSize: 15,
                                  fontWeight: FontWeight.w900,
                                ),
                              ),
                      ),
                    ),
                  ),
                ],
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildPreviewButton(
    List<PlayerPoolItem> allPlayers,
    List<PlayerPoolItem> selectedPlayers,
  ) {
    return SizedBox(
      width: 58,
      height: 54,
      child: OutlinedButton(
        onPressed: selectedPlayers.isEmpty
            ? null
            : () => _showGroundPreviewSheet(allPlayers, selectedPlayers),
        style: OutlinedButton.styleFrom(
          foregroundColor: Colors.white,
          side: BorderSide(color: Colors.white.withValues(alpha: 0.16)),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(18),
          ),
          padding: EdgeInsets.zero,
        ),
        child: const Icon(Icons.remove_red_eye_outlined),
      ),
    );
  }

  Widget _buildSelectedFooterPills(List<PlayerPoolItem> selectedPlayers) {
    final roleCounts = _roleDistribution(selectedPlayers);

    final children = <Widget>[
      for (final role in _preferredRoleOrder)
        Padding(
          padding: const EdgeInsets.only(right: 8),
          child: _FooterCountPill(
            label: role,
            value: '${roleCounts[role] ?? 0}',
            color: (roleCounts[role] ?? 0) > 0
                ? const Color(0xFF39E48A)
                : Colors.white54,
          ),
        ),
    ];

    return SizedBox(
      height: 38,
      child: ListView(scrollDirection: Axis.horizontal, children: children),
    );
  }
}

class _HeaderInfoChip extends StatelessWidget {
  const _HeaderInfoChip({
    required this.icon,
    required this.label,
  });

  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.05),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 16, color: const Color(0xFF63D9FF)),
          const SizedBox(width: 8),
          Text(
            label,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 12,
              fontWeight: FontWeight.w800,
            ),
          ),
        ],
      ),
    );
  }
}

class _MatchupTeamStat extends StatelessWidget {
  const _MatchupTeamStat({
    required this.team,
    required this.count,
    this.reversed = false,
  });

  final _MatchHeaderTeam team;
  final int count;
  final bool reversed;

  @override
  Widget build(BuildContext context) {
    final countText = Text(
      '$count',
      style: const TextStyle(
        color: Color(0xFFFFD36A),
        fontSize: 20,
        fontWeight: FontWeight.w900,
      ),
    );
    final nameText = Flexible(
      child: Text(
        team.shortName,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        style: const TextStyle(
          color: Colors.white,
          fontSize: 18,
          fontWeight: FontWeight.w900,
        ),
      ),
    );
    final children = <Widget>[
      _MatchTeamLogo(team: team),
      const SizedBox(width: 10),
      nameText,
      const SizedBox(width: 8),
      countText,
    ];

    return Row(
      mainAxisAlignment: reversed
          ? MainAxisAlignment.end
          : MainAxisAlignment.start,
      children: reversed ? children.reversed.toList() : children,
    );
  }
}

class _MatchTeamLogo extends StatelessWidget {
  const _MatchTeamLogo({
    required this.team,
  });

  final _MatchHeaderTeam team;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 38,
      height: 38,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        border: Border.all(color: Colors.white.withValues(alpha: 0.12)),
        gradient: const LinearGradient(
          colors: [Color(0xFF29344E), Color(0xFF111827)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
      ),
      clipBehavior: Clip.antiAlias,
      child: (team.logoUrl ?? '').trim().isEmpty
          ? Center(
              child: Text(
                team.shortName,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 11,
                  fontWeight: FontWeight.w900,
                ),
              ),
            )
          : Image.network(
              team.logoUrl!,
              fit: BoxFit.cover,
              errorBuilder: (_, _, _) => Center(
                child: Text(
                  team.shortName,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 11,
                    fontWeight: FontWeight.w900,
                  ),
                ),
              ),
            ),
    );
  }
}

class _SelectionPill extends StatelessWidget {
  const _SelectionPill({required this.label, required this.color});

  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.14),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: color.withValues(alpha: 0.3)),
      ),
      child: Text(
        label,
        style: TextStyle(
          color: color,
          fontSize: 11,
          fontWeight: FontWeight.w800,
        ),
      ),
    );
  }
}

class _LeadershipChoiceButton extends StatelessWidget {
  const _LeadershipChoiceButton({
    required this.label,
    required this.selected,
    required this.color,
    required this.onTap,
  });

  final String label;
  final bool selected;
  final Color color;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      borderRadius: BorderRadius.circular(999),
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        width: 56,
        height: 56,
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: selected ? color : Colors.white,
          shape: BoxShape.circle,
          border: Border.all(
            color: selected ? color : Colors.white.withValues(alpha: 0.18),
          ),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.16),
              blurRadius: 10,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: Text(
          label,
          style: TextStyle(
            color: selected ? const Color(0xFF06111D) : const Color(0xFF152239),
            fontSize: 15,
            fontWeight: FontWeight.w900,
          ),
        ),
      ),
    );
  }
}

class _PreviewLegendItem extends StatelessWidget {
  const _PreviewLegendItem({required this.color, required this.label});

  final Color color;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 14,
          height: 14,
          decoration: BoxDecoration(color: color, shape: BoxShape.circle),
        ),
        const SizedBox(width: 8),
        Text(
          label,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 12,
            fontWeight: FontWeight.w700,
          ),
        ),
      ],
    );
  }
}

class _FooterCountPill extends StatelessWidget {
  const _FooterCountPill({
    required this.label,
    required this.value,
    required this.color,
  });

  final String label;
  final String value;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: color.withValues(alpha: 0.28)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            label,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 11,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(width: 8),
          Text(
            value,
            style: TextStyle(
              color: color,
              fontSize: 11,
              fontWeight: FontWeight.w900,
            ),
          ),
        ],
      ),
    );
  }
}

class _TeamPalette {
  const _TeamPalette({
    required this.primary,
    required this.secondary,
    required this.soft,
  });

  final Color primary;
  final Color secondary;
  final Color soft;
}

class _MatchHeaderTeam {
  const _MatchHeaderTeam({
    required this.teamName,
    required this.shortName,
    required this.logoUrl,
  });

  final String teamName;
  final String shortName;
  final String? logoUrl;
}

import 'package:intl/intl.dart';

import '../../../../core/network/api_helpers.dart';

class FixtureParticipant {
  final String externalTeamId;
  final String teamName;
  final String shortName;
  final String logoUrl;
  final bool isHome;

  const FixtureParticipant({
    required this.externalTeamId,
    required this.teamName,
    required this.shortName,
    required this.logoUrl,
    required this.isHome,
  });

  factory FixtureParticipant.fromJson(Map<String, dynamic> json) {
    return FixtureParticipant(
      externalTeamId: asString(json['externalTeamId']),
      teamName: asString(json['teamName'], fallback: 'Team'),
      shortName: asString(json['shortName']),
      logoUrl: asString(json['logoUrl']),
      isHome: json['isHome'] == true,
    );
  }

  String get displayShort => shortName.isNotEmpty ? shortName : teamName;
}

class FixtureSummary {
  final String id;
  final String externalFixtureId;
  final String externalLeagueId;
  final String title;
  final String status;
  final DateTime? startTime;
  final DateTime? deadlineTime;
  final String venue;
  final String league;
  final List<FixtureParticipant> participants;

  const FixtureSummary({
    required this.id,
    required this.externalFixtureId,
    required this.externalLeagueId,
    required this.title,
    required this.status,
    required this.startTime,
    required this.deadlineTime,
    required this.venue,
    required this.league,
    required this.participants,
  });

  factory FixtureSummary.fromJson(Map<String, dynamic> json) {
    final participantsRaw = json['participants'] is List
        ? List<dynamic>.from(json['participants'] as List)
        : const <dynamic>[];

    final participants = participantsRaw
        .map(
          (e) =>
              FixtureParticipant.fromJson(Map<String, dynamic>.from(e as Map)),
        )
        .toList();

    final home = _findParticipant(participants, true);
    final away = _findParticipant(participants, false);

    final teamAName =
        home?.teamName ??
        asString(
          json['teamAName'] ?? json['localTeamName'],
          fallback: 'Team A',
        );
    final teamBName =
        away?.teamName ??
        asString(
          json['teamBName'] ?? json['visitorTeamName'],
          fallback: 'Team B',
        );

    return FixtureSummary(
      id: asString(json['fixtureId'] ?? json['id']),
      externalFixtureId: asString(json['externalFixtureId']),
      externalLeagueId: asString(json['externalLeagueId'] ?? json['leagueId']),
      title: asString(json['title'], fallback: '$teamAName vs $teamBName'),
      status: asString(json['status'], fallback: 'UPCOMING'),
      startTime: _parseDate(
        json['startTime'] ?? json['startingAt'] ?? json['matchTime'],
      ),
      deadlineTime: _parseDate(json['deadlineTime']),
      venue: asString(json['venue'] ?? json['venueName']),
      league: asString(
        json['league'] ?? json['tournament'],
        fallback: 'Cricket',
      ),
      participants: participants,
    );
  }

  FixtureParticipant? get homeTeam => _findParticipant(participants, true);
  FixtureParticipant? get awayTeam => _findParticipant(participants, false);

  String get teamAName =>
      homeTeam?.teamName ??
      (participants.isNotEmpty ? participants.first.teamName : 'Team A');
  String get teamBName {
    if (awayTeam != null) return awayTeam!.teamName;
    if (participants.length > 1) return participants[1].teamName;
    return 'Team B';
  }

  String get teamAShort => homeTeam?.displayShort ?? _shortFromName(teamAName);
  String get teamBShort {
    if (awayTeam != null) return awayTeam!.displayShort;
    if (participants.length > 1) return participants[1].displayShort;
    return _shortFromName(teamBName);
  }

  String get statusLabel {
    final s = status.trim().toUpperCase();
    switch (s) {
      case 'NS':
      case 'NOT STARTED':
        return 'UPCOMING';
      case 'LIVE':
      case 'IN PLAY':
      case '1ST INNINGS':
      case '2ND INNINGS':
      case '3RD INNINGS':
      case '4TH INNINGS':
      case 'INNINGS BREAK':
      case 'INT.':
      case 'INTERRUPTED':
      case 'DELAYED':
        return 'LIVE';
      case 'FT':
      case 'FINISHED':
      case 'COMPLETED':
        return 'COMPLETED';
      case 'CANCL.':
      case 'CANCELLED':
      case 'ABAN.':
      case 'ABANDONED':
      case 'NO RESULT':
      case 'NR':
        return 'CANCELLED';
      default:
        return s.isEmpty ? 'UPCOMING' : s;
    }
  }

  String get timeText => startTime == null
      ? '-'
      : DateFormat('dd MMM, hh:mm a').format(startTime!);

  String get homeSubtitle {
    if (startTime == null) return league;
    return '${DateFormat('dd MMM • hh:mm a').format(startTime!)}${league.isNotEmpty ? ' • $league' : ''}';
  }
}

class FixtureDetail {
  final FixtureSummary summary;
  final String teamAShort;
  final String teamBShort;
  final String format;
  final String note;
  final FixtureLiveData? liveData;

  const FixtureDetail({
    required this.summary,
    required this.teamAShort,
    required this.teamBShort,
    required this.format,
    required this.note,
    required this.liveData,
  });

  factory FixtureDetail.fromJson(Map<String, dynamic> json) {
    final summary = FixtureSummary.fromJson(json);
    final liveDataJson = json['fixtureLiveData'];
    return FixtureDetail(
      summary: summary,
      teamAShort: summary.teamAShort,
      teamBShort: summary.teamBShort,
      format: asString(json['format'], fallback: 'T20'),
      note: asString(json['note']),
      liveData: liveDataJson is Map
          ? FixtureLiveData.fromJson(
              Map<String, dynamic>.from(liveDataJson),
            )
          : null,
    );
  }
}

class FixtureLiveData {
  final bool live;
  final String note;
  final bool superOver;
  final String superOverStatus;
  final String lastPeriod;
  final int? revisedTarget;
  final int? revisedOvers;
  final List<FixtureInningsScore> innings;

  const FixtureLiveData({
    required this.live,
    required this.note,
    required this.superOver,
    required this.superOverStatus,
    required this.lastPeriod,
    required this.revisedTarget,
    required this.revisedOvers,
    required this.innings,
  });

  factory FixtureLiveData.fromJson(Map<String, dynamic> json) {
    final inningsRaw = json['innings'] is List
        ? List<dynamic>.from(json['innings'] as List)
        : const <dynamic>[];

    return FixtureLiveData(
      live: asBool(json['live']),
      note: asString(json['note']),
      superOver: asBool(json['superOver']),
      superOverStatus: asString(json['superOverStatus']),
      lastPeriod: asString(json['lastPeriod']),
      revisedTarget: _asNullableInt(json['revisedTarget']),
      revisedOvers: _asNullableInt(json['revisedOvers']),
      innings: inningsRaw
          .map(
            (e) => FixtureInningsScore.fromJson(
              Map<String, dynamic>.from(e as Map),
            ),
          )
          .toList(),
    );
  }

  bool get hasContent => innings.isNotEmpty || note.isNotEmpty || superOver;
}

class FixtureInningsScore {
  final String scoreboard;
  final String label;
  final String? teamId;
  final String teamName;
  final String shortName;
  final int? score;
  final int? wicketsOut;
  final String overs;
  final bool current;
  final bool superOver;
  final String summary;

  const FixtureInningsScore({
    required this.scoreboard,
    required this.label,
    required this.teamId,
    required this.teamName,
    required this.shortName,
    required this.score,
    required this.wicketsOut,
    required this.overs,
    required this.current,
    required this.superOver,
    required this.summary,
  });

  factory FixtureInningsScore.fromJson(Map<String, dynamic> json) {
    return FixtureInningsScore(
      scoreboard: asString(json['scoreboard']),
      label: asString(json['label'], fallback: 'Innings'),
      teamId: asStringOrNull(json['teamId']),
      teamName: asString(json['teamName'], fallback: 'Team'),
      shortName: asString(
        json['shortName'],
        fallback: asString(json['teamName'], fallback: 'Team'),
      ),
      score: _asNullableInt(json['score']),
      wicketsOut: _asNullableInt(json['wicketsOut']),
      overs: asString(json['overs']),
      current: asBool(json['current']),
      superOver: asBool(json['superOver']),
      summary: asString(json['summary']),
    );
  }

  String get scoreline {
    if (score == null) return '-';
    if (wicketsOut == null) return '$score';
    return '$score/$wicketsOut';
  }

  String get oversText => overs.isEmpty ? '' : '$overs overs';
}

FixtureParticipant? _findParticipant(
  List<FixtureParticipant> items,
  bool isHome,
) {
  for (final item in items) {
    if (item.isHome == isHome) return item;
  }
  return null;
}

String _shortFromName(String name) {
  final clean = name.trim();
  if (clean.isEmpty) return 'TBD';
  final parts = clean.split(' ').where((e) => e.isNotEmpty).toList();
  if (parts.length == 1) {
    return parts.first.length <= 4
        ? parts.first.toUpperCase()
        : parts.first.substring(0, 3).toUpperCase();
  }
  return parts.take(3).map((e) => e[0].toUpperCase()).join();
}

DateTime? _parseDate(dynamic raw) {
  final value = asString(raw);
  if (value.isEmpty) return null;
  return DateTime.tryParse(value)?.toLocal();
}

int? _asNullableInt(dynamic value) {
  if (value == null) return null;
  final parsed = asInt(value, fallback: -1);
  return parsed < 0 ? null : parsed;
}

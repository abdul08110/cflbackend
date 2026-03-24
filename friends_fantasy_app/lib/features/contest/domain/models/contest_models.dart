import '../../../../core/network/api_helpers.dart';

class ContestSummary {
  final String id;
  final String name;
  final String contestType;
  final String? communityId;
  final String? fixtureId;
  final String status;
  final double entryFee;
  final double prizePool;
  final int spots;
  final int joined;
  final int winnersCount;

  const ContestSummary({
    required this.id,
    required this.name,
    required this.contestType,
    required this.communityId,
    this.fixtureId,
    required this.status,
    required this.entryFee,
    required this.prizePool,
    required this.spots,
    required this.joined,
    required this.winnersCount,
  });

  factory ContestSummary.fromJson(Map<String, dynamic> json) {
    final data = json['contest'] is Map<String, dynamic>
        ? Map<String, dynamic>.from(json['contest'] as Map)
        : json;

    return ContestSummary(
      id: asString(data['contestId'] ?? data['id']),
      name: asString(
        data['contestName'] ?? data['name'] ?? data['title'],
        fallback: 'Contest',
      ),
      contestType: asString(data['contestType'], fallback: 'PUBLIC'),
      communityId: asStringOrNull(data['communityId']),
      fixtureId: asStringOrNull(data['fixtureId']),
      status: asString(data['status'], fallback: 'OPEN'),
      entryFee: asDouble(
        data['entryFeePoints'] ??
            data['entryFee'] ??
            data['joinAmount'] ??
            data['fee'],
      ),
      prizePool: asDouble(
        data['prizePoolPoints'] ??
            data['prizePool'] ??
            data['totalPrize'] ??
            data['prize'],
      ),
      spots: asInt(data['maxSpots'] ?? data['spots'] ?? data['totalSpots']),
      joined: asInt(
        data['spotsFilled'] ??
            data['joined'] ??
            data['filledSpots'] ??
            data['joinedSpots'],
      ),
      winnersCount: asInt(
        data['winnerCount'] ?? data['winnersCount'] ?? data['winners'],
      ),
    );
  }
}

class ContestEntrySummary {
  final String id;
  final String contestId;
  final String? teamId;
  final String teamName;
  final double fantasyPoints;
  final int rank;
  final int prizePointsAwarded;
  final String status;

  const ContestEntrySummary({
    required this.id,
    required this.contestId,
    required this.teamId,
    required this.teamName,
    required this.fantasyPoints,
    required this.rank,
    required this.prizePointsAwarded,
    required this.status,
  });

  bool get hasSelectedTeam => teamId != null && teamId!.isNotEmpty;

  factory ContestEntrySummary.fromJson(Map<String, dynamic> json) {
    return ContestEntrySummary(
      id: asString(json['entryId'] ?? json['id']),
      contestId: asString(json['contestId']),
      teamId: asStringOrNull(json['teamId']),
      teamName: asString(json['teamName'], fallback: 'No team selected'),
      fantasyPoints: asDouble(json['fantasyPoints'] ?? json['points']),
      rank: asInt(json['rankNo'] ?? json['rank']),
      prizePointsAwarded: asInt(json['prizePointsAwarded']),
      status: asString(json['status'], fallback: 'JOINED'),
    );
  }
}

class PrizeBreakdown {
  final String rank;
  final String prize;

  const PrizeBreakdown({required this.rank, required this.prize});

  factory PrizeBreakdown.fromJson(Map<String, dynamic> json) {
    if (json.containsKey('rankFrom') && json.containsKey('rankTo')) {
      final rankFrom = asInt(json['rankFrom']);
      final rankTo = asInt(json['rankTo']);
      final prizePoints = asDouble(
        json['prizePoints'] ?? json['prize'] ?? json['amount'],
      );

      final rankLabel = rankFrom == rankTo
          ? '#$rankFrom'
          : '#$rankFrom - #$rankTo';

      return PrizeBreakdown(
        rank: rankLabel,
        prize: '${prizePoints.toStringAsFixed(0)} pts',
      );
    }

    return PrizeBreakdown(
      rank: asString(json['rank'] ?? json['ranks'] ?? 'Winner'),
      prize: asString(
        json['prize'] ?? json['amount'] ?? json['reward'] ?? '0 pts',
      ),
    );
  }
}

class ContestDetail {
  final ContestSummary contest;
  final String fixtureId;
  final String firstPrize;
  final String description;
  final List<PrizeBreakdown> prizes;

  const ContestDetail({
    required this.contest,
    required this.fixtureId,
    required this.firstPrize,
    required this.description,
    required this.prizes,
  });

  factory ContestDetail.fromJson(Map<String, dynamic> json) {
    final data = unwrapMap(json);

    return ContestDetail(
      contest: ContestSummary.fromJson(data),
      fixtureId: asString(data['fixtureId']),
      firstPrize: asString(
        data['firstPrizePoints'] ??
            data['firstPrize'] ??
            data['topPrize'] ??
            data['rank1Prize'],
      ),
      description: asString(data['description'] ?? ''),
      prizes:
          ((data['prizes'] as List?) ??
                  (data['prizeBreakdown'] as List?) ??
                  const [])
              .map(
                (e) => PrizeBreakdown.fromJson(
                  Map<String, dynamic>.from(e as Map),
                ),
              )
              .toList(),
    );
  }
}

class LeaderboardEntry {
  final String entryId;
  final String userId;
  final String? teamId;
  final int rank;
  final String username;
  final String teamName;
  final double points;
  final double winnings;
  final String status;

  const LeaderboardEntry({
    required this.entryId,
    required this.userId,
    required this.teamId,
    required this.rank,
    required this.username,
    required this.teamName,
    required this.points,
    required this.winnings,
    required this.status,
  });

  factory LeaderboardEntry.fromJson(Map<String, dynamic> json) =>
      LeaderboardEntry(
        entryId: asString(json['entryId']),
        userId: asString(json['userId']),
        teamId: asStringOrNull(json['teamId']),
        rank: asInt(json['position'] ?? json['rankNo'] ?? json['rank']),
        username: asString(json['username']),
        teamName: asString(json['teamName'], fallback: 'No team selected'),
        points: asDouble(json['points'] ?? json['fantasyPoints']),
        winnings: asDouble(
          json['winnings'] ??
              json['winningAmount'] ??
              json['prizePointsAwarded'],
        ),
        status: asString(json['status'], fallback: 'JOINED'),
      );
}

class MyContestEntry {
  final String contestName;
  final String fixtureTitle;
  final double entryFee;
  final double winnings;
  final double points;
  final String status;

  const MyContestEntry({
    required this.contestName,
    required this.fixtureTitle,
    required this.entryFee,
    required this.winnings,
    required this.points,
    required this.status,
  });

  factory MyContestEntry.fromJson(Map<String, dynamic> json) => MyContestEntry(
    contestName: asString(json['contestName']),
    fixtureTitle: asString(json['fixtureTitle']),
    entryFee: asDouble(json['entryFeePoints'] ?? json['entryFee']),
    winnings: asDouble(json['winnings']),
    points: asDouble(json['points']),
    status: asString(json['status']),
  );
}

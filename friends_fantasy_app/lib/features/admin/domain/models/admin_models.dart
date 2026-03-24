import '../../../../core/network/api_helpers.dart';

class AdminFixtureParticipant {
  final int externalTeamId;
  final String teamName;
  final String? shortName;
  final String? logoUrl;
  final bool isHome;

  const AdminFixtureParticipant({
    required this.externalTeamId,
    required this.teamName,
    this.shortName,
    this.logoUrl,
    required this.isHome,
  });

  factory AdminFixtureParticipant.fromJson(Map<String, dynamic> json) {
    return AdminFixtureParticipant(
      externalTeamId: (json['externalTeamId'] ?? 0) as int,
      teamName: (json['teamName'] ?? '') as String,
      shortName: json['shortName'] as String?,
      logoUrl: json['logoUrl'] as String?,
      isHome: (json['isHome'] ?? false) as bool,
    );
  }
}

class AdminFixture {
  final int fixtureId;
  final int externalFixtureId;
  final String title;
  final String status;
  final DateTime startTime;
  final DateTime deadlineTime;
  final List<AdminFixtureParticipant> participants;

  const AdminFixture({
    required this.fixtureId,
    required this.externalFixtureId,
    required this.title,
    required this.status,
    required this.startTime,
    required this.deadlineTime,
    required this.participants,
  });

  factory AdminFixture.fromJson(Map<String, dynamic> json) {
    final participantsJson = (json['participants'] as List<dynamic>? ?? []);
    return AdminFixture(
      fixtureId: (json['fixtureId'] ?? 0) as int,
      externalFixtureId: (json['externalFixtureId'] ?? 0) as int,
      title: (json['title'] ?? '') as String,
      status: (json['status'] ?? '') as String,
      startTime: DateTime.parse(json['startTime'] as String),
      deadlineTime: DateTime.parse(json['deadlineTime'] as String),
      participants: participantsJson
          .map(
            (e) => AdminFixtureParticipant.fromJson(e as Map<String, dynamic>),
          )
          .toList(),
    );
  }
}

class ContestPrizeDraft {
  final int rankFrom;
  final int rankTo;
  final int prizePoints;

  const ContestPrizeDraft({
    required this.rankFrom,
    required this.rankTo,
    required this.prizePoints,
  });

  Map<String, dynamic> toJson() {
    return {'rankFrom': rankFrom, 'rankTo': rankTo, 'prizePoints': prizePoints};
  }
}

class CreateContestPayload {
  final String contestName;
  final int maxSpots;
  final int entryFeePoints;
  final int prizePoolPoints;
  final int winnerCount;
  final bool joinConfirmRequired;
  final int scoringTemplateId;
  final List<ContestPrizeDraft> prizes;

  const CreateContestPayload({
    required this.contestName,
    required this.maxSpots,
    required this.entryFeePoints,
    required this.prizePoolPoints,
    required this.winnerCount,
    required this.joinConfirmRequired,
    required this.scoringTemplateId,
    required this.prizes,
  });

  Map<String, dynamic> toJson() {
    return {
      'contestName': contestName,
      'maxSpots': maxSpots,
      'entryFeePoints': entryFeePoints,
      'prizePoolPoints': prizePoolPoints,
      'winnerCount': winnerCount,
      'joinConfirmRequired': joinConfirmRequired,
      'scoringTemplateId': scoringTemplateId,
      'prizes': prizes.map((e) => e.toJson()).toList(),
    };
  }
}

class AdminManagedUser {
  final String userId;
  final String username;
  final String fullName;
  final String mobile;
  final String email;
  final String status;
  final int walletBalance;
  final int lifetimeEarnedPoints;
  final int lifetimeSpentPoints;
  final DateTime? createdAt;

  const AdminManagedUser({
    required this.userId,
    required this.username,
    required this.fullName,
    required this.mobile,
    required this.email,
    required this.status,
    required this.walletBalance,
    required this.lifetimeEarnedPoints,
    required this.lifetimeSpentPoints,
    required this.createdAt,
  });

  bool get isBlocked => status.toUpperCase() == 'BLOCKED';

  factory AdminManagedUser.fromJson(Map<String, dynamic> json) {
    final rawCreatedAt = asStringOrNull(json['createdAt']);

    return AdminManagedUser(
      userId: asString(json['userId']),
      username: asString(json['username']),
      fullName: asString(
        json['fullName'],
        fallback: asString(json['username'], fallback: 'User'),
      ),
      mobile: asString(json['mobile']),
      email: asString(json['email']),
      status: asString(json['status'], fallback: 'ACTIVE'),
      walletBalance: asInt(json['walletBalance']),
      lifetimeEarnedPoints: asInt(json['lifetimeEarnedPoints']),
      lifetimeSpentPoints: asInt(json['lifetimeSpentPoints']),
      createdAt: rawCreatedAt == null ? null : DateTime.tryParse(rawCreatedAt),
    );
  }

  AdminManagedUser copyWith({
    String? userId,
    String? username,
    String? fullName,
    String? mobile,
    String? email,
    String? status,
    int? walletBalance,
    int? lifetimeEarnedPoints,
    int? lifetimeSpentPoints,
    DateTime? createdAt,
  }) {
    return AdminManagedUser(
      userId: userId ?? this.userId,
      username: username ?? this.username,
      fullName: fullName ?? this.fullName,
      mobile: mobile ?? this.mobile,
      email: email ?? this.email,
      status: status ?? this.status,
      walletBalance: walletBalance ?? this.walletBalance,
      lifetimeEarnedPoints: lifetimeEarnedPoints ?? this.lifetimeEarnedPoints,
      lifetimeSpentPoints: lifetimeSpentPoints ?? this.lifetimeSpentPoints,
      createdAt: createdAt ?? this.createdAt,
    );
  }

  AdminManagedUser applyWalletUpdate(AdminWalletSnapshot wallet) {
    return copyWith(
      walletBalance: wallet.balancePoints,
      lifetimeEarnedPoints: wallet.lifetimeEarnedPoints,
      lifetimeSpentPoints: wallet.lifetimeSpentPoints,
    );
  }
}

class AdminWalletSnapshot {
  final String userId;
  final int balancePoints;
  final int lifetimeEarnedPoints;
  final int lifetimeSpentPoints;

  const AdminWalletSnapshot({
    required this.userId,
    required this.balancePoints,
    required this.lifetimeEarnedPoints,
    required this.lifetimeSpentPoints,
  });

  factory AdminWalletSnapshot.fromJson(Map<String, dynamic> json) {
    return AdminWalletSnapshot(
      userId: asString(json['userId']),
      balancePoints: asInt(json['balancePoints']),
      lifetimeEarnedPoints: asInt(json['lifetimeEarnedPoints']),
      lifetimeSpentPoints: asInt(json['lifetimeSpentPoints']),
    );
  }
}

class AdminWalletAdjustmentPayload {
  final int points;
  final String remarks;

  const AdminWalletAdjustmentPayload({
    required this.points,
    required this.remarks,
  });

  Map<String, dynamic> toJson() {
    return {'points': points, 'remarks': remarks};
  }
}

class AdminUserStatusActionPayload {
  final String remarks;

  const AdminUserStatusActionPayload({required this.remarks});

  Map<String, dynamic> toJson() {
    return {'remarks': remarks};
  }
}

class AdminUserActivityHistory {
  final String userId;
  final int totalPointsAdded;
  final int totalPointsDeducted;
  final List<AdminUserActivityItem> history;

  const AdminUserActivityHistory({
    required this.userId,
    required this.totalPointsAdded,
    required this.totalPointsDeducted,
    required this.history,
  });

  factory AdminUserActivityHistory.fromJson(Map<String, dynamic> json) {
    final historyRaw = json['history'] is List
        ? List<dynamic>.from(json['history'] as List)
        : const <dynamic>[];

    return AdminUserActivityHistory(
      userId: asString(json['userId']),
      totalPointsAdded: asInt(json['totalPointsAdded']),
      totalPointsDeducted: asInt(json['totalPointsDeducted']),
      history: historyRaw
          .map(
            (item) => AdminUserActivityItem.fromJson(
              Map<String, dynamic>.from(item as Map),
            ),
          )
          .toList(),
    );
  }
}

class AdminUserActivityItem {
  final String id;
  final String type;
  final int? points;
  final String remarks;
  final String? adminId;
  final String adminUsername;
  final DateTime? createdAt;

  const AdminUserActivityItem({
    required this.id,
    required this.type,
    required this.points,
    required this.remarks,
    required this.adminId,
    required this.adminUsername,
    required this.createdAt,
  });

  bool get isPointsAdded => type == 'POINTS_ADDED';

  bool get isPointsDeducted => type == 'POINTS_DEDUCTED';

  factory AdminUserActivityItem.fromJson(Map<String, dynamic> json) {
    final rawPoints = json['points'];

    return AdminUserActivityItem(
      id: asString(json['activityId'] ?? json['id']),
      type: asString(json['activityType']),
      points: rawPoints == null ? null : asInt(rawPoints),
      remarks: asString(json['remarks']),
      adminId: asStringOrNull(json['adminId']),
      adminUsername: asString(json['adminUsername'], fallback: 'Admin'),
      createdAt: _parseAdminDate(json['createdAt']),
    );
  }
}

DateTime? _parseAdminDate(dynamic value) {
  final raw = asStringOrNull(value);
  if (raw == null) return null;
  return DateTime.tryParse(raw)?.toLocal();
}

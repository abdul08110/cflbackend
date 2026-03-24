import '../../../../core/network/api_helpers.dart';

class Room {
  final String id;
  final String? contestId;
  final String? fixtureId;
  final String name;
  final String code;
  final String sport;
  final int memberCount;
  final int maxMembers;
  final String createdBy;
  final String createdByUsername;
  final String status;
  final String contestStatus;
  final String fixtureStatus;
  final bool isActive;
  final bool isPrivate;
  final String myRole;
  final bool isMember;
  final bool isInvited;
  final int contestCount;
  final int joiningPoints;
  final int prizePoolPoints;
  final int winnerPayoutPoints;
  final String fixtureTitle;
  final DateTime? fixtureStartTime;
  final DateTime? fixtureDeadlineTime;
  final bool teamCreated;
  final bool canCreateTeam;
  final bool canInvite;
  final bool canViewParticipantTeams;
  final bool canEdit;
  final bool canDelete;

  const Room({
    required this.id,
    this.contestId,
    this.fixtureId,
    required this.name,
    required this.code,
    required this.sport,
    required this.memberCount,
    required this.maxMembers,
    required this.createdBy,
    required this.createdByUsername,
    required this.status,
    required this.contestStatus,
    required this.fixtureStatus,
    required this.isActive,
    required this.isPrivate,
    required this.myRole,
    required this.isMember,
    required this.isInvited,
    required this.contestCount,
    required this.joiningPoints,
    required this.prizePoolPoints,
    required this.winnerPayoutPoints,
    required this.fixtureTitle,
    required this.fixtureStartTime,
    required this.fixtureDeadlineTime,
    required this.teamCreated,
    required this.canCreateTeam,
    required this.canInvite,
    required this.canViewParticipantTeams,
    required this.canEdit,
    required this.canDelete,
  });

  factory Room.fromJson(Map<String, dynamic> json) {
    final data = _unwrapRoomMap(json);

    final status = asString(
      data['status'] ?? data['roomStatus'],
      fallback: 'ACTIVE',
    );
    final activeField = data['isActive'] ?? data['active'] ?? data['is_active'];

    final bool active =
        (activeField == null || asBool(activeField)) &&
        status != 'CLOSED' &&
        status != 'COMPLETED' &&
        status != 'INACTIVE';

    return Room(
      id: asString(data['communityId'] ?? data['id'] ?? data['roomId']),
      contestId: asStringOrNull(data['contestId']),
      fixtureId: asStringOrNull(data['fixtureId']),
      name: asString(
        data['communityName'] ??
            data['name'] ??
            data['roomName'] ??
            data['title'],
        fallback: 'Community',
      ),
      code: asString(
        data['communityCode'] ??
            data['code'] ??
            data['roomCode'] ??
            data['inviteCode'],
      ),
      sport: asString(
        data['sport'] ?? data['sportName'] ?? data['sportId'],
        fallback: 'CRICKET',
      ),
      memberCount: asInt(
        data['joinedMembers'] ??
            data['memberCount'] ??
            data['membersCount'] ??
            data['currentMembers'] ??
            data['members_count'],
      ),
      maxMembers: asInt(
        data['maxSpots'] ??
            data['maxMembers'] ??
            data['totalSpots'] ??
            data['capacity'],
        fallback: 20,
      ),
      createdBy: asString(
        data['createdBy'] ??
            data['createdByUserId'] ??
            data['ownerId'] ??
            data['creatorId'] ??
            data['userId'] ??
            (data['creator'] is Map ? data['creator']['id'] : null),
      ),
      createdByUsername: asString(
        data['createdByUsername'] ?? data['ownerUsername'] ?? data['creatorName'],
      ),
      status: status,
      contestStatus: asString(
        data['contestStatus'] ?? data['contest_state'],
        fallback: 'NO_CONTESTS',
      ),
      fixtureStatus: asString(data['fixtureStatus'], fallback: 'UPCOMING'),
      isActive: active,
      isPrivate: asBool(data['isPrivate'], fallback: true),
      myRole: asString(data['myRole']),
      isMember: asBool(data['isMember']),
      isInvited: asBool(data['isInvited']),
      contestCount: asInt(data['contestCount']),
      joiningPoints: asInt(data['joiningPoints'] ?? data['entryFeePoints']),
      prizePoolPoints: asInt(data['prizePoolPoints'] ?? data['prizePool']),
      winnerPayoutPoints: asInt(
        data['winnerPayoutPoints'] ?? data['firstPrizePoints'],
      ),
      fixtureTitle: asString(data['fixtureTitle']),
      fixtureStartTime: _parseDate(data['fixtureStartTime']),
      fixtureDeadlineTime: _parseDate(data['fixtureDeadlineTime']),
      teamCreated: asBool(data['teamCreated']),
      canCreateTeam: asBool(data['canCreateTeam']),
      canInvite: asBool(data['canInvite']),
      canViewParticipantTeams: asBool(data['canViewParticipantTeams']),
      canEdit: asBool(data['canEdit']),
      canDelete: asBool(data['canDelete']),
    );
  }
}

class RoomDetail {
  final Room community;
  final List<RoomMember> members;
  final List<RoomContest> contests;

  const RoomDetail({
    required this.community,
    required this.members,
    required this.contests,
  });

  factory RoomDetail.fromJson(Map<String, dynamic> json) {
    final data = unwrapMap(json);
    final communityMap = data['community'] is Map
        ? Map<String, dynamic>.from(data['community'] as Map)
        : data;

    final membersRaw = data['members'] is List
        ? List<dynamic>.from(data['members'] as List)
        : const <dynamic>[];
    final contestsRaw = data['contests'] is List
        ? List<dynamic>.from(data['contests'] as List)
        : const <dynamic>[];

    return RoomDetail(
      community: Room.fromJson(communityMap),
      members: membersRaw
          .map((e) => RoomMember.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList(),
      contests: contestsRaw
          .map(
            (e) => RoomContest.fromJson(
              Map<String, dynamic>.from(e as Map),
            ),
          )
          .toList(),
    );
  }
}

class RoomContest {
  final String id;
  final String communityId;
  final String fixtureId;
  final String contestName;
  final String contestStatus;
  final String fixtureTitle;
  final String fixtureLeague;
  final int entryFeePoints;
  final int prizePoolPoints;
  final int firstPrizePoints;
  final int maxSpots;
  final int spotsFilled;
  final int spotsLeft;
  final int myEntriesCount;
  final bool joinedByMe;
  final bool canJoin;
  final bool canInvite;
  final String createdByUsername;
  final DateTime? fixtureStartTime;
  final DateTime? fixtureDeadlineTime;
  final List<RoomContestParticipant> participants;

  const RoomContest({
    required this.id,
    required this.communityId,
    required this.fixtureId,
    required this.contestName,
    required this.contestStatus,
    required this.fixtureTitle,
    required this.fixtureLeague,
    required this.entryFeePoints,
    required this.prizePoolPoints,
    required this.firstPrizePoints,
    required this.maxSpots,
    required this.spotsFilled,
    required this.spotsLeft,
    required this.myEntriesCount,
    required this.joinedByMe,
    required this.canJoin,
    required this.canInvite,
    required this.createdByUsername,
    required this.fixtureStartTime,
    required this.fixtureDeadlineTime,
    required this.participants,
  });

  factory RoomContest.fromJson(Map<String, dynamic> json) {
    final participantsRaw = json['participants'] is List
        ? List<dynamic>.from(json['participants'] as List)
        : const <dynamic>[];

    return RoomContest(
      id: asString(json['contestId'] ?? json['id']),
      communityId: asString(json['communityId']),
      fixtureId: asString(json['fixtureId']),
      contestName: asString(
        json['contestName'] ?? json['name'],
        fallback: 'Community Contest',
      ),
      contestStatus: asString(json['contestStatus'], fallback: 'OPEN'),
      fixtureTitle: asString(json['fixtureTitle']),
      fixtureLeague: asString(json['fixtureLeague']),
      entryFeePoints: asInt(json['entryFeePoints']),
      prizePoolPoints: asInt(json['prizePoolPoints']),
      firstPrizePoints: asInt(json['firstPrizePoints']),
      maxSpots: asInt(json['maxSpots']),
      spotsFilled: asInt(json['spotsFilled']),
      spotsLeft: asInt(json['spotsLeft']),
      myEntriesCount: asInt(json['myEntriesCount']),
      joinedByMe: asBool(json['joinedByMe']),
      canJoin: asBool(json['canJoin']),
      canInvite: asBool(json['canInvite']),
      createdByUsername: asString(
        json['createdByUsername'],
        fallback: 'Community member',
      ),
      fixtureStartTime: _parseDate(json['fixtureStartTime']),
      fixtureDeadlineTime: _parseDate(json['fixtureDeadlineTime']),
      participants: participantsRaw
          .map(
            (e) => RoomContestParticipant.fromJson(
              Map<String, dynamic>.from(e as Map),
            ),
          )
          .toList(),
    );
  }
}

class RoomContestParticipant {
  final String teamName;
  final String shortName;
  final bool isHome;

  const RoomContestParticipant({
    required this.teamName,
    required this.shortName,
    required this.isHome,
  });

  factory RoomContestParticipant.fromJson(Map<String, dynamic> json) {
    return RoomContestParticipant(
      teamName: asString(json['teamName'], fallback: 'Team'),
      shortName: asString(json['shortName']),
      isHome: asBool(json['isHome']),
    );
  }
}

class RoomMember {
  final String id;
  final String username;
  final String fullName;
  final String mobile;
  final String role;
  final String status;
  final bool teamCreated;
  final DateTime? joinedAt;

  const RoomMember({
    required this.id,
    required this.username,
    required this.fullName,
    required this.mobile,
    required this.role,
    required this.status,
    required this.teamCreated,
    required this.joinedAt,
  });

  factory RoomMember.fromJson(Map<String, dynamic> json) {
    final user = json['user'] is Map
        ? Map<String, dynamic>.from(json['user'] as Map)
        : json;

    return RoomMember(
      id: asString(
        user['id'] ??
            user['userId'] ??
            user['memberId'] ??
            json['id'] ??
            json['userId'],
      ),
      username: asString(user['username'] ?? user['userName'] ?? 'User'),
      fullName: asString(
        user['fullName'] ??
            user['name'] ??
            user['displayName'] ??
            user['username'],
        fallback: 'Player',
      ),
      mobile: asString(user['mobile']),
      role: asString(json['role'], fallback: 'MEMBER'),
      status: asString(json['status'], fallback: 'JOINED'),
      teamCreated: asBool(json['teamCreated']),
      joinedAt: _parseDate(json['joinedAt']),
    );
  }
}

class RoomEntry {
  final String id;
  final String contestId;
  final String? teamId;
  final String teamName;
  final int entryFeePoints;
  final double fantasyPoints;
  final int rank;
  final int prizePointsAwarded;
  final String status;
  final DateTime? joinedAt;

  const RoomEntry({
    required this.id,
    required this.contestId,
    required this.teamId,
    required this.teamName,
    required this.entryFeePoints,
    required this.fantasyPoints,
    required this.rank,
    required this.prizePointsAwarded,
    required this.status,
    required this.joinedAt,
  });

  factory RoomEntry.fromJson(Map<String, dynamic> json) {
    return RoomEntry(
      id: asString(json['entryId'] ?? json['id']),
      contestId: asString(json['contestId']),
      teamId: asStringOrNull(json['teamId']),
      teamName: asString(json['teamName']),
      entryFeePoints: asInt(json['entryFeePoints']),
      fantasyPoints: asDouble(json['fantasyPoints'] ?? json['points']),
      rank: asInt(json['rankNo']),
      prizePointsAwarded: asInt(json['prizePointsAwarded']),
      status: asString(json['status'], fallback: 'JOINED'),
      joinedAt: _parseDate(json['joinedAt']),
    );
  }
}

class RoomLeaderboardEntry {
  final int position;
  final String entryId;
  final String userId;
  final String username;
  final String? teamId;
  final String teamName;
  final double fantasyPoints;
  final int prizePointsAwarded;
  final String status;

  const RoomLeaderboardEntry({
    required this.position,
    required this.entryId,
    required this.userId,
    required this.username,
    required this.teamId,
    required this.teamName,
    required this.fantasyPoints,
    required this.prizePointsAwarded,
    required this.status,
  });

  factory RoomLeaderboardEntry.fromJson(Map<String, dynamic> json) {
    return RoomLeaderboardEntry(
      position: asInt(json['position'] ?? json['rankNo']),
      entryId: asString(json['entryId']),
      userId: asString(json['userId']),
      username: asString(json['username'], fallback: 'User'),
      teamId: asStringOrNull(json['teamId']),
      teamName: asString(json['teamName'], fallback: 'No team selected'),
      fantasyPoints: asDouble(json['fantasyPoints'] ?? json['points']),
      prizePointsAwarded: asInt(json['prizePointsAwarded']),
      status: asString(json['status'], fallback: 'JOINED'),
    );
  }
}

class RoomInvitation {
  final String id;
  final String communityId;
  final String roomName;
  final String invitedBy;
  final int joiningPoints;
  final int joinedMembers;
  final int maxSpots;
  final String? inviteMessage;
  final String status;

  const RoomInvitation({
    required this.id,
    required this.communityId,
    required this.roomName,
    required this.invitedBy,
    required this.joiningPoints,
    required this.joinedMembers,
    required this.maxSpots,
    required this.inviteMessage,
    required this.status,
  });

  factory RoomInvitation.fromJson(Map<String, dynamic> json) {
    final communityId = asString(json['communityId'] ?? json['roomId']);
    final invitedByValue = asString(
      json['invitedBy'] ??
          json['senderUsername'] ??
          json['fromUser'] ??
          json['invitedByUserId'],
    );

    return RoomInvitation(
      id: asString(json['id'] ?? json['invitationId']),
      communityId: communityId,
      roomName: asString(
        json['communityName'] ?? json['roomName'] ?? json['room'],
        fallback: communityId.isEmpty
            ? 'Community invite'
            : 'Community #$communityId',
      ),
      invitedBy: invitedByValue.isEmpty ? 'Private invite' : invitedByValue,
      joiningPoints: asInt(json['joiningPoints']),
      joinedMembers: asInt(json['joinedMembers']),
      maxSpots: asInt(json['maxSpots'], fallback: 0),
      inviteMessage: asStringOrNull(json['inviteMessage']),
      status: asString(json['status'], fallback: 'PENDING'),
    );
  }
}

class CommunityTeamView {
  final String id;
  final String name;
  final List<CommunityTeamPlayer> players;

  const CommunityTeamView({
    required this.id,
    required this.name,
    required this.players,
  });

  factory CommunityTeamView.fromJson(Map<String, dynamic> json) {
    final playersRaw = json['players'] is List
        ? List<dynamic>.from(json['players'] as List)
        : const <dynamic>[];

    return CommunityTeamView(
      id: asString(json['teamId'] ?? json['id']),
      name: asString(json['teamName'] ?? json['name'], fallback: 'Team'),
      players: playersRaw
          .map(
            (e) => CommunityTeamPlayer.fromJson(
              Map<String, dynamic>.from(e as Map),
            ),
          )
          .toList(),
    );
  }
}

class CommunityTeamPlayer {
  final String playerName;
  final String roleCode;
  final String teamSide;
  final bool isCaptain;
  final bool isViceCaptain;

  const CommunityTeamPlayer({
    required this.playerName,
    required this.roleCode,
    required this.teamSide,
    required this.isCaptain,
    required this.isViceCaptain,
  });

  factory CommunityTeamPlayer.fromJson(Map<String, dynamic> json) {
    return CommunityTeamPlayer(
      playerName: asString(json['playerName'], fallback: 'Player'),
      roleCode: asString(json['roleCode']),
      teamSide: asString(json['teamSide']),
      isCaptain: asBool(json['isCaptain']),
      isViceCaptain: asBool(json['isViceCaptain']),
    );
  }
}

Map<String, dynamic> _unwrapRoomMap(Map<String, dynamic> json) {
  final data = unwrapMap(json);

  if (data['community'] is Map) {
    return Map<String, dynamic>.from(data['community'] as Map);
  }

  if (data['room'] is Map) {
    return Map<String, dynamic>.from(data['room'] as Map);
  }

  return data;
}

DateTime? _parseDate(dynamic value) {
  final raw = asStringOrNull(value);
  if (raw == null) return null;
  return DateTime.tryParse(raw)?.toLocal();
}

import '../../../../core/network/api_helpers.dart';

class FantasyTeam {
  final String id;
  final String name;
  final String captainPlayerId;
  final String viceCaptainPlayerId;
  final bool isLocked;
  final bool canDelete;
  final List<String> playerIds;
  final List<String> substitutePlayerIds;
  final List<FantasyTeamPlayer> players;
  final List<FantasyTeamPlayer> substitutes;

  const FantasyTeam({
    required this.id,
    required this.name,
    required this.captainPlayerId,
    required this.viceCaptainPlayerId,
    required this.isLocked,
    required this.canDelete,
    required this.playerIds,
    required this.substitutePlayerIds,
    required this.players,
    required this.substitutes,
  });

  factory FantasyTeam.fromJson(Map<String, dynamic> json) {
    final rawPlayers = json['players'] is List
        ? List<dynamic>.from(json['players'] as List)
        : (json['playerIds'] is List
              ? List<dynamic>.from(json['playerIds'] as List)
              : <dynamic>[]);
    final rawSubstitutes = json['substitutes'] is List
        ? List<dynamic>.from(json['substitutes'] as List)
        : (json['substitutePlayerIds'] is List
              ? List<dynamic>.from(json['substitutePlayerIds'] as List)
              : <dynamic>[]);

    List<String> parsePoolIds(List<dynamic> items) {
      return items
          .map((e) {
            if (e is Map) {
              final map = Map<String, dynamic>.from(e);
              return asString(
                map['fixturePlayerPoolId'] ?? map['id'] ?? map['playerId'],
              );
            }
            return asString(e);
          })
          .where((e) => e.isNotEmpty)
          .toList();
    }

    final ids = parsePoolIds(rawPlayers);
    final substituteIds = parsePoolIds(rawSubstitutes);
    final players = rawPlayers
        .whereType<Map>()
        .map((e) => FantasyTeamPlayer.fromJson(Map<String, dynamic>.from(e)))
        .toList();
    final substitutes = rawSubstitutes
        .whereType<Map>()
        .map((e) => FantasyTeamPlayer.fromJson(Map<String, dynamic>.from(e)))
        .toList();

    return FantasyTeam(
      id: asString(json['id'] ?? json['teamId']),
      name: asString(json['name'] ?? json['teamName'], fallback: 'My Team'),
      captainPlayerId: asString(json['captainPlayerId'] ?? json['captainId']),
      viceCaptainPlayerId: asString(
        json['viceCaptainPlayerId'] ?? json['viceCaptainId'],
      ),
      isLocked: asBool(json['isLocked']),
      canDelete: asBool(json['canDelete'], fallback: !asBool(json['isLocked'])),
      playerIds: ids,
      substitutePlayerIds: substituteIds,
      players: players,
      substitutes: substitutes,
    );
  }
}

class FantasyTeamPlayer {
  final String fixturePlayerPoolId;
  final String playerId;
  final String playerName;
  final String roleCode;
  final String teamSide;
  final String teamName;
  final double creditValue;
  final String? imageUrl;
  final double? fantasyPoints;
  final bool isCaptain;
  final bool isViceCaptain;
  final bool isSubstitute;
  final int substitutePriority;

  const FantasyTeamPlayer({
    required this.fixturePlayerPoolId,
    required this.playerId,
    required this.playerName,
    required this.roleCode,
    required this.teamSide,
    required this.teamName,
    required this.creditValue,
    required this.imageUrl,
    required this.fantasyPoints,
    required this.isCaptain,
    required this.isViceCaptain,
    required this.isSubstitute,
    required this.substitutePriority,
  });

  factory FantasyTeamPlayer.fromJson(Map<String, dynamic> json) {
    return FantasyTeamPlayer(
      fixturePlayerPoolId: asString(json['fixturePlayerPoolId']),
      playerId: asString(json['playerId']),
      playerName: asString(json['playerName'], fallback: 'Player'),
      roleCode: asString(json['roleCode']),
      teamSide: asString(json['teamSide']),
      teamName: asString(json['teamName'], fallback: 'Team'),
      creditValue: asDouble(json['creditValue'] ?? json['credit']),
      imageUrl: asStringOrNull(json['imageUrl']),
      fantasyPoints:
          json.containsKey('fantasyPoints') || json.containsKey('points')
          ? asDouble(json['fantasyPoints'] ?? json['points'])
          : null,
      isCaptain: asBool(json['isCaptain']),
      isViceCaptain: asBool(json['isViceCaptain']),
      isSubstitute: asBool(json['isSubstitute']),
      substitutePriority: asInt(json['substitutePriority']),
    );
  }
}

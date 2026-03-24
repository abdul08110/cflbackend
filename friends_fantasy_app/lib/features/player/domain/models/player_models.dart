class PlayerPoolItem {
  final String id; // fixturePlayerPoolId
  final String playerId; // actual playerId
  final String name;
  final String role;
  final String teamName;
  final double credit;
  final double selectionPercent;
  final bool isAnnounced;
  final bool isPlaying;
  final String? imageUrl;

  const PlayerPoolItem({
    required this.id,
    required this.playerId,
    required this.name,
    required this.role,
    required this.teamName,
    required this.credit,
    required this.selectionPercent,
    required this.isAnnounced,
    required this.isPlaying,
    this.imageUrl,
  });

  factory PlayerPoolItem.fromJson(Map<String, dynamic> json) {
    final fixturePlayerPoolId = json['fixturePlayerPoolId'];
    final playerId = json['playerId'];
    final playerName = json['playerName'] ?? json['name'] ?? '';
    final roleCode = json['roleCode'] ?? json['role'] ?? '';
    final creditValue = json['creditValue'] ?? json['credit'] ?? 0;
    final selectionPercentValue = json['selectionPercent'] ?? 0;
    final externalTeamId = json['externalTeamId'];
    final teamName =
        json['teamName'] ?? (externalTeamId != null ? 'Team $externalTeamId' : 'Team');

    return PlayerPoolItem(
      id: '${fixturePlayerPoolId ?? ''}',
      playerId: '${playerId ?? ''}',
      name: '$playerName',
      role: '$roleCode',
      teamName: '$teamName',
      credit: (creditValue is num)
          ? creditValue.toDouble()
          : double.tryParse('$creditValue') ?? 0,
      selectionPercent: (selectionPercentValue is num)
          ? selectionPercentValue.toDouble()
          : double.tryParse('$selectionPercentValue') ?? 0,
      isAnnounced: json['isAnnounced'] == true,
      isPlaying: json['isPlaying'] == true,
      imageUrl: json['imageUrl'] as String?,
    );
  }
}

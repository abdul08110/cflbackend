import '../../../../core/network/api_helpers.dart';

class MyStats {
  final int contestsPlayed;
  final int contestsWon;
  final double totalWinnings;
  final double totalSpent;

  const MyStats({required this.contestsPlayed, required this.contestsWon, required this.totalWinnings, required this.totalSpent});

  factory MyStats.fromJson(Map<String, dynamic> json) => MyStats(
        contestsPlayed: asInt(json['contestsPlayed']),
        contestsWon: asInt(json['contestsWon']),
        totalWinnings: asDouble(json['totalWinnings']),
        totalSpent: asDouble(json['totalSpent']),
      );
}

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_client.dart';
import '../../../core/network/api_endpoints.dart';
import '../../../core/network/api_helpers.dart';
import '../../player/domain/models/player_models.dart';
import '../domain/models/team_models.dart';

final teamRepositoryProvider = Provider<TeamRepository>(
  (ref) => TeamRepository(ref.watch(dioProvider)),
);

final playerPoolProvider = FutureProvider.autoDispose
    .family<List<PlayerPoolItem>, String>(
      (ref, fixtureId) async =>
          ref.watch(teamRepositoryProvider).getPlayerPool(fixtureId),
    );

final myTeamsProvider = FutureProvider.autoDispose
    .family<List<FantasyTeam>, String>(
      (ref, fixtureId) async =>
          ref.watch(teamRepositoryProvider).getMyTeams(fixtureId),
    );

class TeamRepository {
  TeamRepository(this._dio);
  final Dio _dio;

  Future<List<PlayerPoolItem>> getPlayerPool(
    String fixtureId, {
    bool forceSync = false,
  }) async {
    try {
      final response = await _dio.get(
        ApiEndpoints.fixturePlayerPool(fixtureId),
        queryParameters: forceSync ? {'forceSync': true} : null,
      );
      return unwrapList(response.data)
          .map(
            (e) => PlayerPoolItem.fromJson(Map<String, dynamic>.from(e as Map)),
          )
          .toList();
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<List<FantasyTeam>> getMyTeams(String fixtureId) async {
    try {
      final response = await _dio.get(ApiEndpoints.myTeamsByFixture(fixtureId));
      return unwrapList(response.data)
          .map((e) => FantasyTeam.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList();
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<void> createTeam({
    required String fixtureId,
    required String teamName,
    required List<String> fixturePlayerPoolIds,
    List<String> substituteFixturePlayerPoolIds = const [],
    required String captainPlayerId,
    required String viceCaptainPlayerId,
  }) async {
    try {
      await _dio.post(
        ApiEndpoints.createTeam(fixtureId),
        data: {
          'teamName': teamName,
          'players': fixturePlayerPoolIds
              .map((id) => {'fixturePlayerPoolId': int.parse(id)})
              .toList(),
          'substitutes': substituteFixturePlayerPoolIds
              .map((id) => {'fixturePlayerPoolId': int.parse(id)})
              .toList(),
          'captainPlayerId': int.parse(captainPlayerId),
          'viceCaptainPlayerId': int.parse(viceCaptainPlayerId),
        },
      );
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<void> updateTeam({
    required String teamId,
    required String teamName,
    required List<String> fixturePlayerPoolIds,
    List<String> substituteFixturePlayerPoolIds = const [],
    required String captainPlayerId,
    required String viceCaptainPlayerId,
  }) async {
    try {
      await _dio.put(
        ApiEndpoints.updateTeam(teamId),
        data: {
          'teamName': teamName,
          'players': fixturePlayerPoolIds
              .map((id) => {'fixturePlayerPoolId': int.parse(id)})
              .toList(),
          'substitutes': substituteFixturePlayerPoolIds
              .map((id) => {'fixturePlayerPoolId': int.parse(id)})
              .toList(),
          'captainPlayerId': int.parse(captainPlayerId),
          'viceCaptainPlayerId': int.parse(viceCaptainPlayerId),
        },
      );
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<void> deleteTeam(String teamId) async {
    try {
      await _dio.delete(ApiEndpoints.deleteTeam(teamId));
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }
}

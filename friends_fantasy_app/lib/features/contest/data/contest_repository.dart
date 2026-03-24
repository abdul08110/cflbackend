import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_client.dart';
import '../../../core/network/api_endpoints.dart';
import '../../../core/network/api_helpers.dart';
import '../../auth/presentation/providers/auth_controller.dart';
import '../../team/domain/models/team_models.dart';
import '../domain/models/contest_models.dart';

final contestRepositoryProvider = Provider<ContestRepository>((ref) => ContestRepository(ref.watch(dioProvider)));
final contestsByFixtureProvider = FutureProvider.autoDispose.family<List<ContestSummary>, String>((ref, fixtureId) async => ref.watch(contestRepositoryProvider).getContestsByFixture(fixtureId));
final contestDetailProvider = FutureProvider.autoDispose.family<ContestDetail, String>((ref, contestId) async => ref.watch(contestRepositoryProvider).getContestDetail(contestId));
final leaderboardProvider = FutureProvider.autoDispose.family<List<LeaderboardEntry>, String>((ref, contestId) async => ref.watch(contestRepositoryProvider).getLeaderboard(contestId));
final myContestEntriesProvider = FutureProvider.autoDispose.family<List<ContestEntrySummary>, String>((ref, contestId) async {
  final currentUserId = ref.watch(
    authControllerProvider.select((state) => state.user?.id),
  );
  if (currentUserId == null) {
    return const <ContestEntrySummary>[];
  }

  return ref.watch(contestRepositoryProvider).getMyEntries(contestId);
});
final myContestHistoryProvider = FutureProvider.autoDispose<List<MyContestEntry>>((ref) async => ref.watch(contestRepositoryProvider).getMyHistory());

class ContestRepository {
  ContestRepository(this._dio);
  final Dio _dio;

  Future<List<ContestSummary>> getContestsByFixture(String fixtureId) async {
    try {
      final response = await _dio.get(ApiEndpoints.fixtureContests(fixtureId));
      return unwrapList(response.data).map((e) => ContestSummary.fromJson(Map<String, dynamic>.from(e as Map))).toList();
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<ContestDetail> getContestDetail(String contestId) async {
    try {
      final response = await _dio.get(ApiEndpoints.contestDetail(contestId));
      return ContestDetail.fromJson(unwrapMap(response.data));
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<List<LeaderboardEntry>> getLeaderboard(String contestId) async {
    try {
      final response = await _dio.get(ApiEndpoints.contestLeaderboard(contestId));
      return unwrapList(response.data).map((e) => LeaderboardEntry.fromJson(Map<String, dynamic>.from(e as Map))).toList();
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<List<ContestEntrySummary>> getMyEntries(String contestId) async {
    try {
      final response = await _dio.get(ApiEndpoints.myContestEntries(contestId));
      return unwrapList(response.data)
          .map(
            (e) => ContestEntrySummary.fromJson(
              Map<String, dynamic>.from(e as Map),
            ),
          )
          .toList();
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<FantasyTeam> getContestTeamView({
    required String contestId,
    required String teamId,
  }) async {
    try {
      final response = await _dio.get(
        ApiEndpoints.contestTeamView(contestId, teamId),
      );
      return FantasyTeam.fromJson(unwrapMap(response.data));
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<void> joinContest({required String contestId, required String teamId}) async {
    try {
      await _dio.post(ApiEndpoints.joinContest(contestId), data: {'teamId': teamId});
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<List<MyContestEntry>> getMyHistory() async {
    try {
      final response = await _dio.get(ApiEndpoints.myContestHistory);
      return unwrapList(response.data).map((e) => MyContestEntry.fromJson(Map<String, dynamic>.from(e as Map))).toList();
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }
}

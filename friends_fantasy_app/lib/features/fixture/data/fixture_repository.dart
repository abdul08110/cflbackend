import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_client.dart';
import '../../../core/network/api_endpoints.dart';
import '../../../core/network/api_helpers.dart';
import '../domain/models/fixture_models.dart';

final fixtureRepositoryProvider = Provider<FixtureRepository>(
  (ref) => FixtureRepository(ref.watch(dioProvider)),
);

final homeUpcomingFixturesProvider =
    FutureProvider.autoDispose<List<FixtureSummary>>((ref) async {
      final link = ref.keepAlive();
      final timer = Timer(const Duration(minutes: 2), link.close);
      ref.onDispose(timer.cancel);

      return ref
          .watch(fixtureRepositoryProvider)
          .getUpcomingFixtures(limit: 12);
    });

final upcomingFixturesProvider =
    FutureProvider.autoDispose<List<FixtureSummary>>(
      (ref) async => ref.watch(fixtureRepositoryProvider).getUpcomingFixtures(),
    );
final fixtureDetailProvider = FutureProvider.autoDispose
    .family<FixtureDetail, String>(
      (ref, fixtureId) async =>
          ref.watch(fixtureRepositoryProvider).getFixtureDetail(fixtureId),
    );

class FixtureRepository {
  FixtureRepository(this._dio);
  final Dio _dio;

  Future<List<FixtureSummary>> getUpcomingFixtures({int? limit}) async {
    try {
      final response = await _dio.get(
        ApiEndpoints.upcomingFixtures,
        queryParameters: {
          if (limit != null && limit > 0) 'limit': limit,
        },
      );
      return unwrapList(response.data)
          .map(
            (e) => FixtureSummary.fromJson(Map<String, dynamic>.from(e as Map)),
          )
          .toList();
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<void> syncUpcomingFixtures() async {
    try {
      await _dio.post(ApiEndpoints.adminSyncUpcomingFixtures);
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<FixtureDetail> getFixtureDetail(String fixtureId) async {
    try {
      final response = await _dio.get(ApiEndpoints.fixtureDetail(fixtureId));
      return FixtureDetail.fromJson(unwrapMap(response.data));
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }
}

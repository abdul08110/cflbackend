import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_client.dart';
import '../../../core/network/api_endpoints.dart';
import '../../../core/network/api_helpers.dart';
import '../domain/models/stats_models.dart';

final statsRepositoryProvider = Provider<StatsRepository>((ref) => StatsRepository(ref.watch(dioProvider)));
final myStatsProvider = FutureProvider.autoDispose<MyStats>((ref) async => ref.watch(statsRepositoryProvider).getMyStats());

class StatsRepository {
  StatsRepository(this._dio);
  final Dio _dio;

  Future<MyStats> getMyStats() async {
    try {
      final response = await _dio.get(ApiEndpoints.myStats);
      return MyStats.fromJson(unwrapMap(response.data));
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }
}

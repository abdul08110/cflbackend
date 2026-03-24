import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_client.dart';
import '../../../core/network/api_endpoints.dart';
import '../../../core/network/api_helpers.dart';
import '../domain/models/admin_models.dart';

final adminRepositoryProvider = Provider<AdminRepository>(
  (ref) => AdminRepository(ref.watch(dioProvider)),
);

final adminUpcomingFixturesProvider =
    FutureProvider.autoDispose<List<AdminFixture>>(
      (ref) async => ref.watch(adminRepositoryProvider).getUpcomingFixtures(),
    );

class AdminRepository {
  AdminRepository(this._dio);

  final Dio _dio;

  Future<List<AdminFixture>> getUpcomingFixtures() async {
    try {
      final response = await _dio.get(ApiEndpoints.adminUpcomingFixtures);

      return unwrapList(response.data)
          .map(
            (e) => AdminFixture.fromJson(Map<String, dynamic>.from(e as Map)),
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

  Future<void> createContest({
    required String fixtureId,
    required CreateContestPayload payload,
  }) async {
    try {
      await _dio.post(
        ApiEndpoints.adminCreateContest(fixtureId),
        data: payload.toJson(),
      );
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<List<AdminManagedUser>> getUsers({String query = ''}) async {
    try {
      final response = await _dio.get(
        ApiEndpoints.adminUsers,
        queryParameters: {'query': query.trim()},
      );

      return unwrapList(response.data)
          .map(
            (e) =>
                AdminManagedUser.fromJson(Map<String, dynamic>.from(e as Map)),
          )
          .toList();
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<AdminWalletSnapshot> creditWallet({
    required String userId,
    required AdminWalletAdjustmentPayload payload,
  }) async {
    try {
      final response = await _dio.post(
        ApiEndpoints.adminWalletCredit(userId),
        data: payload.toJson(),
      );

      return AdminWalletSnapshot.fromJson(unwrapMap(response.data));
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<AdminWalletSnapshot> debitWallet({
    required String userId,
    required AdminWalletAdjustmentPayload payload,
  }) async {
    try {
      final response = await _dio.post(
        ApiEndpoints.adminWalletDebit(userId),
        data: payload.toJson(),
      );

      return AdminWalletSnapshot.fromJson(unwrapMap(response.data));
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<AdminUserActivityHistory> getUserActivityHistory(String userId) async {
    try {
      final response = await _dio.get(
        ApiEndpoints.adminUserActivityHistory(userId),
      );
      return AdminUserActivityHistory.fromJson(unwrapMap(response.data));
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<AdminManagedUser> blockUser({
    required String userId,
    required AdminUserStatusActionPayload payload,
  }) async {
    try {
      final response = await _dio.patch(
        ApiEndpoints.adminBlockUser(userId),
        data: payload.toJson(),
      );
      return AdminManagedUser.fromJson(unwrapMap(response.data));
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<AdminManagedUser> unblockUser({
    required String userId,
    required AdminUserStatusActionPayload payload,
  }) async {
    try {
      final response = await _dio.patch(
        ApiEndpoints.adminUnblockUser(userId),
        data: payload.toJson(),
      );
      return AdminManagedUser.fromJson(unwrapMap(response.data));
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }
}

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_client.dart';
import '../../../core/network/api_endpoints.dart';
import '../../../core/network/api_helpers.dart';
import '../domain/models/friend_models.dart';

final friendRepositoryProvider =
Provider<FriendRepository>((ref) => FriendRepository(ref.watch(dioProvider)));

final friendsListProvider = FutureProvider.autoDispose<List<FriendUser>>(
      (ref) async => ref.watch(friendRepositoryProvider).getFriends(),
);

final incomingFriendRequestsProvider =
FutureProvider.autoDispose<List<FriendRequestItem>>(
      (ref) async => ref.watch(friendRepositoryProvider).getIncomingRequests(),
);

final outgoingFriendRequestsProvider =
FutureProvider.autoDispose<List<FriendRequestItem>>(
      (ref) async => ref.watch(friendRepositoryProvider).getOutgoingRequests(),
);

class FriendRepository {
  FriendRepository(this._dio);
  final Dio _dio;

  Future<List<FriendUser>> getFriends() async {
    try {
      final response = await _dio.get(ApiEndpoints.friends);
      return unwrapList(response.data)
          .map((e) => FriendUser.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList();
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<List<FriendUser>> search(String query) async {
    try {
      final response = await _dio.get(ApiEndpoints.userSearch(query));
      return unwrapList(response.data)
          .map((e) => FriendUser.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList();
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<void> sendRequest({String? username, String? userId}) async {
    try {
      await _dio.post(
        ApiEndpoints.sendFriendRequest,
        data: {
          if (username != null && username.isNotEmpty) 'username': username,
          if (userId != null && userId.isNotEmpty) 'userId': int.tryParse(userId),
        },
      );
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<List<FriendRequestItem>> getIncomingRequests() async {
    try {
      final response = await _dio.get(ApiEndpoints.incomingFriendRequests);
      return unwrapList(response.data)
          .map((e) => FriendRequestItem.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList();
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<List<FriendRequestItem>> getOutgoingRequests() async {
    try {
      final response = await _dio.get(ApiEndpoints.outgoingFriendRequests);
      return unwrapList(response.data)
          .map((e) => FriendRequestItem.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList();
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<void> acceptRequest(String requestId) async {
    try {
      await _dio.post(ApiEndpoints.acceptFriendRequest(requestId));
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<void> rejectRequest(String requestId) async {
    try {
      await _dio.post(ApiEndpoints.rejectFriendRequest(requestId));
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<void> unfriend(String friendId) async {
    try {
      await _dio.delete(ApiEndpoints.unfriend(friendId));
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }
}
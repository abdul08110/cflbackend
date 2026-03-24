import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_client.dart';
import '../../../core/network/api_endpoints.dart';
import '../../../core/network/api_helpers.dart';
import '../domain/models/room_models.dart';

final roomRepositoryProvider = Provider<RoomRepository>(
  (ref) => RoomRepository(ref.watch(dioProvider)),
);

final allRoomsProvider = FutureProvider.autoDispose<List<Room>>(
  (ref) async => ref.watch(roomRepositoryProvider).getAllRooms(),
);

final myRoomsProvider = FutureProvider.autoDispose<List<Room>>(
  (ref) async => ref.watch(roomRepositoryProvider).getMyRooms(),
);

final roomDetailProvider = FutureProvider.autoDispose
    .family<RoomDetail, String>(
      (ref, roomId) async =>
          ref.watch(roomRepositoryProvider).getRoomDetail(roomId),
    );

final incomingRoomInvitesProvider =
    FutureProvider.autoDispose<List<RoomInvitation>>(
      (ref) async => ref.watch(roomRepositoryProvider).getIncomingInvitations(),
    );

final roomMembersProvider = FutureProvider.autoDispose
    .family<List<RoomMember>, String>(
      (ref, roomId) async =>
          ref.watch(roomRepositoryProvider).getRoomMembers(roomId),
    );

class RoomRepository {
  RoomRepository(this._dio);
  final Dio _dio;

  Future<List<Room>> getAllRooms() async {
    try {
      final response = await _dio.get(ApiEndpoints.communities);
      return unwrapList(
        response.data,
      ).map((e) => Room.fromJson(Map<String, dynamic>.from(e as Map))).toList();
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<List<Room>> getMyRooms() async {
    try {
      final response = await _dio.get(ApiEndpoints.myCommunities);
      return unwrapList(
        response.data,
      ).map((e) => Room.fromJson(Map<String, dynamic>.from(e as Map))).toList();
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<RoomDetail> getRoomDetail(String roomId) async {
    try {
      final response = await _dio.get(ApiEndpoints.communityDetail(roomId));
      return RoomDetail.fromJson(unwrapMap(response.data));
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<Room> createRoom({
    required String name,
    int maxMembers = 20,
  }) async {
    try {
      final response = await _dio.post(
        ApiEndpoints.createCommunity,
        data: {
          'communityName': name,
          'maxSpots': maxMembers,
        },
      );
      return Room.fromJson(unwrapMap(response.data));
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<void> deleteRoom(String roomId) async {
    try {
      await _dio.delete(ApiEndpoints.deleteCommunity(roomId));
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<Room> updateRoom({
    required String roomId,
    required String name,
    required int maxMembers,
  }) async {
    try {
      final response = await _dio.put(
        ApiEndpoints.updateCommunity(roomId),
        data: {
          'communityName': name,
          'maxSpots': maxMembers,
        },
      );
      return Room.fromJson(unwrapMap(response.data));
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<void> joinByCode(String code) async {
    try {
      await _dio.post(
        ApiEndpoints.joinCommunityByCode,
        data: {'communityCode': code},
      );
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<void> invite({
    required String roomId,
    String? username,
    String? mobile,
    String? inviteMessage,
  }) async {
    try {
      await _dio.post(
        ApiEndpoints.inviteToCommunity(roomId),
        data: {
          if (username != null && username.isNotEmpty) 'username': username,
          if (mobile != null && mobile.isNotEmpty) 'mobile': mobile,
          if (inviteMessage != null && inviteMessage.isNotEmpty)
            'inviteMessage': inviteMessage,
        },
      );
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<RoomContest> createCommunityContest({
    required String roomId,
    required String fixtureId,
    required int joiningPoints,
    required int winnerCount,
    required int maxSpots,
  }) async {
    try {
      final response = await _dio.post(
        ApiEndpoints.createCommunityContest(roomId),
        data: {
          'fixtureId': int.parse(fixtureId),
          'joiningPoints': joiningPoints,
          'winnerCount': winnerCount,
          'maxSpots': maxSpots,
        },
      );
      return RoomContest.fromJson(unwrapMap(response.data));
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<void> inviteToCommunityContest({
    required String roomId,
    required String contestId,
    String? username,
    String? mobile,
    String? inviteMessage,
  }) async {
    try {
      await _dio.post(
        ApiEndpoints.inviteToCommunityContest(roomId, contestId),
        data: {
          if (username != null && username.isNotEmpty) 'username': username,
          if (mobile != null && mobile.isNotEmpty) 'mobile': mobile,
          if (inviteMessage != null && inviteMessage.isNotEmpty)
            'inviteMessage': inviteMessage,
        },
      );
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<List<RoomInvitation>> getIncomingInvitations() async {
    try {
      final response = await _dio.get(
        ApiEndpoints.incomingCommunityInvitations,
      );
      return unwrapList(response.data)
          .map(
            (e) => RoomInvitation.fromJson(Map<String, dynamic>.from(e as Map)),
          )
          .toList();
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<void> acceptInvitation(String invitationId) async {
    try {
      await _dio.post(ApiEndpoints.acceptCommunityInvitation(invitationId));
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<void> declineInvitation(String invitationId) async {
    try {
      await _dio.post(ApiEndpoints.declineCommunityInvitation(invitationId));
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<List<RoomMember>> getRoomMembers(String roomId) async {
    try {
      final response = await _dio.get(ApiEndpoints.roomMembers(roomId));
      return unwrapList(response.data)
          .map((e) => RoomMember.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList();
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<CommunityTeamView> getCommunityTeamView({
    required String roomId,
    required String teamId,
  }) async {
    try {
      final response = await _dio.get(
        ApiEndpoints.communityTeamView(roomId, teamId),
      );
      return CommunityTeamView.fromJson(unwrapMap(response.data));
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }

  Future<void> createCommunityTeam({
    required String roomId,
    required String teamName,
    required List<String> fixturePlayerPoolIds,
    List<String> substituteFixturePlayerPoolIds = const [],
    required String captainPlayerId,
    required String viceCaptainPlayerId,
  }) async {
    try {
      await _dio.post(
        ApiEndpoints.communityTeam(roomId),
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

  Future<void> selectCommunityTeam({
    required String roomId,
    required String teamId,
  }) async {
    try {
      await _dio.put(
        ApiEndpoints.communityTeam(roomId),
        data: {'teamId': int.parse(teamId)},
      );
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }
}

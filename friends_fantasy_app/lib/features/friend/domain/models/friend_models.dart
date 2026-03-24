import '../../../../core/network/api_helpers.dart';

class FriendUser {
  final String id;
  final String username;
  final String fullName;
  final String mobile;
  final bool alreadyFriend;
  final bool requestPending;
  final String? requestDirection;

  const FriendUser({
    required this.id,
    required this.username,
    required this.fullName,
    required this.mobile,
    this.alreadyFriend = false,
    this.requestPending = false,
    this.requestDirection,
  });

  factory FriendUser.fromJson(Map<String, dynamic> json) {
    final username = asString(
      json['username'] ?? json['senderUsername'] ?? json['receiverUsername'],
      fallback: 'User',
    );

    final mobile = asString(
      json['mobileMasked'] ?? json['mobile'] ?? '',
    );

    return FriendUser(
      id: asString(json['userId'] ?? json['id']),
      username: username,
      fullName: asString(json['fullName'] ?? json['name'] ?? username),
      mobile: mobile,
      alreadyFriend: (json['alreadyFriend'] ?? false) == true,
      requestPending: (json['requestPending'] ?? false) == true,
      requestDirection: json['requestDirection'] as String?,
    );
  }
}

class FriendRequestItem {
  final String id;
  final String username;
  final String fullName;
  final String status;

  const FriendRequestItem({
    required this.id,
    required this.username,
    required this.fullName,
    required this.status,
  });

  factory FriendRequestItem.fromJson(Map<String, dynamic> json) {
    final username = asString(
      json['senderUsername'] ?? json['receiverUsername'] ?? json['username'],
      fallback: 'User',
    );

    return FriendRequestItem(
      id: asString(json['requestId'] ?? json['id']),
      username: username,
      fullName: asString(json['fullName'] ?? json['name'] ?? username),
      status: asString(json['status'], fallback: 'PENDING'),
    );
  }
}
import '../../../../core/network/api_helpers.dart';

class AppNotification {
  final String id;
  final String type;
  final String title;
  final String body;
  final String? payloadJson;
  final bool isRead;
  final DateTime? createdAt;

  const AppNotification({
    required this.id,
    required this.type,
    required this.title,
    required this.body,
    required this.payloadJson,
    required this.isRead,
    required this.createdAt,
  });

  bool get isActionable =>
      type == 'FRIEND_REQUEST' || type.startsWith('COMMUNITY_');

  factory AppNotification.fromJson(Map<String, dynamic> json) {
    return AppNotification(
      id: asString(json['notificationId'] ?? json['id']),
      type: asString(json['type']),
      title: asString(json['title']),
      body: asString(json['body']),
      payloadJson: asStringOrNull(json['payloadJson']),
      isRead: asBool(json['isRead']),
      createdAt: _parseDate(json['createdAt']),
    );
  }
}

DateTime? _parseDate(dynamic value) {
  final raw = asStringOrNull(value);
  if (raw == null) return null;
  return DateTime.tryParse(raw)?.toLocal();
}

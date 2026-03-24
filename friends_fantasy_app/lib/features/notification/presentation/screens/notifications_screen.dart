import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../../app/widgets/primary_scaffold.dart';
import '../../../../core/widgets/async_value_view.dart';
import '../../../../core/widgets/empty_state.dart';
import '../../../../core/widgets/section_card.dart';
import '../../data/notification_repository.dart';
import '../../domain/models/notification_models.dart';

class NotificationsScreen extends ConsumerWidget {
  const NotificationsScreen({super.key});

  Future<void> _openNotification(
    BuildContext context,
    WidgetRef ref,
    AppNotification notification,
  ) async {
    if (!notification.isRead) {
      await ref
          .read(notificationRepositoryProvider)
          .markAsRead(notification.id);
      ref.invalidate(myNotificationsProvider);
      ref.invalidate(notificationUnreadCountProvider);
    }

    if (!context.mounted) return;

    if (notification.type.startsWith('FRIEND_')) {
      context.push('/friends');
      return;
    }

    if (notification.type.startsWith('COMMUNITY_')) {
      context.push('/communities');
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final notifications = ref.watch(myNotificationsProvider);

    return PrimaryScaffold(
      currentIndex: -1,
      title: 'Notifications',
      actions: [
        TextButton(
          onPressed: () async {
            await ref.read(notificationRepositoryProvider).markAllAsRead();
            ref.invalidate(myNotificationsProvider);
            ref.invalidate(notificationUnreadCountProvider);
          },
          child: const Text('Mark all'),
        ),
      ],
      body: AsyncValueView(
        value: notifications,
        onRetry: () => ref.invalidate(myNotificationsProvider),
        data: (items) {
          if (items.isEmpty) {
            return const EmptyState(
              icon: Icons.notifications_none_rounded,
              title: 'No notifications',
              subtitle:
                  'Friend requests, community invites, and updates appear here.',
            );
          }

          return ListView(
            padding: const EdgeInsets.only(top: 8, bottom: 24),
            children: items
                .map(
                  (notification) => SectionCard(
                    child: InkWell(
                      borderRadius: BorderRadius.circular(20),
                      onTap: () =>
                          _openNotification(context, ref, notification),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Container(
                            width: 44,
                            height: 44,
                            decoration: BoxDecoration(
                              borderRadius: BorderRadius.circular(14),
                              color: notification.isRead
                                  ? Colors.white.withValues(alpha: 0.06)
                                  : const Color(0xFF18C8FF).withValues(
                                      alpha: 0.18,
                                    ),
                            ),
                            child: Icon(
                              _iconFor(notification.type),
                              color: notification.isRead
                                  ? Colors.white70
                                  : const Color(0xFF66E3FF),
                            ),
                          ),
                          const SizedBox(width: 14),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  children: [
                                    Expanded(
                                      child: Text(
                                        notification.title,
                                        style: const TextStyle(
                                          fontSize: 16,
                                          fontWeight: FontWeight.w800,
                                        ),
                                      ),
                                    ),
                                    if (!notification.isRead)
                                      Container(
                                        width: 10,
                                        height: 10,
                                        decoration: const BoxDecoration(
                                          color: Color(0xFF39E48A),
                                          shape: BoxShape.circle,
                                        ),
                                      ),
                                  ],
                                ),
                                const SizedBox(height: 6),
                                Text(
                                  notification.body,
                                  style: const TextStyle(
                                    color: Color(0xFFA9B4D0),
                                    height: 1.5,
                                  ),
                                ),
                                if (notification.createdAt != null) ...[
                                  const SizedBox(height: 10),
                                  Text(
                                    DateFormat(
                                      'dd MMM, hh:mm a',
                                    ).format(notification.createdAt!),
                                    style: const TextStyle(
                                      color: Color(0xFF7F8AA8),
                                      fontSize: 12,
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                ],
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                )
                .toList(),
          );
        },
      ),
    );
  }
}

IconData _iconFor(String type) {
  if (type.startsWith('FRIEND_')) {
    return Icons.people_alt_rounded;
  }

  if (type.startsWith('COMMUNITY_')) {
    return Icons.groups_rounded;
  }

  return Icons.notifications_rounded;
}

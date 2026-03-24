import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../app/widgets/primary_scaffold.dart';
import '../../../../core/widgets/async_value_view.dart';
import '../../../../core/widgets/empty_state.dart';
import '../../../../core/widgets/section_card.dart';
import '../../data/room_repository.dart';
import '../../domain/models/room_models.dart';

class RoomsScreen extends ConsumerWidget {
  const RoomsScreen({super.key});

  Future<void> _handleAcceptInvite(
    BuildContext context,
    WidgetRef ref,
    RoomInvitation invite,
  ) async {
    try {
      await ref.read(roomRepositoryProvider).acceptInvitation(invite.id);
      ref.invalidate(incomingRoomInvitesProvider);
      ref.invalidate(myRoomsProvider);
      ref.invalidate(allRoomsProvider);

      if (!context.mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('Joined ${invite.roomName}')));
    } catch (e) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.toString().replaceFirst('Exception: ', ''))),
      );
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final myRooms = ref.watch(myRoomsProvider);
    final allRooms = ref.watch(allRoomsProvider);
    final invites = ref.watch(incomingRoomInvitesProvider);

    return PrimaryScaffold(
      currentIndex: 1,
      title: 'Communities',
      actions: [
        IconButton(
          onPressed: () {
            ref.invalidate(myRoomsProvider);
            ref.invalidate(allRoomsProvider);
            ref.invalidate(incomingRoomInvitesProvider);
          },
          icon: const Icon(Icons.refresh_rounded),
        ),
        TextButton(
          onPressed: () => context.push('/communities/join'),
          child: const Text(
            'Join Code',
            style: TextStyle(
              color: Colors.white,
              fontWeight: FontWeight.w800,
            ),
          ),
        ),
      ],
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => context.push('/communities/create'),
        icon: const Icon(Icons.add_rounded),
        label: const Text('Create'),
      ),
      body: ListView(
        padding: const EdgeInsets.only(top: 8, bottom: 100),
        children: [
          Container(
            margin: const EdgeInsets.fromLTRB(16, 8, 16, 8),
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(28),
              gradient: const LinearGradient(
                colors: [
                  Color(0xFF163056),
                  Color(0xFF10213C),
                  Color(0xFF0A1323),
                ],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Your Community Hub',
                  style: TextStyle(
                    fontSize: 24,
                    fontWeight: FontWeight.w900,
                  ),
                ),
                const SizedBox(height: 8),
                const Text(
                  'Create private cricket communities, join with a shared code, accept invites, and run multiple contests inside your circle.',
                  style: TextStyle(
                    color: Color(0xFFA9B4D0),
                    height: 1.45,
                  ),
                ),
                const SizedBox(height: 16),
                Wrap(
                  spacing: 10,
                  runSpacing: 10,
                  children: [
                    _InfoPill(
                      icon: Icons.mail_rounded,
                      label: invites.maybeWhen(
                        data: (items) => '${items.length} invites',
                        orElse: () => 'Invites loading',
                      ),
                    ),
                    _InfoPill(
                      icon: Icons.groups_rounded,
                      label: myRooms.maybeWhen(
                        data: (items) => '${items.length} joined',
                        orElse: () => 'Communities loading',
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 18),
                Row(
                  children: [
                    Expanded(
                      child: ElevatedButton(
                        onPressed: () => context.push('/communities/create'),
                        child: const Text('Create Community'),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: OutlinedButton(
                        onPressed: () => context.push('/communities/join'),
                        child: const Text('Join By Code'),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          SectionCard(
            child: invites.when(
              data: (items) {
                if (items.isEmpty) {
                  return const Text('No pending community invitations.');
                }

                return Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Invitations',
                      style: TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 12),
                    ...items.map(
                      (invite) => Padding(
                        padding: const EdgeInsets.only(bottom: 12),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              invite.roomName,
                              style: const TextStyle(
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              invite.inviteMessage?.trim().isNotEmpty == true
                                  ? invite.inviteMessage!
                                  : 'Invited by ${invite.invitedBy}',
                              style: const TextStyle(color: Color(0xFFA9B4D0)),
                            ),
                            const SizedBox(height: 10),
                            Wrap(
                              spacing: 8,
                              runSpacing: 8,
                              children: [
                                _InfoPill(
                                  icon: Icons.groups_rounded,
                                  label: invite.maxSpots > 0
                                      ? '${invite.joinedMembers}/${invite.maxSpots} members'
                                      : '${invite.joinedMembers} joined',
                                ),
                                const _InfoPill(
                                  icon: Icons.lock_rounded,
                                  label: 'Invite only',
                                ),
                              ],
                            ),
                            const SizedBox(height: 10),
                            Row(
                              mainAxisAlignment: MainAxisAlignment.end,
                              children: [
                                TextButton(
                                  onPressed: () async {
                                    await ref
                                        .read(roomRepositoryProvider)
                                        .declineInvitation(invite.id);
                                    ref.invalidate(incomingRoomInvitesProvider);
                                  },
                                  child: const Text('Decline'),
                                ),
                                const SizedBox(width: 8),
                                ElevatedButton(
                                  onPressed: () =>
                                      _handleAcceptInvite(context, ref, invite),
                                  child: const Text('Accept'),
                                ),
                              ],
                            ),
                          ],
                        ),
                      ),
                    ),
                  ],
                );
              },
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (e, _) => Text(e.toString()),
            ),
          ),
          AsyncValueView(
            value: myRooms,
            onRetry: () => ref.invalidate(myRoomsProvider),
            data: (items) {
              return Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const SectionCard(
                    child: Text(
                      'My Communities',
                      style: TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                  if (items.isEmpty)
                    const EmptyState(
                      icon: Icons.groups_rounded,
                      title: 'No joined communities',
                      subtitle:
                          'Create a community or accept an invitation to start playing with your group.',
                    )
                  else
                    ...items.map(
                      (room) => _RoomCard(
                        room: room,
                        onTap: () => context.push('/communities/${room.id}'),
                        trailing: _RoomBadge(
                          label: room.myRole.toUpperCase() == 'OWNER'
                              ? 'Owner'
                              : 'Member',
                          color: room.myRole.toUpperCase() == 'OWNER'
                              ? const Color(0xFFFFD36A)
                              : const Color(0xFF63D9FF),
                        ),
                      ),
                    ),
                ],
              );
            },
          ),
          AsyncValueView(
            value: allRooms,
            onRetry: () => ref.invalidate(allRoomsProvider),
            data: (items) {
              final visible = items.where((room) => !room.isMember).toList();

              return Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const SectionCard(
                    child: Text(
                      'Discover Communities',
                      style: TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                  if (visible.isEmpty)
                    const EmptyState(
                      icon: Icons.travel_explore_rounded,
                      title: 'No other communities',
                      subtitle:
                          'When other users create communities, they will appear here. You can join with an invite or a shared code.',
                    )
                  else
                    ...visible.map(
                      (room) => _RoomCard(
                        room: room,
                        onTap: () => context.push('/communities/${room.id}'),
                        trailing: _RoomBadge(
                          label: room.isInvited ? 'Invited' : 'Invite only',
                          color: room.isInvited
                              ? const Color(0xFF39E48A)
                              : const Color(0xFF8EA2D7),
                        ),
                      ),
                    ),
                ],
              );
            },
          ),
        ],
      ),
    );
  }
}

class _RoomCard extends StatelessWidget {
  const _RoomCard({
    required this.room,
    this.onTap,
    this.trailing,
  });

  final Room room;
  final VoidCallback? onTap;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final isOwner = room.myRole.toUpperCase() == 'OWNER';

    return SectionCard(
      child: InkWell(
        borderRadius: BorderRadius.circular(20),
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.all(4),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(20),
            gradient: LinearGradient(
              colors: [
                Colors.white.withValues(alpha: 0.05),
                Colors.white.withValues(alpha: 0.02),
              ],
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            ),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        room.name,
                        style: const TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        room.createdByUsername.isEmpty
                            ? 'Code: ${room.code}'
                            : 'Owner: ${room.createdByUsername}',
                        style: const TextStyle(color: Color(0xFFA9B4D0)),
                      ),
                    ],
                  ),
                ),
                ?trailing,
              ],
            ),
            const SizedBox(height: 14),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                _InfoPill(
                  icon: Icons.groups_rounded,
                  label: '${room.memberCount}/${room.maxMembers} members',
                ),
                _InfoPill(
                  icon: Icons.sports_cricket_rounded,
                  label: '${room.contestCount} contests',
                ),
                if (isOwner && room.code.isNotEmpty)
                  _InfoPill(
                    icon: Icons.vpn_key_rounded,
                    label: room.code,
                  ),
              ],
            ),
            const SizedBox(height: 14),
            Text(
              room.isMember
                  ? isOwner
                        ? 'Open this community to invite members, manage contests, and play inside your private group.'
                        : 'Open this community to create contests and play inside your private group.'
                  : 'Visible to everyone, but only invited users can join this community.',
              style: const TextStyle(
                color: Color(0xFFA9B4D0),
                height: 1.5,
              ),
            ),
            ],
          ),
        ),
      ),
    );
  }
}

class _RoomBadge extends StatelessWidget {
  const _RoomBadge({required this.label, required this.color});

  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.14),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: color.withValues(alpha: 0.28)),
      ),
      child: Text(
        label,
        style: TextStyle(color: color, fontWeight: FontWeight.w800),
      ),
    );
  }
}

class _InfoPill extends StatelessWidget {
  const _InfoPill({required this.icon, required this.label});

  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.06),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 15, color: const Color(0xFFA9B4D0)),
          const SizedBox(width: 6),
          Text(
            label,
            style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
          ),
        ],
      ),
    );
  }
}

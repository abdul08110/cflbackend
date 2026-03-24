import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../app/widgets/primary_scaffold.dart';
import '../../../../core/widgets/async_value_view.dart';
import '../../../../core/widgets/section_card.dart';
import '../../data/friend_repository.dart';
import '../../domain/models/friend_models.dart';

class FriendsScreen extends ConsumerStatefulWidget {
  const FriendsScreen({super.key});

  @override
  ConsumerState<FriendsScreen> createState() => _FriendsScreenState();
}

class _FriendsScreenState extends ConsumerState<FriendsScreen> {
  final _searchController = TextEditingController();
  List<FriendUser> _searchResults = const [];
  bool _searching = false;

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _showInfoDialog({
    required String title,
    required String message,
  }) {
    return showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: Text(message),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('OK'),
          ),
        ],
      ),
    );
  }

  Future<void> _search() async {
    final query = _searchController.text.trim();
    if (query.isEmpty) return;

    setState(() => _searching = true);

    try {
      final results = await ref.read(friendRepositoryProvider).search(query);
      if (!mounted) return;
      setState(() => _searchResults = results);

      if (results.isEmpty) {
        await _showInfoDialog(
          title: 'User Not Found',
          message: 'There is no user with this username or mobile number.',
        );
      }
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.toString().replaceFirst('Exception: ', ''))),
      );
    } finally {
      if (mounted) setState(() => _searching = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final friends = ref.watch(friendsListProvider);
    final incoming = ref.watch(incomingFriendRequestsProvider);

    final friendsCount = friends.valueOrNull?.length ?? 0;
    final incomingCount = incoming.valueOrNull?.length ?? 0;

    return PrimaryScaffold(
      currentIndex: 3,
      title: 'Friends',
      body: ListView(
        padding: const EdgeInsets.only(top: 8, bottom: 24),
        children: [
          SectionCard(
            child: Row(
              children: [
                Expanded(
                  child: _StatPill(
                    label: 'Friends',
                    value: '$friendsCount',
                  ),
                ),
                Expanded(
                  child: _StatPill(
                    label: 'Requests',
                    value: '$incomingCount',
                  ),
                ),
                Expanded(
                  child: _StatPill(
                    label: 'Search',
                    value: '${_searchResults.length}',
                  ),
                ),
              ],
            ),
          ),
          SectionCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Add friend',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _searchController,
                  decoration: InputDecoration(
                    labelText: 'Search by username or mobile',
                    prefixIcon: const Icon(Icons.search_rounded),
                    suffixIcon: IconButton(
                      onPressed: _searching ? null : _search,
                      icon: const Icon(Icons.send_rounded),
                    ),
                  ),
                  onSubmitted: (_) => _search(),
                ),
                if (_searching) ...[
                  const SizedBox(height: 12),
                  const LinearProgressIndicator(),
                ],
                if (_searchResults.isNotEmpty) ...[
                  const SizedBox(height: 12),
                  ..._searchResults.map(
                    (user) => ListTile(
                      contentPadding: EdgeInsets.zero,
                      title: Text(user.fullName),
                      subtitle: Text('${user.username} • ${user.mobile}'),
                      trailing: _buildSearchAction(user),
                    ),
                  ),
                ],
              ],
            ),
          ),
          SectionCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Incoming Requests',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
                ),
                const SizedBox(height: 12),
                incoming.when(
                  data: (items) {
                    if (items.isEmpty) {
                      return const Text('No incoming requests.');
                    }

                    return Column(
                      children: items
                          .map(
                            (item) => Row(
                              children: [
                                Expanded(
                                  child: Text('${item.fullName} • ${item.username}'),
                                ),
                                TextButton(
                                  onPressed: () async {
                                    final messenger = ScaffoldMessenger.of(
                                      context,
                                    );
                                    await ref
                                        .read(friendRepositoryProvider)
                                        .acceptRequest(item.id);

                                    ref.invalidate(incomingFriendRequestsProvider);
                                    ref.invalidate(friendsListProvider);

                                    if (!mounted) return;
                                    messenger.showSnackBar(
                                      const SnackBar(
                                        content: Text('Friend request accepted'),
                                      ),
                                    );
                                  },
                                  child: const Text('Accept'),
                                ),
                                TextButton(
                                  onPressed: () async {
                                    final messenger = ScaffoldMessenger.of(
                                      context,
                                    );
                                    await ref
                                        .read(friendRepositoryProvider)
                                        .rejectRequest(item.id);

                                    ref.invalidate(incomingFriendRequestsProvider);

                                    if (!mounted) return;
                                    messenger.showSnackBar(
                                      const SnackBar(
                                        content: Text('Friend request rejected'),
                                      ),
                                    );
                                  },
                                  child: const Text('Reject'),
                                ),
                              ],
                            ),
                          )
                          .toList(),
                    );
                  },
                  loading: () => const Center(child: CircularProgressIndicator()),
                  error: (e, _) => Text(e.toString()),
                ),
              ],
            ),
          ),
          AsyncValueView(
            value: friends,
            onRetry: () => ref.invalidate(friendsListProvider),
            data: (items) => SectionCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Friends List',
                    style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
                  ),
                  const SizedBox(height: 12),
                  if (items.isEmpty)
                    const Text('No friends added yet.')
                  else
                    ...items.map(
                      (friend) => ListTile(
                        contentPadding: EdgeInsets.zero,
                        title: Text(friend.fullName),
                        subtitle: Text('${friend.username} • ${friend.mobile}'),
                        trailing: TextButton(
                          onPressed: () async {
                            final messenger = ScaffoldMessenger.of(context);
                            await ref
                                .read(friendRepositoryProvider)
                                .unfriend(friend.id);

                            ref.invalidate(friendsListProvider);

                            if (!mounted) return;
                            messenger.showSnackBar(
                              const SnackBar(content: Text('Friend removed')),
                            );
                          },
                          child: const Text('Remove'),
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSearchAction(FriendUser user) {
    if (user.alreadyFriend) {
      return const Text(
        'Friends',
        style: TextStyle(fontWeight: FontWeight.w700),
      );
    }

    if (user.requestPending) {
      return Text(
        user.requestDirection == 'INCOMING' ? 'Requested You' : 'Pending',
        style: const TextStyle(fontWeight: FontWeight.w700),
      );
    }

    return TextButton(
      onPressed: () async {
        await ref.read(friendRepositoryProvider).sendRequest(
              username: user.username,
            );

        if (!mounted) return;

        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Friend request sent')),
        );

        setState(() {
          _searchResults = _searchResults
              .map(
                (e) => e.id == user.id
                    ? FriendUser(
                        id: e.id,
                        username: e.username,
                        fullName: e.fullName,
                        mobile: e.mobile,
                        alreadyFriend: e.alreadyFriend,
                        requestPending: true,
                        requestDirection: 'OUTGOING',
                      )
                    : e,
              )
              .toList();
        });
      },
      child: const Text('Add'),
    );
  }
}

class _StatPill extends StatelessWidget {
  const _StatPill({
    required this.label,
    required this.value,
  });

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Text(
          value,
          style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800),
        ),
        const SizedBox(height: 4),
        Text(label),
      ],
    );
  }
}

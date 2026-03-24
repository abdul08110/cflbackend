import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/widgets/async_value_view.dart';
import '../../../../core/widgets/empty_state.dart';
import '../../../../core/widgets/section_card.dart';
import '../../data/team_repository.dart';

class MyTeamsScreen extends ConsumerWidget {
  const MyTeamsScreen({super.key, required this.fixtureId});
  final String fixtureId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final teams = ref.watch(myTeamsProvider(fixtureId));
    return Scaffold(
      appBar: AppBar(
        title: const Text('My Teams'),
        actions: [IconButton(onPressed: () => context.push('/fixtures/$fixtureId/team'), icon: const Icon(Icons.add_rounded))],
      ),
      body: AsyncValueView(
        value: teams,
        onRetry: () => ref.invalidate(myTeamsProvider(fixtureId)),
        data: (items) {
          if (items.isEmpty) {
            return EmptyState(
              icon: Icons.groups_3_rounded,
              title: 'No teams created',
              subtitle: 'Create your first team for this fixture.',
              action: ElevatedButton(onPressed: () => context.push('/fixtures/$fixtureId/team'), child: const Text('Create Team')),
            );
          }
          return ListView(
            padding: const EdgeInsets.only(top: 8, bottom: 24),
            children: items.map((team) => SectionCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(team.name, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
                  const SizedBox(height: 8),
                  Text(
                    team.canDelete
                        ? '${team.playerIds.length} players selected'
                        : 'Joined teams cannot be deleted',
                  ),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Expanded(child: OutlinedButton(onPressed: () => context.push('/fixtures/$fixtureId/team?teamId=${team.id}'), child: const Text('Edit'))),
                      const SizedBox(width: 12),
                      Expanded(
                        child: ElevatedButton(
                          onPressed: team.canDelete
                              ? () async {
                                  try {
                                    await ref
                                        .read(teamRepositoryProvider)
                                        .deleteTeam(team.id);
                                    ref.invalidate(myTeamsProvider(fixtureId));
                                  } catch (e) {
                                    if (context.mounted) {
                                      ScaffoldMessenger.of(context).showSnackBar(
                                        SnackBar(
                                          content: Text(
                                            e.toString().replaceFirst(
                                              'Exception: ',
                                              '',
                                            ),
                                          ),
                                        ),
                                      );
                                    }
                                  }
                                }
                              : null,
                          child: Text(team.canDelete ? 'Delete' : 'Joined'),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            )).toList(),
          );
        },
      ),
    );
  }
}

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../app/widgets/primary_scaffold.dart';
import '../../../../core/widgets/async_value_view.dart';
import '../../../../core/widgets/empty_state.dart';
import '../../../../core/widgets/section_card.dart';
import '../../data/fixture_repository.dart';

class FixturesScreen extends ConsumerWidget {
  const FixturesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final fixtures = ref.watch(upcomingFixturesProvider);

    return PrimaryScaffold(
      currentIndex: 2,
      title: 'Upcoming Cricket Fixtures',
      actions: [
        IconButton(
          onPressed: () => ref.invalidate(upcomingFixturesProvider),
          icon: const Icon(Icons.refresh_rounded),
        ),
      ],
      body: AsyncValueView(
        value: fixtures,
        onRetry: () => ref.invalidate(upcomingFixturesProvider),
        data: (items) {
          if (items.isEmpty) {
            return const EmptyState(
              icon: Icons.sports_cricket_rounded,
              title: 'No upcoming fixtures',
              subtitle:
                  'Upcoming cricket fixtures will appear here after sync.',
            );
          }

          return ListView(
            padding: const EdgeInsets.only(top: 8, bottom: 24),
            children: items.map<Widget>((fixture) {
              return SectionCard(
                child: ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text(
                    '${fixture.teamAName} vs ${fixture.teamBName}',
                    style: const TextStyle(fontWeight: FontWeight.w800),
                  ),
                  subtitle: Text(
                    '${fixture.timeText}\n'
                    '${fixture.venue.isEmpty ? fixture.league : fixture.venue}',
                  ),
                  trailing: const Icon(Icons.chevron_right_rounded),
                  onTap: () => context.push('/fixtures/${fixture.id}'),
                ),
              );
            }).toList(),
          );
        },
      ),
    );
  }
}

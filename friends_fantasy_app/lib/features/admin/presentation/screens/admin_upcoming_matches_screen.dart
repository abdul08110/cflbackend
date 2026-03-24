import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../data/admin_repository.dart';
import '../../domain/models/admin_models.dart';

final adminUpcomingFixturesProvider =
FutureProvider.autoDispose<List<AdminFixture>>((ref) async {
  final repo = ref.read(adminRepositoryProvider);
  await repo.syncUpcomingFixtures();
  return repo.getUpcomingFixtures();
});

class AdminUpcomingMatchesScreen extends ConsumerWidget {
  const AdminUpcomingMatchesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final asyncFixtures = ref.watch(adminUpcomingFixturesProvider);

    return Scaffold(
      backgroundColor: const Color(0xFF070B17),
      appBar: AppBar(
        title: const Text('Upcoming Matches'),
        backgroundColor: const Color(0xFF0F1528),
        actions: [
          IconButton(
            onPressed: () => ref.invalidate(adminUpcomingFixturesProvider),
            icon: const Icon(Icons.refresh_rounded),
          ),
        ],
      ),
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            colors: [Color(0xFF140A0D), Color(0xFF090C18), Color(0xFF05070F)],
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
          ),
        ),
        child: asyncFixtures.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (error, stack) => Center(
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Text(
                'Failed to load upcoming matches\n$error',
                textAlign: TextAlign.center,
                style: const TextStyle(color: Colors.white70),
              ),
            ),
          ),
          data: (fixtures) {
            if (fixtures.isEmpty) {
              return const Center(
                child: Text(
                  'No upcoming matches found',
                  style: TextStyle(color: Colors.white70),
                ),
              );
            }

            return RefreshIndicator(
              onRefresh: () async {
                ref.invalidate(adminUpcomingFixturesProvider);
                await ref.read(adminUpcomingFixturesProvider.future);
              },
              child: ListView.separated(
                padding: const EdgeInsets.all(16),
                itemCount: fixtures.length,
                separatorBuilder: (_, _) => const SizedBox(height: 14),
                itemBuilder: (context, index) {
                  final fixture = fixtures[index];
                  return _AdminFixtureCard(
                    fixture: fixture,
                    onTap: () {
                      context.push(
                        '/admin/create-contest/${fixture.fixtureId}',
                        extra: fixture,
                      );
                    },
                  );
                },
              ),
            );
          },
        ),
      ),
    );
  }
}

class _AdminFixtureCard extends StatelessWidget {
  const _AdminFixtureCard({
    required this.fixture,
    required this.onTap,
  });

  final AdminFixture fixture;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final home = fixture.participants.where((e) => e.isHome).toList();
    final away = fixture.participants.where((e) => !e.isHome).toList();

    final homeName =
    home.isNotEmpty ? (home.first.shortName ?? home.first.teamName) : 'TBD';
    final awayName =
    away.isNotEmpty ? (away.first.shortName ?? away.first.teamName) : 'TBD';

    return Material(
      color: Colors.transparent,
      child: InkWell(
        borderRadius: BorderRadius.circular(24),
        onTap: onTap,
        child: Ink(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(24),
            color: const Color(0xFF0F1528),
            border: Border.all(color: Colors.white.withValues(alpha: 0.06)),
            boxShadow: [
              BoxShadow(
                color: const Color(0xFFFF8A65).withValues(alpha: 0.08),
                blurRadius: 20,
                spreadRadius: 1,
              ),
            ],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  _StatusChip(label: fixture.status),
                  const Spacer(),
                  Text(
                    _formatDateTime(fixture.startTime),
                    style: const TextStyle(
                      color: Colors.white70,
                      fontWeight: FontWeight.w600,
                      fontSize: 12,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 14),
              Text(
                fixture.title,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 18,
                  fontWeight: FontWeight.w900,
                ),
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: _TeamBlock(
                      title: homeName,
                      subtitle: home.isNotEmpty ? home.first.teamName : 'Home team',
                      alignEnd: false,
                    ),
                  ),
                  const Padding(
                    padding: EdgeInsets.symmetric(horizontal: 12),
                    child: Text(
                      'VS',
                      style: TextStyle(
                        color: Colors.white54,
                        fontSize: 16,
                        fontWeight: FontWeight.w900,
                      ),
                    ),
                  ),
                  Expanded(
                    child: _TeamBlock(
                      title: awayName,
                      subtitle: away.isNotEmpty ? away.first.teamName : 'Away team',
                      alignEnd: true,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              SizedBox(
                width: double.infinity,
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    gradient: const LinearGradient(
                      colors: [Color(0xFFFF5F6D), Color(0xFFFFC371)],
                    ),
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: ElevatedButton(
                    onPressed: onTap,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.transparent,
                      shadowColor: Colors.transparent,
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(16),
                      ),
                    ),
                    child: const Text(
                      'Create Contest',
                      style: TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  String _formatDateTime(DateTime dateTime) {
    final d = dateTime.day.toString().padLeft(2, '0');
    final m = dateTime.month.toString().padLeft(2, '0');
    final y = dateTime.year.toString();
    final hh = dateTime.hour.toString().padLeft(2, '0');
    final mm = dateTime.minute.toString().padLeft(2, '0');
    return '$d/$m/$y  $hh:$mm';
  }
}

class _TeamBlock extends StatelessWidget {
  const _TeamBlock({
    required this.title,
    required this.subtitle,
    required this.alignEnd,
  });

  final String title;
  final String subtitle;
  final bool alignEnd;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment:
      alignEnd ? CrossAxisAlignment.end : CrossAxisAlignment.start,
      children: [
        Text(
          title,
          textAlign: alignEnd ? TextAlign.right : TextAlign.left,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 20,
            fontWeight: FontWeight.w900,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          subtitle,
          textAlign: alignEnd ? TextAlign.right : TextAlign.left,
          style: const TextStyle(
            color: Colors.white60,
            fontSize: 12,
            fontWeight: FontWeight.w600,
          ),
        ),
      ],
    );
  }
}

class _StatusChip extends StatelessWidget {
  const _StatusChip({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 7),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(999),
        color: Colors.white.withValues(alpha: 0.08),
        border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
      ),
      child: Text(
        label,
        style: const TextStyle(
          color: Colors.white,
          fontSize: 11,
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }
}

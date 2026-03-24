import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../app/widgets/primary_scaffold.dart';
import '../../../../core/widgets/async_value_view.dart';
import '../../../../core/widgets/section_card.dart';
import '../../../auth/presentation/providers/auth_controller.dart';
import '../../../contest/data/contest_repository.dart';
import '../../../contest/domain/models/contest_models.dart';
import '../../data/stats_repository.dart';

class ProfileScreen extends ConsumerWidget {
  const ProfileScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final auth = ref.watch(authControllerProvider);
    final stats = ref.watch(myStatsProvider);
    final history = ref.watch(myContestHistoryProvider);

    return PrimaryScaffold(
      currentIndex: -1, // No longer in main nav
      title: 'Profile',
      actions: const [], // Logout is now globally in PrimaryScaffold
      body: ListView(
        padding: const EdgeInsets.only(top: 8, bottom: 24),
        children: [
          SectionCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(auth.user?.fullName ?? 'Player',
                    style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w800)),
                const SizedBox(height: 8),
                Text(auth.user?.username ?? ''),
                const SizedBox(height: 4),
                Text(auth.user?.mobile ?? ''),
                if ((auth.user?.email ?? '').isNotEmpty) ...[
                  const SizedBox(height: 4),
                  Text(auth.user!.email!)
                ],
                const SizedBox(height: 16),
                SizedBox(
                  width: double.infinity,
                  child: OutlinedButton.icon(
                    onPressed: () => context.push('/profile/change-password'),
                    icon: const Icon(Icons.lock_reset_rounded),
                    label: const Text(
                      'Change Password',
                      textAlign: TextAlign.center,
                    ),
                  ),
                ),
              ],
            ),
          ),
          AsyncValueView(
            value: stats,
            onRetry: () => ref.invalidate(myStatsProvider),
            data: (data) => SectionCard(
              child: Row(
                children: [
                  Expanded(
                      child: _ProfileStat(label: 'Played', value: '${data.contestsPlayed}')),
                  Expanded(child: _ProfileStat(label: 'Won', value: '${data.contestsWon}')),
                  Expanded(
                      child: _ProfileStat(
                          label: 'Winnings', value: data.totalWinnings.toStringAsFixed(0))),
                ],
              ),
            ),
          ),
          SectionCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Contest History',
                    style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
                const SizedBox(height: 12),
                history.when(
                  data: (List<MyContestEntry> items) {
                    if (items.isEmpty) return const Text('No contest history yet.');
                    return Column(
                      children: items
                          .map<Widget>((entry) => ListTile(
                                contentPadding: EdgeInsets.zero,
                                title: Text(entry.contestName),
                                subtitle: Text(entry.fixtureTitle),
                                trailing: Column(
                                  mainAxisAlignment: MainAxisAlignment.center,
                                  crossAxisAlignment: CrossAxisAlignment.end,
                                  children: [
                                    Text('${entry.points.toStringAsFixed(1)} pts'),
                                    Text(entry.winnings.toStringAsFixed(2))
                                  ],
                                ),
                              ))
                          .toList(),
                    );
                  },
                  loading: () => const Center(child: CircularProgressIndicator()),
                  error: (e, _) => Text(e.toString()),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _ProfileStat extends StatelessWidget {
  const _ProfileStat({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Text(value, style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
        const SizedBox(height: 4),
        Text(label),
      ],
    );
  }
}

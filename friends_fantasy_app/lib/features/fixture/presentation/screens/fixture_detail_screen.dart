import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/widgets/async_value_view.dart';
import '../../../../core/widgets/live_match_scorecard.dart';
import '../../../../core/widgets/section_card.dart';
import '../../data/fixture_repository.dart';
import '../../domain/models/fixture_models.dart';

class FixtureDetailScreen extends ConsumerStatefulWidget {
  const FixtureDetailScreen({super.key, required this.fixtureId});
  final String fixtureId;

  @override
  ConsumerState<FixtureDetailScreen> createState() => _FixtureDetailScreenState();
}

class _FixtureDetailScreenState extends ConsumerState<FixtureDetailScreen> {
  static const _liveRefreshInterval = Duration(seconds: 30);

  Timer? _autoRefreshTimer;

  @override
  void initState() {
    super.initState();
    _autoRefreshTimer = Timer.periodic(_liveRefreshInterval, (_) {
      if (!mounted) return;

      final detail = ref.read(fixtureDetailProvider(widget.fixtureId));
      final shouldRefresh = detail.maybeWhen(
        data: _shouldAutoRefresh,
        orElse: () => false,
      );

      if (shouldRefresh) {
        ref.invalidate(fixtureDetailProvider(widget.fixtureId));
      }
    });
  }

  @override
  void dispose() {
    _autoRefreshTimer?.cancel();
    super.dispose();
  }

  bool _shouldAutoRefresh(FixtureDetail detail) {
    if (detail.liveData?.live == true) {
      return true;
    }
    return detail.summary.statusLabel == 'LIVE';
  }

  @override
  Widget build(BuildContext context) {
    final detail = ref.watch(fixtureDetailProvider(widget.fixtureId));

    return Scaffold(
      appBar: AppBar(title: const Text('Fixture Detail')),
      body: ListView(
        padding: const EdgeInsets.only(top: 8, bottom: 24),
        children: [
          AsyncValueView(
            value: detail,
            onRetry: () => ref.invalidate(fixtureDetailProvider(widget.fixtureId)),
            data: (data) => SectionCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '${data.summary.teamAName} vs ${data.summary.teamBName}',
                    style: const TextStyle(
                      fontSize: 24,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(data.summary.timeText),
                  const SizedBox(height: 4),
                  Text(data.summary.venue),
                  if (data.format.isNotEmpty) ...[
                    const SizedBox(height: 8),
                    Text(
                      data.format,
                      style: const TextStyle(
                        color: Color(0xFFA9B4D0),
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ],
                  if (data.note.isNotEmpty) ...[
                    const SizedBox(height: 8),
                    Text(data.note),
                  ],
                  const SizedBox(height: 16),
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton(
                      onPressed: () => context.push('/communities'),
                      child: const Text(
                        'Open Community',
                        textAlign: TextAlign.center,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
          AsyncValueView(
            value: detail,
            onRetry: () => ref.invalidate(fixtureDetailProvider(widget.fixtureId)),
            data: (data) => data.liveData != null && data.liveData!.hasContent
                ? LiveMatchScorecard(
                    liveData: data.liveData!,
                    title: 'Match Scorecard',
                  )
                : const SizedBox.shrink(),
          ),
          const SectionCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Community Format',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800),
                ),
                SizedBox(height: 12),
                Text(
                  'Communities are created separately. Once you are inside a community, any member can create a contest for this match with fixed joining points, and only community members can join it.',
                  style: TextStyle(height: 1.5),
                ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: OutlinedButton.icon(
              onPressed: () => context.push('/communities'),
              icon: const Icon(Icons.add_circle_outline_rounded),
              label: const Text('Open Community', textAlign: TextAlign.center),
            ),
          ),
        ],
      ),
    );
  }
}

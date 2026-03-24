import 'package:flutter/material.dart';

import '../../features/fixture/domain/models/fixture_models.dart';
import 'section_card.dart';

class LiveMatchScorecard extends StatelessWidget {
  const LiveMatchScorecard({
    super.key,
    required this.liveData,
    this.title = 'Live Scorecard',
  });

  final FixtureLiveData liveData;
  final String title;

  @override
  Widget build(BuildContext context) {
    if (!liveData.hasContent) {
      return const SizedBox.shrink();
    }

    final superOverTone = switch (liveData.superOverStatus) {
      'ACTIVE' => const Color(0xFFFFA24B),
      'COMPLETED' => const Color(0xFF63D9FF),
      _ => const Color(0xFFA9B4D0),
    };

    return SectionCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  title,
                  style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w800,
                  ),
                ),
              ),
              if (liveData.live)
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 10,
                    vertical: 6,
                  ),
                  decoration: BoxDecoration(
                    color: const Color(0xFFFF7A59).withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(999),
                    border: Border.all(
                      color: const Color(0xFFFF7A59).withValues(alpha: 0.28),
                    ),
                  ),
                  child: const Text(
                    'LIVE',
                    style: TextStyle(
                      color: Color(0xFFFF7A59),
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
            ],
          ),
          if (liveData.innings.isNotEmpty) ...[
            const SizedBox(height: 14),
            ...liveData.innings.map(
              (innings) => Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: _InningsTile(innings: innings),
              ),
            ),
          ],
          if (liveData.revisedTarget != null || liveData.revisedOvers != null) ...[
            const SizedBox(height: 4),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: 0.04),
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
              ),
              child: Text(
                'Revised target ${liveData.revisedTarget ?? '-'} in ${liveData.revisedOvers ?? '-'} overs',
                style: const TextStyle(
                  color: Color(0xFFA9B4D0),
                  fontWeight: FontWeight.w700,
                ),
              ),
            ),
          ],
          if (liveData.superOver) ...[
            const SizedBox(height: 4),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: superOverTone.withValues(alpha: 0.12),
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: superOverTone.withValues(alpha: 0.24)),
              ),
              child: Text(
                liveData.superOverStatus == 'ACTIVE'
                    ? 'Super Over is active. Fantasy points stay locked to regular play, Dream11-style.'
                    : 'This match went to a Super Over. Fantasy points stay based on regular play only.',
                style: TextStyle(
                  color: superOverTone,
                  fontWeight: FontWeight.w700,
                  height: 1.4,
                ),
              ),
            ),
          ],
          if (liveData.lastPeriod.isNotEmpty) ...[
            const SizedBox(height: 10),
            Text(
              liveData.lastPeriod,
              style: const TextStyle(
                color: Color(0xFFA9B4D0),
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
          if (liveData.note.isNotEmpty) ...[
            const SizedBox(height: 10),
            Text(
              liveData.note,
              style: const TextStyle(
                color: Color(0xFFE7EDF8),
                height: 1.45,
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _InningsTile extends StatelessWidget {
  const _InningsTile({required this.innings});

  final FixtureInningsScore innings;

  @override
  Widget build(BuildContext context) {
    final accent = innings.superOver
        ? const Color(0xFFFFA24B)
        : innings.current
            ? const Color(0xFF39E48A)
            : const Color(0xFF63D9FF);

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(18),
        gradient: LinearGradient(
          colors: innings.current
              ? [
                  accent.withValues(alpha: 0.20),
                  const Color(0xFF0E162B),
                ]
              : [
                  Colors.white.withValues(alpha: 0.05),
                  Colors.white.withValues(alpha: 0.03),
                ],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        border: Border.all(color: accent.withValues(alpha: 0.24)),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Text(
                      innings.shortName,
                      style: const TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.w900,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 8,
                        vertical: 4,
                      ),
                      decoration: BoxDecoration(
                        color: accent.withValues(alpha: 0.14),
                        borderRadius: BorderRadius.circular(999),
                      ),
                      child: Text(
                        innings.label,
                        style: TextStyle(
                          color: accent,
                          fontSize: 11,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 6),
                Text(
                  innings.teamName,
                  style: const TextStyle(color: Color(0xFFA9B4D0)),
                ),
              ],
            ),
          ),
          const SizedBox(width: 12),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(
                innings.scoreline,
                style: const TextStyle(
                  fontSize: 24,
                  fontWeight: FontWeight.w900,
                ),
              ),
              if (innings.oversText.isNotEmpty)
                Text(
                  innings.oversText,
                  style: const TextStyle(
                    color: Color(0xFFA9B4D0),
                    fontWeight: FontWeight.w700,
                  ),
                ),
            ],
          ),
        ],
      ),
    );
  }
}

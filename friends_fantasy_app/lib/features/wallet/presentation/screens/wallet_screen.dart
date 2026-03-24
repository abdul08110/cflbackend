import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../../../app/widgets/primary_scaffold.dart';
import '../../../../core/widgets/async_value_view.dart';
import '../../../../core/widgets/section_card.dart';
import '../../data/wallet_repository.dart';
import '../../domain/models/wallet_models.dart';

class WalletScreen extends ConsumerStatefulWidget {
  const WalletScreen({super.key, this.initialSection});

  final String? initialSection;

  @override
  ConsumerState<WalletScreen> createState() => _WalletScreenState();
}

class _WalletScreenState extends ConsumerState<WalletScreen> {
  final GlobalKey _historySectionKey = GlobalKey();
  bool _historyScrollScheduled = false;

  void _scrollToHistoryIfNeeded() {
    if (widget.initialSection != 'history' || _historyScrollScheduled) return;

    _historyScrollScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final targetContext = _historySectionKey.currentContext;
      if (targetContext == null) {
        _historyScrollScheduled = false;
        return;
      }

      Scrollable.ensureVisible(
        targetContext,
        duration: const Duration(milliseconds: 420),
        curve: Curves.easeOutCubic,
        alignment: 0.08,
      );
    });
  }

  @override
  Widget build(BuildContext context) {
    final wallet = ref.watch(myWalletProvider);
    return PrimaryScaffold(
      currentIndex: 2,
      title: 'Wallet',
      body: AsyncValueView(
        value: wallet,
        onRetry: () => ref.invalidate(myWalletProvider),
        data: (data) {
          _scrollToHistoryIfNeeded();

          return ListView(
            padding: const EdgeInsets.only(top: 8, bottom: 24),
            children: [
              SectionCard(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Point Balance',
                      style: TextStyle(fontSize: 16, color: Color(0xFFA9B4D0)),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      '${data.balance.toStringAsFixed(2)} ${data.currency}',
                      style: const TextStyle(
                        fontSize: 30,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ],
                ),
              ),
              SectionCard(
                child: Row(
                  children: [
                    Expanded(
                      child: _InfoBlock(
                        title: 'Contests Joined',
                        value: '${data.totalContestsJoined}',
                      ),
                    ),
                    Expanded(
                      child: _InfoBlock(
                        title: 'Total Winnings',
                        value: data.totalWinnings.toStringAsFixed(2),
                      ),
                    ),
                  ],
                ),
              ),
              const SectionCard(
                child: Text(
                  'Your wallet tracks community joins, contest entries, winnings, and refunds. You need enough points before joining or creating a community.',
                  style: TextStyle(height: 1.5),
                ),
              ),
              SectionCard(
                key: _historySectionKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Wallet History',
                      style: TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                    const SizedBox(height: 12),
                    if (data.history.isEmpty)
                      const Text(
                        'No wallet transactions yet.',
                        style: TextStyle(color: Color(0xFFA9B4D0)),
                      )
                    else
                      ...data.history.map(
                        (entry) => Padding(
                          padding: const EdgeInsets.only(bottom: 12),
                          child: _TransactionTile(entry: entry),
                        ),
                      ),
                  ],
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}

class _InfoBlock extends StatelessWidget {
  const _InfoBlock({required this.title, required this.value});
  final String title;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: const TextStyle(color: Color(0xFFA9B4D0))),
        const SizedBox(height: 8),
        Text(
          value,
          style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800),
        ),
      ],
    );
  }
}

class _TransactionTile extends StatelessWidget {
  const _TransactionTile({required this.entry});

  final WalletTransactionItem entry;

  @override
  Widget build(BuildContext context) {
    final isCredit = entry.direction == 'CREDIT';
    final accent = isCredit ? const Color(0xFF39E48A) : const Color(0xFFFF8A8A);
    final icon = isCredit ? Icons.south_west_rounded : Icons.north_east_rounded;
    final timestamp = entry.createdAt == null
        ? null
        : DateFormat('dd MMM, hh:mm a').format(entry.createdAt!);

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.04),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              color: accent.withValues(alpha: 0.14),
              borderRadius: BorderRadius.circular(14),
            ),
            child: Icon(icon, color: accent),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  _titleFor(entry),
                  style: const TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  entry.remarks,
                  style: const TextStyle(color: Color(0xFFA9B4D0), height: 1.4),
                ),
                const SizedBox(height: 6),
                Text(
                  'Balance after: ${entry.balanceAfter} pts',
                  style: const TextStyle(color: Color(0xFFA9B4D0)),
                ),
                if (timestamp != null) ...[
                  const SizedBox(height: 4),
                  Text(
                    timestamp,
                    style: const TextStyle(color: Color(0xFFA9B4D0)),
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(width: 12),
          Text(
            '${entry.signedPoints > 0 ? '+' : ''}${entry.signedPoints} pts',
            style: TextStyle(
              color: accent,
              fontSize: 16,
              fontWeight: FontWeight.w900,
            ),
          ),
        ],
      ),
    );
  }

  String _titleFor(WalletTransactionItem entry) {
    switch (entry.txnType) {
      case 'ADMIN_CREDIT':
        return 'Points Added';
      case 'ADMIN_DEBIT':
        return 'Points Removed';
      case 'CONTEST_JOIN_DEBIT':
        return 'Contest Join';
      case 'CONTEST_WIN_CREDIT':
        return 'Contest Win';
      case 'REFUND':
        return 'Refund';
      case 'BONUS':
        return 'Bonus';
      case 'ADJUSTMENT':
        return 'Adjustment';
      default:
        return 'Wallet Transaction';
    }
  }
}

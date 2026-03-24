import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../data/admin_repository.dart';
import '../../domain/models/admin_models.dart';

class AdminCreateContestScreen extends ConsumerStatefulWidget {
  const AdminCreateContestScreen({
    super.key,
    required this.fixtureId,
    this.fixture,
  });

  final int fixtureId;
  final AdminFixture? fixture;

  @override
  ConsumerState<AdminCreateContestScreen> createState() =>
      _AdminCreateContestScreenState();
}

class _AdminCreateContestScreenState
    extends ConsumerState<AdminCreateContestScreen> {
  final _formKey = GlobalKey<FormState>();

  final _contestNameController = TextEditingController();
  final _maxSpotsController = TextEditingController();
  final _entryFeeController = TextEditingController();
  final _prizePoolController = TextEditingController();
  final _winnerCountController = TextEditingController();

  bool _joinConfirmRequired = false;
  bool _isBusy = false;

  final List<_PrizeRowData> _prizeRows = [
    _PrizeRowData(),
  ];

  @override
  void dispose() {
    _contestNameController.dispose();
    _maxSpotsController.dispose();
    _entryFeeController.dispose();
    _prizePoolController.dispose();
    _winnerCountController.dispose();

    for (final row in _prizeRows) {
      row.dispose();
    }
    super.dispose();
  }

  void _addPrizeRow() {
    setState(() {
      _prizeRows.add(_PrizeRowData());
    });
  }

  void _removePrizeRow(int index) {
    if (_prizeRows.length == 1) return;
    setState(() {
      _prizeRows[index].dispose();
      _prizeRows.removeAt(index);
    });
  }

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();
    if (!_formKey.currentState!.validate()) return;

    final winnerCount = int.parse(_winnerCountController.text.trim());
    final prizePool = int.parse(_prizePoolController.text.trim());

    final prizes = <ContestPrizeDraft>[];
    int coveredWinners = 0;
    int totalPrizePoints = 0;

    for (final row in _prizeRows) {
      final from = int.parse(row.rankFromController.text.trim());
      final to = int.parse(row.rankToController.text.trim());
      final prize = int.parse(row.prizePointsController.text.trim());

      if (from > to) {
        _showMessage('Prize rank range is invalid');
        return;
      }

      final winnersInBand = (to - from) + 1;
      coveredWinners += winnersInBand;
      totalPrizePoints += winnersInBand * prize;

      prizes.add(
        ContestPrizeDraft(
          rankFrom: from,
          rankTo: to,
          prizePoints: prize,
        ),
      );
    }

    if (coveredWinners != winnerCount) {
      _showMessage('Prize breakup winner count must equal total winner count');
      return;
    }

    if (totalPrizePoints > prizePool) {
      _showMessage('Prize breakup total exceeds prize pool');
      return;
    }

    final payload = CreateContestPayload(
      contestName: _contestNameController.text.trim(),
      maxSpots: int.parse(_maxSpotsController.text.trim()),
      entryFeePoints: int.parse(_entryFeeController.text.trim()),
      prizePoolPoints: prizePool,
      winnerCount: winnerCount,
      joinConfirmRequired: _joinConfirmRequired,
      scoringTemplateId: 1,
      prizes: prizes,
    );

    setState(() => _isBusy = true);

    try {
      await ref.read(adminRepositoryProvider).createContest(
        fixtureId: widget.fixtureId.toString(),
        payload: payload,
      );

      if (!mounted) return;
      _showMessage('Contest created successfully', isError: false);
      Navigator.of(context).pop();
    } catch (e) {
      _showMessage('Failed to create contest: ${e.toString().replaceFirst('Exception: ', '')}');
    } finally {
      if (mounted) {
        setState(() => _isBusy = false);
      }
    }
  }

  void _showMessage(String message, {bool isError = true}) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        backgroundColor:
        isError ? Colors.redAccent.shade200 : Colors.green.shade600,
        content: Text(message),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final fixture = widget.fixture;

    return Scaffold(
      backgroundColor: const Color(0xFF070B17),
      appBar: AppBar(
        title: const Text('Create Contest'),
        backgroundColor: const Color(0xFF0F1528),
      ),
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            colors: [Color(0xFF140A0D), Color(0xFF090C18), Color(0xFF05070F)],
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
          ),
        ),
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 28),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (fixture != null) ...[
                _FixtureInfoCard(fixture: fixture),
                const SizedBox(height: 16),
              ],
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Form(
                    key: _formKey,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'Contest Details',
                          style: TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.w900,
                          ),
                        ),
                        const SizedBox(height: 16),
                        TextFormField(
                          controller: _contestNameController,
                          decoration: const InputDecoration(
                            labelText: 'Contest Name',
                          ),
                          validator: _requiredField,
                        ),
                        const SizedBox(height: 14),
                        TextFormField(
                          controller: _maxSpotsController,
                          keyboardType: TextInputType.number,
                          decoration: const InputDecoration(
                            labelText: 'Max Spots',
                          ),
                          validator: _numberField,
                        ),
                        const SizedBox(height: 14),
                        TextFormField(
                          controller: _entryFeeController,
                          keyboardType: TextInputType.number,
                          decoration: const InputDecoration(
                            labelText: 'Entry Fee Points',
                          ),
                          validator: _numberField,
                        ),
                        const SizedBox(height: 14),
                        TextFormField(
                          controller: _prizePoolController,
                          keyboardType: TextInputType.number,
                          decoration: const InputDecoration(
                            labelText: 'Prize Pool Points',
                          ),
                          validator: _numberField,
                        ),
                        const SizedBox(height: 14),
                        TextFormField(
                          controller: _winnerCountController,
                          keyboardType: TextInputType.number,
                          decoration: const InputDecoration(
                            labelText: 'Winner Count',
                          ),
                          validator: _numberField,
                        ),
                        const SizedBox(height: 12),
                        SwitchListTile(
                          contentPadding: EdgeInsets.zero,
                          value: _joinConfirmRequired,
                          onChanged: (value) {
                            setState(() => _joinConfirmRequired = value);
                          },
                          title: const Text('Join Confirmation Required'),
                        ),
                        const SizedBox(height: 8),
                        Row(
                          children: [
                            const Expanded(
                              child: Text(
                                'Prize Breakup',
                                style: TextStyle(
                                  fontSize: 16,
                                  fontWeight: FontWeight.w800,
                                ),
                              ),
                            ),
                            TextButton.icon(
                              onPressed: _addPrizeRow,
                              icon: const Icon(Icons.add_rounded),
                              label: const Text('Add Row'),
                            ),
                          ],
                        ),
                        const SizedBox(height: 8),
                        ...List.generate(_prizeRows.length, (index) {
                          final row = _prizeRows[index];
                          return Padding(
                            padding: const EdgeInsets.only(bottom: 12),
                            child: _PrizeRowWidget(
                              index: index,
                              row: row,
                              onRemove: () => _removePrizeRow(index),
                            ),
                          );
                        }),
                        const SizedBox(height: 12),
                        SizedBox(
                          width: double.infinity,
                          child: ElevatedButton(
                            onPressed: _isBusy ? null : _submit,
                            child: _isBusy
                                ? const SizedBox(
                              height: 22,
                              width: 22,
                              child: CircularProgressIndicator(
                                strokeWidth: 2.2,
                                color: Colors.white,
                              ),
                            )
                                : const Text('Create Contest'),
                          ),
                        ),
                      ],
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

  String? _requiredField(String? value) {
    if (value == null || value.trim().isEmpty) {
      return 'This field is required';
    }
    return null;
  }

  String? _numberField(String? value) {
    if (value == null || value.trim().isEmpty) {
      return 'This field is required';
    }
    final parsed = int.tryParse(value.trim());
    if (parsed == null || parsed <= 0) {
      return 'Enter a valid number';
    }
    return null;
  }
}

class _FixtureInfoCard extends StatelessWidget {
  const _FixtureInfoCard({required this.fixture});

  final AdminFixture fixture;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(24),
        color: const Color(0xFF0F1528),
        border: Border.all(color: Colors.white.withValues(alpha: 0.06)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            fixture.title,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 18,
              fontWeight: FontWeight.w900,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            'Start: ${fixture.startTime}',
            style: const TextStyle(color: Colors.white70),
          ),
          const SizedBox(height: 4),
          Text(
            'Deadline: ${fixture.deadlineTime}',
            style: const TextStyle(color: Colors.white70),
          ),
        ],
      ),
    );
  }
}

class _PrizeRowWidget extends StatelessWidget {
  const _PrizeRowWidget({
    required this.index,
    required this.row,
    required this.onRemove,
  });

  final int index;
  final _PrizeRowData row;
  final VoidCallback onRemove;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.black.withValues(alpha: 0.05),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.black12),
      ),
      child: Column(
        children: [
          Row(
            children: [
              Text(
                'Prize Row ${index + 1}',
                style: const TextStyle(fontWeight: FontWeight.w700),
              ),
              const Spacer(),
              IconButton(
                onPressed: onRemove,
                icon: const Icon(Icons.delete_outline_rounded),
              ),
            ],
          ),
          const SizedBox(height: 8),
          TextFormField(
            controller: row.rankFromController,
            keyboardType: TextInputType.number,
            decoration: const InputDecoration(labelText: 'Rank From'),
            validator: _numberField,
          ),
          const SizedBox(height: 10),
          TextFormField(
            controller: row.rankToController,
            keyboardType: TextInputType.number,
            decoration: const InputDecoration(labelText: 'Rank To'),
            validator: _numberField,
          ),
          const SizedBox(height: 10),
          TextFormField(
            controller: row.prizePointsController,
            keyboardType: TextInputType.number,
            decoration: const InputDecoration(labelText: 'Prize Points'),
            validator: _numberField,
          ),
        ],
      ),
    );
  }

  static String? _numberField(String? value) {
    if (value == null || value.trim().isEmpty) {
      return 'Required';
    }
    final parsed = int.tryParse(value.trim());
    if (parsed == null || parsed <= 0) {
      return 'Invalid number';
    }
    return null;
  }
}

class _PrizeRowData {
  final rankFromController = TextEditingController();
  final rankToController = TextEditingController();
  final prizePointsController = TextEditingController();

  void dispose() {
    rankFromController.dispose();
    rankToController.dispose();
    prizePointsController.dispose();
  }
}
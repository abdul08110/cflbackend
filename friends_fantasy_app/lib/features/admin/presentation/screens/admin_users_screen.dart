import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../data/admin_repository.dart';
import '../../domain/models/admin_models.dart';

class AdminUsersScreen extends ConsumerStatefulWidget {
  const AdminUsersScreen({super.key});

  @override
  ConsumerState<AdminUsersScreen> createState() => _AdminUsersScreenState();
}

class _AdminUsersScreenState extends ConsumerState<AdminUsersScreen> {
  final _searchController = TextEditingController();
  final _busyActions = <String>{};

  bool _isLoading = true;
  String? _errorMessage;
  List<AdminManagedUser> _users = const [];

  @override
  void initState() {
    super.initState();
    _searchController.addListener(_handleSearchChanged);
    Future.microtask(() => _loadUsers());
  }

  @override
  void dispose() {
    _searchController.removeListener(_handleSearchChanged);
    _searchController.dispose();
    super.dispose();
  }

  void _handleSearchChanged() {
    if (!mounted) return;
    setState(() {});
  }

  Future<void> _loadUsers({bool showLoader = true}) async {
    if (showLoader && mounted) {
      setState(() {
        _isLoading = true;
        _errorMessage = null;
      });
    }

    try {
      final users = await ref
          .read(adminRepositoryProvider)
          .getUsers(query: _searchController.text);

      if (!mounted) return;
      setState(() {
        _users = users;
        _isLoading = false;
        _errorMessage = null;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isLoading = false;
        _errorMessage = e.toString().replaceFirst('Exception: ', '');
      });
    }
  }

  Future<void> _changeWallet(
    AdminManagedUser user, {
    required bool isCredit,
  }) async {
    final payload = await showDialog<AdminWalletAdjustmentPayload>(
      context: context,
      builder: (context) => _WalletAdjustmentDialog(isCredit: isCredit),
    );

    if (payload == null || !mounted) return;

    final actionKey = '${isCredit ? 'credit' : 'debit'}-${user.userId}';
    await _runBusyAction(actionKey, () async {
      final repo = ref.read(adminRepositoryProvider);
      final wallet = isCredit
          ? await repo.creditWallet(userId: user.userId, payload: payload)
          : await repo.debitWallet(userId: user.userId, payload: payload);

      if (!mounted) return;
      _replaceUser(user.applyWalletUpdate(wallet));
      _showSnackBar(
        isCredit
            ? 'Wallet points loaded for ${user.username}'
            : 'Wallet points removed for ${user.username}',
      );
    });
  }

  Future<void> _openUserHistory(AdminManagedUser user) async {
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      showDragHandle: true,
      builder: (context) => FractionallySizedBox(
        heightFactor: 0.88,
        child: _AdminUserHistorySheet(user: user),
      ),
    );
  }

  Future<void> _toggleUserStatus(AdminManagedUser user) async {
    final isBlocked = user.isBlocked;
    final payload = await showDialog<AdminUserStatusActionPayload>(
      context: context,
      builder: (context) =>
          _UserStatusActionDialog(user: user, activating: isBlocked),
    );

    if (payload == null || !mounted) return;

    final actionKey = 'status-${user.userId}';
    await _runBusyAction(actionKey, () async {
      final repo = ref.read(adminRepositoryProvider);
      final updated = isBlocked
          ? await repo.unblockUser(userId: user.userId, payload: payload)
          : await repo.blockUser(userId: user.userId, payload: payload);

      if (!mounted) return;
      _replaceUser(updated);
      _showSnackBar(
        '${user.username} has been ${isBlocked ? 'activated' : 'blocked'}',
      );
    });
  }

  Future<void> _runBusyAction(
    String key,
    Future<void> Function() action,
  ) async {
    setState(() => _busyActions.add(key));
    try {
      await action();
    } catch (e) {
      if (mounted) {
        _showSnackBar(e.toString().replaceFirst('Exception: ', ''));
      }
    } finally {
      if (mounted) {
        setState(() => _busyActions.remove(key));
      }
    }
  }

  void _replaceUser(AdminManagedUser updated) {
    if (!mounted) return;
    setState(() {
      _users = [
        for (final user in _users)
          if (user.userId == updated.userId) updated else user,
      ];
    });
  }

  void _showSnackBar(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF070B17),
      appBar: AppBar(
        backgroundColor: const Color(0xFF0F1528),
        leading: IconButton(
          onPressed: () => context.go('/admin/home'),
          icon: const Icon(Icons.arrow_back_rounded),
          tooltip: 'Back',
        ),
        title: const Text('Admin Wallet Controls'),
      ),
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            colors: [Color(0xFF140A0D), Color(0xFF090C18), Color(0xFF05070F)],
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
          ),
        ),
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 12),
              child: Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(24),
                  gradient: const LinearGradient(
                    colors: [Color(0xFF181124), Color(0xFF0C1425)],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                  border: Border.all(
                    color: Colors.white.withValues(alpha: 0.06),
                  ),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Wallet and access controls',
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 18,
                        fontWeight: FontWeight.w900,
                      ),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      'Only admins can load wallet points, remove wallet points, and block users. Tap any user card to open admin activity history.',
                      style: TextStyle(
                        color: Colors.white.withValues(alpha: 0.68),
                        height: 1.45,
                      ),
                    ),
                    const SizedBox(height: 14),
                    Row(
                      children: [
                        Expanded(
                          child: TextField(
                            controller: _searchController,
                            onSubmitted: (_) => _loadUsers(),
                            style: const TextStyle(color: Colors.white),
                            decoration: InputDecoration(
                              hintText: 'Search by username, mobile, or email',
                              prefixIcon: const Icon(Icons.search_rounded),
                              suffixIcon: _searchController.text.isEmpty
                                  ? null
                                  : IconButton(
                                      onPressed: () {
                                        _searchController.clear();
                                        _loadUsers();
                                      },
                                      icon: const Icon(Icons.close_rounded),
                                    ),
                            ),
                          ),
                        ),
                        const SizedBox(width: 12),
                        ElevatedButton(
                          onPressed: _loadUsers,
                          style: ElevatedButton.styleFrom(
                            minimumSize: const Size(0, 48),
                          ),
                          child: const Text('Search'),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            Expanded(child: _buildBody()),
          ],
        ),
      ),
    );
  }

  Widget _buildBody() {
    if (_isLoading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_errorMessage != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(
                Icons.error_outline_rounded,
                color: Colors.white70,
                size: 38,
              ),
              const SizedBox(height: 12),
              Text(
                _errorMessage!,
                textAlign: TextAlign.center,
                style: const TextStyle(color: Colors.white70),
              ),
              const SizedBox(height: 14),
              ElevatedButton(
                onPressed: _loadUsers,
                style: ElevatedButton.styleFrom(
                  minimumSize: const Size(120, 48),
                ),
                child: const Text('Retry'),
              ),
            ],
          ),
        ),
      );
    }

    if (_users.isEmpty) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: Text(
            'No users found.\nTry username, mobile, or email.',
            textAlign: TextAlign.center,
            style: TextStyle(color: Colors.white70, height: 1.5),
          ),
        ),
      );
    }

    return RefreshIndicator(
      onRefresh: () => _loadUsers(showLoader: false),
      child: ListView.separated(
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
        itemCount: _users.length,
        separatorBuilder: (_, _) => const SizedBox(height: 14),
        itemBuilder: (context, index) {
          final user = _users[index];
          final joinedLabel = user.createdAt == null
              ? 'Joined recently'
              : 'Joined ${DateFormat('dd MMM yyyy').format(user.createdAt!.toLocal())}';

          return Material(
            color: Colors.transparent,
            child: InkWell(
              borderRadius: BorderRadius.circular(24),
              onTap: () => _openUserHistory(user),
              child: Ink(
                padding: const EdgeInsets.all(18),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(24),
                  color: const Color(0xFF0F1528),
                  border: Border.all(
                    color: Colors.white.withValues(alpha: 0.06),
                  ),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                user.fullName,
                                style: const TextStyle(
                                  color: Colors.white,
                                  fontSize: 18,
                                  fontWeight: FontWeight.w900,
                                ),
                              ),
                              const SizedBox(height: 4),
                              Text(
                                '@${user.username}',
                                style: const TextStyle(
                                  color: Color(0xFF96A7CF),
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                            ],
                          ),
                        ),
                        _StatusChip(status: user.status),
                      ],
                    ),
                    const SizedBox(height: 14),
                    Wrap(
                      spacing: 10,
                      runSpacing: 10,
                      children: [
                        _MetaChip(
                          icon: Icons.phone_android_rounded,
                          text: user.mobile,
                        ),
                        _MetaChip(icon: Icons.email_outlined, text: user.email),
                        _MetaChip(
                          icon: Icons.calendar_today_rounded,
                          text: joinedLabel,
                        ),
                      ],
                    ),
                    const SizedBox(height: 14),
                    Row(
                      children: [
                        Icon(
                          Icons.history_rounded,
                          size: 16,
                          color: Colors.white.withValues(alpha: 0.78),
                        ),
                        const SizedBox(width: 8),
                        Text(
                          'Tap to view admin history',
                          style: TextStyle(
                            color: Colors.white.withValues(alpha: 0.74),
                            fontSize: 12,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),
                    Container(
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(20),
                        color: Colors.white.withValues(alpha: 0.04),
                        border: Border.all(
                          color: Colors.white.withValues(alpha: 0.05),
                        ),
                      ),
                      child: Row(
                        children: [
                          _WalletStat(
                            label: 'Wallet',
                            value: '${user.walletBalance} pts',
                            color: const Color(0xFF4DD0E1),
                          ),
                          _WalletStat(
                            label: 'Earned',
                            value: '${user.lifetimeEarnedPoints} pts',
                            color: const Color(0xFF66BB6A),
                          ),
                          _WalletStat(
                            label: 'Spent',
                            value: '${user.lifetimeSpentPoints} pts',
                            color: const Color(0xFFFFB74D),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 16),
                    Row(
                      children: [
                        Expanded(
                          child: _ActionButton(
                            label: 'Load',
                            color: const Color(0xFF2E7D32),
                            busy: _busyActions.contains(
                              'credit-${user.userId}',
                            ),
                            onPressed: () =>
                                _changeWallet(user, isCredit: true),
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: _ActionButton(
                            label: 'Remove',
                            color: const Color(0xFFEF6C00),
                            busy: _busyActions.contains('debit-${user.userId}'),
                            onPressed: () =>
                                _changeWallet(user, isCredit: false),
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: _ActionButton(
                            label: user.isBlocked ? 'Activate' : 'Block',
                            color: user.isBlocked
                                ? const Color(0xFF2E7D32)
                                : const Color(0xFFC62828),
                            busy: _busyActions.contains(
                              'status-${user.userId}',
                            ),
                            onPressed: () => _toggleUserStatus(user),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}

class _WalletAdjustmentDialog extends StatefulWidget {
  const _WalletAdjustmentDialog({required this.isCredit});

  final bool isCredit;

  @override
  State<_WalletAdjustmentDialog> createState() =>
      _WalletAdjustmentDialogState();
}

class _WalletAdjustmentDialogState extends State<_WalletAdjustmentDialog> {
  final _formKey = GlobalKey<FormState>();
  final _pointsController = TextEditingController();
  late final TextEditingController _remarksController;

  @override
  void initState() {
    super.initState();
    _remarksController = TextEditingController(
      text: widget.isCredit
          ? 'Admin loaded wallet points'
          : 'Admin removed wallet points',
    );
  }

  @override
  void dispose() {
    _pointsController.dispose();
    _remarksController.dispose();
    super.dispose();
  }

  void _submit() {
    if (!_formKey.currentState!.validate()) {
      return;
    }

    Navigator.of(context).pop(
      AdminWalletAdjustmentPayload(
        points: int.parse(_pointsController.text.trim()),
        remarks: _remarksController.text.trim(),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      backgroundColor: const Color(0xFF12192E),
      title: Text(
        widget.isCredit ? 'Load Wallet Points' : 'Remove Wallet Points',
        style: const TextStyle(color: Colors.white),
      ),
      content: SingleChildScrollView(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 360),
          child: Form(
            key: _formKey,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextFormField(
                  controller: _pointsController,
                  keyboardType: TextInputType.number,
                  textInputAction: TextInputAction.next,
                  style: const TextStyle(color: Colors.white),
                  decoration: const InputDecoration(
                    labelText: 'Points',
                    hintText: 'Enter point value',
                  ),
                  validator: (value) {
                    final parsed = int.tryParse((value ?? '').trim());
                    if (parsed == null || parsed <= 0) {
                      return 'Enter a valid number';
                    }
                    return null;
                  },
                ),
                const SizedBox(height: 14),
                TextFormField(
                  controller: _remarksController,
                  maxLines: 3,
                  minLines: 2,
                  textInputAction: TextInputAction.done,
                  onFieldSubmitted: (_) => _submit(),
                  style: const TextStyle(color: Colors.white),
                  decoration: const InputDecoration(
                    labelText: 'Remarks',
                    hintText: 'Why are you changing this wallet?',
                  ),
                  validator: (value) {
                    if (value == null || value.trim().isEmpty) {
                      return 'Remarks are required';
                    }
                    return null;
                  },
                ),
              ],
            ),
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Cancel'),
        ),
        ElevatedButton(
          onPressed: _submit,
          style: ElevatedButton.styleFrom(minimumSize: const Size(80, 40)),
          child: Text(widget.isCredit ? 'Load' : 'Remove'),
        ),
      ],
    );
  }
}

class _UserStatusActionDialog extends StatefulWidget {
  const _UserStatusActionDialog({required this.user, required this.activating});

  final AdminManagedUser user;
  final bool activating;

  @override
  State<_UserStatusActionDialog> createState() =>
      _UserStatusActionDialogState();
}

class _UserStatusActionDialogState extends State<_UserStatusActionDialog> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _remarksController;

  @override
  void initState() {
    super.initState();
    _remarksController = TextEditingController(
      text: widget.activating
          ? 'User activated after admin review'
          : 'User blocked after admin review',
    );
  }

  @override
  void dispose() {
    _remarksController.dispose();
    super.dispose();
  }

  void _submit() {
    if (!_formKey.currentState!.validate()) {
      return;
    }

    Navigator.of(context).pop(
      AdminUserStatusActionPayload(remarks: _remarksController.text.trim()),
    );
  }

  @override
  Widget build(BuildContext context) {
    final title = widget.activating ? 'Activate User' : 'Block User';
    final actionLabel = widget.activating ? 'Activate' : 'Block';
    final actionColor = widget.activating
        ? const Color(0xFF2E7D32)
        : const Color(0xFFC62828);
    final message = widget.activating
        ? 'Activate ${widget.user.username}? They will be able to log in and use the app again.'
        : 'Block ${widget.user.username}? They will no longer be able to log in or use the app.';

    return AlertDialog(
      backgroundColor: const Color(0xFF12192E),
      title: Text(title, style: const TextStyle(color: Colors.white)),
      content: SingleChildScrollView(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 360),
          child: Form(
            key: _formKey,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  message,
                  style: const TextStyle(color: Colors.white70, height: 1.5),
                ),
                const SizedBox(height: 14),
                TextFormField(
                  controller: _remarksController,
                  maxLines: 3,
                  minLines: 2,
                  textInputAction: TextInputAction.done,
                  onFieldSubmitted: (_) => _submit(),
                  style: const TextStyle(color: Colors.white),
                  decoration: const InputDecoration(
                    labelText: 'Remarks',
                    hintText: 'Why is this status being changed?',
                  ),
                  validator: (value) {
                    if (value == null || value.trim().isEmpty) {
                      return 'Remarks are required';
                    }
                    return null;
                  },
                ),
              ],
            ),
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Cancel'),
        ),
        ElevatedButton(
          onPressed: _submit,
          style: ElevatedButton.styleFrom(
            backgroundColor: actionColor,
            minimumSize: const Size(96, 40),
          ),
          child: Text(actionLabel),
        ),
      ],
    );
  }
}

class _AdminUserHistorySheet extends ConsumerStatefulWidget {
  const _AdminUserHistorySheet({required this.user});

  final AdminManagedUser user;

  @override
  ConsumerState<_AdminUserHistorySheet> createState() =>
      _AdminUserHistorySheetState();
}

class _AdminUserHistorySheetState
    extends ConsumerState<_AdminUserHistorySheet> {
  late Future<AdminUserActivityHistory> _future;

  @override
  void initState() {
    super.initState();
    _future = _loadHistory();
  }

  Future<AdminUserActivityHistory> _loadHistory() {
    return ref
        .read(adminRepositoryProvider)
        .getUserActivityHistory(widget.user.userId);
  }

  void _reload() {
    setState(() => _future = _loadHistory());
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: Color(0xFF0A1020),
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
      ),
      child: SafeArea(
        top: false,
        child: FutureBuilder<AdminUserActivityHistory>(
          future: _future,
          builder: (context, snapshot) {
            if (snapshot.connectionState == ConnectionState.waiting) {
              return const Center(child: CircularProgressIndicator());
            }

            if (snapshot.hasError) {
              return Padding(
                padding: const EdgeInsets.all(24),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const Icon(
                      Icons.error_outline_rounded,
                      color: Colors.white70,
                      size: 38,
                    ),
                    const SizedBox(height: 12),
                    Text(
                      snapshot.error.toString().replaceFirst('Exception: ', ''),
                      textAlign: TextAlign.center,
                      style: const TextStyle(color: Colors.white70),
                    ),
                    const SizedBox(height: 16),
                    ElevatedButton(
                      onPressed: _reload,
                      child: const Text('Retry'),
                    ),
                  ],
                ),
              );
            }

            final history = snapshot.data;
            if (history == null) {
              return const SizedBox.shrink();
            }

            return Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              widget.user.fullName,
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 22,
                                fontWeight: FontWeight.w900,
                              ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              '@${widget.user.username}',
                              style: const TextStyle(
                                color: Color(0xFF96A7CF),
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                          ],
                        ),
                      ),
                      IconButton(
                        onPressed: () => Navigator.of(context).pop(),
                        icon: const Icon(Icons.close_rounded),
                      ),
                    ],
                  ),
                  const SizedBox(height: 6),
                  Text(
                    'Admin activity history for wallet changes and status actions.',
                    style: TextStyle(
                      color: Colors.white.withValues(alpha: 0.7),
                      height: 1.45,
                    ),
                  ),
                  const SizedBox(height: 16),
                  Row(
                    children: [
                      Expanded(
                        child: _HistoryStatCard(
                          label: 'Points Added',
                          value: '${history.totalPointsAdded} pts',
                          color: const Color(0xFF66BB6A),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: _HistoryStatCard(
                          label: 'Points Deducted',
                          value: '${history.totalPointsDeducted} pts',
                          color: const Color(0xFFFFB74D),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 18),
                  Row(
                    children: [
                      const Text(
                        'History',
                        style: TextStyle(
                          color: Colors.white,
                          fontSize: 18,
                          fontWeight: FontWeight.w900,
                        ),
                      ),
                      const Spacer(),
                      TextButton(
                        onPressed: _reload,
                        child: const Text('Refresh'),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  if (history.history.isEmpty)
                    Expanded(
                      child: Center(
                        child: Text(
                          'No admin activity found for this user yet.',
                          textAlign: TextAlign.center,
                          style: TextStyle(
                            color: Colors.white.withValues(alpha: 0.7),
                          ),
                        ),
                      ),
                    )
                  else
                    Expanded(
                      child: ListView.separated(
                        itemCount: history.history.length,
                        separatorBuilder: (_, _) => const SizedBox(height: 12),
                        itemBuilder: (context, index) {
                          return _AdminHistoryTile(
                            item: history.history[index],
                          );
                        },
                      ),
                    ),
                ],
              ),
            );
          },
        ),
      ),
    );
  }
}

class _HistoryStatCard extends StatelessWidget {
  const _HistoryStatCard({
    required this.label,
    required this.value,
    required this.color,
  });

  final String label;
  final String value;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(18),
        color: Colors.white.withValues(alpha: 0.05),
        border: Border.all(color: Colors.white.withValues(alpha: 0.06)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: const TextStyle(
              color: Colors.white60,
              fontSize: 12,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            value,
            style: TextStyle(
              color: color,
              fontSize: 18,
              fontWeight: FontWeight.w900,
            ),
          ),
        ],
      ),
    );
  }
}

class _AdminHistoryTile extends StatelessWidget {
  const _AdminHistoryTile({required this.item});

  final AdminUserActivityItem item;

  @override
  Widget build(BuildContext context) {
    final tone = _toneFor(item.type);
    final timestamp = item.createdAt == null
        ? null
        : DateFormat('dd MMM yyyy, hh:mm a').format(item.createdAt!);

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(18),
        color: Colors.white.withValues(alpha: 0.05),
        border: Border.all(color: Colors.white.withValues(alpha: 0.06)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(14),
              color: tone.color.withValues(alpha: 0.14),
            ),
            child: Icon(tone.icon, color: tone.color),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        tone.label,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 15,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                    ),
                    if (item.points != null)
                      Text(
                        item.isPointsAdded
                            ? '+${item.points} pts'
                            : item.isPointsDeducted
                            ? '-${item.points} pts'
                            : '${item.points} pts',
                        style: TextStyle(
                          color: tone.color,
                          fontWeight: FontWeight.w900,
                        ),
                      ),
                  ],
                ),
                const SizedBox(height: 6),
                Text(
                  item.remarks.isEmpty ? 'No remarks added.' : item.remarks,
                  style: const TextStyle(
                    color: Color(0xFFA9B4D0),
                    height: 1.45,
                  ),
                ),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    _MiniInfoPill(
                      icon: Icons.manage_accounts_rounded,
                      label: item.adminUsername,
                    ),
                    if (timestamp != null)
                      _MiniInfoPill(
                        icon: Icons.schedule_rounded,
                        label: timestamp,
                      ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _MiniInfoPill extends StatelessWidget {
  const _MiniInfoPill({required this.icon, required this.label});

  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(999),
        color: Colors.white.withValues(alpha: 0.05),
        border: Border.all(color: Colors.white.withValues(alpha: 0.06)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 14, color: const Color(0xFFFFB199)),
          const SizedBox(width: 6),
          Text(
            label,
            style: const TextStyle(
              color: Colors.white70,
              fontSize: 12,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}

class _HistoryTone {
  const _HistoryTone({
    required this.label,
    required this.icon,
    required this.color,
  });

  final String label;
  final IconData icon;
  final Color color;
}

_HistoryTone _toneFor(String type) {
  switch (type) {
    case 'POINTS_ADDED':
      return const _HistoryTone(
        label: 'Points Added',
        icon: Icons.add_card_rounded,
        color: Color(0xFF66BB6A),
      );
    case 'POINTS_DEDUCTED':
      return const _HistoryTone(
        label: 'Points Deducted',
        icon: Icons.remove_circle_outline_rounded,
        color: Color(0xFFFFB74D),
      );
    case 'USER_BLOCKED':
      return const _HistoryTone(
        label: 'User Blocked',
        icon: Icons.block_rounded,
        color: Color(0xFFE57373),
      );
    case 'USER_ACTIVATED':
      return const _HistoryTone(
        label: 'User Activated',
        icon: Icons.verified_user_rounded,
        color: Color(0xFF4DD0E1),
      );
    default:
      return const _HistoryTone(
        label: 'Admin Action',
        icon: Icons.admin_panel_settings_rounded,
        color: Color(0xFF90CAF9),
      );
  }
}

class _ActionButton extends StatelessWidget {
  const _ActionButton({
    required this.label,
    required this.color,
    required this.busy,
    required this.onPressed,
  });

  final String label;
  final Color color;
  final bool busy;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(16),
        gradient: LinearGradient(
          colors: [color, color.withValues(alpha: 0.78)],
        ),
      ),
      child: ElevatedButton(
        onPressed: busy ? null : onPressed,
        style: ElevatedButton.styleFrom(
          backgroundColor: Colors.transparent,
          shadowColor: Colors.transparent,
          disabledBackgroundColor: Colors.transparent,
          minimumSize: const Size(0, 0),
          padding: const EdgeInsets.symmetric(vertical: 13),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
        ),
        child: busy
            ? const SizedBox(
                width: 18,
                height: 18,
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                  color: Colors.white,
                ),
              )
            : Text(label),
      ),
    );
  }
}

class _MetaChip extends StatelessWidget {
  const _MetaChip({required this.icon, required this.text});

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(999),
        color: Colors.white.withValues(alpha: 0.05),
        border: Border.all(color: Colors.white.withValues(alpha: 0.06)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, color: const Color(0xFFFFB199), size: 14),
          const SizedBox(width: 6),
          ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 220),
            child: Text(
              text,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                color: Colors.white70,
                fontSize: 12,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _WalletStat extends StatelessWidget {
  const _WalletStat({
    required this.label,
    required this.value,
    required this.color,
  });

  final String label;
  final String value;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: const TextStyle(
              color: Colors.white60,
              fontSize: 12,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            value,
            style: TextStyle(
              color: color,
              fontSize: 14,
              fontWeight: FontWeight.w900,
            ),
          ),
        ],
      ),
    );
  }
}

class _StatusChip extends StatelessWidget {
  const _StatusChip({required this.status});

  final String status;

  @override
  Widget build(BuildContext context) {
    final normalized = status.toUpperCase();
    final blocked = normalized == 'BLOCKED';

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(999),
        color: (blocked ? const Color(0xFFE53935) : const Color(0xFF2E7D32))
            .withValues(alpha: 0.16),
        border: Border.all(
          color: (blocked ? const Color(0xFFE57373) : const Color(0xFF81C784))
              .withValues(alpha: 0.45),
        ),
      ),
      child: Text(
        normalized,
        style: TextStyle(
          color: blocked ? const Color(0xFFFFCDD2) : const Color(0xFFC8E6C9),
          fontSize: 11,
          fontWeight: FontWeight.w800,
        ),
      ),
    );
  }
}

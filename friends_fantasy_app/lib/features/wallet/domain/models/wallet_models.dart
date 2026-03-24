import '../../../../core/network/api_helpers.dart';

class WalletSummary {
  final String walletId;
  final double balance;
  final String currency;
  final int totalContestsJoined;
  final double totalWinnings;
  final List<WalletTransactionItem> history;

  const WalletSummary({
    required this.walletId,
    required this.balance,
    required this.currency,
    required this.totalContestsJoined,
    required this.totalWinnings,
    required this.history,
  });

  factory WalletSummary.fromJson(Map<String, dynamic> json) {
    final data = json['wallet'] is Map
        ? Map<String, dynamic>.from(json['wallet'] as Map)
        : (json['data'] is Map
        ? Map<String, dynamic>.from(json['data'] as Map)
        : json);

    return WalletSummary(
      walletId: asString(
        data['walletId'] ?? data['userId'] ?? data['id'] ?? data['_id'],
      ),
      balance: asDouble(
        data['balancePoints'] ??
            data['balance'] ??
            data['points'] ??
            data['pointBalance'] ??
            data['amount'] ??
            data['available_balance'] ??
            data['wallet_balance'] ??
            data['current_balance'],
      ),
      currency: asString(
        data['currency'] ?? data['unit'] ?? data['points_unit'],
        fallback: 'PTS',
      ),
      totalContestsJoined: asInt(
        data['totalContestsJoined'] ??
            data['contests_count'] ??
            data['joined_count'] ??
            data['total_joined'],
      ),
      totalWinnings: asDouble(
        data['totalWinnings'] ??
            data['lifetimeEarnedPoints'] ??
            data['winnings'] ??
            data['total_winnings'] ??
            data['won_amount'] ??
            data['total_won'],
      ),
      history: ((data['history'] ?? data['transactions']) is List
              ? List<dynamic>.from(
                  (data['history'] ?? data['transactions']) as List,
                )
              : const <dynamic>[])
          .map(
            (e) => WalletTransactionItem.fromJson(
              Map<String, dynamic>.from(e as Map),
            ),
          )
          .toList(),
    );
  }
}

class WalletTransactionItem {
  final String id;
  final String txnType;
  final String direction;
  final int points;
  final int signedPoints;
  final int balanceAfter;
  final String? refType;
  final String? refId;
  final String remarks;
  final DateTime? createdAt;

  const WalletTransactionItem({
    required this.id,
    required this.txnType,
    required this.direction,
    required this.points,
    required this.signedPoints,
    required this.balanceAfter,
    required this.refType,
    required this.refId,
    required this.remarks,
    required this.createdAt,
  });

  factory WalletTransactionItem.fromJson(Map<String, dynamic> json) {
    final direction = asString(
      json['direction'],
      fallback: _isCreditType(asString(json['txnType'])) ? 'CREDIT' : 'DEBIT',
    );
    final points = asInt(json['points']);
    final signedPoints = asInt(
      json['signedPoints'],
      fallback: direction == 'CREDIT' ? points : -points,
    );

    return WalletTransactionItem(
      id: asString(json['transactionId'] ?? json['id']),
      txnType: asString(json['txnType']),
      direction: direction,
      points: points,
      signedPoints: signedPoints,
      balanceAfter: asInt(json['balanceAfter']),
      refType: asStringOrNull(json['refType']),
      refId: asStringOrNull(json['refId']),
      remarks: asString(
        json['remarks'],
        fallback: _fallbackRemarks(asString(json['txnType'])),
      ),
      createdAt: _parseDate(json['createdAt']),
    );
  }

  static bool _isCreditType(String txnType) {
    return txnType == 'ADMIN_CREDIT' ||
        txnType == 'CONTEST_WIN_CREDIT' ||
        txnType == 'REFUND' ||
        txnType == 'BONUS';
  }

  static String _fallbackRemarks(String txnType) {
    switch (txnType) {
      case 'ADMIN_CREDIT':
        return 'Admin credited points';
      case 'ADMIN_DEBIT':
        return 'Admin debited points';
      case 'CONTEST_JOIN_DEBIT':
        return 'Contest join deduction';
      case 'CONTEST_WIN_CREDIT':
        return 'Contest winnings credited';
      case 'REFUND':
        return 'Refund credited';
      case 'BONUS':
        return 'Bonus credited';
      case 'ADJUSTMENT':
        return 'Wallet adjusted';
      default:
        return 'Wallet transaction';
    }
  }
}

DateTime? _parseDate(dynamic value) {
  final raw = asStringOrNull(value);
  if (raw == null) return null;
  return DateTime.tryParse(raw)?.toLocal();
}

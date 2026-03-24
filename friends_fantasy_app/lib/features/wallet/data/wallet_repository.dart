import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_client.dart';
import '../../../core/network/api_endpoints.dart';
import '../../../core/network/api_helpers.dart';
import '../domain/models/wallet_models.dart';

final walletRepositoryProvider = Provider<WalletRepository>((ref) => WalletRepository(ref.watch(dioProvider)));
final myWalletProvider = FutureProvider.autoDispose<WalletSummary>((ref) async => ref.watch(walletRepositoryProvider).getMyWallet());

class WalletRepository {
  WalletRepository(this._dio);
  final Dio _dio;

  Future<WalletSummary> getMyWallet() async {
    try {
      final response = await _dio.get(ApiEndpoints.myWallet);
      return WalletSummary.fromJson(unwrapMap(response.data));
    } catch (e) {
      throw Exception(extractErrorMessage(e));
    }
  }
}

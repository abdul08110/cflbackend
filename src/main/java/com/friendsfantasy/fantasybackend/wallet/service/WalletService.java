package com.friendsfantasy.fantasybackend.wallet.service;

import com.friendsfantasy.fantasybackend.stats.entity.UserStats;
import com.friendsfantasy.fantasybackend.stats.service.UserStatsService;
import com.friendsfantasy.fantasybackend.wallet.dto.WalletSummaryResponse;
import com.friendsfantasy.fantasybackend.wallet.dto.WalletTransactionResponse;
import com.friendsfantasy.fantasybackend.wallet.entity.WalletAccount;
import com.friendsfantasy.fantasybackend.wallet.entity.WalletTransaction;
import com.friendsfantasy.fantasybackend.wallet.repository.WalletAccountRepository;
import com.friendsfantasy.fantasybackend.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletService {

        private final WalletAccountRepository walletAccountRepository;
        private final WalletTransactionRepository walletTransactionRepository;
        private final UserStatsService userStatsService;

        @Transactional
        public void createWalletIfNotExists(Long userId) {
                if (!walletAccountRepository.existsById(userId)) {
                        walletAccountRepository.save(
                                        WalletAccount.builder()
                                                        .userId(userId)
                                                        .balancePoints(0)
                                                        .lifetimeEarnedPoints(0)
                                                        .lifetimeSpentPoints(0)
                                                        .build());
                }
        }

        public WalletAccount getWallet(Long userId) {
                return walletAccountRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("Wallet not found"));
        }

        public WalletSummaryResponse getWalletSummary(Long userId) {
                createWalletIfNotExists(userId);

                WalletAccount wallet = getWallet(userId);
                UserStats stats = userStatsService.getStats(userId);
                List<WalletTransactionResponse> history = walletTransactionRepository
                                .findByUserIdOrderByCreatedAtDesc(userId)
                                .stream()
                                .map(this::mapWalletTransaction)
                                .toList();

                return WalletSummaryResponse.builder()
                                .walletId(wallet.getUserId())
                                .balance(wallet.getBalancePoints())
                                .currency("PTS")
                                .totalContestsJoined(stats.getTotalContestsJoined())
                                .totalWinnings(stats.getTotalPointsWon())
                                .history(history)
                                .build();
        }

        @Transactional
        public WalletAccount creditPoints(Long userId, Integer points, String remarks, Long adminId) {
                createWalletIfNotExists(userId);

                WalletAccount wallet = walletAccountRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("Wallet not found"));

                int newBalance = wallet.getBalancePoints() + points;
                wallet.setBalancePoints(newBalance);
                wallet.setLifetimeEarnedPoints(wallet.getLifetimeEarnedPoints() + points);
                walletAccountRepository.save(wallet);

                WalletTransaction txn = WalletTransaction.builder()
                                .userId(userId)
                                .txnType(WalletTransaction.TxnType.ADMIN_CREDIT)
                                .points(points)
                                .balanceAfter(newBalance)
                                .remarks(remarks)
                                .createdByAdminId(adminId)
                                .build();

                walletTransactionRepository.save(txn);

                return wallet;
        }

        @Transactional
        public WalletAccount debitPoints(Long userId, Integer points, String remarks, Long adminId) {
                createWalletIfNotExists(userId);

                WalletAccount wallet = walletAccountRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("Wallet not found"));

                if (wallet.getBalancePoints() < points) {
                        throw new RuntimeException("Cannot remove more points than the user currently has");
                }

                int newBalance = wallet.getBalancePoints() - points;
                wallet.setBalancePoints(newBalance);
                wallet.setLifetimeSpentPoints(wallet.getLifetimeSpentPoints() + points);
                walletAccountRepository.save(wallet);

                WalletTransaction txn = WalletTransaction.builder()
                                .userId(userId)
                                .txnType(WalletTransaction.TxnType.ADMIN_DEBIT)
                                .points(points)
                                .balanceAfter(newBalance)
                                .remarks(remarks)
                                .createdByAdminId(adminId)
                                .build();

                walletTransactionRepository.save(txn);

                return wallet;
        }

        @Transactional
        public WalletTransaction debitForContestJoin(Long userId, Integer points, Long contestId, String remarks) {
                createWalletIfNotExists(userId);

                WalletAccount wallet = walletAccountRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("Wallet not found"));

                if (wallet.getBalancePoints() < points) {
                        throw new RuntimeException("Insufficient points. Please contact admin to add points.");
                }

                int newBalance = wallet.getBalancePoints() - points;
                wallet.setBalancePoints(newBalance);
                wallet.setLifetimeSpentPoints(wallet.getLifetimeSpentPoints() + points);
                walletAccountRepository.save(wallet);

                WalletTransaction txn = WalletTransaction.builder()
                                .userId(userId)
                                .txnType(WalletTransaction.TxnType.CONTEST_JOIN_DEBIT)
                                .points(points)
                                .balanceAfter(newBalance)
                                .refType("CONTEST")
                                .refId(contestId)
                                .remarks(remarks)
                                .build();

                return walletTransactionRepository.save(txn);
        }

        @Transactional
        public WalletTransaction creditContestWin(Long userId, Integer points, Long contestId, String remarks) {
                createWalletIfNotExists(userId);

                WalletAccount wallet = walletAccountRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("Wallet not found"));

                int newBalance = wallet.getBalancePoints() + points;
                wallet.setBalancePoints(newBalance);
                wallet.setLifetimeEarnedPoints(wallet.getLifetimeEarnedPoints() + points);
                walletAccountRepository.save(wallet);

                WalletTransaction txn = WalletTransaction.builder()
                                .userId(userId)
                                .txnType(WalletTransaction.TxnType.CONTEST_WIN_CREDIT)
                                .points(points)
                                .balanceAfter(newBalance)
                                .refType("CONTEST")
                                .refId(contestId)
                                .remarks(remarks)
                                .build();

                return walletTransactionRepository.save(txn);
        }

        @Transactional
        public WalletTransaction creditRefund(Long userId, Integer points, Long contestId, String remarks) {
                createWalletIfNotExists(userId);

                WalletAccount wallet = walletAccountRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("Wallet not found"));

                int newBalance = wallet.getBalancePoints() + points;
                wallet.setBalancePoints(newBalance);
                wallet.setLifetimeEarnedPoints(wallet.getLifetimeEarnedPoints() + points);
                walletAccountRepository.save(wallet);

                WalletTransaction txn = WalletTransaction.builder()
                                .userId(userId)
                                .txnType(WalletTransaction.TxnType.REFUND)
                                .points(points)
                                .balanceAfter(newBalance)
                                .refType("CONTEST")
                                .refId(contestId)
                                .remarks(remarks)
                                .build();

                return walletTransactionRepository.save(txn);
        }

        private WalletTransactionResponse mapWalletTransaction(WalletTransaction txn) {
                boolean isCredit = isCreditTxn(txn.getTxnType());
                int points = txn.getPoints() == null ? 0 : txn.getPoints();

                return WalletTransactionResponse.builder()
                                .transactionId(txn.getId())
                                .txnType(txn.getTxnType().name())
                                .direction(isCredit ? "CREDIT" : "DEBIT")
                                .points(points)
                                .signedPoints(isCredit ? points : -points)
                                .balanceAfter(txn.getBalanceAfter())
                                .refType(txn.getRefType())
                                .refId(txn.getRefId())
                                .remarks(txn.getRemarks())
                                .createdAt(txn.getCreatedAt())
                                .build();
        }

        private boolean isCreditTxn(WalletTransaction.TxnType txnType) {
                return txnType == WalletTransaction.TxnType.ADMIN_CREDIT
                                || txnType == WalletTransaction.TxnType.CONTEST_WIN_CREDIT
                                || txnType == WalletTransaction.TxnType.REFUND
                                || txnType == WalletTransaction.TxnType.BONUS;
        }
}

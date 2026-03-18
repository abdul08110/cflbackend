package com.friendsfantasy.fantasybackend.wallet.service;

import com.friendsfantasy.fantasybackend.wallet.entity.WalletAccount;
import com.friendsfantasy.fantasybackend.wallet.entity.WalletTransaction;
import com.friendsfantasy.fantasybackend.wallet.repository.WalletAccountRepository;
import com.friendsfantasy.fantasybackend.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletService {

        private final WalletAccountRepository walletAccountRepository;
        private final WalletTransactionRepository walletTransactionRepository;

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

        @Transactional
        public WalletAccount creditPoints(Long userId, Integer points, String remarks, Long adminId) {
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
        public WalletTransaction debitForContestJoin(Long userId, Integer points, Long contestId, String remarks) {
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
}
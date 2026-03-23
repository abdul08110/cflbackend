package com.friendsfantasy.fantasybackend.wallet.repository;

import com.friendsfantasy.fantasybackend.wallet.entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    List<WalletTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<WalletTransaction> findByUserIdAndTxnTypeInOrderByCreatedAtDesc(
            Long userId,
            Collection<WalletTransaction.TxnType> txnTypes
    );
}

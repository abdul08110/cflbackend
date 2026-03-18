package com.friendsfantasy.fantasybackend.wallet.repository;

import com.friendsfantasy.fantasybackend.wallet.entity.WalletAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletAccountRepository extends JpaRepository<WalletAccount, Long> {
}
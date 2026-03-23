package com.friendsfantasy.fantasybackend.wallet.repository;

import com.friendsfantasy.fantasybackend.wallet.entity.WalletAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface WalletAccountRepository extends JpaRepository<WalletAccount, Long> {
    List<WalletAccount> findAllByUserIdIn(Collection<Long> userIds);
}

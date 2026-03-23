package com.friendsfantasy.fantasybackend.admin.user.service;

import com.friendsfantasy.fantasybackend.admin.auth.entity.AdminUser;
import com.friendsfantasy.fantasybackend.admin.auth.repository.AdminUserRepository;
import com.friendsfantasy.fantasybackend.admin.user.dto.AdminUserActivityHistoryResponse;
import com.friendsfantasy.fantasybackend.admin.user.dto.AdminUserActivityItemResponse;
import com.friendsfantasy.fantasybackend.admin.user.dto.AdminUserSummaryResponse;
import com.friendsfantasy.fantasybackend.admin.user.entity.AdminUserStatusActivity;
import com.friendsfantasy.fantasybackend.admin.user.repository.AdminUserStatusActivityRepository;
import com.friendsfantasy.fantasybackend.auth.entity.User;
import com.friendsfantasy.fantasybackend.auth.entity.UserProfile;
import com.friendsfantasy.fantasybackend.auth.entity.UserSession;
import com.friendsfantasy.fantasybackend.auth.repository.UserProfileRepository;
import com.friendsfantasy.fantasybackend.auth.repository.UserRepository;
import com.friendsfantasy.fantasybackend.auth.repository.UserSessionRepository;
import com.friendsfantasy.fantasybackend.wallet.entity.WalletAccount;
import com.friendsfantasy.fantasybackend.wallet.entity.WalletTransaction;
import com.friendsfantasy.fantasybackend.wallet.repository.WalletAccountRepository;
import com.friendsfantasy.fantasybackend.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserManagementService {

    private static final int DEFAULT_PAGE_SIZE = 50;

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserSessionRepository userSessionRepository;
    private final WalletAccountRepository walletAccountRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final AdminUserRepository adminUserRepository;
    private final AdminUserStatusActivityRepository adminUserStatusActivityRepository;

    public List<AdminUserSummaryResponse> getUsers(String query) {
        String normalizedQuery = query == null ? "" : query.trim();

        List<User> users = userRepository.searchForAdmin(
                normalizedQuery,
                PageRequest.of(0, DEFAULT_PAGE_SIZE)
        );

        return toSummaries(users);
    }

    @Transactional
    public AdminUserSummaryResponse blockUser(Long userId, Long adminUserId, String remarks) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getStatus() == User.Status.DELETED) {
            throw new RuntimeException("Deleted user cannot be blocked");
        }

        user.setStatus(User.Status.BLOCKED);
        userRepository.save(user);

        List<UserSession> activeSessions = userSessionRepository.findAllByUserIdAndRevokedAtIsNull(userId);
        if (!activeSessions.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            activeSessions.forEach(session -> session.setRevokedAt(now));
            userSessionRepository.saveAll(activeSessions);
        }

        recordStatusActivity(
                userId,
                adminUserId,
                AdminUserStatusActivity.ActivityType.USER_BLOCKED,
                remarks
        );

        return toSummary(user, getProfileMap(List.of(userId)), getWalletMap(List.of(userId)));
    }

    @Transactional
    public AdminUserSummaryResponse unblockUser(Long userId, Long adminUserId, String remarks) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getStatus() == User.Status.DELETED) {
            throw new RuntimeException("Deleted user cannot be activated");
        }

        user.setStatus(User.Status.ACTIVE);
        userRepository.save(user);

        recordStatusActivity(
                userId,
                adminUserId,
                AdminUserStatusActivity.ActivityType.USER_ACTIVATED,
                remarks
        );

        return toSummary(user, getProfileMap(List.of(userId)), getWalletMap(List.of(userId)));
    }

    public AdminUserActivityHistoryResponse getUserActivityHistory(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<WalletTransaction> walletActivities =
                walletTransactionRepository.findByUserIdAndTxnTypeInOrderByCreatedAtDesc(
                        userId,
                        List.of(
                                WalletTransaction.TxnType.ADMIN_CREDIT,
                                WalletTransaction.TxnType.ADMIN_DEBIT
                        )
                );
        List<AdminUserStatusActivity> statusActivities =
                adminUserStatusActivityRepository.findByUserIdOrderByCreatedAtDesc(userId);

        Map<Long, String> adminNames = getAdminNames(walletActivities, statusActivities);
        List<AdminUserActivityItemResponse> history = new ArrayList<>();

        for (WalletTransaction walletActivity : walletActivities) {
            history.add(AdminUserActivityItemResponse.builder()
                    .activityId("wallet-" + walletActivity.getId())
                    .activityType(walletActivity.getTxnType() == WalletTransaction.TxnType.ADMIN_CREDIT
                            ? "POINTS_ADDED"
                            : "POINTS_DEDUCTED")
                    .points(walletActivity.getPoints())
                    .remarks(walletActivity.getRemarks())
                    .adminId(walletActivity.getCreatedByAdminId())
                    .adminUsername(resolveAdminUsername(walletActivity.getCreatedByAdminId(), adminNames))
                    .createdAt(walletActivity.getCreatedAt())
                    .build());
        }

        for (AdminUserStatusActivity statusActivity : statusActivities) {
            history.add(AdminUserActivityItemResponse.builder()
                    .activityId("status-" + statusActivity.getId())
                    .activityType(statusActivity.getActivityType().name())
                    .points(null)
                    .remarks(statusActivity.getRemarks())
                    .adminId(statusActivity.getAdminUserId())
                    .adminUsername(resolveAdminUsername(statusActivity.getAdminUserId(), adminNames))
                    .createdAt(statusActivity.getCreatedAt())
                    .build());
        }

        history.sort(Comparator.comparing(
                AdminUserActivityItemResponse::getCreatedAt,
                Comparator.nullsLast(LocalDateTime::compareTo)
        ).reversed());

        int totalPointsAdded = walletActivities.stream()
                .filter(txn -> txn.getTxnType() == WalletTransaction.TxnType.ADMIN_CREDIT)
                .map(WalletTransaction::getPoints)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        int totalPointsDeducted = walletActivities.stream()
                .filter(txn -> txn.getTxnType() == WalletTransaction.TxnType.ADMIN_DEBIT)
                .map(WalletTransaction::getPoints)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        return AdminUserActivityHistoryResponse.builder()
                .userId(userId)
                .totalPointsAdded(totalPointsAdded)
                .totalPointsDeducted(totalPointsDeducted)
                .history(history)
                .build();
    }

    public AdminUserSummaryResponse getUserSummary(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return toSummary(user, getProfileMap(List.of(userId)), getWalletMap(List.of(userId)));
    }

    private List<AdminUserSummaryResponse> toSummaries(List<User> users) {
        if (users.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> userIds = users.stream()
                .map(User::getId)
                .toList();

        Map<Long, UserProfile> profilesByUserId = getProfileMap(userIds);
        Map<Long, WalletAccount> walletsByUserId = getWalletMap(userIds);

        return users.stream()
                .map(user -> toSummary(user, profilesByUserId, walletsByUserId))
                .toList();
    }

    private Map<Long, UserProfile> getProfileMap(Collection<Long> userIds) {
        return userProfileRepository.findAllByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(UserProfile::getUserId, Function.identity()));
    }

    private Map<Long, WalletAccount> getWalletMap(Collection<Long> userIds) {
        return walletAccountRepository.findAllByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(WalletAccount::getUserId, Function.identity()));
    }

    private void recordStatusActivity(
            Long userId,
            Long adminUserId,
            AdminUserStatusActivity.ActivityType activityType,
            String remarks
    ) {
        adminUserStatusActivityRepository.save(AdminUserStatusActivity.builder()
                .userId(userId)
                .adminUserId(adminUserId)
                .activityType(activityType)
                .remarks(remarks == null ? "" : remarks.trim())
                .build());
    }

    private Map<Long, String> getAdminNames(
            List<WalletTransaction> walletActivities,
            List<AdminUserStatusActivity> statusActivities
    ) {
        Set<Long> adminIds = new HashSet<>();
        for (WalletTransaction walletActivity : walletActivities) {
            if (walletActivity.getCreatedByAdminId() != null) {
                adminIds.add(walletActivity.getCreatedByAdminId());
            }
        }
        for (AdminUserStatusActivity statusActivity : statusActivities) {
            if (statusActivity.getAdminUserId() != null) {
                adminIds.add(statusActivity.getAdminUserId());
            }
        }

        if (adminIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, String> adminNames = new HashMap<>();
        for (AdminUser adminUser : adminUserRepository.findAllById(adminIds)) {
            adminNames.put(adminUser.getId(), adminUser.getUsername());
        }
        return adminNames;
    }

    private String resolveAdminUsername(Long adminId, Map<Long, String> adminNames) {
        if (adminId == null) {
            return "Admin";
        }

        String username = adminNames.get(adminId);
        if (username == null || username.isBlank()) {
            return "Admin #" + adminId;
        }

        return username;
    }

    private AdminUserSummaryResponse toSummary(
            User user,
            Map<Long, UserProfile> profilesByUserId,
            Map<Long, WalletAccount> walletsByUserId
    ) {
        UserProfile profile = profilesByUserId.get(user.getId());
        WalletAccount wallet = walletsByUserId.get(user.getId());

        return AdminUserSummaryResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(profile != null ? profile.getFullName() : user.getUsername())
                .mobile(user.getMobile())
                .email(user.getEmail())
                .status(user.getStatus())
                .walletBalance(wallet != null ? wallet.getBalancePoints() : 0)
                .lifetimeEarnedPoints(wallet != null ? wallet.getLifetimeEarnedPoints() : 0)
                .lifetimeSpentPoints(wallet != null ? wallet.getLifetimeSpentPoints() : 0)
                .createdAt(user.getCreatedAt())
                .build();
    }
}

package com.friendsfantasy.fantasybackend.stats.service;

import com.friendsfantasy.fantasybackend.stats.entity.UserStats;
import com.friendsfantasy.fantasybackend.stats.repository.UserStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class UserStatsService {

    private final UserStatsRepository userStatsRepository;

    @Transactional
    public void createIfMissing(Long userId) {
        if (!userStatsRepository.existsById(userId)) {
            userStatsRepository.save(UserStats.builder().userId(userId).build());
        }
    }

    @Transactional
    public void recordContestJoin(Long userId, Integer spentPoints) {
        createIfMissing(userId);

        UserStats stats = userStatsRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User stats not found"));

        stats.setTotalContestsJoined(stats.getTotalContestsJoined() + 1);
        stats.setTotalPointsSpent(stats.getTotalPointsSpent() + spentPoints);
        recalculateWinRate(stats);

        userStatsRepository.save(stats);
    }

    @Transactional
    public void recordContestWin(Long userId, Integer prizePoints, Integer rankNo) {
        createIfMissing(userId);

        UserStats stats = userStatsRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User stats not found"));

        stats.setTotalContestsWon(stats.getTotalContestsWon() + 1);
        stats.setTotalPointsWon(stats.getTotalPointsWon() + prizePoints);

        if (stats.getBestRank() == null || rankNo < stats.getBestRank()) {
            stats.setBestRank(rankNo);
        }

        recalculateWinRate(stats);
        userStatsRepository.save(stats);
    }

    public UserStats getStats(Long userId) {
        createIfMissing(userId);
        return userStatsRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User stats not found"));
    }

    @Transactional
    public void recordCommunityCreated(Long userId) {
        createIfMissing(userId);

        UserStats stats = userStatsRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User stats not found"));

        stats.setTotalRoomsCreated(stats.getTotalRoomsCreated() + 1);
        userStatsRepository.save(stats);
    }

    private void recalculateWinRate(UserStats stats) {
        if (stats.getTotalContestsJoined() == 0) {
            stats.setWinRate(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            return;
        }

        BigDecimal rate = BigDecimal.valueOf(stats.getTotalContestsWon())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(stats.getTotalContestsJoined()), 2, RoundingMode.HALF_UP);

        stats.setWinRate(rate);
    }
}

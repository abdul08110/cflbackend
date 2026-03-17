package com.friendsfantasy.fantasybackend.service;

import com.friendsfantasy.fantasybackend.dto.CreateTeamRequest;
import com.friendsfantasy.fantasybackend.dto.JoinContestRequest;
import com.friendsfantasy.fantasybackend.dto.LeaderboardResponse;
import com.friendsfantasy.fantasybackend.entity.*;
import com.friendsfantasy.fantasybackend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ContestService {

    private final TeamRepository teamRepository;
    private final TeamPlayerRepository teamPlayerRepository;
    private final ContestEntryRepository contestEntryRepository;
    private final ContestRepository contestRepository;
    private final PlayerPointRepository playerPointRepository;
    private final UserRepository userRepository;

    public ContestService(TeamRepository teamRepository,
                          TeamPlayerRepository teamPlayerRepository,
                          ContestEntryRepository contestEntryRepository,
                          ContestRepository contestRepository,
                          PlayerPointRepository playerPointRepository,
                          UserRepository userRepository) {
        this.teamRepository = teamRepository;
        this.teamPlayerRepository = teamPlayerRepository;
        this.contestEntryRepository = contestEntryRepository;
        this.contestRepository = contestRepository;
        this.playerPointRepository = playerPointRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Team createTeam(CreateTeamRequest request) {
        if (request.getUserId() == null || request.getMatchId() == null) {
            throw new RuntimeException("User and match are required");
        }

        if (request.getPlayerIds() == null || request.getPlayerIds().size() != 11) {
            throw new RuntimeException("Exactly 11 players must be selected");
        }

        if (request.getCaptainId() == null || request.getViceCaptainId() == null) {
            throw new RuntimeException("Captain and vice captain are required");
        }

        if (request.getCaptainId().equals(request.getViceCaptainId())) {
            throw new RuntimeException("Captain and vice captain cannot be same");
        }

        Team team = new Team();
        team.setUserId(request.getUserId());
        team.setMatchId(request.getMatchId());
        team.setCaptainId(request.getCaptainId());
        team.setViceCaptainId(request.getViceCaptainId());

        Team savedTeam = teamRepository.save(team);

        for (Integer playerId : request.getPlayerIds()) {
            TeamPlayer teamPlayer = new TeamPlayer();
            teamPlayer.setTeamId(savedTeam.getId());
            teamPlayer.setPlayerId(playerId);
            teamPlayerRepository.save(teamPlayer);
        }

        return savedTeam;
    }

    public ContestEntry joinContest(JoinContestRequest request) {
        Contest contest = contestRepository.findById(request.getContestId())
                .orElseThrow(() -> new RuntimeException("Contest not found"));

        long joinedCount = contestEntryRepository.countByContestId(request.getContestId());
        if (contest.getMaxUsers() != null && joinedCount >= contest.getMaxUsers()) {
            throw new RuntimeException("Contest is full");
        }

        boolean alreadyJoined = contestEntryRepository.existsByContestIdAndUserIdAndTeamId(
                request.getContestId(),
                request.getUserId(),
                request.getTeamId()
        );

        if (alreadyJoined) {
            throw new RuntimeException("Already joined this contest with same team");
        }

        ContestEntry entry = new ContestEntry();
        entry.setContestId(request.getContestId());
        entry.setUserId(request.getUserId());
        entry.setTeamId(request.getTeamId());

        return contestEntryRepository.save(entry);
    }

    public List<LeaderboardResponse> getLeaderboard(Integer contestId) {
        List<ContestEntry> entries = contestEntryRepository.findByContestId(contestId);
        List<LeaderboardResponse> leaderboard = new ArrayList<>();

        for (ContestEntry entry : entries) {
            Team team = teamRepository.findById(entry.getTeamId())
                    .orElseThrow(() -> new RuntimeException("Team not found"));

            List<TeamPlayer> teamPlayers = teamPlayerRepository.findByTeamId(team.getId());
            int totalPoints = 0;

            for (TeamPlayer teamPlayer : teamPlayers) {
                int basePoints = playerPointRepository
                        .findByMatchIdAndPlayerId(team.getMatchId(), teamPlayer.getPlayerId())
                        .map(PlayerPoint::getPoints)
                        .orElse(0);

                if (teamPlayer.getPlayerId().equals(team.getCaptainId())) {
                    totalPoints += basePoints * 2;
                } else if (teamPlayer.getPlayerId().equals(team.getViceCaptainId())) {
                    totalPoints += (int) Math.round(basePoints * 1.5);
                } else {
                    totalPoints += basePoints;
                }
            }

            User user = userRepository.findById(entry.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            leaderboard.add(new LeaderboardResponse(
                    user.getId(),
                    user.getName(),
                    team.getId(),
                    totalPoints
            ));
        }

        leaderboard.sort((a, b) -> Integer.compare(b.getPoints(), a.getPoints()));
        return leaderboard;
    }
}
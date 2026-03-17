package com.friendsfantasy.fantasybackend.service;

import com.friendsfantasy.fantasybackend.entity.Contest;
import com.friendsfantasy.fantasybackend.entity.Match;
import com.friendsfantasy.fantasybackend.entity.Player;
import com.friendsfantasy.fantasybackend.repository.ContestRepository;
import com.friendsfantasy.fantasybackend.repository.MatchRepository;
import com.friendsfantasy.fantasybackend.repository.PlayerRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MatchService {

    private final MatchRepository matchRepository;
    private final PlayerRepository playerRepository;
    private final ContestRepository contestRepository;

    public MatchService(MatchRepository matchRepository,
                        PlayerRepository playerRepository,
                        ContestRepository contestRepository) {
        this.matchRepository = matchRepository;
        this.playerRepository = playerRepository;
        this.contestRepository = contestRepository;
    }

    public List<Match> getAllMatches() {
        return matchRepository.findAll();
    }

    public List<Player> getPlayersForMatch(Integer matchId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));

        return playerRepository.findByTeamIn(List.of(match.getTeam1(), match.getTeam2()));
    }

    public List<Contest> getContestsByMatch(Integer matchId) {
        return contestRepository.findByMatchId(matchId);
    }
}
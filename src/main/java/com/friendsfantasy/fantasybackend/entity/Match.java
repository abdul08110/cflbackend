package com.friendsfantasy.fantasybackend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "matches")
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "team1", nullable = false)
    private String team1;

    @Column(name = "team2", nullable = false)
    private String team2;

    @Column(name = "match_date")
    private LocalDateTime matchDate;

    @Column(name = "status")
    private String status;

    public Match() {
    }

    public Match(Integer id, String team1, String team2, LocalDateTime matchDate, String status) {
        this.id = id;
        this.team1 = team1;
        this.team2 = team2;
        this.matchDate = matchDate;
        this.status = status;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTeam1() {
        return team1;
    }

    public void setTeam1(String team1) {
        this.team1 = team1;
    }

    public String getTeam2() {
        return team2;
    }

    public void setTeam2(String team2) {
        this.team2 = team2;
    }

    public LocalDateTime getMatchDate() {
        return matchDate;
    }

    public void setMatchDate(LocalDateTime matchDate) {
        this.matchDate = matchDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
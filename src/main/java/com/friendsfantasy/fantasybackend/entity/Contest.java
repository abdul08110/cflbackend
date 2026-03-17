package com.friendsfantasy.fantasybackend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "contests")
public class Contest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "match_id", nullable = false)
    private Integer matchId;

    @Column(nullable = false)
    private String name;

    @Column(name = "max_users")
    private Integer maxUsers;

    public Contest() {
    }

    public Contest(Integer id, Integer matchId, String name, Integer maxUsers) {
        this.id = id;
        this.matchId = matchId;
        this.name = name;
        this.maxUsers = maxUsers;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getMatchId() {
        return matchId;
    }

    public void setMatchId(Integer matchId) {
        this.matchId = matchId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getMaxUsers() {
        return maxUsers;
    }

    public void setMaxUsers(Integer maxUsers) {
        this.maxUsers = maxUsers;
    }
}
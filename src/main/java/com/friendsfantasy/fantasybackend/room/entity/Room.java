package com.friendsfantasy.fantasybackend.room.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

    public enum Status {
        ACTIVE, CLOSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sport_id", nullable = false)
    private Long sportId;

    @Column(name = "fixture_id")
    private Long fixtureId;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "room_name", nullable = false, length = 100)
    private String roomName;

    @Column(name = "room_code", nullable = false, length = 6, unique = true)
    private String roomCode;

    @Column(name = "is_private", nullable = false)
    private Boolean isPrivate = true;

    @Column(name = "max_members", nullable = false)
    private Integer maxMembers = 20;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}

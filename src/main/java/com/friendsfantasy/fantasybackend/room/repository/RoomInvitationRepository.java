package com.friendsfantasy.fantasybackend.room.repository;

import com.friendsfantasy.fantasybackend.room.entity.RoomInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoomInvitationRepository extends JpaRepository<RoomInvitation, Long> {
    Optional<RoomInvitation> findByIdAndStatus(Long id, RoomInvitation.Status status);
}
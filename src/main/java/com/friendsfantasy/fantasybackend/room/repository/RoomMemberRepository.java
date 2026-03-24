package com.friendsfantasy.fantasybackend.room.repository;

import com.friendsfantasy.fantasybackend.room.entity.RoomMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {

    List<RoomMember> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, RoomMember.Status status);

    Optional<RoomMember> findByRoomIdAndUserId(Long roomId, Long userId);

    long countByRoomIdAndStatus(Long roomId, RoomMember.Status status);

    List<RoomMember> findByRoomIdAndStatusOrderByJoinedAtAsc(Long roomId, RoomMember.Status status);

    boolean existsByRoomIdAndUserIdAndStatus(Long roomId, Long userId, RoomMember.Status status);

    List<RoomMember> findByRoomIdOrderByCreatedAtAsc(Long roomId);
}

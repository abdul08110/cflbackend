package com.friendsfantasy.fantasybackend.room.repository;

import com.friendsfantasy.fantasybackend.room.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {
    Optional<Room> findByRoomCode(String roomCode);
    boolean existsByRoomCode(String roomCode);
    boolean existsByRoomNameIgnoreCaseAndStatus(String roomName, Room.Status status);
    boolean existsByRoomNameIgnoreCaseAndStatusAndIdNot(String roomName, Room.Status status, Long id);
    List<Room> findByStatusOrderByCreatedAtDescIdDesc(Room.Status status);
}

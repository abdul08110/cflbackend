package com.friendsfantasy.fantasybackend.room.service;

import com.friendsfantasy.fantasybackend.auth.entity.User;
import com.friendsfantasy.fantasybackend.auth.repository.UserRepository;
import com.friendsfantasy.fantasybackend.notification.entity.Notification;
import com.friendsfantasy.fantasybackend.notification.repository.NotificationRepository;
import com.friendsfantasy.fantasybackend.room.dto.*;
import com.friendsfantasy.fantasybackend.room.entity.Room;
import com.friendsfantasy.fantasybackend.room.entity.RoomInvitation;
import com.friendsfantasy.fantasybackend.room.entity.RoomMember;
import com.friendsfantasy.fantasybackend.room.repository.RoomInvitationRepository;
import com.friendsfantasy.fantasybackend.room.repository.RoomMemberRepository;
import com.friendsfantasy.fantasybackend.room.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomInvitationRepository roomInvitationRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    @Transactional
    public RoomSummaryResponse createRoom(Long userId, CreateRoomRequest request) {
        Room room = Room.builder()
                .sportId(request.getSportId())
                .createdByUserId(userId)
                .roomName(request.getRoomName().trim())
                .roomCode(generateUniqueRoomCode())
                .isPrivate(Boolean.TRUE.equals(request.getIsPrivate()))
                .maxMembers(request.getMaxMembers())
                .status(Room.Status.ACTIVE)
                .build();

        room = roomRepository.save(room);

        RoomMember owner = RoomMember.builder()
                .roomId(room.getId())
                .userId(userId)
                .role(RoomMember.Role.OWNER)
                .status(RoomMember.Status.JOINED)
                .joinedAt(LocalDateTime.now())
                .build();

        roomMemberRepository.save(owner);

        return toRoomSummary(room, owner.getRole().name());
    }

    public List<RoomSummaryResponse> getMyRooms(Long userId) {
        List<RoomMember> memberships = roomMemberRepository
                .findByUserIdAndStatusOrderByCreatedAtDesc(userId, RoomMember.Status.JOINED);

        List<RoomSummaryResponse> response = new ArrayList<>();

        for (RoomMember membership : memberships) {
            Room room = roomRepository.findById(membership.getRoomId()).orElse(null);
            if (room != null) {
                response.add(toRoomSummary(room, membership.getRole().name()));
            }
        }

        return response;
    }

    public RoomDetailResponse getRoomDetails(Long roomId, Long userId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        RoomMember myMembership = roomMemberRepository.findByRoomIdAndUserId(roomId, userId)
                .filter(m -> m.getStatus() == RoomMember.Status.JOINED)
                .orElseThrow(() -> new RuntimeException("You are not a member of this room"));

        List<RoomMemberResponse> members = roomMemberRepository
                .findByRoomIdAndStatusOrderByJoinedAtAsc(roomId, RoomMember.Status.JOINED)
                .stream()
                .map(this::toMemberResponse)
                .collect(Collectors.toList());

        return RoomDetailResponse.builder()
                .room(toRoomSummary(room, myMembership.getRole().name()))
                .members(members)
                .build();
    }

    @Transactional
    public RoomSummaryResponse joinByCode(Long userId, JoinRoomByCodeRequest request) {
        String roomCode = request.getRoomCode().trim().toUpperCase();

        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new RuntimeException("Invalid room code"));

        if (room.getStatus() != Room.Status.ACTIVE) {
            throw new RuntimeException("Room is not active");
        }

        RoomMember existing = roomMemberRepository.findByRoomIdAndUserId(room.getId(), userId).orElse(null);

        if (existing != null && existing.getStatus() == RoomMember.Status.JOINED) {
            throw new RuntimeException("You are already in this room");
        }

        long memberCount = roomMemberRepository.countByRoomIdAndStatus(room.getId(), RoomMember.Status.JOINED);
        if (memberCount >= room.getMaxMembers()) {
            throw new RuntimeException("Room is full");
        }

        if (existing != null) {
            existing.setStatus(RoomMember.Status.JOINED);
            existing.setRole(existing.getRole() == null ? RoomMember.Role.MEMBER : existing.getRole());
            existing.setJoinedAt(LocalDateTime.now());
            roomMemberRepository.save(existing);
            return toRoomSummary(room, existing.getRole().name());
        }

        RoomMember member = RoomMember.builder()
                .roomId(room.getId())
                .userId(userId)
                .role(RoomMember.Role.MEMBER)
                .status(RoomMember.Status.JOINED)
                .joinedAt(LocalDateTime.now())
                .build();

        roomMemberRepository.save(member);

        return toRoomSummary(room, member.getRole().name());
    }

    @Transactional
    public Map<String, Object> inviteToRoom(Long inviterUserId, Long roomId, InviteToRoomRequest request) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        roomMemberRepository.findByRoomIdAndUserId(roomId, inviterUserId)
                .filter(m -> m.getStatus() == RoomMember.Status.JOINED)
                .orElseThrow(() -> new RuntimeException("Only room members can invite users"));

        if (room.getStatus() != Room.Status.ACTIVE) {
            throw new RuntimeException("Room is not active");
        }

        String username = request.getUsername() != null ? request.getUsername().trim() : null;
        String mobile = request.getMobile() != null ? request.getMobile().trim() : null;

        if ((username == null || username.isBlank()) && (mobile == null || mobile.isBlank())) {
            throw new RuntimeException("Username or mobile is required");
        }

        User targetUser = null;

        if (username != null && !username.isBlank()) {
            targetUser = userRepository.findByUsername(username).orElse(null);
        }

        if (targetUser == null && mobile != null && !mobile.isBlank()) {
            targetUser = userRepository.findByMobile(mobile).orElse(null);
        }

        if (targetUser != null && Objects.equals(targetUser.getId(), inviterUserId)) {
            throw new RuntimeException("You cannot invite yourself");
        }

        if (targetUser != null) {
            RoomMember existing = roomMemberRepository.findByRoomIdAndUserId(roomId, targetUser.getId()).orElse(null);
            if (existing != null && existing.getStatus() == RoomMember.Status.JOINED) {
                throw new RuntimeException("User is already in this room");
            }
        }

        RoomInvitation invitation = RoomInvitation.builder()
                .roomId(roomId)
                .invitedByUserId(inviterUserId)
                .invitedUserId(targetUser != null ? targetUser.getId() : null)
                .invitedMobile(targetUser != null ? targetUser.getMobile() : mobile)
                .invitedUsername(targetUser != null ? targetUser.getUsername() : username)
                .inviteMessage(request.getInviteMessage())
                .status(RoomInvitation.Status.PENDING)
                .build();

        invitation = roomInvitationRepository.save(invitation);

        if (targetUser != null) {
            String payload = "{\"roomId\":" + roomId + ",\"invitationId\":" + invitation.getId() + "}";

            Notification notification = Notification.builder()
                    .userId(targetUser.getId())
                    .type("ROOM_INVITE")
                    .title("Room Invitation")
                    .body("You have been invited to join room " + room.getRoomName())
                    .payloadJson(payload)
                    .isRead(false)
                    .build();

            notificationRepository.save(notification);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("invitationId", invitation.getId());
        result.put("roomId", roomId);
        result.put("invitedUserId", targetUser != null ? targetUser.getId() : null);
        result.put("invitedUsername", invitation.getInvitedUsername());
        result.put("invitedMobile", invitation.getInvitedMobile());
        result.put("status", invitation.getStatus().name());

        return result;
    }

    @Transactional
    public RoomSummaryResponse acceptInvitation(Long currentUserId, Long invitationId) {
        RoomInvitation invitation = roomInvitationRepository.findByIdAndStatus(invitationId, RoomInvitation.Status.PENDING)
                .orElseThrow(() -> new RuntimeException("Invitation not found"));

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        boolean allowed = false;

        if (invitation.getInvitedUserId() != null && Objects.equals(invitation.getInvitedUserId(), currentUserId)) {
            allowed = true;
        }

        if (!allowed && invitation.getInvitedUserId() == null && invitation.getInvitedMobile() != null) {
            allowed = invitation.getInvitedMobile().equals(currentUser.getMobile());
        }

        if (!allowed) {
            throw new RuntimeException("This invitation does not belong to you");
        }

        Room room = roomRepository.findById(invitation.getRoomId())
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (room.getStatus() != Room.Status.ACTIVE) {
            throw new RuntimeException("Room is not active");
        }

        long memberCount = roomMemberRepository.countByRoomIdAndStatus(room.getId(), RoomMember.Status.JOINED);
        if (memberCount >= room.getMaxMembers()) {
            throw new RuntimeException("Room is full");
        }

        RoomMember existing = roomMemberRepository.findByRoomIdAndUserId(room.getId(), currentUserId).orElse(null);

        if (existing == null) {
            existing = RoomMember.builder()
                    .roomId(room.getId())
                    .userId(currentUserId)
                    .role(RoomMember.Role.MEMBER)
                    .status(RoomMember.Status.JOINED)
                    .joinedAt(LocalDateTime.now())
                    .build();
        } else {
            existing.setStatus(RoomMember.Status.JOINED);
            existing.setJoinedAt(LocalDateTime.now());
            if (existing.getRole() == null) {
                existing.setRole(RoomMember.Role.MEMBER);
            }
        }

        roomMemberRepository.save(existing);

        invitation.setStatus(RoomInvitation.Status.ACCEPTED);
        invitation.setInvitedUserId(currentUserId);
        invitation.setRespondedAt(LocalDateTime.now());
        roomInvitationRepository.save(invitation);

        Notification notification = Notification.builder()
                .userId(invitation.getInvitedByUserId())
                .type("ROOM_INVITE_ACCEPTED")
                .title("Invitation Accepted")
                .body(currentUser.getUsername() + " joined your room " + room.getRoomName())
                .payloadJson("{\"roomId\":" + room.getId() + "}")
                .isRead(false)
                .build();

        notificationRepository.save(notification);

        return toRoomSummary(room, existing.getRole().name());
    }

    @Transactional
    public Map<String, Object> declineInvitation(Long currentUserId, Long invitationId) {
        RoomInvitation invitation = roomInvitationRepository.findByIdAndStatus(invitationId, RoomInvitation.Status.PENDING)
                .orElseThrow(() -> new RuntimeException("Invitation not found"));

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        boolean allowed = false;

        if (invitation.getInvitedUserId() != null && Objects.equals(invitation.getInvitedUserId(), currentUserId)) {
            allowed = true;
        }

        if (!allowed && invitation.getInvitedUserId() == null && invitation.getInvitedMobile() != null) {
            allowed = invitation.getInvitedMobile().equals(currentUser.getMobile());
        }

        if (!allowed) {
            throw new RuntimeException("This invitation does not belong to you");
        }

        invitation.setStatus(RoomInvitation.Status.DECLINED);
        invitation.setInvitedUserId(currentUserId);
        invitation.setRespondedAt(LocalDateTime.now());
        roomInvitationRepository.save(invitation);

        Notification notification = Notification.builder()
                .userId(invitation.getInvitedByUserId())
                .type("ROOM_INVITE_DECLINED")
                .title("Invitation Declined")
                .body(currentUser.getUsername() + " declined your room invite")
                .payloadJson("{\"roomId\":" + invitation.getRoomId() + "}")
                .isRead(false)
                .build();

        notificationRepository.save(notification);

        Map<String, Object> result = new HashMap<>();
        result.put("invitationId", invitation.getId());
        result.put("status", invitation.getStatus().name());
        return result;
    }

    public List<RoomMemberResponse> getRoomMembers(Long roomId, Long currentUserId) {
        roomMemberRepository.findByRoomIdAndUserId(roomId, currentUserId)
                .filter(m -> m.getStatus() == RoomMember.Status.JOINED)
                .orElseThrow(() -> new RuntimeException("You are not a member of this room"));

        return roomMemberRepository.findByRoomIdAndStatusOrderByJoinedAtAsc(roomId, RoomMember.Status.JOINED)
                .stream()
                .map(this::toMemberResponse)
                .collect(Collectors.toList());
    }

    private RoomSummaryResponse toRoomSummary(Room room, String myRole) {
        long memberCount = roomMemberRepository.countByRoomIdAndStatus(room.getId(), RoomMember.Status.JOINED);

        return RoomSummaryResponse.builder()
                .roomId(room.getId())
                .sportId(room.getSportId())
                .roomName(room.getRoomName())
                .roomCode(room.getRoomCode())
                .isPrivate(room.getIsPrivate())
                .maxMembers(room.getMaxMembers())
                .memberCount(memberCount)
                .myRole(myRole)
                .status(room.getStatus().name())
                .build();
    }

    private RoomMemberResponse toMemberResponse(RoomMember member) {
        User user = userRepository.findById(member.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        return RoomMemberResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .mobile(user.getMobile())
                .role(member.getRole().name())
                .status(member.getStatus().name())
                .joinedAt(member.getJoinedAt())
                .build();
    }

    private String generateUniqueRoomCode() {
        Random random = new Random();

        for (int attempt = 0; attempt < 50; attempt++) {
            StringBuilder code = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                code.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            }
            String finalCode = code.toString();
            if (!roomRepository.existsByRoomCode(finalCode)) {
                return finalCode;
            }
        }

        throw new RuntimeException("Unable to generate unique room code");
    }
}
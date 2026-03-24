package com.friendsfantasy.fantasybackend.room.service;

import com.friendsfantasy.fantasybackend.auth.entity.User;
import com.friendsfantasy.fantasybackend.auth.repository.UserRepository;
import com.friendsfantasy.fantasybackend.common.ApiException;
import com.friendsfantasy.fantasybackend.contest.dto.ContestEntryResponse;
import com.friendsfantasy.fantasybackend.contest.entity.Contest;
import com.friendsfantasy.fantasybackend.contest.repository.ContestEntryRepository;
import com.friendsfantasy.fantasybackend.contest.repository.ContestRepository;
import com.friendsfantasy.fantasybackend.contest.service.ContestEntryService;
import com.friendsfantasy.fantasybackend.fixture.entity.Fixture;
import com.friendsfantasy.fantasybackend.fixture.entity.FixtureParticipant;
import com.friendsfantasy.fantasybackend.fixture.repository.FixtureParticipantRepository;
import com.friendsfantasy.fantasybackend.fixture.repository.FixtureRepository;
import com.friendsfantasy.fantasybackend.fixture.service.FixtureSnapshotMapper;
import com.friendsfantasy.fantasybackend.notification.service.NotificationService;
import com.friendsfantasy.fantasybackend.room.dto.AvailableContestFixtureParticipantResponse;
import com.friendsfantasy.fantasybackend.room.dto.CreateCommunityContestRequest;
import com.friendsfantasy.fantasybackend.room.dto.CreateRoomRequest;
import com.friendsfantasy.fantasybackend.room.dto.InviteToCommunityContestRequest;
import com.friendsfantasy.fantasybackend.room.dto.InviteToRoomRequest;
import com.friendsfantasy.fantasybackend.room.dto.JoinRoomByCodeRequest;
import com.friendsfantasy.fantasybackend.room.dto.RoomAvailableContestResponse;
import com.friendsfantasy.fantasybackend.room.dto.RoomDetailResponse;
import com.friendsfantasy.fantasybackend.room.dto.RoomInvitationResponse;
import com.friendsfantasy.fantasybackend.room.dto.RoomMemberResponse;
import com.friendsfantasy.fantasybackend.room.dto.RoomSummaryResponse;
import com.friendsfantasy.fantasybackend.room.dto.SelectCommunityTeamRequest;
import com.friendsfantasy.fantasybackend.room.dto.UpdateRoomRequest;
import com.friendsfantasy.fantasybackend.room.entity.Room;
import com.friendsfantasy.fantasybackend.room.entity.RoomInvitation;
import com.friendsfantasy.fantasybackend.room.entity.RoomMember;
import com.friendsfantasy.fantasybackend.room.repository.RoomInvitationRepository;
import com.friendsfantasy.fantasybackend.room.repository.RoomMemberRepository;
import com.friendsfantasy.fantasybackend.room.repository.RoomRepository;
import com.friendsfantasy.fantasybackend.stats.service.UserStatsService;
import com.friendsfantasy.fantasybackend.team.dto.CreateTeamRequest;
import com.friendsfantasy.fantasybackend.team.dto.TeamResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoomService {

    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final Set<Integer> ALLOWED_JOIN_POINTS = Set.of(500, 1000, 2000, 5000, 10000, 20000, 50000);

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomInvitationRepository roomInvitationRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final ContestRepository contestRepository;
    private final ContestEntryRepository contestEntryRepository;
    private final FixtureRepository fixtureRepository;
    private final FixtureParticipantRepository fixtureParticipantRepository;
    private final ContestEntryService contestEntryService;
    private final FixtureSnapshotMapper fixtureSnapshotMapper;
    private final UserStatsService userStatsService;

    @Value("${app.cricket-sport-id:1}")
    private Long cricketSportId;

    @Transactional
    public RoomSummaryResponse createRoom(Long userId, CreateRoomRequest request) {
        User currentUser = getUser(userId);
        String communityName = normalizeCommunityName(request.getCommunityName());

        if (roomRepository.existsByRoomNameIgnoreCaseAndStatus(communityName, Room.Status.ACTIVE)) {
            throw ApiException.conflict("Community name already exists");
        }

        Room room = Room.builder()
                .sportId(cricketSportId)
                .fixtureId(null)
                .createdByUserId(userId)
                .roomName(communityName)
                .roomCode(generateUniqueRoomCode())
                .isPrivate(true)
                .maxMembers(request.getMaxSpots())
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

        userStatsService.recordCommunityCreated(userId);
        return toRoomSummary(room, currentUser);
    }

    @Transactional
    public RoomSummaryResponse updateRoom(Long userId, Long roomId, UpdateRoomRequest request) {
        User currentUser = getUser(userId);
        Room room = requireActiveRoom(roomId);
        requireOwnerMember(roomId, userId, "Only the community owner can edit this community");

        String communityName = normalizeCommunityName(request.getCommunityName());
        if (roomRepository.existsByRoomNameIgnoreCaseAndStatusAndIdNot(communityName, Room.Status.ACTIVE, roomId)) {
            throw ApiException.conflict("Community name already exists");
        }

        long joinedMembers = roomMemberRepository.countByRoomIdAndStatus(roomId, RoomMember.Status.JOINED);
        int highestContestFill = contestRepository.findByRoomIdOrderByCreatedAtDescIdDesc(roomId).stream()
                .filter(contest -> contest.getStatus() == Contest.Status.OPEN || contest.getStatus() == Contest.Status.FULL)
                .map(Contest::getSpotsFilled)
                .max(Integer::compareTo)
                .orElse(0);

        int minimumAllowedSpots = (int) Math.max(joinedMembers, highestContestFill);
        if (request.getMaxSpots() < minimumAllowedSpots) {
            throw ApiException.conflict("Max spots cannot be lower than current joined members or active contest spots");
        }

        room.setRoomName(communityName);
        room.setMaxMembers(request.getMaxSpots());
        room = roomRepository.save(room);
        syncOpenCommunityContestCap(room);

        return toRoomSummary(room, currentUser);
    }

    public List<RoomSummaryResponse> getAllRooms(Long userId) {
        User currentUser = getUser(userId);
        return roomRepository.findByStatusOrderByCreatedAtDescIdDesc(Room.Status.ACTIVE)
                .stream()
                .map(room -> toRoomSummary(room, currentUser))
                .toList();
    }

    public List<RoomSummaryResponse> getMyRooms(Long userId) {
        User currentUser = getUser(userId);
        List<RoomMember> memberships = roomMemberRepository
                .findByUserIdAndStatusOrderByCreatedAtDesc(userId, RoomMember.Status.JOINED);

        Map<Long, RoomSummaryResponse> responseByRoomId = new LinkedHashMap<>();
        for (RoomMember membership : memberships) {
            Room room = roomRepository.findById(membership.getRoomId()).orElse(null);
            if (room == null) {
                continue;
            }

            responseByRoomId.putIfAbsent(room.getId(), toRoomSummary(room, currentUser));
        }

        return new ArrayList<>(responseByRoomId.values());
    }

    public RoomDetailResponse getRoomDetails(Long roomId, Long userId) {
        User currentUser = getUser(userId);
        Room room = requireActiveRoom(roomId);
        requireJoinedMember(roomId, userId, "You are not a member of this community");

        try {
            contestEntryService.syncCommunityContestState(roomId);
        } catch (Exception ex) {
            log.warn("Community contest state sync failed for room {}. Returning saved room detail.", roomId, ex);
        }

        List<RoomMemberResponse> members = roomMemberRepository
                .findByRoomIdAndStatusOrderByJoinedAtAsc(roomId, RoomMember.Status.JOINED)
                .stream()
                .map(this::toMemberResponse)
                .toList();

        List<RoomAvailableContestResponse> contests = contestRepository.findByRoomIdOrderByCreatedAtDescIdDesc(roomId)
                .stream()
                .map(contest -> toRoomContestResponse(contest, room, currentUser))
                .toList();

        return RoomDetailResponse.builder()
                .community(toRoomSummary(room, currentUser))
                .members(members)
                .contests(contests)
                .build();
    }

    @Transactional
    public Map<String, Object> deleteRoom(Long userId, Long roomId) {
        Room room = requireActiveRoom(roomId);
        requireOwnerMember(roomId, userId, "Only the community owner can delete this community");

        contestEntryService.cancelCommunityContestsForRoomClosure(roomId, room.getRoomName());

        List<RoomInvitation> pendingInvitations = roomInvitationRepository
                .findByRoomIdAndStatusOrderByCreatedAtDesc(roomId, RoomInvitation.Status.PENDING);
        for (RoomInvitation invitation : pendingInvitations) {
            invitation.setStatus(RoomInvitation.Status.EXPIRED);
            invitation.setRespondedAt(LocalDateTime.now());
        }
        roomInvitationRepository.saveAll(pendingInvitations);

        List<RoomMember> members = roomMemberRepository.findByRoomIdOrderByCreatedAtAsc(roomId);
        for (RoomMember member : members) {
            if (member.getStatus() == RoomMember.Status.JOINED || member.getStatus() == RoomMember.Status.INVITED) {
                member.setStatus(RoomMember.Status.REMOVED);
            }
        }
        roomMemberRepository.saveAll(members);

        room.setStatus(Room.Status.CLOSED);
        roomRepository.save(room);

        return Map.of(
                "communityId", room.getId(),
                "status", room.getStatus().name()
        );
    }

    @Transactional
    public RoomSummaryResponse joinByCode(Long userId, JoinRoomByCodeRequest request) {
        User currentUser = getUser(userId);
        String code = request.getCommunityCode() == null
                ? ""
                : request.getCommunityCode().trim().toUpperCase();

        Room room = roomRepository.findByRoomCode(code)
                .orElseThrow(() -> new RuntimeException("Community not found for this code"));

        if (room.getStatus() != Room.Status.ACTIVE) {
            throw new RuntimeException("Community is not active");
        }

        RoomMember membership = roomMemberRepository.findByRoomIdAndUserId(room.getId(), userId).orElse(null);
        if (membership != null && membership.getStatus() == RoomMember.Status.JOINED) {
            return toRoomSummary(room, currentUser);
        }

        long memberCount = roomMemberRepository.countByRoomIdAndStatus(room.getId(), RoomMember.Status.JOINED);
        if (memberCount >= room.getMaxMembers()) {
            throw new RuntimeException("Community is full");
        }

        if (membership == null) {
            membership = RoomMember.builder()
                    .roomId(room.getId())
                    .userId(userId)
                    .role(RoomMember.Role.MEMBER)
                    .status(RoomMember.Status.JOINED)
                    .joinedAt(LocalDateTime.now())
                    .build();
        } else {
            if (membership.getRole() != RoomMember.Role.OWNER) {
                membership.setRole(RoomMember.Role.MEMBER);
            }
            membership.setStatus(RoomMember.Status.JOINED);
            membership.setJoinedAt(LocalDateTime.now());
        }
        roomMemberRepository.save(membership);

        List<RoomInvitation> pendingInvites = roomInvitationRepository
                .findByRoomIdAndStatusOrderByCreatedAtDesc(room.getId(), RoomInvitation.Status.PENDING);
        for (RoomInvitation invitation : pendingInvites) {
            boolean matchesUser = Objects.equals(invitation.getInvitedUserId(), userId);
            boolean matchesMobile = currentUser.getMobile() != null
                    && !currentUser.getMobile().isBlank()
                    && Objects.equals(invitation.getInvitedMobile(), currentUser.getMobile().trim());

            if (matchesUser || matchesMobile) {
                invitation.setStatus(RoomInvitation.Status.ACCEPTED);
                invitation.setInvitedUserId(userId);
                invitation.setRespondedAt(LocalDateTime.now());
                roomInvitationRepository.save(invitation);
            }
        }

        if (!Objects.equals(room.getCreatedByUserId(), userId)) {
            notificationService.createNotification(
                    room.getCreatedByUserId(),
                    "COMMUNITY_JOINED",
                    "Community Joined",
                    currentUser.getUsername() + " joined your community " + room.getRoomName() + " using the code",
                    "{\"communityId\":" + room.getId() + "}"
            );
        }

        return toRoomSummary(room, currentUser);
    }

    @Transactional
    public Map<String, Object> inviteToRoom(Long inviterUserId, Long roomId, InviteToRoomRequest request) {
        Room room = requireActiveRoom(roomId);
        requireOwnerMember(roomId, inviterUserId, "Only the community owner can invite users");

        String username = request.getUsername() != null ? request.getUsername().trim() : null;
        String mobile = request.getMobile() != null ? request.getMobile().trim() : null;

        if ((username == null || username.isBlank()) && (mobile == null || mobile.isBlank())) {
            throw new RuntimeException("Username or mobile is required");
        }

        User targetUser = resolveInviteTargetUser(username, mobile);
        if (Objects.equals(targetUser.getId(), inviterUserId)) {
            throw new RuntimeException("You cannot invite yourself");
        }

        RoomMember existingMembership = roomMemberRepository.findByRoomIdAndUserId(roomId, targetUser.getId()).orElse(null);
        if (existingMembership != null && existingMembership.getStatus() == RoomMember.Status.JOINED) {
            throw new RuntimeException("User is already in this community");
        }

        if (roomInvitationRepository.existsByRoomIdAndInvitedUserIdAndStatus(roomId, targetUser.getId(), RoomInvitation.Status.PENDING)
                || (targetUser.getMobile() != null
                && roomInvitationRepository.existsByRoomIdAndInvitedMobileAndStatus(
                        roomId,
                        targetUser.getMobile().trim(),
                        RoomInvitation.Status.PENDING
                ))) {
            throw new RuntimeException("A pending invitation already exists for this user");
        }

        long memberCount = roomMemberRepository.countByRoomIdAndStatus(roomId, RoomMember.Status.JOINED);
        if (memberCount >= room.getMaxMembers()) {
            throw new RuntimeException("Community is full");
        }

        if (existingMembership == null) {
            existingMembership = RoomMember.builder()
                    .roomId(roomId)
                    .userId(targetUser.getId())
                    .role(RoomMember.Role.MEMBER)
                    .status(RoomMember.Status.INVITED)
                    .build();
        } else {
            existingMembership.setRole(RoomMember.Role.MEMBER);
            existingMembership.setStatus(RoomMember.Status.INVITED);
            existingMembership.setJoinedAt(null);
        }
        roomMemberRepository.save(existingMembership);

        RoomInvitation invitation = RoomInvitation.builder()
                .roomId(roomId)
                .invitedByUserId(inviterUserId)
                .invitedUserId(targetUser.getId())
                .invitedMobile(targetUser.getMobile())
                .invitedUsername(targetUser.getUsername())
                .inviteMessage(request.getInviteMessage())
                .status(RoomInvitation.Status.PENDING)
                .build();
        invitation = roomInvitationRepository.save(invitation);

        notificationService.createNotification(
                targetUser.getId(),
                "COMMUNITY_INVITE",
                "Community Invitation",
                "You have been invited to join community " + room.getRoomName(),
                "{\"communityId\":" + roomId + ",\"invitationId\":" + invitation.getId() + "}"
        );

        Map<String, Object> result = new HashMap<>();
        result.put("invitationId", invitation.getId());
        result.put("communityId", roomId);
        result.put("invitedUserId", targetUser.getId());
        result.put("invitedUsername", invitation.getInvitedUsername());
        result.put("invitedMobile", invitation.getInvitedMobile());
        result.put("status", invitation.getStatus().name());
        return result;
    }

    @Transactional
    public RoomAvailableContestResponse createCommunityContest(Long userId, Long roomId, CreateCommunityContestRequest request) {
        validateJoinPoints(request.getJoiningPoints());

        Room room = requireActiveRoom(roomId);
        requireJoinedMember(roomId, userId, "Only community members can create community contests");

        Fixture fixture = fixtureRepository.findById(request.getFixtureId())
                .orElseThrow(() -> new RuntimeException("Fixture not found"));
        if (!fixture.getDeadlineTime().isAfter(LocalDateTime.now())) {
            throw new RuntimeException("Contest creation is closed for this fixture");
        }

        if (!Objects.equals(room.getSportId(), fixture.getSportId())) {
            throw new RuntimeException("Fixture sport does not match community sport");
        }

        int winnerCount = request.getWinnerCount() == null ? 1 : request.getWinnerCount();
        int maxSpots = request.getMaxSpots() == null ? room.getMaxMembers() : request.getMaxSpots();
        validateCommunityContestStructure(winnerCount, maxSpots, room.getMaxMembers());

        User creator = getUser(userId);

        Contest contest = Contest.builder()
                .fixtureId(fixture.getId())
                .roomId(room.getId())
                .scoringTemplateId(1L)
                .contestName(buildCommunityContestName(fixture, request.getJoiningPoints(), creator.getUsername()))
                .contestType(Contest.ContestType.COMMUNITY)
                .entryFeePoints(request.getJoiningPoints())
                .prizePoolPoints(0)
                .winnerCount(winnerCount)
                .maxSpots(maxSpots)
                .spotsFilled(0)
                .joinConfirmRequired(false)
                .firstPrizePoints(0)
                .status(Contest.Status.OPEN)
                .createdByUserId(userId)
                .build();

        contest = contestRepository.save(contest);
        contestEntryService.reserveCommunityContestSpot(userId, contest.getId());
        contest = contestRepository.findById(contest.getId())
                .orElseThrow(() -> new RuntimeException("Community contest not found"));
        return toRoomContestResponse(contest, room, creator);
    }

    @Transactional
    public Map<String, Object> inviteToCommunityContest(
            Long inviterUserId,
            Long roomId,
            Long contestId,
            InviteToCommunityContestRequest request
    ) {
        Room room = requireActiveRoom(roomId);
        RoomMember inviterMembership = requireJoinedMember(roomId, inviterUserId, "You are not a member of this community");
        Contest contest = contestRepository.findByIdAndRoomId(contestId, roomId)
                .orElseThrow(() -> new RuntimeException("Community contest not found"));

        boolean isOwner = inviterMembership.getRole() == RoomMember.Role.OWNER;
        boolean isContestCreator = Objects.equals(contest.getCreatedByUserId(), inviterUserId);
        if (!isOwner && !isContestCreator) {
            throw new RuntimeException("Only the contest creator or community owner can invite to this contest");
        }

        Fixture fixture = fixtureRepository.findById(contest.getFixtureId())
                .orElseThrow(() -> new RuntimeException("Fixture not found"));
        if (!fixture.getDeadlineTime().isAfter(LocalDateTime.now())) {
            throw new RuntimeException("Contest invites are closed because the fixture has started");
        }
        if (contest.getStatus() == Contest.Status.CANCELLED || contest.getStatus() == Contest.Status.COMPLETED) {
            throw new RuntimeException("This contest is no longer active");
        }

        String username = request.getUsername() != null ? request.getUsername().trim() : null;
        String mobile = request.getMobile() != null ? request.getMobile().trim() : null;
        if ((username == null || username.isBlank()) && (mobile == null || mobile.isBlank())) {
            throw new RuntimeException("Username or mobile is required");
        }

        User targetUser = resolveInviteTargetUser(username, mobile);
        if (Objects.equals(targetUser.getId(), inviterUserId)) {
            throw new RuntimeException("You cannot invite yourself");
        }

        roomMemberRepository.findByRoomIdAndUserId(roomId, targetUser.getId())
                .filter(member -> member.getStatus() == RoomMember.Status.JOINED)
                .orElseThrow(() -> new RuntimeException("Only users inside this community can be invited to the contest"));

        if (contestEntryRepository.existsByContestIdAndUserId(contestId, targetUser.getId())) {
            throw new RuntimeException("User has already joined this contest");
        }

        User inviter = getUser(inviterUserId);
        notificationService.createNotification(
                targetUser.getId(),
                "COMMUNITY_CONTEST_INVITE",
                "Community Contest Invitation",
                inviter.getUsername() + " invited you to " + contest.getContestName(),
                "{\"communityId\":" + roomId + ",\"contestId\":" + contestId + "}"
        );

        Map<String, Object> result = new HashMap<>();
        result.put("communityId", roomId);
        result.put("contestId", contestId);
        result.put("invitedUserId", targetUser.getId());
        result.put("invitedUsername", targetUser.getUsername());
        result.put("status", "SENT");
        return result;
    }

    @Transactional
    public TeamResponse createCommunityTeam(Long userId, Long roomId, CreateTeamRequest request) {
        throw new RuntimeException("Create a team from the fixture and join a specific community contest instead");
    }

    @Transactional
    public ContestEntryResponse selectCommunityTeam(Long userId, Long roomId, SelectCommunityTeamRequest request) {
        throw new RuntimeException("Join a specific community contest with the team you want to use");
    }

    @Transactional
    public RoomSummaryResponse acceptInvitation(Long currentUserId, Long invitationId) {
        RoomInvitation invitation = roomInvitationRepository.findByIdAndStatus(invitationId, RoomInvitation.Status.PENDING)
                .orElseThrow(() -> new RuntimeException("Invitation not found"));

        User currentUser = getUser(currentUserId);
        validateInvitationOwnership(invitation, currentUser);

        Room room = requireActiveRoom(invitation.getRoomId());

        long memberCount = roomMemberRepository.countByRoomIdAndStatus(room.getId(), RoomMember.Status.JOINED);
        if (memberCount >= room.getMaxMembers()) {
            throw new RuntimeException("Community is full");
        }

        RoomMember membership = roomMemberRepository.findByRoomIdAndUserId(room.getId(), currentUserId).orElse(null);
        if (membership == null) {
            membership = RoomMember.builder()
                    .roomId(room.getId())
                    .userId(currentUserId)
                    .role(RoomMember.Role.MEMBER)
                    .status(RoomMember.Status.JOINED)
                    .joinedAt(LocalDateTime.now())
                    .build();
        } else {
            membership.setRole(RoomMember.Role.MEMBER);
            membership.setStatus(RoomMember.Status.JOINED);
            membership.setJoinedAt(LocalDateTime.now());
        }
        roomMemberRepository.save(membership);

        invitation.setStatus(RoomInvitation.Status.ACCEPTED);
        invitation.setInvitedUserId(currentUserId);
        invitation.setRespondedAt(LocalDateTime.now());
        roomInvitationRepository.save(invitation);

        notificationService.createNotification(
                invitation.getInvitedByUserId(),
                "COMMUNITY_INVITE_ACCEPTED",
                "Invitation Accepted",
                currentUser.getUsername() + " joined your community " + room.getRoomName(),
                "{\"communityId\":" + room.getId() + "}"
        );

        return toRoomSummary(room, currentUser);
    }

    @Transactional
    public Map<String, Object> declineInvitation(Long currentUserId, Long invitationId) {
        RoomInvitation invitation = roomInvitationRepository.findByIdAndStatus(invitationId, RoomInvitation.Status.PENDING)
                .orElseThrow(() -> new RuntimeException("Invitation not found"));

        User currentUser = getUser(currentUserId);
        validateInvitationOwnership(invitation, currentUser);

        RoomMember membership = roomMemberRepository.findByRoomIdAndUserId(invitation.getRoomId(), currentUserId).orElse(null);
        if (membership != null && membership.getStatus() == RoomMember.Status.INVITED) {
            membership.setStatus(RoomMember.Status.LEFT);
            roomMemberRepository.save(membership);
        }

        invitation.setStatus(RoomInvitation.Status.DECLINED);
        invitation.setInvitedUserId(currentUserId);
        invitation.setRespondedAt(LocalDateTime.now());
        roomInvitationRepository.save(invitation);

        notificationService.createNotification(
                invitation.getInvitedByUserId(),
                "COMMUNITY_INVITE_DECLINED",
                "Invitation Declined",
                currentUser.getUsername() + " declined your community invite",
                "{\"communityId\":" + invitation.getRoomId() + "}"
        );

        return Map.of(
                "invitationId", invitation.getId(),
                "status", invitation.getStatus().name()
        );
    }

    public List<RoomInvitationResponse> getIncomingInvitations(Long currentUserId) {
        User currentUser = getUser(currentUserId);

        List<RoomInvitation> invitations = new ArrayList<>(roomInvitationRepository
                .findByInvitedUserIdAndStatusOrderByCreatedAtDesc(currentUserId, RoomInvitation.Status.PENDING));

        if (currentUser.getMobile() != null && !currentUser.getMobile().isBlank()) {
            List<RoomInvitation> mobileInvites = roomInvitationRepository
                    .findByInvitedMobileAndStatusOrderByCreatedAtDesc(
                            currentUser.getMobile().trim(),
                            RoomInvitation.Status.PENDING
                    );
            Set<Long> seenIds = new HashSet<>();
            for (RoomInvitation invitation : invitations) {
                seenIds.add(invitation.getId());
            }
            for (RoomInvitation invitation : mobileInvites) {
                if (seenIds.add(invitation.getId())) {
                    invitations.add(invitation);
                }
            }
        }

        return invitations.stream()
                .map(this::toRoomInvitationResponse)
                .toList();
    }

    public List<RoomMemberResponse> getRoomMembers(Long roomId, Long currentUserId) {
        requireJoinedMember(roomId, currentUserId, "You are not a member of this community");

        return roomMemberRepository.findByRoomIdAndStatusOrderByJoinedAtAsc(roomId, RoomMember.Status.JOINED)
                .stream()
                .map(this::toMemberResponse)
                .toList();
    }

    public TeamResponse getCommunityTeamView(Long roomId, Long teamId, Long currentUserId) {
        throw new RuntimeException("Open the contest directly to review participating teams");
    }

    private Room requireActiveRoom(Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Community not found"));
        if (room.getStatus() != Room.Status.ACTIVE) {
            throw new RuntimeException("Community is not active");
        }
        return room;
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private RoomMember requireJoinedMember(Long roomId, Long userId, String message) {
        return roomMemberRepository.findByRoomIdAndUserId(roomId, userId)
                .filter(member -> member.getStatus() == RoomMember.Status.JOINED)
                .orElseThrow(() -> new RuntimeException(message));
    }

    private RoomMember requireOwnerMember(Long roomId, Long userId, String message) {
        RoomMember membership = requireJoinedMember(roomId, userId, message);
        if (membership.getRole() != RoomMember.Role.OWNER) {
            throw new RuntimeException(message);
        }
        return membership;
    }

    private void validateInvitationOwnership(RoomInvitation invitation, User currentUser) {
        boolean allowed = false;
        if (invitation.getInvitedUserId() != null && Objects.equals(invitation.getInvitedUserId(), currentUser.getId())) {
            allowed = true;
        }
        if (!allowed && invitation.getInvitedUserId() == null && invitation.getInvitedMobile() != null) {
            allowed = invitation.getInvitedMobile().equals(currentUser.getMobile());
        }
        if (!allowed) {
            throw new RuntimeException("This invitation does not belong to you");
        }
    }

    private User resolveInviteTargetUser(String username, String mobile) {
        User userByUsername = null;
        if (username != null && !username.isBlank()) {
            userByUsername = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("No user found with this username"));
        }

        User userByMobile = null;
        if (mobile != null && !mobile.isBlank()) {
            userByMobile = userRepository.findByMobile(mobile)
                    .orElseThrow(() -> new RuntimeException("No user found with this mobile"));
        }

        if (userByUsername != null && userByMobile != null
                && !Objects.equals(userByUsername.getId(), userByMobile.getId())) {
            throw new RuntimeException("Username and mobile belong to different users");
        }

        return userByUsername != null ? userByUsername : userByMobile;
    }

    private RoomInvitationResponse toRoomInvitationResponse(RoomInvitation invitation) {
        Room room = roomRepository.findById(invitation.getRoomId()).orElse(null);
        User inviter = userRepository.findById(invitation.getInvitedByUserId()).orElse(null);

        long joinedMembers = room == null
                ? 0L
                : roomMemberRepository.countByRoomIdAndStatus(room.getId(), RoomMember.Status.JOINED);

        return RoomInvitationResponse.builder()
                .invitationId(invitation.getId())
                .communityId(invitation.getRoomId())
                .communityName(room != null ? room.getRoomName() : null)
                .invitedBy(inviter != null ? inviter.getUsername() : null)
                .invitedByUsername(inviter != null ? inviter.getUsername() : null)
                .joiningPoints(null)
                .maxSpots(room != null ? room.getMaxMembers() : null)
                .joinedMembers(joinedMembers)
                .invitedByUserId(invitation.getInvitedByUserId())
                .invitedUserId(invitation.getInvitedUserId())
                .invitedMobile(invitation.getInvitedMobile())
                .invitedUsername(invitation.getInvitedUsername())
                .status(invitation.getStatus().name())
                .inviteMessage(invitation.getInviteMessage())
                .respondedAt(invitation.getRespondedAt())
                .createdAt(invitation.getCreatedAt())
                .build();
    }

    private RoomSummaryResponse toRoomSummary(Room room, User currentUser) {
        RoomMember myMembership = roomMemberRepository.findByRoomIdAndUserId(room.getId(), currentUser.getId())
                .filter(member -> member.getStatus() == RoomMember.Status.JOINED)
                .orElse(null);
        boolean isOwner = myMembership != null && myMembership.getRole() == RoomMember.Role.OWNER;

        boolean invited = hasPendingInvitation(room.getId(), currentUser);
        User creator = userRepository.findById(room.getCreatedByUserId()).orElse(null);
        List<Contest> contests = contestRepository.findByRoomIdOrderByCreatedAtDescIdDesc(room.getId());

        return RoomSummaryResponse.builder()
                .communityId(room.getId())
                .contestId(contests.isEmpty() ? null : contests.get(0).getId())
                .fixtureId(null)
                .sportId(room.getSportId())
                .createdByUserId(room.getCreatedByUserId())
                .createdByUsername(creator != null ? creator.getUsername() : null)
                .communityName(room.getRoomName())
                .communityCode(isOwner ? room.getRoomCode() : null)
                .isPrivate(room.getIsPrivate())
                .maxSpots(room.getMaxMembers())
                .joinedMembers(roomMemberRepository.countByRoomIdAndStatus(room.getId(), RoomMember.Status.JOINED))
                .contestCount((int) contestRepository.countByRoomId(room.getId()))
                .joiningPoints(null)
                .prizePoolPoints(null)
                .winnerPayoutPoints(null)
                .myRole(myMembership != null ? myMembership.getRole().name() : null)
                .status(room.getStatus().name())
                .isMember(myMembership != null)
                .isInvited(invited)
                .contestStatus(summarizeContestStatus(contests))
                .fixtureStatus(null)
                .fixtureTitle(null)
                .fixtureStartTime(null)
                .fixtureDeadlineTime(null)
                .teamCreated(false)
                .canCreateTeam(false)
                .canInvite(isOwner && room.getStatus() == Room.Status.ACTIVE)
                .canViewParticipantTeams(false)
                .canEdit(isOwner && room.getStatus() == Room.Status.ACTIVE)
                .canDelete(isOwner && room.getStatus() == Room.Status.ACTIVE)
                .build();
    }

    private String summarizeContestStatus(List<Contest> contests) {
        if (contests.isEmpty()) {
            return "NO_CONTESTS";
        }

        boolean hasLive = contests.stream().anyMatch(contest -> contest.getStatus() == Contest.Status.LIVE);
        if (hasLive) {
            return Contest.Status.LIVE.name();
        }

        boolean hasOpen = contests.stream().anyMatch(contest ->
                contest.getStatus() == Contest.Status.OPEN || contest.getStatus() == Contest.Status.FULL
        );
        if (hasOpen) {
            return Contest.Status.OPEN.name();
        }

        boolean hasCompleted = contests.stream().anyMatch(contest -> contest.getStatus() == Contest.Status.COMPLETED);
        if (hasCompleted) {
            return Contest.Status.COMPLETED.name();
        }

        boolean hasCancelled = contests.stream().anyMatch(contest -> contest.getStatus() == Contest.Status.CANCELLED);
        if (hasCancelled) {
            return Contest.Status.CANCELLED.name();
        }

        return contests.get(0).getStatus().name();
    }

    private RoomAvailableContestResponse toRoomContestResponse(Contest contest, Room room, User currentUser) {
        Fixture fixture = fixtureRepository.findById(contest.getFixtureId()).orElse(null);
        List<FixtureParticipant> participants = fixture == null
                ? List.of()
                : fixtureParticipantRepository.findByFixtureIdOrderByIsHomeDescTeamNameAsc(fixture.getId());
        User creator = contest.getCreatedByUserId() == null ? null : userRepository.findById(contest.getCreatedByUserId()).orElse(null);
        RoomMember viewerMembership = roomMemberRepository.findByRoomIdAndUserId(room.getId(), currentUser.getId())
                .filter(member -> member.getStatus() == RoomMember.Status.JOINED)
                .orElse(null);

        long myEntriesCount = contestEntryRepository.countByContestIdAndUserId(contest.getId(), currentUser.getId());
        boolean beforeDeadline = fixture != null && fixture.getDeadlineTime().isAfter(LocalDateTime.now());
        boolean canJoin = viewerMembership != null
                && contest.getStatus() == Contest.Status.OPEN
                && beforeDeadline
                && contest.getSpotsFilled() < contest.getMaxSpots();
        boolean canInvite = viewerMembership != null
                && beforeDeadline
                && contest.getStatus() != Contest.Status.CANCELLED
                && contest.getStatus() != Contest.Status.COMPLETED
                && (viewerMembership.getRole() == RoomMember.Role.OWNER
                || Objects.equals(contest.getCreatedByUserId(), currentUser.getId()));

        String fixtureLeague = "";
        if (fixture != null) {
            fixtureLeague = fixtureSnapshotMapper.buildSnapshot(fixture, participants).league();
        }

        return RoomAvailableContestResponse.builder()
                .contestId(contest.getId())
                .communityId(room.getId())
                .fixtureId(contest.getFixtureId())
                .contestName(contest.getContestName())
                .entryFeePoints(contest.getEntryFeePoints())
                .prizePoolPoints(contest.getPrizePoolPoints())
                .winnerCount(contest.getWinnerCount())
                .maxSpots(contest.getMaxSpots())
                .spotsFilled(contest.getSpotsFilled())
                .spotsLeft(Math.max(0, contest.getMaxSpots() - contest.getSpotsFilled()))
                .joinConfirmRequired(contest.getJoinConfirmRequired())
                .firstPrizePoints(contest.getFirstPrizePoints())
                .contestStatus(contest.getStatus().name())
                .createdByUserId(contest.getCreatedByUserId())
                .createdByUsername(creator != null ? creator.getUsername() : null)
                .fixtureLeague(fixtureLeague)
                .myEntriesCount((int) myEntriesCount)
                .joinedByMe(myEntriesCount > 0)
                .canJoin(canJoin)
                .canInvite(canInvite)
                .fixtureTitle(fixture != null ? fixture.getTitle() : null)
                .fixtureStartTime(fixture != null ? fixture.getStartTime() : null)
                .fixtureDeadlineTime(fixture != null ? fixture.getDeadlineTime() : null)
                .participants(participants.stream()
                        .map(participant -> AvailableContestFixtureParticipantResponse.builder()
                                .externalTeamId(participant.getExternalTeamId())
                                .teamName(participant.getTeamName())
                                .shortName(participant.getShortName())
                                .logoUrl(participant.getLogoUrl())
                                .isHome(participant.getIsHome())
                                .build())
                        .toList())
                .build();
    }

    private RoomMemberResponse toMemberResponse(RoomMember member) {
        User user = getUser(member.getUserId());
        return RoomMemberResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .mobile(user.getMobile())
                .role(member.getRole().name())
                .status(member.getStatus().name())
                .teamCreated(false)
                .joinedAt(member.getJoinedAt())
                .build();
    }

    private boolean hasPendingInvitation(Long roomId, User user) {
        if (roomInvitationRepository.existsByRoomIdAndInvitedUserIdAndStatus(roomId, user.getId(), RoomInvitation.Status.PENDING)) {
            return true;
        }

        return user.getMobile() != null
                && !user.getMobile().isBlank()
                && roomInvitationRepository.existsByRoomIdAndInvitedMobileAndStatus(
                roomId,
                user.getMobile().trim(),
                RoomInvitation.Status.PENDING
        );
    }

    private String buildCommunityContestName(Fixture fixture, Integer joiningPoints, String username) {
        List<FixtureParticipant> participants = fixtureParticipantRepository.findByFixtureIdOrderByIsHomeDescTeamNameAsc(fixture.getId());
        String matchLabel = fixture.getTitle();
        if (participants.size() >= 2) {
            FixtureParticipant home = participants.stream()
                    .filter(FixtureParticipant::getIsHome)
                    .findFirst()
                    .orElse(participants.get(0));
            FixtureParticipant away = participants.stream()
                    .filter(participant -> !Boolean.TRUE.equals(participant.getIsHome()))
                    .findFirst()
                    .orElse(participants.get(1));
            String homeShort = home.getShortName() != null && !home.getShortName().isBlank() ? home.getShortName() : home.getTeamName();
            String awayShort = away.getShortName() != null && !away.getShortName().isBlank() ? away.getShortName() : away.getTeamName();
            matchLabel = homeShort + " vs " + awayShort;
        }

        return matchLabel + " " + joiningPoints + " point contest by " + username;
    }

    private void validateJoinPoints(Integer joiningPoints) {
        if (!ALLOWED_JOIN_POINTS.contains(joiningPoints)) {
            throw new RuntimeException("Joining points must be one of 500, 1000, 2000, 5000, 10000, 20000, 50000");
        }
    }

    private void validateCommunityContestStructure(int winnerCount, int maxSpots, int communityMaxMembers) {
        if (winnerCount < 1 || winnerCount > 3) {
            throw new RuntimeException("winnerCount must be 1, 2, or 3");
        }

        int minSpots = switch (winnerCount) {
            case 1 -> 2;
            case 2 -> 5;
            case 3 -> 10;
            default -> 2;
        };
        int maxAllowedSpots = switch (winnerCount) {
            case 1 -> 10;
            case 2 -> 20;
            case 3 -> 30;
            default -> 10;
        };

        if (maxSpots < minSpots || maxSpots > maxAllowedSpots) {
            throw new RuntimeException(
                    "maxSpots must be between " + minSpots + " and " + maxAllowedSpots
                            + " when winnerCount is " + winnerCount
            );
        }

        if (maxSpots > communityMaxMembers) {
            throw new RuntimeException("Contest max spots cannot exceed the community member limit");
        }
    }

    private String normalizeCommunityName(String communityName) {
        if (communityName == null || communityName.trim().isBlank()) {
            throw ApiException.badRequest("Community name is required");
        }

        String normalized = communityName.trim().replaceAll("\\s+", " ");
        if (normalized.length() > 100) {
            throw ApiException.badRequest("Community name must be 100 characters or less");
        }
        return normalized;
    }

    private void syncOpenCommunityContestCap(Room room) {
        List<Contest> contests = contestRepository.findByRoomIdOrderByCreatedAtDescIdDesc(room.getId());
        List<Contest> updatableContests = new ArrayList<>();

        for (Contest contest : contests) {
            if (contest.getStatus() != Contest.Status.OPEN && contest.getStatus() != Contest.Status.FULL) {
                continue;
            }

            int nextMaxSpots = Math.min(
                    contest.getMaxSpots() != null ? contest.getMaxSpots() : room.getMaxMembers(),
                    room.getMaxMembers()
            );
            contest.setMaxSpots(nextMaxSpots);
            contest.setStatus(contest.getSpotsFilled() >= nextMaxSpots
                    ? Contest.Status.FULL
                    : Contest.Status.OPEN);
            updatableContests.add(contest);
        }

        if (!updatableContests.isEmpty()) {
            contestRepository.saveAll(updatableContests);
        }
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

        throw new RuntimeException("Unable to generate unique community code");
    }
}

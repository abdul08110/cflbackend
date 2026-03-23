package com.friendsfantasy.fantasybackend.room.service;

import com.friendsfantasy.fantasybackend.auth.entity.User;
import com.friendsfantasy.fantasybackend.auth.repository.UserRepository;
import com.friendsfantasy.fantasybackend.contest.dto.ContestEntryResponse;
import com.friendsfantasy.fantasybackend.contest.dto.LeaderboardEntryResponse;
import com.friendsfantasy.fantasybackend.contest.entity.Contest;
import com.friendsfantasy.fantasybackend.contest.entity.ContestEntry;
import com.friendsfantasy.fantasybackend.contest.repository.ContestEntryRepository;
import com.friendsfantasy.fantasybackend.contest.repository.ContestRepository;
import com.friendsfantasy.fantasybackend.contest.service.ContestEntryService;
import com.friendsfantasy.fantasybackend.fixture.entity.Fixture;
import com.friendsfantasy.fantasybackend.fixture.entity.FixtureParticipant;
import com.friendsfantasy.fantasybackend.fixture.repository.FixtureParticipantRepository;
import com.friendsfantasy.fantasybackend.fixture.repository.FixtureRepository;
import com.friendsfantasy.fantasybackend.fixture.service.FixtureSnapshotMapper;
import com.friendsfantasy.fantasybackend.notification.entity.Notification;
import com.friendsfantasy.fantasybackend.notification.repository.NotificationRepository;
import com.friendsfantasy.fantasybackend.room.dto.CreateRoomRequest;
import com.friendsfantasy.fantasybackend.room.dto.InviteToRoomRequest;
import com.friendsfantasy.fantasybackend.room.dto.JoinRoomByCodeRequest;
import com.friendsfantasy.fantasybackend.room.dto.RoomDetailResponse;
import com.friendsfantasy.fantasybackend.room.dto.RoomInvitationResponse;
import com.friendsfantasy.fantasybackend.room.dto.RoomMemberResponse;
import com.friendsfantasy.fantasybackend.room.dto.RoomSummaryResponse;
import com.friendsfantasy.fantasybackend.room.dto.SelectCommunityTeamRequest;
import com.friendsfantasy.fantasybackend.room.entity.Room;
import com.friendsfantasy.fantasybackend.room.entity.RoomInvitation;
import com.friendsfantasy.fantasybackend.room.entity.RoomMember;
import com.friendsfantasy.fantasybackend.room.repository.RoomInvitationRepository;
import com.friendsfantasy.fantasybackend.room.repository.RoomMemberRepository;
import com.friendsfantasy.fantasybackend.room.repository.RoomRepository;
import com.friendsfantasy.fantasybackend.stats.service.UserStatsService;
import com.friendsfantasy.fantasybackend.team.dto.CreateTeamRequest;
import com.friendsfantasy.fantasybackend.team.dto.TeamResponse;
import com.friendsfantasy.fantasybackend.team.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RoomService {

    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final Set<Integer> ALLOWED_JOIN_POINTS = Set.of(500, 1000, 2000, 5000, 10000, 20000, 50000);

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomInvitationRepository roomInvitationRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ContestRepository contestRepository;
    private final ContestEntryRepository contestEntryRepository;
    private final FixtureRepository fixtureRepository;
    private final FixtureParticipantRepository fixtureParticipantRepository;
    private final ContestEntryService contestEntryService;
    private final FixtureSnapshotMapper fixtureSnapshotMapper;
    private final TeamService teamService;
    private final UserStatsService userStatsService;

    @Transactional
    public RoomSummaryResponse createRoom(Long userId, CreateRoomRequest request) {
        validateJoinPoints(request.getJoiningPoints());

        Fixture fixture = fixtureRepository.findById(request.getFixtureId())
                .orElseThrow(() -> new RuntimeException("Fixture not found"));

        if (!fixture.getDeadlineTime().isAfter(LocalDateTime.now())) {
            throw new RuntimeException("Community creation is closed for this fixture");
        }

        Room room = Room.builder()
                .sportId(fixture.getSportId())
                .fixtureId(fixture.getId())
                .createdByUserId(userId)
                .roomName(request.getCommunityName().trim())
                .roomCode(generateUniqueRoomCode())
                .isPrivate(Boolean.TRUE.equals(request.getIsPrivate()))
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

        Contest contest = Contest.builder()
                .fixtureId(fixture.getId())
                .roomId(room.getId())
                .scoringTemplateId(1L)
                .contestName(room.getRoomName())
                .contestType(Contest.ContestType.COMMUNITY)
                .entryFeePoints(request.getJoiningPoints())
                .prizePoolPoints(0)
                .winnerCount(1)
                .maxSpots(room.getMaxMembers())
                .spotsFilled(0)
                .joinConfirmRequired(false)
                .firstPrizePoints(0)
                .status(Contest.Status.OPEN)
                .createdByUserId(userId)
                .build();
        contestRepository.save(contest);

        contestEntryService.createCommunityEntry(userId, room.getId());
        userStatsService.recordCommunityCreated(userId);

        return toRoomSummary(room, owner.getRole().name(), userId);
    }

    public List<RoomSummaryResponse> getMyRooms(Long userId) {
        List<RoomMember> memberships = roomMemberRepository
                .findByUserIdAndStatusOrderByCreatedAtDesc(userId, RoomMember.Status.JOINED);

        List<RoomSummaryResponse> response = new ArrayList<>();
        for (RoomMember membership : memberships) {
            Room room = roomRepository.findById(membership.getRoomId()).orElse(null);
            if (room == null) {
                continue;
            }

            contestEntryService.syncCommunityContestState(room.getId());
            response.add(toRoomSummary(room, membership.getRole().name(), userId));
        }

        return response;
    }

    public RoomDetailResponse getRoomDetails(Long roomId, Long userId) {
        contestEntryService.syncCommunityContestState(roomId);

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Community not found"));

        RoomMember myMembership = requireJoinedMember(roomId, userId, "You are not a member of this community");
        Contest contest = contestRepository.findByRoomId(roomId).orElse(null);
        ContestEntryResponse myEntry = contestEntryService.getCommunityEntry(userId, roomId);
        List<LeaderboardEntryResponse> leaderboard = contest != null
                ? contestEntryService.getLeaderboard(contest.getId())
                : List.of();
        Fixture fixture = resolveFixture(room, contest);
        List<FixtureParticipant> participants = fixture == null
                ? List.of()
                : fixtureParticipantRepository.findByFixtureIdOrderByIsHomeDescTeamNameAsc(fixture.getId());

        Map<Long, Boolean> teamCreatedByUser = new HashMap<>();
        if (contest != null) {
            for (ContestEntry entry : contestEntryRepository.findByContestIdOrderByJoinedAtAsc(contest.getId())) {
                teamCreatedByUser.put(entry.getUserId(), entry.getUserMatchTeamId() != null);
            }
        }

        List<RoomMemberResponse> members = roomMemberRepository
                .findByRoomIdAndStatusOrderByJoinedAtAsc(roomId, RoomMember.Status.JOINED)
                .stream()
                .map(member -> toMemberResponse(member, teamCreatedByUser.getOrDefault(member.getUserId(), false)))
                .toList();

        return RoomDetailResponse.builder()
                .community(toRoomSummary(room, myMembership.getRole().name(), userId))
                .myEntry(myEntry)
                .fixtureLiveData(fixture == null ? null : fixtureSnapshotMapper.buildLiveData(fixture, participants))
                .members(members)
                .leaderboard(leaderboard)
                .build();
    }

    @Transactional
    public RoomSummaryResponse joinByCode(Long userId, JoinRoomByCodeRequest request) {
        String communityCode = request.getCommunityCode().trim().toUpperCase();

        Room room = roomRepository.findByRoomCode(communityCode)
                .orElseThrow(() -> new RuntimeException("Invalid community code"));

        contestEntryService.syncCommunityContestState(room.getId());

        if (room.getStatus() != Room.Status.ACTIVE) {
            throw new RuntimeException("Community is not active");
        }

        RoomMember existing = roomMemberRepository.findByRoomIdAndUserId(room.getId(), userId).orElse(null);
        if (existing != null && existing.getStatus() == RoomMember.Status.JOINED) {
            throw new RuntimeException("You are already in this community");
        }

        long memberCount = roomMemberRepository.countByRoomIdAndStatus(room.getId(), RoomMember.Status.JOINED);
        if (memberCount >= room.getMaxMembers()) {
            throw new RuntimeException("Community is full");
        }

        if (existing == null) {
            existing = RoomMember.builder()
                    .roomId(room.getId())
                    .userId(userId)
                    .role(RoomMember.Role.MEMBER)
                    .status(RoomMember.Status.JOINED)
                    .joinedAt(LocalDateTime.now())
                    .build();
        } else {
            existing.setStatus(RoomMember.Status.JOINED);
            existing.setRole(existing.getRole() == null ? RoomMember.Role.MEMBER : existing.getRole());
            existing.setJoinedAt(LocalDateTime.now());
        }

        roomMemberRepository.save(existing);
        contestEntryService.createCommunityEntry(userId, room.getId());

        return toRoomSummary(room, existing.getRole().name(), userId);
    }

    @Transactional
    public Map<String, Object> inviteToRoom(Long inviterUserId, Long roomId, InviteToRoomRequest request) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Community not found"));

        requireJoinedMember(roomId, inviterUserId, "Only community members can invite users");
        ensureCommunityJoinWindowOpen(roomId);

        if (room.getStatus() != Room.Status.ACTIVE) {
            throw new RuntimeException("Community is not active");
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

        RoomMember existing = roomMemberRepository.findByRoomIdAndUserId(roomId, targetUser.getId()).orElse(null);
        if (existing != null && existing.getStatus() == RoomMember.Status.JOINED) {
            throw new RuntimeException("User is already in this community");
        }

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

        String payload = "{\"communityId\":" + roomId + ",\"invitationId\":" + invitation.getId() + "}";
        Notification notification = Notification.builder()
                .userId(targetUser.getId())
                .type("COMMUNITY_INVITE")
                .title("Community Invitation")
                .body("You have been invited to join community " + room.getRoomName())
                .payloadJson(payload)
                .isRead(false)
                .build();
        notificationRepository.save(notification);

        Map<String, Object> result = new HashMap<>();
        result.put("invitationId", invitation.getId());
        result.put("communityId", roomId);
        result.put("invitedUserId", targetUser != null ? targetUser.getId() : null);
        result.put("invitedUsername", invitation.getInvitedUsername());
        result.put("invitedMobile", invitation.getInvitedMobile());
        result.put("status", invitation.getStatus().name());
        return result;
    }

    @Transactional
    public TeamResponse createCommunityTeam(Long userId, Long roomId, CreateTeamRequest request) {
        contestEntryService.syncCommunityContestState(roomId);

        requireJoinedMember(roomId, userId, "You are not a member of this community");

        Contest contest = contestRepository.findByRoomId(roomId)
                .orElseThrow(() -> new RuntimeException("Community contest not found"));

        Fixture fixture = fixtureRepository.findById(contest.getFixtureId())
                .orElseThrow(() -> new RuntimeException("Fixture not found"));

        if (!fixture.getDeadlineTime().isAfter(LocalDateTime.now())) {
            throw new RuntimeException("Team creation is closed for this community");
        }

        contestEntryRepository.findByContestIdAndUserId(contest.getId(), userId)
                .orElseThrow(() -> new RuntimeException("You have not joined this community"));

        TeamResponse team = teamService.createTeam(userId, fixture.getId(), request);
        contestEntryService.attachTeamToCommunityEntry(userId, roomId, team.getTeamId());
        return team;
    }

    @Transactional
    public ContestEntryResponse selectCommunityTeam(Long userId, Long roomId, SelectCommunityTeamRequest request) {
        requireJoinedMember(roomId, userId, "You are not a member of this community");
        return contestEntryService.attachTeamToCommunityEntry(userId, roomId, request.getTeamId());
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
                .orElseThrow(() -> new RuntimeException("Community not found"));

        contestEntryService.syncCommunityContestState(room.getId());

        if (room.getStatus() != Room.Status.ACTIVE) {
            throw new RuntimeException("Community is not active");
        }

        long memberCount = roomMemberRepository.countByRoomIdAndStatus(room.getId(), RoomMember.Status.JOINED);
        if (memberCount >= room.getMaxMembers()) {
            throw new RuntimeException("Community is full");
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
        contestEntryService.createCommunityEntry(currentUserId, room.getId());

        invitation.setStatus(RoomInvitation.Status.ACCEPTED);
        invitation.setInvitedUserId(currentUserId);
        invitation.setRespondedAt(LocalDateTime.now());
        roomInvitationRepository.save(invitation);

        Notification notification = Notification.builder()
                .userId(invitation.getInvitedByUserId())
                .type("COMMUNITY_INVITE_ACCEPTED")
                .title("Invitation Accepted")
                .body(currentUser.getUsername() + " joined your community " + room.getRoomName())
                .payloadJson("{\"communityId\":" + room.getId() + "}")
                .isRead(false)
                .build();
        notificationRepository.save(notification);

        return toRoomSummary(room, existing.getRole().name(), currentUserId);
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
                .type("COMMUNITY_INVITE_DECLINED")
                .title("Invitation Declined")
                .body(currentUser.getUsername() + " declined your community invite")
                .payloadJson("{\"communityId\":" + invitation.getRoomId() + "}")
                .isRead(false)
                .build();
        notificationRepository.save(notification);

        Map<String, Object> result = new HashMap<>();
        result.put("invitationId", invitation.getId());
        result.put("status", invitation.getStatus().name());
        return result;
    }

    public List<RoomInvitationResponse> getIncomingInvitations(Long currentUserId) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<RoomInvitation> invitations = new ArrayList<>();
        invitations.addAll(roomInvitationRepository.findByInvitedUserIdAndStatusOrderByCreatedAtDesc(
                currentUserId,
                RoomInvitation.Status.PENDING
        ));

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
                if (!seenIds.contains(invitation.getId())) {
                    invitations.add(invitation);
                }
            }
        }

        return invitations.stream()
                .map(this::toRoomInvitationResponse)
                .toList();
    }

    public List<RoomMemberResponse> getRoomMembers(Long roomId, Long currentUserId) {
        contestEntryService.syncCommunityContestState(roomId);
        requireJoinedMember(roomId, currentUserId, "You are not a member of this community");

        Contest contest = contestRepository.findByRoomId(roomId).orElse(null);
        Map<Long, Boolean> teamCreatedByUser = new HashMap<>();
        if (contest != null) {
            for (ContestEntry entry : contestEntryRepository.findByContestIdOrderByJoinedAtAsc(contest.getId())) {
                teamCreatedByUser.put(entry.getUserId(), entry.getUserMatchTeamId() != null);
            }
        }

        return roomMemberRepository.findByRoomIdAndStatusOrderByJoinedAtAsc(roomId, RoomMember.Status.JOINED)
                .stream()
                .map(member -> toMemberResponse(member, teamCreatedByUser.getOrDefault(member.getUserId(), false)))
                .toList();
    }

    public TeamResponse getCommunityTeamView(Long roomId, Long teamId, Long currentUserId) {
        contestEntryService.syncCommunityContestState(roomId);

        requireJoinedMember(roomId, currentUserId, "You are not a member of this community");

        Contest contest = contestRepository.findByRoomId(roomId)
                .orElseThrow(() -> new RuntimeException("Community contest not found"));

        Fixture fixture = fixtureRepository.findById(contest.getFixtureId())
                .orElseThrow(() -> new RuntimeException("Fixture not found"));

        if (contest.getStatus() != Contest.Status.CANCELLED
                && fixture.getDeadlineTime().isAfter(LocalDateTime.now())) {
            throw new RuntimeException("Participant teams can be viewed after the match starts");
        }

        contestEntryRepository.findByContestIdAndUserMatchTeamId(contest.getId(), teamId)
                .orElseThrow(() -> new RuntimeException("This team is not part of the community"));

        return teamService.getTeamById(teamId);
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
        Contest contest = room == null ? null : contestRepository.findByRoomId(room.getId()).orElse(null);
        long joinedMembers = room == null
                ? 0L
                : roomMemberRepository.countByRoomIdAndStatus(room.getId(), RoomMember.Status.JOINED);

        return RoomInvitationResponse.builder()
                .invitationId(invitation.getId())
                .communityId(invitation.getRoomId())
                .communityName(room != null ? room.getRoomName() : null)
                .invitedBy(inviter != null ? inviter.getUsername() : null)
                .joiningPoints(contest != null ? contest.getEntryFeePoints() : null)
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

    private RoomSummaryResponse toRoomSummary(Room room, String myRole, Long currentUserId) {
        Contest contest = contestRepository.findByRoomId(room.getId()).orElse(null);
        Fixture fixture = resolveFixture(room, contest);
        ContestEntry myEntry = contest == null
                ? null
                : contestEntryRepository.findByContestIdAndUserId(contest.getId(), currentUserId).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        boolean contestEnded = contest != null
                && (contest.getStatus() == Contest.Status.COMPLETED
                || contest.getStatus() == Contest.Status.CANCELLED);
        boolean beforeDeadline = fixture != null && fixture.getDeadlineTime().isAfter(now);

        boolean teamCreated = myEntry != null && myEntry.getUserMatchTeamId() != null;
        boolean canCreateTeam = myEntry != null
                && myEntry.getStatus() == ContestEntry.Status.JOINED
                && fixture != null
                && beforeDeadline
                && !contestEnded;
        boolean canInvite = fixture != null && beforeDeadline && !contestEnded;
        boolean canViewParticipantTeams = contestEnded || (fixture != null && !beforeDeadline);

        return RoomSummaryResponse.builder()
                .communityId(room.getId())
                .contestId(contest != null ? contest.getId() : null)
                .fixtureId(fixture != null ? fixture.getId() : room.getFixtureId())
                .sportId(room.getSportId())
                .createdByUserId(room.getCreatedByUserId())
                .communityName(room.getRoomName())
                .communityCode(room.getRoomCode())
                .isPrivate(room.getIsPrivate())
                .maxSpots(room.getMaxMembers())
                .joinedMembers(roomMemberRepository.countByRoomIdAndStatus(room.getId(), RoomMember.Status.JOINED))
                .joiningPoints(contest != null ? contest.getEntryFeePoints() : null)
                .prizePoolPoints(contest != null ? contest.getPrizePoolPoints() : null)
                .winnerPayoutPoints(contest != null ? contest.getFirstPrizePoints() : null)
                .myRole(myRole)
                .status(room.getStatus().name())
                .contestStatus(contest != null ? contest.getStatus().name() : null)
                .fixtureStatus(fixture != null ? fixture.getStatus() : null)
                .fixtureTitle(fixture != null ? fixture.getTitle() : null)
                .fixtureStartTime(fixture != null ? fixture.getStartTime() : null)
                .fixtureDeadlineTime(fixture != null ? fixture.getDeadlineTime() : null)
                .teamCreated(teamCreated)
                .canCreateTeam(canCreateTeam)
                .canInvite(canInvite)
                .canViewParticipantTeams(canViewParticipantTeams)
                .build();
    }

    private RoomMemberResponse toMemberResponse(RoomMember member, boolean teamCreated) {
        User user = userRepository.findById(member.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        return RoomMemberResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .mobile(user.getMobile())
                .role(member.getRole().name())
                .status(member.getStatus().name())
                .teamCreated(teamCreated)
                .joinedAt(member.getJoinedAt())
                .build();
    }

    private Fixture resolveFixture(Room room, Contest contest) {
        Long fixtureId = room.getFixtureId();
        if (fixtureId == null && contest != null) {
            fixtureId = contest.getFixtureId();
        }

        if (fixtureId == null) {
            return null;
        }

        return fixtureRepository.findById(fixtureId).orElse(null);
    }

    private RoomMember requireJoinedMember(Long roomId, Long userId, String message) {
        return roomMemberRepository.findByRoomIdAndUserId(roomId, userId)
                .filter(member -> member.getStatus() == RoomMember.Status.JOINED)
                .orElseThrow(() -> new RuntimeException(message));
    }

    private void ensureCommunityJoinWindowOpen(Long roomId) {
        contestEntryService.syncCommunityContestState(roomId);

        Contest contest = contestRepository.findByRoomId(roomId).orElse(null);
        if (contest == null) {
            return;
        }

        Fixture fixture = fixtureRepository.findById(contest.getFixtureId())
                .orElseThrow(() -> new RuntimeException("Fixture not found"));

        if (!fixture.getDeadlineTime().isAfter(LocalDateTime.now())) {
            throw new RuntimeException("Community invites are closed because the fixture has started");
        }

        if (contest.getStatus() == Contest.Status.CANCELLED) {
            throw new RuntimeException("Community invites are closed because the match was cancelled");
        }

        if (contest.getStatus() == Contest.Status.COMPLETED) {
            throw new RuntimeException("Community invites are closed because this contest has already ended");
        }
    }

    private void validateJoinPoints(Integer joiningPoints) {
        if (!ALLOWED_JOIN_POINTS.contains(joiningPoints)) {
            throw new RuntimeException("Joining points must be one of 500, 1000, 2000, 5000, 10000, 20000, 50000");
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

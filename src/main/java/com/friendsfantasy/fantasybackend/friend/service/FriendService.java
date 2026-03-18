package com.friendsfantasy.fantasybackend.friend.service;

import com.friendsfantasy.fantasybackend.auth.entity.User;
import com.friendsfantasy.fantasybackend.auth.repository.UserRepository;
import com.friendsfantasy.fantasybackend.friend.dto.*;
import com.friendsfantasy.fantasybackend.friend.entity.FriendRequest;
import com.friendsfantasy.fantasybackend.friend.repository.FriendRequestRepository;
import com.friendsfantasy.fantasybackend.notification.entity.Notification;
import com.friendsfantasy.fantasybackend.notification.repository.NotificationRepository;
import com.friendsfantasy.fantasybackend.stats.entity.UserStats;
import com.friendsfantasy.fantasybackend.stats.service.UserStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendRequestRepository friendRequestRepository;
    private final UserRepository userRepository;
    private final UserStatsService userStatsService;
    private final NotificationRepository notificationRepository;

    @Transactional
    public FriendRequestResponse sendFriendRequest(Long currentUserId, SendFriendRequestRequest request) {
        User sender = userRepository.findById(currentUserId)
                .orElseThrow(() -> new RuntimeException("Sender not found"));

        User receiver = resolveTargetUser(request);

        if (receiver == null) {
            throw new RuntimeException("Target user not found");
        }

        if (Objects.equals(sender.getId(), receiver.getId())) {
            throw new RuntimeException("You cannot send a friend request to yourself");
        }

        if (friendRequestRepository.existsPairWithStatus(
                sender.getId(), receiver.getId(), FriendRequest.Status.ACCEPTED)) {
            throw new RuntimeException("You are already friends");
        }

        Optional<FriendRequest> sameDirection = friendRequestRepository
                .findBySenderUserIdAndReceiverUserId(sender.getId(), receiver.getId());

        if (sameDirection.isPresent()) {
            FriendRequest existing = sameDirection.get();

            if (existing.getStatus() == FriendRequest.Status.PENDING) {
                throw new RuntimeException("Friend request already sent");
            }

            if (existing.getStatus() == FriendRequest.Status.ACCEPTED) {
                throw new RuntimeException("You are already friends");
            }

            existing.setStatus(FriendRequest.Status.PENDING);
            existing.setRespondedAt(null);
            existing = friendRequestRepository.save(existing);

            createNotification(
                    receiver.getId(),
                    "FRIEND_REQUEST",
                    "New Friend Request",
                    sender.getUsername() + " sent you a friend request",
                    "{\"senderUserId\":" + sender.getId() + ",\"requestId\":" + existing.getId() + "}"
            );

            return mapFriendRequest(existing);
        }

        Optional<FriendRequest> reverseDirection = friendRequestRepository
                .findBySenderUserIdAndReceiverUserId(receiver.getId(), sender.getId());

        if (reverseDirection.isPresent() && reverseDirection.get().getStatus() == FriendRequest.Status.PENDING) {
            throw new RuntimeException("This user has already sent you a friend request");
        }

        FriendRequest friendRequest = FriendRequest.builder()
                .senderUserId(sender.getId())
                .receiverUserId(receiver.getId())
                .status(FriendRequest.Status.PENDING)
                .build();

        friendRequest = friendRequestRepository.save(friendRequest);

        createNotification(
                receiver.getId(),
                "FRIEND_REQUEST",
                "New Friend Request",
                sender.getUsername() + " sent you a friend request",
                "{\"senderUserId\":" + sender.getId() + ",\"requestId\":" + friendRequest.getId() + "}"
        );

        return mapFriendRequest(friendRequest);
    }

    public List<FriendRequestResponse> getIncomingRequests(Long currentUserId) {
        List<FriendRequest> requests = friendRequestRepository
                .findByReceiverUserIdAndStatusOrderByCreatedAtDesc(currentUserId, FriendRequest.Status.PENDING);

        return requests.stream().map(this::mapFriendRequest).toList();
    }

    public List<FriendRequestResponse> getOutgoingRequests(Long currentUserId) {
        List<FriendRequest> requests = friendRequestRepository
                .findBySenderUserIdAndStatusOrderByCreatedAtDesc(currentUserId, FriendRequest.Status.PENDING);

        return requests.stream().map(this::mapFriendRequest).toList();
    }

    @Transactional
    public FriendRequestResponse acceptRequest(Long currentUserId, Long requestId) {
        FriendRequest request = friendRequestRepository
                .findByIdAndReceiverUserIdAndStatus(requestId, currentUserId, FriendRequest.Status.PENDING)
                .orElseThrow(() -> new RuntimeException("Friend request not found"));

        request.setStatus(FriendRequest.Status.ACCEPTED);
        request.setRespondedAt(LocalDateTime.now());
        request = friendRequestRepository.save(request);

        User receiver = userRepository.findById(currentUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        createNotification(
                request.getSenderUserId(),
                "FRIEND_REQUEST_ACCEPTED",
                "Friend Request Accepted",
                receiver.getUsername() + " accepted your friend request",
                "{\"friendUserId\":" + receiver.getId() + "}"
        );

        return mapFriendRequest(request);
    }

    @Transactional
    public FriendRequestResponse rejectRequest(Long currentUserId, Long requestId) {
        FriendRequest request = friendRequestRepository
                .findByIdAndReceiverUserIdAndStatus(requestId, currentUserId, FriendRequest.Status.PENDING)
                .orElseThrow(() -> new RuntimeException("Friend request not found"));

        request.setStatus(FriendRequest.Status.REJECTED);
        request.setRespondedAt(LocalDateTime.now());
        request = friendRequestRepository.save(request);

        return mapFriendRequest(request);
    }

    public List<FriendResponse> getFriends(Long currentUserId) {
        List<FriendRequest> accepted = friendRequestRepository
                .findAllForUserByStatus(currentUserId, FriendRequest.Status.ACCEPTED);

        List<FriendResponse> response = new ArrayList<>();

        for (FriendRequest fr : accepted) {
            Long friendUserId = Objects.equals(fr.getSenderUserId(), currentUserId)
                    ? fr.getReceiverUserId()
                    : fr.getSenderUserId();

            User friend = userRepository.findById(friendUserId)
                    .orElseThrow(() -> new RuntimeException("Friend user not found"));

            response.add(FriendResponse.builder()
                    .userId(friend.getId())
                    .username(friend.getUsername())
                    .mobileMasked(maskMobile(friend.getMobile()))
                    .build());
        }

        response.sort(Comparator.comparing(FriendResponse::getUsername, String.CASE_INSENSITIVE_ORDER));
        return response;
    }

    @Transactional
    public Map<String, Object> unfriend(Long currentUserId, Long friendUserId) {
        List<FriendRequest> pairHistory = friendRequestRepository.findPairHistory(currentUserId, friendUserId);

        List<FriendRequest> acceptedRows = pairHistory.stream()
                .filter(fr -> fr.getStatus() == FriendRequest.Status.ACCEPTED)
                .toList();

        if (acceptedRows.isEmpty()) {
            throw new RuntimeException("You are not friends with this user");
        }

        friendRequestRepository.deleteAll(acceptedRows);

        Map<String, Object> response = new HashMap<>();
        response.put("friendUserId", friendUserId);
        response.put("unfriended", true);
        return response;
    }

    public FriendStatsResponse getFriendStats(Long currentUserId, Long friendUserId) {
        if (!friendRequestRepository.existsPairWithStatus(
                currentUserId, friendUserId, FriendRequest.Status.ACCEPTED)) {
            throw new RuntimeException("You can view stats only for added friends");
        }

        User friend = userRepository.findById(friendUserId)
                .orElseThrow(() -> new RuntimeException("Friend not found"));

        UserStats stats = userStatsService.getStats(friendUserId);

        return FriendStatsResponse.builder()
                .userId(friend.getId())
                .username(friend.getUsername())
                .totalContestsJoined(stats.getTotalContestsJoined())
                .totalContestsWon(stats.getTotalContestsWon())
                .totalPointsWon(stats.getTotalPointsWon())
                .totalPointsSpent(stats.getTotalPointsSpent())
                .winRate(stats.getWinRate())
                .bestRank(stats.getBestRank())
                .build();
    }

    public List<UserSearchResponse> searchUsers(Long currentUserId, String query) {
        if (query == null || query.trim().isBlank()) {
            return List.of();
        }

        List<User> users = userRepository.searchActiveUsers(query.trim(), currentUserId);
        List<UserSearchResponse> response = new ArrayList<>();

        for (User user : users) {
            boolean alreadyFriend = friendRequestRepository.existsPairWithStatus(
                    currentUserId, user.getId(), FriendRequest.Status.ACCEPTED);

            List<FriendRequest> pairHistory = friendRequestRepository.findPairHistory(currentUserId, user.getId());

            boolean requestPending = false;
            String requestDirection = null;

            for (FriendRequest fr : pairHistory) {
                if (fr.getStatus() == FriendRequest.Status.PENDING) {
                    requestPending = true;
                    requestDirection = Objects.equals(fr.getSenderUserId(), currentUserId)
                            ? "OUTGOING"
                            : "INCOMING";
                    break;
                }
            }

            response.add(UserSearchResponse.builder()
                    .userId(user.getId())
                    .username(user.getUsername())
                    .mobileMasked(maskMobile(user.getMobile()))
                    .alreadyFriend(alreadyFriend)
                    .requestPending(requestPending)
                    .requestDirection(requestDirection)
                    .build());
        }

        return response;
    }

    private User resolveTargetUser(SendFriendRequestRequest request) {
        if (request.getUserId() != null) {
            return userRepository.findById(request.getUserId()).orElse(null);
        }

        if (request.getUsername() != null && !request.getUsername().trim().isBlank()) {
            return userRepository.findByUsername(request.getUsername().trim()).orElse(null);
        }

        return null;
    }

    private FriendRequestResponse mapFriendRequest(FriendRequest request) {
        User sender = userRepository.findById(request.getSenderUserId())
                .orElseThrow(() -> new RuntimeException("Sender not found"));

        User receiver = userRepository.findById(request.getReceiverUserId())
                .orElseThrow(() -> new RuntimeException("Receiver not found"));

        return FriendRequestResponse.builder()
                .requestId(request.getId())
                .senderUserId(sender.getId())
                .senderUsername(sender.getUsername())
                .receiverUserId(receiver.getId())
                .receiverUsername(receiver.getUsername())
                .status(request.getStatus().name())
                .respondedAt(request.getRespondedAt())
                .createdAt(request.getCreatedAt())
                .build();
    }

    private void createNotification(Long userId, String type, String title, String body, String payloadJson) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .body(body)
                .payloadJson(payloadJson)
                .isRead(false)
                .build();

        notificationRepository.save(notification);
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 4) {
            return "****";
        }

        int visible = Math.min(4, mobile.length());
        return "******" + mobile.substring(mobile.length() - visible);
    }
}
package com.friendsfantasy.fantasybackend.friend.repository;

import com.friendsfantasy.fantasybackend.friend.entity.FriendRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {

    Optional<FriendRequest> findBySenderUserIdAndReceiverUserId(Long senderUserId, Long receiverUserId);

    Optional<FriendRequest> findByIdAndReceiverUserIdAndStatus(
            Long id,
            Long receiverUserId,
            FriendRequest.Status status
    );

    List<FriendRequest> findByReceiverUserIdAndStatusOrderByCreatedAtDesc(
            Long receiverUserId,
            FriendRequest.Status status
    );

    List<FriendRequest> findBySenderUserIdAndStatusOrderByCreatedAtDesc(
            Long senderUserId,
            FriendRequest.Status status
    );

    @Query("""
            select fr
            from FriendRequest fr
            where ((fr.senderUserId = :userA and fr.receiverUserId = :userB)
               or  (fr.senderUserId = :userB and fr.receiverUserId = :userA))
            order by fr.createdAt desc
            """)
    List<FriendRequest> findPairHistory(Long userA, Long userB);

    @Query("""
            select fr
            from FriendRequest fr
            where (fr.senderUserId = :userId or fr.receiverUserId = :userId)
              and fr.status = :status
            order by fr.createdAt desc
            """)
    List<FriendRequest> findAllForUserByStatus(Long userId, FriendRequest.Status status);

    @Query("""
            select case when count(fr) > 0 then true else false end
            from FriendRequest fr
            where ((fr.senderUserId = :userA and fr.receiverUserId = :userB)
               or  (fr.senderUserId = :userB and fr.receiverUserId = :userA))
              and fr.status = :status
            """)
    boolean existsPairWithStatus(Long userA, Long userB, FriendRequest.Status status);
}
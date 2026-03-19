package com.friendsfantasy.fantasybackend.auth.repository;

import com.friendsfantasy.fantasybackend.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByMobile(String mobile);
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByMobileOrUsername(String mobile, String username);
    boolean existsByMobile(String mobile);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    @Query("""
            select u
            from User u
            where u.id <> :currentUserId
              and u.status = com.friendsfantasy.fantasybackend.auth.entity.User.Status.ACTIVE
              and (
                    lower(u.username) like lower(concat('%', :query, '%'))
                 or u.mobile like concat('%', :query, '%')
              )
            order by u.username asc
            """)
    List<User> searchActiveUsers(String query, Long currentUserId);
}
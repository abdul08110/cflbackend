package com.friendsfantasy.fantasybackend.security;

import com.friendsfantasy.fantasybackend.auth.entity.User;
import com.friendsfantasy.fantasybackend.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String usernameOrMobile) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(usernameOrMobile)
                .or(() -> userRepository.findByMobile(usernameOrMobile))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        boolean enabled = user.getStatus() == User.Status.ACTIVE;

        return new UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getMobile(),
                user.getPasswordHash(),
                enabled
        );
    }

    public UserDetails loadByUsernameOnly(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        boolean enabled = user.getStatus() == User.Status.ACTIVE;

        return new UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getMobile(),
                user.getPasswordHash(),
                enabled
        );
    }
}
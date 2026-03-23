package com.friendsfantasy.fantasybackend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService customUserDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = authHeader.substring(7);
            String tokenType = jwtService.extractType(token);

            if (!"access".equals(tokenType)) {
                filterChain.doFilter(request, response);
                return;
            }

            String principalType = jwtService.extractPrincipalType(token);
            String username = jwtService.extractUsername(token);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // ADMIN TOKEN
                if ("ADMIN".equalsIgnoreCase(principalType)) {
                    Long adminId = jwtService.extractAdminId(token);

                    if (adminId != null && jwtService.isAdminTokenValid(token, "access")) {
                        AdminPrincipal adminPrincipal = new AdminPrincipal(adminId, username);
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        adminPrincipal,
                                        null,
                                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                                );

                        authentication.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request)
                        );

                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } else {
                    // NORMAL USER TOKEN
                    UserPrincipal userPrincipal =
                            (UserPrincipal) customUserDetailsService.loadByUsernameOnly(username);

                    if (userPrincipal.isEnabled()
                            && jwtService.isTokenValid(token, userPrincipal, "access")) {
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        userPrincipal,
                                        null,
                                        userPrincipal.getAuthorities()
                                );

                        authentication.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request)
                        );

                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        filterChain.doFilter(request, response);
    }
}

package com.friendsfantasy.fantasybackend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.friendsfantasy.fantasybackend.admin.auth.entity.AdminUser;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiry-minutes:60}")
    private long accessTokenExpiryMinutes;

    @Value("${jwt.refresh-token-expiry-days:30}")
    private long refreshTokenExpiryDays;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "JWT secret is missing. Configure jwt.secret or JWT_SECRET before starting the application");
        }

        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes long");
        }

        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(UserPrincipal userPrincipal) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(userPrincipal.getUsername())
                .claims(Map.of(
                        "userId", userPrincipal.getId(),
                        "mobile", userPrincipal.getMobile(),
                        "type", "access",
                        "principalType", "USER",
                        "role", "USER"
                ))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenExpiryMinutes, ChronoUnit.MINUTES)))
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(UserPrincipal userPrincipal) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(userPrincipal.getUsername())
                .claims(Map.of(
                        "userId", userPrincipal.getId(),
                        "mobile", userPrincipal.getMobile(),
                        "type", "refresh",
                        "principalType", "USER",
                        "role", "USER"
                ))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(refreshTokenExpiryDays, ChronoUnit.DAYS)))
                .signWith(secretKey)
                .compact();
    }

    public String generateAdminAccessToken(AdminUser user) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(user.getUsername())
                .claims(Map.of(
                        "adminId", user.getId(),
                        "role", "ADMIN",
                        "principalType", "ADMIN",
                        "type", "access"
                ))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenExpiryMinutes, ChronoUnit.MINUTES)))
                .signWith(secretKey)
                .compact();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public Long extractUserId(String token) {
        Object value = extractAllClaims(token).get("userId");
        if (value == null) return null;
        if (value instanceof Integer i) return i.longValue();
        if (value instanceof Long l) return l;
        return Long.valueOf(String.valueOf(value));
    }

    public Long extractAdminId(String token) {
        Object value = extractAllClaims(token).get("adminId");
        if (value == null) return null;
        if (value instanceof Integer i) return i.longValue();
        if (value instanceof Long l) return l;
        return Long.valueOf(String.valueOf(value));
    }

    public String extractType(String token) {
        Object value = extractAllClaims(token).get("type");
        return value == null ? null : String.valueOf(value);
    }

    public String extractPrincipalType(String token) {
        Object value = extractAllClaims(token).get("principalType");
        return value == null ? null : String.valueOf(value);
    }

    public String extractRole(String token) {
        Object value = extractAllClaims(token).get("role");
        return value == null ? null : String.valueOf(value);
    }

    public Date extractExpiration(String token) {
        return extractAllClaims(token).getExpiration();
    }

    public boolean isTokenValid(String token, UserPrincipal userPrincipal, String expectedType) {
        String username = extractUsername(token);
        String type = extractType(token);

        return username != null
                && username.equals(userPrincipal.getUsername())
                && expectedType.equals(type)
                && !isTokenExpired(token);
    }

    public boolean isAdminTokenValid(String token, String expectedType) {
        String username = extractUsername(token);
        String type = extractType(token);
        String principalType = extractPrincipalType(token);
        String role = extractRole(token);

        return username != null
                && expectedType.equals(type)
                && "ADMIN".equalsIgnoreCase(principalType)
                && "ADMIN".equalsIgnoreCase(role)
                && !isTokenExpired(token);
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

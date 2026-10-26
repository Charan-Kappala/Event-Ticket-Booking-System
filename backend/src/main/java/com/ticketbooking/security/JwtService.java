package com.ticketbooking.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ticketbooking.model.Role;
import com.ticketbooking.model.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

/**
 * Issues and verifies HS256-signed JWTs.
 * Token claims: sub = user ID, email, role, iat, exp.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration tokenLifetime;
    private final Clock clock;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expiration:PT24H}") Duration tokenLifetime,
                      Clock clock) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            // HS256 needs a key of at least 256 bits; refuse to start with a weak one
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes long");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.tokenLifetime = tokenLifetime;
        this.clock = clock;
    }

    public String createToken(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .setSubject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(tokenLifetime)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /** Returns the user if the token's signature is valid and it hasn't expired; empty otherwise. */
    public Optional<CurrentUser> parse(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .setClock(() -> Date.from(clock.instant()))
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return Optional.of(new CurrentUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get("email", String.class),
                    Role.valueOf(claims.get("role", String.class))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}

package com.slotly.api.module.auth.service.impl;

import com.slotly.api.module.auth.service.AuthService;

import com.slotly.api.module.auth.dto.request.LoginRequest;
import com.slotly.api.module.auth.dto.request.RegisterRequest;
import com.slotly.api.module.auth.dto.response.LoginResponse;
import com.slotly.api.module.auth.dto.response.RegistrationResponse;
import com.slotly.api.module.user.entity.User;
import com.slotly.api.module.user.entity.UserRole;
import com.slotly.api.module.user.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

@Service
public class AuthServiceImpl implements AuthService {
    private static final long ACCESS_TOKEN_LIFETIME_SECONDS = 3600;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecretKey jwtSigningKey;
    private final String dummyPasswordHash;
    private final Clock clock;

    public AuthServiceImpl(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        @Value("${slotly.jwt.secret}") String jwtSecret,
        Clock clock
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtSigningKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.dummyPasswordHash = passwordEncoder.encode("slotly-login-timing-guard");
        this.clock = clock;
    }

    @Transactional
    public RegistrationResponse register(RegisterRequest request) {
        if (request.role() == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin registration is not allowed");
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }

        User user = new User(
            request.username(),
            passwordEncoder.encode(request.password()),
            request.firstName(),
            request.lastName(),
            request.role()
        );
        User savedUser;
        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }

        return new RegistrationResponse(
            savedUser.getId(),
            savedUser.getUsername(),
            savedUser.getRole()
        );
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw invalidCredentials();
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw invalidCredentials();
        }

        Instant issuedAt = clock.instant();
        String accessToken = Jwts.builder()
            .subject(user.getId().toString())
            .claim("role", user.getRole().name())
            .claim("username", user.getUsername())
            .claim("version", user.getAuthVersion())
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(issuedAt.plusSeconds(ACCESS_TOKEN_LIFETIME_SECONDS)))
            .signWith(jwtSigningKey)
            .compact();

        return new LoginResponse(
            accessToken,
            "Bearer",
            ACCESS_TOKEN_LIFETIME_SECONDS
        );
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }
}

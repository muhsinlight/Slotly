package com.slotly.api.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;
import com.slotly.api.common.response.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final SecretKey signingKey;
    private final ObjectMapper objectMapper;
    private final com.slotly.api.module.user.repository.UserRepository users;

    public JwtAuthenticationFilter(
        @Value("${slotly.jwt.secret}") String secret,
        ObjectMapper objectMapper,
        com.slotly.api.module.user.repository.UserRepository users
    ) {
        this.objectMapper = objectMapper;
        this.users = users;
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring("Bearer ".length());

            try {
                Jws<Claims> parsedToken = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token);

                Claims claims = parsedToken.getPayload();
                String subject = claims.getSubject();
                String role = claims.get("role", String.class);

                if (subject == null || !isSupportedRole(role)) {
                    throw new JwtException("Token claims are invalid");
                }
                var user = users.findById(java.util.UUID.fromString(subject))
                    .orElseThrow(() -> new JwtException("Account no longer exists"));
                Number version = claims.get("version", Number.class);
                if (user.getRole().name().equals(role) == false || user.getAuthVersion() != (version == null ? 0 : version.longValue())) {
                    throw new JwtException("Session has been revoked");
                }

                var authentication = new UsernamePasswordAuthenticationToken(
                    subject,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role))
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException exception) {
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                objectMapper.writeValue(
                    response.getOutputStream(),
                    ApiResponse.failure(HttpStatus.UNAUTHORIZED, "Invalid or expired bearer token")
                );
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isSupportedRole(String role) {
        return "OWNER".equals(role) || "CUSTOMER".equals(role) || "ADMIN".equals(role);
    }
}

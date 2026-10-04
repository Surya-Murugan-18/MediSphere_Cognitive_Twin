package com.medisphere.websocket;

import com.medisphere.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * WebSocket STOMP channel interceptor for JWT authentication.
 *
 * Per design.md §8.2:
 *   "WebSocket connections require a valid JWT. The JWT is sent in the
 *    STOMP CONNECT frame headers: Authorization: Bearer <accessToken>"
 *
 * On CONNECT:
 *   1. Extract the Authorization header from the STOMP frame.
 *   2. Validate the JWT using the existing JwtTokenProvider.
 *   3. Set the Spring Security principal on the STOMP session.
 *   4. Reject the connection (throw exception) if the token is invalid.
 *
 * This reuses the Phase 1 authentication infrastructure — no separate
 * auth system is created.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            // Only intercept CONNECT frames; let all others through
            return message;
        }

        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("WebSocket CONNECT rejected: missing or malformed Authorization header");
            throw new org.springframework.security.access.AccessDeniedException(
                    "WebSocket connection requires a valid JWT token.");
        }

        String token = authHeader.substring(7);

        if (!jwtTokenProvider.validateAccessToken(token)) {
            log.warn("WebSocket CONNECT rejected: invalid JWT token");
            throw new org.springframework.security.access.AccessDeniedException(
                    "WebSocket connection rejected: invalid or expired JWT token.");
        }

        try {
            String providerId = jwtTokenProvider.extractProviderId(token);
            String role       = jwtTokenProvider.extractRole(token).name();

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            providerId,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role)));

            accessor.setUser(authentication);
            log.debug("WebSocket CONNECT authenticated: providerId={} role={}", providerId, role);

        } catch (Exception e) {
            log.warn("WebSocket CONNECT rejected: token parsing error — {}", e.getMessage());
            throw new org.springframework.security.access.AccessDeniedException(
                    "WebSocket authentication failed.");
        }

        return message;
    }
}

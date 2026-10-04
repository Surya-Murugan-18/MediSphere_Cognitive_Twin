package com.medisphere.config;

import com.medisphere.websocket.WebSocketAuthChannelInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

/**
 * STOMP over SockJS WebSocket configuration.
 *
 * Per design.md §8:
 *   Client connects to:  /ws  (SockJS fallback supported)
 *   Broker destinations: /topic/...
 *
 * JWT authentication is enforced in {@link WebSocketAuthChannelInterceptor}
 * on the STOMP CONNECT frame before any subscription is processed.
 *
 * Phase 8 security hardening (B8.7):
 *   WebSocket allowed origin patterns are now driven by the same
 *   medisphere.cors.allowed-origins configuration as the HTTP CORS bean.
 *   This eliminates the wildcard ("*") that was present before Phase 8.
 *   Safe fallback: if no origins are configured, only localhost patterns
 *   are permitted.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthChannelInterceptor authInterceptor;
    private final CorsProperties corsProperties;

    public WebSocketConfig(WebSocketAuthChannelInterceptor authInterceptor,
                           CorsProperties corsProperties) {
        this.authInterceptor = authInterceptor;
        this.corsProperties  = corsProperties;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Enable simple in-memory broker for /topic destinations
        registry.enableSimpleBroker("/topic");
        // Application destination prefix (client → server, if needed)
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Use the same allowed-origins as the HTTP CORS configuration.
        // Never use "*" — in a clinical environment all origins must be explicit.
        List<String> origins = corsProperties.allowedOrigins();
        String[] allowedPatterns = (origins != null && !origins.isEmpty())
                ? origins.toArray(new String[0])
                : new String[]{"http://localhost:3000", "http://localhost:5173"};

        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedPatterns)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Register JWT interceptor — validates token on CONNECT, rejects invalid sessions
        registration.interceptors(authInterceptor);
    }
}

package com.traffic.traffic_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket and STOMP message broker configuration for broadcasting real-time
 * traffic light state changes and countdown updates to connected browser clients.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // In-memory message broker with destination prefix /topic
        config.enableSimpleBroker("/topic");
        // Application destination prefix for incoming messages (if needed)
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Standard WebSocket STOMP endpoint with SockJS fallback support
        registry.addEndpoint("/ws-traffic")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        // Plain WebSocket endpoint for lightweight clients without SockJS
        registry.addEndpoint("/ws-traffic-plain")
                .setAllowedOriginPatterns("*");
    }
}

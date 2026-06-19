package com.lumira.message.config;

import com.lumira.common.web.WebProperties;
import com.lumira.message.service.MessageSessionHandshakeInterceptor;
import com.lumira.message.service.MessageWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.List;

@Configuration
@EnableWebSocket
public class MessageWebSocketConfig implements WebSocketConfigurer {

    private static final String MESSAGE_WS_PATH = "/ws/message";

    private final MessageWebSocketHandler messageWebSocketHandler;
    private final MessageSessionHandshakeInterceptor handshakeInterceptor;
    private final WebProperties webProperties;

    public MessageWebSocketConfig(
            MessageWebSocketHandler messageWebSocketHandler,
            MessageSessionHandshakeInterceptor handshakeInterceptor,
            WebProperties webProperties
    ) {
        this.messageWebSocketHandler = messageWebSocketHandler;
        this.handshakeInterceptor = handshakeInterceptor;
        this.webProperties = webProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        var registration = registry.addHandler(messageWebSocketHandler, MESSAGE_WS_PATH)
                .addInterceptors(handshakeInterceptor);
        applyAllowedOrigins(registration);
    }

    private void applyAllowedOrigins(org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration registration) {
        List<String> origins = webProperties.getCorsAllowedOrigins();
        List<String> patterns = webProperties.getCorsAllowedOriginPatterns();
        if (origins != null && !origins.isEmpty()) {
            registration.setAllowedOrigins(origins.toArray(new String[0]));
            return;
        }
        if (patterns != null && !patterns.isEmpty()) {
            registration.setAllowedOriginPatterns(patterns.toArray(new String[0]));
            return;
        }
        registration.setAllowedOriginPatterns("*");
    }
}

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
    private static final String[] DEFAULT_DEV_ALLOWED_ORIGIN_PATTERNS = {
            "http://localhost:*",
            "http://127.0.0.1:*",
            "http://192.168.*:*",
            "http://10.*:*",
            "http://172.16.*:*",
            "http://172.17.*:*",
            "http://172.18.*:*",
            "http://172.19.*:*",
            "http://172.20.*:*",
            "http://172.21.*:*",
            "http://172.22.*:*",
            "http://172.23.*:*",
            "http://172.24.*:*",
            "http://172.25.*:*",
            "http://172.26.*:*",
            "http://172.27.*:*",
            "http://172.28.*:*",
            "http://172.29.*:*",
            "http://172.30.*:*",
            "http://172.31.*:*",
            "https://localhost:*",
            "https://127.0.0.1:*",
            "https://192.168.*:*",
            "https://10.*:*",
            "https://172.16.*:*",
            "https://172.17.*:*",
            "https://172.18.*:*",
            "https://172.19.*:*",
            "https://172.20.*:*",
            "https://172.21.*:*",
            "https://172.22.*:*",
            "https://172.23.*:*",
            "https://172.24.*:*",
            "https://172.25.*:*",
            "https://172.26.*:*",
            "https://172.27.*:*",
            "https://172.28.*:*",
            "https://172.29.*:*",
            "https://172.30.*:*",
            "https://172.31.*:*"
    };

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
        registration.setAllowedOriginPatterns(DEFAULT_DEV_ALLOWED_ORIGIN_PATTERNS);
    }
}

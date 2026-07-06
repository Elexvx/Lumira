package com.lumira.message.config;

import com.lumira.common.web.WebProperties;
import com.lumira.message.service.MessageSessionHandshakeInterceptor;
import com.lumira.message.service.MessageWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageWebSocketConfigTest {

    @Test
    void shouldUseConfiguredCorsAllowedOriginsWhenPresent() {
        WebProperties webProperties = new WebProperties();
        webProperties.setCorsAllowedOrigins(List.of("https://app.example.com"));
        WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);
        WebSocketHandlerRegistration registration = mock(WebSocketHandlerRegistration.class);
        when(registry.addHandler(any(), any(String[].class))).thenReturn(registration);
        when(registration.addInterceptors(any())).thenReturn(registration);
        MessageWebSocketConfig config = new MessageWebSocketConfig(
                mock(MessageWebSocketHandler.class),
                mock(MessageSessionHandshakeInterceptor.class),
                webProperties
        );

        config.registerWebSocketHandlers(registry);

        verify(registration).setAllowedOrigins("https://app.example.com");
        verify(registration, never()).setAllowedOriginPatterns("*");
    }

    @Test
    void shouldUseConfiguredCorsAllowedOriginPatternsWhenPresent() {
        WebProperties webProperties = new WebProperties();
        webProperties.setCorsAllowedOriginPatterns(List.of("https://*.example.com"));
        WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);
        WebSocketHandlerRegistration registration = mock(WebSocketHandlerRegistration.class);
        when(registry.addHandler(any(), any(String[].class))).thenReturn(registration);
        when(registration.addInterceptors(any())).thenReturn(registration);
        MessageWebSocketConfig config = new MessageWebSocketConfig(
                mock(MessageWebSocketHandler.class),
                mock(MessageSessionHandshakeInterceptor.class),
                webProperties
        );

        config.registerWebSocketHandlers(registry);

        verify(registration).setAllowedOriginPatterns("https://*.example.com");
    }

    @Test
    void shouldFallbackToTrustedDevelopmentOriginPatternsInsteadOfWildcard() {
        WebProperties webProperties = new WebProperties();
        WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);
        WebSocketHandlerRegistration registration = mock(WebSocketHandlerRegistration.class);
        when(registry.addHandler(any(), any(String[].class))).thenReturn(registration);
        when(registration.addInterceptors(any())).thenReturn(registration);
        MessageWebSocketConfig config = new MessageWebSocketConfig(
                mock(MessageWebSocketHandler.class),
                mock(MessageSessionHandshakeInterceptor.class),
                webProperties
        );

        config.registerWebSocketHandlers(registry);

        verify(registration).setAllowedOriginPatterns(
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
        );
        verify(registration, never()).setAllowedOriginPatterns("*");
    }
}

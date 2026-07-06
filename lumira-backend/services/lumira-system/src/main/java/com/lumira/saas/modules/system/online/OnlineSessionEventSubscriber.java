package com.lumira.saas.modules.system.online;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

@Component
public class OnlineSessionEventSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final OnlineSessionStreamService onlineSessionStreamService;
    private final OnlineSessionEventIdentityVerifier identityVerifier;

    public OnlineSessionEventSubscriber(
            ObjectMapper objectMapper,
            OnlineSessionStreamService onlineSessionStreamService,
            ObjectProvider<OnlineSessionEventIdentityVerifier> identityVerifierProvider
    ) {
        this.objectMapper = objectMapper;
        this.onlineSessionStreamService = onlineSessionStreamService;
        this.identityVerifier = identityVerifierProvider.getIfAvailable();
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        if (!StringUtils.hasText(payload)) {
            return;
        }

        try {
            OnlineSessionEventTrustValidator.requireTrustedSerializedEvent(payload);
            OnlineSessionEvent event = objectMapper.readValue(payload, OnlineSessionEvent.class);
            OnlineSessionEventTrustValidator.requireTrustedEvent(event);
            if (identityVerifier != null && !identityVerifier.hasTrustedIdentity(event)) {
                return;
            }
            onlineSessionStreamService.dispatch(event);
        } catch (Exception ignored) {
            // Ignore malformed pub/sub messages so the listener keeps running.
        }
    }
}

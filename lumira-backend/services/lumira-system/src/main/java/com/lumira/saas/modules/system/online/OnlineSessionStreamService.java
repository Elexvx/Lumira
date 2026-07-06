package com.lumira.saas.modules.system.online;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OnlineSessionStreamService {

    private static final long SSE_TIMEOUT_MILLIS = 0L;
    private static final String PERMISSION_VIEW = "system:online-user:view";
    private static final Duration DEFAULT_TRUST_REVALIDATION_INTERVAL = Duration.ofSeconds(30);

    private final ObjectMapper objectMapper;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final Clock clock;
    private final Duration trustRevalidationInterval;
    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> subscriberIdsBySessionId = new ConcurrentHashMap<>();
    private final Map<String, Instant> trustedAtBySubscriberId = new ConcurrentHashMap<>();

    public OnlineSessionStreamService(
            ObjectMapper objectMapper,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(objectMapper, sessionAuthenticationService, Clock.systemUTC(), DEFAULT_TRUST_REVALIDATION_INTERVAL);
    }

    OnlineSessionStreamService(
            ObjectMapper objectMapper,
            SessionAuthenticationService sessionAuthenticationService,
            Clock clock,
            Duration trustRevalidationInterval
    ) {
        this.objectMapper = objectMapper;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.clock = clock;
        this.trustRevalidationInterval = trustRevalidationInterval == null
                ? DEFAULT_TRUST_REVALIDATION_INTERVAL
                : trustRevalidationInterval;
    }

    public SseEmitter openStream(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)
                || currentUser.getPermissions() == null
                || (!currentUser.getPermissions().contains("*")
                && !currentUser.getPermissions().contains(PERMISSION_VIEW))) {
            throw new BizException(ErrorCode.FORBIDDEN, "Missing online session permission");
        }

        String sessionId;
        try {
            sessionId = OnlineSessionEventTrustValidator.requireTrustedSessionId(currentUser.getSessionId());
        } catch (IllegalArgumentException exception) {
            throw new BizException(ErrorCode.FORBIDDEN, "Invalid online session");
        }

        SessionAuthenticationService.AuthenticatedAccess authenticatedAccess;
        try {
            authenticatedAccess = sessionAuthenticationService.authenticateSessionTicket(
                    sessionId,
                    currentUser.getUserId(),
                    currentUser.getUserUuid().trim(),
                    normalizeSimulatedRoleId(currentUser.getSimulatedRoleId()),
                    currentUser.getSessionVersion(),
                    currentUser.getPermissionsVersion().trim()
            );
        } catch (RuntimeException exception) {
            throw new BizException(ErrorCode.FORBIDDEN, "Invalid online session");
        }
        CurrentUser trustedCurrentUser = authenticatedAccess == null ? null : authenticatedAccess.currentUser();
        if (!hasViewPermission(trustedCurrentUser)) {
            throw new BizException(ErrorCode.FORBIDDEN, "Missing online session permission");
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        String subscriberId = UUID.randomUUID().toString();
        Subscriber subscriber = new Subscriber(
                subscriberId,
                sessionId,
                trustedCurrentUser.getUserId(),
                trustedCurrentUser.getUserUuid().trim(),
                normalizeSimulatedRoleId(trustedCurrentUser.getSimulatedRoleId()),
                trustedCurrentUser.getSessionVersion(),
                trustedCurrentUser.getPermissionsVersion().trim(),
                emitter
        );

        subscribers.put(subscriberId, subscriber);
        subscriberIdsBySessionId.computeIfAbsent(sessionId, key -> ConcurrentHashMap.newKeySet()).add(subscriberId);
        trustedAtBySubscriberId.put(subscriberId, clock.instant());

        emitter.onCompletion(() -> removeSubscriber(subscriberId));
        emitter.onTimeout(() -> {
            try {
                emitter.complete();
            } finally {
                removeSubscriber(subscriberId);
            }
        });
        emitter.onError(throwable -> {
            try {
                emitter.completeWithError(throwable);
            } finally {
                removeSubscriber(subscriberId);
            }
        });

        try {
            emitter.send(SseEmitter.event()
                    .name("heartbeat")
                    .data(clock.instant().toString()));
        } catch (IOException | IllegalStateException exception) {
            removeSubscriber(subscriberId);
        }

        return emitter;
    }

    public void dispatch(OnlineSessionEvent event) {
        if (event == null) {
            return;
        }
        try {
            OnlineSessionEventTrustValidator.requireTrustedEvent(event);
        } catch (IllegalArgumentException exception) {
            return;
        }

        Collection<Subscriber> targets = new ArrayList<>(subscribers.values());
        for (Subscriber subscriber : targets) {
            if (!hasTrustedSubscription(subscriber)) {
                continue;
            }
            send(subscriber, event);
        }

        if (OnlineSessionEvent.ACTION_REMOVED.equals(event.getAction()) && event.getSessionId() != null) {
            closeSessionConnections(event.getSessionId());
        }
    }

    public void heartbeat() {
        for (Subscriber subscriber : subscribers.values()) {
            if (!hasTrustedSubscription(subscriber)) {
                continue;
            }
            try {
                subscriber.emitter().send(SseEmitter.event()
                        .name("heartbeat")
                        .data(clock.instant().toString()));
            } catch (IOException | IllegalStateException exception) {
                removeSubscriber(subscriber.subscriberId());
            }
        }
    }

    public void scheduledHeartbeat() {
        heartbeat();
    }

    public void closeSessionConnections(String sessionId) {
        String normalizedSessionId;
        try {
            normalizedSessionId = OnlineSessionEventTrustValidator.requireTrustedSessionId(sessionId);
        } catch (IllegalArgumentException exception) {
            return;
        }
        Set<String> subscriberIds = subscriberIdsBySessionId.remove(normalizedSessionId);
        if (subscriberIds == null || subscriberIds.isEmpty()) {
            return;
        }

        for (String subscriberId : subscriberIds) {
            Subscriber subscriber = subscribers.remove(subscriberId);
            trustedAtBySubscriberId.remove(subscriberId);
            if (subscriber != null) {
                subscriber.emitter().complete();
            }
        }
    }

    private void send(Subscriber subscriber, OnlineSessionEvent event) {
        try {
            subscriber.emitter().send(SseEmitter.event()
                    .name("session-change")
                    .data(serialize(event)));
        } catch (IOException | RuntimeException exception) {
            removeSubscriber(subscriber.subscriberId());
        }
    }

    private String serialize(OnlineSessionEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            OnlineSessionEventTrustValidator.requireTrustedSerializedEvent(payload);
            return payload;
        } catch (JsonProcessingException exception) {
            throw new RuntimeException("Failed to serialize online session event", exception);
        }
    }

    int subscriberCount() {
        return subscribers.size();
    }

    private boolean hasTrustedSubscription(Subscriber subscriber) {
        if (subscriber == null) {
            return false;
        }
        Instant now = clock.instant();
        if (!shouldRevalidateTrust(subscriber.subscriberId(), now)) {
            return true;
        }
        try {
            SessionAuthenticationService.AuthenticatedAccess authenticatedAccess =
                    sessionAuthenticationService.authenticateSessionTicket(
                            subscriber.sessionId(),
                            subscriber.userId(),
                            subscriber.userUuid(),
                            subscriber.simulatedRoleId(),
                            subscriber.sessionVersion(),
                            subscriber.permissionsVersion()
                    );
            if (!hasViewPermission(authenticatedAccess.currentUser())) {
                closeSubscriber(subscriber.subscriberId());
                return false;
            }
            trustedAtBySubscriberId.put(subscriber.subscriberId(), now);
            return true;
        } catch (RuntimeException exception) {
            closeSubscriber(subscriber.subscriberId());
            return false;
        }
    }

    private boolean shouldRevalidateTrust(String subscriberId, Instant now) {
        Instant trustedAt = trustedAtBySubscriberId.get(subscriberId);
        if (trustedAt == null) {
            return true;
        }
        return Duration.between(trustedAt, now).compareTo(trustRevalidationInterval) >= 0;
    }

    private boolean hasViewPermission(CurrentUser currentUser) {
        if (currentUser == null || currentUser.getPermissions() == null) {
            return false;
        }
        return currentUser.getPermissions().contains("*") || currentUser.getPermissions().contains(PERMISSION_VIEW);
    }

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
    }

    private void closeSubscriber(String subscriberId) {
        Subscriber subscriber = subscribers.get(subscriberId);
        removeSubscriber(subscriberId);
        if (subscriber != null) {
            subscriber.emitter().complete();
        }
    }

    private void removeSubscriber(String subscriberId) {
        Subscriber subscriber = subscribers.remove(subscriberId);
        trustedAtBySubscriberId.remove(subscriberId);
        if (subscriber == null) {
            return;
        }

        Set<String> sessionSubscribers = subscriberIdsBySessionId.get(subscriber.sessionId());
        if (sessionSubscribers != null) {
            sessionSubscribers.remove(subscriberId);
            if (sessionSubscribers.isEmpty()) {
                subscriberIdsBySessionId.remove(subscriber.sessionId());
            }
        }
    }

    private record Subscriber(
            String subscriberId,
            String sessionId,
            Long userId,
            String userUuid,
            Long simulatedRoleId,
            Integer sessionVersion,
            String permissionsVersion,
            SseEmitter emitter
    ) {
    }
}

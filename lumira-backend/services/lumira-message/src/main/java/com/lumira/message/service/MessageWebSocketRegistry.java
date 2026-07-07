package com.lumira.message.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.message.MessageEventDTO;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.message.infrastructure.security.MessageSessionAuthenticationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MessageWebSocketRegistry {

    private static final Logger logger = LoggerFactory.getLogger(MessageWebSocketRegistry.class);
    private static final Duration DEFAULT_TRUST_REVALIDATION_INTERVAL = Duration.ZERO;

    private final ObjectMapper objectMapper;
    private final MessageEventFactory messageEventFactory;
    private final MessageSessionAuthenticationService sessionAuthenticationService;
    private final Counter connectCounter;
    private final Counter disconnectCounter;
    private final Counter sendCounter;
    private final Counter sendFailureCounter;
    private final Counter heartbeatCounter;
    private final Clock clock;
    private final Duration trustRevalidationInterval;
    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> subscriberIdsByUserId = new ConcurrentHashMap<>();
    private final Map<String, Instant> trustedAtBySubscriberId = new ConcurrentHashMap<>();

    @Autowired
    public MessageWebSocketRegistry(
            ObjectMapper objectMapper,
            MessageEventFactory messageEventFactory,
            MeterRegistry meterRegistry,
            ObjectProvider<MessageSessionAuthenticationService> sessionAuthenticationServiceProvider
    ) {
        this(
                objectMapper,
                messageEventFactory,
                meterRegistry,
                sessionAuthenticationServiceProvider.getIfAvailable(),
                Clock.systemUTC(),
                DEFAULT_TRUST_REVALIDATION_INTERVAL
        );
    }

    MessageWebSocketRegistry(
            ObjectMapper objectMapper,
            MessageEventFactory messageEventFactory,
            MeterRegistry meterRegistry,
            MessageSessionAuthenticationService sessionAuthenticationService,
            Clock clock,
            Duration trustRevalidationInterval
    ) {
        this.objectMapper = objectMapper;
        this.messageEventFactory = messageEventFactory;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.clock = clock;
        this.trustRevalidationInterval = trustRevalidationInterval == null ? DEFAULT_TRUST_REVALIDATION_INTERVAL : trustRevalidationInterval;
        this.connectCounter = Counter.builder("message.websocket.connect.total").register(meterRegistry);
        this.disconnectCounter = Counter.builder("message.websocket.disconnect.total").register(meterRegistry);
        this.sendCounter = Counter.builder("message.websocket.send.total").register(meterRegistry);
        this.sendFailureCounter = Counter.builder("message.websocket.send.failure.total").register(meterRegistry);
        this.heartbeatCounter = Counter.builder("message.websocket.heartbeat.total").register(meterRegistry);
        Gauge.builder("message.websocket.connections.current", subscribers, Map::size)
                .description("Current active message websocket connections")
                .register(meterRegistry);
    }

    public CurrentUser register(WebSocketSession session, CurrentUser currentUser) {
        if (session == null) {
            return null;
        }
        CurrentUser trustedCurrentUser = authenticateTrustedCurrentUser(currentUser);
        if (trustedCurrentUser == null) {
            return null;
        }
        registerSubscriber(session, trustedCurrentUser);
        return trustedCurrentUser;
    }

    private void registerSubscriber(WebSocketSession session, CurrentUser currentUser) {
        String subscriberId = UUID.randomUUID().toString();
        Subscriber subscriber = new Subscriber(
                subscriberId,
                session,
                currentUser.getUserId(),
                normalizeText(currentUser.getUserUuid()),
                normalizeText(currentUser.getSessionId()),
                normalizeSimulatedRoleId(currentUser.getSimulatedRoleId()),
                currentUser.getSessionVersion(),
                normalizeText(currentUser.getPermissionsVersion()),
                LocalDateTime.now(clock)
        );
        subscribers.put(subscriberId, subscriber);
        subscriberIdsByUserId.computeIfAbsent(currentUser.getUserId(), key -> ConcurrentHashMap.newKeySet()).add(subscriberId);
        trustedAtBySubscriberId.put(subscriberId, clock.instant());
        connectCounter.increment();
        sendSafely(subscriber, messageEventFactory.toConnectedEvent(currentUser.getUserId(), subscriber.userUuid()), false);
    }

    public void unregister(WebSocketSession session) {
        if (session == null) {
            return;
        }

        Collection<String> subscriberIds = new ArrayList<>();
        for (Map.Entry<String, Subscriber> entry : subscribers.entrySet()) {
            if (entry.getValue().session().getId().equals(session.getId())) {
                subscriberIds.add(entry.getKey());
            }
        }
        subscriberIds.forEach(this::removeSubscriber);
    }

    public void sendToAll(MessageEventDTO event) {
        if (event == null) {
            return;
        }
        for (Subscriber subscriber : subscribers.values()) {
            sendSafely(subscriber, event, true);
        }
    }

    public void sendToUser(Long userId, String userUuid, MessageEventDTO event) {
        if (!isValidUserId(userId) || normalizeText(userUuid) == null) {
            return;
        }
        dispatch(subscriberIdsByUserId.get(userId), event, userId, normalizeText(userUuid));
    }

    public void sendHeartbeat() {
        for (Subscriber subscriber : subscribers.values()) {
            heartbeatCounter.increment();
            sendSafely(subscriber, messageEventFactory.createHeartbeatEvent(subscriber.userId(), subscriber.userUuid()), true);
        }
    }

    public void scheduledHeartbeat() {
        sendHeartbeat();
    }

    public Snapshot snapshot() {
        List<UserConnectionCount> users = subscriberIdsByUserId.entrySet()
                .stream()
                .map(entry -> new UserConnectionCount(entry.getKey(), entry.getValue().size()))
                .sorted(Comparator.comparing(UserConnectionCount::connectionCount).reversed())
                .limit(20)
                .toList();
        LocalDateTime earliestConnectedAt = subscribers.values()
                .stream()
                .map(Subscriber::connectedAt)
                .min(LocalDateTime::compareTo)
                .orElse(null);
        return new Snapshot(
                subscribers.size(),
                subscriberIdsByUserId.size(),
                users,
                earliestConnectedAt,
                LocalDateTime.now()
        );
    }

    private void dispatch(Set<String> subscriberIds, MessageEventDTO event, Long userId, String expectedUserUuid) {
        if (subscriberIds == null || subscriberIds.isEmpty() || event == null) {
            return;
        }
        for (String subscriberId : subscriberIds) {
            Subscriber subscriber = subscribers.get(subscriberId);
            if (subscriber == null) {
                continue;
            }
            if (userId != null && !subscriber.userId().equals(userId)) {
                continue;
            }
            if (expectedUserUuid != null && !expectedUserUuid.equals(subscriber.userUuid())) {
                continue;
            }
            if (!eventMatchesSubscriberIdentity(event, subscriber)) {
                continue;
            }
            sendSafely(subscriber, event, true);
        }
    }

    private boolean eventMatchesSubscriberIdentity(MessageEventDTO event, Subscriber subscriber) {
        String eventUserUuid = normalizeText(event.getUserUuid());
        if (eventUserUuid == null) {
            return true;
        }
        return eventUserUuid.equals(subscriber.userUuid());
    }

    private void sendSafely(Subscriber subscriber, MessageEventDTO event, boolean revalidateTrust) {
        try {
            if (revalidateTrust && !isTrustedSubscriber(subscriber)) {
                closeAndRemoveSubscriber(subscriber);
                return;
            }
            if (subscriber.session().isOpen()) {
                subscriber.session().sendMessage(new TextMessage(serialize(event)));
                sendCounter.increment();
            } else {
                removeSubscriber(subscriber.subscriberId());
            }
        } catch (IOException | RuntimeException exception) {
            sendFailureCounter.increment();
            logger.debug("消息WebSocket发送失败: sessionId={}, message={}", subscriber.session().getId(), exception.getMessage());
            removeSubscriber(subscriber.subscriberId());
        }
    }

    private String serialize(MessageEventDTO event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new RuntimeException("消息事件序列化失败", exception);
        }
    }

    private void removeSubscriber(String subscriberId) {
        Subscriber subscriber = subscribers.remove(subscriberId);
        if (subscriber == null) {
            return;
        }
        disconnectCounter.increment();
        trustedAtBySubscriberId.remove(subscriberId);

        Set<String> userSubscribers = subscriberIdsByUserId.get(subscriber.userId());
        if (userSubscribers != null) {
            userSubscribers.remove(subscriberId);
            if (userSubscribers.isEmpty()) {
                subscriberIdsByUserId.remove(subscriber.userId());
            }
        }
    }

    private boolean isValidUserId(Long userId) {
        return userId != null && userId > 0;
    }

    private CurrentUser authenticateTrustedCurrentUser(CurrentUser currentUser) {
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            return null;
        }
        if (sessionAuthenticationService == null) {
            return null;
        }
        MessageSessionAuthenticationService.AuthenticatedAccess authenticatedAccess;
        try {
            authenticatedAccess = sessionAuthenticationService.authenticateSessionTicket(
                    currentUser.getSessionId(),
                    currentUser.getUserId(),
                    currentUser.getUserUuid(),
                    normalizeSimulatedRoleId(currentUser.getSimulatedRoleId()),
                    currentUser.getSessionVersion(),
                    currentUser.getPermissionsVersion()
            );
        } catch (RuntimeException exception) {
            return null;
        }
        CurrentUser trustedCurrentUser = authenticatedAccess == null ? null : authenticatedAccess.currentUser();
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(trustedCurrentUser) || !hasMessageViewPermission(trustedCurrentUser)) {
            return null;
        }
        return trustedCurrentUser;
    }

    private boolean isTrustedSubscriber(Subscriber subscriber) {
        if (subscriber == null) {
            return false;
        }
        Instant now = clock.instant();
        Instant trustedAt = trustedAtBySubscriberId.get(subscriber.subscriberId());
        if (trustedAt != null
                && !trustRevalidationInterval.isNegative()
                && !trustRevalidationInterval.isZero()
                && trustedAt.plus(trustRevalidationInterval).isAfter(now)) {
            return true;
        }
        CurrentUser trustedCurrentUser = authenticateTrustedSubscriber(subscriber);
        boolean trusted = AuthenticationTrustSupport.isTrustedCurrentUser(trustedCurrentUser)
                && hasMessageViewPermission(trustedCurrentUser);
        if (trusted) {
            trustedAtBySubscriberId.put(subscriber.subscriberId(), now);
        }
        return trusted;
    }

    private CurrentUser authenticateTrustedSubscriber(Subscriber subscriber) {
        if (subscriber == null) {
            return null;
        }
        if (sessionAuthenticationService == null) {
            return null;
        }
        MessageSessionAuthenticationService.AuthenticatedAccess authenticatedAccess;
        try {
            authenticatedAccess = sessionAuthenticationService.authenticateSessionTicket(
                    subscriber.sessionId(),
                    subscriber.userId(),
                    subscriber.userUuid(),
                    subscriber.simulatedRoleId(),
                    subscriber.sessionVersion(),
                    subscriber.permissionsVersion()
            );
        } catch (RuntimeException exception) {
            return null;
        }
        return authenticatedAccess == null ? null : authenticatedAccess.currentUser();
    }

    private boolean hasMessageViewPermission(CurrentUser currentUser) {
        if (currentUser == null || currentUser.getPermissions() == null) {
            return false;
        }
        return currentUser.getPermissions().contains("*")
                || currentUser.getPermissions().contains("message:message:view")
                || currentUser.getPermissions().contains("system:notification:view");
    }

    private void closeAndRemoveSubscriber(Subscriber subscriber) {
        if (subscriber == null) {
            return;
        }
        try {
            if (subscriber.session().isOpen()) {
                subscriber.session().close();
            }
        } catch (IOException | RuntimeException exception) {
            logger.debug("Failed to close stale websocket session {}", subscriber.session().getId(), exception);
        } finally {
            removeSubscriber(subscriber.subscriberId());
        }
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
    }

    private record Subscriber(
            String subscriberId,
            WebSocketSession session,
            Long userId,
            String userUuid,
            String sessionId,
            Long simulatedRoleId,
            Integer sessionVersion,
            String permissionsVersion,
            LocalDateTime connectedAt
    ) {
    }

    public record Snapshot(
            int activeConnections,
            int userCount,
            List<UserConnectionCount> topUsers,
            LocalDateTime earliestConnectedAt,
            LocalDateTime sampledAt
    ) {
    }

    public record UserConnectionCount(Long userId, int connectionCount) {
    }
}

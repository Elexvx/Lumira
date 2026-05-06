package com.legendary.invention.message.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legendary.invention.api.message.MessageEventDTO;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MessageWebSocketRegistry {

    private static final Logger logger = LoggerFactory.getLogger(MessageWebSocketRegistry.class);

    private final ObjectMapper objectMapper;
    private final MessageEventFactory messageEventFactory;
    private final Counter connectCounter;
    private final Counter disconnectCounter;
    private final Counter sendCounter;
    private final Counter sendFailureCounter;
    private final Counter heartbeatCounter;
    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> subscriberIdsByTenantId = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> subscriberIdsByUserId = new ConcurrentHashMap<>();

    public MessageWebSocketRegistry(
            ObjectMapper objectMapper,
            MessageEventFactory messageEventFactory,
            MeterRegistry meterRegistry
    ) {
        this.objectMapper = objectMapper;
        this.messageEventFactory = messageEventFactory;
        this.connectCounter = Counter.builder("message.websocket.connect.total").register(meterRegistry);
        this.disconnectCounter = Counter.builder("message.websocket.disconnect.total").register(meterRegistry);
        this.sendCounter = Counter.builder("message.websocket.send.total").register(meterRegistry);
        this.sendFailureCounter = Counter.builder("message.websocket.send.failure.total").register(meterRegistry);
        this.heartbeatCounter = Counter.builder("message.websocket.heartbeat.total").register(meterRegistry);
        Gauge.builder("message.websocket.connections.current", subscribers, Map::size)
                .description("Current active message websocket connections")
                .register(meterRegistry);
    }

    public void register(WebSocketSession session, Long tenantId, Long userId) {
        String subscriberId = UUID.randomUUID().toString();
        Subscriber subscriber = new Subscriber(subscriberId, session, tenantId, userId);
        subscribers.put(subscriberId, subscriber);
        subscriberIdsByTenantId.computeIfAbsent(tenantId, key -> ConcurrentHashMap.newKeySet()).add(subscriberId);
        subscriberIdsByUserId.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(subscriberId);
        connectCounter.increment();
        sendSafely(subscriber, messageEventFactory.toConnectedEvent(tenantId, userId));
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

    public void sendToTenant(Long tenantId, MessageEventDTO event) {
        dispatch(subscriberIdsByTenantId.get(tenantId), event, tenantId, null);
    }

    public void sendToUser(Long tenantId, Long userId, MessageEventDTO event) {
        dispatch(subscriberIdsByUserId.get(userId), event, tenantId, userId);
    }

    public void sendHeartbeat() {
        for (Subscriber subscriber : subscribers.values()) {
            heartbeatCounter.increment();
            sendSafely(subscriber, messageEventFactory.createHeartbeatEvent(subscriber.tenantId(), subscriber.userId()));
        }
    }

    public void scheduledHeartbeat() {
        sendHeartbeat();
    }

    private void dispatch(Set<String> subscriberIds, MessageEventDTO event, Long tenantId, Long userId) {
        if (subscriberIds == null || subscriberIds.isEmpty() || event == null) {
            return;
        }
        for (String subscriberId : subscriberIds) {
            Subscriber subscriber = subscribers.get(subscriberId);
            if (subscriber == null) {
                continue;
            }
            if (!subscriber.tenantId().equals(tenantId)) {
                continue;
            }
            if (userId != null && !subscriber.userId().equals(userId)) {
                continue;
            }
            sendSafely(subscriber, event);
        }
    }

    private void sendSafely(Subscriber subscriber, MessageEventDTO event) {
        try {
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

        Set<String> tenantSubscribers = subscriberIdsByTenantId.get(subscriber.tenantId());
        if (tenantSubscribers != null) {
            tenantSubscribers.remove(subscriberId);
            if (tenantSubscribers.isEmpty()) {
                subscriberIdsByTenantId.remove(subscriber.tenantId());
            }
        }

        Set<String> userSubscribers = subscriberIdsByUserId.get(subscriber.userId());
        if (userSubscribers != null) {
            userSubscribers.remove(subscriberId);
            if (userSubscribers.isEmpty()) {
                subscriberIdsByUserId.remove(subscriber.userId());
            }
        }
    }

    private record Subscriber(String subscriberId, WebSocketSession session, Long tenantId, Long userId) {
    }
}

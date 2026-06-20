package com.lumira.saas.modules.system.online;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
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

    private final ObjectMapper objectMapper;
    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> subscriberIdsBySessionId = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> subscriberIdsByTenantId = new ConcurrentHashMap<>();

    public OnlineSessionStreamService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SseEmitter openStream(CurrentUser currentUser) {
        if (currentUser == null || currentUser.getSessionId() == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "缺少在线会话上下文");
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        String subscriberId = UUID.randomUUID().toString();
        if (currentUser.getCurrentTenantId() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "租户上下文缺失");
        }
        Long tenantId = currentUser.getCurrentTenantId();
        Subscriber subscriber = new Subscriber(subscriberId, currentUser.getSessionId(), tenantId, emitter);

        subscribers.put(subscriberId, subscriber);
        subscriberIdsBySessionId.computeIfAbsent(currentUser.getSessionId(), key -> ConcurrentHashMap.newKeySet()).add(subscriberId);
        subscriberIdsByTenantId.computeIfAbsent(tenantId, key -> ConcurrentHashMap.newKeySet()).add(subscriberId);

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
                    .data(Instant.now().toString()));
        } catch (IOException | IllegalStateException exception) {
            removeSubscriber(subscriberId);
        }

        return emitter;
    }

    public void dispatch(OnlineSessionEvent event) {
        if (event == null) {
            return;
        }

        Collection<Subscriber> targets = new ArrayList<>();
        if (event.getTenantId() != null) {
            Set<String> subscriberIds = subscriberIdsByTenantId.get(event.getTenantId());
            if (subscriberIds != null) {
                subscriberIds.stream()
                        .map(subscribers::get)
                        .filter(subscriber -> subscriber != null)
                        .forEach(targets::add);
            }
        } else {
            targets.addAll(subscribers.values());
        }

        for (Subscriber subscriber : targets) {
            send(subscriber, event);
        }

        if (OnlineSessionEvent.ACTION_REMOVED.equals(event.getAction()) && event.getSessionId() != null) {
            closeSessionConnections(event.getSessionId());
        }
    }

    public void heartbeat() {
        for (Subscriber subscriber : subscribers.values()) {
            try {
                subscriber.emitter().send(SseEmitter.event()
                        .name("heartbeat")
                        .data(Instant.now().toString()));
            } catch (IOException | IllegalStateException exception) {
                removeSubscriber(subscriber.subscriberId());
            }
        }
    }

    public void scheduledHeartbeat() {
        heartbeat();
    }

    public void closeSessionConnections(String sessionId) {
        Set<String> subscriberIds = subscriberIdsBySessionId.remove(sessionId);
        if (subscriberIds == null || subscriberIds.isEmpty()) {
            return;
        }

        for (String subscriberId : subscriberIds) {
            Subscriber subscriber = subscribers.remove(subscriberId);
            if (subscriber != null) {
                Set<String> tenantSubscribers = subscriberIdsByTenantId.get(subscriber.tenantId());
                if (tenantSubscribers != null) {
                    tenantSubscribers.remove(subscriberId);
                    if (tenantSubscribers.isEmpty()) {
                        subscriberIdsByTenantId.remove(subscriber.tenantId());
                    }
                }
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
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new RuntimeException("在线会话事件序列化失败", exception);
        }
    }

    private void removeSubscriber(String subscriberId) {
        Subscriber subscriber = subscribers.remove(subscriberId);
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

        Set<String> tenantSubscribers = subscriberIdsByTenantId.get(subscriber.tenantId());
        if (tenantSubscribers != null) {
            tenantSubscribers.remove(subscriberId);
            if (tenantSubscribers.isEmpty()) {
                subscriberIdsByTenantId.remove(subscriber.tenantId());
            }
        }
    }

    private record Subscriber(String subscriberId, String sessionId, Long tenantId, SseEmitter emitter) {
    }
}

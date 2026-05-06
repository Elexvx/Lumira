package com.legendary.invention.message.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legendary.invention.common.web.TraceContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

@Service
public class PlatformEventOutboxService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PlatformEventOutboxService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void recordAfterCommit(
            String sourceType,
            String eventType,
            Long tenantId,
            Long userId,
            String eventKey,
            Object payload
    ) {
        Runnable recordAction = () -> record(sourceType, eventType, tenantId, userId, eventKey, payload);
        if (!TransactionSynchronizationManager.isSynchronizationActive() || !TransactionSynchronizationManager.isActualTransactionActive()) {
            recordAction.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                recordAction.run();
            }
        });
    }

    public void record(
            String sourceType,
            String eventType,
            Long tenantId,
            Long userId,
            String eventKey,
            Object payload
    ) {
        jdbcTemplate.update(
                """
                        insert into platform_event_outbox (
                            tenant_id, user_id, source_type, event_type, event_key, payload_json, dispatch_status,
                            retry_count, trace_id, request_id, created_by, created_at, updated_by, updated_at, deleted
                        ) values (?, ?, ?, ?, ?, ?, 'RECORDED', 0, ?, ?, ?, ?, ?, ?, 0)
                        """,
                tenantId,
                userId,
                sourceType,
                eventType,
                eventKey,
                serialize(payload),
                TraceContext.getTraceId(),
                TraceContext.getRequestId(),
                userId == null ? 0L : userId,
                LocalDateTime.now(),
                userId == null ? 0L : userId,
                LocalDateTime.now()
        );
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("平台事件 outbox payload 序列化失败", exception);
        }
    }
}

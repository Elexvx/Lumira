package com.lumira.file.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.web.TraceContext;
import com.lumira.file.mapper.FilePlatformEventOutboxMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

@Service("filePlatformEventOutboxService")
public class PlatformEventOutboxService {

    public static final String STATUS_RECORDED = "RECORDED";
    private static final Logger logger = LoggerFactory.getLogger(PlatformEventOutboxService.class);

    private final ObjectMapper objectMapper;
    private final FilePlatformEventOutboxMapper platformEventOutboxMapper;

    public PlatformEventOutboxService(ObjectMapper objectMapper, FilePlatformEventOutboxMapper platformEventOutboxMapper) {
        this.objectMapper = objectMapper;
        this.platformEventOutboxMapper = platformEventOutboxMapper;
    }

    public void recordAfterCommit(String sourceType, String eventType, Long tenantId, Long userId, String eventKey, Object payload) {
        Runnable recordAction = () -> record(sourceType, eventType, tenantId, userId, eventKey, payload);
        if (!TransactionSynchronizationManager.isSynchronizationActive() || !TransactionSynchronizationManager.isActualTransactionActive()) {
            recordAction.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    recordAction.run();
                } catch (RuntimeException exception) {
                    logger.warn("文件事件 outbox 记录失败: {}", exception.getMessage(), exception);
                }
            }
        });
    }

    public PlatformEventOutboxEntity record(String sourceType, String eventType, Long tenantId, Long userId, String eventKey, Object payload) {
        LocalDateTime now = LocalDateTime.now();
        PlatformEventOutboxEntity entity = new PlatformEventOutboxEntity();
        entity.setTenantId(tenantId);
        entity.setUserId(userId);
        entity.setSourceType(sourceType);
        entity.setEventType(eventType);
        entity.setEventKey(eventKey);
        entity.setPayloadJson(serialize(payload));
        entity.setDispatchStatus(STATUS_RECORDED);
        entity.setRetryCount(0);
        entity.setTraceId(TraceContext.getTraceId());
        entity.setRequestId(TraceContext.getRequestId());
        entity.setCreatedBy(userId == null ? 0L : userId);
        entity.setUpdatedBy(userId == null ? 0L : userId);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setDeleted(0);
        platformEventOutboxMapper.insert(entity);
        return entity;
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("文件事件 outbox payload 序列化失败", exception);
        }
    }
}

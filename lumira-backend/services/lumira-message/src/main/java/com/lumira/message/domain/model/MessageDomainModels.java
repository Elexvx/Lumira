package com.lumira.message.domain.model;

import com.lumira.domain.event.StandardDomainEvent;
import com.lumira.domain.model.AggregateRoot;
import com.lumira.domain.model.EntityId;
import com.lumira.domain.model.ReadModel;
import java.time.Instant;
import java.util.Map;

public final class MessageDomainModels {

    private MessageDomainModels() {
    }

    public static final class NoticeAggregate extends AggregateRoot<Long> {
        private String status;

        public NoticeAggregate(Long noticeId, String status) {
            super(EntityId.of(noticeId));
            this.status = status == null ? "PUBLISHED" : status;
        }

        public void markRead(Long userId, String userUuid) {
            if (userId == null || userId <= 0 || userUuid == null || userUuid.isBlank()) {
                throw new IllegalArgumentException("Trusted user identity is required when marking a notice as read");
            }
            java.util.LinkedHashMap<String, Object> attributes = new java.util.LinkedHashMap<>();
            attributes.put("userId", userId);
            attributes.put("userUuid", userUuid.trim());
            registerEvent(StandardDomainEvent.of(
                    "MESSAGE_NOTICE_READ",
                    "message.notice",
                    String.valueOf(id().value()),
                    attributes
            ));
        }

        public void archive() {
            if ("ARCHIVED".equals(status)) {
                return;
            }
            status = "ARCHIVED";
            registerEvent(StandardDomainEvent.of(
                    "MESSAGE_NOTICE_ARCHIVED",
                    "message.notice",
                    String.valueOf(id().value()),
                    Map.of("archivedAt", Instant.now().toString())
            ));
        }

        public void retract() {
            if ("RETRACTED".equals(status)) {
                return;
            }
            status = "RETRACTED";
            registerEvent(StandardDomainEvent.of(
                    "MESSAGE_NOTICE_RETRACTED",
                    "message.notice",
                    String.valueOf(id().value()),
                    Map.of("retractedAt", Instant.now().toString())
            ));
        }
    }

    public record NoticeListItemReadModel(
            Long noticeId,
            String title,
            String status,
            boolean read,
            Instant createdAt
    ) implements ReadModel {
    }
}

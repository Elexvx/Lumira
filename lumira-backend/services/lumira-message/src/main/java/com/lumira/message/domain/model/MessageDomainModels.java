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
        private final Long tenantId;
        private String status;

        public NoticeAggregate(Long noticeId, Long tenantId, String status) {
            super(EntityId.of(noticeId));
            this.tenantId = tenantId;
            this.status = status == null ? "PUBLISHED" : status;
        }

        public void markRead(Long userId) {
            registerEvent(StandardDomainEvent.of(
                    "MESSAGE_NOTICE_READ",
                    "message.notice",
                    String.valueOf(id().value()),
                    tenantId,
                    Map.of("userId", userId)
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
                    tenantId,
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
                    tenantId,
                    Map.of("retractedAt", Instant.now().toString())
            ));
        }
    }

    public record NoticeListItemReadModel(
            Long noticeId,
            Long tenantId,
            String title,
            String status,
            boolean read,
            Instant createdAt
    ) implements ReadModel {
    }
}

package com.lumira.message.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumira.message.domain.model.MessageDomainModels.NoticeAggregate;
import org.junit.jupiter.api.Test;

class MessageDomainModelsTest {

    @Test
    void noticeAggregateEmitsReadAndSingleArchiveEvent() {
        NoticeAggregate notice = new NoticeAggregate(30L, 1L, "PUBLISHED");

        notice.markRead(20L);
        notice.retract();
        notice.archive();
        notice.archive();

        assertThat(notice.domainEvents()).hasSize(3);
        assertThat(notice.domainEvents().get(0).eventType()).isEqualTo("MESSAGE_NOTICE_READ");
        assertThat(notice.domainEvents().get(1).eventType()).isEqualTo("MESSAGE_NOTICE_RETRACTED");
        assertThat(notice.domainEvents().get(2).eventType()).isEqualTo("MESSAGE_NOTICE_ARCHIVED");
    }
}

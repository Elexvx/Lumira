package com.lumira.message.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lumira.message.domain.model.MessageDomainModels.NoticeAggregate;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class MessageDomainModelsTest {

    @Test
    void noticeAggregateEmitsReadAndSingleArchiveEvent() {
        NoticeAggregate notice = new NoticeAggregate(30L, "PUBLISHED");

        notice.markRead(20L, " user-uuid-20 ");
        notice.retract();
        notice.archive();
        notice.archive();

        assertThat(notice.domainEvents()).hasSize(3);
        assertThat(notice.domainEvents().get(0).eventType()).isEqualTo("MESSAGE_NOTICE_READ");
        assertThat(notice.domainEvents().get(0).attributes())
                .containsEntry("userId", 20L)
                .containsEntry("userUuid", "user-uuid-20");
        assertThat(notice.domainEvents().get(1).eventType()).isEqualTo("MESSAGE_NOTICE_RETRACTED");
        assertThat(notice.domainEvents().get(2).eventType()).isEqualTo("MESSAGE_NOTICE_ARCHIVED");
    }

    @Test
    void noticeAggregateDoesNotExposeNumericOnlyReadIdentity() {
        NoticeAggregate notice = new NoticeAggregate(30L, "PUBLISHED");

        assertThat(Arrays.stream(NoticeAggregate.class.getMethods())
                .filter(method -> method.getDeclaringClass().equals(NoticeAggregate.class))
                .map(Method::toString)
                .filter(signature -> signature.contains("markRead(java.lang.Long)"))
                .toList())
                .isEmpty();
        assertThatThrownBy(() -> notice.markRead(20L, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Trusted user identity");
        assertThat(notice.domainEvents()).isEmpty();
    }
}

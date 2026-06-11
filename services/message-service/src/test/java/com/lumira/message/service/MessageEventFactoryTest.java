package com.lumira.message.service;

import com.lumira.message.vo.MessageVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MessageEventFactoryTest {

    private final MessageEventFactory factory = new MessageEventFactory();

    @Test
    void createCreatedEventShouldBuildUnifiedContract() {
        MessageVO.NoticeVO notice = buildNotice(1001L, 2001L, 3001L, "ROLE");

        var event = factory.createCreatedEvent(notice);

        assertThat(event.getEventCategory()).isEqualTo(MessageEventFactory.CATEGORY_BUSINESS);
        assertThat(event.getSourceType()).isEqualTo(MessageEventFactory.SOURCE_MESSAGE);
        assertThat(event.getEventType()).isEqualTo(MessageEventFactory.EVENT_CREATED);
        assertThat(event.getEventKey()).contains("NOTICE_CREATED", "1001", "9001");
        assertThat(event.getNotice()).isNotNull();
        assertThat(event.getNotice().getTitle()).isEqualTo("系统提醒");
        assertThat(event.getPayload()).containsEntry("message", "消息已发布");
        assertThat(event.getPayload()).containsEntry("targetRoleId", 3001L);
    }

    @Test
    void createSyncStateEventShouldIncludeSnapshotPayload() {
        var event = factory.createSyncStateEvent(1001L, 2001L, 8, 99L, 3);

        assertThat(event.getEventCategory()).isEqualTo(MessageEventFactory.CATEGORY_COMPENSATION);
        assertThat(event.getEventType()).isEqualTo(MessageEventFactory.EVENT_SYNC_STATE);
        assertThat(event.getVersion()).isEqualTo(99L);
        assertThat(event.getUnreadCount()).isEqualTo(8);
        assertThat(event.getPayload()).containsEntry("latestVersion", 99L);
        assertThat(event.getPayload()).containsEntry("unreadCount", 8);
        assertThat(event.getPayload()).containsEntry("sessionVersion", 3);
    }

    private MessageVO.NoticeVO buildNotice(Long tenantId, Long targetUserId, Long targetRoleId, String targetScope) {
        MessageVO.NoticeVO notice = new MessageVO.NoticeVO();
        notice.setId(9001L);
        notice.setTenantId(tenantId);
        notice.setMessageType("MESSAGE");
        notice.setTargetScope(targetScope);
        notice.setTargetUserId(targetUserId);
        notice.setTargetRoleId(targetRoleId);
        notice.setTitle("系统提醒");
        notice.setContent("内容");
        notice.setSourceType("MANUAL");
        notice.setPublishStatus("PUBLISHED");
        notice.setPublishedAt(LocalDateTime.now());
        notice.setCreatedAt(LocalDateTime.now());
        notice.setUpdatedAt(LocalDateTime.now());
        return notice;
    }
}

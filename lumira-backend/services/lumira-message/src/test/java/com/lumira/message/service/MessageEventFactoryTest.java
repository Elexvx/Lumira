package com.lumira.message.service;

import com.lumira.message.vo.MessageVO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageEventFactoryTest {

    private final MessageEventFactory factory = new MessageEventFactory();

    @Test
    void createCreatedEventShouldBuildUnifiedContract() {
        MessageVO.NoticeVO notice = buildNotice(2001L, 3001L, "ROLE");

        var event = factory.createCreatedEvent(notice);

        assertThat(event.getEventCategory()).isEqualTo(MessageEventFactory.CATEGORY_BUSINESS);
        assertThat(event.getSourceType()).isEqualTo(MessageEventFactory.SOURCE_MESSAGE);
        assertThat(event.getEventType()).isEqualTo(MessageEventFactory.EVENT_CREATED);
        assertThat(event.getUserId()).isNull();
        assertThat(event.getUserUuid()).isNull();
        assertThat(event.getEventKey()).contains("NOTICE_CREATED", "all", "9001");
        assertThat(event.getNotice()).isNotNull();
        assertThat(event.getNotice().getTitle()).isEqualTo("system reminder");
        assertThat(event.getPayload()).containsEntry("message", "message published");
        assertThat(event.getPayload()).containsEntry("targetUserId", 2001L);
        assertThat(event.getPayload()).containsEntry("targetUserUuid", "user-uuid-2001");
        assertThat(event.getPayload()).containsEntry("targetRoleId", 3001L);
    }

    @Test
    void createReadEventShouldRequireUserUuid() {
        assertThatThrownBy(() -> factory.createReadEvent(2001L, null, buildNotice(2001L, null, "USER"), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userUuid");
    }

    @Test
    void createReadEventShouldIncludeUserUuid() {
        var event = factory.createReadEvent(2001L, " user-uuid-2001 ", buildNotice(2001L, null, "USER"), 1);

        assertThat(event.getEventType()).isEqualTo(MessageEventFactory.EVENT_READ);
        assertThat(event.getUserId()).isEqualTo(2001L);
        assertThat(event.getUserUuid()).isEqualTo("user-uuid-2001");
        assertThat(event.getUnreadCount()).isEqualTo(1);
    }

    @Test
    void createUnreadCountEventShouldRequireUserUuid() {
        assertThatThrownBy(() -> factory.createUnreadCountEvent(2001L, null, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userUuid");
    }

    @Test
    void createUnreadCountEventShouldIncludeUserUuid() {
        var event = factory.createUnreadCountEvent(2001L, " user-uuid-2001 ", 3);

        assertThat(event.getEventType()).isEqualTo(MessageEventFactory.EVENT_UNREAD_COUNT);
        assertThat(event.getUserId()).isEqualTo(2001L);
        assertThat(event.getUserUuid()).isEqualTo("user-uuid-2001");
        assertThat(event.getUnreadCount()).isEqualTo(3);
    }

    @Test
    void createSyncStateEventShouldRequireUserUuid() {
        assertThatThrownBy(() -> factory.createSyncStateEvent(2001L, null, 8, 99L, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userUuid");
    }

    @Test
    void createSyncStateEventShouldIncludeSnapshotPayload() {
        var event = factory.createSyncStateEvent(2001L, " user-uuid-2001 ", 8, 99L, 3);

        assertThat(event.getEventCategory()).isEqualTo(MessageEventFactory.CATEGORY_COMPENSATION);
        assertThat(event.getEventType()).isEqualTo(MessageEventFactory.EVENT_SYNC_STATE);
        assertThat(event.getUserUuid()).isEqualTo("user-uuid-2001");
        assertThat(event.getVersion()).isEqualTo(99L);
        assertThat(event.getUnreadCount()).isEqualTo(8);
        assertThat(event.getPayload()).containsEntry("latestVersion", 99L);
        assertThat(event.getPayload()).containsEntry("unreadCount", 8);
        assertThat(event.getPayload()).containsEntry("sessionVersion", 3);
    }

    @Test
    void heartbeatAndConnectedEventsShouldNotExposeNumericOnlyUserIdOperations() {
        assertThat(Arrays.stream(MessageEventFactory.class.getMethods())
                .filter(method -> method.getDeclaringClass().equals(MessageEventFactory.class))
                .map(Method::toString)
                .filter(signature -> signature.contains("createHeartbeatEvent(java.lang.Long)")
                        || signature.contains("toConnectedEvent(java.lang.Long)")
                        || signature.contains("toHeartbeatEvent(java.lang.Long)"))
                .toList())
                .isEmpty();
    }

    private MessageVO.NoticeVO buildNotice(Long targetUserId, Long targetRoleId, String targetScope) {
        MessageVO.NoticeVO notice = new MessageVO.NoticeVO();
        notice.setId(9001L);
        notice.setMessageType("MESSAGE");
        notice.setTargetScope(targetScope);
        notice.setTargetUserId(targetUserId);
        notice.setTargetUserUuid(targetUserId == null ? null : "user-uuid-" + targetUserId);
        notice.setTargetRoleId(targetRoleId);
        notice.setTitle("system reminder");
        notice.setContent("content");
        notice.setSourceType("MANUAL");
        notice.setPublishStatus("PUBLISHED");
        notice.setPublishedAt(LocalDateTime.now());
        notice.setCreatedAt(LocalDateTime.now());
        notice.setUpdatedAt(LocalDateTime.now());
        return notice;
    }
}

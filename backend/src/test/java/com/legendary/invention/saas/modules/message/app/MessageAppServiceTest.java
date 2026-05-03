package com.legendary.invention.saas.modules.message.app;

import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.infrastructure.event.PlatformEventOutboxService;
import com.legendary.invention.saas.modules.audit.app.OperationAuditService;
import com.legendary.invention.saas.modules.message.dto.MessageDTO;
import com.legendary.invention.saas.modules.message.service.MessagePushService;
import com.legendary.invention.saas.modules.message.service.MessageWebSocketRegistry;
import com.legendary.invention.saas.modules.message.vo.MessageVO;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageAppServiceTest {

    @Test
    void createMessageShouldPersistAndPublishCreatedNotice() {
        StubJdbcTemplate jdbcTemplate = new StubJdbcTemplate(0L);
        CapturingMessagePushService messagePushService = new CapturingMessagePushService();
        MessageAppService service = new MessageAppService(
                jdbcTemplate,
                new OperationAuditService(null) {
                    @Override
                    public void log(
                            Long tenantId,
                            Long userId,
                            String username,
                            String moduleName,
                            String actionName,
                            String operationType,
                            String resultStatus,
                            String detailMessage
                    ) {
                    }
                },
                messagePushService
        );

        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(2001L);
        currentUser.setUsername("admin");
        currentUser.setCurrentTenantId(1001L);

        MessageDTO.MessageCreateRequest request = new MessageDTO.MessageCreateRequest();
        request.setTitle("手动发送测试");
        request.setContent("测试内容");
        request.setTargetScope("TENANT");

        jdbcTemplate.createdNotice = new MessageVO.NoticeVO();
        jdbcTemplate.createdNotice.setId(9001L);
        jdbcTemplate.createdNotice.setTenantId(1001L);
        jdbcTemplate.createdNotice.setMessageType("MESSAGE");
        jdbcTemplate.createdNotice.setTargetScope("TENANT");
        jdbcTemplate.createdNotice.setTitle("手动发送测试");
        jdbcTemplate.createdNotice.setContent("测试内容");
        jdbcTemplate.createdNotice.setSourceType("MANUAL");
        jdbcTemplate.createdNotice.setPublishStatus("PUBLISHED");

        MessageVO.NoticeVO notice = service.createMessage(currentUser, request);

        assertNotNull(notice);
        assertEquals("手动发送测试", notice.getTitle());
        assertEquals(1, jdbcTemplate.createdMessageCount.get());
        assertNotNull(messagePushService.lastCreatedNotice);
        assertEquals("手动发送测试", messagePushService.lastCreatedNotice.getTitle());
    }

    @Test
    void markAllReadShouldClearUnreadCountAndPublishLatestCount() {
        StubJdbcTemplate jdbcTemplate = new StubJdbcTemplate(3L);
        CapturingMessagePushService messagePushService = new CapturingMessagePushService();
        MessageAppService service = new MessageAppService(
                jdbcTemplate,
                new OperationAuditService(null) {
                },
                messagePushService
        );

        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(2001L);
        currentUser.setUsername("admin");
        currentUser.setCurrentTenantId(1001L);

        MessageVO.UnreadCountVO unreadCountVO = service.markAllRead(currentUser);

        assertNotNull(unreadCountVO);
        assertEquals(0L, unreadCountVO.getUnreadCount());
        assertTrue(jdbcTemplate.lastUpdateSql.contains("insert into msg_notice_read"));
        assertEquals(0L, jdbcTemplate.unreadCount.get());
        assertEquals(1001L, messagePushService.lastTenantId.get());
        assertEquals(2001L, messagePushService.lastUserId.get());
        assertEquals(0, messagePushService.lastUnreadCount.get());
    }

    @Test
    void listArchiveShouldQueryTenantWideNotUserVisibleOnly() {
        StubJdbcTemplate jdbcTemplate = new StubJdbcTemplate(0L);
        MessageAppService service = new MessageAppService(
                jdbcTemplate,
                new OperationAuditService(null) {
                },
                new CapturingMessagePushService()
        );

        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(2001L);
        currentUser.setUsername("admin");
        currentUser.setCurrentTenantId(1001L);

        MessageVO.NoticeVO notice = new MessageVO.NoticeVO();
        notice.setId(11L);
        notice.setTenantId(1001L);
        notice.setTitle("归档消息");
        notice.setContent("内容");
        notice.setMessageType("MESSAGE");
        notice.setTargetScope("TENANT");
        notice.setSourceType("MANUAL");
        notice.setPublishStatus("PUBLISHED");
        notice.setReadFlag(Boolean.TRUE);
        jdbcTemplate.archiveRecords = List.of(notice);
        jdbcTemplate.archiveCount = 1L;

        MessageDTO.MessageArchiveQueryRequest request = new MessageDTO.MessageArchiveQueryRequest();
        request.setKeyword("归档");
        request.setSourceType("MANUAL");
        request.setPublishStatus("PUBLISHED");
        request.setPageNo(1L);
        request.setPageSize(10L);

        var response = service.listArchive(currentUser, request);

        assertEquals(1L, response.getTotal());
        assertEquals(1, response.getRecords().size());
        assertTrue(jdbcTemplate.lastArchiveCountSql.contains("from msg_notice n"));
        assertTrue(jdbcTemplate.lastArchiveListSql.contains("from msg_notice n"));
        assertTrue(jdbcTemplate.lastArchiveListSql.contains("left join msg_notice_read"));
        assertTrue(jdbcTemplate.lastArchiveCountSql.contains("n.deleted = 0"));
        assertTrue(jdbcTemplate.lastArchiveCountSql.contains("n.title like ?"));
        assertTrue(jdbcTemplate.lastArchiveCountSql.contains("n.source_type = ?"));
        assertTrue(jdbcTemplate.lastArchiveCountSql.contains("n.publish_status = ?"));
    }

    private static final class StubJdbcTemplate extends JdbcTemplate {
        private final AtomicLong unreadCount;
        private final AtomicInteger createdMessageCount = new AtomicInteger();
        private String lastUpdateSql = "";
        private String lastArchiveCountSql = "";
        private String lastArchiveListSql = "";
        private long archiveCount = 0L;
        private List<MessageVO.NoticeVO> archiveRecords = List.of();
        private MessageVO.NoticeVO createdNotice;

        private StubJdbcTemplate(long unreadCount) {
            this.unreadCount = new AtomicLong(unreadCount);
        }

        @Override
        public int update(String sql, Object... args) {
            lastUpdateSql = sql;
            if (sql.contains("insert into msg_notice_read")) {
                int inserted = Math.toIntExact(unreadCount.get());
                unreadCount.set(0);
                return inserted;
            }
            return 1;
        }

        @Override
        public int update(PreparedStatementCreator psc, KeyHolder generatedKeyHolder) {
            lastUpdateSql = "insert into msg_notice";
            createdMessageCount.incrementAndGet();
            if (createdNotice == null) {
                createdNotice = new MessageVO.NoticeVO();
                createdNotice.setId(9001L);
                createdNotice.setTenantId(1001L);
                createdNotice.setMessageType("MESSAGE");
                createdNotice.setTargetScope("TENANT");
                createdNotice.setTitle("手动发送测试");
                createdNotice.setContent("测试内容");
                createdNotice.setSourceType("MANUAL");
                createdNotice.setPublishStatus("PUBLISHED");
            }
            generatedKeyHolder.getKeyList().add(Map.of("id", createdNotice.getId()));
            return 1;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (requiredType == Long.class) {
                if (sql.contains("not exists")) {
                    return requiredType.cast(unreadCount.get());
                }
                lastArchiveCountSql = sql;
                return requiredType.cast(archiveCount);
            }
            throw new IllegalStateException("Unexpected query type: " + requiredType);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            lastArchiveListSql = sql;
            if (sql.contains("limit 1") && createdNotice != null) {
                return (List<T>) List.of(createdNotice);
            }
            if (archiveRecords.isEmpty()) {
                return List.of();
            }
            return (List<T>) archiveRecords;
        }
    }

    private static final class CapturingMessagePushService extends MessagePushService {
        private final AtomicLong lastTenantId = new AtomicLong();
        private final AtomicLong lastUserId = new AtomicLong();
        private final AtomicInteger lastUnreadCount = new AtomicInteger(-1);
        private MessageVO.NoticeVO lastCreatedNotice;

        private CapturingMessagePushService() {
            super((MessageWebSocketRegistry) null, new NoopPlatformEventOutboxService());
        }

        @Override
        public void publishCreated(MessageVO.NoticeVO notice) {
            lastCreatedNotice = notice;
        }

        @Override
        public void publishUnreadCount(Long tenantId, Long userId, Integer unreadCount) {
            lastTenantId.set(tenantId == null ? 0 : tenantId);
            lastUserId.set(userId == null ? 0 : userId);
            lastUnreadCount.set(unreadCount == null ? -1 : unreadCount);
        }
    }

    private static final class NoopPlatformEventOutboxService extends PlatformEventOutboxService {
        private NoopPlatformEventOutboxService() {
            super(null, null);
        }

        @Override
        public void recordAfterCommit(
                String sourceType,
                String eventType,
                Long tenantId,
                Long userId,
                String eventKey,
                Object payload
        ) {
        }
    }
}

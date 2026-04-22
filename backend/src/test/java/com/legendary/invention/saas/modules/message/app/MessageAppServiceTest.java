package com.legendary.invention.saas.modules.message.app;

import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.modules.audit.app.OperationAuditService;
import com.legendary.invention.saas.modules.message.dto.MessageDTO;
import com.legendary.invention.saas.modules.message.service.MessagePushService;
import com.legendary.invention.saas.modules.message.service.MessageWebSocketRegistry;
import com.legendary.invention.saas.modules.message.vo.MessageVO;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageAppServiceTest {

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
        private String lastUpdateSql = "";
        private String lastArchiveCountSql = "";
        private String lastArchiveListSql = "";
        private long archiveCount = 0L;
        private List<MessageVO.NoticeVO> archiveRecords = List.of();

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

        private CapturingMessagePushService() {
            super((MessageWebSocketRegistry) null);
        }

        @Override
        public void publishUnreadCount(Long tenantId, Long userId, Integer unreadCount) {
            lastTenantId.set(tenantId == null ? 0 : tenantId);
            lastUserId.set(userId == null ? 0 : userId);
            lastUnreadCount.set(unreadCount == null ? -1 : unreadCount);
        }
    }
}

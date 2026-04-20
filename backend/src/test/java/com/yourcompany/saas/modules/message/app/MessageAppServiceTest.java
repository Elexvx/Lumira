package com.yourcompany.saas.modules.message.app;

import com.yourcompany.saas.infrastructure.security.CurrentUser;
import com.yourcompany.saas.modules.audit.app.OperationAuditService;
import com.yourcompany.saas.modules.message.service.MessagePushService;
import com.yourcompany.saas.modules.message.service.MessageWebSocketRegistry;
import com.yourcompany.saas.modules.message.vo.MessageVO;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

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

    private static final class StubJdbcTemplate extends JdbcTemplate {
        private final AtomicLong unreadCount;
        private String lastUpdateSql = "";

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
                return requiredType.cast(unreadCount.get());
            }
            throw new IllegalStateException("Unexpected query type: " + requiredType);
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

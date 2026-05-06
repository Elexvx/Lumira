package com.legendary.invention.message.service;

import com.legendary.invention.api.message.MessageEventDTO;
import com.legendary.invention.common.security.CurrentUser;
import com.legendary.invention.message.app.MessageAppService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MessageConnectionSnapshotService {

    private final JdbcTemplate jdbcTemplate;
    private final MessageEventFactory messageEventFactory;
    private final MessageEventDeliveryService messageEventDeliveryService;
    private final MessageAppService messageAppService;

    public MessageConnectionSnapshotService(
            JdbcTemplate jdbcTemplate,
            MessageEventFactory messageEventFactory,
            MessageEventDeliveryService messageEventDeliveryService,
            MessageAppService messageAppService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.messageEventFactory = messageEventFactory;
        this.messageEventDeliveryService = messageEventDeliveryService;
        this.messageAppService = messageAppService;
    }

    public void emitSnapshot(CurrentUser currentUser) {
        if (currentUser == null || currentUser.getCurrentTenantId() == null || currentUser.getUserId() == null) {
            return;
        }

        Integer unreadCount = messageAppService.countUnread(currentUser).intValue();
        Long latestVersion = latestVersion(currentUser.getCurrentTenantId());
        MessageEventDTO event = messageEventFactory.createSyncStateEvent(
                currentUser.getCurrentTenantId(),
                currentUser.getUserId(),
                unreadCount,
                latestVersion,
                currentUser.getSessionVersion()
        );
        messageEventDeliveryService.deliver(event);
    }

    private Long latestVersion(Long tenantId) {
        Long version = jdbcTemplate.queryForObject(
                """
                        select coalesce(max(id), 0)
                        from platform_event_outbox
                        where tenant_id = ?
                          and source_type = ?
                          and deleted = 0
                        """,
                Long.class,
                tenantId,
                MessageEventFactory.SOURCE_MESSAGE
        );
        return version == null ? 0L : version;
    }
}

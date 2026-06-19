package com.lumira.message.service;

import com.lumira.api.message.MessageEventDTO;
import com.lumira.common.constant.PlatformConstants;
import com.lumira.common.security.CurrentUser;
import com.lumira.message.app.MessageAppService;
import com.lumira.message.app.PlatformEventOutboxService;
import org.springframework.stereotype.Service;

@Service
public class MessageConnectionSnapshotService {

    private final MessageEventFactory messageEventFactory;
    private final MessageEventDeliveryService messageEventDeliveryService;
    private final MessageAppService messageAppService;
    private final PlatformEventOutboxService outboxService;

    public MessageConnectionSnapshotService(
            MessageEventFactory messageEventFactory,
            MessageEventDeliveryService messageEventDeliveryService,
            MessageAppService messageAppService,
            PlatformEventOutboxService outboxService
    ) {
        this.messageEventFactory = messageEventFactory;
        this.messageEventDeliveryService = messageEventDeliveryService;
        this.messageAppService = messageAppService;
        this.outboxService = outboxService;
    }

    public void emitSnapshot(CurrentUser currentUser) {
        if (currentUser == null || currentUser.getUserId() == null) {
            return;
        }

        Integer unreadCount = messageAppService.countUnread(currentUser).intValue();
        Long tenantId = PlatformConstants.PLATFORM_TENANT_ID;
        Long latestVersion = latestVersion(tenantId);
        MessageEventDTO event = messageEventFactory.createSyncStateEvent(
                tenantId,
                currentUser.getUserId(),
                unreadCount,
                latestVersion,
                currentUser.getSessionVersion()
        );
        messageEventDeliveryService.deliver(event);
    }

    private Long latestVersion(Long tenantId) {
        return outboxService.latestVersionForTenant(tenantId);
    }
}

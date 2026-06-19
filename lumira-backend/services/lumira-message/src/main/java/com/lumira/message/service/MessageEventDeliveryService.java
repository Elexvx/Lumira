package com.lumira.message.service;

import com.lumira.api.message.MessageEventDTO;
import com.lumira.api.message.MessageNoticeDTO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class MessageEventDeliveryService {

    private final MessageWebSocketRegistry messageWebSocketRegistry;
    private final MessageRecipientResolver messageRecipientResolver;

    public MessageEventDeliveryService(
            MessageWebSocketRegistry messageWebSocketRegistry,
            MessageRecipientResolver messageRecipientResolver
    ) {
        this.messageWebSocketRegistry = messageWebSocketRegistry;
        this.messageRecipientResolver = messageRecipientResolver;
    }

    public void deliver(MessageEventDTO event) {
        if (event == null) {
            return;
        }

        MessageNoticeDTO notice = event.getNotice();
        if (notice == null) {
            deliverByUserOrTenant(event, event.getTenantId(), event.getUserId());
            return;
        }

        if (!StringUtils.hasText(notice.getTargetScope()) || "TENANT".equalsIgnoreCase(notice.getTargetScope())) {
            messageWebSocketRegistry.sendToTenant(notice.getTenantId(), event);
            return;
        }

        if ("USER".equalsIgnoreCase(notice.getTargetScope()) && notice.getTargetUserId() != null) {
            messageWebSocketRegistry.sendToUser(notice.getTenantId(), notice.getTargetUserId(), event);
            return;
        }

        if ("ROLE".equalsIgnoreCase(notice.getTargetScope()) && notice.getTargetRoleId() != null) {
            List<Long> userIds = messageRecipientResolver.resolveRecipientUserIds(notice);
            if (userIds.isEmpty()) {
                return;
            }
            for (Long userId : userIds) {
                messageWebSocketRegistry.sendToUser(notice.getTenantId(), userId, event);
            }
            return;
        }

        messageWebSocketRegistry.sendToTenant(notice.getTenantId(), event);
    }

    private void deliverByUserOrTenant(MessageEventDTO event, Long tenantId, Long userId) {
        if (userId != null) {
            messageWebSocketRegistry.sendToUser(tenantId, userId, event);
            return;
        }
        messageWebSocketRegistry.sendToTenant(tenantId, event);
    }
}

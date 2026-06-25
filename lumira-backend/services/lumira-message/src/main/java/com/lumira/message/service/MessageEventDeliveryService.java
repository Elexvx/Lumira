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
            deliverByUserOrBroadcast(event, event.getUserId());
            return;
        }

        if (!StringUtils.hasText(notice.getTargetScope()) || isPlatformScope(notice.getTargetScope())) {
            messageWebSocketRegistry.sendToAll(event);
            return;
        }

        if ("USER".equalsIgnoreCase(notice.getTargetScope()) && notice.getTargetUserId() != null) {
            messageWebSocketRegistry.sendToUser(notice.getTargetUserId(), event);
            return;
        }

        if ("ROLE".equalsIgnoreCase(notice.getTargetScope()) && notice.getTargetRoleId() != null) {
            List<Long> userIds = messageRecipientResolver.resolveRecipientUserIds(notice);
            if (userIds.isEmpty()) {
                return;
            }
            for (Long userId : userIds) {
                messageWebSocketRegistry.sendToUser(userId, event);
            }
            return;
        }

        messageWebSocketRegistry.sendToAll(event);
    }

    private void deliverByUserOrBroadcast(MessageEventDTO event, Long userId) {
        if (userId != null) {
            messageWebSocketRegistry.sendToUser(userId, event);
            return;
        }
        messageWebSocketRegistry.sendToAll(event);
    }

    private boolean isPlatformScope(String targetScope) {
        return "PLATFORM".equalsIgnoreCase(targetScope);
    }
}

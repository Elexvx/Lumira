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
            deliverByUserOrBroadcast(event);
            return;
        }

        if (!StringUtils.hasText(notice.getTargetScope()) || isPlatformScope(notice.getTargetScope())) {
            messageWebSocketRegistry.sendToAll(event);
            return;
        }

        if ("USER".equalsIgnoreCase(notice.getTargetScope()) && notice.getTargetUserId() != null) {
            for (MessageRecipientResolver.Recipient recipient : messageRecipientResolver.resolveRecipients(notice)) {
                messageWebSocketRegistry.sendToUser(recipient.userId(), recipient.userUuid(), event);
            }
            return;
        }

        if ("ROLE".equalsIgnoreCase(notice.getTargetScope()) && notice.getTargetRoleId() != null) {
            List<MessageRecipientResolver.Recipient> recipients = messageRecipientResolver.resolveRecipients(notice);
            if (recipients.isEmpty()) {
                return;
            }
            for (MessageRecipientResolver.Recipient recipient : recipients) {
                messageWebSocketRegistry.sendToUser(recipient.userId(), recipient.userUuid(), event);
            }
            return;
        }

        return;
    }

    private void deliverByUserOrBroadcast(MessageEventDTO event) {
        if (event.getUserId() != null && StringUtils.hasText(event.getUserUuid())) {
            messageWebSocketRegistry.sendToUser(event.getUserId(), event.getUserUuid(), event);
            return;
        }
        if (event.getUserId() != null) {
            return;
        }
        messageWebSocketRegistry.sendToAll(event);
    }

    private boolean isPlatformScope(String targetScope) {
        return "PLATFORM".equalsIgnoreCase(targetScope);
    }
}

package com.lumira.message.service;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.message.MessageNoticeDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class MessageRecipientResolver {

    private static final String STATUS_ENABLED = "ENABLED";

    private final ObjectProvider<SystemInternalApi> systemInternalApi;

    public MessageRecipientResolver(ObjectProvider<SystemInternalApi> systemInternalApi) {
        this.systemInternalApi = systemInternalApi;
    }

    public List<Long> resolveRecipientUserIds(MessageNoticeDTO notice) {
        return resolveRecipients(notice).stream().map(Recipient::userId).toList();
    }

    public List<Recipient> resolveRecipients(MessageNoticeDTO notice) {
        if (notice == null) {
            return List.of();
        }
        String targetScope = notice.getTargetScope();
        if (!StringUtils.hasText(targetScope) || isPlatformScope(targetScope)) {
            return List.of();
        }
        if ("USER".equalsIgnoreCase(targetScope)) {
            if (notice.getTargetUserId() == null || !StringUtils.hasText(notice.getTargetUserUuid())) {
                return List.of();
            }
            String targetUserUuid = notice.getTargetUserUuid().trim();
            return identitiesByIds(List.of(notice.getTargetUserId())).stream()
                    .filter(recipient -> targetUserUuid.equals(recipient.userUuid()))
                    .toList();
        }
        if ("ROLE".equalsIgnoreCase(targetScope) && notice.getTargetRoleId() != null) {
            SystemInternalApi internalApi = systemInternalApi.getIfAvailable();
            if (internalApi == null) {
                return List.of();
            }
            return identities(internalApi.roleUserIdentities(notice.getTargetRoleId()));
        }
        return List.of();
    }

    private List<Recipient> identitiesByIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        SystemInternalApi internalApi = systemInternalApi.getIfAvailable();
        if (internalApi == null) {
            return List.of();
        }
        return identities(internalApi.userIdentitiesByIds(deduplicate(userIds)));
    }

    private List<Recipient> identities(List<SystemUserSnapshotDTO> users) {
        if (users == null || users.isEmpty()) {
            return List.of();
        }
        return users.stream()
                .filter(user -> user != null
                        && user.userId() != null
                        && user.userId() > 0
                        && StringUtils.hasText(user.userUuid())
                        && StringUtils.hasText(user.status())
                        && STATUS_ENABLED.equalsIgnoreCase(user.status().trim()))
                .map(user -> new Recipient(user.userId(), user.userUuid().trim()))
                .distinct()
                .toList();
    }

    private List<Long> deduplicate(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        Set<Long> distinct = new LinkedHashSet<>(userIds);
        return new ArrayList<>(distinct);
    }

    private boolean isPlatformScope(String targetScope) {
        return "PLATFORM".equalsIgnoreCase(targetScope);
    }

    public record Recipient(Long userId, String userUuid) {
    }
}

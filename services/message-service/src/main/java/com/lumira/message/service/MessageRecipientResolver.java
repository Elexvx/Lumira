package com.lumira.message.service;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.message.MessageNoticeDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class MessageRecipientResolver {

    private final SystemInternalApi systemInternalApi;

    public MessageRecipientResolver(SystemInternalApi systemInternalApi) {
        this.systemInternalApi = systemInternalApi;
    }

    public List<Long> resolveRecipientUserIds(MessageNoticeDTO notice) {
        if (notice == null) {
            return List.of();
        }
        String targetScope = notice.getTargetScope();
        if (!StringUtils.hasText(targetScope) || "TENANT".equalsIgnoreCase(targetScope)) {
            return List.of();
        }
        if ("USER".equalsIgnoreCase(targetScope)) {
            return notice.getTargetUserId() == null ? List.of() : List.of(notice.getTargetUserId());
        }
        if ("ROLE".equalsIgnoreCase(targetScope) && notice.getTargetRoleId() != null) {
            return deduplicate(systemInternalApi.userIdsByRole(notice.getTenantId(), notice.getTargetRoleId()));
        }
        return List.of();
    }

    private List<Long> deduplicate(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        Set<Long> distinct = new LinkedHashSet<>(userIds);
        return new ArrayList<>(distinct);
    }
}

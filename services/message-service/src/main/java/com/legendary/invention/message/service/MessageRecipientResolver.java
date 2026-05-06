package com.legendary.invention.message.service;

import com.legendary.invention.api.message.MessageNoticeDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class MessageRecipientResolver {

    private final JdbcTemplate jdbcTemplate;

    public MessageRecipientResolver(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
            List<Long> userIds = jdbcTemplate.queryForList(
                    """
                            select distinct ur.user_id
                            from sys_user_role ur
                            where ur.tenant_id = ?
                              and ur.role_id = ?
                              and ur.deleted = 0
                            """,
                    Long.class,
                    notice.getTenantId(),
                    notice.getTargetRoleId()
            );
            return deduplicate(userIds);
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

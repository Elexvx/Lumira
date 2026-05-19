package com.legendary.invention.message.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.legendary.invention.api.message.MessageNoticeDTO;
import com.legendary.invention.message.entity.SysUserRoleEntity;
import com.legendary.invention.message.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class MessageRecipientResolver {

    private final SysUserRoleMapper sysUserRoleMapper;

    public MessageRecipientResolver(SysUserRoleMapper sysUserRoleMapper) {
        this.sysUserRoleMapper = sysUserRoleMapper;
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
            List<Long> userIds = sysUserRoleMapper.selectList(new QueryWrapper<SysUserRoleEntity>()
                            .select("user_id")
                            .eq("tenant_id", notice.getTenantId())
                            .eq("role_id", notice.getTargetRoleId())
                            .eq("deleted", 0)
                            .groupBy("user_id"))
                    .stream()
                    .map(SysUserRoleEntity::getUserId)
                    .toList();
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

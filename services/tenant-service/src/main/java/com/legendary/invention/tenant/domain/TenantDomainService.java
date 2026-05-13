package com.legendary.invention.tenant.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.legendary.invention.api.tenant.MyTenantDTO;
import com.legendary.invention.api.tenant.TenantSummaryDTO;
import com.legendary.invention.api.tenant.TenantSwitchCheckDTO;
import com.legendary.invention.tenant.entity.SysUserTenantEntity;
import com.legendary.invention.tenant.entity.TenantInfoEntity;
import com.legendary.invention.tenant.mapper.SysUserTenantMapper;
import com.legendary.invention.tenant.mapper.TenantInfoMapper;
import com.legendary.invention.tenant.vo.TenantSummaryVO;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TenantDomainService {

    private static final Long PLATFORM_TENANT_ID = com.legendary.invention.common.constant.PlatformConstants.PLATFORM_TENANT_ID;

    private final SysUserTenantMapper sysUserTenantMapper;
    private final TenantInfoMapper tenantInfoMapper;

    public TenantDomainService(SysUserTenantMapper sysUserTenantMapper, TenantInfoMapper tenantInfoMapper) {
        this.sysUserTenantMapper = sysUserTenantMapper;
        this.tenantInfoMapper = tenantInfoMapper;
    }

    public List<UserTenantAccess> listUserTenantAccess(Long userId) {
        List<SysUserTenantEntity> relations = sysUserTenantMapper.selectList(
                new LambdaQueryWrapper<SysUserTenantEntity>()
                        .eq(SysUserTenantEntity::getUserId, userId)
                        .eq(SysUserTenantEntity::getDeleted, 0)
                        .eq(SysUserTenantEntity::getStatus, "ENABLED")
        );
        if (relations.isEmpty()) {
            return platformTenantAccess();
        }

        List<Long> tenantIds = relations.stream().map(SysUserTenantEntity::getTenantId).distinct().toList();
        Map<Long, TenantInfoEntity> tenantMap = tenantInfoMapper.selectList(new LambdaQueryWrapper<TenantInfoEntity>()
                        .in(TenantInfoEntity::getId, tenantIds)
                        .eq(TenantInfoEntity::getDeleted, 0)
                )
                .stream()
                .collect(Collectors.toMap(TenantInfoEntity::getId, Function.identity()));

        List<UserTenantAccess> accessList = relations.stream()
                .map(rel -> {
                    TenantInfoEntity tenant = tenantMap.get(rel.getTenantId());
                    if (tenant == null) {
                        return null;
                    }
                    UserTenantAccess access = new UserTenantAccess();
                    access.setTenantId(rel.getTenantId());
                    access.setDefault(rel.getIsDefault() != null && rel.getIsDefault() == 1);
                    access.setTenant(tenant);
                    return access;
                })
                .filter(item -> item != null)
                .toList();
        return accessList.isEmpty() ? platformTenantAccess() : accessList;
    }

    public List<MyTenantDTO> listVisibleTenants(Long userId) {
        return toMyTenantDTO(listUserTenantAccess(userId));
    }

    public Optional<TenantInfoEntity> findTenantById(Long tenantId) {
        if (tenantId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tenantInfoMapper.selectOne(
                new LambdaQueryWrapper<TenantInfoEntity>()
                        .eq(TenantInfoEntity::getId, tenantId)
                        .eq(TenantInfoEntity::getDeleted, 0)
                .last("limit 1")
        ));
    }

    public TenantSwitchCheckDTO validateTenantSwitch(Long userId, Long tenantId) {
        TenantInfoEntity tenant = findTenantById(tenantId).orElse(null);
        if (tenant == null) {
            return new TenantSwitchCheckDTO(tenantId, null, false, "租户不存在");
        }
        if (!"ENABLED".equalsIgnoreCase(tenant.getStatus())) {
            return new TenantSwitchCheckDTO(tenantId, toTenantSummaryDTO(tenant), false, "当前租户已停用");
        }
        if (!PLATFORM_TENANT_ID.equals(tenantId) && !isUserInTenant(userId, tenantId)) {
            return new TenantSwitchCheckDTO(tenantId, toTenantSummaryDTO(tenant), false, "当前账号未绑定该租户");
        }
        return new TenantSwitchCheckDTO(tenantId, toTenantSummaryDTO(tenant), true, "可以切换到该租户");
    }

    public boolean isUserInTenant(Long userId, Long tenantId) {
        if (PLATFORM_TENANT_ID.equals(tenantId)) {
            return true;
        }
        Long count = sysUserTenantMapper.selectCount(new LambdaQueryWrapper<SysUserTenantEntity>()
                .eq(SysUserTenantEntity::getUserId, userId)
                .eq(SysUserTenantEntity::getTenantId, tenantId)
                .eq(SysUserTenantEntity::getStatus, "ENABLED")
                .eq(SysUserTenantEntity::getDeleted, 0)
                .last("limit 1"));
        return count != null && count > 0;
    }

    private List<UserTenantAccess> platformTenantAccess() {
        return findTenantById(PLATFORM_TENANT_ID)
                .map(tenant -> {
                    UserTenantAccess access = new UserTenantAccess();
                    access.setTenantId(PLATFORM_TENANT_ID);
                    access.setDefault(true);
                    access.setTenant(tenant);
                    return List.of(access);
                })
                .orElseGet(Collections::emptyList);
    }

    public List<MyTenantDTO> toMyTenantDTO(List<UserTenantAccess> accessList) {
        return accessList.stream().map(access -> {
            MyTenantDTO dto = new MyTenantDTO();
            fillTenantSummary(dto, access.getTenant());
            dto.setIsDefault(access.isDefault());
            return dto;
        }).toList();
    }

    public TenantSummaryDTO toTenantSummaryDTO(TenantInfoEntity tenantInfo) {
        if (tenantInfo == null) {
            return null;
        }
        return new TenantSummaryDTO(
                tenantInfo.getId(),
                tenantInfo.getTenantCode(),
                tenantInfo.getTenantName(),
                tenantInfo.getTenantShortName(),
                tenantInfo.getStatus(),
                tenantInfo.getCreatedAt(),
                tenantInfo.getUpdatedAt()
        );
    }

    public TenantSummaryVO toTenantSummaryVO(TenantInfoEntity tenantInfo) {
        if (tenantInfo == null) {
            return null;
        }
        TenantSummaryVO vo = new TenantSummaryVO();
        fillTenantSummary(vo, tenantInfo);
        return vo;
    }

    private void fillTenantSummary(TenantSummaryVO vo, TenantInfoEntity tenantInfo) {
        vo.setTenantId(tenantInfo.getId());
        vo.setTenantCode(tenantInfo.getTenantCode());
        vo.setTenantName(tenantInfo.getTenantName());
        vo.setTenantShortName(tenantInfo.getTenantShortName());
        vo.setStatus(tenantInfo.getStatus());
        vo.setCreatedAt(tenantInfo.getCreatedAt());
        vo.setUpdatedAt(tenantInfo.getUpdatedAt());
    }

    private void fillTenantSummary(MyTenantDTO vo, TenantInfoEntity tenantInfo) {
        vo.setTenantId(tenantInfo.getId());
        vo.setTenantCode(tenantInfo.getTenantCode());
        vo.setTenantName(tenantInfo.getTenantName());
        vo.setTenantShortName(tenantInfo.getTenantShortName());
        vo.setStatus(tenantInfo.getStatus());
        vo.setCreatedAt(tenantInfo.getCreatedAt());
        vo.setUpdatedAt(tenantInfo.getUpdatedAt());
    }
}

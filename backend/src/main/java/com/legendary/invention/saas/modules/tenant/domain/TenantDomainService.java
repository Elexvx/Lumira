package com.legendary.invention.saas.modules.tenant.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.legendary.invention.saas.modules.tenant.entity.SysUserTenantEntity;
import com.legendary.invention.saas.modules.tenant.entity.TenantInfoEntity;
import com.legendary.invention.saas.modules.tenant.mapper.SysUserTenantMapper;
import com.legendary.invention.saas.modules.tenant.mapper.TenantInfoMapper;
import com.legendary.invention.saas.modules.tenant.vo.MyTenantVO;
import com.legendary.invention.saas.modules.tenant.vo.TenantSummaryVO;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TenantDomainService {

    private static final Long PLATFORM_TENANT_ID = 1001L;

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

    public List<MyTenantVO> listVisibleTenants(Long userId) {
        return toMyTenantVO(listUserTenantAccess(userId));
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

    public List<MyTenantVO> toMyTenantVO(List<UserTenantAccess> accessList) {
        return accessList.stream()
                .map(access -> {
                    MyTenantVO vo = new MyTenantVO();
                    fillTenantSummary(vo, access.getTenant());
                    vo.setIsDefault(access.isDefault());
                    return vo;
                })
                .toList();
    }

    public TenantSummaryVO toTenantSummary(TenantInfoEntity tenantInfo) {
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
}

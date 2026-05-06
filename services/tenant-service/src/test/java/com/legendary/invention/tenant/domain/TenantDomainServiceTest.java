package com.legendary.invention.tenant.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.legendary.invention.api.tenant.TenantSwitchCheckDTO;
import com.legendary.invention.tenant.entity.SysUserTenantEntity;
import com.legendary.invention.tenant.entity.TenantInfoEntity;
import com.legendary.invention.tenant.mapper.SysUserTenantMapper;
import com.legendary.invention.tenant.mapper.TenantInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantDomainServiceTest {

    @Mock
    private SysUserTenantMapper sysUserTenantMapper;

    @Mock
    private TenantInfoMapper tenantInfoMapper;

    private TenantDomainService tenantDomainService;

    @BeforeEach
    void setUp() {
        tenantDomainService = new TenantDomainService(sysUserTenantMapper, tenantInfoMapper);
    }

    @Test
    void listUserTenantAccess_shouldFallbackToPlatformTenantWhenNoBindingExists() {
        when(sysUserTenantMapper.selectList(anyUserTenantQuery())).thenReturn(List.of());
        when(tenantInfoMapper.selectOne(anyTenantQuery())).thenReturn(platformTenant());

        List<UserTenantAccess> accessList = tenantDomainService.listUserTenantAccess(10086L);

        assertThat(accessList).hasSize(1);
        UserTenantAccess access = accessList.get(0);
        assertThat(access.getTenantId()).isEqualTo(1001L);
        assertThat(access.isDefault()).isTrue();
        assertThat(access.getTenant().getTenantCode()).isEqualTo("platform");
        verify(tenantInfoMapper).selectOne(anyTenantQuery());
    }

    @Test
    void listUserTenantAccess_shouldMapEnabledTenantBindings() {
        when(sysUserTenantMapper.selectList(anyUserTenantQuery())).thenReturn(List.of(
                relation(1001L, 100L, 1),
                relation(1002L, 100L, 0)
        ));
        when(tenantInfoMapper.selectList(anyTenantQuery())).thenReturn(List.of(
                tenant(1001L, "platform", "平台租户"),
                tenant(1002L, "tenant-2", "第二租户")
        ));

        List<UserTenantAccess> accessList = tenantDomainService.listUserTenantAccess(100L);

        assertThat(accessList).hasSize(2);
        assertThat(accessList).extracting(UserTenantAccess::getTenantId).containsExactly(1001L, 1002L);
        assertThat(accessList.get(0).isDefault()).isTrue();
        assertThat(accessList.get(1).isDefault()).isFalse();
        assertThat(accessList.get(1).getTenant().getTenantName()).isEqualTo("第二租户");
    }

    @Test
    void listVisibleTenants_shouldMapToTenantSummaries() {
        when(sysUserTenantMapper.selectList(anyUserTenantQuery())).thenReturn(List.of(
                relation(1001L, 100L, 1)
        ));
        when(tenantInfoMapper.selectList(anyTenantQuery())).thenReturn(List.of(
                tenant(1001L, "platform", "平台租户")
        ));

        assertThat(tenantDomainService.listVisibleTenants(100L))
                .hasSize(1)
                .first()
                .satisfies(tenant -> {
                    assertThat(tenant.getTenantId()).isEqualTo(1001L);
                    assertThat(tenant.getIsDefault()).isTrue();
                });
    }

    @Test
    void validateTenantSwitch_shouldRejectUnboundTenant() {
        when(tenantInfoMapper.selectOne(anyTenantQuery())).thenReturn(tenant(2002L, "tenant-2", "第二租户"));
        when(sysUserTenantMapper.selectCount(anyUserTenantQuery())).thenReturn(0L);

        TenantSwitchCheckDTO check = tenantDomainService.validateTenantSwitch(100L, 2002L);

        assertThat(check.allowed()).isFalse();
        assertThat(check.message()).isEqualTo("当前账号未绑定该租户");
    }

    @Test
    void isUserInTenant_shouldTreatPlatformTenantAsAlwaysAccessible() {
        assertThat(tenantDomainService.isUserInTenant(100L, 1001L)).isTrue();
        verify(sysUserTenantMapper, never()).selectCount(anyUserTenantQuery());
    }

    @Test
    void isUserInTenant_shouldCheckMapperForRegularTenant() {
        when(sysUserTenantMapper.selectCount(anyUserTenantQuery())).thenReturn(1L);

        assertThat(tenantDomainService.isUserInTenant(100L, 1002L)).isTrue();
        verify(sysUserTenantMapper).selectCount(anyUserTenantQuery());
    }

    private TenantInfoEntity platformTenant() {
        return tenant(1001L, "platform", "平台租户");
    }

    private TenantInfoEntity tenant(Long id, String code, String name) {
        TenantInfoEntity tenant = new TenantInfoEntity();
        tenant.setId(id);
        tenant.setTenantCode(code);
        tenant.setTenantName(name);
        tenant.setTenantShortName(name);
        tenant.setStatus("ENABLED");
        tenant.setCreatedAt(LocalDateTime.now());
        tenant.setUpdatedAt(LocalDateTime.now());
        tenant.setDeleted(0);
        return tenant;
    }

    private SysUserTenantEntity relation(Long tenantId, Long userId, int isDefault) {
        SysUserTenantEntity relation = new SysUserTenantEntity();
        relation.setTenantId(tenantId);
        relation.setUserId(userId);
        relation.setIsDefault(isDefault);
        relation.setStatus("ENABLED");
        relation.setDeleted(0);
        return relation;
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<SysUserTenantEntity> anyUserTenantQuery() {
        return (LambdaQueryWrapper<SysUserTenantEntity>) any(LambdaQueryWrapper.class);
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<TenantInfoEntity> anyTenantQuery() {
        return (LambdaQueryWrapper<TenantInfoEntity>) any(LambdaQueryWrapper.class);
    }
}

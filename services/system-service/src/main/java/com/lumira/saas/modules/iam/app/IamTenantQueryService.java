package com.lumira.saas.modules.iam.app;

import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class IamTenantQueryService {

    private static final Long PLATFORM_TENANT_ID = com.lumira.common.constant.PlatformConstants.PLATFORM_TENANT_ID;

    private final MyBatisQueryOperations jdbcTemplate;

    public IamTenantQueryService(MyBatisQueryOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TenantSnapshot> listCurrentUserTenants(CurrentUser currentUser) {
        Long userId = currentUser == null ? null : currentUser.getUserId();
        if (userId == null) {
            return List.of();
        }
        return jdbcTemplate.query(
                """
                        select t.id as id, t.tenant_code as tenantCode, t.tenant_name as tenantName,
                               t.status as tenantStatus, t.remark as remark,
                               ut.status as membershipStatus, ut.is_default as defaultTenant,
                               t.created_at as createdAt, t.updated_at as updatedAt
                        from sys_user_tenant ut
                        join sys_tenant t
                          on t.id = ut.tenant_id
                         and t.deleted = 0
                        where ut.user_id = ?
                          and ut.deleted = 0
                        order by ut.is_default desc, t.id asc
                        """,
                new BeanPropertyRowMapper<>(TenantSnapshot.class),
                userId
        );
    }

    public TenantSnapshot currentTenant(CurrentUser currentUser) {
        Long tenantId = currentUser == null || currentUser.getCurrentTenantId() == null
                ? PLATFORM_TENANT_ID
                : currentUser.getCurrentTenantId();
        TenantSnapshot snapshot = jdbcTemplate.queryForObject(
                """
                        select t.id as id, t.tenant_code as tenantCode, t.tenant_name as tenantName,
                               t.status as tenantStatus, t.remark as remark,
                               case when ut.status is null then t.status else ut.status end as membershipStatus,
                               case when ut.is_default is null then 0 else ut.is_default end as defaultTenant,
                               t.created_at as createdAt, t.updated_at as updatedAt
                        from sys_tenant t
                        left join sys_user_tenant ut
                          on ut.tenant_id = t.id
                         and ut.user_id = ?
                         and ut.deleted = 0
                        where t.id = ?
                          and t.deleted = 0
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(TenantSnapshot.class),
                currentUser == null ? null : currentUser.getUserId(),
                tenantId
        );
        if (snapshot != null) {
            return snapshot;
        }
        TenantSnapshot fallback = new TenantSnapshot();
        fallback.setId(tenantId);
        fallback.setTenantCode(String.valueOf(tenantId));
        fallback.setTenantName("Tenant " + tenantId);
        fallback.setTenantStatus("UNKNOWN");
        fallback.setMembershipStatus("UNKNOWN");
        fallback.setDefaultTenant(false);
        return fallback;
    }

    public static class TenantSnapshot {
        private Long id;
        private String tenantCode;
        private String tenantName;
        private String tenantStatus;
        private String membershipStatus;
        private Boolean defaultTenant;
        private String remark;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTenantCode() { return tenantCode; }
        public void setTenantCode(String tenantCode) { this.tenantCode = tenantCode; }
        public String getTenantName() { return tenantName; }
        public void setTenantName(String tenantName) { this.tenantName = tenantName; }
        public String getTenantStatus() { return tenantStatus; }
        public void setTenantStatus(String tenantStatus) { this.tenantStatus = tenantStatus; }
        public String getMembershipStatus() { return membershipStatus; }
        public void setMembershipStatus(String membershipStatus) { this.membershipStatus = membershipStatus; }
        public Boolean getDefaultTenant() { return defaultTenant; }
        public void setDefaultTenant(Boolean defaultTenant) { this.defaultTenant = defaultTenant; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }
}

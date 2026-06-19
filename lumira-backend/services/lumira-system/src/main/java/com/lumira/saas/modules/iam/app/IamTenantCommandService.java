package com.lumira.saas.modules.iam.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class IamTenantCommandService {

    private static final Long PLATFORM_TENANT_ID = com.lumira.common.constant.PlatformConstants.PLATFORM_TENANT_ID;

    private final MyBatisQueryOperations jdbcTemplate;
    private final IamTenantQueryService tenantQueryService;
    private final PermissionSnapshotService permissionSnapshotService;
    private final OperationAuditService operationAuditService;

    public IamTenantCommandService(
            MyBatisQueryOperations jdbcTemplate,
            IamTenantQueryService tenantQueryService,
            PermissionSnapshotService permissionSnapshotService,
            OperationAuditService operationAuditService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.tenantQueryService = tenantQueryService;
        this.permissionSnapshotService = permissionSnapshotService;
        this.operationAuditService = operationAuditService;
    }

    @Transactional
    public IamTenantQueryService.TenantSnapshot createTenant(CurrentUser currentUser, TenantUpsertRequest request) {
        Long operatorId = operatorId(currentUser);
        try {
            jdbcTemplate.update(
                    """
                            insert into sys_tenant (
                                tenant_code, tenant_name, status, remark, created_by, updated_by, deleted
                            ) values (?, ?, ?, ?, ?, ?, 0)
                            """,
                    normalizeCode(request.getTenantCode()),
                    normalizeName(request.getTenantName()),
                    normalizeStatus(request.getStatus()),
                    normalizeText(request.getRemark()),
                    operatorId,
                    operatorId
            );
        } catch (DuplicateKeyException exception) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "租户编码已存在，请更换后重试");
        }
        Long tenantId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        operationAuditService.log(currentTenantId(currentUser), operatorId, username(currentUser), "tenant", "create", "CREATE", "SUCCESS", "创建租户: " + request.getTenantName());
        return tenantQueryService.currentTenant(currentUserWithTenant(currentUser, tenantId));
    }

    @Transactional
    public IamTenantQueryService.TenantSnapshot updateTenant(CurrentUser currentUser, Long tenantId, TenantUpsertRequest request) {
        ensureTenantExists(tenantId);
        try {
            jdbcTemplate.update(
                    """
                            update sys_tenant
                            set tenant_code = ?, tenant_name = ?, status = ?, remark = ?,
                                updated_by = ?, updated_at = ?
                            where id = ? and deleted = 0
                            """,
                    normalizeCode(request.getTenantCode()),
                    normalizeName(request.getTenantName()),
                    normalizeStatus(request.getStatus()),
                    normalizeText(request.getRemark()),
                    operatorId(currentUser),
                    LocalDateTime.now(),
                    tenantId
            );
        } catch (DuplicateKeyException exception) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "租户编码已存在，请更换后重试");
        }
        permissionSnapshotService.invalidateTenant(tenantId);
        operationAuditService.log(currentTenantId(currentUser), operatorId(currentUser), username(currentUser), "tenant", "update", "UPDATE", "SUCCESS", "更新租户: " + tenantId);
        return tenantQueryService.currentTenant(currentUserWithTenant(currentUser, tenantId));
    }

    @Transactional
    public IamTenantQueryService.TenantSnapshot changeTenantStatus(CurrentUser currentUser, Long tenantId, TenantStatusRequest request) {
        ensureTenantExists(tenantId);
        String status = normalizeStatus(request.getStatus());
        ensureMutableStatus(tenantId, status);
        jdbcTemplate.update(
                """
                        update sys_tenant
                        set status = ?, updated_by = ?, updated_at = ?
                        where id = ? and deleted = 0
                        """,
                status,
                operatorId(currentUser),
                LocalDateTime.now(),
                tenantId
        );
        permissionSnapshotService.invalidateTenant(tenantId);
        operationAuditService.log(currentTenantId(currentUser), operatorId(currentUser), username(currentUser), "tenant", "status", "UPDATE", "SUCCESS", "更新租户状态: " + tenantId + " -> " + status);
        return tenantQueryService.currentTenant(currentUserWithTenant(currentUser, tenantId));
    }

    @Transactional
    public boolean archiveTenant(CurrentUser currentUser, Long tenantId) {
        ensureTenantExists(tenantId);
        if (PLATFORM_TENANT_ID.equals(tenantId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "平台默认租户不允许归档");
        }
        jdbcTemplate.update(
                """
                        update sys_tenant
                        set deleted = 1, updated_by = ?, updated_at = ?
                        where id = ? and deleted = 0
                        """,
                operatorId(currentUser),
                LocalDateTime.now(),
                tenantId
        );
        permissionSnapshotService.invalidateTenant(tenantId);
        operationAuditService.log(currentTenantId(currentUser), operatorId(currentUser), username(currentUser), "tenant", "archive", "DELETE", "SUCCESS", "归档租户: " + tenantId);
        return true;
    }

    @Transactional
    public IamTenantQueryService.TenantSnapshot upsertTenantMember(CurrentUser currentUser, Long tenantId, Long userId, TenantMemberRequest request) {
        ensureTenantExists(tenantId);
        ensureUserExists(userId);
        Long operatorId = operatorId(currentUser);
        boolean defaultTenant = Boolean.TRUE.equals(request.getDefaultTenant());
        if (defaultTenant) {
            jdbcTemplate.update(
                    "update sys_user_tenant set is_default = 0, updated_by = ?, updated_at = ? where user_id = ? and deleted = 0",
                    operatorId,
                    LocalDateTime.now(),
                    userId
            );
        }
        jdbcTemplate.update(
                """
                        insert into sys_user_tenant (tenant_id, user_id, is_default, status, created_by, updated_by, deleted)
                        values (?, ?, ?, ?, ?, ?, 0)
                        on duplicate key update is_default = values(is_default), status = values(status),
                                                 updated_by = values(updated_by), updated_at = current_timestamp, deleted = 0
                        """,
                tenantId,
                userId,
                defaultTenant ? 1 : 0,
                normalizeStatus(request.getStatus()),
                operatorId,
                operatorId
        );
        permissionSnapshotService.invalidateTenant(tenantId);
        operationAuditService.log(currentTenantId(currentUser), operatorId, username(currentUser), "tenant", "member-upsert", "UPDATE", "SUCCESS", "更新租户成员关系: tenant=" + tenantId + ", user=" + userId);
        return tenantQueryService.currentTenant(currentUserWithTenant(currentUser, tenantId));
    }

    private void ensureTenantExists(Long tenantId) {
        if (tenantId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "租户ID不能为空");
        }
        boolean exists = jdbcTemplate.exists(
                "select 1 from sys_tenant where id = ? and deleted = 0 limit 1",
                tenantId
        );
        if (!exists) {
            throw new BizException(ErrorCode.NOT_FOUND, "租户不存在");
        }
    }

    private void ensureUserExists(Long userId) {
        if (userId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "用户ID不能为空");
        }
        boolean exists = jdbcTemplate.exists(
                "select 1 from sys_user where id = ? and deleted = 0 limit 1",
                userId
        );
        if (!exists) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
    }

    private void ensureMutableStatus(Long tenantId, String status) {
        if (PLATFORM_TENANT_ID.equals(tenantId) && !"ENABLED".equals(status)) {
            throw new BizException(ErrorCode.FORBIDDEN, "平台默认租户不允许停用");
        }
    }

    private String normalizeCode(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "租户编码不能为空");
        }
        return value.trim();
    }

    private String normalizeName(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "租户名称不能为空");
        }
        return value.trim();
    }

    private String normalizeStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return "ENABLED";
        }
        String status = value.trim().toUpperCase();
        if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "租户状态只能是 ENABLED 或 DISABLED");
        }
        return status;
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Long operatorId(CurrentUser currentUser) {
        return currentUser == null || currentUser.getUserId() == null ? 0L : currentUser.getUserId();
    }

    private Long currentTenantId(CurrentUser currentUser) {
        return currentUser == null || currentUser.getCurrentTenantId() == null ? PLATFORM_TENANT_ID : currentUser.getCurrentTenantId();
    }

    private String username(CurrentUser currentUser) {
        return currentUser == null ? null : currentUser.getUsername();
    }

    private CurrentUser currentUserWithTenant(CurrentUser currentUser, Long tenantId) {
        CurrentUser target = new CurrentUser();
        if (currentUser != null) {
            target.setUserId(currentUser.getUserId());
            target.setUsername(currentUser.getUsername());
            target.setSessionId(currentUser.getSessionId());
            target.setSessionVersion(currentUser.getSessionVersion());
            target.setAuthenticated(currentUser.isAuthenticated());
            target.setPermissions(currentUser.getPermissions());
            target.setRoleIds(currentUser.getRoleIds());
            target.setPrimaryDeptId(currentUser.getPrimaryDeptId());
            target.setDeptIds(currentUser.getDeptIds());
            target.setDescendantDeptIds(currentUser.getDescendantDeptIds());
            target.setDataScopes(currentUser.getDataScopes());
            target.setSimulatedRoleId(currentUser.getSimulatedRoleId());
        }
        target.setCurrentTenantId(tenantId);
        return target;
    }

    public static class TenantUpsertRequest {
        @NotBlank
        private String tenantCode;
        @NotBlank
        private String tenantName;
        private String status;
        private String remark;

        public String getTenantCode() { return tenantCode; }
        public void setTenantCode(String tenantCode) { this.tenantCode = tenantCode; }
        public String getTenantName() { return tenantName; }
        public void setTenantName(String tenantName) { this.tenantName = tenantName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
    }

    public static class TenantStatusRequest {
        @NotBlank
        private String status;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class TenantMemberRequest {
        @NotBlank
        private String status;
        private Boolean defaultTenant;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Boolean getDefaultTenant() { return defaultTenant; }
        public void setDefaultTenant(Boolean defaultTenant) { this.defaultTenant = defaultTenant; }
    }
}

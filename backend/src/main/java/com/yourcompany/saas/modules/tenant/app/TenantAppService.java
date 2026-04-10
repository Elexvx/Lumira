package com.yourcompany.saas.modules.tenant.app;

import com.yourcompany.saas.common.enums.ErrorCode;
import com.yourcompany.saas.common.exception.BizException;
import com.yourcompany.saas.common.vo.PageResponse;
import com.yourcompany.saas.infrastructure.security.CurrentUser;
import com.yourcompany.saas.infrastructure.security.model.AuthSession;
import com.yourcompany.saas.infrastructure.security.service.AuthSessionStore;
import com.yourcompany.saas.infrastructure.security.service.JwtTokenService;
import com.yourcompany.saas.modules.audit.app.LoginAuditService;
import com.yourcompany.saas.modules.audit.app.OperationAuditService;
import com.yourcompany.saas.modules.iam.service.PermissionGuard;
import com.yourcompany.saas.modules.iam.service.PermissionSnapshotService;
import com.yourcompany.saas.modules.tenant.domain.TenantDomainService;
import com.yourcompany.saas.modules.tenant.dto.TenantDTO;
import com.yourcompany.saas.modules.tenant.entity.TenantInfoEntity;
import com.yourcompany.saas.modules.tenant.vo.CurrentTenantVO;
import com.yourcompany.saas.modules.tenant.vo.MyTenantVO;
import com.yourcompany.saas.modules.tenant.vo.SwitchTenantVO;
import com.yourcompany.saas.modules.tenant.vo.TenantSummaryVO;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class TenantAppService {

    private static final Long PLATFORM_TENANT_ID = 1001L;

    private final TenantDomainService tenantDomainService;
    private final JdbcTemplate jdbcTemplate;
    private final PermissionGuard permissionGuard;
    private final AuthSessionStore authSessionStore;
    private final JwtTokenService jwtTokenService;
    private final LoginAuditService loginAuditService;
    private final OperationAuditService operationAuditService;
    private final PermissionSnapshotService permissionSnapshotService;

    public TenantAppService(
            TenantDomainService tenantDomainService,
            JdbcTemplate jdbcTemplate,
            PermissionGuard permissionGuard,
            AuthSessionStore authSessionStore,
            JwtTokenService jwtTokenService,
            LoginAuditService loginAuditService,
            OperationAuditService operationAuditService,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this.tenantDomainService = tenantDomainService;
        this.jdbcTemplate = jdbcTemplate;
        this.permissionGuard = permissionGuard;
        this.authSessionStore = authSessionStore;
        this.jwtTokenService = jwtTokenService;
        this.loginAuditService = loginAuditService;
        this.operationAuditService = operationAuditService;
        this.permissionSnapshotService = permissionSnapshotService;
    }

    public CurrentTenantVO currentTenant(CurrentUser currentUser) {
        CurrentTenantVO response = new CurrentTenantVO();
        if (currentUser.getCurrentTenantId() == null) {
            response.setHasCurrentTenant(false);
            response.setCurrentTenant(null);
            return response;
        }

        TenantSummaryVO tenant = tenantDomainService.findTenantById(currentUser.getCurrentTenantId())
                .map(tenantDomainService::toTenantSummary)
                .orElse(null);

        response.setHasCurrentTenant(tenant != null);
        response.setCurrentTenant(tenant);
        return response;
    }

    public List<MyTenantVO> myTenants(CurrentUser currentUser) {
        return tenantDomainService.toMyTenantVO(tenantDomainService.listUserTenantAccess(currentUser.getUserId()));
    }

    public PageResponse<TenantSummaryVO> listTenants(CurrentUser currentUser, String tenantCode, String tenantName, String status, long pageNo, long pageSize) {
        requirePermission(currentUser, "tenant:view");

        String baseSql = """
                from tenant_info
                where deleted = 0
                """;
        List<Object> params = new ArrayList<>();
        if (StringUtils.hasText(tenantCode)) {
            baseSql += " and tenant_code like ?";
            params.add(like(tenantCode));
        }
        if (StringUtils.hasText(tenantName)) {
            baseSql += " and tenant_name like ?";
            params.add(like(tenantName));
        }
        if (StringUtils.hasText(status)) {
            baseSql += " and status = ?";
            params.add(status);
        }

        String selectSql = """
                select id as tenantId,
                       tenant_code as tenantCode,
                       tenant_name as tenantName,
                       tenant_short_name as tenantShortName,
                       status,
                       created_at as createdAt,
                       updated_at as updatedAt
                """ + baseSql + """
                order by id desc
                """;
        return pageQuery(selectSql, "select count(1) " + baseSql, TenantSummaryVO.class, pageNo, pageSize, params);
    }

    public TenantSummaryVO getTenant(CurrentUser currentUser, Long tenantId) {
        requirePermission(currentUser, "tenant:view");
        return tenantDomainService.findTenantById(tenantId)
                .map(tenantDomainService::toTenantSummary)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "租户不存在: " + tenantId));
    }

    @Transactional
    public TenantSummaryVO createTenant(CurrentUser currentUser, TenantDTO.TenantUpsertRequest request) {
        requirePermission(currentUser, "tenant:create");
        String tenantCode = normalizeCode(request.getTenantCode());
        assertTenantCodeAvailable(tenantCode, null);

        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        insert into tenant_info (
                            tenant_code, tenant_name, tenant_short_name, status,
                            created_by, created_at, updated_by, updated_at, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                tenantCode,
                normalizeText(request.getTenantName()),
                normalizeText(request.getTenantShortName()),
                request.getStatus(),
                currentUser.getUserId(),
                now,
                currentUser.getUserId(),
                now
        );

        TenantSummaryVO created = findTenantByCode(tenantCode)
                .map(tenantDomainService::toTenantSummary)
                .orElseThrow(() -> new BizException(ErrorCode.SYSTEM_ERROR, "租户创建后未能读取到新记录"));
        operationAuditService.log(resolveAuditTenantId(currentUser), currentUser.getUserId(), currentUser.getUsername(), "tenant", "create", "CREATE", "SUCCESS", "创建租户: " + tenantCode);
        return created;
    }

    @Transactional
    public TenantSummaryVO updateTenant(CurrentUser currentUser, Long tenantId, TenantDTO.TenantUpsertRequest request) {
        requirePermission(currentUser, "tenant:update");
        TenantInfoEntity existing = tenantDomainService.findTenantById(tenantId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "租户不存在: " + tenantId));
        String tenantCode = normalizeCode(request.getTenantCode());
        assertTenantCodeAvailable(tenantCode, tenantId);

        if (currentUser.getCurrentTenantId() != null
                && currentUser.getCurrentTenantId().equals(tenantId)
                && !"ENABLED".equalsIgnoreCase(request.getStatus())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "不能停用当前租户");
        }

        int affected = jdbcTemplate.update(
                """
                        update tenant_info
                        set tenant_code = ?,
                            tenant_name = ?,
                            tenant_short_name = ?,
                            status = ?,
                            updated_by = ?,
                            updated_at = ?
                        where id = ? and deleted = 0
                        """,
                tenantCode,
                normalizeText(request.getTenantName()),
                normalizeText(request.getTenantShortName()),
                request.getStatus(),
                currentUser.getUserId(),
                LocalDateTime.now(),
                tenantId
        );
        if (affected <= 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "租户不存在: " + tenantId);
        }

        operationAuditService.log(resolveAuditTenantId(currentUser), currentUser.getUserId(), currentUser.getUsername(), "tenant", "update", "UPDATE", "SUCCESS", "更新租户: " + existing.getTenantCode() + " -> " + tenantCode);
        return tenantDomainService.findTenantById(tenantId)
                .map(tenantDomainService::toTenantSummary)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "租户不存在: " + tenantId));
    }

    @Transactional
    public boolean deleteTenant(CurrentUser currentUser, Long tenantId) {
        requirePermission(currentUser, "tenant:delete");
        TenantInfoEntity tenant = tenantDomainService.findTenantById(tenantId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "租户不存在: " + tenantId));

        if (PLATFORM_TENANT_ID.equals(tenantId)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "默认租户不允许删除");
        }
        if (currentUser.getCurrentTenantId() != null && currentUser.getCurrentTenantId().equals(tenantId)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "不能删除当前租户");
        }

        int affected = jdbcTemplate.update(
                "update tenant_info set deleted = 1, updated_by = ?, updated_at = ? where id = ? and deleted = 0",
                currentUser.getUserId(),
                LocalDateTime.now(),
                tenantId
        );
        if (affected <= 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "租户不存在: " + tenantId);
        }

        operationAuditService.log(resolveAuditTenantId(currentUser), currentUser.getUserId(), currentUser.getUsername(), "tenant", "delete", "DELETE", "SUCCESS", "删除租户: " + tenant.getTenantCode());
        return true;
    }

    public SwitchTenantVO switchTenant(CurrentUser currentUser, Long targetTenantId, String loginIp, String userAgent) {
        if (!tenantDomainService.isUserInTenant(currentUser.getUserId(), targetTenantId)) {
            loginAuditService.log(currentUser.getUserId(), targetTenantId, currentUser.getUsername(), "TENANT_SWITCH", "FAIL", "用户不属于目标租户", loginIp, userAgent);
            throw new BizException(ErrorCode.FORBIDDEN, "无权切换到该租户");
        }

        TenantInfoEntity tenantInfo = tenantDomainService.findTenantById(targetTenantId)
                .orElseThrow(() -> new BizException(
                        ErrorCode.TENANT_ERROR,
                        "租户不存在: " + targetTenantId,
                        ErrorCode.TENANT_ERROR.getDefaultUserMessage()
                ));

        if (!"ENABLED".equalsIgnoreCase(tenantInfo.getStatus())) {
            throw new BizException(
                    ErrorCode.TENANT_ERROR,
                    "租户已停用: " + targetTenantId,
                    ErrorCode.TENANT_ERROR.getDefaultUserMessage()
            );
        }

        AuthSession session = authSessionStore.findBySessionId(currentUser.getSessionId())
                .orElseThrow(() -> new BizException(
                        ErrorCode.SESSION_EXPIRED,
                        "租户切换失败，会话已失效",
                        ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
                ));

        session.setCurrentTenantId(targetTenantId);
        session.setSessionVersion(session.getSessionVersion() == null ? 1 : session.getSessionVersion() + 1);

        var sessionTtl = jwtTokenService.calculateSessionTtl(session.getExpireTime());
        if (sessionTtl.isNegative() || sessionTtl.isZero()) {
            throw new BizException(
                    ErrorCode.SESSION_EXPIRED,
                    "租户切换失败，会话已过期",
                    ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
            );
        }

        authSessionStore.save(session, sessionTtl, true);

        SwitchTenantVO response = new SwitchTenantVO();
        response.setCurrentTenant(tenantDomainService.toTenantSummary(tenantInfo));
        response.setAccessToken(jwtTokenService.generateAccessToken(session));
        response.setTokenType("Bearer");
        response.setExpiresIn(jwtTokenService.getAccessTokenExpireSeconds());
        response.setSessionVersion(session.getSessionVersion());
        permissionSnapshotService.invalidateTenant(targetTenantId);
        response.setPermissionsVersion(permissionSnapshotService.loadSnapshot(targetTenantId, currentUser.getUserId()).getVersion());

        loginAuditService.log(currentUser.getUserId(), targetTenantId, currentUser.getUsername(), "TENANT_SWITCH", "SUCCESS", null, loginIp, userAgent);
        return response;
    }

    private void requirePermission(CurrentUser currentUser, String permissionKey) {
        permissionGuard.requirePermission(currentUser, permissionKey);
    }

    private PageResponse<TenantSummaryVO> pageQuery(String selectSql, String countSql, Class<TenantSummaryVO> voClass, long pageNo, long pageSize, List<Object> params) {
        long safePageNo = Math.max(pageNo, 1);
        long safePageSize = Math.max(pageSize, 1);
        long offset = (safePageNo - 1) * safePageSize;

        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safePageSize);
        queryParams.add(offset);

        List<TenantSummaryVO> records = jdbcTemplate.query(selectSql + " limit ? offset ?", new BeanPropertyRowMapper<>(voClass), queryParams.toArray());
        Long total = queryForCount(countSql, params);

        PageResponse<TenantSummaryVO> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total == null ? 0L : total);
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        return response;
    }

    private Long queryForCount(String countSql, List<Object> params) {
        try {
            return jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
        } catch (EmptyResultDataAccessException ex) {
            return 0L;
        }
    }

    private Optional<TenantInfoEntity> findTenantByCode(String tenantCode) {
        return Optional.ofNullable(jdbcTemplate.query(
                """
                        select id,
                               tenant_code as tenantCode,
                               tenant_name as tenantName,
                               tenant_short_name as tenantShortName,
                               status,
                               created_at as createdAt,
                               updated_at as updatedAt,
                               deleted
                        from tenant_info
                        where tenant_code = ? and deleted = 0
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(TenantInfoEntity.class),
                tenantCode
        ).stream().findFirst().orElse(null));
    }

    private void assertTenantCodeAvailable(String tenantCode, Long tenantId) {
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(1)
                        from tenant_info
                        where tenant_code = ?
                          and (? is null or id <> ?)
                        """,
                Long.class,
                tenantCode,
                tenantId,
                tenantId
        );
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "租户编码已存在: " + tenantCode);
        }
    }

    private Long resolveAuditTenantId(CurrentUser currentUser) {
        return currentUser.getCurrentTenantId() == null ? PLATFORM_TENANT_ID : currentUser.getCurrentTenantId();
    }

    private String normalizeCode(String value) {
        return normalizeText(value);
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim();
    }

    private String like(String value) {
        return "%" + value.trim() + "%";
    }
}

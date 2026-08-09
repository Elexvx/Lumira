package com.lumira.saas.modules.system.audit.infrastructure;

import com.lumira.api.ai.AiAuditReadPort;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.system.audit.repository.SystemAuditQueryRepository;
import com.lumira.saas.modules.system.audit.vo.AuditLogVO;
import com.lumira.saas.modules.system.vo.SystemVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

/** JDBC/MyBatis implementation of dashboard and audit query read models. */
@Repository
public class JdbcSystemAuditQueryRepository implements SystemAuditQueryRepository {
    private static final long MAX_PAGE_SIZE = 100L;
    private final MyBatisQueryOperations database;
    private final AiAuditReadPort aiAuditReadPort;

    public JdbcSystemAuditQueryRepository(MyBatisQueryOperations database) {
        this(database, null);
    }

    @Autowired
    public JdbcSystemAuditQueryRepository(MyBatisQueryOperations database, AiAuditReadPort aiAuditReadPort) {
        this.database = database;
        this.aiAuditReadPort = aiAuditReadPort;
    }

    @Override
    public PageResponse<SystemVO.AuditLogVO> findLoginLogs(LoginSearch search) {
        String where = " from audit_login_log l where 1 = 1";
        List<Object> params = new ArrayList<>();
        if (StringUtils.hasText(search.username())) {
            where += " and l.username like ?";
            params.add(like(search.username()));
        }
        if (StringUtils.hasText(search.loginType())) {
            where += " and l.login_type = ?";
            params.add(search.loginType());
        }
        if (search.start() != null) {
            where += " and l.created_at >= ?";
            params.add(search.start());
        }
        if (search.end() != null) {
            where += " and l.created_at <= ?";
            params.add(search.end());
        }
        return page(
                "select l.id, l.user_id as userId, l.username, l.login_type as logType, l.login_result as logResult, l.fail_reason as failReason, l.login_ip as loginIp, l.user_agent as userAgent, l.request_id as requestId, l.trace_id as traceId, l.created_at as createdAt"
                        + where + " order by l.id desc",
                "select count(1)" + where,
                search.pageNo(), search.pageSize(), params
        );
    }

    @Override
    public List<AuditLogVO> findSuccessfulLoginLogs(Long userId, long limit) {
        return new ArrayList<>(database.query(
                """
                        select l.id, l.user_id as userId, l.username, l.login_type as logType,
                               l.login_result as logResult, l.fail_reason as failReason, l.login_ip as loginIp,
                               l.user_agent as userAgent, l.request_id as requestId, l.trace_id as traceId, l.created_at as createdAt
                        from audit_login_log l
                        where l.user_id = ?
                          and l.login_result = 'SUCCESS'
                          and l.login_type <> 'LOGOUT'
                        order by l.created_at desc, l.id desc
                        limit ?
                        """,
                new BeanPropertyRowMapper<>(AuditLogVO.class), userId, safeLimit(limit)
        ));
    }

    @Override
    public List<AuditLogVO> findRecentOperationLogs(String username, long limit) {
        String where = " from audit_operation_log l where 1 = 1";
        List<Object> params = new ArrayList<>();
        if (StringUtils.hasText(username)) {
            where += " and l.username like ?";
            params.add(like(username));
        }
        params.add(safeLimit(limit));
        return new ArrayList<>(database.query(
                "select l.id, l.user_id as userId, l.username, l.module_name as moduleName, l.action_name as actionName, l.operation_type as operationType, l.result_status as logResult, l.detail_message as detailMessage, l.request_id as requestId, l.trace_id as traceId, l.created_at as createdAt"
                        + where + " order by l.created_at desc, l.id desc limit ?",
                new BeanPropertyRowMapper<>(AuditLogVO.class), params.toArray()
        ));
    }

    @Override
    public PageResponse<SystemVO.AuditLogVO> findOperationLogs(OperationSearch search) {
        String where = " from audit_operation_log l where 1 = 1";
        List<Object> params = new ArrayList<>();
        if (StringUtils.hasText(search.username())) {
            where += " and l.username like ?";
            params.add(like(search.username()));
        }
        if (search.start() != null) {
            where += " and l.created_at >= ?";
            params.add(search.start());
        }
        if (search.end() != null) {
            where += " and l.created_at <= ?";
            params.add(search.end());
        }
        return page(operationSelect() + where + " order by l.id desc", "select count(1)" + where, search.pageNo(), search.pageSize(), params);
    }

    @Override
    public PageResponse<SystemVO.AuditLogVO> findVerificationLogs(VerificationSearch search) {
        String where = " from audit_operation_log l where l.deleted = 0 and l.module_name = 'verification'";
        List<Object> params = new ArrayList<>();
        if (StringUtils.hasText(search.channel())) {
            where += " and l.operation_type = ?";
            params.add(search.channel());
        }
        if (StringUtils.hasText(search.scene())) {
            where += " and l.action_name = ?";
            params.add(search.scene());
        }
        if (StringUtils.hasText(search.resultStatus())) {
            where += " and l.result_status = ?";
            params.add(search.resultStatus());
        }
        if (search.start() != null) {
            where += " and l.created_at >= ?";
            params.add(search.start());
        }
        if (search.end() != null) {
            where += " and l.created_at <= ?";
            params.add(search.end());
        }
        return page(operationSelect() + where + " order by l.id desc", "select count(1)" + where, search.pageNo(), search.pageSize(), params);
    }

    @Override
    public PageResponse<SystemVO.AuditLogVO> findAiCallLogs(AiCallSearch search) {
        if (aiAuditReadPort == null) {
            throw new IllegalStateException("AI audit read port is required outside isolated legacy tests");
        }
        AiAuditReadPort.AiToolAuditPage page = aiAuditReadPort.findToolAudits(new AiAuditReadPort.AiToolAuditSearch(
                search.employeeId(),
                search.skillCode(),
                search.resultStatus(),
                search.start(),
                search.end(),
                search.pageNo(),
                search.pageSize()
        ));
        PageResponse<SystemVO.AuditLogVO> response = new PageResponse<>();
        response.setRecords(page.records().stream().map(this::toSystemAuditLog).toList());
        response.setTotal(page.total());
        response.setPageNo(page.pageNo());
        response.setPageSize(page.pageSize());
        return response;
    }

    private SystemVO.AuditLogVO toSystemAuditLog(AiAuditReadPort.AiToolAuditRecord source) {
        SystemVO.AuditLogVO target = new SystemVO.AuditLogVO();
        target.setId(source.id());
        target.setConversationId(source.conversationId());
        target.setEmployeeId(source.employeeId());
        target.setSkillCode(source.skillCode());
        target.setToolName(source.toolName());
        target.setPermissionMode(source.permissionMode());
        target.setConfirmRequired(source.confirmRequired());
        target.setConfirmResult(source.confirmResult());
        target.setLogResult(source.logResult());
        target.setDetailMessage(source.detailMessage());
        target.setRequestPayloadJson(source.requestPayloadJson());
        target.setResponsePayloadJson(source.responsePayloadJson());
        target.setCreatedAt(source.createdAt());
        target.setModuleName("AI");
        target.setActionName(source.toolName());
        target.setOperationType("CALL");
        return target;
    }

    private String operationSelect() {
        return """
                select l.id, l.user_id as userId, l.username, l.module_name as moduleName,
                       l.action_name as actionName, l.operation_type as operationType, l.result_status as logResult,
                       l.detail_message as detailMessage, l.request_id as requestId, l.trace_id as traceId,
                       l.created_at as createdAt
                """;
    }

    private PageResponse<SystemVO.AuditLogVO> page(String selectSql, String countSql, long pageNo, long pageSize, List<Object> params) {
        long safePageNo = pageNo <= 0 ? 1 : pageNo;
        long safePageSize = safeLimit(pageSize);
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safePageSize);
        queryParams.add((safePageNo - 1) * safePageSize);
        List<SystemVO.AuditLogVO> rows = database.query(
                selectSql + " limit ? offset ?", new BeanPropertyRowMapper<>(SystemVO.AuditLogVO.class), queryParams.toArray()
        );
        Long count = safePageNo == 1 && rows.size() < safePageSize
                ? (long) rows.size()
                : database.queryForObject(countSql, Long.class, params.toArray());
        PageResponse<SystemVO.AuditLogVO> response = new PageResponse<>();
        response.setRecords(rows);
        response.setTotal(count == null ? 0L : count);
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        return response;
    }

    private long safeLimit(long limit) {
        return Math.max(1L, Math.min(limit, MAX_PAGE_SIZE));
    }

    private String like(String value) {
        return "%" + value.trim() + "%";
    }
}

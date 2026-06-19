package com.lumira.ai.app;

import com.lumira.ai.vo.AiConversationVO;
import com.lumira.ai.vo.AiEmployeeVO;
import com.lumira.ai.vo.AiKnowledgeBaseVO;
import com.lumira.ai.vo.AiKnowledgeDocumentVO;
import com.lumira.ai.vo.AiMessageAttachmentVO;
import com.lumira.ai.vo.AiMessageVO;
import com.lumira.ai.vo.AiToolVO;
import com.lumira.ai.vo.PageResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AiReadQueryService {

    private static final long MAX_PAGE_SIZE = 100L;
    private static final int MAX_CONVERSATION_MESSAGES = 500;
    private static final String SCOPE_TENANT = "TENANT";

    private final JdbcTemplate jdbcTemplate;

    public AiReadQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResponse<AiEmployeeVO> listEmployees(CurrentUser currentUser, long pageNo, long pageSize) {
        Long tenantId = currentTenantId(currentUser);
        PageBounds bounds = pageBounds(pageNo, pageSize);
        List<AiEmployeeVO> records = jdbcTemplate.query(
                employeeSelect("""
                        where e.tenant_id = ?
                          and e.is_deleted = 0
                        order by e.sort_order asc, e.id desc
                        limit ? offset ?
                        """),
                this::mapEmployee,
                tenantId,
                bounds.limitPlusOne(),
                bounds.offset()
        );
        return cappedPage(records, bounds);
    }

    public AiEmployeeVO getAssistantEmployee(CurrentUser currentUser) {
        Long tenantId = currentTenantId(currentUser);
        return jdbcTemplate.query(
                employeeSelect("""
                        where e.tenant_id = ?
                          and e.is_deleted = 0
                          and e.enabled = 1
                        order by e.sort_order asc, e.id desc
                        limit 1
                        """),
                this::mapEmployee,
                tenantId
        ).stream().findFirst().orElse(null);
    }

    public PageResponse<AiConversationVO> listConversations(CurrentUser currentUser, Long employeeId, long pageNo, long pageSize) {
        Long tenantId = currentTenantId(currentUser);
        Long userId = currentUserId(currentUser);
        PageBounds bounds = pageBounds(pageNo, pageSize);
        List<AiConversationVO> records = jdbcTemplate.query(
                """
                        select c.id,
                               c.tenant_id,
                               c.employee_id,
                               c.owner_user_id,
                               coalesce(e.nickname, e.username, 'AI 助手') as employee_name,
                               c.conversation_code,
                               c.title,
                               c.status,
                               c.is_pinned,
                               (
                                   select m.content
                                   from ai_message m
                                   where m.tenant_id = c.tenant_id
                                     and m.conversation_id = c.id
                                     and m.is_deleted = 0
                                   order by m.id desc
                                   limit 1
                               ) as preview,
                               c.latest_message_at,
                               c.create_time,
                               c.update_time
                        from ai_conversation c
                        left join ai_employee e
                          on e.id = c.employee_id
                         and e.tenant_id = c.tenant_id
                         and e.is_deleted = 0
                        where c.tenant_id = ?
                          and c.owner_user_id = ?
                          and (? is null or c.employee_id = ?)
                          and c.is_deleted = 0
                        order by c.is_pinned desc, coalesce(c.latest_message_at, c.create_time) desc, c.id desc
                        limit ? offset ?
                        """,
                this::mapConversation,
                tenantId,
                userId,
                employeeId,
                employeeId,
                bounds.limitPlusOne(),
                bounds.offset()
        );
        return cappedPage(records, bounds);
    }

    public List<AiMessageVO> listConversationMessages(CurrentUser currentUser, Long conversationId) {
        Long tenantId = currentTenantId(currentUser);
        Long userId = currentUserId(currentUser);
        requireConversation(tenantId, userId, conversationId);
        List<AiMessageVO> messages = jdbcTemplate.query(
                """
                        select id, conversation_id, role, content, create_time
                        from ai_message
                        where tenant_id = ?
                          and conversation_id = ?
                          and is_deleted = 0
                        order by id asc
                        limit ?
                        """,
                this::mapMessage,
                tenantId,
                conversationId,
                MAX_CONVERSATION_MESSAGES
        );
        Map<Long, List<AiMessageAttachmentVO>> attachments = loadMessageAttachments(tenantId, conversationId);
        return messages.stream()
                .map(message -> message.withAttachments(attachments.getOrDefault(message.id(), List.of())))
                .toList();
    }

    public PageResponse<AiKnowledgeBaseVO> listKnowledgeBases(
            CurrentUser currentUser,
            String keyword,
            String status,
            String scope,
            long pageNo,
            long pageSize
    ) {
        Long tenantId = currentTenantId(currentUser);
        PageBounds bounds = pageBounds(pageNo, pageSize);
        StringBuilder where = new StringBuilder(" where kb.tenant_id = ? and kb.is_deleted = 0");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        appendAccessibleKnowledgeBaseFilter(where, args, currentUser, scope);
        if (StringUtils.hasText(keyword)) {
            where.append(" and (kb.name like ? or kb.description like ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (StringUtils.hasText(status)) {
            where.append(" and kb.status = ?");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }
        args.add(bounds.limitPlusOne());
        args.add(bounds.offset());
        List<AiKnowledgeBaseVO> records = jdbcTemplate.query(
                knowledgeBaseSelect(where.toString()) + """
                        order by kb.id desc
                        limit ? offset ?
                        """,
                this::mapKnowledgeBase,
                args.toArray()
        );
        return cappedPage(records, bounds);
    }

    public AiKnowledgeBaseVO getKnowledgeBase(CurrentUser currentUser, Long id) {
        Long tenantId = currentTenantId(currentUser);
        StringBuilder where = new StringBuilder(" where kb.tenant_id = ? and kb.id = ? and kb.is_deleted = 0");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.add(id);
        appendAccessibleKnowledgeBaseFilter(where, args, currentUser, null);
        return jdbcTemplate.query(knowledgeBaseSelect(where.toString()) + " limit 1", this::mapKnowledgeBase, args.toArray())
                .stream()
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "知识库不存在"));
    }

    public PageResponse<AiKnowledgeDocumentVO> listKnowledgeDocuments(CurrentUser currentUser, Long knowledgeBaseId, long pageNo, long pageSize) {
        Long tenantId = currentTenantId(currentUser);
        getKnowledgeBase(currentUser, knowledgeBaseId);
        PageBounds bounds = pageBounds(pageNo, pageSize);
        List<AiKnowledgeDocumentVO> records = jdbcTemplate.query(
                """
                        select id, tenant_id, knowledge_base_id, file_id, title, original_file_name, file_extension,
                               mime_type, file_size_bytes, status, parse_error, extracted_char_count, chunk_count,
                               created_by, create_time, update_time
                        from ai_knowledge_document
                        where tenant_id = ?
                          and knowledge_base_id = ?
                          and is_deleted = 0
                        order by id desc
                        limit ? offset ?
                        """,
                this::mapKnowledgeDocument,
                tenantId,
                knowledgeBaseId,
                bounds.limitPlusOne(),
                bounds.offset()
        );
        return cappedPage(records, bounds);
    }

    public List<AiToolVO> listTools(CurrentUser currentUser) {
        return nativeTools().stream()
                .sorted((left, right) -> left.toolCode().compareTo(right.toolCode()))
                .toList();
    }

    private String employeeSelect(String tail) {
        return """
                select e.id, e.tenant_id, e.username, e.nickname, e.position, e.avatar_key,
                       e.description, e.greeting, e.default_llm_service_id,
                       e.enabled, e.sort_order, e.create_time, e.update_time,
                       s.title as default_llm_service_title
                from ai_employee e
                left join ai_llm_service s
                  on s.id = e.default_llm_service_id
                 and s.tenant_id = e.tenant_id
                 and s.is_deleted = 0
                """ + tail;
    }

    private String knowledgeBaseSelect(String where) {
        return """
                select kb.id,
                       kb.tenant_id,
                       kb.kb_code,
                       kb.name,
                       kb.description,
                       kb.status,
                       kb.visibility_scope,
                       kb.owner_user_id,
                       kb.created_by,
                       kb.create_time,
                       kb.update_time,
                       count(distinct d.id) as document_count,
                       count(c.id) as chunk_count
                from ai_knowledge_base kb
                left join ai_knowledge_document d
                  on d.tenant_id = kb.tenant_id and d.knowledge_base_id = kb.id and d.is_deleted = 0
                left join ai_knowledge_chunk c
                  on c.tenant_id = kb.tenant_id and c.knowledge_base_id = kb.id and c.is_deleted = 0
                """ + where + """
                group by kb.id, kb.tenant_id, kb.kb_code, kb.name, kb.description, kb.status, kb.visibility_scope,
                         kb.owner_user_id, kb.created_by, kb.create_time, kb.update_time
                """;
    }

    private AiEmployeeVO mapEmployee(ResultSet rs, int rowNum) throws SQLException {
        AiEmployeeVO employee = new AiEmployeeVO();
        employee.setId(rs.getLong("id"));
        employee.setTenantId(rs.getLong("tenant_id"));
        employee.setUsername(rs.getString("username"));
        employee.setNickname(rs.getString("nickname"));
        employee.setPosition(rs.getString("position"));
        employee.setAvatarKey(rs.getString("avatar_key"));
        employee.setDescription(rs.getString("description"));
        employee.setGreeting(rs.getString("greeting"));
        long defaultLlmServiceId = rs.getLong("default_llm_service_id");
        employee.setDefaultLlmServiceId(rs.wasNull() ? null : defaultLlmServiceId);
        employee.setDefaultLlmServiceTitle(rs.getString("default_llm_service_title"));
        employee.setEnabled(rs.getBoolean("enabled"));
        employee.setSortOrder(rs.getInt("sort_order"));
        employee.setCreateTime(toLocalDateTime(rs, "create_time"));
        employee.setUpdateTime(toLocalDateTime(rs, "update_time"));
        return employee;
    }

    private AiConversationVO mapConversation(ResultSet rs, int rowNum) throws SQLException {
        return new AiConversationVO(
                rs.getLong("id"),
                rs.getLong("tenant_id"),
                rs.getLong("employee_id"),
                rs.getLong("owner_user_id"),
                rs.getString("employee_name"),
                rs.getString("conversation_code"),
                rs.getString("title"),
                rs.getString("preview"),
                rs.getString("status"),
                rs.getBoolean("is_pinned"),
                toLocalDateTime(rs, "latest_message_at"),
                toLocalDateTime(rs, "create_time"),
                toLocalDateTime(rs, "update_time")
        );
    }

    private AiMessageVO mapMessage(ResultSet rs, int rowNum) throws SQLException {
        return new AiMessageVO(
                rs.getLong("id"),
                rs.getLong("conversation_id"),
                rs.getString("role"),
                rs.getString("content"),
                List.of(),
                toLocalDateTime(rs, "create_time")
        );
    }

    private AiKnowledgeBaseVO mapKnowledgeBase(ResultSet rs, int rowNum) throws SQLException {
        return new AiKnowledgeBaseVO(
                rs.getLong("id"),
                rs.getLong("tenant_id"),
                rs.getString("kb_code"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("status"),
                rs.getString("visibility_scope"),
                objectLong(rs, "owner_user_id"),
                objectLong(rs, "document_count"),
                objectLong(rs, "chunk_count"),
                objectLong(rs, "created_by"),
                toLocalDateTime(rs, "create_time"),
                toLocalDateTime(rs, "update_time")
        );
    }

    private AiKnowledgeDocumentVO mapKnowledgeDocument(ResultSet rs, int rowNum) throws SQLException {
        return new AiKnowledgeDocumentVO(
                rs.getLong("id"),
                rs.getLong("tenant_id"),
                rs.getLong("knowledge_base_id"),
                objectLong(rs, "file_id"),
                rs.getString("title"),
                rs.getString("original_file_name"),
                rs.getString("file_extension"),
                rs.getString("mime_type"),
                objectLong(rs, "file_size_bytes"),
                rs.getString("status"),
                rs.getString("parse_error"),
                objectInt(rs, "extracted_char_count"),
                objectInt(rs, "chunk_count"),
                objectLong(rs, "created_by"),
                toLocalDateTime(rs, "create_time"),
                toLocalDateTime(rs, "update_time")
        );
    }

    private void requireConversation(Long tenantId, Long ownerUserId, Long conversationId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                        select 1
                        from ai_conversation
                        where tenant_id = ?
                          and owner_user_id = ?
                          and id = ?
                          and is_deleted = 0
                        limit 1
                        """,
                tenantId,
                ownerUserId,
                conversationId
        );
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在");
        }
    }

    private Map<Long, List<AiMessageAttachmentVO>> loadMessageAttachments(Long tenantId, Long conversationId) {
        Map<Long, List<AiMessageAttachmentVO>> attachmentMap = new LinkedHashMap<>();
        jdbcTemplate.query(
                """
                        select id,
                               file_id,
                               message_id,
                               original_file_name,
                               file_extension,
                               mime_type,
                               file_size_bytes,
                               concat(round(coalesce(file_size_bytes, 0) / 1024, 1), ' KB') as file_size_label,
                               public_url,
                               preview_url,
                               download_url,
                               preview_mode
                        from ai_message_attachment
                        where tenant_id = ?
                          and conversation_id = ?
                          and is_deleted = 0
                        order by id asc
                        """,
                (rs, rowNum) -> {
                    AiMessageAttachmentVO attachment = new AiMessageAttachmentVO(
                            rs.getLong("id"),
                            objectLong(rs, "file_id"),
                            rs.getString("original_file_name"),
                            rs.getString("file_extension"),
                            rs.getString("mime_type"),
                            objectLong(rs, "file_size_bytes"),
                            rs.getString("file_size_label"),
                            rs.getString("public_url"),
                            rs.getString("preview_url"),
                            rs.getString("download_url"),
                            rs.getString("preview_mode")
                    );
                    Long messageId = rs.getLong("message_id");
                    attachmentMap.computeIfAbsent(messageId, ignored -> new ArrayList<>()).add(attachment);
                    return attachment;
                },
                tenantId,
                conversationId
        );
        return attachmentMap;
    }

    private void appendAccessibleKnowledgeBaseFilter(StringBuilder where, List<Object> args, CurrentUser currentUser, String scope) {
        if (hasAllPermission(currentUser)) {
            appendScopeFilter(where, args, currentUser, scope);
            return;
        }
        String normalizedScope = StringUtils.hasText(scope) ? scope.trim().toUpperCase(Locale.ROOT) : null;
        if ("OWNED".equals(normalizedScope)) {
            where.append(" and kb.owner_user_id = ?");
            args.add(currentUserId(currentUser));
            return;
        }
        if (SCOPE_TENANT.equals(normalizedScope)) {
            where.append(" and kb.visibility_scope = ?");
            args.add(SCOPE_TENANT);
            return;
        }
        if ("SHARED".equals(normalizedScope)) {
            where.append(" and kb.owner_user_id <> ?");
            args.add(currentUserId(currentUser));
            where.append(" and ").append(buildAclExistsClause(currentUser, args));
            return;
        }

        where.append(" and (kb.owner_user_id = ?");
        args.add(currentUserId(currentUser));
        where.append(" or kb.visibility_scope = ?");
        args.add(SCOPE_TENANT);
        where.append(" or ").append(buildAclExistsClause(currentUser, args)).append(")");
    }

    private void appendScopeFilter(StringBuilder where, List<Object> args, CurrentUser currentUser, String scope) {
        if (!StringUtils.hasText(scope)) {
            return;
        }
        String normalizedScope = scope.trim().toUpperCase(Locale.ROOT);
        if ("OWNED".equals(normalizedScope)) {
            where.append(" and kb.owner_user_id = ?");
            args.add(currentUserId(currentUser));
        } else if ("SHARED".equals(normalizedScope)) {
            where.append(" and kb.owner_user_id <> ?");
            args.add(currentUserId(currentUser));
            where.append(" and ").append(buildAclExistsClause(currentUser, args));
        } else if (SCOPE_TENANT.equals(normalizedScope)) {
            where.append(" and kb.visibility_scope = ?");
            args.add(SCOPE_TENANT);
        }
    }

    private String buildAclExistsClause(CurrentUser currentUser, List<Object> args) {
        List<String> permissions = List.of("VIEW", "USE", "MANAGE");
        StringBuilder clause = new StringBuilder();
        clause.append("exists (select 1 from ai_knowledge_base_acl acl where acl.tenant_id = kb.tenant_id")
                .append(" and acl.knowledge_base_id = kb.id and acl.is_deleted = 0 and acl.permission in (")
                .append("?,".repeat(permissions.size()));
        clause.setLength(clause.length() - 1);
        clause.append(") and (");
        args.addAll(permissions);

        List<String> subjectClauses = new ArrayList<>();
        subjectClauses.add("(acl.subject_type = 'USER' and acl.subject_id = ?)");
        args.add(currentUserId(currentUser));
        Set<Long> roleIds = currentUser == null ? Set.of() : currentUser.getRoleIds();
        if (!roleIds.isEmpty()) {
            subjectClauses.add("(acl.subject_type = 'ROLE' and acl.subject_id in (" + "?,".repeat(roleIds.size()).replaceFirst(",$", "") + "))");
            args.addAll(roleIds);
        }
        Set<Long> deptIds = new LinkedHashSet<>(currentUser == null ? Set.of() : currentUser.getDeptIds());
        if (currentUser != null && currentUser.getPrimaryDeptId() != null) {
            deptIds.add(currentUser.getPrimaryDeptId());
        }
        if (!deptIds.isEmpty()) {
            subjectClauses.add("(acl.subject_type = 'DEPARTMENT' and acl.subject_id in (" + "?,".repeat(deptIds.size()).replaceFirst(",$", "") + "))");
            args.addAll(deptIds);
        }
        clause.append(String.join(" or ", subjectClauses)).append("))");
        return clause.toString();
    }

    private List<AiToolVO> nativeTools() {
        return List.of(
                tool("audit.ai_call.search", "检索 AI 工具审计", "audit", "按数字员工、技能编码和结果状态检索 AI 调用审计日志。", "MEDIUM", true, false, "audit:view"),
                tool("file.object.search", "检索文件对象", "file", "按关键词、类型和状态检索文件中心对象。", "MEDIUM", true, false, "system:file:view"),
                tool("system.config.read", "读取非敏感系统配置", "system", "按配置键读取非敏感平台配置。", "MEDIUM", true, false, "system:config:view"),
                tool("system.menu.list", "读取系统菜单与模块入口", "system", "按当前账号权限读取系统菜单、路由、权限键和状态。", "LOW", true, false, "system:menu:view"),
                tool("system.permission.snapshot", "读取当前权限上下文", "system", "返回当前登录用户、租户、角色、部门和权限集合。", "LOW", true, false, null),
                tool("system.user.create", "新增系统用户", "system", "在当前租户和当前账号权限范围内新增系统用户。", "HIGH", false, true, "system:user:create"),
                tool("system.user.search", "检索系统用户", "system", "按关键词和状态检索当前租户用户。", "MEDIUM", true, false, "system:user:view"),
                tool("system.user.update", "编辑系统用户", "system", "在当前租户和当前账号权限范围内编辑用户基础信息、角色和部门。", "HIGH", false, true, "system:user:update")
        );
    }

    private AiToolVO tool(
            String code,
            String name,
            String category,
            String description,
            String riskLevel,
            boolean readOnly,
            boolean needConfirm,
            String requiredPermission
    ) {
        return new AiToolVO(
                code,
                name,
                category,
                description,
                riskLevel,
                readOnly,
                needConfirm,
                requiredPermission,
                Map.of("type", "object", "properties", Map.of())
        );
    }

    private <T> PageResponse<T> cappedPage(List<T> records, PageBounds bounds) {
        boolean hasMore = records.size() > bounds.pageSize();
        List<T> boundedRecords = hasMore ? records.subList(0, Math.toIntExact(bounds.pageSize())) : records;
        PageResponse<T> response = new PageResponse<>();
        response.setRecords(boundedRecords);
        response.setPageNo(bounds.pageNo());
        response.setPageSize(bounds.pageSize());
        response.setHasMore(hasMore);
        response.setTotal(hasMore ? bounds.offset() + boundedRecords.size() + 1L : bounds.offset() + boundedRecords.size());
        return response;
    }

    private PageBounds pageBounds(long pageNo, long pageSize) {
        long safePageNo = Math.max(1L, pageNo);
        long safePageSize = Math.min(Math.max(1L, pageSize), MAX_PAGE_SIZE);
        return new PageBounds(safePageNo, safePageSize, (safePageNo - 1L) * safePageSize);
    }

    private Long currentTenantId(CurrentUser currentUser) {
        if (currentUser == null || currentUser.getCurrentTenantId() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Tenant context is required");
        }
        return currentUser.getCurrentTenantId();
    }

    private Long currentUserId(CurrentUser currentUser) {
        if (currentUser == null || currentUser.getUserId() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "User context is required");
        }
        return currentUser.getUserId();
    }

    private boolean hasAllPermission(CurrentUser currentUser) {
        return currentUser != null && currentUser.getPermissions().contains("*");
    }

    private Long objectLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Integer objectInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private LocalDateTime toLocalDateTime(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private record PageBounds(long pageNo, long pageSize, long offset) {
        long limitPlusOne() {
            return pageSize + 1L;
        }
    }
}

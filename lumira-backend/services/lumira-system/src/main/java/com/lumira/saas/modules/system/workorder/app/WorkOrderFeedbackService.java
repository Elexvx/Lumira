package com.lumira.saas.modules.system.workorder.app;

import com.lumira.api.client.FileInternalApi;
import com.lumira.api.file.FileObjectDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PlatformContext;
import com.lumira.common.vo.PageResponse;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.system.workorder.dto.WorkOrderFeedbackDTO;
import com.lumira.saas.modules.system.workorder.vo.WorkOrderFeedbackVO;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class WorkOrderFeedbackService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_DETAIL_HTML_LENGTH = 200_000;
    private static final String SUPPORT_FEEDBACK_BUCKET = "support_feedback";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MyBatisQueryOperations jdbcTemplate;
    private final WorkOrderFeedbackPluginStateService pluginStateService;
    private final FileInternalApi fileInternalApi;

    public WorkOrderFeedbackService(
            MyBatisQueryOperations jdbcTemplate,
            WorkOrderFeedbackPluginStateService pluginStateService,
            FileInternalApi fileInternalApi
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.pluginStateService = pluginStateService;
        this.fileInternalApi = fileInternalApi;
    }

    public PageResponse<WorkOrderFeedbackVO.WorkOrderRecord> list(
            CurrentUser currentUser,
            String keyword,
            String status,
            String priority,
            String scope,
            long pageNo,
            long pageSize
    ) {
        pluginStateService.ensureEnabled(currentUser);
        Long tenantId = tenantId();
        StringBuilder baseSql = new StringBuilder("""
                from sys_work_order_feedback
                where tenant_id = ?
                  and deleted = 0
                """);
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        if (!isAdminScope(scope)) {
            baseSql.append(" and submitter_id = ?");
            params.add(currentUser.getUserId());
        }
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim() + "%";
            baseSql.append(" and (title like ? or submitter_name like ?)");
            params.add(like);
            params.add(like);
        }
        if (StringUtils.hasText(status)) {
            baseSql.append(" and status = ?");
            params.add(normalizeStatus(status, false));
        }
        if (StringUtils.hasText(priority)) {
            baseSql.append(" and priority = ?");
            params.add(normalizePriority(priority));
        }
        String selectSql = """
                select id, tenant_id as tenantId, title, detail_html as detailHtml,
                       priority, status, submitter_id as submitterId, submitter_name as submitterName,
                       admin_reply as adminReply, handled_by as handledBy, handled_at as handledAt,
                       created_at as createdAt, updated_at as updatedAt
                """ + baseSql + " order by updated_at desc, id desc";
        return pageQuery(selectSql, "select count(1) " + baseSql, pageNo, pageSize, params);
    }

    public WorkOrderFeedbackVO.WorkOrderRecord detail(CurrentUser currentUser, Long id, String scope) {
        pluginStateService.ensureEnabled(currentUser);
        if (id == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "工单不存在");
        }
        String visibilitySql = isAdminScope(scope) ? "" : " and submitter_id = ?";
        List<Object> params = new ArrayList<>(List.of(id, tenantId()));
        if (!isAdminScope(scope)) {
            params.add(currentUser.getUserId());
        }
        WorkOrderFeedbackVO.WorkOrderRecord record = jdbcTemplate.queryForObject("""
                select id, tenant_id as tenantId, title, detail_html as detailHtml,
                       priority, status, submitter_id as submitterId, submitter_name as submitterName,
                       admin_reply as adminReply, handled_by as handledBy, handled_at as handledAt,
                       created_at as createdAt, updated_at as updatedAt
                from sys_work_order_feedback
                where id = ?
                  and tenant_id = ?
                  and deleted = 0
                """ + visibilitySql,
                new BeanPropertyRowMapper<>(WorkOrderFeedbackVO.WorkOrderRecord.class),
                params.toArray()
        );
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "工单不存在或无权查看");
        }
        formatDateFields(record);
        return record;
    }

    public FileObjectDTO uploadImage(CurrentUser currentUser, MultipartFile file) {
        pluginStateService.ensureEnabled(currentUser);
        try {
            return fileInternalApi.uploadImage(file, "工单反馈", "工单反馈富文本图片", SUPPORT_FEEDBACK_BUCKET);
        } catch (BizException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "图片上传失败，请检查存储空间配置或稍后重试");
        }
    }

    @Transactional
    public WorkOrderFeedbackVO.WorkOrderRecord create(CurrentUser currentUser, WorkOrderFeedbackDTO.CreateRequest request) {
        pluginStateService.ensureEnabled(currentUser);
        String title = normalizeRequiredText(request == null ? null : request.getTitle(), 160, "请填写工单标题");
        String detailHtml = normalizeRequiredText(request.getDetailHtml(), MAX_DETAIL_HTML_LENGTH, "请填写问题详情");
        String priority = normalizePriority(request.getPriority());
        jdbcTemplate.update("""
                insert into sys_work_order_feedback (
                    tenant_id, title, detail_html, priority, status, submitter_id, submitter_name,
                    created_by, created_at, updated_by, updated_at, deleted
                ) values (?, ?, ?, ?, 'OPEN', ?, ?, ?, now(), ?, now(), 0)
                """,
                tenantId(), title, detailHtml, priority, currentUser.getUserId(),
                displayName(currentUser), currentUser.getUserId(), currentUser.getUserId());
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        return detail(currentUser, id, "mine");
    }

    @Transactional
    public WorkOrderFeedbackVO.WorkOrderRecord updateStatus(CurrentUser currentUser, Long id, WorkOrderFeedbackDTO.StatusRequest request) {
        pluginStateService.ensureEnabled(currentUser);
        detail(currentUser, id, "admin");
        String status = normalizeStatus(request == null ? null : request.getStatus(), true);
        String adminReply = normalizeNullableText(request == null ? null : request.getAdminReply(), 4000);
        jdbcTemplate.update("""
                update sys_work_order_feedback
                   set status = ?,
                       admin_reply = ?,
                       handled_by = ?,
                       handled_at = case when ? in ('RESOLVED', 'CLOSED') then now() else handled_at end,
                       updated_by = ?,
                       updated_at = now()
                 where id = ?
                   and tenant_id = ?
                   and deleted = 0
                """,
                status, adminReply, currentUser.getUserId(), status, currentUser.getUserId(), id, tenantId());
        return detail(currentUser, id, "admin");
    }

    private PageResponse<WorkOrderFeedbackVO.WorkOrderRecord> pageQuery(String selectSql, String countSql, long pageNo, long pageSize, List<Object> params) {
        long safePageNo = pageNo <= 0 ? 1 : pageNo;
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        long offset = (safePageNo - 1L) * safePageSize;
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safePageSize);
        queryParams.add(offset);
        List<WorkOrderFeedbackVO.WorkOrderRecord> records = jdbcTemplate.query(
                selectSql + " limit ? offset ?",
                new BeanPropertyRowMapper<>(WorkOrderFeedbackVO.WorkOrderRecord.class),
                queryParams.toArray()
        );
        records.forEach(this::formatDateFields);
        long total = safePageNo == 1 && records.size() < safePageSize
                ? records.size()
                : nullToZero(jdbcTemplate.queryForObject(countSql, Long.class, params.toArray()));
        PageResponse<WorkOrderFeedbackVO.WorkOrderRecord> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total);
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        return response;
    }

    private boolean isAdminScope(String scope) {
        return "admin".equalsIgnoreCase(scope);
    }

    private String normalizeStatus(String status, boolean requireKnown) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!StringUtils.hasText(normalized) && !requireKnown) {
            return normalized;
        }
        if (List.of("OPEN", "PROCESSING", "RESOLVED", "CLOSED").contains(normalized)) {
            return normalized;
        }
        throw new BizException(ErrorCode.BAD_REQUEST, "工单状态无效");
    }

    private String normalizePriority(String priority) {
        String normalized = priority == null ? "" : priority.trim().toUpperCase(Locale.ROOT);
        return List.of("LOW", "NORMAL", "HIGH", "URGENT").contains(normalized) ? normalized : "NORMAL";
    }

    private String normalizeRequiredText(String value, int maxLength, String message) {
        String normalized = value == null ? "" : value.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new BizException(ErrorCode.BAD_REQUEST, message);
        }
        if (normalized.length() > maxLength) {
            throw new BizException(ErrorCode.BAD_REQUEST, "内容长度不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private String normalizeNullableText(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new BizException(ErrorCode.BAD_REQUEST, "回复长度不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private String displayName(CurrentUser currentUser) {
        return currentUser.getUsername();
    }

    private Long tenantId() {
        return PlatformContext.compatibilityTenantId();
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private void formatDateFields(WorkOrderFeedbackVO.WorkOrderRecord record) {
        record.setCreatedAt(formatDateText(record.getCreatedAt()));
        record.setUpdatedAt(formatDateText(record.getUpdatedAt()));
        record.setHandledAt(formatDateText(record.getHandledAt()));
    }

    private String formatDateText(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        try {
            return LocalDateTime.parse(value.replace(" ", "T")).format(DATE_TIME_FORMATTER);
        } catch (RuntimeException ignored) {
            return value;
        }
    }
}

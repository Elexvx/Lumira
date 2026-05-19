package com.legendary.invention.saas.modules.evaluation.app;

import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.saas.common.vo.PageResponse;
import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.modules.evaluation.dto.EvaluationDTO;
import com.legendary.invention.saas.modules.evaluation.vo.EvaluationVO;
import com.legendary.invention.saas.modules.task.app.TaskCenterAppService;
import org.springframework.dao.EmptyResultDataAccessException;
import com.legendary.invention.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.legendary.invention.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class EvaluationAppService {

    private final MyBatisQueryOperations jdbcTemplate;
    private final TaskCenterAppService taskCenterAppService;

    public EvaluationAppService(MyBatisQueryOperations jdbcTemplate, TaskCenterAppService taskCenterAppService) {
        this.jdbcTemplate = jdbcTemplate;
        this.taskCenterAppService = taskCenterAppService;
    }

    public PageResponse<EvaluationVO.TemplateVO> listTemplates(CurrentUser currentUser, long pageNo, long pageSize) {
        PageResponse<EvaluationVO.TemplateVO> page = pageQuery(
                "select id, template_name as templateName, object_type as objectType, description, enabled, create_time as createTime from evaluation_template where tenant_id = ? order by id desc",
                "select count(1) from evaluation_template where tenant_id = ?",
                EvaluationVO.TemplateVO.class,
                pageNo,
                pageSize,
                List.of(tenantId(currentUser))
        );
        page.getRecords().forEach(template -> fillTemplate(tenantId(currentUser), template));
        return page;
    }

    @Transactional
    public EvaluationVO.TemplateVO createTemplate(CurrentUser currentUser, EvaluationDTO.TemplateRequest request) {
        Long tenantId = tenantId(currentUser);
        jdbcTemplate.update(
                "insert into evaluation_template (tenant_id, template_name, object_type, description, enabled) values (?, ?, ?, ?, 1)",
                tenantId, clean(request.getTemplateName()), clean(request.getObjectType()), cleanNullable(request.getDescription())
        );
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        replaceTemplateChildren(tenantId, id, request);
        return getTemplate(tenantId, id);
    }

    @Transactional
    public EvaluationVO.TemplateVO updateTemplate(CurrentUser currentUser, Long id, EvaluationDTO.TemplateRequest request) {
        Long tenantId = tenantId(currentUser);
        requireTemplate(tenantId, id);
        jdbcTemplate.update(
                "update evaluation_template set template_name = ?, object_type = ?, description = ?, update_time = ? where tenant_id = ? and id = ?",
                clean(request.getTemplateName()), clean(request.getObjectType()), cleanNullable(request.getDescription()), LocalDateTime.now(), tenantId, id
        );
        replaceTemplateChildren(tenantId, id, request);
        return getTemplate(tenantId, id);
    }

    public boolean updateTemplateEnabled(CurrentUser currentUser, Long id, boolean enabled) {
        Long tenantId = tenantId(currentUser);
        requireTemplate(tenantId, id);
        jdbcTemplate.update("update evaluation_template set enabled = ?, update_time = ? where tenant_id = ? and id = ?", enabled ? 1 : 0, LocalDateTime.now(), tenantId, id);
        return true;
    }

    @Transactional
    public EvaluationVO.InstanceVO createInstance(CurrentUser currentUser, EvaluationDTO.InstanceCreateRequest request) {
        Long tenantId = tenantId(currentUser);
        EvaluationVO.TemplateVO template = getTemplate(tenantId, request.getTemplateId());
        if (!Boolean.TRUE.equals(template.getEnabled())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "评分模板未启用");
        }
        jdbcTemplate.update(
                """
                        insert into evaluation_instance (
                            tenant_id, template_id, object_type, object_id, object_title, status, creator_id, reviewer_user_id
                        ) values (?, ?, ?, ?, ?, 'SCORING', ?, ?)
                        """,
                tenantId, template.getId(), template.getObjectType(), request.getObjectId(), clean(request.getObjectTitle()), currentUser.getUserId(), request.getReviewerUserId()
        );
        Long instanceId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        for (Long scorerId : request.getScorerUserIds()) {
            jdbcTemplate.update("insert into evaluation_score_task (tenant_id, instance_id, assignee_user_id, status) values (?, ?, ?, 'PENDING')", tenantId, instanceId, scorerId);
            Long taskId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
            taskCenterAppService.createTask(tenantId, "EVALUATION", template.getObjectType(), request.getObjectId(), request.getObjectTitle(),
                    "评分待办：" + request.getObjectTitle(), template.getTemplateName(), scorerId, null, null, "EVALUATION", taskId, "/evaluations?instanceId=" + instanceId);
        }
        return getInstance(currentUser, instanceId);
    }

    public PageResponse<EvaluationVO.InstanceVO> listInstances(CurrentUser currentUser, String objectType, long pageNo, long pageSize) {
        Long tenantId = tenantId(currentUser);
        List<Object> params = new ArrayList<>(List.of(tenantId));
        String where = "tenant_id = ?";
        if (StringUtils.hasText(objectType)) {
            where += " and object_type = ?";
            params.add(objectType);
        }
        where += evaluationVisibilityWhere(currentUser, params);
        return pageQuery(
                "select id, template_id as templateId, object_type as objectType, object_id as objectId, object_title as objectTitle, status, creator_id as creatorId, reviewer_user_id as reviewerUserId, final_score as finalScore, final_grade as finalGrade, archive_comment as archiveComment, create_time as createTime from evaluation_instance where " + where + " order by id desc",
                "select count(1) from evaluation_instance where " + where,
                EvaluationVO.InstanceVO.class,
                pageNo,
                pageSize,
                params
        );
    }

    public EvaluationVO.InstanceVO getInstance(CurrentUser currentUser, Long id) {
        EvaluationVO.InstanceVO instance = requireVisibleInstance(currentUser, id);
        instance.setScoreTasks(listScoreTasks(tenantId(currentUser), id));
        return instance;
    }

    public PageResponse<EvaluationVO.ScoreTaskVO> myPendingTasks(CurrentUser currentUser, long pageNo, long pageSize) {
        return pageQuery(
                """
                        select id, instance_id as instanceId, assignee_user_id as assigneeUserId, status, total_score as totalScore,
                               comment, submitted_at as submittedAt, create_time as createTime
                        from evaluation_score_task
                        where tenant_id = ? and assignee_user_id = ? and status = 'PENDING'
                        order by id desc
                        """,
                "select count(1) from evaluation_score_task where tenant_id = ? and assignee_user_id = ? and status = 'PENDING'",
                EvaluationVO.ScoreTaskVO.class,
                pageNo,
                pageSize,
                List.of(tenantId(currentUser), currentUser.getUserId())
        );
    }

    @Transactional
    public EvaluationVO.InstanceVO submitScore(CurrentUser currentUser, Long taskId, EvaluationDTO.ScoreSubmitRequest request) {
        Long tenantId = tenantId(currentUser);
        EvaluationVO.ScoreTaskVO task = requireScoreTask(tenantId, taskId);
        if (!currentUser.getUserId().equals(task.getAssigneeUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "当前账号不是该评分任务处理人");
        }
        if (!"PENDING".equals(task.getStatus())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "评分任务已提交");
        }
        jdbcTemplate.update("delete from evaluation_score_detail where tenant_id = ? and score_task_id = ?", tenantId, taskId);
        BigDecimal total = BigDecimal.ZERO;
        for (EvaluationDTO.ScoreDetailRequest detail : request.getDetails()) {
            EvaluationVO.DimensionVO dimension = requireDimensionForInstance(tenantId, task.getInstanceId(), detail.getDimensionId());
            total = total.add(detail.getScore().multiply(dimension.getWeight()).divide(BigDecimal.valueOf(100)));
            jdbcTemplate.update(
                    "insert into evaluation_score_detail (tenant_id, score_task_id, dimension_id, score, comment) values (?, ?, ?, ?, ?)",
                    tenantId, taskId, detail.getDimensionId(), detail.getScore(), cleanNullable(detail.getComment())
            );
        }
        jdbcTemplate.update(
                "update evaluation_score_task set status = 'SUBMITTED', total_score = ?, comment = ?, submitted_at = ?, update_time = ? where tenant_id = ? and id = ?",
                total, cleanNullable(request.getComment()), LocalDateTime.now(), LocalDateTime.now(), tenantId, taskId
        );
        taskCenterAppService.completeSourceTask(tenantId, "EVALUATION", taskId, currentUser.getUserId());
        Long pending = jdbcTemplate.queryForObject("select count(1) from evaluation_score_task where tenant_id = ? and instance_id = ? and status = 'PENDING'", Long.class, tenantId, task.getInstanceId());
        if (pending == null || pending == 0) {
            EvaluationVO.InstanceVO instance = requireInstance(tenantId, task.getInstanceId());
            jdbcTemplate.update("update evaluation_instance set status = 'REVIEWING', final_score = ?, update_time = ? where tenant_id = ? and id = ?", averageSubmittedScore(tenantId, task.getInstanceId()), LocalDateTime.now(), tenantId, task.getInstanceId());
            if (instance.getReviewerUserId() != null) {
                taskCenterAppService.createTask(tenantId, "REVIEW", instance.getObjectType(), instance.getObjectId(), instance.getObjectTitle(),
                        "评审复核：" + instance.getObjectTitle(), "确认最终评分与等级", instance.getReviewerUserId(), null, null, "EVALUATION_REVIEW", instance.getId(), "/evaluations?instanceId=" + instance.getId());
            }
        }
        return getInstance(currentUser, task.getInstanceId());
    }

    @Transactional
    public EvaluationVO.InstanceVO review(CurrentUser currentUser, Long instanceId, EvaluationDTO.ReviewRequest request) {
        Long tenantId = tenantId(currentUser);
        EvaluationVO.InstanceVO instance = requireVisibleInstance(currentUser, instanceId);
        if (!canReviewInstance(currentUser, instance)) {
            throw new BizException(ErrorCode.FORBIDDEN, "当前账号不是该评审复核人");
        }
        jdbcTemplate.update(
                "update evaluation_instance set status = 'REVIEWED', final_score = ?, final_grade = ?, archive_comment = ?, update_time = ? where tenant_id = ? and id = ?",
                request.getFinalScore(), clean(request.getFinalGrade()), cleanNullable(request.getComment()), LocalDateTime.now(), tenantId, instanceId
        );
        jdbcTemplate.update(
                "insert into evaluation_review_record (tenant_id, instance_id, reviewer_id, final_score, final_grade, comment) values (?, ?, ?, ?, ?, ?)",
                tenantId, instanceId, currentUser.getUserId(), request.getFinalScore(), clean(request.getFinalGrade()), cleanNullable(request.getComment())
        );
        taskCenterAppService.completeSourceTask(tenantId, "EVALUATION_REVIEW", instanceId, currentUser.getUserId());
        return getInstance(currentUser, instanceId);
    }

    @Transactional
    public EvaluationVO.InstanceVO archive(CurrentUser currentUser, Long instanceId, EvaluationDTO.ArchiveRequest request) {
        Long tenantId = tenantId(currentUser);
        EvaluationVO.InstanceVO instance = requireVisibleInstance(currentUser, instanceId);
        if (instance.getFinalScore() == null || !StringUtils.hasText(instance.getFinalGrade())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "请先完成复核再归档");
        }
        jdbcTemplate.update("update evaluation_instance set status = 'ARCHIVED', archive_comment = ?, update_time = ? where tenant_id = ? and id = ?", cleanNullable(request.getComment()), LocalDateTime.now(), tenantId, instanceId);
        jdbcTemplate.update(
                """
                        insert into evaluation_result (
                            tenant_id, instance_id, object_type, object_id, object_title, final_score, final_grade,
                            archive_comment, archived_by
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        on duplicate key update final_score = values(final_score), final_grade = values(final_grade),
                                                archive_comment = values(archive_comment), archived_by = values(archived_by), archived_at = now()
                        """,
                tenantId, instanceId, instance.getObjectType(), instance.getObjectId(), instance.getObjectTitle(), instance.getFinalScore(),
                instance.getFinalGrade(), cleanNullable(request.getComment()), currentUser.getUserId()
        );
        return getInstance(currentUser, instanceId);
    }

    private void replaceTemplateChildren(Long tenantId, Long templateId, EvaluationDTO.TemplateRequest request) {
        jdbcTemplate.update("delete from evaluation_dimension where tenant_id = ? and template_id = ?", tenantId, templateId);
        jdbcTemplate.update("delete from evaluation_grade_rule where tenant_id = ? and template_id = ?", tenantId, templateId);
        int index = 0;
        for (EvaluationDTO.DimensionRequest dimension : request.getDimensions()) {
            jdbcTemplate.update("insert into evaluation_dimension (tenant_id, template_id, dimension_name, weight, max_score, sort_order) values (?, ?, ?, ?, ?, ?)",
                    tenantId, templateId, clean(dimension.getDimensionName()), dimension.getWeight(), dimension.getMaxScore(), dimension.getSortOrder() == null ? index : dimension.getSortOrder());
            index += 10;
        }
        for (EvaluationDTO.GradeRuleRequest rule : request.getGradeRules()) {
            jdbcTemplate.update("insert into evaluation_grade_rule (tenant_id, template_id, grade_code, grade_name, min_score, max_score) values (?, ?, ?, ?, ?, ?)",
                    tenantId, templateId, clean(rule.getGradeCode()), clean(rule.getGradeName()), rule.getMinScore(), rule.getMaxScore());
        }
    }

    private EvaluationVO.TemplateVO getTemplate(Long tenantId, Long id) {
        EvaluationVO.TemplateVO template = requireTemplate(tenantId, id);
        fillTemplate(tenantId, template);
        return template;
    }

    private void fillTemplate(Long tenantId, EvaluationVO.TemplateVO template) {
        template.setDimensions(jdbcTemplate.query("select id, dimension_name as dimensionName, weight, max_score as maxScore, sort_order as sortOrder from evaluation_dimension where tenant_id = ? and template_id = ? order by sort_order asc", new BeanPropertyRowMapper<>(EvaluationVO.DimensionVO.class), tenantId, template.getId()));
        template.setGradeRules(jdbcTemplate.query("select id, grade_code as gradeCode, grade_name as gradeName, min_score as minScore, max_score as maxScore from evaluation_grade_rule where tenant_id = ? and template_id = ? order by min_score desc", new BeanPropertyRowMapper<>(EvaluationVO.GradeRuleVO.class), tenantId, template.getId()));
    }

    private EvaluationVO.TemplateVO requireTemplate(Long tenantId, Long id) {
        try {
            return jdbcTemplate.queryForObject("select id, template_name as templateName, object_type as objectType, description, enabled, create_time as createTime from evaluation_template where tenant_id = ? and id = ?", new BeanPropertyRowMapper<>(EvaluationVO.TemplateVO.class), tenantId, id);
        } catch (EmptyResultDataAccessException ex) {
            throw new BizException(ErrorCode.NOT_FOUND, "评分模板不存在");
        }
    }

    private EvaluationVO.InstanceVO requireInstance(Long tenantId, Long id) {
        try {
            return jdbcTemplate.queryForObject("select id, template_id as templateId, object_type as objectType, object_id as objectId, object_title as objectTitle, status, creator_id as creatorId, reviewer_user_id as reviewerUserId, final_score as finalScore, final_grade as finalGrade, archive_comment as archiveComment, create_time as createTime from evaluation_instance where tenant_id = ? and id = ?", new BeanPropertyRowMapper<>(EvaluationVO.InstanceVO.class), tenantId, id);
        } catch (EmptyResultDataAccessException ex) {
            throw new BizException(ErrorCode.NOT_FOUND, "评审实例不存在");
        }
    }

    private EvaluationVO.InstanceVO requireVisibleInstance(CurrentUser currentUser, Long id) {
        Long tenantId = tenantId(currentUser);
        List<Object> params = new ArrayList<>(List.of(tenantId, id));
        String where = "tenant_id = ? and id = ?" + evaluationVisibilityWhere(currentUser, params);
        try {
            return jdbcTemplate.queryForObject(
                    "select id, template_id as templateId, object_type as objectType, object_id as objectId, object_title as objectTitle, status, creator_id as creatorId, reviewer_user_id as reviewerUserId, final_score as finalScore, final_grade as finalGrade, archive_comment as archiveComment, create_time as createTime from evaluation_instance where " + where,
                    new BeanPropertyRowMapper<>(EvaluationVO.InstanceVO.class),
                    params.toArray()
            );
        } catch (EmptyResultDataAccessException ex) {
            throw new BizException(ErrorCode.NOT_FOUND, "评审实例不存在或无权访问");
        }
    }

    private String evaluationVisibilityWhere(CurrentUser currentUser, List<Object> params) {
        if (hasAnyPermission(currentUser.getPermissions(), Set.of("evaluation:template:manage", "evaluation:archive", "*"))) {
            return "";
        }
        params.add(currentUser.getUserId());
        params.add(currentUser.getUserId());
        params.add(currentUser.getUserId());
        return """
                 and (
                   creator_id = ?
                   or reviewer_user_id = ?
                   or exists (
                     select 1
                     from evaluation_score_task st
                     where st.tenant_id = evaluation_instance.tenant_id
                       and st.instance_id = evaluation_instance.id
                       and st.assignee_user_id = ?
                   )
                 )
                """;
    }

    private boolean hasAnyPermission(Set<String> permissions, Set<String> expected) {
        if (permissions == null || permissions.isEmpty()) {
            return false;
        }
        return expected.stream().anyMatch(permissions::contains);
    }

    private boolean canReviewInstance(CurrentUser currentUser, EvaluationVO.InstanceVO instance) {
        if (instance.getReviewerUserId() != null) {
            return instance.getReviewerUserId().equals(currentUser.getUserId())
                    || hasAnyPermission(currentUser.getPermissions(), Set.of("evaluation:archive", "evaluation:template:manage", "*"));
        }
        return hasAnyPermission(currentUser.getPermissions(), Set.of("evaluation:archive", "evaluation:template:manage", "*"));
    }

    private EvaluationVO.ScoreTaskVO requireScoreTask(Long tenantId, Long id) {
        try {
            return jdbcTemplate.queryForObject("select id, instance_id as instanceId, assignee_user_id as assigneeUserId, status, total_score as totalScore, comment, submitted_at as submittedAt, create_time as createTime from evaluation_score_task where tenant_id = ? and id = ?", new BeanPropertyRowMapper<>(EvaluationVO.ScoreTaskVO.class), tenantId, id);
        } catch (EmptyResultDataAccessException ex) {
            throw new BizException(ErrorCode.NOT_FOUND, "评分任务不存在");
        }
    }

    private EvaluationVO.DimensionVO requireDimensionForInstance(Long tenantId, Long instanceId, Long dimensionId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select d.id, d.dimension_name as dimensionName, d.weight, d.max_score as maxScore, d.sort_order as sortOrder
                            from evaluation_dimension d
                            join evaluation_instance i
                              on i.tenant_id = d.tenant_id
                             and i.template_id = d.template_id
                            where d.tenant_id = ?
                              and i.id = ?
                              and d.id = ?
                            """,
                    new BeanPropertyRowMapper<>(EvaluationVO.DimensionVO.class),
                    tenantId,
                    instanceId,
                    dimensionId
            );
        } catch (EmptyResultDataAccessException ex) {
            throw new BizException(ErrorCode.NOT_FOUND, "评分维度不存在");
        }
    }

    private List<EvaluationVO.ScoreTaskVO> listScoreTasks(Long tenantId, Long instanceId) {
        return jdbcTemplate.query("select id, instance_id as instanceId, assignee_user_id as assigneeUserId, status, total_score as totalScore, comment, submitted_at as submittedAt, create_time as createTime from evaluation_score_task where tenant_id = ? and instance_id = ? order by id asc", new BeanPropertyRowMapper<>(EvaluationVO.ScoreTaskVO.class), tenantId, instanceId);
    }

    private BigDecimal averageSubmittedScore(Long tenantId, Long instanceId) {
        BigDecimal score = jdbcTemplate.queryForObject("select avg(total_score) from evaluation_score_task where tenant_id = ? and instance_id = ? and status = 'SUBMITTED'", BigDecimal.class, tenantId, instanceId);
        return score == null ? BigDecimal.ZERO : score;
    }

    private <T> PageResponse<T> pageQuery(String selectSql, String countSql, Class<T> voClass, long pageNo, long pageSize, List<Object> params) {
        long safePageNo = pageNo <= 0 ? 1 : pageNo;
        long safePageSize = pageSize <= 0 ? 10 : pageSize;
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safePageSize);
        queryParams.add((safePageNo - 1) * safePageSize);
        List<T> records = jdbcTemplate.query(selectSql + " limit ? offset ?", new BeanPropertyRowMapper<>(voClass), queryParams.toArray());
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
        PageResponse<T> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total == null ? 0 : total);
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        return response;
    }

    private Long tenantId(CurrentUser currentUser) {
        return com.legendary.invention.common.constant.PlatformConstants.PLATFORM_TENANT_ID;
    }

    private String clean(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "必填字段不能为空");
        }
        return value.trim();
    }

    private String cleanNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}

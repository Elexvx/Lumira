package com.legendary.invention.saas.modules.approval.app;

import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.saas.common.vo.PageResponse;
import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.modules.approval.dto.ApprovalDTO;
import com.legendary.invention.saas.modules.approval.vo.ApprovalVO;
import com.legendary.invention.saas.modules.audit.app.OperationAuditService;
import com.legendary.invention.saas.modules.task.app.TaskCenterAppService;
import org.springframework.dao.EmptyResultDataAccessException;
import com.legendary.invention.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.legendary.invention.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ApprovalAppService implements WorkflowEngineAdapter {

    private static final long MAX_PAGE_SIZE = 100L;

    private final MyBatisQueryOperations jdbcTemplate;
    private final TaskCenterAppService taskCenterAppService;
    private final OperationAuditService operationAuditService;

    public ApprovalAppService(MyBatisQueryOperations jdbcTemplate, TaskCenterAppService taskCenterAppService, OperationAuditService operationAuditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.taskCenterAppService = taskCenterAppService;
        this.operationAuditService = operationAuditService;
    }

    public PageResponse<ApprovalVO.TemplateVO> listTemplates(CurrentUser currentUser, long pageNo, long pageSize) {
        PageResponse<ApprovalVO.TemplateVO> page = pageQuery(
                """
                        select id, template_name as templateName, business_type as businessType, description,
                               enabled, create_time as createTime
                        from approval_template
                        where tenant_id = ?
                        order by id desc
                        """,
                "select count(1) from approval_template where tenant_id = ?",
                ApprovalVO.TemplateVO.class,
                pageNo,
                pageSize,
                List.of(tenantId(currentUser))
        );
        page.getRecords().forEach(item -> item.setNodes(listNodes(tenantId(currentUser), item.getId())));
        return page;
    }

    @Transactional
    public ApprovalVO.TemplateVO createTemplate(CurrentUser currentUser, ApprovalDTO.TemplateRequest request) {
        Long tenantId = tenantId(currentUser);
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        insert into approval_template (tenant_id, template_name, business_type, description, enabled, create_time, update_time)
                        values (?, ?, ?, ?, 1, ?, ?)
                        """,
                tenantId, clean(request.getTemplateName()), clean(request.getBusinessType()), cleanNullable(request.getDescription()), now, now
        );
        Long templateId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        replaceNodes(tenantId, templateId, request.getNodes());
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "approval", "template-create", "CREATE", "SUCCESS", "创建审批模板: " + request.getTemplateName());
        return getTemplate(tenantId, templateId);
    }

    @Transactional
    public ApprovalVO.TemplateVO updateTemplate(CurrentUser currentUser, Long id, ApprovalDTO.TemplateRequest request) {
        Long tenantId = tenantId(currentUser);
        requireTemplate(tenantId, id);
        jdbcTemplate.update(
                """
                        update approval_template
                        set template_name = ?, business_type = ?, description = ?, update_time = ?
                        where tenant_id = ? and id = ?
                        """,
                clean(request.getTemplateName()), clean(request.getBusinessType()), cleanNullable(request.getDescription()), LocalDateTime.now(), tenantId, id
        );
        replaceNodes(tenantId, id, request.getNodes());
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "approval", "template-update", "UPDATE", "SUCCESS", "更新审批模板: " + id);
        return getTemplate(tenantId, id);
    }

    public boolean updateTemplateEnabled(CurrentUser currentUser, Long id, boolean enabled) {
        Long tenantId = tenantId(currentUser);
        requireTemplate(tenantId, id);
        jdbcTemplate.update("update approval_template set enabled = ?, update_time = ? where tenant_id = ? and id = ?", enabled ? 1 : 0, LocalDateTime.now(), tenantId, id);
        return true;
    }

    @Override
    @Transactional
    public ApprovalVO.InstanceVO start(CurrentUser currentUser, ApprovalDTO.InstanceCreateRequest request) {
        Long tenantId = tenantId(currentUser);
        ApprovalVO.TemplateVO template = requireTemplateByBusinessType(tenantId, clean(request.getBusinessType()));
        if (!Boolean.TRUE.equals(template.getEnabled()) || template.getNodes() == null || template.getNodes().isEmpty()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "审批模板未启用或未配置节点");
        }
        ApprovalVO.NodeVO firstNode = template.getNodes().get(0);
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        insert into approval_instance (
                            tenant_id, template_id, business_type, business_id, business_title, summary, payload_json,
                            applicant_id, applicant_name, status, current_node_id, create_time, update_time
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?)
                        """,
                tenantId, template.getId(), clean(request.getBusinessType()), request.getBusinessId(), clean(request.getBusinessTitle()),
                cleanNullable(request.getSummary()), cleanNullable(request.getPayloadJson()), currentUser.getUserId(), currentUser.getUsername(),
                firstNode.getId(), now, now
        );
        Long instanceId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        createApprovalTask(tenantId, instanceId, firstNode, request.getBusinessType(), request.getBusinessId(), request.getBusinessTitle());
        record(tenantId, instanceId, null, "SUBMIT", currentUser, "提交审批");
        operationAuditService.log(tenantId, currentUser.getUserId(), currentUser.getUsername(), "approval", "instance-submit", "CREATE", "SUCCESS", "提交审批: " + request.getBusinessTitle());
        return getInstance(currentUser, instanceId);
    }

    @Override
    @Transactional
    public ApprovalVO.InstanceVO approve(CurrentUser currentUser, Long taskId, String comment) {
        ApprovalVO.TaskVO task = requirePendingTaskForUser(currentUser, taskId);
        Long tenantId = tenantId(currentUser);
        jdbcTemplate.update(
                """
                        update approval_task
                        set status = 'APPROVED', handled_by = ?, handled_comment = ?, handled_at = ?, update_time = ?
                        where tenant_id = ? and id = ? and status = 'PENDING'
                        """,
                currentUser.getUserId(), cleanNullable(comment), LocalDateTime.now(), LocalDateTime.now(), tenantId, taskId
        );
        taskCenterAppService.completeSourceTask(tenantId, "APPROVAL", taskId, currentUser.getUserId());
        record(tenantId, task.getInstanceId(), taskId, "APPROVE", currentUser, comment);
        advanceOrFinish(currentUser, task);
        return getInstance(currentUser, task.getInstanceId());
    }

    @Override
    @Transactional
    public ApprovalVO.InstanceVO reject(CurrentUser currentUser, Long taskId, String comment) {
        ApprovalVO.TaskVO task = requirePendingTaskForUser(currentUser, taskId);
        Long tenantId = tenantId(currentUser);
        jdbcTemplate.update(
                "update approval_task set status = 'REJECTED', handled_by = ?, handled_comment = ?, handled_at = ?, update_time = ? where tenant_id = ? and id = ?",
                currentUser.getUserId(), cleanNullable(comment), LocalDateTime.now(), LocalDateTime.now(), tenantId, taskId
        );
        jdbcTemplate.update("update approval_instance set status = 'REJECTED', update_time = ? where tenant_id = ? and id = ?", LocalDateTime.now(), tenantId, task.getInstanceId());
        taskCenterAppService.completeSourceTask(tenantId, "APPROVAL", taskId, currentUser.getUserId());
        cancelOtherApprovalTasks(tenantId, task.getInstanceId(), taskId);
        record(tenantId, task.getInstanceId(), taskId, "REJECT", currentUser, comment);
        return getInstance(currentUser, task.getInstanceId());
    }

    @Override
    @Transactional
    public ApprovalVO.InstanceVO cancel(CurrentUser currentUser, Long instanceId) {
        Long tenantId = tenantId(currentUser);
        ApprovalVO.InstanceVO instance = requireInstance(tenantId, instanceId);
        if (!currentUser.getUserId().equals(instance.getApplicantId()) && !hasPermission(currentUser, "approval:template:manage")) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能撤回自己发起的审批");
        }
        jdbcTemplate.update("update approval_instance set status = 'CANCELLED', update_time = ? where tenant_id = ? and id = ?", LocalDateTime.now(), tenantId, instanceId);
        cancelOtherApprovalTasks(tenantId, instanceId, null);
        record(tenantId, instanceId, null, "CANCEL", currentUser, "撤回审批");
        return getInstance(currentUser, instanceId);
    }

    public PageResponse<ApprovalVO.InstanceVO> listInstances(CurrentUser currentUser, String scope, long pageNo, long pageSize) {
        Long tenantId = tenantId(currentUser);
        List<Object> params = new ArrayList<>(List.of(tenantId));
        String where = "tenant_id = ?";
        if ("submitted".equals(scope)) {
            where += " and applicant_id = ?";
            params.add(currentUser.getUserId());
        } else {
            where += approvalInstanceVisibilityWhere(currentUser, params);
        }
        PageResponse<ApprovalVO.InstanceVO> page = pageQuery(
                """
                        select id, template_id as templateId, business_type as businessType, business_id as businessId,
                               business_title as businessTitle, summary, payload_json as payloadJson, applicant_id as applicantId,
                               applicant_name as applicantName, status, current_node_id as currentNodeId, create_time as createTime
                        from approval_instance
                        where
                        """ + where + " order by id desc",
                "select count(1) from approval_instance where " + where,
                ApprovalVO.InstanceVO.class,
                pageNo,
                pageSize,
                params
        );
        return page;
    }

    public PageResponse<ApprovalVO.TaskVO> myPendingTasks(CurrentUser currentUser, long pageNo, long pageSize) {
        Long tenantId = tenantId(currentUser);
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        params.add(currentUser.getUserId());
        String visibility = "t.tenant_id = ? and t.status = 'PENDING' and (t.assignee_user_id = ?";
        if (currentUser.getUserId() != null) {
            visibility += """
                     or exists (
                        select 1
                        from sys_user_role ur
                        where ur.tenant_id = t.tenant_id
                          and ur.user_id = ?
                          and ur.role_id = t.assignee_role_id
                          and ur.deleted = 0
                    )
                    """;
            params.add(currentUser.getUserId());
        }
        if (hasPermission(currentUser, "approval:approve")) {
            visibility += " or t.assignee_dept_id is not null";
        }
        visibility += ")";
        return pageQuery(
                """
                        select t.id, t.instance_id as instanceId, t.node_id as nodeId,
                               i.business_type as businessType, i.business_title as businessTitle,
                               t.assignee_user_id as assigneeUserId, t.assignee_role_id as assigneeRoleId,
                               t.assignee_dept_id as assigneeDeptId, t.status, t.handled_by as handledBy,
                               t.handled_comment as handledComment, t.handled_at as handledAt, t.create_time as createTime
                        from approval_task t
                        join approval_instance i on i.tenant_id = t.tenant_id and i.id = t.instance_id
                        where
                        """ + visibility + " order by t.id desc",
                """
                        select count(1)
                        from approval_task t
                        join approval_instance i on i.tenant_id = t.tenant_id and i.id = t.instance_id
                        where
                        """ + visibility,
                ApprovalVO.TaskVO.class,
                pageNo,
                pageSize,
                params
        );
    }

    public ApprovalVO.InstanceVO getInstance(CurrentUser currentUser, Long id) {
        Long tenantId = tenantId(currentUser);
        ApprovalVO.InstanceVO instance = requireInstance(tenantId, id);
        if (!canViewInstance(currentUser, instance)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看该审批");
        }
        instance.setTasks(listTasks(tenantId, id));
        instance.setRecords(listRecords(tenantId, id));
        return instance;
    }

    private boolean canViewInstance(CurrentUser currentUser, ApprovalVO.InstanceVO instance) {
        if (currentUser.getUserId() != null && currentUser.getUserId().equals(instance.getApplicantId())) {
            return true;
        }
        Long tenantId = tenantId(currentUser);
        List<ApprovalVO.TaskVO> tasks = listTasks(tenantId, instance.getId());
        for (ApprovalVO.TaskVO task : tasks) {
            if (canHandle(currentUser, task) || (currentUser.getUserId() != null && currentUser.getUserId().equals(task.getHandledBy()))) {
                return true;
            }
        }
        return hasPermission(currentUser, "approval:template:manage");
    }

    private String approvalInstanceVisibilityWhere(CurrentUser currentUser, List<Object> params) {
        if (hasAnyPermission(currentUser.getPermissions(), Set.of("approval:template:manage", "*"))) {
            return "";
        }
        Long userId = currentUser.getUserId();
        params.add(userId);
        params.add(userId);
        params.add(userId);
        params.add(userId);
        String deptVisibility = hasPermission(currentUser, "approval:approve")
                ? " or t.assignee_dept_id is not null"
                : "";
        return """
                 and (
                        applicant_id = ?
                     or exists (
                            select 1
                            from approval_task t
                            where t.tenant_id = approval_instance.tenant_id
                              and t.instance_id = approval_instance.id
                              and (
                                     t.assignee_user_id = ?
                                  or t.handled_by = ?
                                  or exists (
                                        select 1
                                        from sys_user_role ur
                                        where ur.tenant_id = t.tenant_id
                                          and ur.user_id = ?
                                          and ur.role_id = t.assignee_role_id
                                          and ur.deleted = 0
                                  )
                                  %s
                              )
                        )
                 )
                """.formatted(deptVisibility);
    }

    private void advanceOrFinish(CurrentUser currentUser, ApprovalVO.TaskVO task) {
        Long tenantId = tenantId(currentUser);
        ApprovalVO.InstanceVO instance = requireInstance(tenantId, task.getInstanceId());
        List<ApprovalVO.NodeVO> nodes = listNodes(tenantId, instance.getTemplateId());
        int currentIndex = -1;
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).getId().equals(task.getNodeId())) {
                currentIndex = i;
                break;
            }
        }
        if (currentIndex < 0 || currentIndex + 1 >= nodes.size()) {
            jdbcTemplate.update("update approval_instance set status = 'APPROVED', current_node_id = null, update_time = ? where tenant_id = ? and id = ?", LocalDateTime.now(), tenantId, task.getInstanceId());
            record(tenantId, task.getInstanceId(), task.getId(), "FINISH", currentUser, "审批通过");
            return;
        }
        ApprovalVO.NodeVO nextNode = nodes.get(currentIndex + 1);
        jdbcTemplate.update("update approval_instance set current_node_id = ?, update_time = ? where tenant_id = ? and id = ?", nextNode.getId(), LocalDateTime.now(), tenantId, task.getInstanceId());
        createApprovalTask(tenantId, task.getInstanceId(), nextNode, instance.getBusinessType(), instance.getBusinessId(), instance.getBusinessTitle());
    }

    private Long createApprovalTask(Long tenantId, Long instanceId, ApprovalVO.NodeVO node, String businessType, Long businessId, String businessTitle) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = "USER".equals(node.getApproverType()) ? node.getApproverId() : null;
        Long roleId = "ROLE".equals(node.getApproverType()) ? node.getApproverId() : null;
        Long deptId = "DEPARTMENT".equals(node.getApproverType()) ? node.getApproverId() : null;
        jdbcTemplate.update(
                """
                        insert into approval_task (
                            tenant_id, instance_id, node_id, assignee_user_id, assignee_role_id, assignee_dept_id,
                            status, create_time, update_time
                        ) values (?, ?, ?, ?, ?, ?, 'PENDING', ?, ?)
                        """,
                tenantId, instanceId, node.getId(), userId, roleId, deptId, now, now
        );
        Long taskId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        taskCenterAppService.createTask(tenantId, "APPROVAL", businessType, businessId, businessTitle, "审批待办：" + businessTitle,
                node.getNodeName(), userId, roleId, deptId, "APPROVAL", taskId, "/approvals?instanceId=" + instanceId);
        return taskId;
    }

    private ApprovalVO.TaskVO requirePendingTaskForUser(CurrentUser currentUser, Long taskId) {
        Long tenantId = tenantId(currentUser);
        ApprovalVO.TaskVO task = queryTask(tenantId, taskId);
        if (!"PENDING".equals(task.getStatus())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "审批任务已处理");
        }
        if (!canHandle(currentUser, task)) {
            throw new BizException(ErrorCode.FORBIDDEN, "当前账号不是该审批任务处理人");
        }
        return task;
    }

    private boolean canHandle(CurrentUser currentUser, ApprovalVO.TaskVO task) {
        if (currentUser.getUserId().equals(task.getAssigneeUserId())) {
            return true;
        }
        Long tenantId = tenantId(currentUser);
        if (task.getAssigneeRoleId() != null) {
            Integer count = jdbcTemplate.queryForObject("select count(1) from sys_user_role where tenant_id = ? and user_id = ? and role_id = ? and deleted = 0", Integer.class, tenantId, currentUser.getUserId(), task.getAssigneeRoleId());
            if (count != null && count > 0) {
                return true;
            }
        }
        return task.getAssigneeDeptId() != null && hasPermission(currentUser, "approval:approve");
    }

    private void cancelOtherApprovalTasks(Long tenantId, Long instanceId, Long exceptTaskId) {
        List<ApprovalVO.TaskVO> tasks = listTasks(tenantId, instanceId);
        for (ApprovalVO.TaskVO task : tasks) {
            if ("PENDING".equals(task.getStatus()) && (exceptTaskId == null || !exceptTaskId.equals(task.getId()))) {
                jdbcTemplate.update("update approval_task set status = 'CANCELLED', update_time = ? where tenant_id = ? and id = ?", LocalDateTime.now(), tenantId, task.getId());
                taskCenterAppService.cancelSourceTasks(tenantId, "APPROVAL", task.getId());
            }
        }
    }

    private void replaceNodes(Long tenantId, Long templateId, List<ApprovalDTO.NodeRequest> nodes) {
        jdbcTemplate.update("delete from approval_template_node where tenant_id = ? and template_id = ?", tenantId, templateId);
        int index = 0;
        for (ApprovalDTO.NodeRequest node : nodes) {
            String approverType = clean(node.getApproverType()).toUpperCase(Locale.ROOT);
            if (!List.of("USER", "ROLE", "DEPARTMENT").contains(approverType)) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "审批人类型仅支持 USER/ROLE/DEPARTMENT");
            }
            jdbcTemplate.update(
                    """
                            insert into approval_template_node (
                                tenant_id, template_id, node_name, sort_order, approval_policy, approver_type, approver_id
                            ) values (?, ?, ?, ?, 'ANY_ONE', ?, ?)
                            """,
                    tenantId, templateId, clean(node.getNodeName()), node.getSortOrder() == null ? index : node.getSortOrder(), approverType, node.getApproverId()
            );
            index += 10;
        }
    }

    private ApprovalVO.TemplateVO getTemplate(Long tenantId, Long id) {
        ApprovalVO.TemplateVO template = requireTemplate(tenantId, id);
        template.setNodes(listNodes(tenantId, id));
        return template;
    }

    private ApprovalVO.TemplateVO requireTemplate(Long tenantId, Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    "select id, template_name as templateName, business_type as businessType, description, enabled, create_time as createTime from approval_template where tenant_id = ? and id = ?",
                    new BeanPropertyRowMapper<>(ApprovalVO.TemplateVO.class),
                    tenantId,
                    id
            );
        } catch (EmptyResultDataAccessException ex) {
            throw new BizException(ErrorCode.NOT_FOUND, "审批模板不存在");
        }
    }

    private ApprovalVO.TemplateVO requireTemplateByBusinessType(Long tenantId, String businessType) {
        try {
            ApprovalVO.TemplateVO template = jdbcTemplate.queryForObject(
                    "select id, template_name as templateName, business_type as businessType, description, enabled, create_time as createTime from approval_template where tenant_id = ? and business_type = ?",
                    new BeanPropertyRowMapper<>(ApprovalVO.TemplateVO.class),
                    tenantId,
                    businessType
            );
            template.setNodes(listNodes(tenantId, template.getId()));
            return template;
        } catch (EmptyResultDataAccessException ex) {
            throw new BizException(ErrorCode.NOT_FOUND, "未找到可用审批模板");
        }
    }

    private ApprovalVO.InstanceVO requireInstance(Long tenantId, Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id, template_id as templateId, business_type as businessType, business_id as businessId,
                                   business_title as businessTitle, summary, payload_json as payloadJson, applicant_id as applicantId,
                                   applicant_name as applicantName, status, current_node_id as currentNodeId, create_time as createTime
                            from approval_instance
                            where tenant_id = ? and id = ?
                            """,
                    new BeanPropertyRowMapper<>(ApprovalVO.InstanceVO.class),
                    tenantId,
                    id
            );
        } catch (EmptyResultDataAccessException ex) {
            throw new BizException(ErrorCode.NOT_FOUND, "审批实例不存在");
        }
    }

    private ApprovalVO.TaskVO queryTask(Long tenantId, Long taskId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            select id, instance_id as instanceId, node_id as nodeId, assignee_user_id as assigneeUserId,
                                   assignee_role_id as assigneeRoleId, assignee_dept_id as assigneeDeptId, status,
                                   handled_by as handledBy, handled_comment as handledComment, handled_at as handledAt, create_time as createTime
                            from approval_task
                            where tenant_id = ? and id = ?
                            """,
                    new BeanPropertyRowMapper<>(ApprovalVO.TaskVO.class),
                    tenantId,
                    taskId
            );
        } catch (EmptyResultDataAccessException ex) {
            throw new BizException(ErrorCode.NOT_FOUND, "审批任务不存在");
        }
    }

    private List<ApprovalVO.NodeVO> listNodes(Long tenantId, Long templateId) {
        return jdbcTemplate.query(
                """
                        select id, node_name as nodeName, sort_order as sortOrder, approval_policy as approvalPolicy,
                               approver_type as approverType, approver_id as approverId
                        from approval_template_node
                        where tenant_id = ? and template_id = ?
                        order by sort_order asc, id asc
                        """,
                new BeanPropertyRowMapper<>(ApprovalVO.NodeVO.class),
                tenantId,
                templateId
        );
    }

    private List<ApprovalVO.TaskVO> listTasks(Long tenantId, Long instanceId) {
        return jdbcTemplate.query(
                """
                        select id, instance_id as instanceId, node_id as nodeId, assignee_user_id as assigneeUserId,
                               assignee_role_id as assigneeRoleId, assignee_dept_id as assigneeDeptId, status,
                               handled_by as handledBy, handled_comment as handledComment, handled_at as handledAt, create_time as createTime
                        from approval_task
                        where tenant_id = ? and instance_id = ?
                        order by id asc
                        """,
                new BeanPropertyRowMapper<>(ApprovalVO.TaskVO.class),
                tenantId,
                instanceId
        );
    }

    private List<ApprovalVO.RecordVO> listRecords(Long tenantId, Long instanceId) {
        return jdbcTemplate.query(
                """
                        select id, instance_id as instanceId, task_id as taskId, action, operator_id as operatorId,
                               operator_name as operatorName, comment, create_time as createTime
                        from approval_record
                        where tenant_id = ? and instance_id = ?
                        order by id asc
                        """,
                new BeanPropertyRowMapper<>(ApprovalVO.RecordVO.class),
                tenantId,
                instanceId
        );
    }

    private void record(Long tenantId, Long instanceId, Long taskId, String action, CurrentUser currentUser, String comment) {
        jdbcTemplate.update(
                "insert into approval_record (tenant_id, instance_id, task_id, action, operator_id, operator_name, comment, create_time) values (?, ?, ?, ?, ?, ?, ?, ?)",
                tenantId, instanceId, taskId, action, currentUser.getUserId(), currentUser.getUsername(), cleanNullable(comment), LocalDateTime.now()
        );
    }

    private <T> PageResponse<T> pageQuery(String selectSql, String countSql, Class<T> voClass, long pageNo, long pageSize, List<Object> params) {
        long safePageNo = pageNo <= 0 ? 1 : pageNo;
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
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

    private boolean hasPermission(CurrentUser currentUser, String permission) {
        return currentUser.getPermissions() != null && (currentUser.getPermissions().contains("*") || currentUser.getPermissions().contains(permission));
    }

    private boolean hasAnyPermission(Set<String> permissions, Set<String> expected) {
        if (permissions == null || permissions.isEmpty()) {
            return false;
        }
        return expected.stream().anyMatch(permissions::contains);
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

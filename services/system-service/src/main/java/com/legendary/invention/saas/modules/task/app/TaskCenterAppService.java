package com.legendary.invention.saas.modules.task.app;

import com.legendary.invention.saas.common.vo.PageResponse;
import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.modules.task.vo.TaskVO;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class TaskCenterAppService {

    private final JdbcTemplate jdbcTemplate;

    public TaskCenterAppService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResponse<TaskVO.TaskItemVO> myPending(CurrentUser currentUser, long pageNo, long pageSize) {
        return listTasks(currentUser, "PENDING", pageNo, pageSize);
    }

    public PageResponse<TaskVO.TaskItemVO> myHandled(CurrentUser currentUser, long pageNo, long pageSize) {
        return listTasks(currentUser, "DONE", pageNo, pageSize);
    }

    public TaskVO.TaskSummaryVO summary(CurrentUser currentUser) {
        TaskVO.TaskSummaryVO summary = new TaskVO.TaskSummaryVO();
        summary.setPendingCount(countByType(currentUser, null));
        summary.setApprovalCount(countByType(currentUser, "APPROVAL"));
        summary.setEvaluationCount(countByType(currentUser, "EVALUATION"));
        summary.setReviewCount(countByType(currentUser, "REVIEW"));
        summary.setLatestPending(myPending(currentUser, 1, 5).getRecords());
        return summary;
    }

    public Long createTask(Long tenantId, String taskType, String businessType, Long businessId, String businessTitle,
                           String title, String description, Long assigneeUserId, Long assigneeRoleId, Long assigneeDeptId,
                           String sourceModule, Long sourceTaskId, String redirectUrl) {
        jdbcTemplate.update(
                """
                        insert into task_instance (
                            tenant_id, task_type, business_type, business_id, business_title, title, description,
                            assignee_user_id, assignee_role_id, assignee_dept_id, status, source_module, source_task_id,
                            redirect_url, create_time, update_time
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, ?)
                        """,
                tenantId, taskType, businessType, businessId, businessTitle, title, description,
                assigneeUserId, assigneeRoleId, assigneeDeptId, sourceModule, sourceTaskId, redirectUrl,
                LocalDateTime.now(), LocalDateTime.now()
        );
        return jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
    }

    public void completeSourceTask(Long tenantId, String sourceModule, Long sourceTaskId, Long operatorId) {
        jdbcTemplate.update(
                """
                        update task_instance
                        set status = 'DONE', completed_by = ?, completed_at = ?, update_time = ?
                        where tenant_id = ? and source_module = ? and source_task_id = ? and status = 'PENDING'
                        """,
                operatorId, LocalDateTime.now(), LocalDateTime.now(), tenantId, sourceModule, sourceTaskId
        );
    }

    public void cancelSourceTasks(Long tenantId, String sourceModule, Long sourceTaskId) {
        jdbcTemplate.update(
                """
                        update task_instance
                        set status = 'CANCELLED', update_time = ?
                        where tenant_id = ? and source_module = ? and source_task_id = ? and status = 'PENDING'
                        """,
                LocalDateTime.now(), tenantId, sourceModule, sourceTaskId
        );
    }

    private PageResponse<TaskVO.TaskItemVO> listTasks(CurrentUser currentUser, String status, long pageNo, long pageSize) {
        long safePageNo = pageNo <= 0 ? 1 : pageNo;
        long safePageSize = pageSize <= 0 ? 10 : pageSize;
        List<Object> params = new ArrayList<>();
        String where = assignmentWhere(currentUser, params) + " and t.status = ?";
        params.add(status);
        String select = """
                select t.id, t.task_type as taskType, t.business_type as businessType, t.business_id as businessId,
                       t.business_title as businessTitle, t.title, t.description, t.status, t.source_module as sourceModule,
                       t.source_task_id as sourceTaskId, t.redirect_url as redirectUrl, t.due_time as dueTime,
                       t.completed_at as completedAt, t.create_time as createTime
                from task_instance t
                where
                """ + where + " order by t.create_time desc limit ? offset ?";
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safePageSize);
        queryParams.add((safePageNo - 1) * safePageSize);
        List<TaskVO.TaskItemVO> records = jdbcTemplate.query(select, new BeanPropertyRowMapper<>(TaskVO.TaskItemVO.class), queryParams.toArray());
        Long total = jdbcTemplate.queryForObject("select count(1) from task_instance t where " + where, Long.class, params.toArray());
        PageResponse<TaskVO.TaskItemVO> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total == null ? 0 : total);
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        return response;
    }

    private long countByType(CurrentUser currentUser, String taskType) {
        List<Object> params = new ArrayList<>();
        String where = assignmentWhere(currentUser, params) + " and t.status = 'PENDING'";
        if (taskType != null) {
            where += " and t.task_type = ?";
            params.add(taskType);
        }
        Long count = jdbcTemplate.queryForObject("select count(1) from task_instance t where " + where, Long.class, params.toArray());
        return count == null ? 0 : count;
    }

    private String assignmentWhere(CurrentUser currentUser, List<Object> params) {
        Long tenantId = com.legendary.invention.common.constant.PlatformConstants.PLATFORM_TENANT_ID;
        Long userId = currentUser.getUserId();
        params.add(tenantId);
        params.add(userId);
        List<Long> roleIds = listRoleIds(tenantId, userId);
        StringBuilder where = new StringBuilder("t.tenant_id = ? and (t.assignee_user_id = ?");
        if (!roleIds.isEmpty()) {
            where.append(" or t.assignee_role_id in (");
            for (int i = 0; i < roleIds.size(); i++) {
                if (i > 0) {
                    where.append(", ");
                }
                where.append("?");
                params.add(roleIds.get(i));
            }
            where.append(")");
        }
        if (hasAnyPermission(currentUser.getPermissions(), Set.of("approval:approve", "evaluation:score", "evaluation:review", "*"))) {
            // Department ownership is modeled now; until employee-department binding exists, privileged handlers can see department tasks.
            where.append(" or t.assignee_dept_id is not null");
        }
        where.append(")");
        return where.toString();
    }

    private List<Long> listRoleIds(Long tenantId, Long userId) {
        return jdbcTemplate.queryForList(
                "select role_id from sys_user_role where tenant_id = ? and user_id = ? and deleted = 0",
                Long.class,
                tenantId,
                userId
        );
    }

    private boolean hasAnyPermission(Set<String> permissions, Set<String> expected) {
        if (permissions == null || permissions.isEmpty()) {
            return false;
        }
        return expected.stream().anyMatch(permissions::contains);
    }
}

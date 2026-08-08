package com.lumira.saas.modules.system.department.infrastructure;

import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.system.department.repository.SystemDepartmentRepository;
import com.lumira.saas.modules.system.department.vo.DepartmentVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

/** MyBatis/JDBC adapter for the system-department persistence boundary. */
@Repository
public class JdbcSystemDepartmentRepository implements SystemDepartmentRepository {
    private final MyBatisQueryOperations database;

    public JdbcSystemDepartmentRepository(MyBatisQueryOperations database) {
        this.database = database;
    }

    @Override
    public List<DepartmentVO> findAllActive() {
        return database.query(departmentSelect() + " order by d.sort_no asc, d.id asc", new BeanPropertyRowMapper<>(DepartmentVO.class));
    }

    @Override
    public DepartmentVO findActiveById(Long departmentId) {
        List<DepartmentVO> rows = database.query(
                departmentSelect() + " and d.id = ?",
                new BeanPropertyRowMapper<>(DepartmentVO.class),
                departmentId
        );
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @Override
    public DepartmentCreateResult create(DepartmentCreate command) {
        int writeCount = database.update(
                """
                        insert into sys_department (
                            parent_id, dept_code, dept_name, sort_no, status,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                command.parentId(),
                command.deptCode(),
                command.deptName(),
                command.sortNo() == null ? 0 : command.sortNo(),
                command.status(),
                command.actor().userId(),
                command.actor().userUuid(),
                command.actor().userId(),
                command.actor().userUuid()
        );
        Long departmentId = writeCount == 1 ? database.queryForObject("select last_insert_id()", Long.class) : null;
        return new DepartmentCreateResult(writeCount, departmentId);
    }

    @Override
    public int update(DepartmentUpdate command) {
        return database.update(
                """
                        update sys_department
                        set parent_id = ?,
                            dept_code = ?,
                            dept_name = ?,
                            sort_no = ?,
                            status = ?,
                            updated_by = ?,
                            updated_by_uuid = ?,
                            updated_at = ?
                        where id = ?
                          and dept_code = ?
                          and status = ?
                          and deleted = 0
                        """,
                command.parentId(),
                command.deptCode(),
                command.deptName(),
                command.sortNo() == null ? 0 : command.sortNo(),
                command.status(),
                command.actor().userId(),
                command.actor().userUuid(),
                LocalDateTime.now(),
                command.departmentId(),
                command.expectedDeptCode(),
                command.expectedStatus()
        );
    }

    @Override
    public boolean hasActiveChildren(Long departmentId) {
        return database.exists("select 1 from sys_department where parent_id = ? and deleted = 0 limit 1", departmentId);
    }

    @Override
    public boolean hasAssignedActiveUsers(Long departmentId) {
        return database.exists(
                """
                        select 1
                        from sys_user_department ud
                        join sys_user u
                          on u.id = ud.user_id
                         and u.uuid = ud.user_uuid
                         and u.deleted = 0
                        where ud.dept_id = ?
                          and ud.user_uuid is not null
                          and trim(ud.user_uuid) <> ''
                          and ud.deleted = 0
                        limit 1
                        """,
                departmentId
        );
    }

    @Override
    public int softDelete(DepartmentVersion department, Actor actor, LocalDateTime updatedAt) {
        return database.update(
                """
                        update sys_department
                        set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and dept_code = ? and status = ? and deleted = 0
                        """,
                actor.userId(), actor.userUuid(), updatedAt,
                department.departmentId(), department.deptCode(), department.status()
        );
    }

    @Override
    public int retireClosureForDescendant(Long departmentId) {
        return database.update(
                """
                        update sys_department_closure
                        set deleted = 1
                        where descendant_id = ?
                        """,
                departmentId
        );
    }

    @Override
    public List<Long> findActiveSubtreeIds(Long rootDepartmentId) {
        return database.queryForList(
                """
                        with recursive dept_tree as (
                            select id
                            from sys_department
                            where id = ? and deleted = 0
                            union all
                            select child.id
                            from sys_department child
                            join dept_tree parent on parent.id = child.parent_id
                            where child.deleted = 0
                        )
                        select id from dept_tree
                        """,
                Long.class,
                rootDepartmentId
        );
    }

    @Override
    public int retireClosureForDescendants(List<Long> departmentIds) {
        if (departmentIds == null || departmentIds.isEmpty()) {
            return 0;
        }
        String placeholders = "?,".repeat(departmentIds.size()).replaceFirst(",$", "");
        return database.update(
                "update sys_department_closure set deleted = 1 where descendant_id in (" + placeholders + ")",
                new ArrayList<>(departmentIds).toArray()
        );
    }

    @Override
    public void ensureSelfClosure(Long departmentId, LocalDateTime createdAt) {
        database.update(
                """
                        insert into sys_department_closure (ancestor_id, descendant_id, depth, deleted, created_at)
                        values (?, ?, 0, 0, ?)
                        on duplicate key update
                            deleted = case
                                when exists (select 1 from sys_department a where a.id = values(ancestor_id) and a.deleted = 0)
                                 and exists (select 1 from sys_department d where d.id = values(descendant_id) and d.deleted = 0)
                                then 0 else deleted end,
                            depth = case
                                when exists (select 1 from sys_department a where a.id = values(ancestor_id) and a.deleted = 0)
                                 and exists (select 1 from sys_department d where d.id = values(descendant_id) and d.deleted = 0)
                                then values(depth) else depth end
                        """,
                departmentId, departmentId, createdAt
        );
    }

    @Override
    public void ensureInheritedClosure(Long departmentId, Long parentDepartmentId, LocalDateTime createdAt) {
        database.update(
                """
                        insert into sys_department_closure (ancestor_id, descendant_id, depth, deleted, created_at)
                        select closure.ancestor_id, ?, closure.depth + 1, 0, ?
                        from sys_department_closure closure
                        join sys_department ancestor on ancestor.id = closure.ancestor_id and ancestor.deleted = 0
                        where closure.descendant_id = ?
                          and closure.deleted = 0
                        on duplicate key update
                            deleted = case
                                when exists (select 1 from sys_department a where a.id = values(ancestor_id) and a.deleted = 0)
                                 and exists (select 1 from sys_department d where d.id = values(descendant_id) and d.deleted = 0)
                                then 0 else deleted end,
                            depth = case
                                when exists (select 1 from sys_department a where a.id = values(ancestor_id) and a.deleted = 0)
                                 and exists (select 1 from sys_department d where d.id = values(descendant_id) and d.deleted = 0)
                                then values(depth) else depth end
                        """,
                departmentId, createdAt, parentDepartmentId
        );
    }

    @Override
    public boolean existsActiveDeptCode(String deptCode, Long excludedDepartmentId) {
        return database.exists(
                """
                        select 1
                        from sys_department
                        where dept_code = ?
                          and (? is null or id <> ?)
                        limit 1
                        """,
                deptCode, excludedDepartmentId, excludedDepartmentId
        );
    }

    private String departmentSelect() {
        return """
                select d.id,
                       d.parent_id as parentId,
                       d.dept_code as deptCode,
                       d.dept_name as deptName,
                       d.sort_no as sortNo,
                       d.status,
                       coalesce(uc.user_count, 0) as userCount,
                       d.created_at as createdAt,
                       d.updated_at as updatedAt
                from sys_department d
                left join (
                    select ud.dept_id, count(distinct ud.user_id) as user_count
                    from sys_user_department ud
                    join sys_user u
                      on u.id = ud.user_id
                     and u.uuid = ud.user_uuid
                     and u.deleted = 0
                    where ud.user_uuid is not null
                      and trim(ud.user_uuid) <> ''
                      and ud.deleted = 0
                    group by ud.dept_id
                ) uc on uc.dept_id = d.id
                where d.deleted = 0
                """;
    }
}

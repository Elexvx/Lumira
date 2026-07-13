package com.lumira.saas.modules.system.workorder.infrastructure;

import com.lumira.common.vo.PageResponse;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.system.workorder.repository.WorkOrderFeedbackRepository;
import com.lumira.saas.modules.system.workorder.vo.WorkOrderFeedbackVO;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class JdbcWorkOrderFeedbackRepository implements WorkOrderFeedbackRepository {
    private static final String SELECT = """
            select id, title, detail_html as detailHtml,
                   priority, status, submitter_id as submitterId, submitter_uuid as submitterUuid, submitter_name as submitterName,
                   admin_reply as adminReply, handled_by as handledBy, handled_at as handledAt,
                   created_at as createdAt, updated_at as updatedAt
            """;
    private final MyBatisQueryOperations database;

    public JdbcWorkOrderFeedbackRepository(MyBatisQueryOperations database) { this.database = database; }

    @Override
    public PageResponse<WorkOrderFeedbackVO.WorkOrderRecord> findPage(Filter filter, long pageNo, long pageSize) {
        StringBuilder where = new StringBuilder(" from sys_work_order_feedback where deleted = 0");
        List<Object> params = new ArrayList<>();
        applyOwner(where, params, filter.owner());
        if (StringUtils.hasText(filter.keyword())) {
            String like = "%" + filter.keyword().trim() + "%";
            where.append(" and (title like ? or submitter_name like ?)");
            params.add(like); params.add(like);
        }
        if (StringUtils.hasText(filter.status())) { where.append(" and status = ?"); params.add(filter.status()); }
        if (StringUtils.hasText(filter.priority())) { where.append(" and priority = ?"); params.add(filter.priority()); }
        long safePageNo = Math.max(1L, pageNo);
        long safePageSize = Math.max(1L, Math.min(pageSize, 100L));
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safePageSize); queryParams.add((safePageNo - 1L) * safePageSize);
        List<WorkOrderFeedbackVO.WorkOrderRecord> records = database.query(
                SELECT + where + " order by updated_at desc, id desc limit ? offset ?",
                new BeanPropertyRowMapper<>(WorkOrderFeedbackVO.WorkOrderRecord.class), queryParams.toArray());
        long total = safePageNo == 1 && records.size() < safePageSize ? records.size()
                : valueOrZero(database.queryForObject("select count(1)" + where, Long.class, params.toArray()));
        PageResponse<WorkOrderFeedbackVO.WorkOrderRecord> response = new PageResponse<>();
        response.setRecords(records); response.setTotal(total); response.setPageNo(safePageNo); response.setPageSize(safePageSize);
        return response;
    }

    @Override
    public WorkOrderFeedbackVO.WorkOrderRecord findById(Long id, Owner owner) {
        StringBuilder where = new StringBuilder(" from sys_work_order_feedback where id = ? and deleted = 0");
        List<Object> params = new ArrayList<>(List.of(id));
        applyOwner(where, params, owner);
        return database.queryForObject(SELECT + where,
                new BeanPropertyRowMapper<>(WorkOrderFeedbackVO.WorkOrderRecord.class), params.toArray());
    }

    @Override
    public Long insert(String title, String detailHtml, String priority, String initialStatus, Long userId, String uuid, String username) {
        int inserted = database.update("""
                insert into sys_work_order_feedback (
                    title, detail_html, priority, status, submitter_id, submitter_uuid, submitter_name,
                    created_by, created_by_uuid, created_at, updated_by, updated_by_uuid, updated_at, deleted
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), ?, ?, now(), 0)
                """, title, detailHtml, priority, initialStatus, userId, uuid, username, userId, uuid, userId, uuid);
        return inserted == 1 ? database.queryForObject("select last_insert_id()", Long.class) : null;
    }

    @Override
    public int updateStatus(Long id, String expectedStatus, Long submitterId, String submitterUuid,
                            String status, boolean terminalStatus, String adminReply, Long userId, String userUuid) {
        return database.update("""
                update sys_work_order_feedback set status = ?, admin_reply = ?, handled_by = ?,
                    handled_at = case when ? then now() else handled_at end,
                    updated_by = ?, updated_by_uuid = ?, updated_at = now()
                where id = ? and status = ? and submitter_id = ? and submitter_uuid = ? and deleted = 0
                """, status, adminReply, userId, terminalStatus, userId, userUuid, id, expectedStatus, submitterId, submitterUuid);
    }

    @Override
    public List<PolicyItem> findEnabledPolicyItems(String dictionaryCode) {
        return database.query("""
                select i.item_label, i.item_value, i.remark, i.sort_no
                from sys_dict_type t join sys_dict_item i on i.dict_type_id = t.id
                where t.dict_code = ? and t.status = 'ENABLED' and t.deleted = 0
                  and i.status = 'ENABLED' and i.deleted = 0
                order by i.sort_no, i.id
                """, (rs, rowNum) -> new PolicyItem(rs.getString("item_label"), rs.getString("item_value"),
                rs.getString("remark"), rs.getInt("sort_no")), dictionaryCode);
    }

    private void applyOwner(StringBuilder sql, List<Object> params, Owner owner) {
        if (owner != null && owner.restricted()) {
            sql.append(" and submitter_id = ? and submitter_uuid = ?");
            params.add(owner.userId()); params.add(owner.userUuid());
        }
    }

    private long valueOrZero(Long value) { return value == null ? 0L : value; }
}

package com.lumira.saas.modules.system.user.infrastructure;

import com.lumira.saas.modules.system.user.repository.UserExportTaskWorkerRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcUserExportTaskWorkerRepository implements UserExportTaskWorkerRepository {
    private static final String MODULE = "system:user";
    private static final String PENDING = "PENDING";
    private static final String RUNNING = "RUNNING";
    private static final String SUCCESS = "SUCCESS";
    private static final String FAILED = "FAILED";
    private final JdbcTemplate database;

    public JdbcUserExportTaskWorkerRepository(JdbcTemplate database) { this.database = database; }

    @Override
    public List<TaskClaim> claim(int limit, String workerId, String claimToken, LocalDateTime claimedAt, LocalDateTime expiresAt) {
        database.update("""
                update sys_export_task t
                join (select t.id from sys_export_task t
                      where t.deleted = 0 and t.module_key = ? and t.created_by > 0
                        and t.created_by_uuid is not null and t.created_by_uuid <> ''
                        and (t.status = ? or (t.status = ? and t.claim_expires_at is not null and t.claim_expires_at <= ?))
                      order by t.created_at asc, t.id asc limit ?) picked on picked.id = t.id
                set t.status = ?, t.started_at = coalesce(t.started_at, ?), t.finished_at = null,
                    t.error_message = null, t.claimed_by = ?, t.claim_token = ?, t.claim_expires_at = ?
                where t.deleted = 0
                """, MODULE, PENDING, RUNNING, claimedAt, limit, RUNNING, claimedAt, workerId, claimToken, expiresAt);
        return database.query("""
                select id, module_key as moduleKey, status, request_payload as requestPayload,
                       created_by as createdBy, created_by_uuid as createdByUuid, claim_token as claimToken
                from sys_export_task where deleted = 0 and module_key = ? and claim_token = ?
                order by created_at asc, id asc
                """, (rs, rowNum) -> new TaskClaim(rs.getLong("id"), rs.getString("moduleKey"), rs.getString("status"),
                rs.getString("requestPayload"), rs.getLong("createdBy"), rs.getString("createdByUuid"), rs.getString("claimToken")),
                MODULE, claimToken);
    }

    @Override
    public int markSucceeded(TaskClaim task, Long fileId, String fileName, LocalDateTime finishedAt) {
        return database.update("""
                update sys_export_task set status = ?, file_id = ?, file_name = ?, error_message = null, finished_at = ?,
                    claimed_by = null, claim_token = null, claim_expires_at = null
                where id = ? and module_key = ? and created_by = ? and created_by_uuid = ?
                  and claim_token = ? and deleted = 0 and status = ?
                """, SUCCESS, fileId, fileName, finishedAt, task.id(), task.moduleKey(), task.createdBy(),
                task.createdByUuid(), task.claimToken(), RUNNING);
    }

    @Override
    public int markFailed(TaskClaim task, String errorMessage, LocalDateTime finishedAt) {
        return database.update("""
                update sys_export_task set status = ?, error_message = ?, finished_at = ?,
                    claimed_by = null, claim_token = null, claim_expires_at = null
                where id = ? and module_key = ? and created_by = ? and created_by_uuid = ?
                  and claim_token = ? and deleted = 0 and status = ?
                """, FAILED, errorMessage, finishedAt, task.id(), task.moduleKey(), task.createdBy(),
                task.createdByUuid(), task.claimToken(), RUNNING);
    }
}

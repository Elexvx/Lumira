package com.lumira.saas.modules.export;

import com.lumira.api.export.ExportTaskQueuePort;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** JDBC owner of durable task claiming and compare-and-set completion. */
@Repository
public class JdbcExportTaskQueueRepository implements ExportTaskQueuePort {
    private static final String PENDING = "PENDING";
    private static final String RUNNING = "RUNNING";
    private static final String SUCCESS = "SUCCESS";
    private static final String FAILED = "FAILED";

    private final JdbcTemplate database;

    public JdbcExportTaskQueueRepository(JdbcTemplate database) {
        this.database = database;
    }

    @Override
    public List<ExportTaskClaim> claim(
            String moduleKey,
            int limit,
            String workerId,
            String claimToken,
            LocalDateTime claimedAt,
            LocalDateTime expiresAt
    ) {
        requireClaimArguments(moduleKey, limit, workerId, claimToken, claimedAt, expiresAt);
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
                """, moduleKey, PENDING, RUNNING, claimedAt, limit, RUNNING, claimedAt, workerId, claimToken, expiresAt);
        return database.query("""
                select id, module_key as moduleKey, status, request_payload as requestPayload,
                       created_by as createdBy, created_by_uuid as createdByUuid, claim_token as claimToken
                from sys_export_task where deleted = 0 and module_key = ? and claim_token = ?
                order by created_at asc, id asc
                """, (resultSet, rowNumber) -> new ExportTaskClaim(
                        resultSet.getLong("id"),
                        resultSet.getString("moduleKey"),
                        resultSet.getString("status"),
                        resultSet.getString("requestPayload"),
                        resultSet.getLong("createdBy"),
                        resultSet.getString("createdByUuid"),
                        resultSet.getString("claimToken")
                ), moduleKey, claimToken);
    }

    @Override
    public int markSucceeded(ExportTaskClaim task, Long fileId, String fileName, LocalDateTime finishedAt) {
        return database.update("""
                update sys_export_task set status = ?, file_id = ?, file_name = ?, error_message = null, finished_at = ?,
                    claimed_by = null, claim_token = null, claim_expires_at = null
                where id = ? and module_key = ? and created_by = ? and created_by_uuid = ?
                  and claim_token = ? and deleted = 0 and status = ?
                """, SUCCESS, fileId, fileName, finishedAt, task.id(), task.moduleKey(), task.createdBy(),
                task.createdByUuid(), task.claimToken(), RUNNING);
    }

    @Override
    public int markFailed(ExportTaskClaim task, String errorMessage, LocalDateTime finishedAt) {
        return database.update("""
                update sys_export_task set status = ?, error_message = ?, finished_at = ?,
                    claimed_by = null, claim_token = null, claim_expires_at = null
                where id = ? and module_key = ? and created_by = ? and created_by_uuid = ?
                  and claim_token = ? and deleted = 0 and status = ?
                """, FAILED, errorMessage, finishedAt, task.id(), task.moduleKey(), task.createdBy(),
                task.createdByUuid(), task.claimToken(), RUNNING);
    }

    private void requireClaimArguments(
            String moduleKey,
            int limit,
            String workerId,
            String claimToken,
            LocalDateTime claimedAt,
            LocalDateTime expiresAt
    ) {
        if (!StringUtils.hasText(moduleKey)
                || limit < 1
                || !StringUtils.hasText(workerId)
                || !StringUtils.hasText(claimToken)
                || claimedAt == null
                || expiresAt == null) {
            throw new IllegalArgumentException("Export task claim arguments are invalid");
        }
    }
}

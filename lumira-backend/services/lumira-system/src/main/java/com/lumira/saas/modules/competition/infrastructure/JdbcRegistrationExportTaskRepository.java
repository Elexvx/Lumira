package com.lumira.saas.modules.competition.infrastructure;

import com.lumira.saas.modules.competition.repository.RegistrationExportTaskRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRegistrationExportTaskRepository implements RegistrationExportTaskRepository {
    private static final String MODULE = "competition:registration";
    private static final String PENDING = "PENDING";
    private static final String RUNNING = "RUNNING";
    private static final String SUCCESS = "SUCCESS";
    private static final String FAILED = "FAILED";

    private final JdbcTemplate database;

    public JdbcRegistrationExportTaskRepository(JdbcTemplate database) {
        this.database = database;
    }

    @Override
    public List<TaskClaim> claim(
            int limit,
            String workerId,
            String claimToken,
            LocalDateTime claimedAt,
            LocalDateTime expiresAt
    ) {
        database.update(
                """
                        update sys_export_task task
                        join (
                            select candidate.id
                              from sys_export_task candidate
                             where candidate.deleted = 0
                               and candidate.module_key = ?
                               and candidate.created_by > 0
                               and candidate.created_by_uuid is not null
                               and candidate.created_by_uuid <> ''
                               and (
                                   candidate.status = ?
                                   or (
                                       candidate.status = ?
                                       and candidate.claim_expires_at is not null
                                       and candidate.claim_expires_at <= ?
                                   )
                               )
                             order by candidate.created_at asc, candidate.id asc
                             limit ?
                        ) picked on picked.id = task.id
                           set task.status = ?,
                               task.started_at = coalesce(task.started_at, ?),
                               task.finished_at = null,
                               task.error_message = null,
                               task.claimed_by = ?,
                               task.claim_token = ?,
                               task.claim_expires_at = ?
                         where task.deleted = 0
                        """,
                MODULE,
                PENDING,
                RUNNING,
                claimedAt,
                limit,
                RUNNING,
                claimedAt,
                workerId,
                claimToken,
                expiresAt
        );
        return database.query(
                """
                        select id, module_key as moduleKey, status,
                               request_payload as requestPayload,
                               created_by as createdBy,
                               created_by_uuid as createdByUuid,
                               claim_token as claimToken
                          from sys_export_task
                         where deleted = 0
                           and module_key = ?
                           and claim_token = ?
                         order by created_at asc, id asc
                        """,
                (row, rowNum) -> new TaskClaim(
                        row.getLong("id"),
                        row.getString("moduleKey"),
                        row.getString("status"),
                        row.getString("requestPayload"),
                        row.getLong("createdBy"),
                        row.getString("createdByUuid"),
                        row.getString("claimToken")
                ),
                MODULE,
                claimToken
        );
    }

    @Override
    public int markSucceeded(TaskClaim task, Long fileId, String fileName, LocalDateTime finishedAt) {
        return database.update(
                """
                        update sys_export_task
                           set status = ?, file_id = ?, file_name = ?, error_message = null,
                               finished_at = ?, claimed_by = null, claim_token = null,
                               claim_expires_at = null
                         where id = ? and module_key = ?
                           and created_by = ? and created_by_uuid = ?
                           and claim_token = ? and deleted = 0 and status = ?
                        """,
                SUCCESS,
                fileId,
                fileName,
                finishedAt,
                task.id(),
                task.moduleKey(),
                task.createdBy(),
                task.createdByUuid(),
                task.claimToken(),
                RUNNING
        );
    }

    @Override
    public int markFailed(TaskClaim task, String errorMessage, LocalDateTime finishedAt) {
        return database.update(
                """
                        update sys_export_task
                           set status = ?, error_message = ?, finished_at = ?,
                               claimed_by = null, claim_token = null, claim_expires_at = null
                         where id = ? and module_key = ?
                           and created_by = ? and created_by_uuid = ?
                           and claim_token = ? and deleted = 0 and status = ?
                        """,
                FAILED,
                errorMessage,
                finishedAt,
                task.id(),
                task.moduleKey(),
                task.createdBy(),
                task.createdByUuid(),
                task.claimToken(),
                RUNNING
        );
    }
}

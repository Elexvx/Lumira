package com.lumira.saas.modules.competition.infrastructure;

import com.lumira.saas.modules.competition.infrastructure.persistence.BeanPropertyRowMapper;
import com.lumira.saas.modules.competition.infrastructure.persistence.CompetitionSqlOperations;
import com.lumira.saas.modules.competition.repository.CertificateRecordRepository;
import com.lumira.saas.modules.competition.vo.CertificateVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class JdbcCertificateRecordRepository implements CertificateRecordRepository {
    private final CompetitionSqlOperations database;
    public JdbcCertificateRecordRepository(CompetitionSqlOperations database) { this.database = database; }

    @Override public Long insertBatch(BatchCreate c) { int n = database.update("""
            insert into certificate_batch (batch_no, batch_name, template_id, template_version_id, competition_id, stage_id,
                source_type, source_ref_id, total_count, success_count, failed_count, status, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 'GENERATING', ?, ?, ?, ?, 0)
            """, c.batchNo(), c.batchName(), c.templateId(), c.templateVersionId(), c.competitionId(), c.stageId(),
            c.sourceType(), c.sourceRefId(), c.totalCount(), c.userId(), c.userUuid(), c.userId(), c.userUuid()); return n > 0 ? lastId() : null; }
    @Override public int completeBatch(Long id, int success, int failed, String status, String error, Long userId, String uuid, LocalDateTime at) { return database.update("update certificate_batch set success_count = ?, failed_count = ?, status = ?, error_message = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and created_by = ? and created_by_uuid = ? and deleted = 0", success, failed, status, error, userId, uuid, at, id, userId, uuid); }

    @Override public BatchPage findBatches(Long ownerId, String ownerUuid, long offset, long limit) { List<Object> p = new ArrayList<>(); String owner = owner("certificate_batch", ownerId, ownerUuid, p); Long total = database.queryForObject("select count(1) from certificate_batch where deleted = 0" + owner, Long.class, p.toArray()); p.add(offset); p.add(limit); List<CertificateVO.Batch> rows = database.query(batchSelect() + " from certificate_batch where deleted = 0" + owner + " order by created_at desc, id desc limit ?, ?", new BeanPropertyRowMapper<>(CertificateVO.Batch.class), p.toArray()); return new BatchPage(rows, total == null ? 0 : total); }
    @Override public BatchPage findBatchesForCompetition(Long ownerId, String ownerUuid, Long competitionId, long offset, long limit) { List<Object> p = new ArrayList<>(); String owner = owner("certificate_batch", ownerId, ownerUuid, p); p.add(competitionId); String scope = owner + " and certificate_batch.competition_id = ?"; Long total = database.queryForObject("select count(1) from certificate_batch where deleted = 0" + scope, Long.class, p.toArray()); p.add(offset); p.add(limit); List<CertificateVO.Batch> rows = database.query(batchSelect() + " from certificate_batch where deleted = 0" + scope + " order by created_at desc, id desc limit ?, ?", new BeanPropertyRowMapper<>(CertificateVO.Batch.class), p.toArray()); return new BatchPage(rows, total == null ? 0 : total); }
    @Override public CertificateVO.Batch findBatch(Long id, Long ownerId, String ownerUuid) { List<Object> p = new ArrayList<>(); p.add(id); String owner = owner("certificate_batch", ownerId, ownerUuid, p); return first(database.query(batchSelect() + " from certificate_batch where id = ? and deleted = 0" + owner, new BeanPropertyRowMapper<>(CertificateVO.Batch.class), p.toArray())); }

    @Override public RecordPage findRecords(String no, String name, String status, Long ownerId, String ownerUuid, long offset, long limit) { List<Object> p = new ArrayList<>(); StringBuilder w = new StringBuilder(" from certificate_record r left join certificate_template t on r.template_id = t.id where r.deleted = 0"); w.append(owner("r", ownerId, ownerUuid, p)); if (StringUtils.hasText(no)) { w.append(" and r.certificate_no like ?"); p.add("%" + no + "%"); } if (StringUtils.hasText(name)) { w.append(" and r.recipient_name like ?"); p.add("%" + name + "%"); } if (StringUtils.hasText(status)) { w.append(" and r.status = ?"); p.add(status); } Long total = database.queryForObject("select count(1)" + w, Long.class, p.toArray()); p.add(offset); p.add(limit); List<CertificateVO.Record> rows = database.query(recordSelect() + w + " order by r.created_at desc, r.id desc limit ?, ?", new BeanPropertyRowMapper<>(CertificateVO.Record.class), p.toArray()); return new RecordPage(rows, total == null ? 0 : total); }
    @Override public RecordPage findRecordsForCompetition(String no, String name, String status, Long ownerId, String ownerUuid, Long competitionId, long offset, long limit) { List<Object> p = new ArrayList<>(); StringBuilder w = new StringBuilder(" from certificate_record r left join certificate_template t on r.template_id = t.id where r.deleted = 0"); w.append(owner("r", ownerId, ownerUuid, p)); w.append(" and r.competition_id = ?"); p.add(competitionId); if (StringUtils.hasText(no)) { w.append(" and r.certificate_no like ?"); p.add("%" + no + "%"); } if (StringUtils.hasText(name)) { w.append(" and r.recipient_name like ?"); p.add("%" + name + "%"); } if (StringUtils.hasText(status)) { w.append(" and r.status = ?"); p.add(status); } Long total = database.queryForObject("select count(1)" + w, Long.class, p.toArray()); p.add(offset); p.add(limit); List<CertificateVO.Record> rows = database.query(recordSelect() + w + " order by r.created_at desc, r.id desc limit ?, ?", new BeanPropertyRowMapper<>(CertificateVO.Record.class), p.toArray()); return new RecordPage(rows, total == null ? 0 : total); }
    @Override public CertificateVO.Record findRecord(Long id) { return first(database.query(recordSelect() + " from certificate_record r left join certificate_template t on r.template_id = t.id where r.id = ? and r.deleted = 0 limit 1", new BeanPropertyRowMapper<>(CertificateVO.Record.class), id)); }
    @Override public CertificateVO.Record findRecord(Long id, Long ownerId, String ownerUuid) { List<Object> p = new ArrayList<>(); p.add(id); String owner = owner("r", ownerId, ownerUuid, p); return first(database.query(recordSelect() + " from certificate_record r left join certificate_template t on r.template_id = t.id where r.id = ? and r.deleted = 0" + owner + " limit 1", new BeanPropertyRowMapper<>(CertificateVO.Record.class), p.toArray())); }
    @Override public CertificateVO.Record findByPublicToken(String token) { return first(database.query(recordSelect() + " from certificate_record r left join certificate_template t on r.template_id = t.id where r.public_token = ? and r.deleted = 0 limit 1", new BeanPropertyRowMapper<>(CertificateVO.Record.class), token)); }
    @Override public CertificateVO.Record findByCertificateNo(String no) { return first(database.query(recordSelect() + " from certificate_record r left join certificate_template t on r.template_id = t.id where r.certificate_no = ? and r.deleted = 0 limit 1", new BeanPropertyRowMapper<>(CertificateVO.Record.class), no)); }

    @Override public int revoke(CertificateVO.Record r, String reason, Long userId, String uuid, Long ownerId, String ownerUuid, LocalDateTime at) { List<Object> p = new ArrayList<>(); p.add(reason); p.add(at); p.add(userId); p.add(uuid); p.add(at); p.add(r.getId()); p.add(r.getCertificateNo()); p.add(r.getBatchId()); p.add(r.getStatus()); String owner = owner("certificate_record", ownerId, ownerUuid, p); return database.update("update certificate_record set status = 'REVOKED', revoked_reason = ?, revoked_at = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and certificate_no = ? and batch_id = ? and status = ? and deleted = 0" + owner, p.toArray()); }
    @Override public int updateFile(CertificateVO.Record r, String url, Long userId, String uuid, Long ownerId, String ownerUuid, LocalDateTime at) { List<Object> p = new ArrayList<>(List.of(url, userId, uuid, at, r.getId(), r.getCertificateNo(), r.getBatchId(), r.getStatus())); String owner = owner("certificate_record", ownerId, ownerUuid, p); return database.update("update certificate_record set certificate_file_url = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and certificate_no = ? and batch_id = ? and status = ?" + owner, p.toArray()); }

    @Override public Long insertRecord(RecordCreate c) { int n = database.update("""
            insert into certificate_record (certificate_no, verification_code, public_token, batch_id, template_id, template_version_id,
                competition_id, stage_id, registration_id, project_id, team_id, user_id,
                recipient_name, recipient_type, competition_title, project_name, team_name, award_name,
                issue_date, expire_date, data_json, status, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'GENERATING', ?, ?, ?, ?, 0)
            """, c.certificateNo(), c.verificationCode(), c.publicToken(), c.batchId(), c.templateId(), c.templateVersionId(),
            c.competitionId(), c.stageId(), c.registrationId(), c.projectId(), c.teamId(), c.recipientUserId(),
            c.recipientName(), c.recipientType(), c.competitionTitle(), c.projectName(), c.teamName(), c.awardName(),
            c.issueDate(), c.expireDate(), c.dataJson(), c.actorUserId(), c.actorUserUuid(),
            c.actorUserId(), c.actorUserUuid()); return n > 0 ? lastId() : null; }
    @Override public int updateGeneratedFile(Long id, String no, Long batchId, String url, Long userId, String uuid, LocalDateTime at) { return database.update("update certificate_record set certificate_file_url = ?, status = 'ISSUED', updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and certificate_no = ? and batch_id = ? and status = 'GENERATING' and created_by = ? and created_by_uuid = ? and deleted = 0", url, userId, uuid, at, id, no, batchId, userId, uuid); }
    @Override public int markGenerationFailed(Long id, String no, Long batchId, Long userId, String uuid, LocalDateTime at) { return database.update("update certificate_record set status = 'FAILED', certificate_file_url = null, updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and certificate_no = ? and batch_id = ? and status in ('GENERATING', 'ISSUED') and created_by = ? and created_by_uuid = ? and deleted = 0", userId, uuid, at, id, no, batchId, userId, uuid); }
    @Override public void insertVerifyLog(Long id, String no, String type, String result, String ip, String agent) { database.update("insert into certificate_verify_log (certificate_id, certificate_no, query_type, query_result, client_ip, user_agent) values (?, ?, ?, ?, ?, ?)", id, no, type, result, ip, agent); }
    @Override public long countCertificateNumbers(String pattern) { Long value = database.queryForObject("select count(1) from certificate_record where certificate_no like ?", Long.class, pattern); return value == null ? 0 : value; }

    @Override
    public List<CertificateVO.AwardSource> findPublishedAwardSources() {
        return database.query("""
                select batch.id as reviewBatchId,
                       batch.batch_no as batchNo,
                       batch.batch_name as batchName,
                       batch.competition_id as competitionId,
                       competition.title as competitionTitle,
                       batch.stage_id as stageId,
                       stage.stage_name as stageName,
                       batch.candidate_count as candidateCount,
                       publication.publication_version as publicationVersion,
                       batch.published_at as publishedAt,
                       (
                           select count(1)
                             from competition_award_grant grant_record
                            where grant_record.review_batch_id = batch.id
                              and grant_record.status in ('GRANTED', 'ISSUED')
                              and grant_record.deleted = 0
                       ) as grantCount,
                       (
                           select count(1)
                             from competition_award_grant grant_record
                            where grant_record.review_batch_id = batch.id
                              and grant_record.status = 'ISSUED'
                              and grant_record.deleted = 0
                       ) as issuedCount
                  from competition_review_batch batch
                  join competition_review_publication publication
                    on publication.batch_id = batch.id
                   and publication.status = 'PUBLISHED'
                   and publication.deleted = 0
                   and publication.publication_version = (
                       select max(latest.publication_version)
                         from competition_review_publication latest
                        where latest.batch_id = batch.id
                          and latest.status = 'PUBLISHED'
                          and latest.deleted = 0
                   )
                  join aiadc_competition competition
                    on competition.id = batch.competition_id
                   and competition.deleted = 0
                  join competition_stage stage
                    on stage.id = batch.stage_id
                   and stage.competition_id = batch.competition_id
                   and stage.deleted = 0
                 where batch.status = 'PUBLISHED'
                   and batch.deleted = 0
                 order by batch.published_at desc, batch.id desc
        """, new BeanPropertyRowMapper<>(CertificateVO.AwardSource.class));
    }

    @Override
    public Integer findPublishedReviewCandidateCount(Long reviewBatchId) {
        return database.queryForObject("""
                select candidate_count
                  from competition_review_batch
                 where id = ? and status = 'PUBLISHED' and deleted = 0
                """, Integer.class, reviewBatchId);
    }

    @Override
    public String findPublishedReviewAwardRulesJson(Long reviewBatchId) {
        return database.queryForObject("""
                select award_rules_json
                  from competition_review_batch
                 where id = ? and status = 'PUBLISHED' and deleted = 0
                """, String.class, reviewBatchId);
    }

    @Override
    public int savePublishedReviewAwardRules(Long reviewBatchId, String awardRulesJson,
                                             Long userId, String userUuid, LocalDateTime updatedAt) {
        return database.update("""
                update competition_review_batch
                   set award_rules_json = ?,
                       updated_by = ?,
                       updated_by_uuid = ?,
                       updated_at = ?,
                       version = version + 1
                 where id = ? and status = 'PUBLISHED' and deleted = 0
                """, awardRulesJson, userId, userUuid, updatedAt, reviewBatchId);
    }

    @Override
    public int clearPublishedReviewAwardRules(Long reviewBatchId,
                                              Long userId, String userUuid, LocalDateTime updatedAt) {
        return database.update("""
                update competition_review_batch
                   set award_rules_json = null,
                       updated_by = ?,
                       updated_by_uuid = ?,
                       updated_at = ?,
                       version = version + 1
                 where id = ? and status = 'PUBLISHED' and deleted = 0
                """, userId, userUuid, updatedAt, reviewBatchId);
    }

    @Override
    public int revokeUnissuedAwardGrants(
            Long reviewBatchId,
            Long userId,
            String userUuid,
            LocalDateTime updatedAt
    ) {
        return database.update("""
                update competition_award_grant
                   set status = 'REVOKED',
                       updated_by = ?,
                       updated_by_uuid = ?,
                       updated_at = ?
                 where review_batch_id = ?
                   and status = 'GRANTED'
                   and certificate_record_id is null
                   and deleted = 0
                """, userId, userUuid, updatedAt, reviewBatchId);
    }

    @Override
    public int grantPublishedAwards(Long reviewBatchId, String awardName, int minRank, int maxRank,
                                    Long userId, String userUuid, LocalDateTime grantedAt) {
        return database.update("""
                insert into competition_award_grant (
                    publication_id, publication_version, review_batch_id, competition_id, stage_id,
                    candidate_id, registration_id, project_id, team_id, user_id, user_uuid,
                    recipient_name, competition_title, project_name, team_name, award_name,
                    rank_no, decision, status, granted_at,
                    created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                )
                select publication.id, publication.publication_version, batch.id,
                       batch.competition_id, batch.stage_id, candidate.id, registration.id,
                       registration.project_id, registration.team_id,
                       registration.owner_user_id, registration.owner_user_uuid,
                       coalesce(
                           nullif(json_unquote(json_extract(registration.team_snapshot_json, '$.teamName')), ''),
                           nullif(json_unquote(json_extract(registration.member_snapshot_json, '$[0].memberName')), ''),
                           concat('User #', registration.owner_user_id)
                       ),
                       competition.title,
                       coalesce(
                           nullif(json_unquote(json_extract(registration.project_snapshot_json, '$.title')), ''),
                           concat('Project #', registration.project_id)
                       ),
                       coalesce(
                           nullif(json_unquote(json_extract(registration.team_snapshot_json, '$.teamName')), ''),
                           concat('Team #', registration.team_id)
                       ),
                       ?,
                       aggregate.rank_no, aggregate.decision, 'GRANTED', ?,
                       ?, ?, ?, ?, 0
                  from competition_review_batch batch
                  join competition_review_publication publication
                    on publication.batch_id = batch.id
                   and publication.status = 'PUBLISHED' and publication.deleted = 0
                   and publication.publication_version = (
                       select max(latest.publication_version)
                         from competition_review_publication latest
                        where latest.batch_id = batch.id
                          and latest.status = 'PUBLISHED' and latest.deleted = 0
                   )
                  join competition_review_candidate candidate
                    on candidate.batch_id = batch.id and candidate.deleted = 0
                  join competition_review_aggregate aggregate
                    on aggregate.batch_id = batch.id
                   and aggregate.candidate_id = candidate.id
                   and aggregate.status = 'FINALIZED' and aggregate.deleted = 0
                  join competition_registration registration
                    on registration.id = candidate.registration_id and registration.deleted = 0
                  join aiadc_competition competition
                    on competition.id = batch.competition_id and competition.deleted = 0
                 where batch.id = ? and batch.status = 'PUBLISHED' and batch.deleted = 0
                   and aggregate.decision in ('PASS', 'ADVANCED')
                   and aggregate.rank_no between ? and ?
                on duplicate key update
                    award_name = if(
                        competition_award_grant.certificate_record_id is null
                            and competition_award_grant.status in ('GRANTED', 'REVOKED'),
                        values(award_name),
                        competition_award_grant.award_name
                    ),
                    rank_no = if(
                        competition_award_grant.certificate_record_id is null
                            and competition_award_grant.status in ('GRANTED', 'REVOKED'),
                        values(rank_no),
                        competition_award_grant.rank_no
                    ),
                    decision = if(
                        competition_award_grant.certificate_record_id is null
                            and competition_award_grant.status in ('GRANTED', 'REVOKED'),
                        values(decision),
                        competition_award_grant.decision
                    ),
                    updated_by = if(
                        competition_award_grant.certificate_record_id is null
                            and competition_award_grant.status in ('GRANTED', 'REVOKED'),
                        values(updated_by),
                        competition_award_grant.updated_by
                    ),
                    updated_by_uuid = if(
                        competition_award_grant.certificate_record_id is null
                            and competition_award_grant.status in ('GRANTED', 'REVOKED'),
                        values(updated_by_uuid),
                        competition_award_grant.updated_by_uuid
                    ),
                    updated_at = if(
                        competition_award_grant.certificate_record_id is null
                            and competition_award_grant.status in ('GRANTED', 'REVOKED'),
                        values(granted_at),
                        competition_award_grant.updated_at
                    ),
                    status = if(
                        competition_award_grant.certificate_record_id is null
                            and competition_award_grant.status in ('GRANTED', 'REVOKED'),
                        'GRANTED',
                        competition_award_grant.status
                    )
                """,
                awardName, grantedAt, userId, userUuid, userId, userUuid,
                reviewBatchId, minRank, maxRank);
    }

    @Override
    public List<CertificateVO.AwardGrant> findAwardGrants(Long reviewBatchId) {
        return database.query(
                awardGrantSelect() + " from competition_award_grant where review_batch_id = ? and deleted = 0 order by rank_no asc, id asc",
                new BeanPropertyRowMapper<>(CertificateVO.AwardGrant.class),
                reviewBatchId
        );
    }

    @Override
    public List<CertificateVO.AwardGrant> findAwardGrantsByIds(List<Long> grantIds) {
        if (grantIds == null || grantIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(grantIds.size(), "?"));
        return database.query(
                awardGrantSelect() + " from competition_award_grant where id in (" + placeholders + ")"
                        + " and status = 'GRANTED' and certificate_record_id is null and deleted = 0 order by id asc",
                new BeanPropertyRowMapper<>(CertificateVO.AwardGrant.class),
                grantIds.toArray()
        );
    }

    @Override
    public List<CertificateVO.AwardGrant> findAwardGrantsByAnyIds(List<Long> grantIds) {
        if (grantIds == null || grantIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(grantIds.size(), "?"));
        return database.query(
                awardGrantSelect() + " from competition_award_grant where id in (" + placeholders + ")"
                        + " and deleted = 0 order by id asc",
                new BeanPropertyRowMapper<>(CertificateVO.AwardGrant.class),
                grantIds.toArray()
        );
    }

    @Override
    public int linkAwardGrant(Long grantId, Long certificateRecordId,
                              Long userId, String userUuid, LocalDateTime updatedAt) {
        return database.update("""
                update competition_award_grant
                   set certificate_record_id = ?, status = 'ISSUED',
                       updated_by = ?, updated_by_uuid = ?, updated_at = ?
                 where id = ? and status = 'GRANTED' and certificate_record_id is null and deleted = 0
                """, certificateRecordId, userId, userUuid, updatedAt, grantId);
    }

    @Override
    public List<CertificateVO.Record> findMyCertificates(Long userId, String userUuid, List<Long> activeTeamIds) {
        List<Object> parameters = new java.util.ArrayList<>();
        parameters.add(userId);
        parameters.add(userUuid);
        parameters.addAll(normalizedTeamIds(activeTeamIds));
        return database.query(
                recordSelect() + ownedAwardCertificateWhere("", activeTeamIds) + " order by r.issue_date desc, r.id desc",
                new BeanPropertyRowMapper<>(CertificateVO.Record.class),
                parameters.toArray()
        );
    }

    @Override
    public CertificateVO.Record findMyCertificate(Long recordId, Long userId, String userUuid, List<Long> activeTeamIds) {
        List<Object> parameters = new java.util.ArrayList<>();
        parameters.add(recordId);
        parameters.add(userId);
        parameters.add(userUuid);
        parameters.addAll(normalizedTeamIds(activeTeamIds));
        return first(database.query(
                recordSelect() + ownedAwardCertificateWhere(" and r.id = ?", activeTeamIds) + " limit 1",
                new BeanPropertyRowMapper<>(CertificateVO.Record.class),
                parameters.toArray()
        ));
    }

    private Long lastId() { return database.queryForObject("select last_insert_id()", Long.class); }
    private static String owner(String alias, Long id, String uuid, List<Object> p) { if (id == null) return ""; p.add(id); p.add(uuid); return " and " + alias + ".created_by = ? and " + alias + ".created_by_uuid = ?"; }
    private static <T> T first(List<T> rows) { return rows.isEmpty() ? null : rows.getFirst(); }
    private static String batchSelect() { return "select id, batch_no as batchNo, batch_name as batchName, template_id as templateId, template_version_id as templateVersionId, competition_id as competitionId, stage_id as stageId, source_type as sourceType, source_ref_id as sourceRefId, total_count as totalCount, success_count as successCount, failed_count as failedCount, status, error_message as errorMessage, created_at as createdAt, updated_at as updatedAt"; }
    private static String recordSelect() { return "select r.id, r.certificate_no as certificateNo, r.verification_code as verificationCode, r.public_token as publicToken, r.batch_id as batchId, r.template_id as templateId, r.template_version_id as templateVersionId, t.template_name as templateName, r.competition_id as competitionId, r.registration_id as registrationId, r.project_id as projectId, r.team_id as teamId, r.user_id as userId, r.recipient_name as recipientName, r.recipient_type as recipientType, r.competition_title as competitionTitle, r.project_name as projectName, r.team_name as teamName, r.award_name as awardName, r.issue_date as issueDate, r.expire_date as expireDate, r.data_json as dataJson, r.certificate_file_url as certificateFileUrl, r.status, r.revoked_reason as revokedReason, r.revoked_at as revokedAt, r.created_at as createdAt, r.updated_at as updatedAt"; }
    private static String awardGrantSelect() { return "select id, publication_id as publicationId, publication_version as publicationVersion, review_batch_id as reviewBatchId, competition_id as competitionId, stage_id as stageId, candidate_id as candidateId, registration_id as registrationId, project_id as projectId, team_id as teamId, user_id as userId, user_uuid as userUuid, recipient_name as recipientName, competition_title as competitionTitle, project_name as projectName, team_name as teamName, award_name as awardName, rank_no as rankNo, decision, status, certificate_record_id as certificateRecordId, granted_at as grantedAt"; }
    private static String ownedAwardCertificateWhere(String prefix, List<Long> activeTeamIds) {
        List<Long> teamIds = normalizedTeamIds(activeTeamIds);
        String teamAccess = teamIds.isEmpty()
                ? ""
                : " or r.team_id in (" + String.join(",", java.util.Collections.nCopies(teamIds.size(), "?")) + ")";
        return " from certificate_record r"
                + " left join certificate_template t on r.template_id = t.id"
                + " join competition_registration registration on registration.id = r.registration_id and registration.deleted = 0"
                + " where r.deleted = 0 and r.status in ('ISSUED', 'REVOKED')" + prefix
                + " and ((registration.owner_user_id = ? and registration.owner_user_uuid = ?)"
                + teamAccess + ")";
    }

    private static List<Long> normalizedTeamIds(List<Long> teamIds) {
        return teamIds == null ? List.of() : teamIds.stream()
                .filter(java.util.Objects::nonNull)
                .filter(teamId -> teamId > 0)
                .distinct()
                .toList();
    }
}

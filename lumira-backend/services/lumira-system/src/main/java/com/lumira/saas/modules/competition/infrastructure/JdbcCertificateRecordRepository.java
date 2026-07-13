package com.lumira.saas.modules.competition.infrastructure;

import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.competition.repository.CertificateRecordRepository;
import com.lumira.saas.modules.competition.vo.CertificateVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class JdbcCertificateRecordRepository implements CertificateRecordRepository {
    private final MyBatisQueryOperations database;
    public JdbcCertificateRecordRepository(MyBatisQueryOperations database) { this.database = database; }

    @Override public Long insertBatch(BatchCreate c) { int n = database.update("""
            insert into certificate_batch (batch_no, batch_name, template_id, template_version_id, competition_id, stage_id,
                source_type, total_count, success_count, failed_count, status, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted)
            values (?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 'GENERATING', ?, ?, ?, ?, 0)
            """, c.batchNo(), c.batchName(), c.templateId(), c.templateVersionId(), c.competitionId(), c.stageId(), c.sourceType(), c.totalCount(), c.userId(), c.userUuid(), c.userId(), c.userUuid()); return n > 0 ? lastId() : null; }
    @Override public int completeBatch(Long id, int success, int failed, String status, String error, Long userId, String uuid, LocalDateTime at) { return database.update("update certificate_batch set success_count = ?, failed_count = ?, status = ?, error_message = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and created_by = ? and created_by_uuid = ? and deleted = 0", success, failed, status, error, userId, uuid, at, id, userId, uuid); }

    @Override public BatchPage findBatches(Long ownerId, String ownerUuid, long offset, long limit) { List<Object> p = new ArrayList<>(); String owner = owner("certificate_batch", ownerId, ownerUuid, p); Long total = database.queryForObject("select count(1) from certificate_batch where deleted = 0" + owner, Long.class, p.toArray()); p.add(offset); p.add(limit); List<CertificateVO.Batch> rows = database.query(batchSelect() + " from certificate_batch where deleted = 0" + owner + " order by created_at desc, id desc limit ?, ?", new BeanPropertyRowMapper<>(CertificateVO.Batch.class), p.toArray()); return new BatchPage(rows, total == null ? 0 : total); }
    @Override public CertificateVO.Batch findBatch(Long id, Long ownerId, String ownerUuid) { List<Object> p = new ArrayList<>(); p.add(id); String owner = owner("certificate_batch", ownerId, ownerUuid, p); return first(database.query(batchSelect() + " from certificate_batch where id = ? and deleted = 0" + owner, new BeanPropertyRowMapper<>(CertificateVO.Batch.class), p.toArray())); }

    @Override public RecordPage findRecords(String no, String name, String status, Long ownerId, String ownerUuid, long offset, long limit) { List<Object> p = new ArrayList<>(); StringBuilder w = new StringBuilder(" from certificate_record r left join certificate_template t on r.template_id = t.id where r.deleted = 0"); w.append(owner("r", ownerId, ownerUuid, p)); if (StringUtils.hasText(no)) { w.append(" and r.certificate_no like ?"); p.add("%" + no + "%"); } if (StringUtils.hasText(name)) { w.append(" and r.recipient_name like ?"); p.add("%" + name + "%"); } if (StringUtils.hasText(status)) { w.append(" and r.status = ?"); p.add(status); } Long total = database.queryForObject("select count(1)" + w, Long.class, p.toArray()); p.add(offset); p.add(limit); List<CertificateVO.Record> rows = database.query(recordSelect() + w + " order by r.created_at desc, r.id desc limit ?, ?", new BeanPropertyRowMapper<>(CertificateVO.Record.class), p.toArray()); return new RecordPage(rows, total == null ? 0 : total); }
    @Override public CertificateVO.Record findRecord(Long id) { return first(database.query(recordSelect() + " from certificate_record r left join certificate_template t on r.template_id = t.id where r.id = ? and r.deleted = 0 limit 1", new BeanPropertyRowMapper<>(CertificateVO.Record.class), id)); }
    @Override public CertificateVO.Record findRecord(Long id, Long ownerId, String ownerUuid) { List<Object> p = new ArrayList<>(); p.add(id); String owner = owner("r", ownerId, ownerUuid, p); return first(database.query(recordSelect() + " from certificate_record r left join certificate_template t on r.template_id = t.id where r.id = ? and r.deleted = 0" + owner + " limit 1", new BeanPropertyRowMapper<>(CertificateVO.Record.class), p.toArray())); }
    @Override public CertificateVO.Record findByPublicToken(String token) { return first(database.query(recordSelect() + " from certificate_record r left join certificate_template t on r.template_id = t.id where r.public_token = ? and r.deleted = 0 limit 1", new BeanPropertyRowMapper<>(CertificateVO.Record.class), token)); }
    @Override public CertificateVO.Record findByCertificateNo(String no) { return first(database.query(recordSelect() + " from certificate_record r left join certificate_template t on r.template_id = t.id where r.certificate_no = ? and r.deleted = 0 limit 1", new BeanPropertyRowMapper<>(CertificateVO.Record.class), no)); }

    @Override public int revoke(CertificateVO.Record r, String reason, Long userId, String uuid, Long ownerId, String ownerUuid, LocalDateTime at) { List<Object> p = new ArrayList<>(); p.add(reason); p.add(at); p.add(userId); p.add(uuid); p.add(at); p.add(r.getId()); p.add(r.getCertificateNo()); p.add(r.getBatchId()); p.add(r.getStatus()); String owner = owner("certificate_record", ownerId, ownerUuid, p); return database.update("update certificate_record set status = 'REVOKED', revoked_reason = ?, revoked_at = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and certificate_no = ? and batch_id = ? and status = ? and deleted = 0" + owner, p.toArray()); }
    @Override public int updateFile(CertificateVO.Record r, String url, Long userId, String uuid, Long ownerId, String ownerUuid, LocalDateTime at) { List<Object> p = new ArrayList<>(List.of(url, userId, uuid, at, r.getId(), r.getCertificateNo(), r.getBatchId(), r.getStatus())); String owner = owner("certificate_record", ownerId, ownerUuid, p); return database.update("update certificate_record set certificate_file_url = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and certificate_no = ? and batch_id = ? and status = ?" + owner, p.toArray()); }

    @Override public Long insertRecord(RecordCreate c) { int n = database.update("""
            insert into certificate_record (certificate_no, verification_code, public_token, batch_id, template_id, template_version_id,
                competition_id, stage_id, recipient_name, recipient_type, competition_title, project_name, team_name, award_name,
                issue_date, expire_date, data_json, status, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ISSUED', ?, ?, ?, ?, 0)
            """, c.certificateNo(), c.verificationCode(), c.publicToken(), c.batchId(), c.templateId(), c.templateVersionId(), c.competitionId(), c.stageId(), c.recipientName(), c.recipientType(), c.competitionTitle(), c.projectName(), c.teamName(), c.awardName(), c.issueDate(), c.expireDate(), c.dataJson(), c.userId(), c.userUuid(), c.userId(), c.userUuid()); return n > 0 ? lastId() : null; }
    @Override public int updateGeneratedFile(Long id, String no, Long batchId, String url, Long userId, String uuid, LocalDateTime at) { return database.update("update certificate_record set certificate_file_url = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and certificate_no = ? and batch_id = ? and status = 'ISSUED' and created_by = ? and created_by_uuid = ? and deleted = 0", url, userId, uuid, at, id, no, batchId, userId, uuid); }
    @Override public void insertVerifyLog(Long id, String no, String type, String result, String ip, String agent) { database.update("insert into certificate_verify_log (certificate_id, certificate_no, query_type, query_result, client_ip, user_agent) values (?, ?, ?, ?, ?, ?)", id, no, type, result, ip, agent); }
    @Override public long countCertificateNumbers(String pattern) { Long value = database.queryForObject("select count(1) from certificate_record where certificate_no like ?", Long.class, pattern); return value == null ? 0 : value; }

    private Long lastId() { return database.queryForObject("select last_insert_id()", Long.class); }
    private static String owner(String alias, Long id, String uuid, List<Object> p) { if (id == null) return ""; p.add(id); p.add(uuid); return " and " + alias + ".created_by = ? and " + alias + ".created_by_uuid = ?"; }
    private static <T> T first(List<T> rows) { return rows.isEmpty() ? null : rows.getFirst(); }
    private static String batchSelect() { return "select id, batch_no as batchNo, batch_name as batchName, template_id as templateId, template_version_id as templateVersionId, competition_id as competitionId, stage_id as stageId, source_type as sourceType, total_count as totalCount, success_count as successCount, failed_count as failedCount, status, error_message as errorMessage, created_at as createdAt, updated_at as updatedAt"; }
    private static String recordSelect() { return "select r.id, r.certificate_no as certificateNo, r.verification_code as verificationCode, r.public_token as publicToken, r.batch_id as batchId, r.template_id as templateId, r.template_version_id as templateVersionId, t.template_name as templateName, r.competition_id as competitionId, r.recipient_name as recipientName, r.recipient_type as recipientType, r.competition_title as competitionTitle, r.project_name as projectName, r.team_name as teamName, r.award_name as awardName, r.issue_date as issueDate, r.expire_date as expireDate, r.data_json as dataJson, r.certificate_file_url as certificateFileUrl, r.status, r.revoked_reason as revokedReason, r.revoked_at as revokedAt, r.created_at as createdAt, r.updated_at as updatedAt"; }
}

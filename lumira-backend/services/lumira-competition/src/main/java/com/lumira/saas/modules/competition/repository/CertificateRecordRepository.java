package com.lumira.saas.modules.competition.repository;

import com.lumira.saas.modules.competition.vo.CertificateVO;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface CertificateRecordRepository {
    Long insertBatch(BatchCreate command);
    int completeBatch(Long batchId, int success, int failed, String status, String errorMessage,
                      Long userId, String userUuid, LocalDateTime updatedAt);
    BatchPage findBatches(Long ownerId, String ownerUuid, long offset, long limit);
    BatchPage findBatchesForCompetition(Long ownerId, String ownerUuid, Long competitionId, long offset, long limit);
    CertificateVO.Batch findBatch(Long id, Long ownerId, String ownerUuid);
    RecordPage findRecords(String certificateNo, String recipientName, String status,
                           Long ownerId, String ownerUuid, long offset, long limit);
    RecordPage findRecordsForCompetition(String certificateNo, String recipientName, String status,
                                         Long ownerId, String ownerUuid, Long competitionId,
                                         long offset, long limit);
    CertificateVO.Record findRecord(Long id);
    CertificateVO.Record findRecord(Long id, Long ownerId, String ownerUuid);
    CertificateVO.Record findByPublicToken(String token);
    CertificateVO.Record findByCertificateNo(String certificateNo);
    int revoke(CertificateVO.Record record, String reason, Long userId, String userUuid,
               Long ownerId, String ownerUuid, LocalDateTime updatedAt);
    int updateFile(CertificateVO.Record record, String fileUrl, Long userId, String userUuid,
                   Long ownerId, String ownerUuid, LocalDateTime updatedAt);
    Long insertRecord(RecordCreate command);
    int updateGeneratedFile(Long id, String certificateNo, Long batchId, String fileUrl,
                            Long userId, String userUuid, LocalDateTime updatedAt);
    int markGenerationFailed(Long id, String certificateNo, Long batchId,
                             Long userId, String userUuid, LocalDateTime updatedAt);
    void insertVerifyLog(Long certificateId, String certificateNo, String queryType,
                         String queryResult, String clientIp, String userAgent);
    long countCertificateNumbers(String prefixPattern);
    List<CertificateVO.AwardSource> findPublishedAwardSources();
    int revokeUnissuedAwardGrants(Long reviewBatchId, Long userId, String userUuid, LocalDateTime updatedAt);
    int grantPublishedAwards(Long reviewBatchId, String awardName, int minRank, int maxRank,
                             Long userId, String userUuid, LocalDateTime grantedAt);
    List<CertificateVO.AwardGrant> findAwardGrants(Long reviewBatchId);
    List<CertificateVO.AwardGrant> findAwardGrantsByIds(List<Long> grantIds);
    List<CertificateVO.AwardGrant> findAwardGrantsByAnyIds(List<Long> grantIds);
    int linkAwardGrant(Long grantId, Long certificateRecordId,
                       Long userId, String userUuid, LocalDateTime updatedAt);
    List<CertificateVO.Record> findMyCertificates(Long userId, String userUuid, List<Long> activeTeamIds);
    CertificateVO.Record findMyCertificate(Long recordId, Long userId, String userUuid, List<Long> activeTeamIds);

    record BatchPage(List<CertificateVO.Batch> records, long total) {}
    record RecordPage(List<CertificateVO.Record> records, long total) {}
    record BatchCreate(String batchNo, String batchName, Long templateId, Long templateVersionId,
                       Long competitionId, Long stageId, String sourceType, Long sourceRefId, int totalCount,
                       Long userId, String userUuid) {}
    record RecordCreate(String certificateNo, String verificationCode, String publicToken, Long batchId,
                        Long templateId, Long templateVersionId, Long competitionId, Long stageId,
                        Long registrationId, Long projectId, Long teamId, Long recipientUserId,
                        String recipientName, String recipientType, String competitionTitle,
                        String projectName, String teamName, String awardName, LocalDate issueDate,
                        LocalDate expireDate, String dataJson, Long actorUserId, String actorUserUuid) {}
}

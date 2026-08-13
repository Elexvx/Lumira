package com.lumira.saas.modules.competition.repository;

import com.lumira.saas.modules.competition.vo.CompetitionRegistrationVO;
import java.util.List;

/**
 * Read boundary for the competition-registration bounded context.
 *
 * <p>Authorization, tenant identity and input validation remain in the application service. This
 * port owns only persisted registration, material, stage, payment and configuration lookups.</p>
 */
public interface RegistrationQueryRepository {

    RegistrationPage findRegistrations(RegistrationSearch search);

    PaymentRecordPage findPaymentRecords(PaymentRecordSearch search);

    List<CompetitionRegistrationVO.MaterialSubmission> findMaterialSubmissions(Long registrationId, Long competitionId);

    Long findMaterialSubmissionIdForOwner(Long registrationId, Long stageId, Long ownerUserId, String ownerUserUuid);

    boolean existsMaterialFile(Long registrationId, Long competitionId, Long fileId);

    List<CompetitionRegistrationVO.Stage> findStages(Long competitionId);

    List<CompetitionRegistrationVO.Stage> findReadableStages(Long competitionId);

    List<CompetitionRegistrationVO.StageReviewCandidate> findStageReviewCandidates(Long stageId, Long competitionId);

    List<String> findCompetitionPaymentProviders(Long competitionId);

    Long findPreliminaryStageId(Long competitionId);

    boolean hasSubmittedMaterial(Long registrationId, Long stageId);

    CompetitionDefinition findCompetition(Long competitionId);

    List<CollectedFieldConfiguration> findCollectedFieldConfigurations(Long competitionId);

    String findTeamSizeLimitsConfiguration(Long competitionId);

    long countConfirmedRegistrations(Long competitionId);

    CompetitionRegistrationVO.Registration findRegistration(Long registrationId);

    CompetitionRegistrationVO.Registration findRegistrationByPaymentOrder(String paymentOrderNo);

    CompetitionRegistrationVO.Stage findStage(Long stageId);

    CompetitionRegistrationVO.StageForm findStageForm(Long stageId);

    CompetitionRegistrationVO.StageForm findReadableStageForm(Long stageId);

    boolean hasPublishedPreliminaryAdvance(Long competitionId, Long registrationId);

    boolean hasPublishedPreliminaryAdvanceForOwner(Long competitionId, Long ownerUserId, String ownerUserUuid);

    record RegistrationSearch(
            Long ownerUserId,
            String ownerUserUuid,
            Long competitionId,
            String status,
            String keyword,
            boolean includeSnapshots,
            long offset,
            long limit
    ) {
    }

    record RegistrationPage(List<CompetitionRegistrationVO.Registration> records, long total) {
    }

    record PaymentRecordSearch(
            Long ownerUserId,
            String ownerUserUuid,
            Long competitionId,
            String keyword,
            String paymentStatus,
            String registrationStatus,
            String providerCode,
            long offset,
            long limit
    ) {
    }

    record PaymentRecordPage(List<CompetitionRegistrationVO.PaymentRecord> records, long total) {
    }

    record CompetitionDefinition(
            Long id,
            String code,
            String feeMode,
            Long entryFeeMinor,
            String currency,
            String registrationStart,
            String registrationEnd
    ) {
    }

    record CollectedFieldConfiguration(
            String itemType,
            String itemKey,
            String title,
            String contentJson,
            Object requiredFlag
    ) {
    }
}

package com.lumira.saas.modules.competition.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Write boundary for competition-registration state and its owned workflow records.
 */
public interface RegistrationWriteRepository extends RegistrationPersistencePort {

    int updateRegistration(UpdateRegistrationCommand command);

    int updateCollectionSchemaSnapshot(Long registrationId, String snapshotJson, LocalDateTime updatedAt);

    int cancelPaymentOrderTasks(Long registrationId, Long operatorUserId, String operatorUserUuid, LocalDateTime updatedAt);

    int cancelPendingRegistration(CancelRegistrationCommand command);

    Long createStage(CreateStageCommand command);

    int updateStage(UpdateStageCommand command);

    void upsertStageReviewResult(StageReviewResultCommand command);

    int createStageForm(CreateStageFormCommand command);

    int updateStageForm(UpdateStageFormCommand command);

    Long createMaterialSubmission(CreateMaterialSubmissionCommand command);

    int updateMaterialSubmission(UpdateMaterialSubmissionCommand command);

    void archiveMaterialValues(ArchiveMaterialValuesCommand command);

    void insertMaterialValues(Long submissionId, List<MaterialValueCommand> values);

    int enqueuePaymentOrderTask(EnqueuePaymentOrderTaskCommand command);

    int detachPaymentOrderForRetry(DetachPaymentOrderForRetryCommand command);

    int enqueuePaymentOrderRetryTask(EnqueuePaymentOrderTaskCommand command);

    List<PaymentOrderTask> claimPaymentOrderTasks(int limit, String claimToken, LocalDateTime now, LocalDateTime claimExpiresAt);

    int attachPaymentOrder(AttachPaymentOrderCommand command);

    int markPaymentOrderTaskSucceeded(PaymentOrderTaskCompletion command);

    int markPaymentOrderTaskFailed(PaymentOrderTaskFailure command);

    int confirmPaidRegistration(ConfirmPaidRegistrationCommand command);

    record UpdateRegistrationCommand(
            Long registrationId,
            String registrationNo,
            Long ownerUserId,
            String ownerUserUuid,
            String status,
            Long competitionId,
            Long teamId,
            Long projectId,
            String feeMode,
            Long entryFeeMinor,
            Integer memberCount,
            Long payableAmountMinor,
            String currency,
            String registrationSnapshotJson,
            String teamSnapshotJson,
            String projectSnapshotJson,
            String memberSnapshotJson,
            Long updatedBy,
            String updatedByUuid,
            LocalDateTime updatedAt
    ) {
    }

    record CancelRegistrationCommand(
            Long registrationId,
            String registrationNo,
            Long ownerUserId,
            String ownerUserUuid,
            Long operatorUserId,
            String operatorUserUuid,
            LocalDateTime updatedAt
    ) {
    }

    record CreateStageCommand(
            Long competitionId,
            String stageCode,
            String stageName,
            LocalDateTime materialSubmitStart,
            LocalDateTime materialSubmitEnd,
            LocalDateTime reviewStart,
            LocalDateTime reviewEnd,
            String status,
            Integer sort,
            String promotionRuleType,
            BigDecimal promotionRuleValue,
            String promotionTiePolicy,
            Long userId,
            String userUuid
    ) {
    }

    record UpdateStageCommand(
            Long stageId,
            Long competitionId,
            String stageCode,
            String stageName,
            LocalDateTime materialSubmitStart,
            LocalDateTime materialSubmitEnd,
            LocalDateTime reviewStart,
            LocalDateTime reviewEnd,
            String status,
            Integer sort,
            String promotionRuleType,
            BigDecimal promotionRuleValue,
            String promotionTiePolicy,
            Long userId,
            String userUuid,
            LocalDateTime updatedAt
    ) {
    }

    record StageReviewResultCommand(
            Long competitionId,
            Long stageId,
            Long registrationId,
            BigDecimal score,
            String decision,
            String reviewComment,
            LocalDateTime publishedAt,
            Long userId,
            String userUuid
    ) {
    }

    record CreateStageFormCommand(
            Long competitionId,
            Long stageId,
            String formName,
            String formSchemaJson,
            Integer version,
            String status,
            Long userId,
            String userUuid
    ) {
    }

    record UpdateStageFormCommand(
            Long formId,
            Long competitionId,
            Long stageId,
            String existingStatus,
            String formName,
            String formSchemaJson,
            Integer version,
            String status,
            Long userId,
            String userUuid,
            LocalDateTime updatedAt
    ) {
    }

    record CreateMaterialSubmissionCommand(
            Long registrationId,
            Long competitionId,
            Long stageId,
            Integer formVersion,
            Long userId,
            String userUuid,
            LocalDateTime submittedAt
    ) {
    }

    record UpdateMaterialSubmissionCommand(
            Long submissionId,
            Long registrationId,
            Long stageId,
            Integer formVersion,
            Long userId,
            String userUuid,
            LocalDateTime submittedAt,
            LocalDateTime updatedAt
    ) {
    }

    record ArchiveMaterialValuesCommand(
            Long submissionId,
            Long registrationId,
            Long stageId,
            Integer formVersion,
            Long userId,
            String userUuid,
            Integer revisionNo
    ) {
    }

    record MaterialValueCommand(
            String fieldKey,
            String fieldType,
            String textValue,
            Long fileId,
            String jsonValue
    ) {
    }

    record EnqueuePaymentOrderTaskCommand(
            Long registrationId,
            String providerCode,
            String clientIp,
            String notifyUrl,
            String returnUrl,
            String ownerUserUuid,
            Long simulatedRoleId,
            LocalDateTime nextRetryAt,
            Long operatorUserId,
            String operatorUserUuid
    ) {
    }

    record PaymentOrderTask(
            Long id,
            Long registrationId,
            String providerCode,
            String clientIp,
            String notifyUrl,
            String returnUrl,
            String ownerUserUuid,
            Long simulatedRoleId,
            Integer attemptNo,
            String claimToken
    ) {
    }

    record DetachPaymentOrderForRetryCommand(
            Long registrationId,
            String registrationNo,
            Long ownerUserId,
            String ownerUserUuid,
            String expectedPaymentOrderNo,
            Long operatorUserId,
            String operatorUserUuid,
            LocalDateTime updatedAt
    ) {
    }

    record AttachPaymentOrderCommand(
            Long registrationId,
            String registrationNo,
            Long ownerUserId,
            String ownerUserUuid,
            Long payableAmountMinor,
            String currency,
            String paymentOrderNo,
            LocalDateTime updatedAt
    ) {
    }

    record PaymentOrderTaskCompletion(
            Long taskId,
            Long registrationId,
            String ownerUserUuid,
            String claimToken,
            String message,
            LocalDateTime updatedAt
    ) {
    }

    record PaymentOrderTaskFailure(
            Long taskId,
            Long registrationId,
            String ownerUserUuid,
            String claimToken,
            String message,
            int maxRetry,
            LocalDateTime nextRetryAt,
            LocalDateTime updatedAt
    ) {
    }

    record ConfirmPaidRegistrationCommand(
            Long registrationId,
            Long ownerUserId,
            String ownerUserUuid,
            String participantNo,
            String paymentOrderNo,
            LocalDateTime updatedAt
    ) {
    }
}

package com.lumira.saas.modules.competition.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.PaymentInternalApi;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.dictionary.DictionaryItemLookupPort;
import com.lumira.api.payment.PaymentCreateOrderRequestDTO;
import com.lumira.api.payment.PaymentOrderDTO;
import com.lumira.api.payment.PaymentCheckoutOptionDTO;
import com.lumira.api.project.ProjectSnapshotPort;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.TrustedCurrentUserResolver;
import com.lumira.common.security.data.DataPermissionDecision;
import com.lumira.common.security.data.DataPermissionResolver;
import com.lumira.common.security.data.DataPermissionRule;
import com.lumira.common.security.data.DataScopeType;
import com.lumira.common.vo.PageResponse;
import com.lumira.saas.modules.competition.dto.CompetitionRegistrationDTO;
import com.lumira.saas.modules.competition.infrastructure.CompetitionRegistrationPersistenceAssemblyConfiguration;
import com.lumira.saas.modules.competition.repository.RegistrationDatasetRepository;
import com.lumira.saas.modules.competition.repository.RegistrationQueryRepository;
import com.lumira.saas.modules.competition.repository.RegistrationWriteRepository;
import com.lumira.saas.modules.competition.vo.CompetitionRegistrationVO;
import com.lumira.team.api.TeamInternalApi;
import com.lumira.team.api.TeamMemberDTO;
import com.lumira.team.api.TeamSummaryDTO;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Import(CompetitionRegistrationPersistenceAssemblyConfiguration.class)
public class CompetitionRegistrationAppService {

    private static final String REGISTRATION_DATA_SCOPE_RESOURCE = "competition:registration";
    private static final String REGISTRATION_VIEW_PERMISSION = "aiadc:registration:view";
    private static final String REGISTRATION_CREATE_PERMISSION = "aiadc:registration:create";
    private static final String REGISTRATION_UPDATE_PERMISSION = "aiadc:registration:update";
    private static final String REGISTRATION_PAY_PERMISSION = "aiadc:registration:pay";
    private static final String MATERIAL_VIEW_PERMISSION = "aiadc:material:view";
    private static final String MATERIAL_SUBMIT_PERMISSION = "aiadc:material:submit";
    private static final String MATERIAL_DOWNLOAD_PERMISSION = "registration:material:download";
    private static final String DATASET_VIEW_PERMISSION = "registration:dataset:view";
    private static final String DATASET_VIEW_SENSITIVE_PERMISSION = "registration:dataset:view-sensitive";
    private static final String DATASET_EXPORT_PERMISSION = "registration:dataset:export";
    private static final String DATASET_EXPORT_SENSITIVE_PERMISSION = "registration:dataset:export-sensitive";
    private static final String STAGE_VIEW_PERMISSION = "aiadc:stage:view";
    private static final String STAGE_MANAGE_PERMISSION = "aiadc:stage:manage";
    private static final String PAYMENT_ORDER_VIEW_PERMISSION = "payment:order:view";
    private static final String PAYMENT_OWNER_DISABLED_MESSAGE = "Payment owner is disabled";
    private static final Set<String> REGISTRATION_STATUSES = Set.of("DRAFT", "PENDING_PAYMENT", "PAID", "CONFIRMED", "CANCELLED");
    private static final Set<String> STAGE_CODES = Set.of("PRELIMINARY", "FINAL");
    private static final Set<String> STAGE_STATUSES = Set.of("DRAFT", "ENABLED", "DISABLED", "CLOSED");
    private static final Set<String> REVIEW_DECISIONS = Set.of("PENDING", "ADVANCED", "ELIMINATED");
    private static final Set<String> PROMOTION_RULE_TYPES = Set.of("PERCENTAGE", "COUNT", "MANUAL");
    private static final Set<String> FORM_STATUSES = Set.of("ENABLED", "DISABLED");
    private static final Set<String> FIELD_TYPES = Set.of("input", "textarea", "file");
    private static final long MAX_PAGE_SIZE = 100L;
    private static final int MAX_PARTICIPANTS_PER_TYPE = 20;
    private static final int MAX_INLINE_PARTICIPANTS = MAX_PARTICIPANTS_PER_TYPE * 2;
    private static final int DEFAULT_STUDENT_MIN_MEMBERS = 1;
    private static final int DEFAULT_STUDENT_MAX_MEMBERS = 15;
    private static final int DEFAULT_TEACHER_MIN_MEMBERS = 0;
    private static final int DEFAULT_TEACHER_MAX_MEMBERS = 3;
    private static final int MAX_SHORT_TEXT_LENGTH = 64;
    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_DESCRIPTION_LENGTH = 1000;
    private static final int MAX_FIELD_TEXT_LENGTH = 5000;
    private static final int MAX_JSON_LENGTH = 10000;
    private static final DateTimeFormatter NO_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final int PAYMENT_ORDER_TASK_MAX_RETRY = 8;
    private static final String INTELLECTUAL_PROPERTY_GROUP = "知识产权信息";
    private static final String INTELLECTUAL_PROPERTY_ENTRIES_KEY = "intellectualProperties";

    private final ObjectMapper objectMapper;
    private final ObjectProvider<TeamInternalApi> teamInternalApiProvider;
    private final ObjectProvider<PaymentInternalApi> paymentInternalApiProvider;
    private final ObjectProvider<SystemInternalApi> systemInternalApiProvider;
    private ObjectProvider<ProjectSnapshotPort> projectSnapshotPortProvider;
    private ObjectProvider<DictionaryItemLookupPort> dictionaryItemLookupPortProvider;
    private final TrustedCurrentUserResolver trustedCurrentUserResolver;
    private final RegistrationDatasetRepository registrationDatasetRepository;
    private final RegistrationQueryRepository registrationQueryRepository;
    private final RegistrationWriteRepository registrationWriteRepository;
    private final boolean enforceTrustedUserResolution;
    private Counter registrationConfirmedCounter;
    private Counter materialSubmittedCounter;

    @Autowired
    public CompetitionRegistrationAppService(
            ObjectMapper objectMapper,
            ObjectProvider<TeamInternalApi> teamInternalApiProvider,
            ObjectProvider<PaymentInternalApi> paymentInternalApiProvider,
            ObjectProvider<SystemInternalApi> systemInternalApiProvider,
            TrustedCurrentUserResolver trustedCurrentUserResolver,
            RegistrationDatasetRepository registrationDatasetRepository,
            RegistrationQueryRepository registrationQueryRepository,
            RegistrationWriteRepository registrationWriteRepository
    ) {
        this(
                objectMapper,
                teamInternalApiProvider,
                paymentInternalApiProvider,
                systemInternalApiProvider,
                trustedCurrentUserResolver,
                registrationDatasetRepository,
                registrationQueryRepository,
                registrationWriteRepository,
                true
        );
    }

    @Autowired
    void setMeterRegistry(MeterRegistry meterRegistry) {
        registrationConfirmedCounter = Counter.builder("competition.registration.confirmed")
                .register(meterRegistry);
        materialSubmittedCounter = Counter.builder("competition.registration.material.submitted")
                .register(meterRegistry);
    }

    CompetitionRegistrationAppService(
            ObjectMapper objectMapper,
            ObjectProvider<TeamInternalApi> teamInternalApiProvider,
            ObjectProvider<PaymentInternalApi> paymentInternalApiProvider,
            ObjectProvider<SystemInternalApi> systemInternalApiProvider,
            TrustedCurrentUserResolver trustedCurrentUserResolver,
            RegistrationDatasetRepository registrationDatasetRepository,
            RegistrationQueryRepository registrationQueryRepository,
            RegistrationWriteRepository registrationWriteRepository,
            boolean enforceTrustedUserResolution
    ) {
        this.objectMapper = objectMapper;
        this.teamInternalApiProvider = teamInternalApiProvider;
        this.paymentInternalApiProvider = paymentInternalApiProvider;
        this.systemInternalApiProvider = systemInternalApiProvider;
        this.trustedCurrentUserResolver = trustedCurrentUserResolver;
        this.registrationDatasetRepository = registrationDatasetRepository;
        this.registrationQueryRepository = registrationQueryRepository;
        this.registrationWriteRepository = registrationWriteRepository;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    @Autowired
    void setProjectSnapshotPortProvider(ObjectProvider<ProjectSnapshotPort> projectSnapshotPortProvider) {
        this.projectSnapshotPortProvider = projectSnapshotPortProvider;
    }

    @Autowired
    void setDictionaryItemLookupPortProvider(ObjectProvider<DictionaryItemLookupPort> dictionaryItemLookupPortProvider) {
        this.dictionaryItemLookupPortProvider = dictionaryItemLookupPortProvider;
    }

    public PageResponse<CompetitionRegistrationVO.Registration> listRegistrations(CurrentUser currentUser, long pageNo, long pageSize) {
        return listRegistrations(currentUser, pageNo, pageSize, null, null, null, false);
    }

    public PageResponse<CompetitionRegistrationVO.Registration> listRegistrations(
            CurrentUser currentUser,
            long pageNo,
            long pageSize,
            Long competitionId,
            String status,
            String keyword
    ) {
        return listRegistrations(currentUser, pageNo, pageSize, competitionId, status, keyword, false);
    }

    public PageResponse<CompetitionRegistrationVO.Registration> listRegistrations(
            CurrentUser currentUser,
            long pageNo,
            long pageSize,
            Long competitionId,
            String status,
            String keyword,
            boolean includeSnapshots
    ) {
        requireRegistrationReadPermission(currentUser);
        long safePageNo = Math.max(1L, pageNo);
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        Long ownerUserId = null;
        String ownerUserUuid = null;
        if (!canViewAllRegistrations(currentUser)) {
            ownerUserId = requireUserId(currentUser);
            ownerUserUuid = requireUserUuid(currentUser);
        }
        if (competitionId != null) {
            requirePositiveId(competitionId, "Competition id must be positive");
        }
        String normalizedStatus = trimToNull(status);
        if (normalizedStatus != null) {
            normalizedStatus = normalizedStatus.toUpperCase(Locale.ROOT);
            if (!REGISTRATION_STATUSES.contains(normalizedStatus)) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Invalid registration status");
            }
        }
        String normalizedKeyword = trimToNull(keyword);
        RegistrationQueryRepository.RegistrationPage page = registrationQueryRepository.findRegistrations(
                new RegistrationQueryRepository.RegistrationSearch(
                        ownerUserId,
                        ownerUserUuid,
                        competitionId,
                        normalizedStatus,
                        normalizedKeyword,
                        includeSnapshots,
                        (safePageNo - 1) * safePageSize,
                        safePageSize
                )
        );
        PageResponse<CompetitionRegistrationVO.Registration> response = new PageResponse<>();
        response.setRecords(page.records());
        response.setTotal(page.total());
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        response.setHasMore(safePageNo * safePageSize < response.getTotal());
        return response;
    }

    public PageResponse<CompetitionRegistrationVO.PaymentRecord> listPaymentRecords(
            CurrentUser currentUser,
            long pageNo,
            long pageSize,
            String keyword,
            String paymentStatus,
            String registrationStatus,
            String providerCode
    ) {
        return listPaymentRecords(
                currentUser,
                pageNo,
                pageSize,
                null,
                keyword,
                paymentStatus,
                registrationStatus,
                providerCode
        );
    }

    public PageResponse<CompetitionRegistrationVO.PaymentRecord> listPaymentRecords(
            CurrentUser currentUser,
            long pageNo,
            long pageSize,
            Long competitionId,
            String keyword,
            String paymentStatus,
            String registrationStatus,
            String providerCode
    ) {
        requirePaymentOrderViewPermission(currentUser);
        long safePageNo = Math.max(1L, pageNo);
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        if (competitionId != null) {
            requirePositiveId(competitionId, "Competition id must be positive");
        }
        Long ownerUserId = null;
        String ownerUserUuid = null;
        if (!canViewAllPaymentRecords(currentUser)) {
            ownerUserId = requireUserId(currentUser);
            ownerUserUuid = requireUserUuid(currentUser);
        }
        String normalizedKeyword = trimToNull(keyword);
        String normalizedPaymentStatus = trimToNull(paymentStatus);
        String normalizedRegistrationStatus = trimToNull(registrationStatus);
        String normalizedProviderCode = trimToNull(providerCode);
        RegistrationQueryRepository.PaymentRecordPage page = registrationQueryRepository.findPaymentRecords(
                new RegistrationQueryRepository.PaymentRecordSearch(
                        ownerUserId,
                        ownerUserUuid,
                        competitionId,
                        normalizedKeyword,
                        normalizedPaymentStatus == null ? null : normalizedPaymentStatus.toUpperCase(Locale.ROOT),
                        normalizedRegistrationStatus == null ? null : normalizedRegistrationStatus.toUpperCase(Locale.ROOT),
                        normalizedProviderCode,
                        (safePageNo - 1) * safePageSize,
                        safePageSize
                )
        );
        List<CompetitionRegistrationVO.PaymentRecord> records = page.records().stream()
                .map(this::hydratePaymentRecord)
                .filter(record -> matchesPaymentRecord(
                        record,
                        normalizedKeyword,
                        normalizedPaymentStatus,
                        normalizedProviderCode
                ))
                .toList();
        PageResponse<CompetitionRegistrationVO.PaymentRecord> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(page.total());
        response.setPageNo(safePageNo);
        response.setPageSize(safePageSize);
        response.setHasMore(safePageNo * safePageSize < response.getTotal());
        return response;
    }

    public CompetitionRegistrationVO.Registration getRegistration(CurrentUser currentUser, Long id) {
        requireRegistrationReadPermission(currentUser);
        requirePositiveId(id, "Registration id is required");
        requireUserId(currentUser);
        CompetitionRegistrationVO.Registration registration = findRegistration(id);
        if (registration == null || !canAccessRegistration(currentUser, registration)) {
            throw biz(ErrorCode.NOT_FOUND, "Registration not found");
        }
        if (!isRegistrationOwner(currentUser, registration) && !canViewSensitiveRegistrationData(currentUser)) {
            redactRegistrationSnapshots(registration);
        }
        return registration;
    }

    public List<CompetitionRegistrationVO.MaterialSubmission> listMaterials(CurrentUser currentUser, Long registrationId) {
        requirePositiveId(registrationId, "Registration id is required");
        CompetitionRegistrationVO.Registration registration = getRegistration(currentUser, registrationId);
        boolean redactSensitiveValues =
                !isRegistrationOwner(currentUser, registration)
                && !canViewSensitiveRegistrationData(currentUser);
        List<CompetitionRegistrationVO.MaterialSubmission> submissions = registrationQueryRepository.findMaterialSubmissions(
                registrationId,
                registration.getCompetitionId()
        );
        for (CompetitionRegistrationVO.MaterialSubmission submission : submissions) {
            if (redactSensitiveValues && submission.getValues() != null) {
                for (CompetitionRegistrationVO.MaterialValue value : submission.getValues()) {
                    if (StringUtils.hasText(value.getTextValue())) {
                        value.setTextValue("[敏感内容已隐藏]");
                    }
                    if (StringUtils.hasText(value.getJsonValue())) {
                        value.setJsonValue("{}");
                    }
                }
            }
        }
        return submissions;
    }

    public void requireMaterialFileAccess(CurrentUser currentUser, Long registrationId, Long fileId) {
        requireAnyPermission(
                currentUser,
                MATERIAL_DOWNLOAD_PERMISSION,
                MATERIAL_DOWNLOAD_PERMISSION
        );
        requirePositiveId(registrationId, "Registration id is required");
        requirePositiveId(fileId, "Material file id is required");
        CompetitionRegistrationVO.Registration registration = getRegistration(currentUser, registrationId);
        if (!registrationQueryRepository.existsMaterialFile(registrationId, registration.getCompetitionId(), fileId)) {
            throw biz(ErrorCode.NOT_FOUND, "Registration material file not found");
        }
    }

    @Transactional
    public CompetitionRegistrationVO.Registration confirmRegistration(
            CurrentUser currentUser,
            Long registrationId,
            CompetitionRegistrationDTO.RegistrationConfirmRequest request
    ) {
        requireRequest(request, "Registration confirmation request is required");
        CompetitionRegistrationDTO.RegistrationCreateRequest registration = request.getRegistration();
        requireRequest(registration, "Registration payload is required");
        if (registration.getProjectId() == null || registration.getProjectId() <= 0) {
            CompetitionRegistrationDTO.ProjectDraftRequest project = request.getProject();
            requireRequest(project, "Project information is required");
            CompetitionRegistrationDTO.ProjectSnapshotRequest projectSnapshot = registration.getProjectSnapshot();
            if (projectSnapshot == null) {
                projectSnapshot = new CompetitionRegistrationDTO.ProjectSnapshotRequest();
                registration.setProjectSnapshot(projectSnapshot);
            }
            projectSnapshot.setTitle(project.getTitle());
            projectSnapshot.setCategory(project.getCategory());
            projectSnapshot.setDescription(project.getDescription());
            projectSnapshot.setImageUrl(project.getImageUrl());
            registration.setProjectId(0L);
        }
        CompetitionRegistrationVO.Registration confirmed;
        if (registrationId == null) {
            confirmed = createRegistration(currentUser, registration);
        } else {
            CompetitionRegistrationVO.Registration existing = getRegistration(currentUser, registrationId);
            if (StringUtils.hasText(existing.getPaymentOrderNo())) {
                throw biz(ErrorCode.BIZ_ERROR, "A registration with a payment order can no longer be edited");
            }
            confirmed = updateRegistration(currentUser, registrationId, registration);
        }
        if (request.getMaterials() != null) {
            confirmed = submitMaterials(currentUser, confirmed.getId(), request.getMaterials());
        }
        if (confirmed.getPayableAmountMinor() != null && confirmed.getPayableAmountMinor() == 0L) {
            requireSubmittedPreliminaryMaterials(confirmed);
            confirmPaidRegistration(confirmed.getId(), null);
            confirmed = getRegistration(currentUser, confirmed.getId());
        }
        increment(registrationConfirmedCounter);
        return confirmed;
    }

    @Transactional
    public CompetitionRegistrationVO.Registration createRegistration(CurrentUser currentUser, CompetitionRegistrationDTO.RegistrationCreateRequest request) {
        Long userId = requirePermission(currentUser, REGISTRATION_CREATE_PERMISSION);
        String userUuid = requireUserUuid(currentUser);
        requireRequest(request, "Registration request is required");
        validateRegistrationCreateRequest(request);
        RegistrationQueryRepository.CompetitionDefinition competition = requireCompetition(request.getCompetitionId());
        requireRegistrationWindowOpen(competition);
        TeamSnapshot team = resolveTeamSnapshot(currentUser, request);
        ProjectSnapshot project = requireProjectSnapshot(request.getProjectId(), request.getProjectSnapshot());
        List<CollectedFieldDefinition> fieldDefinitions = validateCollectedFields(competition.id(), request, team, project);
        int memberCount = countParticipants(team.members(), "STUDENT");
        int teacherCount = countParticipants(team.members(), "TEACHER");
        validateParticipantCounts(competition.id(), memberCount, teacherCount);
        long payableAmountMinor = calculatePayableAmount(competition.feeMode(), competition.entryFeeMinor(), memberCount);
        Long id = registrationWriteRepository.createRegistration(
                new com.lumira.saas.modules.competition.repository.RegistrationPersistencePort.CreateRegistrationCommand(
                        generateRegistrationNo(),
                        competition.id(),
                        team.teamId(),
                        project.projectId(),
                        userId,
                        userUuid,
                        competition.feeMode(),
                        competition.entryFeeMinor(),
                        memberCount,
                        payableAmountMinor,
                        competition.currency(),
                        serializeRegistrationSnapshot(request.getRegistrationExtraValues()),
                        serialize(team.summary()),
                        serialize(project.summary()),
                        serialize(team.members()),
                        serializeCollectionSchemaSnapshot(fieldDefinitions)
                )
        );
        requireRegistrationDatasetLink(
                registrationDatasetRepository.linkRegistration(competition.id(), id, userId, userUuid)
        );
        return getRegistration(currentUser, id);
    }

    @Transactional
    public CompetitionRegistrationVO.Registration updateRegistration(CurrentUser currentUser, Long id, CompetitionRegistrationDTO.RegistrationCreateRequest request) {
        requirePermission(currentUser, REGISTRATION_UPDATE_PERMISSION);
        requirePositiveId(id, "Registration id is required");
        requireRequest(request, "Registration request is required");
        validateRegistrationCreateRequest(request);
        CompetitionRegistrationVO.Registration existing = getRegistration(currentUser, id);
        RegistrationQueryRepository.CompetitionDefinition competition = requireCompetition(request.getCompetitionId());
        requireRegistrationWindowOpen(competition);
        TeamSnapshot team = resolveTeamSnapshot(currentUser, request);
        ProjectSnapshot project = requireProjectSnapshot(request.getProjectId(), request.getProjectSnapshot());
        List<CollectedFieldDefinition> fieldDefinitions = validateCollectedFields(competition.id(), request, team, project);
        int memberCount = countParticipants(team.members(), "STUDENT");
        int teacherCount = countParticipants(team.members(), "TEACHER");
        validateParticipantCounts(competition.id(), memberCount, teacherCount);
        long payableAmountMinor = calculatePayableAmount(competition.feeMode(), competition.entryFeeMinor(), memberCount);
        if (!Set.of("DRAFT", "PENDING_PAYMENT").contains(existing.getStatus())) {
            throw biz(ErrorCode.BIZ_ERROR, "Paid registrations cannot be changed");
        }
        int updated = registrationWriteRepository.updateRegistration(
                new RegistrationWriteRepository.UpdateRegistrationCommand(
                        id,
                        existing.getRegistrationNo(),
                        requirePositiveUserId(existing.getOwnerUserId(), "Registration owner is missing"),
                        requireRegistrationOwnerUserUuid(existing),
                        existing.getStatus(),
                        competition.id(),
                        team.teamId(),
                        project.projectId(),
                        competition.feeMode(),
                        competition.entryFeeMinor(),
                        memberCount,
                        payableAmountMinor,
                        competition.currency(),
                        serializeRegistrationSnapshot(request.getRegistrationExtraValues()),
                        serialize(team.summary()),
                        serialize(project.summary()),
                        serialize(team.members()),
                        requireUserId(currentUser),
                        requireUserUuid(currentUser),
                        LocalDateTime.now()
                )
        );
        requireRegistrationWrite(updated, "Registration changed, please retry");
        persistCollectionSchemaSnapshot(id, fieldDefinitions);
        requireRegistrationDatasetLink(registrationDatasetRepository.linkRegistration(
                competition.id(),
                id,
                requirePositiveUserId(existing.getOwnerUserId(), "Registration owner is missing"),
                requireRegistrationOwnerUserUuid(existing)
        ));
        return getRegistration(currentUser, id);
    }

    @Transactional
    public boolean deletePendingRegistration(CurrentUser currentUser, Long id) {
        requirePermission(currentUser, REGISTRATION_UPDATE_PERMISSION);
        requirePositiveId(id, "Registration id is required");
        CompetitionRegistrationVO.Registration existing = getRegistration(currentUser, id);
        if (!"PENDING_PAYMENT".equals(existing.getStatus())) {
            throw biz(ErrorCode.BIZ_ERROR, "只有待支付的报名记录可以删除");
        }
        if (StringUtils.hasText(existing.getPaymentOrderNo())) {
            PaymentInternalApi paymentApi = paymentInternalApiProvider.getIfAvailable();
            if (paymentApi == null) {
                throw biz(ErrorCode.DEPENDENCY_UNAVAILABLE, "支付服务暂不可用，无法安全取消订单");
            }
            PaymentOrderDTO cancelled = paymentApi.cancelOrder(
                    requirePositiveUserId(existing.getOwnerUserId(), "Registration owner is missing"),
                    requireRegistrationOwnerUserUuid(existing),
                    null,
                    existing.getPaymentOrderNo()
            );
            if (cancelled == null || !"CANCELLED".equals(cancelled.status())) {
                throw biz(ErrorCode.BIZ_ERROR, "支付订单取消失败，报名记录未删除");
            }
        }
        Long operatorId = requireUserId(currentUser);
        String operatorUuid = requireUserUuid(currentUser);
        LocalDateTime deletedAt = LocalDateTime.now();
        registrationWriteRepository.cancelPaymentOrderTasks(id, operatorId, operatorUuid, deletedAt);
        int deleted = registrationWriteRepository.cancelPendingRegistration(
                new RegistrationWriteRepository.CancelRegistrationCommand(
                        id,
                        existing.getRegistrationNo(),
                        requirePositiveUserId(existing.getOwnerUserId(), "Registration owner is missing"),
                        requireRegistrationOwnerUserUuid(existing),
                        operatorId,
                        operatorUuid,
                        deletedAt
                )
        );
        requireRegistrationWrite(deleted, "Registration changed, please retry");
        registrationDatasetRepository.unlinkRegistration(id, operatorId, operatorUuid);
        return true;
    }

    public List<CompetitionRegistrationVO.Stage> listStages(CurrentUser currentUser, Long competitionId) {
        requirePositiveId(competitionId, "Competition id is required");
        requireUserId(currentUser);
        if (canReadAllStages(currentUser)) {
            requireCompetition(competitionId);
            return registrationQueryRepository.findStages(competitionId);
        }
        requireRegistrationStageReadPermission(currentUser);
        List<CompetitionRegistrationVO.Stage> stages = registrationQueryRepository.findReadableStages(competitionId);
        for (CompetitionRegistrationVO.Stage stage : stages) {
            hydrateMaterialAccess(stage, currentUser);
        }
        return stages;
    }

    @Transactional
    public CompetitionRegistrationVO.Stage createStage(CurrentUser currentUser, Long competitionId, CompetitionRegistrationDTO.StageUpsertRequest request) {
        Long userId = requirePermission(currentUser, STAGE_MANAGE_PERMISSION);
        String userUuid = requireUserUuid(currentUser);
        requirePositiveId(competitionId, "Competition id is required");
        requireRequest(request, "Stage request is required");
        requireCompetition(competitionId);
        String stageCode = normalizeEnum(request.getStageCode(), null, STAGE_CODES, "Invalid stage code");
        validateStageWindows(request);
        Long id = registrationWriteRepository.createStage(
                new RegistrationWriteRepository.CreateStageCommand(
                        competitionId,
                        stageCode,
                        trimRequired(request.getStageName(), "Stage name is required"),
                        request.getMaterialSubmitStart(),
                        request.getMaterialSubmitEnd(),
                        request.getReviewStart(),
                        request.getReviewEnd(),
                        normalizeEnum(request.getStatus(), "DRAFT", STAGE_STATUSES, "Invalid stage status"),
                        request.getSort() == null ? 100 : request.getSort(),
                        trimToNull(request.getPromotionRuleType()),
                        request.getPromotionRuleValue(),
                        trimToNull(request.getPromotionTiePolicy()),
                        userId,
                        userUuid
                )
        );
        if (id == null || id <= 0) {
            throw biz(ErrorCode.BIZ_ERROR, "Competition stage changed, please retry");
        }
        return findStage(id);
    }

    @Transactional
    public CompetitionRegistrationVO.Stage updateStage(CurrentUser currentUser, Long stageId, CompetitionRegistrationDTO.StageUpsertRequest request) {
        Long userId = requirePermission(currentUser, STAGE_MANAGE_PERMISSION);
        String userUuid = requireUserUuid(currentUser);
        requirePositiveId(stageId, "Stage id is required");
        requireRequest(request, "Stage request is required");
        CompetitionRegistrationVO.Stage existing = findStage(stageId);
        if (existing == null) {
            throw biz(ErrorCode.NOT_FOUND, "Stage not found");
        }
        validateStageWindows(request);
        int updated = registrationWriteRepository.updateStage(
                new RegistrationWriteRepository.UpdateStageCommand(
                        stageId,
                        existing.getCompetitionId(),
                        existing.getStageCode(),
                        trimRequired(request.getStageName(), "Stage name is required"),
                        request.getMaterialSubmitStart(),
                        request.getMaterialSubmitEnd(),
                        request.getReviewStart(),
                        request.getReviewEnd(),
                        normalizeEnum(request.getStatus(), existing.getStatus(), STAGE_STATUSES, "Invalid stage status"),
                        request.getSort() == null ? existing.getSort() : request.getSort(),
                        trimToNull(request.getPromotionRuleType()),
                        request.getPromotionRuleValue(),
                        trimToNull(request.getPromotionTiePolicy()),
                        userId,
                        userUuid,
                        LocalDateTime.now()
                )
        );
        requireRegistrationWrite(updated, "Competition stage changed, please retry");
        return findStage(stageId);
    }

    public List<CompetitionRegistrationVO.StageReviewCandidate> listStageReviewCandidates(CurrentUser currentUser, Long stageId) {
        requirePermission(currentUser, STAGE_MANAGE_PERMISSION);
        CompetitionRegistrationVO.Stage stage = findStage(stageId);
        if (stage == null) {
            throw biz(ErrorCode.NOT_FOUND, "Stage not found");
        }
        requireReviewWindowOpen(stage);
        return registrationQueryRepository.findStageReviewCandidates(stageId, stage.getCompetitionId());
    }

    @Transactional
    public CompetitionRegistrationVO.StageReviewCandidate saveStageReviewDecision(
            CurrentUser currentUser,
            Long stageId,
            Long registrationId,
            CompetitionRegistrationDTO.StageReviewDecisionRequest request
    ) {
        Long userId = requirePermission(currentUser, STAGE_MANAGE_PERMISSION);
        String userUuid = requireUserUuid(currentUser);
        requireRequest(request, "Review decision is required");
        CompetitionRegistrationVO.Stage stage = findStage(stageId);
        CompetitionRegistrationVO.Registration registration = findRegistration(registrationId);
        if (stage == null || registration == null || !stage.getCompetitionId().equals(registration.getCompetitionId())) {
            throw biz(ErrorCode.NOT_FOUND, "Review candidate not found");
        }
        requireReviewWindowOpen(stage);
        String decision = normalizeEnum(request.getDecision(), null, REVIEW_DECISIONS, "Invalid review decision");
        if (request.getScore() != null && (request.getScore().compareTo(BigDecimal.ZERO) < 0 || request.getScore().compareTo(new BigDecimal("100")) > 0)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Review score must be between 0 and 100");
        }
        LocalDateTime publishedAt = "PENDING".equals(decision) ? null : LocalDateTime.now();
        registrationWriteRepository.upsertStageReviewResult(
                new RegistrationWriteRepository.StageReviewResultCommand(
                        stage.getCompetitionId(),
                        stageId,
                        registrationId,
                        request.getScore(),
                        decision,
                        trimToNull(request.getComment()),
                        publishedAt,
                        userId,
                        userUuid
                )
        );
        return listStageReviewCandidates(currentUser, stageId).stream()
                .filter(candidate -> registrationId.equals(candidate.getRegistrationId()))
                .findFirst()
                .orElseThrow(() -> biz(ErrorCode.NOT_FOUND, "Review candidate not found"));
    }

    @Transactional
    public List<CompetitionRegistrationVO.StageReviewCandidate> applyStagePromotionRule(CurrentUser currentUser, Long stageId) {
        Long userId = requirePermission(currentUser, STAGE_MANAGE_PERMISSION);
        String userUuid = requireUserUuid(currentUser);
        CompetitionRegistrationVO.Stage stage = findStage(stageId);
        if (stage == null || !"PRELIMINARY".equals(stage.getStageCode())) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Promotion rule can only be applied to the preliminary stage");
        }
        requireReviewWindowOpen(stage);
        String ruleType = normalizeEnum(stage.getPromotionRuleType(), null, Set.of("PERCENTAGE", "COUNT"), "Please configure a promotion rule first");
        BigDecimal ruleValue = stage.getPromotionRuleValue();
        if (ruleValue == null || ruleValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Please configure a valid promotion rule value");
        }
        List<CompetitionRegistrationVO.StageReviewCandidate> candidates = listStageReviewCandidates(currentUser, stageId).stream()
                .filter(candidate -> candidate.getScore() != null)
                .toList();
        if (candidates.isEmpty()) {
            throw biz(ErrorCode.BIZ_ERROR, "请先完成评分，再生成晋级名单");
        }
        int target = "COUNT".equals(ruleType)
                ? ruleValue.intValue()
                : (int) Math.ceil(candidates.size() * ruleValue.doubleValue() / 100D);
        target = Math.max(1, Math.min(target, candidates.size()));
        BigDecimal boundaryScore = candidates.get(target - 1).getScore();
        long aboveBoundary = candidates.stream().filter(candidate -> candidate.getScore().compareTo(boundaryScore) > 0).count();
        long atBoundary = candidates.stream().filter(candidate -> candidate.getScore().compareTo(boundaryScore) == 0).count();
        boolean tieNeedsReview = aboveBoundary < target && aboveBoundary + atBoundary > target;
        LocalDateTime now = LocalDateTime.now();
        for (CompetitionRegistrationVO.StageReviewCandidate candidate : candidates) {
            String decision = candidate.getScore().compareTo(boundaryScore) > 0
                    ? "ADVANCED"
                    : candidate.getScore().compareTo(boundaryScore) < 0
                    ? "ELIMINATED"
                    : tieNeedsReview ? "PENDING" : "ADVANCED";
            LocalDateTime publishedAt = "PENDING".equals(decision) ? null : now;
            registrationWriteRepository.upsertStageReviewResult(
                    new RegistrationWriteRepository.StageReviewResultCommand(
                            stage.getCompetitionId(),
                            stageId,
                            candidate.getRegistrationId(),
                            candidate.getScore(),
                            decision,
                            tieNeedsReview && candidate.getScore().compareTo(boundaryScore) == 0
                                    ? "晋级边界同分，请人工确认"
                                    : candidate.getReviewComment(),
                            publishedAt,
                            userId,
                            userUuid
                    )
            );
        }
        return listStageReviewCandidates(currentUser, stageId);
    }

    public CompetitionRegistrationVO.StageForm getStageForm(CurrentUser currentUser, Long stageId) {
        requirePositiveId(stageId, "Stage id is required");
        requireUserId(currentUser);
        CompetitionRegistrationVO.StageForm form = canReadAllStages(currentUser)
                ? findStageForm(stageId)
                : findReadableStageFormForRegistration(stageId, currentUser);
        if (form == null) {
            throw biz(ErrorCode.NOT_FOUND, "Stage form not found");
        }
        return form;
    }

    @Transactional
    public CompetitionRegistrationVO.StageForm upsertStageForm(CurrentUser currentUser, Long stageId, CompetitionRegistrationDTO.StageFormUpsertRequest request) {
        Long userId = requirePermission(currentUser, STAGE_MANAGE_PERMISSION);
        String userUuid = requireUserUuid(currentUser);
        requirePositiveId(stageId, "Stage id is required");
        requireRequest(request, "Stage form request is required");
        CompetitionRegistrationVO.Stage stage = findStage(stageId);
        if (stage == null) {
            throw biz(ErrorCode.NOT_FOUND, "Stage not found");
        }
        validateFormSchema(request.getFormSchemaJson());
        CompetitionRegistrationVO.StageForm existing = findStageForm(stageId);
        int version = request.getVersion() == null || request.getVersion() <= 0
                ? existing == null ? 1 : existing.getVersion()
                : request.getVersion();
        String status = normalizeEnum(request.getStatus(), "ENABLED", FORM_STATUSES, "Invalid form status");
        if (existing == null) {
            int inserted = registrationWriteRepository.createStageForm(
                    new RegistrationWriteRepository.CreateStageFormCommand(
                            stage.getCompetitionId(),
                            stageId,
                            trimRequired(request.getFormName(), "Form name is required"),
                            request.getFormSchemaJson(),
                            version,
                            status,
                            userId,
                            userUuid
                    )
            );
            requireRegistrationWrite(inserted, "Competition stage form changed, please retry");
        } else {
            int updated = registrationWriteRepository.updateStageForm(
                    new RegistrationWriteRepository.UpdateStageFormCommand(
                            existing.getId(),
                            existing.getCompetitionId(),
                            stageId,
                            existing.getStatus(),
                            trimRequired(request.getFormName(), "Form name is required"),
                            request.getFormSchemaJson(),
                            version,
                            status,
                            userId,
                            userUuid,
                            LocalDateTime.now()
                    )
            );
            requireRegistrationWrite(updated, "Competition stage form changed, please retry");
        }
        return getStageForm(currentUser, stageId);
    }

    @Transactional
    public CompetitionRegistrationVO.Registration submitMaterials(CurrentUser currentUser, Long registrationId, CompetitionRegistrationDTO.MaterialSubmitRequest request) {
        Long userId = requirePermission(currentUser, MATERIAL_SUBMIT_PERMISSION);
        String userUuid = requireUserUuid(currentUser);
        requirePositiveId(registrationId, "Registration id is required");
        requireRequest(request, "Material submission request is required");
        requirePositiveId(request.getStageId(), "Stage id is required");
        validateMaterialSubmitRequest(request);
        CompetitionRegistrationVO.Registration registration = getRegistration(currentUser, registrationId);
        CompetitionRegistrationVO.Stage stage = findStage(request.getStageId());
        requireMaterialWindowOpen(stage, registration);
        CompetitionRegistrationVO.StageForm form = findStageForm(request.getStageId());
        if (form == null || !form.getCompetitionId().equals(registration.getCompetitionId())) {
            throw biz(ErrorCode.NOT_FOUND, "Stage form not found");
        }
        validateMaterialValues(form.getFormSchemaJson(), request.getValues());
        Long submissionId = registrationQueryRepository.findMaterialSubmissionIdForOwner(
                registrationId,
                request.getStageId(),
                userId,
                userUuid
        );
        if (submissionId == null) {
            submissionId = registrationWriteRepository.createMaterialSubmission(
                    new RegistrationWriteRepository.CreateMaterialSubmissionCommand(
                            registrationId,
                            registration.getCompetitionId(),
                            request.getStageId(),
                            form.getVersion(),
                            userId,
                            userUuid,
                            LocalDateTime.now()
                    )
            );
            if (submissionId == null || submissionId <= 0) {
                throw biz(ErrorCode.BIZ_ERROR, "Material submission changed, please retry");
            }
        } else {
            LocalDateTime submittedAt = LocalDateTime.now();
            int updated = registrationWriteRepository.updateMaterialSubmission(
                    new RegistrationWriteRepository.UpdateMaterialSubmissionCommand(
                            submissionId,
                            registrationId,
                            request.getStageId(),
                            form.getVersion(),
                            userId,
                            userUuid,
                            submittedAt,
                            submittedAt
                    )
            );
            if (updated == 0) {
                throw biz(ErrorCode.BIZ_ERROR, "Material submission changed, please retry");
            }
            registrationWriteRepository.archiveMaterialValues(
                    new RegistrationWriteRepository.ArchiveMaterialValuesCommand(
                            submissionId,
                            registrationId,
                            request.getStageId(),
                            form.getVersion(),
                            userId,
                            userUuid,
                            null
                    )
            );
        }
        registrationWriteRepository.insertMaterialValues(
                submissionId,
                (request.getValues() == null ? List.<CompetitionRegistrationDTO.MaterialValueRequest>of() : request.getValues())
                        .stream()
                        .map(value -> new RegistrationWriteRepository.MaterialValueCommand(
                                trimRequired(value.getFieldKey(), "Material field key is required"),
                                normalizeFieldType(value.getFieldType()),
                                trimToNull(value.getTextValue()),
                                value.getFileId(),
                                trimToNull(value.getJsonValue())
                        ))
                        .toList()
        );
        increment(materialSubmittedCounter);
        return getRegistration(currentUser, registrationId);
    }

    private void increment(Counter counter) {
        if (counter != null) {
            counter.increment();
        }
    }

    @Transactional
    public CompetitionRegistrationVO.PaymentOrder createPaymentOrder(CurrentUser currentUser, Long registrationId, CompetitionRegistrationDTO.PaymentOrderRequest request) {
        Long userId = requirePermission(currentUser, REGISTRATION_PAY_PERMISSION);
        requirePositiveId(registrationId, "Registration id is required");
        CompetitionRegistrationVO.Registration registration = getRegistration(currentUser, registrationId);
        if (!"PENDING_PAYMENT".equals(registration.getStatus())) {
            throw biz(ErrorCode.BIZ_ERROR, "Only pending-payment registrations can create payment orders");
        }
        requireSubmittedPreliminaryMaterials(registration);
        if (registration.getPayableAmountMinor() == null || registration.getPayableAmountMinor() == 0L) {
            confirmPaidRegistration(registration.getId(), null);
            CompetitionRegistrationVO.PaymentOrder freeOrder = new CompetitionRegistrationVO.PaymentOrder();
            freeOrder.setOrderNo(null);
            freeOrder.setAmountMinor(0L);
            freeOrder.setCurrency(registration.getCurrency());
            freeOrder.setStatus("CONFIRMED");
            return freeOrder;
        }
        boolean modernCheckoutRequest = request != null && StringUtils.hasText(request.getClientType());
        List<CompetitionRegistrationVO.PaymentOption> availableOptions = modernCheckoutRequest
                ? listPaymentOptions(currentUser, registrationId, request.getClientType())
                : List.of();
        if (modernCheckoutRequest && !StringUtils.hasText(request.getProviderCode())) {
            throw biz(ErrorCode.VALIDATION_ERROR, "A payment provider must be selected");
        }
        String providerCode = request != null && StringUtils.hasText(request.getProviderCode())
                ? request.getProviderCode().trim()
                : availableOptions.stream().findFirst().map(CompetitionRegistrationVO.PaymentOption::getProviderCode).orElse("alipay");
        boolean providerAvailable = availableOptions.stream().anyMatch(option -> option.getProviderCode().equalsIgnoreCase(providerCode == null ? "" : providerCode));
        if ((modernCheckoutRequest && !providerAvailable)
                || (!modernCheckoutRequest && !availableOptions.isEmpty() && !providerAvailable)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Selected payment provider is unavailable for this device");
        }
        String clientIp = request == null ? null : request.getClientIp();
        // Provider notifications are always controlled by the persisted payment
        // provider configuration. Browsers may only supply the dedicated,
        // same-purpose synchronous registration return page.
        String notifyUrl = null;
        String returnUrl = normalizeRegistrationReturnUrl(request == null ? null : request.getReturnUrl(), registrationId);
        String operatorUuid = requireUserUuid(currentUser);
        Long simulatedRoleId = normalizeSimulatedRoleId(currentUser.getSimulatedRoleId());
        boolean retryQueued = preparePaymentRetryIfTerminal(
                registration,
                userId,
                operatorUuid,
                simulatedRoleId,
                providerCode,
                clientIp,
                notifyUrl,
                returnUrl
        );
        if (!retryQueued) {
            enqueuePaymentOrderIfReady(
                    registration,
                    userId,
                    operatorUuid,
                    simulatedRoleId,
                    providerCode,
                    clientIp,
                    notifyUrl,
                    returnUrl
            );
        }
        drainPaymentOrderQueue(5);
        CompetitionRegistrationVO.Registration refreshed = getRegistration(currentUser, registrationId);
        if (StringUtils.hasText(refreshed.getPaymentOrderNo())) {
            CompetitionRegistrationVO.PaymentOrder order = findPaymentOrder(
                    refreshed,
                    normalizeSimulatedRoleId(currentUser.getSimulatedRoleId())
            );
            if (order != null) {
                return order;
            }
        }
        CompetitionRegistrationVO.PaymentOrder queuedOrder = new CompetitionRegistrationVO.PaymentOrder();
        queuedOrder.setProviderCode(providerCode);
        queuedOrder.setAmountMinor(refreshed.getPayableAmountMinor());
        queuedOrder.setCurrency(refreshed.getCurrency());
        queuedOrder.setStatus("QUEUED");
        return queuedOrder;
    }

    private boolean preparePaymentRetryIfTerminal(
            CompetitionRegistrationVO.Registration registration,
            Long operatorId,
            String operatorUuid,
            Long simulatedRoleId,
            String providerCode,
            String clientIp,
            String notifyUrl,
            String returnUrl
    ) {
        if (registration == null || !StringUtils.hasText(registration.getPaymentOrderNo())) {
            return false;
        }
        CompetitionRegistrationVO.PaymentOrder existingOrder = null;
        try {
            existingOrder = findPaymentOrder(registration, simulatedRoleId);
        } catch (BizException exception) {
            if (exception.getErrorCode() != ErrorCode.NOT_FOUND) {
                throw exception;
            }
        }
        if (existingOrder != null && !isRetryablePaymentStatus(existingOrder.getStatus())) {
            return false;
        }
        String previousOrderNo = registration.getPaymentOrderNo().trim();
        int detached = registrationWriteRepository.detachPaymentOrderForRetry(
                new RegistrationWriteRepository.DetachPaymentOrderForRetryCommand(
                        registration.getId(),
                        registration.getRegistrationNo(),
                        registration.getOwnerUserId(),
                        requireRegistrationOwnerUserUuid(registration),
                        previousOrderNo,
                        operatorId,
                        operatorUuid,
                        LocalDateTime.now()
                )
        );
        requireRegistrationWrite(detached, "Registration payment attempt changed, please retry");
        int requeued = registrationWriteRepository.enqueuePaymentOrderRetryTask(
                paymentOrderTaskCommand(
                        registration,
                        operatorId,
                        operatorUuid,
                        simulatedRoleId,
                        providerCode,
                        clientIp,
                        notifyUrl,
                        returnUrl
                )
        );
        requireRegistrationWrite(requeued, "Payment order retry task changed, please retry");
        registration.setPaymentOrderNo(null);
        return true;
    }

    private boolean isRetryablePaymentStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        return Set.of("FAILED", "CANCELLED", "EXPIRED", "CLOSED").contains(status.trim().toUpperCase(Locale.ROOT));
    }

    public List<CompetitionRegistrationVO.PaymentOption> listPaymentOptions(CurrentUser currentUser, Long registrationId, String clientType) {
        requirePermission(currentUser, REGISTRATION_PAY_PERMISSION);
        requirePositiveId(registrationId, "Registration id is required");
        CompetitionRegistrationVO.Registration registration = getRegistration(currentUser, registrationId);
        PaymentInternalApi api = paymentInternalApiProvider.getIfAvailable();
        if (api == null) {
            return List.of();
        }
        String normalizedClientType = normalizePaymentClientType(clientType);
        List<PaymentCheckoutOptionDTO> configured = api.listCheckoutOptions(
                requireUserId(currentUser),
                requireUserUuid(currentUser),
                normalizeSimulatedRoleId(currentUser.getSimulatedRoleId())
        );
        Set<String> allowedProviders = findCompetitionPaymentProviders(registration.getCompetitionId());
        return (configured == null ? List.<PaymentCheckoutOptionDTO>of() : configured).stream()
                .filter(option -> allowedProviders.isEmpty() || allowedProviders.contains(option.providerCode()))
                .filter(option -> !StringUtils.hasText(option.currency())
                        || option.currency().equalsIgnoreCase(registration.getCurrency()))
                .map(option -> toPaymentOption(option, normalizedClientType))
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparing(option -> option.getSortOrder() == null ? 100 : option.getSortOrder()))
                .toList();
    }

    private Set<String> findCompetitionPaymentProviders(Long competitionId) {
        return registrationQueryRepository.findCompetitionPaymentProviders(competitionId).stream()
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private CompetitionRegistrationVO.PaymentOption toPaymentOption(PaymentCheckoutOptionDTO configured, String clientType) {
        String scene = resolvePaymentScene(configured.providerCode(), configured.enabledScenes(), clientType);
        if (!StringUtils.hasText(scene)) {
            return null;
        }
        CompetitionRegistrationVO.PaymentOption option = new CompetitionRegistrationVO.PaymentOption();
        option.setProviderCode(configured.providerCode());
        option.setDisplayName(configured.displayName());
        option.setPaymentScene(scene);
        option.setSortOrder(configured.sortOrder());
        return option;
    }

    private String normalizePaymentClientType(String clientType) {
        String normalized = StringUtils.hasText(clientType) ? clientType.trim().toUpperCase(Locale.ROOT) : "DESKTOP";
        return Set.of("DESKTOP", "MOBILE", "WECHAT").contains(normalized) ? normalized : "DESKTOP";
    }

    private String resolvePaymentScene(String providerCode, List<String> enabledScenes, String clientType) {
        Set<String> scenes = enabledScenes == null ? Set.of() : enabledScenes.stream()
                .filter(StringUtils::hasText)
                .map(scene -> scene.trim().toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        String provider = providerCode == null ? "" : providerCode.trim().toLowerCase(Locale.ROOT);
        List<String> preferred = switch (clientType) {
            case "WECHAT" -> "wechat_pay".equals(provider) ? List.of("JSAPI") : List.of();
            case "MOBILE" -> "wechat_pay".equals(provider) ? List.of("H5") : isAlipayStyleProvider(provider) ? List.of("WAP") : List.of("CHECKOUT");
            default -> "wechat_pay".equals(provider) ? List.of("NATIVE") : isAlipayStyleProvider(provider) ? List.of("PC_WEB", "QR_CODE") : List.of("CHECKOUT");
        };
        return preferred.stream().filter(scenes::contains).findFirst().orElse(null);
    }

    private boolean isAlipayStyleProvider(String providerCode) {
        return "alipay".equals(providerCode) || "builtin_mock".equals(providerCode);
    }

    public CompetitionRegistrationVO.PaymentOrder getPaymentStatus(CurrentUser currentUser, Long registrationId) {
        requireRegistrationReadPermission(currentUser);
        requirePositiveId(registrationId, "Registration id is required");
        CompetitionRegistrationVO.Registration registration = getRegistration(currentUser, registrationId);
        drainPaymentOrderQueue(5);
        if (!StringUtils.hasText(registration.getPaymentOrderNo())) {
            CompetitionRegistrationVO.PaymentOrder order = new CompetitionRegistrationVO.PaymentOrder();
            order.setAmountMinor(registration.getPayableAmountMinor());
            order.setCurrency(registration.getCurrency());
            order.setStatus(registration.getStatus());
            return order;
        }
        CompetitionRegistrationVO.PaymentOrder order = findPaymentOrder(
                registration,
                normalizeSimulatedRoleId(currentUser.getSimulatedRoleId())
        );
        if (order == null) {
            throw biz(ErrorCode.NOT_FOUND, "Payment order not found");
        }
        return order;
    }

    @Transactional
    public void markPaidFromPaymentOrder(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return;
        }
        CompetitionRegistrationVO.Registration registration = findRegistrationByPaymentOrder(orderNo);
        if (registration != null) {
            confirmPaidRegistration(registration.getId(), orderNo);
        }
    }

    public int drainPaymentOrderQueue(int limit) {
        PaymentInternalApi paymentInternalApi = paymentInternalApiProvider.getIfAvailable();
        if (paymentInternalApi == null) {
            return 0;
        }
        int processed = 0;
        for (RegistrationWriteRepository.PaymentOrderTask task : claimPaymentOrderTasks(Math.max(1, Math.min(limit, 20))) ) {
            Long taskId = task.id();
            Long registrationId = task.registrationId();
            String claimToken = task.claimToken();
            String taskOwnerUserUuid = trimToNull(task.ownerUserUuid());
            Long taskSimulatedRoleId = normalizeSimulatedRoleId(task.simulatedRoleId());
            try {
                CompetitionRegistrationVO.Registration registration = findRegistration(registrationId);
                if (registration == null || !"PENDING_PAYMENT".equals(registration.getStatus())) {
                    markPaymentOrderTaskSucceeded(taskId, registrationId, taskOwnerUserUuid, claimToken, "Registration no longer needs payment");
                    processed += 1;
                    continue;
                }
                requireTaskOwnerMatchesRegistration(registration, taskOwnerUserUuid);
                if (StringUtils.hasText(registration.getPaymentOrderNo())) {
                    String ownerUserUuid = resolvePaymentOwnerUserUuid(registration.getOwnerUserId(), registration.getOwnerUserUuid());
                    PaymentOrderDTO existingOrder = paymentInternalApi.getOrder(
                            registration.getOwnerUserId(),
                            ownerUserUuid,
                            taskSimulatedRoleId,
                            registration.getPaymentOrderNo()
                    );
                    assertOrderMatchesRegistration(existingOrder, registration);
                    markPaymentOrderTaskSucceeded(taskId, registrationId, taskOwnerUserUuid, claimToken, "Payment order already exists");
                    processed += 1;
                    continue;
                }
                String ownerUserUuid = resolvePaymentOwnerUserUuid(registration.getOwnerUserId(), registration.getOwnerUserUuid());
                int attemptNo = task.attemptNo() == null ? 1 : Math.max(1, task.attemptNo());
                String attemptOrderNo = attemptNo == 1
                        ? "REG-" + registration.getId()
                        : "REG-" + registration.getId() + "-A" + attemptNo;
                PaymentOrderDTO order = paymentInternalApi.createOrder(
                        registration.getOwnerUserId(),
                        ownerUserUuid,
                        taskSimulatedRoleId,
                        new PaymentCreateOrderRequestDTO(
                                trimTaskText(task.providerCode(), "alipay"),
                                attemptOrderNo,
                                "Competition registration " + registration.getRegistrationNo(),
                                registration.getPayableAmountMinor(),
                                registration.getCurrency(),
                                trimTaskText(task.clientIp(), null),
                                trimTaskText(task.notifyUrl(), null),
                                trimTaskText(task.returnUrl(), null),
                                Map.of(
                                        "bizType", "competition_registration",
                                        "registrationId", registration.getId(),
                                        "competitionId", registration.getCompetitionId(),
                                        "teamId", registration.getTeamId(),
                                        "projectId", registration.getProjectId()
                                ),
                                "competition-registration-" + registration.getId() + "-attempt-" + attemptNo
                        )
                );
                assertOrderMatchesRegistration(order, registration);
                int registrationUpdated = registrationWriteRepository.attachPaymentOrder(
                        new RegistrationWriteRepository.AttachPaymentOrderCommand(
                                registration.getId(),
                                registration.getRegistrationNo(),
                                registration.getOwnerUserId(),
                                requireRegistrationOwnerUserUuid(registration),
                                registration.getPayableAmountMinor(),
                                registration.getCurrency(),
                                order.orderNo(),
                                LocalDateTime.now()
                        )
                );
                if (registrationUpdated == 0) {
                    throw biz(ErrorCode.BIZ_ERROR, "Registration payment state changed, please retry");
                }
                markPaymentOrderTaskSucceeded(taskId, registrationId, taskOwnerUserUuid, claimToken, "Payment order created");
                processed += 1;
            } catch (Exception exception) {
                markPaymentOrderTaskFailed(taskId, registrationId, taskOwnerUserUuid, claimToken, exception.getMessage());
            }
        }
        return processed;
    }

    private String resolvePaymentOwnerUserUuid(Long ownerUserId, String expectedOwnerUserUuid) {
        Long normalizedOwnerUserId = requirePositiveUserId(ownerUserId, "Registration owner is missing");
        String normalizedExpectedOwnerUserUuid = requireTrustedOwnerUserUuid(expectedOwnerUserUuid);
        SystemInternalApi systemInternalApi = systemInternalApiProvider == null ? null : systemInternalApiProvider.getIfAvailable();
        if (systemInternalApi == null) {
            throw biz(ErrorCode.UNAUTHORIZED, "Trusted payment owner resolver is unavailable");
        }
        SystemUserSnapshotDTO owner = systemInternalApi.findUserIdentityById(normalizedOwnerUserId);
        if (owner == null || owner.userId() == null || !owner.userId().equals(normalizedOwnerUserId)) {
            throw biz(ErrorCode.UNAUTHORIZED, "Payment owner does not exist");
        }
        if (!StringUtils.hasText(owner.userUuid())) {
            throw biz(ErrorCode.UNAUTHORIZED, "Payment owner userUuid is required");
        }
        if (!normalizedExpectedOwnerUserUuid.equals(owner.userUuid().trim())) {
            throw biz(ErrorCode.UNAUTHORIZED, "Payment owner userUuid mismatch");
        }
        if (!StringUtils.hasText(owner.username())) {
            throw biz(ErrorCode.UNAUTHORIZED, "Payment owner username is required");
        }
        if (!StringUtils.hasText(owner.status()) || !"ENABLED".equalsIgnoreCase(owner.status().trim())) {
            throw new BizException(
                    ErrorCode.UNAUTHORIZED,
                    PAYMENT_OWNER_DISABLED_MESSAGE,
                    "支付所属用户已被禁用"
            );
        }
        return owner.userUuid().trim();
    }

    private void requireTaskOwnerMatchesRegistration(CompetitionRegistrationVO.Registration registration, String taskOwnerUserUuid) {
        String registrationOwnerUserUuid = requireRegistrationOwnerUserUuid(registration);
        if (!registrationOwnerUserUuid.equals(requireTrustedOwnerUserUuid(taskOwnerUserUuid))) {
            throw biz(ErrorCode.UNAUTHORIZED, "Payment order task owner userUuid mismatch");
        }
    }

    private String requireRegistrationOwnerUserUuid(CompetitionRegistrationVO.Registration registration) {
        if (registration == null) {
            throw biz(ErrorCode.UNAUTHORIZED, "Registration owner userUuid is required");
        }
        return requireTrustedOwnerUserUuid(registration.getOwnerUserUuid());
    }

    private String requireTrustedOwnerUserUuid(String ownerUserUuid) {
        if (!StringUtils.hasText(ownerUserUuid)) {
            throw biz(ErrorCode.UNAUTHORIZED, "Registration owner userUuid is required");
        }
        return ownerUserUuid.trim();
    }

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
    }

    private void enqueuePaymentOrderIfReady(
            CompetitionRegistrationVO.Registration registration,
            Long operatorId,
            String operatorUuid,
            Long simulatedRoleId,
            String providerCode,
            String clientIp,
            String notifyUrl,
            String returnUrl
    ) {
        if (registration == null || !"PENDING_PAYMENT".equals(registration.getStatus())) {
            return;
        }
        requireSubmittedPreliminaryMaterials(registration);
        if (registration.getPayableAmountMinor() == null || registration.getPayableAmountMinor() <= 0L) {
            confirmPaidRegistration(registration.getId(), null);
            return;
        }
        if (StringUtils.hasText(registration.getPaymentOrderNo())) {
            return;
        }
        int inserted = registrationWriteRepository.enqueuePaymentOrderTask(
                paymentOrderTaskCommand(
                        registration, operatorId, operatorUuid, simulatedRoleId,
                        providerCode, clientIp, notifyUrl, returnUrl
                )
        );
        requireRegistrationWrite(inserted, "Payment order task changed, please retry");
    }

    private RegistrationWriteRepository.EnqueuePaymentOrderTaskCommand paymentOrderTaskCommand(
            CompetitionRegistrationVO.Registration registration,
            Long operatorId,
            String operatorUuid,
            Long simulatedRoleId,
            String providerCode,
            String clientIp,
            String notifyUrl,
            String returnUrl
    ) {
        return new RegistrationWriteRepository.EnqueuePaymentOrderTaskCommand(
                registration.getId(),
                StringUtils.hasText(providerCode) ? providerCode.trim() : "alipay",
                trimToNull(clientIp),
                trimToNull(notifyUrl),
                trimToNull(returnUrl),
                requireRegistrationOwnerUserUuid(registration),
                normalizeSimulatedRoleId(simulatedRoleId),
                LocalDateTime.now(),
                operatorId,
                operatorUuid
        );
    }

    private List<RegistrationWriteRepository.PaymentOrderTask> claimPaymentOrderTasks(int limit) {
        String claimToken = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        return registrationWriteRepository.claimPaymentOrderTasks(limit, claimToken, now, now.plusMinutes(5));
    }

    private void markPaymentOrderTaskSucceeded(Long taskId, Long registrationId, String ownerUserUuid, String claimToken, String message) {
        registrationWriteRepository.markPaymentOrderTaskSucceeded(
                new RegistrationWriteRepository.PaymentOrderTaskCompletion(
                        taskId,
                        registrationId,
                        requireTrustedOwnerUserUuid(ownerUserUuid),
                        claimToken,
                        message,
                        LocalDateTime.now()
                )
        );
    }

    private void markPaymentOrderTaskFailed(Long taskId, Long registrationId, String ownerUserUuid, String claimToken, String message) {
        registrationWriteRepository.markPaymentOrderTaskFailed(
                new RegistrationWriteRepository.PaymentOrderTaskFailure(
                        taskId,
                        registrationId,
                        requireTrustedOwnerUserUuid(ownerUserUuid),
                        claimToken,
                        message,
                        PAYMENT_ORDER_TASK_MAX_RETRY,
                        null,
                        LocalDateTime.now()
                )
        );
    }

    private void assertOrderMatchesRegistration(PaymentOrderDTO order, CompetitionRegistrationVO.Registration registration) {
        if (order == null || !StringUtils.hasText(order.orderNo())) {
            throw biz(ErrorCode.BIZ_ERROR, "Payment provider did not return an order");
        }
        if (!registration.getPayableAmountMinor().equals(order.amountMinor())
                || !registration.getCurrency().equalsIgnoreCase(order.currency())) {
            throw biz(ErrorCode.BIZ_ERROR, "Payment order amount does not match registration");
        }
    }

    private void confirmPaidRegistration(Long registrationId, String orderNo) {
        CompetitionRegistrationVO.Registration registration = findRegistration(registrationId);
        if (registration == null || StringUtils.hasText(registration.getParticipantNo())) {
            return;
        }
        String participantNo = generateParticipantNo(registration.getCompetitionId());
        Long ownerUserId = requirePositiveUserId(registration.getOwnerUserId(), "Registration owner is missing");
        String ownerUserUuid = requireRegistrationOwnerUserUuid(registration);
        registrationWriteRepository.confirmPaidRegistration(
                new RegistrationWriteRepository.ConfirmPaidRegistrationCommand(
                        registrationId,
                        ownerUserId,
                        ownerUserUuid,
                        participantNo,
                        orderNo,
                        LocalDateTime.now()
                )
        );
    }

    private void requireSubmittedPreliminaryMaterials(CompetitionRegistrationVO.Registration registration) {
        Long preliminaryStageId = registrationQueryRepository.findPreliminaryStageId(registration.getCompetitionId());
        if (preliminaryStageId == null) {
            return;
        }
        if (!registrationQueryRepository.hasSubmittedMaterial(registration.getId(), preliminaryStageId)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Preliminary materials must be submitted before payment");
        }
    }

    private TeamSnapshot resolveTeamSnapshot(CurrentUser currentUser, CompetitionRegistrationDTO.RegistrationCreateRequest request) {
        TeamSnapshot snapshot;
        if (hasInlineRegistrationTeam(request)) {
            snapshot = inlineTeamSnapshot(request);
        } else {
            Long teamId = request.getTeamId();
            if (teamId == null || teamId <= 0) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Team information is required");
            }
            snapshot = resolveTeamSnapshot(requireUserId(currentUser), requireUserUuid(currentUser), teamId);
        }
        return snapshot;
    }

    private boolean hasInlineRegistrationTeam(CompetitionRegistrationDTO.RegistrationCreateRequest request) {
        CompetitionRegistrationDTO.TeamSnapshotRequest team = request.getTeamSnapshot();
        return team != null || request.getMembers() != null && !request.getMembers().isEmpty();
    }

    private TeamSnapshot inlineTeamSnapshot(CompetitionRegistrationDTO.RegistrationCreateRequest request) {
        CompetitionRegistrationDTO.TeamSnapshotRequest team = request.getTeamSnapshot();
        String teamName = team == null ? null : trimToNull(team.getTeamName());
        List<Map<String, Object>> members = normalizeInlineMembers(request.getMembers());
        if (!StringUtils.hasText(teamName)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Team name is required");
        }
        if (members.isEmpty()) {
            throw biz(ErrorCode.VALIDATION_ERROR, "At least one registration member is required");
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("teamId", request.getTeamId() == null ? 0L : request.getTeamId());
        summary.put("teamName", teamName);
        summary.put("teamType", team == null ? null : trimToNull(team.getTeamType()));
        summary.put("avatarUrl", team == null ? null : trimToNull(team.getAvatarUrl()));
        summary.put("visibility", team == null ? null : trimToNull(team.getVisibility()));
        summary.put("joinMode", team == null ? null : trimToNull(team.getJoinMode()));
        summary.put("description", team == null ? null : trimToNull(team.getDescription()));
        if (team != null && team.getExtraValues() != null && !team.getExtraValues().isEmpty()) {
            requireJsonSize(team.getExtraValues(), "Team extra values are too large");
            summary.put("extraValues", team.getExtraValues());
        }
        summary.entrySet().removeIf((entry) -> entry.getValue() == null);
        return new TeamSnapshot(request.getTeamId() == null ? 0L : request.getTeamId(), summary, members);
    }

    private List<Map<String, Object>> normalizeInlineMembers(List<CompetitionRegistrationDTO.MemberSnapshotRequest> members) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        if (members != null && members.size() > MAX_INLINE_PARTICIPANTS) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Too many registration members");
        }
        long studentCount = (members == null ? List.<CompetitionRegistrationDTO.MemberSnapshotRequest>of() : members)
                .stream()
                .filter(member -> "STUDENT".equals(normalizeParticipantType(member.getParticipantType())))
                .count();
        long teacherCount = (members == null ? List.<CompetitionRegistrationDTO.MemberSnapshotRequest>of() : members)
                .stream()
                .filter(member -> "TEACHER".equals(normalizeParticipantType(member.getParticipantType())))
                .count();
        if (studentCount > MAX_PARTICIPANTS_PER_TYPE || teacherCount > MAX_PARTICIPANTS_PER_TYPE) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Too many registration participants of one type");
        }
        for (CompetitionRegistrationDTO.MemberSnapshotRequest member : members == null ? List.<CompetitionRegistrationDTO.MemberSnapshotRequest>of() : members) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("participantType", normalizeParticipantType(member.getParticipantType()));
            row.put("memberName", trimToNull(member.getMemberName()));
            row.put("employeeNo", trimToNull(member.getEmployeeNo()));
            row.put("departmentName", trimToNull(member.getDepartmentName()));
            row.put("systemRole", trimToNull(member.getRole()));
            row.put("remark", trimToNull(member.getRemark()));
            if (member.getExtraValues() != null && !member.getExtraValues().isEmpty()) {
                requireJsonSize(member.getExtraValues(), "Member extra values are too large");
                row.put("extraValues", member.getExtraValues());
            }
            row.entrySet().removeIf((entry) -> entry.getValue() == null);
            if (!row.isEmpty()) {
                normalized.add(row);
            }
        }
        return normalized;
    }

    private TeamSnapshot resolveTeamSnapshot(Long requesterUserId, String requesterUserUuid, Long teamId) {
        TeamInternalApi api = teamInternalApiProvider.getIfAvailable();
        if (api == null) {
            throw biz(ErrorCode.BIZ_ERROR, "Team snapshot resolver is unavailable");
        }
        TeamSummaryDTO team = api.getTeam(requesterUserId, requesterUserUuid, teamId);
        if (team == null) {
            throw biz(ErrorCode.NOT_FOUND, "Team not found");
        }
        List<TeamMemberDTO> members = api.listActiveMembers(requesterUserId, requesterUserUuid, teamId);
        return new TeamSnapshot(teamId, team, normalizeSystemTeamMembers(members));
    }

    private List<Map<String, Object>> normalizeSystemTeamMembers(List<TeamMemberDTO> members) {
        List<Map<String, Object>> snapshots = new ArrayList<>();
        for (TeamMemberDTO member : members == null ? List.<TeamMemberDTO>of() : members) {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("participantType", "STUDENT");
            snapshot.put("id", member.getId());
            snapshot.put("teamId", member.getTeamId());
            snapshot.put("userId", member.getUserId());
            snapshot.put("userUuid", member.getUserUuid());
            snapshot.put("systemRole", trimToNull(member.getRole()));
            snapshot.put("status", trimToNull(member.getStatus()));
            snapshot.put("extraValuesJson", trimToNull(member.getExtraValuesJson()));
            snapshot.put("joinedAt", member.getJoinedAt());
            snapshot.entrySet().removeIf(entry -> entry.getValue() == null);
            snapshots.add(snapshot);
        }
        return snapshots;
    }

    private ProjectSnapshot requireProjectSnapshot(Long projectId, CompetitionRegistrationDTO.ProjectSnapshotRequest projectSnapshotRequest) {
        if (projectId == null || projectId <= 0) {
            requireRequest(projectSnapshotRequest, "Project information is required");
            Map<String, Object> inlineProject = new LinkedHashMap<>();
            inlineProject.put("title", trimRequired(projectSnapshotRequest.getTitle(), "Project title is required"));
            inlineProject.put("category", StringUtils.hasText(projectSnapshotRequest.getCategory())
                    ? projectSnapshotRequest.getCategory().trim() : "INNOVATION");
            inlineProject.put("description", trimToNull(projectSnapshotRequest.getDescription()));
            inlineProject.put("imageUrl", trimToNull(projectSnapshotRequest.getImageUrl()));
            if (projectSnapshotRequest.getExtraValues() != null && !projectSnapshotRequest.getExtraValues().isEmpty()) {
                requireJsonSize(projectSnapshotRequest.getExtraValues(), "Project extra values are too large");
                inlineProject.put("extraValues", projectSnapshotRequest.getExtraValues());
            }
            inlineProject.entrySet().removeIf((entry) -> entry.getValue() == null);
            return new ProjectSnapshot(0L, inlineProject);
        }
        ProjectSnapshotPort projectPort = projectSnapshotPortProvider == null
                ? null : projectSnapshotPortProvider.getIfAvailable();
        com.lumira.api.project.ProjectSnapshot projectSnapshot = projectPort == null
                ? null : projectPort.findProjectSnapshot(projectId);
        if (projectSnapshot == null) {
            throw biz(ErrorCode.NOT_FOUND, "Project not found");
        }
        Map<String, Object> project = new LinkedHashMap<>();
        project.put("id", projectSnapshot.id());
        project.put("code", projectSnapshot.code());
        project.put("locale", projectSnapshot.locale());
        project.put("title", projectSnapshot.title());
        project.put("category", projectSnapshot.category());
        project.put("description", projectSnapshot.description());
        project.put("imageUrl", projectSnapshot.imageUrl());
        project.put("ownerName", projectSnapshot.ownerName());
        project.put("status", projectSnapshot.status());
        project.put("tags", projectSnapshot.tags());
        project.entrySet().removeIf(entry -> entry.getValue() == null);
        if (projectSnapshotRequest != null && projectSnapshotRequest.getExtraValues() != null && !projectSnapshotRequest.getExtraValues().isEmpty()) {
            requireJsonSize(projectSnapshotRequest.getExtraValues(), "Project extra values are too large");
            project = new LinkedHashMap<>(project);
            project.put("extraValues", projectSnapshotRequest.getExtraValues());
        }
        return new ProjectSnapshot(projectId, project);
    }

    private RegistrationQueryRepository.CompetitionDefinition requireCompetition(Long competitionId) {
        requirePositiveId(competitionId, "Competition id is required");
        RegistrationQueryRepository.CompetitionDefinition row = registrationQueryRepository.findCompetition(competitionId);
        if (row == null) {
            throw biz(ErrorCode.NOT_FOUND, "Competition not found");
        }
        return row;
    }

    private long calculatePayableAmount(String feeMode, Long entryFeeMinor, int memberCount) {
        long fee = entryFeeMinor == null ? 0L : entryFeeMinor;
        if (fee < 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Entry fee cannot be negative");
        }
        return "MEMBER".equalsIgnoreCase(feeMode) ? fee * Math.max(0, memberCount) : fee;
    }

    private void validateFormSchema(String schemaJson) {
        requireLength(trimRequired(schemaJson, "Form schema json is required"), MAX_JSON_LENGTH, "Form schema json is too large");
        try {
            JsonNode fields = objectMapper.readTree(schemaJson).path("fields");
            if (!fields.isArray()) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Form schema fields must be an array");
            }
            for (JsonNode field : fields) {
                String key = field.path("key").asText("");
                String type = field.path("type").asText("");
                if (!StringUtils.hasText(key) || !FIELD_TYPES.contains(type)) {
                    throw biz(ErrorCode.VALIDATION_ERROR, "Invalid form field schema");
                }
            }
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Invalid form schema json");
        }
    }

    private void validateMaterialValues(String schemaJson, List<CompetitionRegistrationDTO.MaterialValueRequest> values) {
        Map<String, CompetitionRegistrationDTO.MaterialValueRequest> valueMap = new LinkedHashMap<>();
        for (CompetitionRegistrationDTO.MaterialValueRequest value : values == null ? List.<CompetitionRegistrationDTO.MaterialValueRequest>of() : values) {
            valueMap.put(trimRequired(value.getFieldKey(), "Material field key is required"), value);
            normalizeFieldType(value.getFieldType());
            requireLength(value.getTextValue(), MAX_FIELD_TEXT_LENGTH, "Material text is too large");
            requireLength(value.getJsonValue(), MAX_JSON_LENGTH, "Material json is too large");
            if (value.getFileId() != null && value.getFileId() <= 0) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Material file id is invalid");
            }
        }
        try {
            JsonNode fields = objectMapper.readTree(schemaJson).path("fields");
            for (JsonNode field : fields) {
                String key = field.path("key").asText("");
                String type = field.path("type").asText("");
                if (!FIELD_TYPES.contains(type)) {
                    throw biz(ErrorCode.VALIDATION_ERROR, "Unsupported material field type");
                }
                if (field.path("required").asBoolean(false) && isBlankMaterialValue(valueMap.get(key), type)) {
                    throw biz(ErrorCode.VALIDATION_ERROR, "Required material field is missing: " + key);
                }
            }
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Invalid form schema json");
        }
    }

    private boolean isBlankMaterialValue(CompetitionRegistrationDTO.MaterialValueRequest value, String type) {
        if (value == null) {
            return true;
        }
        if ("file".equals(type)) {
            return value.getFileId() == null && !StringUtils.hasText(value.getJsonValue());
        }
        return !StringUtils.hasText(value.getTextValue()) && !StringUtils.hasText(value.getJsonValue());
    }

    private String normalizeFieldType(String value) {
        String normalized = trimRequired(value, "Material field type is required");
        if (!FIELD_TYPES.contains(normalized)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Unsupported material field type");
        }
        return normalized;
    }

    private void validateRegistrationCreateRequest(CompetitionRegistrationDTO.RegistrationCreateRequest request) {
        requirePositiveId(request.getCompetitionId(), "Competition id is required");
        if ((request.getProjectId() == null || request.getProjectId() <= 0)
                && (request.getProjectSnapshot() == null || !StringUtils.hasText(request.getProjectSnapshot().getTitle()))) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Project information is required");
        }
        if (request.getTeamId() != null && request.getTeamId() <= 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Team id is invalid");
        }
        CompetitionRegistrationDTO.TeamSnapshotRequest team = request.getTeamSnapshot();
        if (team != null) {
            requireLength(team.getTeamName(), MAX_NAME_LENGTH, "Team name is too large");
            requireLength(team.getTeamType(), MAX_SHORT_TEXT_LENGTH, "Team type is too large");
            requireLength(team.getAvatarUrl(), MAX_DESCRIPTION_LENGTH, "Team avatar url is too large");
            requireLength(team.getVisibility(), MAX_SHORT_TEXT_LENGTH, "Team visibility is too large");
            requireLength(team.getJoinMode(), MAX_SHORT_TEXT_LENGTH, "Team join mode is too large");
            requireLength(team.getDescription(), MAX_DESCRIPTION_LENGTH, "Team description is too large");
            requireJsonSize(team.getExtraValues(), "Team extra values are too large");
        }
        List<CompetitionRegistrationDTO.MemberSnapshotRequest> members = request.getMembers();
        if (members != null && members.size() > MAX_INLINE_PARTICIPANTS) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Too many registration members");
        }
        long studentCount = (members == null ? List.<CompetitionRegistrationDTO.MemberSnapshotRequest>of() : members)
                .stream()
                .filter(member -> "STUDENT".equals(normalizeParticipantType(member.getParticipantType())))
                .count();
        long teacherCount = (members == null ? List.<CompetitionRegistrationDTO.MemberSnapshotRequest>of() : members)
                .stream()
                .filter(member -> "TEACHER".equals(normalizeParticipantType(member.getParticipantType())))
                .count();
        if (studentCount > MAX_PARTICIPANTS_PER_TYPE || teacherCount > MAX_PARTICIPANTS_PER_TYPE) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Too many registration participants of one type");
        }
        for (CompetitionRegistrationDTO.MemberSnapshotRequest member : members == null ? List.<CompetitionRegistrationDTO.MemberSnapshotRequest>of() : members) {
            normalizeParticipantType(member.getParticipantType());
            requireLength(member.getMemberName(), MAX_NAME_LENGTH, "Member name is too large");
            requireLength(member.getEmployeeNo(), MAX_SHORT_TEXT_LENGTH, "Member employee no is too large");
            requireLength(member.getDepartmentName(), MAX_NAME_LENGTH, "Member department name is too large");
            requireLength(member.getRole(), MAX_SHORT_TEXT_LENGTH, "Member role is too large");
            requireLength(member.getRemark(), 512, "Member remark is too large");
            requireJsonSize(member.getExtraValues(), "Member extra values are too large");
        }
    }

    private List<CollectedFieldDefinition> validateCollectedFields(
            Long competitionId,
            CompetitionRegistrationDTO.RegistrationCreateRequest request,
            TeamSnapshot resolvedTeam,
            ProjectSnapshot project
    ) {
        List<CollectedFieldDefinition> definitions = loadCollectedFieldDefinitions(competitionId);
        validateScopeValues("REGISTRATION_FIELD", request.getRegistrationExtraValues(), definitions, Map.of());

        CompetitionRegistrationDTO.TeamSnapshotRequest team = request.getTeamSnapshot();
        Map<String, Object> teamSummary = toMutableMap(resolvedTeam.summary());
        Map<String, Object> teamStandards = new LinkedHashMap<>();
        teamStandards.put("teamName", teamSummary.get("teamName"));
        teamStandards.put("teamType", teamSummary.get("teamType"));
        teamStandards.put("avatarUrl", teamSummary.get("avatarUrl"));
        teamStandards.put("description", teamSummary.get("description"));
        validateScopeValues("TEAM_FIELD", team == null ? null : team.getExtraValues(), definitions, teamStandards);

        for (CompetitionRegistrationDTO.MemberSnapshotRequest member : request.getMembers() == null
                ? List.<CompetitionRegistrationDTO.MemberSnapshotRequest>of() : request.getMembers()) {
            String participantScope = "TEACHER".equals(normalizeParticipantType(member.getParticipantType()))
                    ? "TEACHER_FIELD" : "MEMBER_FIELD";
            Map<String, Object> memberStandards = new LinkedHashMap<>();
            memberStandards.put("memberName", member.getMemberName());
            memberStandards.put("employeeNo", member.getEmployeeNo());
            memberStandards.put("departmentName", member.getDepartmentName());
            memberStandards.put("role", member.getRole());
            memberStandards.put("remark", member.getRemark());
            validateScopeValues(participantScope, member.getExtraValues(), definitions, memberStandards);
        }
        Map<String, Object> projectSummary = toMutableMap(project.summary());
        Map<String, Object> projectStandards = new LinkedHashMap<>();
        projectStandards.put("title", projectSummary.get("title"));
        projectStandards.put("imageUrl", projectSummary.get("imageUrl"));
        projectStandards.put("description", projectSummary.get("description"));
        validateScopeValues("PROJECT_FIELD",
                request.getProjectSnapshot() == null ? null : request.getProjectSnapshot().getExtraValues(),
                definitions, projectStandards);
        return definitions;
    }

    private List<CollectedFieldDefinition> loadCollectedFieldDefinitions(Long competitionId) {
        List<CollectedFieldDefinition> definitions = new ArrayList<>();
        for (RegistrationQueryRepository.CollectedFieldConfiguration row
                : registrationQueryRepository.findCollectedFieldConfigurations(competitionId)) {
            String contentJson = row.contentJson();
            JsonNode metadata;
            try {
                metadata = StringUtils.hasText(contentJson) ? objectMapper.readTree(contentJson) : objectMapper.createObjectNode();
            } catch (Exception exception) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Invalid competition field configuration");
            }
            String fieldType = RegistrationFieldValidationPolicy.normalizeFieldType(metadata.path("fieldType").asText("TEXT"));
            if (!RegistrationFieldValidationPolicy.FIELD_TYPES.contains(fieldType)) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Unsupported competition field type");
            }
            String configuredValidationRule = RegistrationFieldValidationPolicy.normalizeValidationRule(
                    metadata.path("validationRule").asText("NONE")
            );
            if (!RegistrationFieldValidationPolicy.VALIDATION_RULES.contains(configuredValidationRule)) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Unsupported competition field validation rule");
            }
            definitions.add(new CollectedFieldDefinition(
                    row.itemType(),
                    row.itemKey(),
                    row.title() == null ? row.itemKey() : row.title(),
                    fieldType,
                    Boolean.TRUE.equals(row.requiredFlag()) || Integer.valueOf(1).equals(row.requiredFlag()),
                    RegistrationFieldValidationPolicy.resolveValidationRule(
                            row.itemType(), row.itemKey(), fieldType, configuredValidationRule
                    ),
                    metadata.path("options").asText(""),
                    metadata.path("groupLabel").asText(""),
                    metadata.path("optionSource").asText("CASCADER".equals(fieldType) ? "DICTIONARY" : "CUSTOM")
                            .trim().toUpperCase(Locale.ROOT),
                    metadata.path("dictCode").asText("").trim()
            ));
        }
        ensureProtectedCollectedField(definitions, "TEAM_FIELD", "teamName", "团队名称", "DISPLAY_NAME");
        ensureProtectedCollectedField(definitions, "MEMBER_FIELD", "memberName", "学生姓名", "PERSON_NAME");
        ensureProtectedCollectedField(definitions, "TEACHER_FIELD", "memberName", "指导老师姓名", "PERSON_NAME");
        ensureProtectedCollectedField(definitions, "PROJECT_FIELD", "title", "项目名称", "DISPLAY_NAME");
        return definitions;
    }

    private void ensureProtectedCollectedField(
            List<CollectedFieldDefinition> definitions,
            String scope,
            String standardKey,
            String title,
            String validationRule
    ) {
        for (int index = 0; index < definitions.size(); index += 1) {
            CollectedFieldDefinition field = definitions.get(index);
            if (scope.equals(field.scope())
                    && standardKey.equals(resolveStandardCollectedFieldKey(scope, field.itemKey()))) {
                definitions.set(index, new CollectedFieldDefinition(
                        scope,
                        field.itemKey(),
                        StringUtils.hasText(field.title()) ? field.title() : title,
                        "TEXT",
                        true,
                        validationRule,
                        field.options(),
                        field.groupLabel(),
                        field.optionSource(),
                        field.dictCode()
                ));
                return;
            }
        }
        definitions.add(new CollectedFieldDefinition(
                scope, standardKey, title, "TEXT", true, validationRule, "", "", "CUSTOM", ""
        ));
    }

    private void validateScopeValues(
            String scope,
            Map<String, Object> extraValues,
            List<CollectedFieldDefinition> definitions,
            Map<String, Object> standardValues
    ) {
        List<CollectedFieldDefinition> scoped = definitions.stream().filter(field -> scope.equals(field.scope())).toList();
        if (scoped.isEmpty()) {
            if (extraValues != null) {
                String unknownKey = extraValues.keySet().stream()
                        .filter(key -> !isWorkflowCollectedField(scope, key))
                        .findFirst()
                        .orElse(null);
                if (unknownKey != null) {
                    throw biz(ErrorCode.VALIDATION_ERROR, "Unknown or disabled registration field: " + unknownKey);
                }
            }
            return;
        }
        Map<String, Object> extras = extraValues == null ? Map.of() : extraValues;
        Set<String> configuredKeys = scoped.stream().map(CollectedFieldDefinition::itemKey).collect(java.util.stream.Collectors.toSet());
        for (String key : extras.keySet()) {
            if ((!configuredKeys.contains(key) && !isWorkflowCollectedField(scope, key))
                    || resolveStandardCollectedFieldKey(scope, key) != null) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Unknown or disabled registration field: " + key);
            }
        }
        for (CollectedFieldDefinition field : scoped) {
            if (isIntellectualPropertyField(field)) {
                continue;
            }
            String standardKey = resolveStandardCollectedFieldKey(scope, field.itemKey());
            Object value = standardKey == null ? extras.get(field.itemKey()) : standardValues.get(standardKey);
            if (field.required() && !hasCollectedValue(value)) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Required registration field is missing: " + field.title());
            }
            if (hasCollectedValue(value)) {
                validateCollectedValue(field, value);
            }
        }
        validateIntellectualPropertyValues(extras, scoped.stream()
                .filter(this::isIntellectualPropertyField)
                .toList());
    }

    private void validateIntellectualPropertyValues(
            Map<String, Object> extraValues,
            List<CollectedFieldDefinition> fields
    ) {
        if (fields.isEmpty()) return;
        Object rawEntries = extraValues.get(INTELLECTUAL_PROPERTY_ENTRIES_KEY);
        List<?> entries;
        if (rawEntries instanceof List<?> list) {
            entries = list;
        } else {
            Map<String, Object> legacyEntry = fields.stream()
                    .filter(field -> extraValues.containsKey(field.itemKey()))
                    .collect(java.util.stream.Collectors.toMap(
                            CollectedFieldDefinition::itemKey,
                            field -> extraValues.get(field.itemKey()),
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));
            entries = legacyEntry.isEmpty() ? List.of() : List.of(legacyEntry);
        }
        if (entries.isEmpty()) {
            fields.stream().filter(CollectedFieldDefinition::required).findFirst().ifPresent(field -> {
                throw biz(ErrorCode.VALIDATION_ERROR, "Required registration field is missing: " + field.title());
            });
            return;
        }
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> values)) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Registration field has an invalid value: " + INTELLECTUAL_PROPERTY_GROUP);
            }
            for (CollectedFieldDefinition field : fields) {
                Object value = values.get(field.itemKey());
                if (field.required() && !hasCollectedValue(value)) {
                    throw biz(ErrorCode.VALIDATION_ERROR, "Required registration field is missing: " + field.title());
                }
                if (hasCollectedValue(value)) {
                    validateCollectedValue(field, value);
                }
            }
        }
    }

    private boolean hasCollectedValue(Object value) {
        if (value == null) return false;
        if (value instanceof String text) return StringUtils.hasText(text);
        if (value instanceof List<?> list) return !list.isEmpty();
        if (value instanceof Map<?, ?> map) return !map.isEmpty();
        return true;
    }

    private void validateCollectedValue(CollectedFieldDefinition field, Object value) {
        if ("NUMBER".equals(field.fieldType()) && !(value instanceof Number)) {
            try {
                new BigDecimal(String.valueOf(value));
            } catch (NumberFormatException exception) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Registration field must be numeric: " + field.title());
            }
        }
        if ("MULTI_SELECT".equals(field.fieldType()) && !(value instanceof List<?>)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Registration field must contain multiple choices: " + field.title());
        }
        if (!"MULTI_SELECT".equals(field.fieldType()) && value instanceof List<?>) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Registration field has an invalid value: " + field.title());
        }
        if (Set.of("TEXT", "TEXTAREA", "DATE", "SELECT", "CASCADER", "MOBILE", "EMAIL", "IMAGE", "ROLE").contains(field.fieldType())
                && !(value instanceof CharSequence)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Registration field must be text: " + field.title());
        }
        List<String> options = field.options().lines().map(String::trim).filter(StringUtils::hasText).toList();
        if ("SELECT".equals(field.fieldType()) && !options.isEmpty() && !options.contains(String.valueOf(value))) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Registration field contains an unavailable option: " + field.title());
        }
        if ("MULTI_SELECT".equals(field.fieldType()) && !options.isEmpty()
                && ((List<?>) value).stream().map(String::valueOf).anyMatch(option -> !options.contains(option))) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Registration field contains an unavailable option: " + field.title());
        }
        if ("DICTIONARY".equals(field.optionSource())
                && Set.of("SELECT", "MULTI_SELECT", "CASCADER").contains(field.fieldType())) {
            validateDictionaryCollectedValue(field, value);
        }
        String rawText = String.valueOf(value);
        String text = rawText.trim();
        if ("DATE".equals(field.fieldType()) && !isValidCollectedDate(field, text)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Invalid date: " + field.title());
        }
        if ("IMAGE".equals(field.fieldType()) && (!StringUtils.hasText(text) || text.length() > 2048)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Invalid image value: " + field.title());
        }
        if (!RegistrationFieldValidationPolicy.isValid(field.validationRule(), rawText)) {
            String message = switch (field.validationRule()) {
                case "CHINA_MOBILE" -> "Invalid mobile number: ";
                case "EMAIL" -> "Invalid email address: ";
                case "ID_CARD" -> "Invalid identity card number: ";
                case "PERSON_NAME" -> "Invalid person name: ";
                case "DISPLAY_NAME" -> "Invalid name text: ";
                default -> "Registration field has an invalid value: ";
            };
            throw biz(ErrorCode.VALIDATION_ERROR, message + field.title());
        }
    }

    private void validateDictionaryCollectedValue(CollectedFieldDefinition field, Object value) {
        if (!StringUtils.hasText(field.dictCode())) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Registration field dictionary is not configured: " + field.title());
        }
        DictionaryItemLookupPort lookupPort = dictionaryItemLookupPortProvider == null
                ? null : dictionaryItemLookupPortProvider.getIfAvailable();
        if (lookupPort == null) {
            throw biz(ErrorCode.DEPENDENCY_UNAVAILABLE, "System dictionary service is unavailable");
        }
        List<String> values = value instanceof List<?> list
                ? list.stream().map(String::valueOf).map(String::trim).filter(StringUtils::hasText).distinct().toList()
                : List.of(String.valueOf(value).trim());
        List<DictionaryItemLookupPort.DictionaryItem> available = lookupPort.enabledItemsByValues(field.dictCode(), values);
        Set<String> resolvedValues = available.stream()
                .map(DictionaryItemLookupPort.DictionaryItem::value)
                .collect(java.util.stream.Collectors.toSet());
        if (values.stream().anyMatch(option -> !resolvedValues.contains(option))) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Registration field contains a disabled or unavailable dictionary option: " + field.title());
        }
        if ("CASCADER".equals(field.fieldType())
                && (available.size() != 1 || !available.getFirst().leaf())) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Registration field must select a final administrative division: " + field.title());
        }
    }

    private boolean isYearOnlyMemberDateField(CollectedFieldDefinition field) {
        return Set.of("MEMBER_FIELD", "TEACHER_FIELD").contains(field.scope())
                && Set.of("enrollmentDate", "graduationDate").contains(field.itemKey());
    }

    private boolean isValidCollectedDate(CollectedFieldDefinition field, String text) {
        if (isYearOnlyMemberDateField(field) && text.matches("\\d{4}")) {
            return true;
        }
        try {
            LocalDate.parse(text);
            return true;
        } catch (DateTimeParseException ignored) {
            try {
                DateTimeFormatter.ISO_DATE_TIME.parse(text);
                return true;
            } catch (DateTimeParseException invalidDateTime) {
                return false;
            }
        }
    }

    private boolean isWorkflowCollectedField(String scope, String itemKey) {
        return "PROJECT_FIELD".equals(scope) && INTELLECTUAL_PROPERTY_ENTRIES_KEY.equals(itemKey);
    }

    private boolean isIntellectualPropertyField(CollectedFieldDefinition field) {
        return "PROJECT_FIELD".equals(field.scope()) && INTELLECTUAL_PROPERTY_GROUP.equals(field.groupLabel());
    }

    private String resolveStandardCollectedFieldKey(String scope, String itemKey) {
        // The configured registration role is distinct from the formal team membership role.
        if (Set.of("MEMBER_FIELD", "TEACHER_FIELD").contains(scope) && "role".equals(itemKey)) {
            return null;
        }
        String normalized = itemKey == null ? "" : itemKey.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        Map<String, Set<String>> aliases = switch (scope) {
            case "TEAM_FIELD" -> Map.of(
                    "teamName", Set.of("teamname", "name"),
                    "teamType", Set.of("teamtype", "type"),
                    "avatarUrl", Set.of("avatarurl", "avatar"),
                    "description", Set.of("description", "teamdescription", "intro")
            );
            case "MEMBER_FIELD" -> Map.of(
                    "memberName", Set.of("membername", "name"),
                    "employeeNo", Set.of("employeeno", "studentno", "memberno"),
                    "departmentName", Set.of("departmentname", "department"),
                    "role", Set.of("role"),
                    "remark", Set.of("remark", "note")
            );
            case "TEACHER_FIELD" -> Map.of(
                    "memberName", Set.of("membername", "teachername", "name"),
                    "employeeNo", Set.of("employeeno", "teacherno", "memberno"),
                    "departmentName", Set.of("departmentname", "department", "organization"),
                    "role", Set.of("role"),
                    "remark", Set.of("remark", "note")
            );
            case "PROJECT_FIELD" -> Map.of(
                    "title", Set.of("projecttitle", "projectname", "title", "name"),
                    "imageUrl", Set.of("imageurl", "projectimage", "projectavatar", "logourl", "logo"),
                    "description", Set.of("projectdescription", "description", "intro")
            );
            default -> Map.of();
        };
        return aliases.entrySet().stream()
                .filter(entry -> entry.getValue().contains(normalized))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private void persistCollectionSchemaSnapshot(Long registrationId, List<CollectedFieldDefinition> definitions) {
        registrationWriteRepository.updateCollectionSchemaSnapshot(
                registrationId,
                serializeCollectionSchemaSnapshot(definitions),
                LocalDateTime.now()
        );
    }

    private String serializeCollectionSchemaSnapshot(List<CollectedFieldDefinition> definitions) {
        List<Map<String, Object>> snapshot = definitions.stream().map(field -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("scope", field.scope());
            item.put("itemKey", field.itemKey());
            item.put("title", field.title());
            item.put("fieldType", field.fieldType());
            item.put("required", field.required());
            item.put("validationRule", field.validationRule());
            item.put("options", field.options());
            item.put("groupLabel", field.groupLabel());
            item.put("optionSource", field.optionSource());
            item.put("dictCode", field.dictCode());
            return item;
        }).toList();
        return serialize(snapshot);
    }

    private void requireRegistrationDatasetLink(int linked) {
        if (linked <= 0) {
            throw biz(ErrorCode.BIZ_ERROR, "Registration dataset is unavailable");
        }
    }

    private void validateMaterialSubmitRequest(CompetitionRegistrationDTO.MaterialSubmitRequest request) {
        for (CompetitionRegistrationDTO.MaterialValueRequest value : request.getValues() == null ? List.<CompetitionRegistrationDTO.MaterialValueRequest>of() : request.getValues()) {
            requireLength(value.getFieldKey(), MAX_NAME_LENGTH, "Material field key is too large");
            requireLength(value.getFieldType(), MAX_SHORT_TEXT_LENGTH, "Material field type is too large");
            requireLength(value.getTextValue(), MAX_FIELD_TEXT_LENGTH, "Material text is too large");
            requireLength(value.getJsonValue(), MAX_JSON_LENGTH, "Material json is too large");
        }
    }

    private String generateParticipantNo(Long competitionId) {
        RegistrationQueryRepository.CompetitionDefinition competition = requireCompetition(competitionId);
        long next = registrationQueryRepository.countConfirmedRegistrations(competitionId);
        return competition.code().toUpperCase(Locale.ROOT) + "-" + String.format("%04d", next);
    }

    private String generateRegistrationNo() {
        String random = Long.toString(ThreadLocalRandom.current().nextLong(36L * 36L * 36L), 36).toUpperCase(Locale.ROOT);
        return "REG-" + LocalDateTime.now().format(NO_TIME_FORMATTER) + "-" + random;
    }

    private CompetitionRegistrationVO.Registration findRegistration(Long id) {
        requirePositiveId(id, "Registration id is required");
        return registrationQueryRepository.findRegistration(id);
    }

    private CompetitionRegistrationVO.Registration findRegistrationByPaymentOrder(String orderNo) {
        return registrationQueryRepository.findRegistrationByPaymentOrder(orderNo);
    }

    private CompetitionRegistrationVO.Stage findStage(Long id) {
        requirePositiveId(id, "Stage id is required");
        return registrationQueryRepository.findStage(id);
    }

    private CompetitionRegistrationVO.StageForm findStageForm(Long stageId) {
        requirePositiveId(stageId, "Stage id is required");
        return registrationQueryRepository.findStageForm(stageId);
    }

    private CompetitionRegistrationVO.StageForm findReadableStageFormForRegistration(Long stageId, CurrentUser currentUser) {
        requireRegistrationStageReadPermission(currentUser);
        requirePositiveId(stageId, "Stage id is required");
        return registrationQueryRepository.findReadableStageForm(stageId);
    }

    private CompetitionRegistrationVO.PaymentOrder findPaymentOrder(CompetitionRegistrationVO.Registration registration) {
        return findPaymentOrder(registration, null);
    }

    private CompetitionRegistrationVO.PaymentOrder findPaymentOrder(
            CompetitionRegistrationVO.Registration registration,
            Long simulatedRoleId
    ) {
        if (registration == null || !StringUtils.hasText(registration.getPaymentOrderNo())) {
            return null;
        }
        PaymentInternalApi paymentApi = paymentInternalApiProvider == null
                ? null : paymentInternalApiProvider.getIfAvailable();
        if (paymentApi == null) {
            throw biz(ErrorCode.BIZ_ERROR, "Payment order resolver is unavailable");
        }
        String ownerUserUuid = resolvePaymentOwnerUserUuid(
                registration.getOwnerUserId(), registration.getOwnerUserUuid()
        );
        PaymentOrderDTO source = paymentApi.getOrder(
                registration.getOwnerUserId(), ownerUserUuid, simulatedRoleId, registration.getPaymentOrderNo()
        );
        if (source == null) {
            return null;
        }
        CompetitionRegistrationVO.PaymentOrder order = new CompetitionRegistrationVO.PaymentOrder();
        order.setOrderNo(source.orderNo());
        order.setProviderCode(source.providerCode());
        order.setAmountMinor(source.amountMinor());
        order.setCurrency(source.currency());
        order.setStatus(source.status());
        order.setPaymentUrl(source.paymentUrl());
        return order;
    }

    private CompetitionRegistrationVO.PaymentRecord hydratePaymentRecord(
            CompetitionRegistrationVO.PaymentRecord record
    ) {
        if (record == null) {
            return null;
        }
        if (!StringUtils.hasText(record.getOrderNo())) {
            if (Long.valueOf(0L).equals(record.getPayableAmountMinor())) {
                record.setPaymentStatus("NOT_REQUIRED");
                record.setProviderCode(null);
                record.setProviderOrderNo(null);
            }
            return record;
        }
        PaymentInternalApi paymentApi = paymentInternalApiProvider == null
                ? null : paymentInternalApiProvider.getIfAvailable();
        if (paymentApi == null) {
            return record;
        }
        CompetitionRegistrationVO.Registration registration = findRegistration(record.getRegistrationId());
        if (registration == null) {
            return record;
        }
        try {
            String ownerUserUuid = resolvePaymentOwnerUserUuid(
                    registration.getOwnerUserId(), registration.getOwnerUserUuid()
            );
            PaymentOrderDTO order = paymentApi.getOrder(
                    registration.getOwnerUserId(), ownerUserUuid, record.getOrderNo()
            );
            if (order == null) {
                return record;
            }
            record.setProviderCode(order.providerCode());
            record.setProviderOrderNo(order.providerOrderNo());
            record.setSubject(order.subject());
            record.setAmountMinor(order.amountMinor());
            record.setCurrency(order.currency());
            record.setPaymentStatus(order.status());
            record.setPaymentUrl(order.paymentUrl());
            record.setFailureCode(order.failureCode());
            record.setFailureMessage(order.failureMessage());
            record.setOrderCreatedAt(order.createdAt());
            record.setPaidAt(order.paidAt());
            if (order.updatedAt() != null) {
                record.setUpdatedAt(order.updatedAt());
            }
        } catch (BizException exception) {
            if (exception.getErrorCode() != ErrorCode.NOT_FOUND && !isDisabledPaymentOwner(exception)) {
                throw exception;
            }
            // A registration can outlive a removed payment order or a disabled
            // owner. Keep the registration-side fallback instead of failing an
            // authorized historical payment ledger read.
        } catch (Exception ignored) {
            // A transient payment read must not reveal another owner's order or fail the whole list.
        }
        return record;
    }

    private boolean isDisabledPaymentOwner(BizException exception) {
        return exception != null && PAYMENT_OWNER_DISABLED_MESSAGE.equals(exception.getMessage());
    }

    private boolean matchesPaymentRecord(
            CompetitionRegistrationVO.PaymentRecord record,
            String keyword,
            String paymentStatus,
            String providerCode
    ) {
        if (record == null) {
            return false;
        }
        if (StringUtils.hasText(paymentStatus)
                && !paymentStatus.equalsIgnoreCase(record.getPaymentStatus())) {
            return false;
        }
        if (StringUtils.hasText(providerCode)
                && !providerCode.equalsIgnoreCase(record.getProviderCode())) {
            return false;
        }
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
        return java.util.stream.Stream.of(
                        record.getRegistrationNo(), record.getParticipantNo(), record.getOrderNo(),
                        record.getProviderOrderNo(), record.getCompetitionCode(), record.getCompetitionTitle(),
                        record.getTeamName(), record.getProjectTitle(), record.getProviderCode(), record.getSubject()
                )
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains(normalizedKeyword));
    }

    private void validateParticipantCounts(Long competitionId, int studentCount, int teacherCount) {
        int studentMinMembers = DEFAULT_STUDENT_MIN_MEMBERS;
        int studentMaxMembers = DEFAULT_STUDENT_MAX_MEMBERS;
        int teacherMinMembers = DEFAULT_TEACHER_MIN_MEMBERS;
        int teacherMaxMembers = DEFAULT_TEACHER_MAX_MEMBERS;
        String contentJson = registrationQueryRepository.findTeamSizeLimitsConfiguration(competitionId);
        if (contentJson != null) {
            try {
                JsonNode metadata = objectMapper.readTree(contentJson);
                studentMinMembers = normalizeConfiguredParticipantLimit(
                        metadata.has("studentMinMembers")
                                ? metadata.path("studentMinMembers").asInt(DEFAULT_STUDENT_MIN_MEMBERS)
                                : metadata.path("teamMinMembers").asInt(DEFAULT_STUDENT_MIN_MEMBERS),
                        DEFAULT_STUDENT_MIN_MEMBERS,
                        1
                );
                studentMaxMembers = normalizeConfiguredParticipantLimit(
                        metadata.has("studentMaxMembers")
                                ? metadata.path("studentMaxMembers").asInt(DEFAULT_STUDENT_MAX_MEMBERS)
                                : metadata.path("teamMaxMembers").asInt(DEFAULT_STUDENT_MAX_MEMBERS),
                        DEFAULT_STUDENT_MAX_MEMBERS,
                        1
                );
                teacherMinMembers = normalizeConfiguredParticipantLimit(
                        metadata.path("teacherMinMembers").asInt(DEFAULT_TEACHER_MIN_MEMBERS),
                        DEFAULT_TEACHER_MIN_MEMBERS,
                        0
                );
                teacherMaxMembers = normalizeConfiguredParticipantLimit(
                        metadata.path("teacherMaxMembers").asInt(DEFAULT_TEACHER_MAX_MEMBERS),
                        DEFAULT_TEACHER_MAX_MEMBERS,
                        0
                );
            } catch (Exception ignored) {
                studentMinMembers = DEFAULT_STUDENT_MIN_MEMBERS;
                studentMaxMembers = DEFAULT_STUDENT_MAX_MEMBERS;
                teacherMinMembers = DEFAULT_TEACHER_MIN_MEMBERS;
                teacherMaxMembers = DEFAULT_TEACHER_MAX_MEMBERS;
            }
        }
        if (studentMinMembers > studentMaxMembers || teacherMinMembers > teacherMaxMembers) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Invalid participant limits");
        }
        if (studentCount < studentMinMembers) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Registration requires at least " + studentMinMembers + " students");
        }
        if (studentCount > studentMaxMembers) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Registration allows at most " + studentMaxMembers + " students");
        }
        if (teacherCount < teacherMinMembers) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Registration requires at least " + teacherMinMembers + " teachers");
        }
        if (teacherCount > teacherMaxMembers) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Registration allows at most " + teacherMaxMembers + " teachers");
        }
    }

    private int normalizeConfiguredParticipantLimit(int value, int fallback, int minimum) {
        return value >= minimum && value <= MAX_PARTICIPANTS_PER_TYPE ? value : fallback;
    }

    private int countParticipants(List<?> participants, String participantType) {
        return (int) (participants == null ? List.of() : participants).stream()
                .filter(participant -> {
                    if (participant instanceof Map<?, ?> values) {
                        return participantType.equals(normalizeParticipantType(
                                values.get("participantType") == null ? null : String.valueOf(values.get("participantType"))
                        ));
                    }
                    return "STUDENT".equals(participantType);
                })
                .count();
    }

    private String normalizeParticipantType(String value) {
        if (!StringUtils.hasText(value)) {
            return "STUDENT";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("STUDENT", "TEACHER").contains(normalized)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Unsupported participant type");
        }
        return normalized;
    }

    private Map<String, Object> toMutableMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, item) -> normalized.put(String.valueOf(key), item));
            return normalized;
        }
        if (value instanceof TeamSummaryDTO team) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("teamName", team.getTeamName());
            normalized.put("teamType", team.getTeamType());
            normalized.put("visibility", team.getVisibility());
            normalized.entrySet().removeIf(entry -> entry.getValue() == null);
            return normalized;
        }
        return new LinkedHashMap<>();
    }

    private boolean canAccessRegistration(CurrentUser currentUser, CompetitionRegistrationVO.Registration registration) {
        return canViewAllRegistrations(currentUser)
                || (requireUserId(currentUser).equals(registration.getOwnerUserId())
                && requireUserUuid(currentUser).equals(registration.getOwnerUserUuid()));
    }

    private boolean canViewAllPaymentRecords(CurrentUser currentUser) {
        return canViewAllRegistrations(currentUser) || hasPermission(currentUser, "payment:order:view");
    }

    private boolean canReadAllStages(CurrentUser currentUser) {
        return hasPermission(currentUser, STAGE_VIEW_PERMISSION) || hasPermission(currentUser, STAGE_MANAGE_PERMISSION);
    }

    private void requireRegistrationReadPermission(CurrentUser currentUser) {
        requireAnyPermission(
                currentUser,
                REGISTRATION_VIEW_PERMISSION,
                REGISTRATION_VIEW_PERMISSION,
                REGISTRATION_CREATE_PERMISSION,
                REGISTRATION_UPDATE_PERMISSION,
                REGISTRATION_PAY_PERMISSION,
                MATERIAL_VIEW_PERMISSION,
                MATERIAL_SUBMIT_PERMISSION,
                MATERIAL_DOWNLOAD_PERMISSION,
                DATASET_VIEW_PERMISSION,
                DATASET_EXPORT_PERMISSION,
                PAYMENT_ORDER_VIEW_PERMISSION,
                STAGE_MANAGE_PERMISSION
        );
    }

    private void requireRegistrationStageReadPermission(CurrentUser currentUser) {
        requireAnyPermission(
                currentUser,
                STAGE_VIEW_PERMISSION,
                STAGE_VIEW_PERMISSION,
                REGISTRATION_VIEW_PERMISSION,
                REGISTRATION_CREATE_PERMISSION,
                REGISTRATION_UPDATE_PERMISSION,
                REGISTRATION_PAY_PERMISSION,
                MATERIAL_VIEW_PERMISSION,
                MATERIAL_SUBMIT_PERMISSION,
                PAYMENT_ORDER_VIEW_PERMISSION,
                STAGE_MANAGE_PERMISSION
        );
    }

    private void requirePaymentOrderViewPermission(CurrentUser currentUser) {
        requireAnyPermission(currentUser, PAYMENT_ORDER_VIEW_PERMISSION, PAYMENT_ORDER_VIEW_PERMISSION);
    }

    private void requireAnyPermission(CurrentUser currentUser, String messagePermission, String... permissionKeys) {
        requireUserId(currentUser);
        Set<String> permissions = trustedPermissions(currentUser);
        if (permissions.contains("*")) {
            return;
        }
        if (permissions.contains(messagePermission)) {
            return;
        }
        for (String permissionKey : permissionKeys) {
            if (permissions.contains(permissionKey)) {
                return;
            }
        }
        throw biz(ErrorCode.FORBIDDEN, "Missing permission: " + messagePermission);
    }

    private boolean canViewAllRegistrations(CurrentUser currentUser) {
        return resolveRegistrationDataPermission(currentUser).scopeType() == DataScopeType.ALL;
    }

    private boolean isRegistrationOwner(
            CurrentUser currentUser,
            CompetitionRegistrationVO.Registration registration
    ) {
        return registration != null
                && requireUserId(currentUser).equals(registration.getOwnerUserId())
                && requireUserUuid(currentUser).equals(registration.getOwnerUserUuid());
    }

    private boolean canViewSensitiveRegistrationData(CurrentUser currentUser) {
        return hasPermission(currentUser, DATASET_VIEW_SENSITIVE_PERMISSION)
                || hasPermission(currentUser, DATASET_EXPORT_SENSITIVE_PERMISSION);
    }

    private void redactRegistrationSnapshots(CompetitionRegistrationVO.Registration registration) {
        registration.setRegistrationSnapshotJson("{}");
        registration.setTeamSnapshotJson("{}");
        registration.setMemberSnapshotJson("[]");
    }

    private DataPermissionDecision resolveRegistrationDataPermission(CurrentUser currentUser) {
        refreshTrustedCurrentUser(currentUser);
        if (!isTrustedCurrentUser(currentUser)) {
            return DataPermissionDecision.self(null);
        }
        Long actorUserId = requireUserId(currentUser);
        return DataPermissionResolver.resolve(
                REGISTRATION_DATA_SCOPE_RESOURCE,
                actorUserId,
                Set.of(),
                Set.of(),
                currentUser.getDataScopes() == null ? List.<DataPermissionRule>of() : currentUser.getDataScopes(),
                trustedPermissions(currentUser)
        );
    }

    private boolean hasPermission(CurrentUser currentUser, String permissionKey) {
        Set<String> permissions = trustedPermissions(currentUser);
        return permissions.contains("*") || permissions.contains(permissionKey);
    }

    private Long requirePermission(CurrentUser currentUser, String permissionKey) {
        Long userId = requireUserId(currentUser);
        if (!hasPermission(currentUser, permissionKey)) {
            throw biz(ErrorCode.FORBIDDEN, "Missing permission: " + permissionKey);
        }
        return userId;
    }

    private Long requireUserId(CurrentUser currentUser) {
        refreshTrustedCurrentUser(currentUser);
        if (!isTrustedCurrentUser(currentUser)) {
            throw biz(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser.getUserId();
    }

    private String requireUserUuid(CurrentUser currentUser) {
        refreshTrustedCurrentUser(currentUser);
        if (!isTrustedCurrentUser(currentUser)) {
            throw biz(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return currentUser.getUserUuid().trim();
    }

    private Set<String> trustedPermissions(CurrentUser currentUser) {
        refreshTrustedCurrentUser(currentUser);
        if (!isTrustedCurrentUser(currentUser)) {
            return Set.of();
        }
        return currentUser.getPermissions() == null ? Set.of() : currentUser.getPermissions();
    }

    private Long requirePositiveUserId(Long userId, String message) {
        if (userId == null || userId <= 0) {
            throw biz(ErrorCode.UNAUTHORIZED, message);
        }
        return userId;
    }

    private void requireRequest(Object request, String message) {
        if (request == null) {
            throw biz(ErrorCode.VALIDATION_ERROR, message);
        }
    }

    private void requirePositiveId(Long id, String message) {
        if (id == null || id <= 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, message);
        }
    }

    private void requireRegistrationWrite(int updated, String message) {
        if (updated <= 0) {
            throw biz(ErrorCode.BIZ_ERROR, message);
        }
    }

    private void refreshTrustedCurrentUser(CurrentUser currentUser) {
        CompetitionAuthenticationTrust.refresh(
                currentUser,
                trustedCurrentUserResolver,
                enforceTrustedUserResolution
        );
    }

    private boolean isTrustedCurrentUser(CurrentUser currentUser) {
        return AuthenticationTrustSupport.isTrustedCurrentUser(currentUser);
    }

    private void validateStageWindows(CompetitionRegistrationDTO.StageUpsertRequest request) {
        requireChronologicalRange(request.getMaterialSubmitStart(), request.getMaterialSubmitEnd(), "Material submission end must be after its start");
        requireChronologicalRange(request.getReviewStart(), request.getReviewEnd(), "Review end must be after its start");
        if (request.getMaterialSubmitEnd() != null && request.getReviewStart() != null
                && request.getReviewStart().isBefore(request.getMaterialSubmitEnd())) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Review cannot start before material submission closes");
        }
        if (StringUtils.hasText(request.getPromotionRuleType())) {
            String ruleType = normalizeEnum(request.getPromotionRuleType(), null, PROMOTION_RULE_TYPES, "Invalid promotion rule type");
            BigDecimal ruleValue = request.getPromotionRuleValue();
            if (!"MANUAL".equals(ruleType) && (ruleValue == null || ruleValue.compareTo(BigDecimal.ZERO) <= 0)) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Promotion rule value must be greater than zero");
            }
            if ("PERCENTAGE".equals(ruleType) && ruleValue.compareTo(new BigDecimal("100")) > 0) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Promotion percentage cannot exceed 100");
            }
        }
    }

    private void requireChronologicalRange(LocalDateTime start, LocalDateTime end, String message) {
        if ((start == null) != (end == null)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Both start and end time are required");
        }
        if (start != null && !end.isAfter(start)) {
            throw biz(ErrorCode.VALIDATION_ERROR, message);
        }
    }

    private void requireRegistrationWindowOpen(RegistrationQueryRepository.CompetitionDefinition competition) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = parseBusinessDateTime(competition.registrationStart());
        LocalDateTime end = parseBusinessDateTime(competition.registrationEnd());
        if (start != null && now.isBefore(start)) {
            throw biz(ErrorCode.BIZ_ERROR, "报名尚未开始");
        }
        if (end != null && now.isAfter(end)) {
            throw biz(ErrorCode.BIZ_ERROR, "报名已截止，不能新增或修改报名信息");
        }
    }

    private LocalDateTime parseBusinessDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().replace('T', ' ');
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        )) {
            try {
                return LocalDateTime.parse(normalized, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported persisted format.
            }
        }
        throw biz(ErrorCode.VALIDATION_ERROR, "赛事时间格式无效");
    }

    private void requireMaterialWindowOpen(CompetitionRegistrationVO.Stage stage, CompetitionRegistrationVO.Registration registration) {
        if (stage == null || !stage.getCompetitionId().equals(registration.getCompetitionId())) {
            throw biz(ErrorCode.NOT_FOUND, "Stage not found");
        }
        if (!"ENABLED".equals(stage.getStatus())) {
            throw biz(ErrorCode.BIZ_ERROR, "当前阶段未开放材料提交");
        }
        LocalDateTime now = LocalDateTime.now();
        if (stage.getMaterialSubmitStart() == null || stage.getMaterialSubmitEnd() == null
                || now.isBefore(stage.getMaterialSubmitStart()) || now.isAfter(stage.getMaterialSubmitEnd())) {
            throw biz(ErrorCode.BIZ_ERROR, "当前不在材料提交时间内");
        }
        if ("FINAL".equals(stage.getStageCode())) {
            if (!registrationQueryRepository.hasPublishedPreliminaryAdvance(
                    registration.getCompetitionId(),
                    registration.getId()
            )) {
                throw biz(ErrorCode.FORBIDDEN, "仅已公布晋级的团队可以修改决赛材料");
            }
        }
    }

    private void requireReviewWindowOpen(CompetitionRegistrationVO.Stage stage) {
        if (!"ENABLED".equals(stage.getStatus())) {
            throw biz(ErrorCode.BIZ_ERROR, "当前阶段未开放评审");
        }
        LocalDateTime now = LocalDateTime.now();
        if (stage.getReviewStart() == null || stage.getReviewEnd() == null
                || now.isBefore(stage.getReviewStart()) || now.isAfter(stage.getReviewEnd())) {
            throw biz(ErrorCode.BIZ_ERROR, "当前不在评审时间内");
        }
    }

    private void hydrateMaterialAccess(CompetitionRegistrationVO.Stage stage, CurrentUser currentUser) {
        LocalDateTime now = LocalDateTime.now();
        if (stage.getMaterialSubmitStart() == null || stage.getMaterialSubmitEnd() == null) {
            stage.setMaterialEditable(false);
            stage.setMaterialAccessReason("材料提交时间未配置");
            return;
        }
        if (now.isBefore(stage.getMaterialSubmitStart())) {
            stage.setMaterialEditable(false);
            stage.setMaterialAccessReason("材料提交尚未开始");
            return;
        }
        if (now.isAfter(stage.getMaterialSubmitEnd())) {
            stage.setMaterialEditable(false);
            stage.setMaterialAccessReason("材料修改已截止");
            return;
        }
        if ("FINAL".equals(stage.getStageCode())) {
            if (!registrationQueryRepository.hasPublishedPreliminaryAdvanceForOwner(
                    stage.getCompetitionId(),
                    requireUserId(currentUser),
                    requireUserUuid(currentUser)
            )) {
                stage.setMaterialEditable(false);
                stage.setMaterialAccessReason("未进入决赛或晋级结果尚未公布");
                return;
            }
        }
        stage.setMaterialEditable(true);
        stage.setMaterialAccessReason("当前可修改");
    }

    private String normalizeEnum(String value, String defaultValue, Set<String> allowed, String message) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : defaultValue;
        if (normalized == null || !allowed.contains(normalized)) {
            throw biz(ErrorCode.VALIDATION_ERROR, message);
        }
        return normalized;
    }

    private String trimRequired(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw biz(ErrorCode.VALIDATION_ERROR, message);
        }
        return trimmed;
    }

    private void requireLength(String value, int maxLength, String message) {
        if (value != null && value.length() > maxLength) {
            throw biz(ErrorCode.VALIDATION_ERROR, message);
        }
    }

    private void requireJsonSize(Object value, String message) {
        if (value == null) {
            return;
        }
        try {
            if (objectMapper.writeValueAsString(value).length() > MAX_JSON_LENGTH) {
                throw biz(ErrorCode.VALIDATION_ERROR, message);
            }
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw biz(ErrorCode.VALIDATION_ERROR, message);
        }
    }

    private String trimTaskText(Object value, String fallback) {
        String text = value == null ? null : String.valueOf(value);
        return StringUtils.hasText(text) ? text.trim() : fallback;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw biz(ErrorCode.BIZ_ERROR, "Failed to serialize registration data");
        }
    }

    private String serializeRegistrationSnapshot(Map<String, Object> registrationExtraValues) {
        Map<String, Object> values = registrationExtraValues == null
                ? Map.of() : new LinkedHashMap<>(registrationExtraValues);
        requireJsonSize(values, "Registration extra values are too large");
        return serialize(values);
    }

    private static BizException biz(ErrorCode code, String message) {
        return new BizException(code, message, message);
    }

    private record TeamSnapshot(Long teamId, Object summary, List<?> members) {
    }

    private record ProjectSnapshot(Long projectId, Object summary) {
    }

    private String normalizeRegistrationReturnUrl(String value, Long registrationId) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            URI uri = URI.create(value.trim());
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || !StringUtils.hasText(uri.getHost())
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null
                    || !"/competitions/register/payment-result".equals(uri.getPath())) {
                throw new IllegalArgumentException("unsupported return URL");
            }
            String query = uri.getRawQuery();
            if (StringUtils.hasText(query)
                    && !query.equals("registrationId=" + registrationId)
                    && !query.equals("registrationId=" + String.valueOf(registrationId))) {
                throw new IllegalArgumentException("unsupported return URL query");
            }
            return uri.toString();
        } catch (RuntimeException exception) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Invalid registration payment return URL");
        }
    }

    private record CollectedFieldDefinition(
            String scope,
            String itemKey,
            String title,
            String fieldType,
            boolean required,
            String validationRule,
            String options,
            String groupLabel,
            String optionSource,
            String dictCode
    ) {
    }

}

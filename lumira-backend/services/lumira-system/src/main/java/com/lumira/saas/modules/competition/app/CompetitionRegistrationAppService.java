package com.lumira.saas.modules.competition.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.api.client.PaymentInternalApi;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.payment.PaymentCreateOrderRequestDTO;
import com.lumira.api.payment.PaymentOrderDTO;
import com.lumira.api.payment.PaymentCheckoutOptionDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.data.DataPermissionDecision;
import com.lumira.common.security.data.DataPermissionResolver;
import com.lumira.common.security.data.DataPermissionRule;
import com.lumira.common.security.data.DataScopeType;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.infrastructure.persistence.mybatis.BeanPropertyRowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.security.service.SessionAuthenticationService;
import com.lumira.saas.modules.competition.dto.CompetitionRegistrationDTO;
import com.lumira.saas.modules.competition.vo.CompetitionRegistrationVO;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.team.api.TeamInternalApi;
import com.lumira.team.api.TeamMemberDTO;
import com.lumira.team.api.TeamSummaryDTO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.regex.Pattern;

@Service
public class CompetitionRegistrationAppService {

    private static final String REGISTRATION_DATA_SCOPE_RESOURCE = "competition:registration";
    private static final String REGISTRATION_VIEW_PERMISSION = "aiadc:registration:view";
    private static final String REGISTRATION_CREATE_PERMISSION = "aiadc:registration:create";
    private static final String REGISTRATION_UPDATE_PERMISSION = "aiadc:registration:update";
    private static final String REGISTRATION_PAY_PERMISSION = "aiadc:registration:pay";
    private static final String MATERIAL_VIEW_PERMISSION = "aiadc:material:view";
    private static final String MATERIAL_SUBMIT_PERMISSION = "aiadc:material:submit";
    private static final String STAGE_VIEW_PERMISSION = "aiadc:stage:view";
    private static final String STAGE_MANAGE_PERMISSION = "aiadc:stage:manage";
    private static final String PAYMENT_ORDER_VIEW_PERMISSION = "payment:order:view";
    private static final String PAYMENT_ORDER_JOIN_ON =
            "po.order_no collate utf8mb4_unicode_ci = cr.payment_order_no collate utf8mb4_unicode_ci ";
    private static final Set<String> REGISTRATION_STATUSES = Set.of("DRAFT", "PENDING_PAYMENT", "PAID", "CONFIRMED", "CANCELLED");
    private static final Set<String> STAGE_CODES = Set.of("PRELIMINARY", "FINAL");
    private static final Set<String> STAGE_STATUSES = Set.of("DRAFT", "ENABLED", "DISABLED", "CLOSED");
    private static final Set<String> REVIEW_DECISIONS = Set.of("PENDING", "ADVANCED", "ELIMINATED");
    private static final Set<String> PROMOTION_RULE_TYPES = Set.of("PERCENTAGE", "COUNT", "MANUAL");
    private static final Set<String> FORM_STATUSES = Set.of("ENABLED", "DISABLED");
    private static final Set<String> FIELD_TYPES = Set.of("input", "textarea", "file");
    private static final long MAX_PAGE_SIZE = 100L;
    private static final int MAX_INLINE_MEMBERS = 20;
    private static final int DEFAULT_TEAM_MIN_MEMBERS = 1;
    private static final int DEFAULT_TEAM_MAX_MEMBERS = 20;
    private static final int MAX_SHORT_TEXT_LENGTH = 64;
    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_DESCRIPTION_LENGTH = 1000;
    private static final int MAX_FIELD_TEXT_LENGTH = 5000;
    private static final int MAX_JSON_LENGTH = 10000;
    private static final DateTimeFormatter NO_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final int PAYMENT_ORDER_TASK_MAX_RETRY = 8;
    private static final Set<String> COLLECTION_FIELD_TYPES = Set.of(
            "TEXT", "TEXTAREA", "IMAGE", "ROLE", "NUMBER", "DATE", "SELECT", "MULTI_SELECT", "MOBILE", "EMAIL"
    );
    private static final String INTELLECTUAL_PROPERTY_GROUP = "知识产权信息";
    private static final String INTELLECTUAL_PROPERTY_ENTRIES_KEY = "intellectualProperties";
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final MyBatisQueryOperations jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<TeamInternalApi> teamInternalApiProvider;
    private final ObjectProvider<PaymentInternalApi> paymentInternalApiProvider;
    private final ObjectProvider<SystemInternalApi> systemInternalApiProvider;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SessionAuthenticationService sessionAuthenticationService;
    private final boolean enforceTrustedUserResolution;

    @Autowired
    public CompetitionRegistrationAppService(
            MyBatisQueryOperations jdbcTemplate,
            ObjectMapper objectMapper,
            ObjectProvider<TeamInternalApi> teamInternalApiProvider,
            ObjectProvider<PaymentInternalApi> paymentInternalApiProvider,
            ObjectProvider<SystemInternalApi> systemInternalApiProvider,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService
    ) {
        this(jdbcTemplate, objectMapper, teamInternalApiProvider, paymentInternalApiProvider, systemInternalApiProvider, permissionSnapshotService, sessionAuthenticationService, true);
    }

    private CompetitionRegistrationAppService(
            MyBatisQueryOperations jdbcTemplate,
            ObjectMapper objectMapper,
            ObjectProvider<TeamInternalApi> teamInternalApiProvider,
            ObjectProvider<PaymentInternalApi> paymentInternalApiProvider,
            ObjectProvider<SystemInternalApi> systemInternalApiProvider,
            PermissionSnapshotService permissionSnapshotService,
            SessionAuthenticationService sessionAuthenticationService,
            boolean enforceTrustedUserResolution
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.teamInternalApiProvider = teamInternalApiProvider;
        this.paymentInternalApiProvider = paymentInternalApiProvider;
        this.systemInternalApiProvider = systemInternalApiProvider;
        this.permissionSnapshotService = permissionSnapshotService;
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    public CompetitionRegistrationAppService(
            MyBatisQueryOperations jdbcTemplate,
            ObjectMapper objectMapper,
            ObjectProvider<TeamInternalApi> teamInternalApiProvider,
            ObjectProvider<PaymentInternalApi> paymentInternalApiProvider,
            ObjectProvider<SystemInternalApi> systemInternalApiProvider,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this(jdbcTemplate, objectMapper, teamInternalApiProvider, paymentInternalApiProvider, systemInternalApiProvider, permissionSnapshotService, null, false);
    }

    public CompetitionRegistrationAppService(
            MyBatisQueryOperations jdbcTemplate,
            ObjectMapper objectMapper,
            ObjectProvider<TeamInternalApi> teamInternalApiProvider,
            ObjectProvider<PaymentInternalApi> paymentInternalApiProvider,
            ObjectProvider<SystemInternalApi> systemInternalApiProvider
    ) {
        this(jdbcTemplate, objectMapper, teamInternalApiProvider, paymentInternalApiProvider, systemInternalApiProvider, null, null, false);
    }

    public PageResponse<CompetitionRegistrationVO.Registration> listRegistrations(CurrentUser currentUser, long pageNo, long pageSize) {
        requireRegistrationReadPermission(currentUser);
        long safePageNo = Math.max(1L, pageNo);
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        List<Object> params = new ArrayList<>();
        String where = " from competition_registration where deleted = 0";
        if (!canViewAllRegistrations(currentUser)) {
            where += " and owner_user_id = ? and owner_user_uuid = ?";
            params.add(requireUserId(currentUser));
            params.add(requireUserUuid(currentUser));
        }
        Long total = jdbcTemplate.queryForObject("select count(1)" + where, Long.class, params.toArray());
        List<Object> selectParams = new ArrayList<>(params);
        selectParams.add((safePageNo - 1) * safePageSize);
        selectParams.add(safePageSize);
        List<CompetitionRegistrationVO.Registration> records = jdbcTemplate.query(
                registrationListSelect() + where + " order by created_at desc, id desc limit ?, ?",
                new BeanPropertyRowMapper<>(CompetitionRegistrationVO.Registration.class),
                selectParams.toArray()
        );
        PageResponse<CompetitionRegistrationVO.Registration> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total == null ? 0L : total);
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
        requirePaymentOrderViewPermission(currentUser);
        long safePageNo = Math.max(1L, pageNo);
        long safePageSize = Math.max(1L, Math.min(pageSize, MAX_PAGE_SIZE));
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                from competition_registration cr
                left join payment_order po on
                """ + PAYMENT_ORDER_JOIN_ON + """
                and po.deleted = 0
                left join aiadc_competition c on c.id = cr.competition_id and c.deleted = 0
                left join aiadc_project p on p.id = cr.project_id and p.deleted = 0
                left join team t on t.id = cr.team_id and t.deleted = 0
                where cr.deleted = 0
                """);
        if (!canViewAllPaymentRecords(currentUser)) {
            where.append(" and cr.owner_user_id = ? and cr.owner_user_uuid = ?");
            params.add(requireUserId(currentUser));
            params.add(requireUserUuid(currentUser));
        }
        String normalizedKeyword = trimToNull(keyword);
        if (normalizedKeyword != null) {
            where.append("""
                     and (
                         cr.registration_no like ?
                         or cr.participant_no like ?
                         or cr.payment_order_no like ?
                         or po.provider_order_no like ?
                         or c.code like ?
                         or c.title like ?
                         or t.team_name like ?
                         or p.title like ?
                         or json_unquote(json_extract(cr.team_snapshot_json, '$.teamName')) like ?
                         or json_unquote(json_extract(cr.project_snapshot_json, '$.title')) like ?
                     )
                    """);
            String likeKeyword = "%" + normalizedKeyword + "%";
            for (int i = 0; i < 10; i += 1) {
                params.add(likeKeyword);
            }
        }
        String normalizedPaymentStatus = trimToNull(paymentStatus);
        if (normalizedPaymentStatus != null) {
            where.append(" and coalesce(po.status, cr.status) = ?");
            params.add(normalizedPaymentStatus.toUpperCase(Locale.ROOT));
        }
        String normalizedRegistrationStatus = trimToNull(registrationStatus);
        if (normalizedRegistrationStatus != null) {
            where.append(" and cr.status = ?");
            params.add(normalizedRegistrationStatus.toUpperCase(Locale.ROOT));
        }
        String normalizedProviderCode = trimToNull(providerCode);
        if (normalizedProviderCode != null) {
            where.append(" and po.provider_code = ?");
            params.add(normalizedProviderCode);
        }

        Long total = jdbcTemplate.queryForObject("select count(1) " + where, Long.class, params.toArray());
        List<Object> selectParams = new ArrayList<>(params);
        selectParams.add((safePageNo - 1) * safePageSize);
        selectParams.add(safePageSize);
        List<CompetitionRegistrationVO.PaymentRecord> records = jdbcTemplate.query(
                paymentRecordSelect()
                        + where
                        + """
                         order by coalesce(po.created_at, cr.updated_at, cr.created_at) desc, cr.id desc
                         limit ?, ?
                        """,
                new BeanPropertyRowMapper<>(CompetitionRegistrationVO.PaymentRecord.class),
                selectParams.toArray()
        );
        PageResponse<CompetitionRegistrationVO.PaymentRecord> response = new PageResponse<>();
        response.setRecords(records);
        response.setTotal(total == null ? 0L : total);
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
        return registration;
    }

    public List<CompetitionRegistrationVO.MaterialSubmission> listMaterials(CurrentUser currentUser, Long registrationId) {
        requirePositiveId(registrationId, "Registration id is required");
        CompetitionRegistrationVO.Registration registration = getRegistration(currentUser, registrationId);
        List<CompetitionRegistrationVO.MaterialSubmission> submissions = jdbcTemplate.query(
                """
                        select id, registration_id as registrationId, competition_id as competitionId,
                               stage_id as stageId, form_version as formVersion,
                               submitter_user_id as submitterUserId, submitter_user_uuid as submitterUserUuid,
                               status, submitted_at as submittedAt, locked_at as lockedAt
                        from registration_material_submission
                        where registration_id = ? and competition_id = ? and deleted = 0
                        order by stage_id asc, id desc
                        """,
                new BeanPropertyRowMapper<>(CompetitionRegistrationVO.MaterialSubmission.class),
                                registrationId,
                registration.getCompetitionId()
        );
        for (CompetitionRegistrationVO.MaterialSubmission submission : submissions) {
            submission.setValues(jdbcTemplate.query(
                    """
                            select id, submission_id as submissionId, field_key as fieldKey, field_type as fieldType,
                                   text_value as textValue, file_id as fileId, json_value as jsonValue
                            from registration_material_value
                            where submission_id = ? and deleted = 0
                            order by id asc
                            """,
                    new BeanPropertyRowMapper<>(CompetitionRegistrationVO.MaterialValue.class),
                                        submission.getId()
            ));
        }
        return submissions;
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
        return confirmed;
    }

    @Transactional
    public CompetitionRegistrationVO.Registration createRegistration(CurrentUser currentUser, CompetitionRegistrationDTO.RegistrationCreateRequest request) {
        Long userId = requirePermission(currentUser, REGISTRATION_CREATE_PERMISSION);
        String userUuid = requireUserUuid(currentUser);
        requireRequest(request, "Registration request is required");
        validateRegistrationCreateRequest(request);
        CompetitionRow competition = requireCompetition(request.getCompetitionId());
        requireRegistrationWindowOpen(competition);
        TeamSnapshot team = resolveTeamSnapshot(currentUser, request);
        ProjectSnapshot project = requireProjectSnapshot(request.getProjectId(), request.getProjectSnapshot());
        List<CollectedFieldDefinition> fieldDefinitions = validateCollectedFields(competition.id(), request, team, project);
        int memberCount = team.members().size();
        validateTeamMemberCount(competition.id(), memberCount);
        long payableAmountMinor = calculatePayableAmount(competition.feeMode(), competition.entryFeeMinor(), memberCount);
        int inserted = jdbcTemplate.update(
                """
                        insert into competition_registration (
                            registration_no, competition_id, team_id, project_id, owner_user_id, owner_user_uuid,
                            status, fee_mode, entry_fee_minor, member_count, payable_amount_minor, currency,
                            registration_snapshot_json, team_snapshot_json, project_snapshot_json, member_snapshot_json,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                                generateRegistrationNo(),
                competition.id(),
                team.teamId(),
                project.projectId(),
                userId,
                userUuid,
                "PENDING_PAYMENT",
                competition.feeMode(),
                competition.entryFeeMinor(),
                memberCount,
                payableAmountMinor,
                competition.currency(),
                serializeRegistrationSnapshot(request.getRegistrationExtraValues()),
                serialize(team.summary()),
                serialize(project.summary()),
                serialize(team.members()),
                userId,
                userUuid,
                userId,
                userUuid
        );
        requireRegistrationWrite(inserted, "Registration changed, please retry");
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        persistCollectionSchemaSnapshot(id, fieldDefinitions);
        return getRegistration(currentUser, id);
    }

    @Transactional
    public CompetitionRegistrationVO.Registration updateRegistration(CurrentUser currentUser, Long id, CompetitionRegistrationDTO.RegistrationCreateRequest request) {
        requirePermission(currentUser, REGISTRATION_UPDATE_PERMISSION);
        requirePositiveId(id, "Registration id is required");
        requireRequest(request, "Registration request is required");
        validateRegistrationCreateRequest(request);
        CompetitionRegistrationVO.Registration existing = getRegistration(currentUser, id);
        CompetitionRow competition = requireCompetition(request.getCompetitionId());
        requireRegistrationWindowOpen(competition);
        TeamSnapshot team = resolveTeamSnapshot(currentUser, request);
        ProjectSnapshot project = requireProjectSnapshot(request.getProjectId(), request.getProjectSnapshot());
        List<CollectedFieldDefinition> fieldDefinitions = validateCollectedFields(competition.id(), request, team, project);
        int memberCount = team.members().size();
        validateTeamMemberCount(competition.id(), memberCount);
        long payableAmountMinor = calculatePayableAmount(competition.feeMode(), competition.entryFeeMinor(), memberCount);
        if (!Set.of("DRAFT", "PENDING_PAYMENT").contains(existing.getStatus())) {
            throw biz(ErrorCode.BIZ_ERROR, "Paid registrations cannot be changed");
        }
        int updated = jdbcTemplate.update(
                """
                        update competition_registration
                        set competition_id = ?, team_id = ?, project_id = ?, fee_mode = ?, entry_fee_minor = ?,
                            member_count = ?, payable_amount_minor = ?, currency = ?, registration_snapshot_json = ?,
                            team_snapshot_json = ?, project_snapshot_json = ?, member_snapshot_json = ?,
                            updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and registration_no = ? and owner_user_id = ? and owner_user_uuid = ?
                          and status = ? and deleted = 0
                        """,
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
                LocalDateTime.now(),
                id,
                existing.getRegistrationNo(),
                requirePositiveUserId(existing.getOwnerUserId(), "Registration owner is missing"),
                requireRegistrationOwnerUserUuid(existing),
                existing.getStatus()
        );
        requireRegistrationWrite(updated, "Registration changed, please retry");
        persistCollectionSchemaSnapshot(id, fieldDefinitions);
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
        jdbcTemplate.update(
                """
                        update competition_payment_order_task
                        set status = 'CANCELLED', process_message = 'Registration deleted before payment',
                            claim_token = null, claim_expires_at = null, updated_by = ?, updated_by_uuid = ?,
                            updated_at = ?, deleted = 1
                        where registration_id = ? and deleted = 0 and status not in ('SUCCEEDED', 'CANCELLED')
                        """,
                operatorId, operatorUuid, LocalDateTime.now(), id
        );
        int deleted = jdbcTemplate.update(
                """
                        update competition_registration
                        set status = 'CANCELLED', updated_by = ?, updated_by_uuid = ?, updated_at = ?, deleted = 1
                        where id = ? and registration_no = ? and owner_user_id = ? and owner_user_uuid = ?
                          and status = 'PENDING_PAYMENT' and deleted = 0
                        """,
                operatorId,
                operatorUuid,
                LocalDateTime.now(),
                id,
                existing.getRegistrationNo(),
                requirePositiveUserId(existing.getOwnerUserId(), "Registration owner is missing"),
                requireRegistrationOwnerUserUuid(existing)
        );
        requireRegistrationWrite(deleted, "Registration changed, please retry");
        return true;
    }

    public List<CompetitionRegistrationVO.Stage> listStages(CurrentUser currentUser, Long competitionId) {
        requirePositiveId(competitionId, "Competition id is required");
        requireUserId(currentUser);
        if (canReadAllStages(currentUser)) {
            requireCompetition(competitionId);
            return jdbcTemplate.query(
                    stageSelect() + " from competition_stage where competition_id = ? and deleted = 0 order by sort asc, id asc",
                    new BeanPropertyRowMapper<>(CompetitionRegistrationVO.Stage.class),
                    competitionId
            );
        }
        requireRegistrationStageReadPermission(currentUser);
        List<CompetitionRegistrationVO.Stage> stages = jdbcTemplate.query(
                """
                        select s.id, s.competition_id as competitionId, s.stage_code as stageCode,
                               s.stage_name as stageName, s.material_submit_start as materialSubmitStart,
                               s.material_submit_end as materialSubmitEnd, s.review_start as reviewStart, s.review_end as reviewEnd,
                               s.status, s.sort, s.promotion_rule_type as promotionRuleType,
                               s.promotion_rule_value as promotionRuleValue, s.promotion_tie_policy as promotionTiePolicy
                        from competition_stage s
                        join aiadc_competition c on c.id = s.competition_id and c.deleted = 0 and c.status = 'published'
                        where s.competition_id = ? and s.status = 'ENABLED' and s.deleted = 0
                        order by s.sort asc, s.id asc
                        """,
                new BeanPropertyRowMapper<>(CompetitionRegistrationVO.Stage.class),
                competitionId
        );
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
        int inserted = jdbcTemplate.update(
                """
                        insert into competition_stage (
                            competition_id, stage_code, stage_name, material_submit_start, material_submit_end,
                            review_start, review_end, status, sort, promotion_rule_type, promotion_rule_value, promotion_tie_policy,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
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
                userUuid,
                userId,
                userUuid
        );
        requireRegistrationWrite(inserted, "Competition stage changed, please retry");
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
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
        int updated = jdbcTemplate.update(
                """
                        update competition_stage
                        set stage_name = ?, material_submit_start = ?, material_submit_end = ?, review_start = ?, review_end = ?,
                            status = ?, sort = ?, promotion_rule_type = ?, promotion_rule_value = ?, promotion_tie_policy = ?,
                            updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and competition_id = ? and stage_code = ? and deleted = 0
                        """,
                trimRequired(request.getStageName(), "Stage name is required"),
                request.getMaterialSubmitStart(), request.getMaterialSubmitEnd(), request.getReviewStart(), request.getReviewEnd(),
                normalizeEnum(request.getStatus(), existing.getStatus(), STAGE_STATUSES, "Invalid stage status"),
                request.getSort() == null ? existing.getSort() : request.getSort(),
                trimToNull(request.getPromotionRuleType()), request.getPromotionRuleValue(), trimToNull(request.getPromotionTiePolicy()),
                userId, userUuid, LocalDateTime.now(), stageId, existing.getCompetitionId(), existing.getStageCode()
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
        return jdbcTemplate.query(
                """
                        select r.id as registrationId, r.registration_no as registrationNo, r.competition_id as competitionId,
                               ? as stageId,
                               coalesce(json_unquote(json_extract(r.team_snapshot_json, '$.teamName')), concat('Team #', r.team_id)) as teamName,
                               coalesce(json_unquote(json_extract(r.project_snapshot_json, '$.title')),
                                        p.title, concat('Project #', r.project_id)) as projectTitle,
                               rr.score, coalesce(rr.decision, 'PENDING') as decision, rr.review_comment as reviewComment,
                               rr.published_at as publishedAt, ms.submitted_at as submittedAt
                        from competition_registration r
                        left join aiadc_project p on p.id = r.project_id and p.deleted = 0
                        left join competition_stage_review_result rr
                          on rr.registration_id = r.id and rr.stage_id = ? and rr.deleted = 0
                        left join registration_material_submission ms
                          on ms.registration_id = r.id and ms.stage_id = ? and ms.deleted = 0
                        where r.competition_id = ? and r.status in ('PAID', 'CONFIRMED') and r.deleted = 0
                        order by case when rr.score is null then 1 else 0 end, rr.score desc, r.created_at asc, r.id asc
                        """,
                new BeanPropertyRowMapper<>(CompetitionRegistrationVO.StageReviewCandidate.class),
                stageId, stageId, stageId, stage.getCompetitionId()
        );
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
        String decision = normalizeEnum(request.getDecision(), null, REVIEW_DECISIONS, "Invalid review decision");
        if (request.getScore() != null && (request.getScore().compareTo(BigDecimal.ZERO) < 0 || request.getScore().compareTo(new BigDecimal("100")) > 0)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Review score must be between 0 and 100");
        }
        LocalDateTime publishedAt = "PENDING".equals(decision) ? null : LocalDateTime.now();
        jdbcTemplate.update(
                """
                        insert into competition_stage_review_result (
                            competition_id, stage_id, registration_id, score, decision, review_comment, published_at,
                            decided_by, decided_by_uuid, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        on duplicate key update score = values(score), decision = values(decision), review_comment = values(review_comment),
                            published_at = values(published_at), decided_by = values(decided_by), decided_by_uuid = values(decided_by_uuid),
                            updated_by = values(updated_by), updated_by_uuid = values(updated_by_uuid), updated_at = current_timestamp
                        """,
                stage.getCompetitionId(), stageId, registrationId, request.getScore(), decision, trimToNull(request.getComment()), publishedAt,
                userId, userUuid, userId, userUuid, userId, userUuid
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
            jdbcTemplate.update(
                    """
                            insert into competition_stage_review_result (
                                competition_id, stage_id, registration_id, score, decision, review_comment, published_at,
                                decided_by, decided_by_uuid, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                            on duplicate key update decision = values(decision), published_at = values(published_at),
                                decided_by = values(decided_by), decided_by_uuid = values(decided_by_uuid),
                                updated_by = values(updated_by), updated_by_uuid = values(updated_by_uuid), updated_at = current_timestamp
                            """,
                    stage.getCompetitionId(), stageId, candidate.getRegistrationId(), candidate.getScore(), decision,
                    tieNeedsReview && candidate.getScore().compareTo(boundaryScore) == 0 ? "晋级边界同分，请人工确认" : candidate.getReviewComment(),
                    publishedAt, userId, userUuid, userId, userUuid, userId, userUuid
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
                int inserted = jdbcTemplate.update(
                    """
                            insert into competition_stage_form (
                                competition_id, stage_id, form_name, form_schema_json, version, status,
                                created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                                        stage.getCompetitionId(),
                    stageId,
                    trimRequired(request.getFormName(), "Form name is required"),
                    request.getFormSchemaJson(),
                    version,
                    status,
                    userId,
                    userUuid,
                    userId,
                    userUuid
            );
            requireRegistrationWrite(inserted, "Competition stage form changed, please retry");
        } else {
            int updated = jdbcTemplate.update(
                    """
                            update competition_stage_form
                            set form_name = ?, form_schema_json = ?, version = ?, status = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                            where id = ? and competition_id = ? and stage_id = ? and status = ? and deleted = 0
                            """,
                    trimRequired(request.getFormName(), "Form name is required"),
                    request.getFormSchemaJson(),
                    version,
                    status,
                    userId,
                    userUuid,
                    LocalDateTime.now(),
                    existing.getId(),
                    existing.getCompetitionId(),
                    stageId,
                    existing.getStatus()
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
        Long submissionId = jdbcTemplate.queryForObject(
                """
                        select s.id
                        from registration_material_submission s
                        join competition_registration r
                          on r.id = s.registration_id
                         and r.owner_user_id = ?
                         and r.owner_user_uuid = ?
                         and r.deleted = 0
                        where s.registration_id = ? and s.stage_id = ? and s.deleted = 0
                        limit 1
                        """,
                Long.class,
                userId,
                userUuid,
                registrationId,
                request.getStageId()
        );
        if (submissionId == null) {
            int inserted = jdbcTemplate.update(
                    """
                            insert into registration_material_submission (
                                registration_id, competition_id, stage_id, form_version, submitter_user_id, submitter_user_uuid,
                                status, submitted_at, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                            ) values (?, ?, ?, ?, ?, ?, 'SUBMITTED', ?, ?, ?, ?, ?, 0)
                            """,
                                        registrationId,
                    registration.getCompetitionId(),
                    request.getStageId(),
                    form.getVersion(),
                    userId,
                    userUuid,
                    LocalDateTime.now(),
                    userId,
                    userUuid,
                    userId,
                    userUuid
            );
            requireRegistrationWrite(inserted, "Material submission changed, please retry");
            submissionId = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        } else {
            int updated = jdbcTemplate.update(
                    """
                            update registration_material_submission
                            set status = 'SUBMITTED', submitter_user_id = ?, submitter_user_uuid = ?,
                                submitted_at = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                            where id = ? and registration_id = ? and stage_id = ? and form_version = ? and deleted = 0
                              and exists (
                                  select 1 from competition_registration r
                                  where r.id = registration_material_submission.registration_id
                                    and r.owner_user_id = ?
                                    and r.owner_user_uuid = ?
                                    and r.deleted = 0
                              )
                            """,
                    userId,
                    userUuid,
                    LocalDateTime.now(),
                    userId,
                    userUuid,
                    LocalDateTime.now(),
                    submissionId,
                    registrationId,
                    request.getStageId(),
                    form.getVersion(),
                    userId,
                    userUuid
            );
            if (updated == 0) {
                throw biz(ErrorCode.BIZ_ERROR, "Material submission changed, please retry");
            }
            Integer revisionNo = jdbcTemplate.queryForObject(
                    "select coalesce(max(revision_no), 0) + 1 from registration_material_value_revision where submission_id = ?",
                    Integer.class,
                    submissionId
            );
            jdbcTemplate.update(
                    """
                            insert into registration_material_value_revision (
                                submission_id, revision_no, field_key, field_type, text_value, file_id, json_value,
                                changed_by, changed_by_uuid
                            )
                            select submission_id, ?, field_key, field_type, text_value, file_id, json_value, ?, ?
                            from registration_material_value
                            where submission_id = ? and deleted = 0
                            """,
                    revisionNo == null ? 1 : revisionNo,
                    userId,
                    userUuid,
                    submissionId
            );
            jdbcTemplate.update(
                    """
                            update registration_material_value
                            set deleted = 1
                            where submission_id = ?
                              and deleted = 0
                              and exists (
                                  select 1
                                  from registration_material_submission s
                                  join competition_registration r
                                    on r.id = s.registration_id
                                   and r.owner_user_id = ?
                                   and r.owner_user_uuid = ?
                                   and r.deleted = 0
                                  where s.id = registration_material_value.submission_id
                                    and s.registration_id = ?
                                    and s.stage_id = ?
                                    and s.form_version = ?
                                    and s.deleted = 0
                              )
                            """,
                    submissionId,
                    userId,
                    userUuid,
                    registrationId,
                    request.getStageId(),
                    form.getVersion()
            );
        }
        for (CompetitionRegistrationDTO.MaterialValueRequest value : request.getValues()) {
            jdbcTemplate.update(
                    """
                            insert into registration_material_value (
                                submission_id, field_key, field_type, text_value, file_id, json_value, deleted
                            ) values (?, ?, ?, ?, ?, ?, 0)
                            """,
                                        submissionId,
                    trimRequired(value.getFieldKey(), "Material field key is required"),
                    normalizeFieldType(value.getFieldType()),
                    trimToNull(value.getTextValue()),
                    value.getFileId(),
                    trimToNull(value.getJsonValue())
            );
        }
        return getRegistration(currentUser, registrationId);
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
        enqueuePaymentOrderIfReady(
                registration,
                userId,
                requireUserUuid(currentUser),
                normalizeSimulatedRoleId(currentUser.getSimulatedRoleId()),
                providerCode,
                clientIp,
                notifyUrl,
                returnUrl
        );
        drainPaymentOrderQueue(5);
        CompetitionRegistrationVO.Registration refreshed = getRegistration(currentUser, registrationId);
        if (StringUtils.hasText(refreshed.getPaymentOrderNo())) {
            CompetitionRegistrationVO.PaymentOrder order = findPaymentOrder(refreshed.getPaymentOrderNo());
            if (order != null) {
                return order;
            }
        }
        CompetitionRegistrationVO.PaymentOrder queuedOrder = new CompetitionRegistrationVO.PaymentOrder();
        queuedOrder.setAmountMinor(refreshed.getPayableAmountMinor());
        queuedOrder.setCurrency(refreshed.getCurrency());
        queuedOrder.setStatus("QUEUED");
        return queuedOrder;
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
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                        select cci.item_key as providerCode
                        from aiadc_competition c
                        join competition_config_set ccs
                          on ccs.competition_uuid = c.uuid
                         and ccs.status = 'PUBLISHED'
                         and ccs.deleted = 0
                        join competition_config_item cci
                          on cci.competition_uuid = c.uuid
                         and cci.config_set_id = ccs.id
                         and cci.item_type = 'PAYMENT_SETTINGS'
                         and cci.enabled = 1
                         and cci.deleted = 0
                        where c.id = ? and c.deleted = 0
                          and ccs.id = (
                            select max(latest.id)
                            from competition_config_set latest
                            where latest.competition_uuid = c.uuid
                              and latest.status = 'PUBLISHED'
                              and latest.deleted = 0
                          )
                        order by cci.sort_order asc, cci.id asc
                        """,
                competitionId
        );
        return rows.stream()
                .map(row -> row.get("providerCode"))
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
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
            case "MOBILE" -> "wechat_pay".equals(provider) ? List.of("H5") : "alipay".equals(provider) ? List.of("WAP") : List.of("CHECKOUT");
            default -> "wechat_pay".equals(provider) ? List.of("NATIVE") : "alipay".equals(provider) ? List.of("PC_WEB", "QR_CODE") : List.of("CHECKOUT");
        };
        return preferred.stream().filter(scenes::contains).findFirst().orElse(null);
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
        CompetitionRegistrationVO.PaymentOrder order = findPaymentOrder(registration.getPaymentOrderNo());
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
        for (Map<String, Object> task : claimPaymentOrderTasks(Math.max(1, Math.min(limit, 20)))) {
            Long taskId = toLong(task.get("id"));
            Long registrationId = toLong(task.get("registrationId"));
            String claimToken = String.valueOf(task.get("claimToken"));
            String taskOwnerUserUuid = trimTaskText(task.get("ownerUserUuid"), null);
            Long taskSimulatedRoleId = normalizeSimulatedRoleId(toLong(task.get("simulatedRoleId")));
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
                PaymentOrderDTO order = paymentInternalApi.createOrder(
                        registration.getOwnerUserId(),
                        ownerUserUuid,
                        taskSimulatedRoleId,
                        new PaymentCreateOrderRequestDTO(
                                trimTaskText(task.get("providerCode"), "alipay"),
                                "REG-" + registration.getId(),
                                "Competition registration " + registration.getRegistrationNo(),
                                registration.getPayableAmountMinor(),
                                registration.getCurrency(),
                                trimTaskText(task.get("clientIp"), null),
                                trimTaskText(task.get("notifyUrl"), null),
                                trimTaskText(task.get("returnUrl"), null),
                                Map.of(
                                        "bizType", "competition_registration",
                                        "registrationId", registration.getId(),
                                        "competitionId", registration.getCompetitionId(),
                                        "teamId", registration.getTeamId(),
                                        "projectId", registration.getProjectId()
                                ),
                                "competition-registration-" + registration.getId()
                        )
                );
                assertOrderMatchesRegistration(order, registration);
                int registrationUpdated = jdbcTemplate.update(
                        """
                                update competition_registration
                                set payment_order_no = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                                where id = ?
                                  and registration_no = ?
                                  and owner_user_id = ?
                                  and owner_user_uuid = ?
                                  and payable_amount_minor = ?
                                  and currency = ?
                                  and deleted = 0
                                  and status = 'PENDING_PAYMENT'
                                  and (payment_order_no is null or payment_order_no = '')
                                """,
                        order.orderNo(),
                        registration.getOwnerUserId(),
                        requireRegistrationOwnerUserUuid(registration),
                        LocalDateTime.now(),
                        registration.getId(),
                        registration.getRegistrationNo(),
                        registration.getOwnerUserId(),
                        requireRegistrationOwnerUserUuid(registration),
                        registration.getPayableAmountMinor(),
                        registration.getCurrency()
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
            throw biz(ErrorCode.UNAUTHORIZED, "Payment owner is disabled");
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
        int inserted = jdbcTemplate.update(
                """
                        insert into competition_payment_order_task (
                            registration_id, provider_code, client_ip, notify_url, return_url, owner_user_uuid, simulated_role_id,
                            status, retry_count, next_retry_at, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, ?, ?, ?, 0)
                        on duplicate key update
                            provider_code = case when registration_id = values(registration_id) and owner_user_uuid = values(owner_user_uuid) then values(provider_code) else provider_code end,
                            client_ip = case when registration_id = values(registration_id) and owner_user_uuid = values(owner_user_uuid) then coalesce(values(client_ip), client_ip) else client_ip end,
                            notify_url = case when registration_id = values(registration_id) and owner_user_uuid = values(owner_user_uuid) then coalesce(values(notify_url), notify_url) else notify_url end,
                            return_url = case when registration_id = values(registration_id) and owner_user_uuid = values(owner_user_uuid) then coalesce(values(return_url), return_url) else return_url end,
                            simulated_role_id = case when registration_id = values(registration_id) and owner_user_uuid = values(owner_user_uuid) then values(simulated_role_id) else simulated_role_id end,
                            status = case when registration_id = values(registration_id) and owner_user_uuid = values(owner_user_uuid) and status in ('FAILED', 'DEAD') then 'PENDING' else status end,
                            next_retry_at = case when registration_id = values(registration_id) and owner_user_uuid = values(owner_user_uuid) and status in ('FAILED', 'DEAD') then values(next_retry_at) else next_retry_at end,
                            updated_by = case when registration_id = values(registration_id) and owner_user_uuid = values(owner_user_uuid) then values(updated_by) else updated_by end,
                            updated_by_uuid = case when registration_id = values(registration_id) and owner_user_uuid = values(owner_user_uuid) then values(updated_by_uuid) else updated_by_uuid end,
                            updated_at = case when registration_id = values(registration_id) and owner_user_uuid = values(owner_user_uuid) then current_timestamp else updated_at end
                        """,
                registration.getId(),
                StringUtils.hasText(providerCode) ? providerCode.trim() : "alipay",
                trimToNull(clientIp),
                trimToNull(notifyUrl),
                trimToNull(returnUrl),
                requireRegistrationOwnerUserUuid(registration),
                normalizeSimulatedRoleId(simulatedRoleId),
                LocalDateTime.now(),
                operatorId,
                operatorUuid,
                operatorId,
                operatorUuid
        );
        requireRegistrationWrite(inserted, "Payment order task changed, please retry");
    }

    private List<Map<String, Object>> claimPaymentOrderTasks(int limit) {
        String claimToken = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        update competition_payment_order_task
                        set status = 'RUNNING', claim_token = ?, claim_expires_at = ?, updated_at = ?
                        where id in (
                            select id from (
                                select t.id
                                from competition_payment_order_task t
                                join competition_registration r
                                  on r.id = t.registration_id
                                 and r.deleted = 0
                                 and r.owner_user_uuid = t.owner_user_uuid
                                join sys_user u
                                  on u.id = r.owner_user_id
                                 and u.deleted = 0
                                 and u.status = 'ENABLED'
                                 and u.uuid = r.owner_user_uuid
                                where t.deleted = 0
                                  and t.status in ('PENDING', 'FAILED')
                                  and (t.next_retry_at is null or t.next_retry_at <= ?)
                                  and t.owner_user_uuid is not null
                                  and t.owner_user_uuid <> ''
                                order by t.created_at asc, t.id asc
                                limit ?
                            ) trusted_tasks
                        )
                        """,
                claimToken,
                now.plusMinutes(5),
                now,
                now,
                limit
        );
        return jdbcTemplate.queryForList(
                """
                        select id, registration_id as registrationId, provider_code as providerCode,
                               client_ip as clientIp, notify_url as notifyUrl, return_url as returnUrl,
                               owner_user_uuid as ownerUserUuid, simulated_role_id as simulatedRoleId, claim_token as claimToken
                        from competition_payment_order_task
                        where deleted = 0
                          and status = 'RUNNING'
                          and claim_token = ?
                          and owner_user_uuid is not null
                          and owner_user_uuid <> ''
                        order by created_at asc, id asc
                        """,
                claimToken
        );
    }

    private void markPaymentOrderTaskSucceeded(Long taskId, Long registrationId, String ownerUserUuid, String claimToken, String message) {
        jdbcTemplate.update(
                """
                        update competition_payment_order_task
                        set status = 'SUCCEEDED', process_message = ?, claim_token = null,
                            claim_expires_at = null, updated_at = ?
                        where id = ?
                          and registration_id = ?
                          and owner_user_uuid = ?
                          and deleted = 0
                          and status = 'RUNNING'
                          and claim_token = ?
                        """,
                message,
                LocalDateTime.now(),
                taskId,
                registrationId,
                requireTrustedOwnerUserUuid(ownerUserUuid),
                claimToken
        );
    }

    private void markPaymentOrderTaskFailed(Long taskId, Long registrationId, String ownerUserUuid, String claimToken, String message) {
        Integer retryCount = jdbcTemplate.queryForObject(
                """
                        select retry_count
                        from competition_payment_order_task
                        where id = ?
                          and registration_id = ?
                          and owner_user_uuid = ?
                          and deleted = 0
                          and status = 'RUNNING'
                          and claim_token = ?
                        limit 1
                        """,
                Integer.class,
                taskId,
                registrationId,
                requireTrustedOwnerUserUuid(ownerUserUuid),
                claimToken
        );
        int nextRetryCount = retryCount == null ? 1 : retryCount + 1;
        String nextStatus = nextRetryCount >= PAYMENT_ORDER_TASK_MAX_RETRY ? "DEAD" : "FAILED";
        jdbcTemplate.update(
                """
                        update competition_payment_order_task
                        set status = ?, retry_count = ?, next_retry_at = ?, process_message = ?,
                            claim_token = null, claim_expires_at = null, updated_at = ?
                        where id = ?
                          and registration_id = ?
                          and owner_user_uuid = ?
                          and deleted = 0
                          and status = 'RUNNING'
                          and claim_token = ?
                        """,
                nextStatus,
                nextRetryCount,
                LocalDateTime.now().plusSeconds(Math.min(300L, 5L * (1L << Math.min(nextRetryCount, 6)))),
                StringUtils.hasText(message) && message.length() > 512 ? message.substring(0, 512) : message,
                LocalDateTime.now(),
                taskId,
                registrationId,
                requireTrustedOwnerUserUuid(ownerUserUuid),
                claimToken
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
        jdbcTemplate.update(
                """
                        update competition_registration
                        set status = 'CONFIRMED', participant_no = ?, payment_order_no = coalesce(?, payment_order_no),
                            updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and deleted = 0
                          and owner_user_id = ? and owner_user_uuid = ?
                          and participant_no is null
                        """,
                participantNo,
                orderNo,
                ownerUserId,
                ownerUserUuid,
                LocalDateTime.now(),
                registrationId,
                ownerUserId,
                ownerUserUuid
        );
    }

    private void requireSubmittedPreliminaryMaterials(CompetitionRegistrationVO.Registration registration) {
                Long preliminaryStageId = jdbcTemplate.queryForObject(
                """
                        select stage.id
                        from competition_stage stage
                        join competition_stage_form form
                          on form.stage_id = stage.id and form.competition_id = stage.competition_id
                         and form.status = 'ENABLED' and form.deleted = 0
                        where stage.competition_id = ? and stage.stage_code = 'PRELIMINARY'
                          and stage.status = 'ENABLED' and stage.deleted = 0
                        order by stage.id asc limit 1
                        """,
                Long.class,
                                registration.getCompetitionId()
        );
        if (preliminaryStageId == null) {
            return;
        }
        Long count = jdbcTemplate.queryForObject(
                """
                        select count(1)
                        from registration_material_submission
                        where registration_id = ? and stage_id = ? and status in ('SUBMITTED', 'LOCKED') and deleted = 0
                        """,
                Long.class,
                                registration.getId(),
                preliminaryStageId
        );
        if (count == null || count == 0) {
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
        if (members != null && members.size() > MAX_INLINE_MEMBERS) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Too many registration members");
        }
        for (CompetitionRegistrationDTO.MemberSnapshotRequest member : members == null ? List.<CompetitionRegistrationDTO.MemberSnapshotRequest>of() : members) {
            Map<String, Object> row = new LinkedHashMap<>();
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
        if (api != null) {
            TeamSummaryDTO team = api.getTeam(requesterUserId, requesterUserUuid, teamId);
            if (team == null) {
                throw biz(ErrorCode.NOT_FOUND, "Team not found");
            }
            List<TeamMemberDTO> members = api.listActiveMembers(requesterUserId, requesterUserUuid, teamId);
            return new TeamSnapshot(teamId, team, normalizeSystemTeamMembers(members));
        }
        Map<String, Object> team = singleRow(
                """
                        select id, team_code as teamCode, team_name as teamName,
                               team_type as teamType, visibility,
                               owner_user_id as ownerUserId, owner_user_uuid as ownerUserUuid, status
                        from team
                        where id = ? and deleted = 0
                          and exists (
                              select 1 from team_member tm
                              where tm.team_id = team.id
                                and tm.user_id = ?
                                and tm.user_uuid = ?
                                and tm.status = 'ACTIVE'
                                and tm.deleted = 0
                          )
                        limit 1
                        """,
                                teamId,
                                requesterUserId,
                                requesterUserUuid
        );
        if (team == null) {
            throw biz(ErrorCode.NOT_FOUND, "Team not found");
        }
        List<Map<String, Object>> members = jdbcTemplate.queryForList(
                """
                        select id, team_id as teamId, user_id as userId, user_uuid as userUuid,
                               role as systemRole, status,
                               extra_values_json as extraValuesJson, joined_at as joinedAt
                        from team_member
                        where team_id = ? and status = 'ACTIVE' and deleted = 0
                        order by id asc
                        """,
                                teamId
        );
        return new TeamSnapshot(teamId, team, members);
    }

    private List<Map<String, Object>> normalizeSystemTeamMembers(List<TeamMemberDTO> members) {
        List<Map<String, Object>> snapshots = new ArrayList<>();
        for (TeamMemberDTO member : members == null ? List.<TeamMemberDTO>of() : members) {
            Map<String, Object> snapshot = new LinkedHashMap<>();
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
        Map<String, Object> project = singleRow(
                """
                        select id, code, locale, title, category, description,
                               image_url as imageUrl, owner_name as ownerName, status, tags
                        from aiadc_project
                        where id = ? and deleted = 0
                        limit 1
                        """,
                                projectId
        );
        if (project == null) {
            throw biz(ErrorCode.NOT_FOUND, "Project not found");
        }
        if (projectSnapshotRequest != null && projectSnapshotRequest.getExtraValues() != null && !projectSnapshotRequest.getExtraValues().isEmpty()) {
            requireJsonSize(projectSnapshotRequest.getExtraValues(), "Project extra values are too large");
            project = new LinkedHashMap<>(project);
            project.put("extraValues", projectSnapshotRequest.getExtraValues());
        }
        return new ProjectSnapshot(projectId, project);
    }

    private CompetitionRow requireCompetition(Long competitionId) {
        requirePositiveId(competitionId, "Competition id is required");
        CompetitionRow row = jdbcTemplate.queryForObject(
                """
                        select id, code, fee_mode as feeMode, entry_fee_minor as entryFeeMinor, currency,
                               registration_start as registrationStart, registration_end as registrationEnd
                        from aiadc_competition
                        where id = ? and deleted = 0
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(CompetitionRow.class),
                                competitionId
        );
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
        if (members != null && members.size() > MAX_INLINE_MEMBERS) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Too many registration members");
        }
        for (CompetitionRegistrationDTO.MemberSnapshotRequest member : members == null ? List.<CompetitionRegistrationDTO.MemberSnapshotRequest>of() : members) {
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
            Map<String, Object> memberStandards = new LinkedHashMap<>();
            memberStandards.put("memberName", member.getMemberName());
            memberStandards.put("employeeNo", member.getEmployeeNo());
            memberStandards.put("departmentName", member.getDepartmentName());
            memberStandards.put("role", member.getRole());
            memberStandards.put("remark", member.getRemark());
            validateScopeValues("MEMBER_FIELD", member.getExtraValues(), definitions, memberStandards);
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
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                        select item.item_type as itemType, item.item_key as itemKey, item.title,
                               item.content_json as contentJson, item.required_flag as requiredFlag,
                               item.sort_order as sortOrder
                        from aiadc_competition competition
                        join competition_config_set config
                          on config.competition_uuid = competition.uuid and config.deleted = 0
                         and config.status in ('DRAFT', 'PUBLISHED')
                        join competition_config_item item
                          on item.config_set_id = config.id and item.competition_uuid = competition.uuid
                         and item.enabled = 1 and item.deleted = 0
                         and item.item_type in ('REGISTRATION_FIELD', 'TEAM_FIELD', 'MEMBER_FIELD', 'PROJECT_FIELD')
                        where competition.id = ? and competition.deleted = 0
                          and config.id = (
                              select max(current_config.id) from competition_config_set current_config
                              where current_config.competition_uuid = competition.uuid
                                and current_config.status in ('DRAFT', 'PUBLISHED') and current_config.deleted = 0
                          )
                        order by item.sort_order asc, item.id asc
                        """,
                competitionId
        );
        List<CollectedFieldDefinition> definitions = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String contentJson = row.get("contentJson") == null ? null : String.valueOf(row.get("contentJson"));
            JsonNode metadata;
            try {
                metadata = StringUtils.hasText(contentJson) ? objectMapper.readTree(contentJson) : objectMapper.createObjectNode();
            } catch (Exception exception) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Invalid competition field configuration");
            }
            String fieldType = metadata.path("fieldType").asText("TEXT").toUpperCase(Locale.ROOT);
            if (!COLLECTION_FIELD_TYPES.contains(fieldType)) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Unsupported competition field type");
            }
            definitions.add(new CollectedFieldDefinition(
                    String.valueOf(row.get("itemType")),
                    String.valueOf(row.get("itemKey")),
                    row.get("title") == null ? String.valueOf(row.get("itemKey")) : String.valueOf(row.get("title")),
                    fieldType,
                    Boolean.TRUE.equals(row.get("requiredFlag")) || Integer.valueOf(1).equals(row.get("requiredFlag")),
                    metadata.path("validationRule").asText("NONE").toUpperCase(Locale.ROOT),
                    metadata.path("options").asText(""),
                    metadata.path("groupLabel").asText("")
            ));
        }
        return definitions;
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
        if (Set.of("TEXT", "TEXTAREA", "DATE", "SELECT", "MOBILE", "EMAIL", "IMAGE", "ROLE").contains(field.fieldType())
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
        String text = String.valueOf(value).trim();
        if ("DATE".equals(field.fieldType()) && !isValidCollectedDate(field, text)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Invalid date: " + field.title());
        }
        if ("IMAGE".equals(field.fieldType()) && (!StringUtils.hasText(text) || text.length() > 2048)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Invalid image value: " + field.title());
        }
        if (("MOBILE".equals(field.fieldType()) || "CHINA_MOBILE".equals(field.validationRule()))
                && !MOBILE_PATTERN.matcher(text).matches()) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Invalid mobile number: " + field.title());
        }
        if (("EMAIL".equals(field.fieldType()) || "EMAIL".equals(field.validationRule()))
                && !EMAIL_PATTERN.matcher(text).matches()) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Invalid email address: " + field.title());
        }
    }

    private boolean isYearOnlyMemberDateField(CollectedFieldDefinition field) {
        return "MEMBER_FIELD".equals(field.scope())
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
        if ("MEMBER_FIELD".equals(scope) && "role".equals(itemKey)) {
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
            return item;
        }).toList();
        jdbcTemplate.update(
                "update competition_registration set collection_schema_snapshot_json = ?, updated_at = ? where id = ? and deleted = 0",
                serialize(snapshot), LocalDateTime.now(), registrationId
        );
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
        CompetitionRow competition = requireCompetition(competitionId);
        Long next = jdbcTemplate.queryForObject(
                "select count(1) + 1 from competition_registration where competition_id = ? and participant_no is not null and deleted = 0",
                Long.class,
                                competitionId
        );
        return competition.code().toUpperCase(Locale.ROOT) + "-" + String.format("%04d", next == null ? 1L : next);
    }

    private String generateRegistrationNo() {
        String random = Long.toString(ThreadLocalRandom.current().nextLong(36L * 36L * 36L), 36).toUpperCase(Locale.ROOT);
        return "REG-" + LocalDateTime.now().format(NO_TIME_FORMATTER) + "-" + random;
    }

    private CompetitionRegistrationVO.Registration findRegistration(Long id) {
        requirePositiveId(id, "Registration id is required");
        return jdbcTemplate.queryForObject(
                registrationSelect() + " from competition_registration where id = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(CompetitionRegistrationVO.Registration.class),
                                id
        );
    }

    private CompetitionRegistrationVO.Registration findRegistrationByPaymentOrder(String orderNo) {
        return jdbcTemplate.queryForObject(
                registrationSelect() + " from competition_registration where payment_order_no = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(CompetitionRegistrationVO.Registration.class),
                                orderNo
        );
    }

    private CompetitionRegistrationVO.Stage findStage(Long id) {
        requirePositiveId(id, "Stage id is required");
        return jdbcTemplate.queryForObject(
                stageSelect() + " from competition_stage where id = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(CompetitionRegistrationVO.Stage.class),
                                id
        );
    }

    private CompetitionRegistrationVO.StageForm findStageForm(Long stageId) {
        requirePositiveId(stageId, "Stage id is required");
        return jdbcTemplate.queryForObject(
                """
                        select id, competition_id as competitionId, stage_id as stageId,
                               form_name as formName, form_schema_json as formSchemaJson, version, status
                        from competition_stage_form
                        where stage_id = ? and status = 'ENABLED' and deleted = 0
                        order by version desc, id desc
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(CompetitionRegistrationVO.StageForm.class),
                                stageId
        );
    }

    private CompetitionRegistrationVO.StageForm findReadableStageFormForRegistration(Long stageId, CurrentUser currentUser) {
        requireRegistrationStageReadPermission(currentUser);
        requirePositiveId(stageId, "Stage id is required");
        return jdbcTemplate.queryForObject(
                """
                        select f.id, f.competition_id as competitionId, f.stage_id as stageId,
                               f.form_name as formName, f.form_schema_json as formSchemaJson, f.version, f.status
                        from competition_stage_form f
                        join competition_stage s on s.id = f.stage_id and s.deleted = 0 and s.status = 'ENABLED'
                        join aiadc_competition c on c.id = s.competition_id and c.deleted = 0 and c.status = 'published'
                        where f.stage_id = ? and f.status = 'ENABLED' and f.deleted = 0
                        order by f.version desc, f.id desc
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(CompetitionRegistrationVO.StageForm.class),
                stageId
        );
    }

    private CompetitionRegistrationVO.PaymentOrder findPaymentOrder(String orderNo) {
        return jdbcTemplate.queryForObject(
                """
                        select order_no as orderNo, amount_minor as amountMinor, currency, status, payment_url as paymentUrl
                        from payment_order
                        where order_no = ? and deleted = 0
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(CompetitionRegistrationVO.PaymentOrder.class),
                                orderNo
        );
    }

    private Map<String, Object> singleRow(String sql, Object... params) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void validateTeamMemberCount(Long competitionId, int memberCount) {
        int minMembers = DEFAULT_TEAM_MIN_MEMBERS;
        int maxMembers = DEFAULT_TEAM_MAX_MEMBERS;
        List<Map<String, Object>> settingsRows = jdbcTemplate.queryForList(
                """
                        select cci.content_json as contentJson
                        from aiadc_competition c
                        join competition_config_set ccs
                          on ccs.competition_uuid = c.uuid
                         and ccs.status in ('DRAFT', 'PUBLISHED')
                         and ccs.deleted = 0
                        join competition_config_item cci
                          on cci.competition_uuid = c.uuid
                         and cci.config_set_id = ccs.id
                         and cci.item_type = 'TEAM_SETTINGS'
                         and cci.item_key = 'team-size-limits'
                         and cci.enabled = 1
                         and cci.deleted = 0
                        where c.id = ? and c.deleted = 0
                        order by ccs.id desc
                        limit 1
                        """,
                competitionId
        );
        if (!settingsRows.isEmpty()) {
            Object contentJson = settingsRows.get(0).get("contentJson");
            try {
                JsonNode metadata = objectMapper.readTree(contentJson == null ? "{}" : String.valueOf(contentJson));
                minMembers = normalizeConfiguredMemberLimit(metadata.path("teamMinMembers").asInt(DEFAULT_TEAM_MIN_MEMBERS), DEFAULT_TEAM_MIN_MEMBERS);
                maxMembers = normalizeConfiguredMemberLimit(metadata.path("teamMaxMembers").asInt(DEFAULT_TEAM_MAX_MEMBERS), DEFAULT_TEAM_MAX_MEMBERS);
            } catch (Exception ignored) {
                minMembers = DEFAULT_TEAM_MIN_MEMBERS;
                maxMembers = DEFAULT_TEAM_MAX_MEMBERS;
            }
        }
        if (minMembers > maxMembers) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Invalid team member limits");
        }
        if (memberCount < minMembers) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Team requires at least " + minMembers + " members");
        }
        if (memberCount > maxMembers) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Team allows at most " + maxMembers + " members");
        }
    }

    private int normalizeConfiguredMemberLimit(int value, int fallback) {
        return value >= 1 && value <= MAX_INLINE_MEMBERS ? value : fallback;
    }

    private Map<String, Object> toMutableMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, item) -> normalized.put(String.valueOf(key), item));
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
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            return;
        }
        if (sessionAuthenticationService != null) {
            CurrentUser refreshed = requireTrustedAuthenticatedCurrentUser(
                    sessionAuthenticationService.authenticateSessionTicket(
                            currentUser.getSessionId(),
                            currentUser.getUserId(),
                            currentUser.getUserUuid(),
                            currentUser.getSimulatedRoleId(),
                            currentUser.getSessionVersion(),
                            currentUser.getPermissionsVersion()
                    ),
                    "Login required"
            );
            copyTrustedCurrentUser(currentUser, refreshed);
            return;
        }
        if (permissionSnapshotService == null) {
            if (enforceTrustedUserResolution) {
                throw biz(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
            }
            return;
        }
        Long userId = currentUser.getUserId();
        String normalizedUserUuid = StringUtils.hasText(currentUser.getUserUuid()) ? currentUser.getUserUuid().trim() : null;
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            throw biz(ErrorCode.UNAUTHORIZED, "Login required");
        }
        SystemInternalApi systemInternalApi = systemInternalApiProvider == null ? null : systemInternalApiProvider.getIfAvailable();
        if (systemInternalApi != null) {
            SystemUserSnapshotDTO userSnapshot = systemInternalApi.findUserIdentityById(userId);
            String currentUserUuid = userSnapshot == null || !StringUtils.hasText(userSnapshot.userUuid())
                    ? null
                    : userSnapshot.userUuid().trim();
            if (userSnapshot == null
                    || userSnapshot.userId() == null
                    || !userId.equals(userSnapshot.userId())
                    || !StringUtils.hasText(currentUserUuid)
                    || !normalizedUserUuid.equals(currentUserUuid)) {
                throw biz(ErrorCode.UNAUTHORIZED, "Login required");
            }
            if (!"ENABLED".equalsIgnoreCase(userSnapshot.status())) {
                throw biz(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
            }
            if (!StringUtils.hasText(userSnapshot.username())) {
                throw biz(ErrorCode.UNAUTHORIZED, "Trusted user username is unavailable");
            }
            userId = userSnapshot.userId();
            normalizedUserUuid = currentUserUuid;
            currentUser.setUserId(userId);
            currentUser.setUserUuid(normalizedUserUuid);
            currentUser.setUsername(userSnapshot.username().trim());
        }
        if (!permissionSnapshotService.isTrustedActiveUser(userId, normalizedUserUuid)) {
            throw biz(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
        }
        Long simulatedRoleId = normalizeSimulatedRoleId(currentUser.getSimulatedRoleId());
        PermissionSnapshotService.PermissionSnapshot snapshot = simulatedRoleId != null
                ? permissionSnapshotService.loadGrantedRoleSnapshot(
                userId,
                normalizedUserUuid,
                simulatedRoleId
        )
                : permissionSnapshotService.loadSnapshot(userId, normalizedUserUuid);
        if (snapshot == null) {
            if (enforceTrustedUserResolution) {
                throw biz(ErrorCode.UNAUTHORIZED, "Trusted user permission snapshot is unavailable");
            }
            return;
        }
        currentUser.setUserUuid(normalizedUserUuid);
        currentUser.setSimulatedRoleId(simulatedRoleId);
        currentUser.setPermissions(snapshot.getPermissions() == null ? Set.of() : Set.copyOf(snapshot.getPermissions()));
        currentUser.setRoleIds(snapshot.getRoleIds() == null ? Set.of() : Set.copyOf(snapshot.getRoleIds()));
        currentUser.setPrimaryDeptId(snapshot.getPrimaryDeptId());
        currentUser.setDeptIds(snapshot.getDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDeptIds()));
        currentUser.setDescendantDeptIds(snapshot.getDescendantDeptIds() == null ? Set.of() : Set.copyOf(snapshot.getDescendantDeptIds()));
        currentUser.setDataScopes(snapshot.getDataScopes() == null ? List.of() : List.copyOf(snapshot.getDataScopes()));
        currentUser.setPermissionsVersion(snapshot.getVersion());
        currentUser.setDefaultHomePath(snapshot.getDefaultHomePath());
    }

    private CurrentUser requireTrustedAuthenticatedCurrentUser(
            SessionAuthenticationService.AuthenticatedAccess authenticatedAccess,
            String message
    ) {
        if (authenticatedAccess == null || !AuthenticationTrustSupport.isTrustedCurrentUser(authenticatedAccess.currentUser())) {
            throw biz(ErrorCode.UNAUTHORIZED, message);
        }
        return authenticatedAccess.currentUser();
    }

    private void copyTrustedCurrentUser(CurrentUser target, CurrentUser source) {
        target.setUserId(source.getUserId());
        target.setUserUuid(source.getUserUuid());
        target.setUsername(source.getUsername());
        target.setSessionId(source.getSessionId());
        target.setSessionVersion(source.getSessionVersion());
        target.setAuthenticated(source.isAuthenticated());
        target.setPermissions(source.getPermissions());
        target.setRoleIds(source.getRoleIds());
        target.setPrimaryDeptId(source.getPrimaryDeptId());
        target.setDeptIds(source.getDeptIds());
        target.setDescendantDeptIds(source.getDescendantDeptIds());
        target.setDataScopes(source.getDataScopes());
        target.setPermissionsVersion(source.getPermissionsVersion());
        target.setRequiresPasswordChange(source.getRequiresPasswordChange());
        target.setDefaultHomePath(source.getDefaultHomePath());
        target.setSimulatedRoleId(normalizeSimulatedRoleId(source.getSimulatedRoleId()));
        target.setLoginType(source.getLoginType());
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

    private void requireRegistrationWindowOpen(CompetitionRow competition) {
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
            throw biz(ErrorCode.BIZ_ERROR, "当前不在材料修改时间内");
        }
        if ("FINAL".equals(stage.getStageCode())) {
            Long advanced = jdbcTemplate.queryForObject(
                    """
                            select count(1)
                            from competition_stage_review_result rr
                            join competition_stage source_stage on source_stage.id = rr.stage_id and source_stage.deleted = 0
                            where rr.competition_id = ? and rr.registration_id = ? and rr.decision = 'ADVANCED'
                              and rr.published_at is not null and rr.deleted = 0
                              and source_stage.stage_code = 'PRELIMINARY'
                            """,
                    Long.class,
                    registration.getCompetitionId(), registration.getId()
            );
            if (advanced == null || advanced == 0) {
                throw biz(ErrorCode.FORBIDDEN, "仅已公布晋级的团队可以修改决赛材料");
            }
        }
    }

    private void hydrateMaterialAccess(CompetitionRegistrationVO.Stage stage, CurrentUser currentUser) {
        LocalDateTime now = LocalDateTime.now();
        if (stage.getMaterialSubmitStart() == null || stage.getMaterialSubmitEnd() == null) {
            stage.setMaterialEditable(false);
            stage.setMaterialAccessReason("材料修改时间未配置");
            return;
        }
        if (now.isBefore(stage.getMaterialSubmitStart())) {
            stage.setMaterialEditable(false);
            stage.setMaterialAccessReason("材料修改尚未开始");
            return;
        }
        if (now.isAfter(stage.getMaterialSubmitEnd())) {
            stage.setMaterialEditable(false);
            stage.setMaterialAccessReason("材料修改已截止");
            return;
        }
        if ("FINAL".equals(stage.getStageCode())) {
            Long advanced = jdbcTemplate.queryForObject(
                    """
                            select count(1)
                            from competition_stage_review_result rr
                            join competition_registration r on r.id = rr.registration_id and r.deleted = 0
                            join competition_stage source_stage on source_stage.id = rr.stage_id and source_stage.deleted = 0
                            where rr.competition_id = ? and r.owner_user_id = ? and r.owner_user_uuid = ?
                              and rr.decision = 'ADVANCED' and rr.published_at is not null and rr.deleted = 0
                              and source_stage.stage_code = 'PRELIMINARY'
                            """,
                    Long.class,
                    stage.getCompetitionId(), requireUserId(currentUser), requireUserUuid(currentUser)
            );
            if (advanced == null || advanced == 0) {
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

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        return Long.parseLong(String.valueOf(value));
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

    private String registrationSelect() {
        return """
                select id, registration_no as registrationNo, competition_id as competitionId,
                       team_id as teamId, project_id as projectId, owner_user_id as ownerUserId,
                       owner_user_uuid as ownerUserUuid, status,
                       fee_mode as feeMode, entry_fee_minor as entryFeeMinor, member_count as memberCount,
                       payable_amount_minor as payableAmountMinor, currency, payment_order_no as paymentOrderNo,
                       participant_no as participantNo, registration_snapshot_json as registrationSnapshotJson,
                       team_snapshot_json as teamSnapshotJson,
                       project_snapshot_json as projectSnapshotJson, member_snapshot_json as memberSnapshotJson,
                       collection_schema_snapshot_json as collectionSchemaSnapshotJson,
                       created_at as createdAt, updated_at as updatedAt
                """;
    }

    private String registrationListSelect() {
        return """
                select id, registration_no as registrationNo, competition_id as competitionId,
                       team_id as teamId, project_id as projectId, owner_user_id as ownerUserId,
                       owner_user_uuid as ownerUserUuid, status,
                       fee_mode as feeMode, entry_fee_minor as entryFeeMinor, member_count as memberCount,
                       payable_amount_minor as payableAmountMinor, currency, payment_order_no as paymentOrderNo,
                       participant_no as participantNo,
                       case when json_valid(team_snapshot_json)
                           then json_unquote(json_extract(team_snapshot_json, '$.teamName')) end as teamName,
                       case when json_valid(project_snapshot_json)
                           then json_unquote(json_extract(project_snapshot_json, '$.title')) end as projectTitle,
                       created_at as createdAt, updated_at as updatedAt
                """;
    }

    private String stageSelect() {
        return """
                select id, competition_id as competitionId, stage_code as stageCode,
                       stage_name as stageName, material_submit_start as materialSubmitStart,
                       material_submit_end as materialSubmitEnd, review_start as reviewStart, review_end as reviewEnd,
                       status, sort, promotion_rule_type as promotionRuleType,
                       promotion_rule_value as promotionRuleValue, promotion_tie_policy as promotionTiePolicy
                """;
    }

    private String paymentRecordSelect() {
        return """
                select cr.id as registrationId, cr.registration_no as registrationNo,
                       cr.competition_id as competitionId, c.code as competitionCode, c.title as competitionTitle,
                       cr.team_id as teamId,
                       coalesce(json_unquote(json_extract(cr.team_snapshot_json, '$.teamName')),
                                t.team_name, concat('Team #', cr.team_id)) as teamName,
                       cr.project_id as projectId,
                       coalesce(json_unquote(json_extract(cr.project_snapshot_json, '$.title')),
                                p.title, concat('Project #', cr.project_id)) as projectTitle,
                       cr.owner_user_id as ownerUserId, cr.status as registrationStatus,
                       cr.participant_no as participantNo, cr.member_count as memberCount,
                       cr.payable_amount_minor as payableAmountMinor,
                       coalesce(po.order_no, cr.payment_order_no) as orderNo,
                       po.provider_code as providerCode, po.provider_order_no as providerOrderNo,
                       po.subject as subject, coalesce(po.amount_minor, cr.payable_amount_minor) as amountMinor,
                       coalesce(po.currency, cr.currency) as currency,
                       coalesce(po.status, cr.status) as paymentStatus,
                       po.payment_url as paymentUrl, po.failure_code as failureCode, po.failure_message as failureMessage,
                       po.created_at as orderCreatedAt, po.paid_at as paidAt,
                       cr.created_at as registrationCreatedAt,
                       case when po.updated_at is null or po.updated_at < cr.updated_at then cr.updated_at else po.updated_at end as updatedAt
                """;
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
            String groupLabel
    ) {
    }

    public static class CompetitionRow {
        private Long id;
        private String code;
        private String feeMode;
        private Long entryFeeMinor;
        private String currency;
        private String registrationStart;
        private String registrationEnd;

        Long id() { return id; }
        String code() { return code == null ? "" : code; }
        String feeMode() { return feeMode == null ? "TEAM" : feeMode; }
        Long entryFeeMinor() { return entryFeeMinor == null ? 0L : entryFeeMinor; }
        String currency() { return currency == null ? "CNY" : currency; }
        String registrationStart() { return registrationStart; }
        String registrationEnd() { return registrationEnd; }
        public void setId(Long id) { this.id = id; }
        public void setCode(String code) { this.code = code; }
        public void setFeeMode(String feeMode) { this.feeMode = feeMode; }
        public void setEntryFeeMinor(Long entryFeeMinor) { this.entryFeeMinor = entryFeeMinor; }
        public void setCurrency(String currency) { this.currency = currency; }
        public void setRegistrationStart(String registrationStart) { this.registrationStart = registrationStart; }
        public void setRegistrationEnd(String registrationEnd) { this.registrationEnd = registrationEnd; }
    }
}

package com.lumira.saas.modules.competition.infrastructure;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.competition.infrastructure.persistence.BeanPropertyRowMapper;
import com.lumira.saas.modules.competition.infrastructure.persistence.CompetitionSqlOperations;
import com.lumira.saas.modules.competition.repository.RegistrationPersistencePort;
import com.lumira.saas.modules.competition.repository.RegistrationQueryRepository;
import com.lumira.saas.modules.competition.repository.RegistrationWriteRepository;
import com.lumira.saas.modules.competition.vo.CompetitionRegistrationVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * JDBC implementation of the registration context's query and write ports.
 *
 * <p>The adapter intentionally owns the legacy shared-schema joins. Application services only
 * express business decisions through the ports, which keeps a future context extraction from
 * leaking MyBatis/SQL back into the synchronous control plane.</p>
 */
@Repository
public class JdbcRegistrationPersistenceAdapter implements RegistrationQueryRepository, RegistrationWriteRepository {

    private final CompetitionSqlOperations database;

    public JdbcRegistrationPersistenceAdapter(CompetitionSqlOperations database) {
        this.database = database;
    }

    @Override
    public Long createRegistration(RegistrationPersistencePort.CreateRegistrationCommand command) {
        int inserted = database.update(
                """
                        insert into competition_registration (
                            registration_no, competition_id, team_id, project_id, owner_user_id, owner_user_uuid,
                            status, fee_mode, entry_fee_minor, member_count, payable_amount_minor, currency,
                            registration_snapshot_json, team_snapshot_json, project_snapshot_json, member_snapshot_json,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                command.registrationNo(),
                command.competitionId(),
                command.teamId(),
                command.projectId(),
                command.ownerUserId(),
                command.ownerUserUuid(),
                "PENDING_PAYMENT",
                command.feeMode(),
                command.entryFeeMinor(),
                command.memberCount(),
                command.payableAmountMinor(),
                command.currency(),
                command.registrationSnapshotJson(),
                command.teamSnapshotJson(),
                command.projectSnapshotJson(),
                command.memberSnapshotJson(),
                command.ownerUserId(),
                command.ownerUserUuid(),
                command.ownerUserId(),
                command.ownerUserUuid()
        );
        if (inserted <= 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Registration changed, please retry");
        }
        Long registrationId = database.queryForObject("select last_insert_id()", Long.class);
        database.update(
                "update competition_registration set collection_schema_snapshot_json = ?, updated_at = ? where id = ? and deleted = 0",
                command.collectionSchemaSnapshotJson(), LocalDateTime.now(), registrationId
        );
        return registrationId;
    }

    @Override
    public RegistrationPage findRegistrations(RegistrationSearch search) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" from competition_registration where deleted = 0");
        if (search.ownerUserId() != null) {
            where.append(" and owner_user_id = ? and owner_user_uuid = ?");
            params.add(search.ownerUserId());
            params.add(search.ownerUserUuid());
        }
        if (search.competitionId() != null) {
            where.append("""
                     and exists (
                         select 1
                           from competition_registration_dataset dataset
                           join competition_registration_dataset_row dataset_row
                             on dataset_row.dataset_id = dataset.id and dataset_row.deleted = 0
                          where dataset.competition_id = ?
                            and dataset.status = 'ENABLED'
                            and dataset.deleted = 0
                            and dataset_row.registration_id = competition_registration.id
                     )
                    """);
            params.add(search.competitionId());
        }
        if (StringUtils.hasText(search.status())) {
            where.append(" and status = ?");
            params.add(search.status());
        }
        if (StringUtils.hasText(search.keyword())) {
            where.append("""
                     and (
                         registration_no like ?
                         or participant_no like ?
                         or json_unquote(json_extract(team_snapshot_json, '$.teamName')) like ?
                         or json_unquote(json_extract(project_snapshot_json, '$.title')) like ?
                     )
                    """);
            String keyword = "%" + search.keyword() + "%";
            for (int index = 0; index < 4; index += 1) {
                params.add(keyword);
            }
        }
        Long total = database.queryForObject("select count(1)" + where, Long.class, params.toArray());
        List<Object> selectParams = new ArrayList<>(params);
        selectParams.add(search.offset());
        selectParams.add(search.limit());
        List<CompetitionRegistrationVO.Registration> records = database.query(
                registrationListSelect(search.includeSnapshots()) + where + " order by created_at desc, id desc limit ?, ?",
                new BeanPropertyRowMapper<>(CompetitionRegistrationVO.Registration.class),
                selectParams.toArray()
        );
        return new RegistrationPage(records, total == null ? 0L : total);
    }

    @Override
    public PaymentRecordPage findPaymentRecords(PaymentRecordSearch search) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("""
                from competition_registration cr
                left join aiadc_competition c on c.id = cr.competition_id and c.deleted = 0
                where cr.deleted = 0
                """);
        if (search.ownerUserId() != null) {
            where.append(" and cr.owner_user_id = ? and cr.owner_user_uuid = ?");
            params.add(search.ownerUserId());
            params.add(search.ownerUserUuid());
        }
        if (search.competitionId() != null) {
            where.append(" and cr.competition_id = ?");
            params.add(search.competitionId());
        }
        if (StringUtils.hasText(search.keyword())) {
            where.append("""
                     and (
                         cr.registration_no like ?
                         or cr.participant_no like ?
                         or cr.payment_order_no like ?
                         or c.code like ?
                         or c.title like ?
                         or json_unquote(json_extract(cr.team_snapshot_json, '$.teamName')) like ?
                         or json_unquote(json_extract(cr.project_snapshot_json, '$.title')) like ?
                     )
                    """);
            String keyword = "%" + search.keyword() + "%";
            for (int index = 0; index < 7; index += 1) {
                params.add(keyword);
            }
        }
        if (StringUtils.hasText(search.registrationStatus())) {
            where.append(" and cr.status = ?");
            params.add(search.registrationStatus());
        }
        if ("NOT_REQUIRED".equalsIgnoreCase(search.paymentStatus())) {
            where.append(" and cr.payable_amount_minor = 0 and (cr.payment_order_no is null or cr.payment_order_no = '')");
        }
        Long total = database.queryForObject("select count(1) " + where, Long.class, params.toArray());
        List<Object> selectParams = new ArrayList<>(params);
        selectParams.add(search.offset());
        selectParams.add(search.limit());
        List<CompetitionRegistrationVO.PaymentRecord> records = database.query(
                paymentRecordSelect() + where + """
                         order by cr.updated_at desc, cr.id desc
                         limit ?, ?
                        """,
                new BeanPropertyRowMapper<>(CompetitionRegistrationVO.PaymentRecord.class),
                selectParams.toArray()
        );
        return new PaymentRecordPage(records, total == null ? 0L : total);
    }

    @Override
    public List<CompetitionRegistrationVO.MaterialSubmission> findMaterialSubmissions(Long registrationId, Long competitionId) {
        List<CompetitionRegistrationVO.MaterialSubmission> submissions = database.query(
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
                competitionId
        );
        for (CompetitionRegistrationVO.MaterialSubmission submission : submissions) {
            submission.setValues(database.query(
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

    @Override
    public Long findMaterialSubmissionIdForOwner(Long registrationId, Long stageId, Long ownerUserId, String ownerUserUuid) {
        return database.queryForObject(
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
                ownerUserId,
                ownerUserUuid,
                registrationId,
                stageId
        );
    }

    @Override
    public boolean existsMaterialFile(Long registrationId, Long competitionId, Long fileId) {
        Long count = database.queryForObject(
                """
                        select count(1)
                          from registration_material_submission submission
                          join registration_material_value material_value
                            on material_value.submission_id = submission.id
                           and material_value.file_id = ?
                           and material_value.deleted = 0
                          join competition_registration_dataset_row dataset_row
                            on dataset_row.registration_id = submission.registration_id
                           and dataset_row.deleted = 0
                          join competition_registration_dataset dataset
                            on dataset.id = dataset_row.dataset_id
                           and dataset.competition_id = submission.competition_id
                           and dataset.status = 'ENABLED'
                           and dataset.deleted = 0
                         where submission.registration_id = ?
                           and submission.competition_id = ?
                           and submission.deleted = 0
                        """,
                Long.class,
                fileId,
                registrationId,
                competitionId
        );
        return count != null && count == 1L;
    }

    @Override
    public List<CompetitionRegistrationVO.Stage> findStages(Long competitionId) {
        return database.query(
                stageSelect() + " from competition_stage where competition_id = ? and deleted = 0 order by sort asc, id asc",
                new BeanPropertyRowMapper<>(CompetitionRegistrationVO.Stage.class),
                competitionId
        );
    }

    @Override
    public List<CompetitionRegistrationVO.Stage> findReadableStages(Long competitionId) {
        return database.query(
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
    }

    @Override
    public List<CompetitionRegistrationVO.StageReviewCandidate> findStageReviewCandidates(Long stageId, Long competitionId) {
        return database.query(
                """
                        select r.id as registrationId, r.registration_no as registrationNo, r.competition_id as competitionId,
                               ? as stageId,
                               coalesce(json_unquote(json_extract(r.team_snapshot_json, '$.teamName')), concat('Team #', r.team_id)) as teamName,
                               coalesce(json_unquote(json_extract(r.project_snapshot_json, '$.title')),
                                        concat('Project #', r.project_id)) as projectTitle,
                               rr.score, coalesce(rr.decision, 'PENDING') as decision, rr.review_comment as reviewComment,
                               rr.published_at as publishedAt, ms.submitted_at as submittedAt
                        from competition_registration r
                        left join competition_stage_review_result rr
                          on rr.registration_id = r.id and rr.stage_id = ? and rr.deleted = 0
                        left join registration_material_submission ms
                          on ms.registration_id = r.id and ms.stage_id = ? and ms.deleted = 0
                        where r.competition_id = ? and r.status in ('PAID', 'CONFIRMED') and r.deleted = 0
                        order by case when rr.score is null then 1 else 0 end, rr.score desc, r.created_at asc, r.id asc
                        """,
                new BeanPropertyRowMapper<>(CompetitionRegistrationVO.StageReviewCandidate.class),
                stageId, stageId, stageId, competitionId
        );
    }

    @Override
    public List<String> findCompetitionPaymentProviders(Long competitionId) {
        return database.queryForList(
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
        ).stream()
                .map(row -> row.get("providerCode"))
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .toList();
    }

    @Override
    public Long findPreliminaryStageId(Long competitionId) {
        return database.queryForObject(
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
                competitionId
        );
    }

    @Override
    public boolean hasSubmittedMaterial(Long registrationId, Long stageId) {
        Long count = database.queryForObject(
                """
                        select count(1)
                        from registration_material_submission
                        where registration_id = ? and stage_id = ? and status in ('SUBMITTED', 'LOCKED') and deleted = 0
                        """,
                Long.class,
                registrationId,
                stageId
        );
        return count != null && count > 0L;
    }

    @Override
    public CompetitionDefinition findCompetition(Long competitionId) {
        CompetitionRow row = database.queryForObject(
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
        return row == null ? null : new CompetitionDefinition(
                row.id,
                row.code == null ? "" : row.code,
                row.feeMode == null ? "TEAM" : row.feeMode,
                row.entryFeeMinor == null ? 0L : row.entryFeeMinor,
                row.currency == null ? "CNY" : row.currency,
                row.registrationStart,
                row.registrationEnd
        );
    }

    @Override
    public List<CollectedFieldConfiguration> findCollectedFieldConfigurations(Long competitionId) {
        return database.queryForList(
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
                         and item.item_type in ('REGISTRATION_FIELD', 'TEAM_FIELD', 'MEMBER_FIELD', 'TEACHER_FIELD', 'PROJECT_FIELD')
                        where competition.id = ? and competition.deleted = 0
                          and config.id = (
                              select max(current_config.id) from competition_config_set current_config
                              where current_config.competition_uuid = competition.uuid
                                and current_config.status in ('DRAFT', 'PUBLISHED') and current_config.deleted = 0
                          )
                        order by item.sort_order asc, item.id asc
                        """,
                competitionId
        ).stream().map(row -> new CollectedFieldConfiguration(
                toText(row.get("itemType")),
                toText(row.get("itemKey")),
                toText(row.get("title")),
                toText(row.get("contentJson")),
                row.get("requiredFlag")
        )).toList();
    }

    @Override
    public String findTeamSizeLimitsConfiguration(Long competitionId) {
        Map<String, Object> row = first(database.queryForList(
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
        ));
        return row == null ? null : toText(row.get("contentJson"));
    }

    @Override
    public long countConfirmedRegistrations(Long competitionId) {
        Long next = database.queryForObject(
                "select count(1) + 1 from competition_registration where competition_id = ? and participant_no is not null and deleted = 0",
                Long.class,
                competitionId
        );
        return next == null ? 1L : next;
    }

    @Override
    public CompetitionRegistrationVO.Registration findRegistration(Long registrationId) {
        return database.queryForObject(
                registrationSelect() + " from competition_registration where id = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(CompetitionRegistrationVO.Registration.class),
                registrationId
        );
    }

    @Override
    public CompetitionRegistrationVO.Registration findRegistrationByPaymentOrder(String paymentOrderNo) {
        return database.queryForObject(
                registrationSelect() + " from competition_registration where payment_order_no = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(CompetitionRegistrationVO.Registration.class),
                paymentOrderNo
        );
    }

    @Override
    public List<PendingPaymentCandidate> findStalePendingPaymentCandidates(LocalDateTime updatedBefore, int limit) {
        return database.query(
                """
                        select cr.id as registration_id, cr.registration_no, cr.competition_id,
                               c.title as competition_title, cr.payment_order_no,
                               cr.owner_user_id, cr.owner_user_uuid, cr.updated_at
                        from competition_registration cr
                        join aiadc_competition c on c.id = cr.competition_id and c.deleted = 0
                        where cr.deleted = 0
                          and cr.status = 'PENDING_PAYMENT'
                          and cr.payment_order_no is not null
                          and cr.payment_order_no <> ''
                          and cr.updated_at <= ?
                        order by cr.updated_at asc, cr.id asc
                        limit ?
                        """,
                (row, rowNum) -> new PendingPaymentCandidate(
                        row.getLong("registration_id"),
                        row.getString("registration_no"),
                        row.getLong("competition_id"),
                        row.getString("competition_title"),
                        row.getString("payment_order_no"),
                        row.getLong("owner_user_id"),
                        row.getString("owner_user_uuid"),
                        row.getTimestamp("updated_at").toLocalDateTime()
                ),
                updatedBefore,
                Math.max(1, Math.min(limit, 500))
        );
    }

    @Override
    public PendingPaymentCandidate findPendingPaymentCandidateByOrder(String paymentOrderNo) {
        return database.queryForObject(
                """
                        select cr.id as registration_id, cr.registration_no, cr.competition_id,
                               c.title as competition_title, cr.payment_order_no,
                               cr.owner_user_id, cr.owner_user_uuid, cr.updated_at
                        from competition_registration cr
                        join aiadc_competition c on c.id = cr.competition_id and c.deleted = 0
                        where cr.deleted = 0
                          and cr.status = 'PENDING_PAYMENT'
                          and cr.payment_order_no = ?
                        limit 1
                        """,
                (row, rowNum) -> new PendingPaymentCandidate(
                        row.getLong("registration_id"),
                        row.getString("registration_no"),
                        row.getLong("competition_id"),
                        row.getString("competition_title"),
                        row.getString("payment_order_no"),
                        row.getLong("owner_user_id"),
                        row.getString("owner_user_uuid"),
                        row.getTimestamp("updated_at").toLocalDateTime()
                ),
                paymentOrderNo
        );
    }

    @Override
    public CompetitionRegistrationVO.Stage findStage(Long stageId) {
        return database.queryForObject(
                stageSelect() + " from competition_stage where id = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(CompetitionRegistrationVO.Stage.class),
                stageId
        );
    }

    @Override
    public CompetitionRegistrationVO.StageForm findStageForm(Long stageId) {
        return database.queryForObject(
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

    @Override
    public CompetitionRegistrationVO.StageForm findReadableStageForm(Long stageId) {
        return database.queryForObject(
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

    @Override
    public boolean hasPublishedPreliminaryAdvance(Long competitionId, Long registrationId) {
        Long advanced = database.queryForObject(
                """
                        select count(1)
                        from competition_stage_review_result rr
                        join competition_stage source_stage on source_stage.id = rr.stage_id and source_stage.deleted = 0
                        where rr.competition_id = ? and rr.registration_id = ? and rr.decision = 'ADVANCED'
                          and rr.published_at is not null and rr.deleted = 0
                          and source_stage.stage_code = 'PRELIMINARY'
                        """,
                Long.class,
                competitionId,
                registrationId
        );
        return advanced != null && advanced > 0L;
    }

    @Override
    public boolean hasPublishedPreliminaryAdvanceForOwner(Long competitionId, Long ownerUserId, String ownerUserUuid) {
        Long advanced = database.queryForObject(
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
                competitionId,
                ownerUserId,
                ownerUserUuid
        );
        return advanced != null && advanced > 0L;
    }

    @Override
    public int updateRegistration(UpdateRegistrationCommand command) {
        return database.update(
                """
                        update competition_registration
                        set competition_id = ?, team_id = ?, project_id = ?, fee_mode = ?, entry_fee_minor = ?,
                            member_count = ?, payable_amount_minor = ?, currency = ?, registration_snapshot_json = ?,
                            team_snapshot_json = ?, project_snapshot_json = ?, member_snapshot_json = ?,
                            updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and registration_no = ? and owner_user_id = ? and owner_user_uuid = ?
                          and status = ? and deleted = 0
                        """,
                command.competitionId(), command.teamId(), command.projectId(), command.feeMode(), command.entryFeeMinor(),
                command.memberCount(), command.payableAmountMinor(), command.currency(), command.registrationSnapshotJson(),
                command.teamSnapshotJson(), command.projectSnapshotJson(), command.memberSnapshotJson(),
                command.updatedBy(), command.updatedByUuid(), command.updatedAt(), command.registrationId(),
                command.registrationNo(), command.ownerUserId(), command.ownerUserUuid(), command.status()
        );
    }

    @Override
    public int updateCollectionSchemaSnapshot(Long registrationId, String snapshotJson, LocalDateTime updatedAt) {
        return database.update(
                "update competition_registration set collection_schema_snapshot_json = ?, updated_at = ? where id = ? and deleted = 0",
                snapshotJson,
                updatedAt,
                registrationId
        );
    }

    @Override
    public int cancelPaymentOrderTasks(Long registrationId, Long operatorUserId, String operatorUserUuid, LocalDateTime updatedAt) {
        return database.update(
                """
                        update competition_payment_order_task
                        set status = 'CANCELLED', process_message = 'Registration deleted before payment',
                            claim_token = null, claim_expires_at = null, updated_by = ?, updated_by_uuid = ?,
                            updated_at = ?, deleted = 1
                        where registration_id = ? and deleted = 0 and status not in ('SUCCEEDED', 'CANCELLED')
                        """,
                operatorUserId, operatorUserUuid, updatedAt, registrationId
        );
    }

    @Override
    public int cancelPendingRegistration(CancelRegistrationCommand command) {
        return database.update(
                """
                        update competition_registration
                        set status = 'CANCELLED', updated_by = ?, updated_by_uuid = ?, updated_at = ?, deleted = 1
                        where id = ? and registration_no = ? and owner_user_id = ? and owner_user_uuid = ?
                          and status = 'PENDING_PAYMENT' and deleted = 0
                        """,
                command.operatorUserId(), command.operatorUserUuid(), command.updatedAt(), command.registrationId(),
                command.registrationNo(), command.ownerUserId(), command.ownerUserUuid()
        );
    }

    @Override
    public Long createStage(CreateStageCommand command) {
        int inserted = database.update(
                """
                        insert into competition_stage (
                            competition_id, stage_code, stage_name, material_submit_start, material_submit_end,
                            review_start, review_end, status, sort, promotion_rule_type, promotion_rule_value, promotion_tie_policy,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                command.competitionId(), command.stageCode(), command.stageName(), command.materialSubmitStart(), command.materialSubmitEnd(),
                command.reviewStart(), command.reviewEnd(), command.status(), command.sort(), command.promotionRuleType(),
                command.promotionRuleValue(), command.promotionTiePolicy(), command.userId(), command.userUuid(),
                command.userId(), command.userUuid()
        );
        if (inserted <= 0) {
            return null;
        }
        return database.queryForObject("select last_insert_id()", Long.class);
    }

    @Override
    public int updateStage(UpdateStageCommand command) {
        return database.update(
                """
                        update competition_stage
                        set stage_name = ?, material_submit_start = ?, material_submit_end = ?, review_start = ?, review_end = ?,
                            status = ?, sort = ?, promotion_rule_type = ?, promotion_rule_value = ?, promotion_tie_policy = ?,
                            updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and competition_id = ? and stage_code = ? and deleted = 0
                        """,
                command.stageName(), command.materialSubmitStart(), command.materialSubmitEnd(), command.reviewStart(), command.reviewEnd(),
                command.status(), command.sort(), command.promotionRuleType(), command.promotionRuleValue(), command.promotionTiePolicy(),
                command.userId(), command.userUuid(), command.updatedAt(), command.stageId(), command.competitionId(), command.stageCode()
        );
    }

    @Override
    public void upsertStageReviewResult(StageReviewResultCommand command) {
        database.update(
                """
                        insert into competition_stage_review_result (
                            competition_id, stage_id, registration_id, score, decision, review_comment, published_at,
                            decided_by, decided_by_uuid, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        on duplicate key update score = values(score), decision = values(decision), review_comment = values(review_comment),
                            published_at = values(published_at), decided_by = values(decided_by), decided_by_uuid = values(decided_by_uuid),
                            updated_by = values(updated_by), updated_by_uuid = values(updated_by_uuid), updated_at = current_timestamp
                        """,
                command.competitionId(), command.stageId(), command.registrationId(), command.score(), command.decision(),
                command.reviewComment(), command.publishedAt(), command.userId(), command.userUuid(), command.userId(), command.userUuid(),
                command.userId(), command.userUuid()
        );
    }

    @Override
    public int createStageForm(CreateStageFormCommand command) {
        return database.update(
                """
                        insert into competition_stage_form (
                            competition_id, stage_id, form_name, form_schema_json, version, status,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                command.competitionId(), command.stageId(), command.formName(), command.formSchemaJson(), command.version(),
                command.status(), command.userId(), command.userUuid(), command.userId(), command.userUuid()
        );
    }

    @Override
    public int updateStageForm(UpdateStageFormCommand command) {
        return database.update(
                """
                        update competition_stage_form
                        set form_name = ?, form_schema_json = ?, version = ?, status = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and competition_id = ? and stage_id = ? and status = ? and deleted = 0
                        """,
                command.formName(), command.formSchemaJson(), command.version(), command.status(), command.userId(), command.userUuid(),
                command.updatedAt(), command.formId(), command.competitionId(), command.stageId(), command.existingStatus()
        );
    }

    @Override
    public Long createMaterialSubmission(CreateMaterialSubmissionCommand command) {
        int inserted = database.update(
                """
                        insert into registration_material_submission (
                            registration_id, competition_id, stage_id, form_version, submitter_user_id, submitter_user_uuid,
                            status, submitted_at, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, 'SUBMITTED', ?, ?, ?, ?, ?, 0)
                        """,
                command.registrationId(), command.competitionId(), command.stageId(), command.formVersion(), command.userId(),
                command.userUuid(), command.submittedAt(), command.userId(), command.userUuid(), command.userId(), command.userUuid()
        );
        if (inserted <= 0) {
            return null;
        }
        return database.queryForObject("select last_insert_id()", Long.class);
    }

    @Override
    public int updateMaterialSubmission(UpdateMaterialSubmissionCommand command) {
        return database.update(
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
                command.userId(), command.userUuid(), command.submittedAt(), command.userId(), command.userUuid(), command.updatedAt(),
                command.submissionId(), command.registrationId(), command.stageId(), command.formVersion(), command.userId(), command.userUuid()
        );
    }

    @Override
    public void archiveMaterialValues(ArchiveMaterialValuesCommand command) {
        Integer revisionNo = command.revisionNo();
        if (revisionNo == null) {
            revisionNo = database.queryForObject(
                    "select coalesce(max(revision_no), 0) + 1 from registration_material_value_revision where submission_id = ?",
                    Integer.class,
                    command.submissionId()
            );
        }
        database.update(
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
                command.userId(),
                command.userUuid(),
                command.submissionId()
        );
        database.update(
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
                command.submissionId(), command.userId(), command.userUuid(), command.registrationId(), command.stageId(), command.formVersion()
        );
    }

    @Override
    public void insertMaterialValues(Long submissionId, List<MaterialValueCommand> values) {
        for (MaterialValueCommand value : values == null ? List.<MaterialValueCommand>of() : values) {
            database.update(
                    """
                            insert into registration_material_value (
                                submission_id, field_key, field_type, text_value, file_id, json_value, deleted
                            ) values (?, ?, ?, ?, ?, ?, 0)
                            """,
                    submissionId, value.fieldKey(), value.fieldType(), value.textValue(), value.fileId(), value.jsonValue()
            );
        }
    }

    @Override
    public int enqueuePaymentOrderTask(EnqueuePaymentOrderTaskCommand command) {
        return database.update(
                """
                        insert into competition_payment_order_task (
                            registration_id, provider_code, client_ip, notify_url, return_url, owner_user_uuid, simulated_role_id,
                            attempt_no, status, retry_count, next_retry_at, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, 1, 'PENDING', 0, ?, ?, ?, ?, ?, 0)
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
                command.registrationId(), command.providerCode(), command.clientIp(), command.notifyUrl(), command.returnUrl(),
                command.ownerUserUuid(), command.simulatedRoleId(), command.nextRetryAt(), command.operatorUserId(),
                command.operatorUserUuid(), command.operatorUserId(), command.operatorUserUuid()
        );
    }

    @Override
    public int detachPaymentOrderForRetry(DetachPaymentOrderForRetryCommand command) {
        return database.update(
                """
                        update competition_registration
                        set payment_order_no = null, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and registration_no = ? and owner_user_id = ? and owner_user_uuid = ?
                          and payment_order_no = ? and status = 'PENDING_PAYMENT' and deleted = 0
                        """,
                command.operatorUserId(), command.operatorUserUuid(), command.updatedAt(),
                command.registrationId(), command.registrationNo(), command.ownerUserId(), command.ownerUserUuid(),
                command.expectedPaymentOrderNo()
        );
    }

    @Override
    public int enqueuePaymentOrderRetryTask(EnqueuePaymentOrderTaskCommand command) {
        return database.update(
                """
                        insert into competition_payment_order_task (
                            registration_id, provider_code, client_ip, notify_url, return_url, owner_user_uuid, simulated_role_id,
                            attempt_no, status, retry_count, next_retry_at, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, 2, 'PENDING', 0, ?, ?, ?, ?, ?, 0)
                        on duplicate key update
                            provider_code = values(provider_code),
                            client_ip = coalesce(values(client_ip), client_ip),
                            notify_url = coalesce(values(notify_url), notify_url),
                            return_url = coalesce(values(return_url), return_url),
                            simulated_role_id = values(simulated_role_id),
                            attempt_no = greatest(2, attempt_no + 1),
                            status = 'PENDING', retry_count = 0, next_retry_at = values(next_retry_at),
                            claim_token = null, claim_expires_at = null, process_message = null,
                            updated_by = values(updated_by), updated_by_uuid = values(updated_by_uuid), updated_at = current_timestamp
                        """,
                command.registrationId(), command.providerCode(), command.clientIp(), command.notifyUrl(), command.returnUrl(),
                command.ownerUserUuid(), command.simulatedRoleId(), command.nextRetryAt(), command.operatorUserId(),
                command.operatorUserUuid(), command.operatorUserId(), command.operatorUserUuid()
        );
    }

    @Override
    public List<PaymentOrderTask> claimPaymentOrderTasks(int limit, String claimToken, LocalDateTime now, LocalDateTime claimExpiresAt) {
        database.update(
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
                claimToken, claimExpiresAt, now, now, limit
        );
        return database.queryForList(
                """
                        select id, registration_id as registrationId, provider_code as providerCode,
                               client_ip as clientIp, notify_url as notifyUrl, return_url as returnUrl,
                               owner_user_uuid as ownerUserUuid, simulated_role_id as simulatedRoleId,
                               attempt_no as attemptNo, claim_token as claimToken
                        from competition_payment_order_task
                        where deleted = 0
                          and status = 'RUNNING'
                          and claim_token = ?
                          and owner_user_uuid is not null
                          and owner_user_uuid <> ''
                        order by created_at asc, id asc
                        """,
                claimToken
        ).stream().map(row -> new PaymentOrderTask(
                toLong(row.get("id")), toLong(row.get("registrationId")), toText(row.get("providerCode")),
                toText(row.get("clientIp")), toText(row.get("notifyUrl")), toText(row.get("returnUrl")),
                toText(row.get("ownerUserUuid")), toLong(row.get("simulatedRoleId")), toInteger(row.get("attemptNo")),
                toText(row.get("claimToken"))
        )).toList();
    }

    @Override
    public int attachPaymentOrder(AttachPaymentOrderCommand command) {
        return database.update(
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
                command.paymentOrderNo(), command.ownerUserId(), command.ownerUserUuid(), command.updatedAt(),
                command.registrationId(), command.registrationNo(), command.ownerUserId(), command.ownerUserUuid(),
                command.payableAmountMinor(), command.currency()
        );
    }

    @Override
    public int markPaymentOrderTaskSucceeded(PaymentOrderTaskCompletion command) {
        return database.update(
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
                command.message(), command.updatedAt(), command.taskId(), command.registrationId(), command.ownerUserUuid(), command.claimToken()
        );
    }

    @Override
    public int markPaymentOrderTaskFailed(PaymentOrderTaskFailure command) {
        Integer retryCount = database.queryForObject(
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
                command.taskId(), command.registrationId(), command.ownerUserUuid(), command.claimToken()
        );
        int nextRetryCount = retryCount == null ? 1 : retryCount + 1;
        String nextStatus = nextRetryCount >= command.maxRetry() ? "DEAD" : "FAILED";
        LocalDateTime nextRetryAt = command.nextRetryAt() == null
                ? command.updatedAt().plusSeconds(Math.min(300L, 5L * (1L << Math.min(nextRetryCount, 6))))
                : command.nextRetryAt();
        String message = command.message();
        if (StringUtils.hasText(message) && message.length() > 512) {
            message = message.substring(0, 512);
        }
        return database.update(
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
                nextStatus, nextRetryCount, nextRetryAt, message, command.updatedAt(), command.taskId(), command.registrationId(),
                command.ownerUserUuid(), command.claimToken()
        );
    }

    @Override
    public int confirmPaidRegistration(ConfirmPaidRegistrationCommand command) {
        return database.update(
                """
                        update competition_registration
                        set status = 'CONFIRMED', participant_no = ?, payment_order_no = coalesce(?, payment_order_no),
                            updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and deleted = 0
                          and owner_user_id = ? and owner_user_uuid = ?
                          and participant_no is null
                        """,
                command.participantNo(), command.paymentOrderNo(), command.ownerUserId(), command.ownerUserUuid(), command.updatedAt(),
                command.registrationId(), command.ownerUserId(), command.ownerUserUuid()
        );
    }

    private static <T> T first(List<T> rows) {
        return rows == null || rows.isEmpty() ? null : rows.get(0);
    }

    private static String toText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null || !StringUtils.hasText(String.valueOf(value)) ? null : Long.valueOf(String.valueOf(value));
    }

    private static Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null || !StringUtils.hasText(String.valueOf(value)) ? null : Integer.valueOf(String.valueOf(value));
    }

    private static String registrationSelect() {
        return """
                select id, registration_no as registrationNo, competition_id as competitionId,
                       (select competition.title
                          from aiadc_competition competition
                         where competition.id = competition_registration.competition_id
                           and competition.deleted = 0
                         limit 1) as competitionTitle,
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

    private static String registrationListSelect(boolean includeSnapshots) {
        String snapshotColumns = includeSnapshots
                ? """
                       registration_snapshot_json as registrationSnapshotJson,
                       team_snapshot_json as teamSnapshotJson,
                       project_snapshot_json as projectSnapshotJson,
                       member_snapshot_json as memberSnapshotJson,
                       collection_schema_snapshot_json as collectionSchemaSnapshotJson,
                  """
                : "";
        return """
                select id, registration_no as registrationNo, competition_id as competitionId,
                       (select competition.title
                          from aiadc_competition competition
                         where competition.id = competition_registration.competition_id
                           and competition.deleted = 0
                         limit 1) as competitionTitle,
                       team_id as teamId, project_id as projectId, owner_user_id as ownerUserId,
                       owner_user_uuid as ownerUserUuid, status,
                       fee_mode as feeMode, entry_fee_minor as entryFeeMinor, member_count as memberCount,
                       payable_amount_minor as payableAmountMinor, currency, payment_order_no as paymentOrderNo,
                       participant_no as participantNo,
                """ + snapshotColumns + """
                       case when json_valid(team_snapshot_json)
                           then json_unquote(json_extract(team_snapshot_json, '$.teamName')) end as teamName,
                       case when json_valid(project_snapshot_json)
                           then json_unquote(json_extract(project_snapshot_json, '$.title')) end as projectTitle,
                       (select count(1)
                          from registration_material_submission rms
                         where rms.registration_id = competition_registration.id and rms.deleted = 0)
                           as materialSubmissionCount,
                       (select count(1)
                          from registration_material_submission rms
                          join registration_material_value rmv
                            on rmv.submission_id = rms.id and rmv.deleted = 0 and rmv.file_id is not null
                         where rms.registration_id = competition_registration.id and rms.deleted = 0)
                           as materialFileCount,
                       created_at as createdAt, updated_at as updatedAt
                """;
    }

    private static String stageSelect() {
        return """
                select id, competition_id as competitionId, stage_code as stageCode,
                       stage_name as stageName, material_submit_start as materialSubmitStart,
                       material_submit_end as materialSubmitEnd, review_start as reviewStart, review_end as reviewEnd,
                       status, sort, promotion_rule_type as promotionRuleType,
                       promotion_rule_value as promotionRuleValue, promotion_tie_policy as promotionTiePolicy
                """;
    }

    private static String paymentRecordSelect() {
        return """
                select cr.id as registrationId, cr.registration_no as registrationNo,
                       cr.competition_id as competitionId, c.code as competitionCode, c.title as competitionTitle,
                       cr.team_id as teamId,
                       coalesce(json_unquote(json_extract(cr.team_snapshot_json, '$.teamName')),
                                concat('Team #', cr.team_id)) as teamName,
                       cr.project_id as projectId,
                       coalesce(json_unquote(json_extract(cr.project_snapshot_json, '$.title')),
                                concat('Project #', cr.project_id)) as projectTitle,
                       cr.owner_user_id as ownerUserId, cr.status as registrationStatus,
                       cr.participant_no as participantNo, cr.member_count as memberCount,
                       cr.payable_amount_minor as payableAmountMinor,
                       cr.payment_order_no as orderNo,
                       null as providerCode, null as providerOrderNo,
                       null as subject, cr.payable_amount_minor as amountMinor,
                       cr.currency as currency,
                       case
                           when cr.payable_amount_minor = 0
                                and (cr.payment_order_no is null or cr.payment_order_no = '')
                               then 'NOT_REQUIRED'
                           else cr.status
                       end as paymentStatus,
                       null as paymentUrl, null as failureCode, null as failureMessage,
                       null as orderCreatedAt, null as paidAt,
                       cr.created_at as registrationCreatedAt,
                       cr.updated_at as updatedAt
                """;
    }

    private static final class CompetitionRow {
        private Long id;
        private String code;
        private String feeMode;
        private Long entryFeeMinor;
        private String currency;
        private String registrationStart;
        private String registrationEnd;

        public void setId(Long id) { this.id = id; }
        public void setCode(String code) { this.code = code; }
        public void setFeeMode(String feeMode) { this.feeMode = feeMode; }
        public void setEntryFeeMinor(Long entryFeeMinor) { this.entryFeeMinor = entryFeeMinor; }
        public void setCurrency(String currency) { this.currency = currency; }
        public void setRegistrationStart(String registrationStart) { this.registrationStart = registrationStart; }
        public void setRegistrationEnd(String registrationEnd) { this.registrationEnd = registrationEnd; }
    }
}

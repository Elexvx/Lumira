package com.lumira.saas.modules.activity.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.activity.model.ActivityRegistrationAnswer;
import com.lumira.saas.modules.activity.model.ActivityRegistrationField;
import com.lumira.saas.modules.activity.repository.ActivityRegistrationRepository;
import com.lumira.saas.modules.activity.vo.ActivityRegistrationVO;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class JdbcActivityRegistrationRepository implements ActivityRegistrationRepository {
    private final ActivitySqlOperations database;
    private final ObjectMapper objectMapper;

    public JdbcActivityRegistrationRepository(ActivitySqlOperations database, ObjectMapper objectMapper) {
        this.database = database;
        this.objectMapper = objectMapper;
    }

    @Autowired
    public JdbcActivityRegistrationRepository(ActivitySqlOperations database) {
        this(database, new ObjectMapper());
    }

    @Override
    public Optional<RegistrationForm> findPublishedRegistrationForm(Long activityId) {
        return database.query("""
                        select id, title, registration_form_json
                          from aiadc_activity
                         where id = ? and status = 'published' and deleted = 0
                         limit 1
                        """,
                (resultSet, rowNumber) -> new RegistrationForm(
                        resultSet.getLong("id"),
                        resultSet.getString("title"),
                        deserializeRegistrationFields(resultSet.getString("registration_form_json"))
                ),
                activityId
        ).stream().findFirst();
    }

    @Override
    public ActivityRegistrationVO create(
            Long userId,
            String userUuid,
            String username,
            RegistrationSubmission submission
    ) {
        Integer exists = database.queryForObject(
                "select count(*) from aiadc_activity where id = ? and status = 'published' and deleted = 0",
                Integer.class,
                submission.activityId()
        );
        if (exists == null || exists == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "Activity not found");
        }
        String applicationNo = "ACT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        database.update("""
                insert into aiadc_activity_registration
                (application_no, activity_id, name, mobile, email, organization, position, remark, form_data_json, status,
                 owner_user_id, owner_user_uuid, owner_username, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'SUBMITTED', ?, ?, ?, ?, ?, ?, ?, 0)
                """, applicationNo, submission.activityId(), submission.name(), submission.mobile(), submission.email(),
                submission.organization(), submission.position(), submission.remark(), serializeAnswers(submission.answers()),
                userId, userUuid, username, userId, userUuid, userId, userUuid);
        return findByApplicationNo(applicationNo);
    }

    @Override
    public List<ActivityRegistrationVO> listVisible(Long userId, String userUuid, boolean viewAll) {
        String ownership = viewAll ? "" : " and r.owner_user_id = ? and r.owner_user_uuid = ?";
        Object[] args = viewAll ? new Object[0] : new Object[]{userId, userUuid};
        List<ActivityRegistrationVO> records = database.query(
                selectSql() + " where r.deleted = 0" + ownership + " order by r.submitted_at desc, r.id desc",
                new BeanPropertyRowMapper<>(ActivityRegistrationVO.class),
                args
        );
        records.forEach(this::hydrateAnswers);
        return records;
    }

    private ActivityRegistrationVO findByApplicationNo(String applicationNo) {
        List<ActivityRegistrationVO> rows = database.query(
                selectSql() + " where r.application_no = ? and r.deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(ActivityRegistrationVO.class),
                applicationNo
        );
        rows.forEach(this::hydrateAnswers);
        if (rows.isEmpty()) {
            throw new IllegalStateException("Created activity registration is unavailable");
        }
        return rows.getFirst();
    }

    private String selectSql() {
        return """
                select r.id, r.application_no as applicationNo, r.activity_id as activityId, a.title as activityTitle,
                       coalesce(nullif(r.name, ''), r.owner_username) as name,
                       r.mobile, r.email, r.organization, r.position, r.remark, r.form_data_json as formDataJson, r.status,
                       r.submitted_at as submittedAt, r.owner_user_id as ownerUserId, r.owner_username as ownerUsername
                  from aiadc_activity_registration r join aiadc_activity a on a.id = r.activity_id and a.deleted = 0
                """;
    }

    private void hydrateAnswers(ActivityRegistrationVO registration) {
        List<ActivityRegistrationAnswer> answers = deserializeAnswers(registration.getFormDataJson());
        registration.setAnswers(answers.isEmpty() ? legacyAnswers(registration) : answers);
    }

    private List<ActivityRegistrationAnswer> legacyAnswers(ActivityRegistrationVO registration) {
        List<ActivityRegistrationAnswer> answers = new ArrayList<>();
        addLegacyAnswer(answers, "name", "姓名", "TEXT", registration.getName());
        addLegacyAnswer(answers, "mobile", "手机号", "MOBILE", registration.getMobile());
        addLegacyAnswer(answers, "email", "邮箱", "EMAIL", registration.getEmail());
        addLegacyAnswer(answers, "organization", "单位", "TEXT", registration.getOrganization());
        addLegacyAnswer(answers, "position", "职务", "TEXT", registration.getPosition());
        addLegacyAnswer(answers, "remark", "备注", "TEXTAREA", registration.getRemark());
        return List.copyOf(answers);
    }

    private void addLegacyAnswer(
            List<ActivityRegistrationAnswer> answers,
            String fieldKey,
            String label,
            String fieldType,
            String value
    ) {
        if (StringUtils.hasText(value)) {
            answers.add(new ActivityRegistrationAnswer(fieldKey, label, fieldType, value));
        }
    }

    private String serializeAnswers(List<ActivityRegistrationAnswer> answers) {
        try {
            return objectMapper.writeValueAsString(answers == null ? List.of() : answers);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Activity registration answers cannot be serialized", exception);
        }
    }

    private List<ActivityRegistrationAnswer> deserializeAnswers(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Activity registration answers are invalid", exception);
        }
    }

    private List<ActivityRegistrationField> deserializeRegistrationFields(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Activity registration form is invalid", exception);
        }
    }
}

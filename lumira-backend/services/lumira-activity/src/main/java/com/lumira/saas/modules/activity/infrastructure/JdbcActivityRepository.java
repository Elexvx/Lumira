package com.lumira.saas.modules.activity.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.saas.modules.activity.dto.ActivityDTO;
import com.lumira.api.dictionary.DictionaryValueNormalizer;
import com.lumira.saas.modules.activity.infrastructure.persistence.ActivitySqlOperations;
import com.lumira.saas.modules.activity.model.ActivityRegistrationField;
import com.lumira.saas.modules.activity.repository.ActivityRepository;
import com.lumira.saas.modules.activity.vo.ActivityVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class JdbcActivityRepository implements ActivityRepository {
    private static final String SELECT = """
            select id, code, locale, title, subtitle, description, image_url as imageUrl, sort, status, tags,
                   cta_label as ctaLabel, cta_href as ctaHref, activity_date as activityDate,
                   activity_time as activityTime, location, featured, registration_form_json as registrationFormJson,
                   created_at as createdAt, updated_at as updatedAt
            """;
    private final ActivitySqlOperations database;
    private final DictionaryValueNormalizer dictionaryValueNormalizer;
    private final ObjectMapper objectMapper;

    @Autowired
    public JdbcActivityRepository(
            ActivitySqlOperations database,
            DictionaryValueNormalizer dictionaryValueNormalizer
    ) {
        this.database = database;
        this.dictionaryValueNormalizer = dictionaryValueNormalizer;
        this.objectMapper = new ObjectMapper();
    }

    public JdbcActivityRepository(ActivitySqlOperations database) {
        this(database, null);
    }

    @Override
    public List<String> findEnabledDictValues(String dictCode) {
        return dictionaryValueNormalizer == null ? List.of() : dictionaryValueNormalizer.enabledValues(dictCode);
    }

    @Override
    public PageData search(String keyword, String status, String locale, Boolean featured, long offset, long limit) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" from aiadc_activity where deleted = 0");
        if (StringUtils.hasText(keyword)) { where.append(" and (title like ? or code like ? or subtitle like ?)"); String p="%"+keyword.trim()+"%"; args.add(p);args.add(p);args.add(p); }
        if (StringUtils.hasText(status)) { where.append(" and status = ?"); args.add(status); }
        if (StringUtils.hasText(locale)) { where.append(" and find_in_set(?, replace(locale, ' ', '')) > 0"); args.add(locale); }
        if (featured != null) { where.append(" and featured = ?"); args.add(featured ? 1 : 0); }
        Long total = database.queryForObject("select count(1)" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args); pageArgs.add(offset); pageArgs.add(limit);
        List<ActivityVO.Activity> records = database.query(SELECT + where + " order by sort asc, updated_at desc, id desc limit ?, ?",
                new BeanPropertyRowMapper<>(ActivityVO.Activity.class), pageArgs.toArray());
        records.forEach(this::hydrateRegistrationFields);
        return new PageData(records, total == null ? 0L : total);
    }

    @Override public Optional<ActivityVO.Activity> findById(Long id) {
        List<ActivityVO.Activity> records = database.query(SELECT + " from aiadc_activity where id = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(ActivityVO.Activity.class), id);
        records.forEach(this::hydrateRegistrationFields);
        return records.stream().findFirst();
    }

    @Override public Long create(ActivityDTO.ActivityUpsertRequest a, Long userId, String uuid) {
        int count = database.update("""
                insert into aiadc_activity (code, locale, title, subtitle, description, image_url, sort, status, tags,
                    cta_label, cta_href, activity_date, activity_time, location, featured, registration_form_json,
                    created_by, created_by_uuid, updated_by, updated_by_uuid, deleted)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, a.getCode(),a.getLocale(),a.getTitle(),a.getSubtitle(),a.getDescription(),a.getImageUrl(),a.getSort(),a.getStatus(),a.getTags(),
                a.getCtaLabel(),a.getCtaHref(),a.getActivityDate(),a.getActivityTime(),a.getLocation(),Boolean.TRUE.equals(a.getFeatured())?1:0,
                serializeRegistrationFields(a.getRegistrationFields()),userId,uuid,userId,uuid);
        if (count <= 0) return null;
        return database.queryForObject("select last_insert_id()", Long.class);
    }

    @Override public int update(Long id, ActivityVO.Activity e, ActivityDTO.ActivityUpsertRequest a, Long userId, String uuid) {
        return database.update("""
                update aiadc_activity set code=?, locale=?, title=?, subtitle=?, description=?, image_url=?, sort=?, status=?, tags=?,
                    cta_label=?, cta_href=?, activity_date=?, activity_time=?, location=?, featured=?, registration_form_json=?,
                    updated_by=?, updated_by_uuid=?, updated_at=?
                where id = ? and code = ? and locale = ? and status = ? and deleted = 0
                """,a.getCode(),a.getLocale(),a.getTitle(),a.getSubtitle(),a.getDescription(),a.getImageUrl(),a.getSort(),a.getStatus(),a.getTags(),
                a.getCtaLabel(),a.getCtaHref(),a.getActivityDate(),a.getActivityTime(),a.getLocation(),Boolean.TRUE.equals(a.getFeatured())?1:0,
                serializeRegistrationFields(a.getRegistrationFields()),userId,uuid,LocalDateTime.now(),id,e.getCode(),e.getLocale(),e.getStatus());
    }

    @Override public int delete(Long id, ActivityVO.Activity e, Long userId, String uuid) {
        return database.update("update aiadc_activity set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ? where id = ? and code = ? and locale = ? and status = ? and deleted = 0",
                userId,uuid,LocalDateTime.now(),id,e.getCode(),e.getLocale(),e.getStatus());
    }

    private void hydrateRegistrationFields(ActivityVO.Activity activity) {
        activity.setRegistrationFields(deserializeRegistrationFields(activity.getRegistrationFormJson()));
    }

    private String serializeRegistrationFields(List<ActivityRegistrationField> fields) {
        try {
            return objectMapper.writeValueAsString(fields == null ? List.of() : fields);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Activity registration form cannot be serialized", exception);
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

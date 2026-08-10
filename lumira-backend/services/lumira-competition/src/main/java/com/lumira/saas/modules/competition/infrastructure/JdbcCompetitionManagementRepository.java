package com.lumira.saas.modules.competition.infrastructure;

import com.lumira.saas.modules.competition.infrastructure.persistence.BeanPropertyRowMapper;
import com.lumira.saas.modules.competition.infrastructure.persistence.CompetitionSqlOperations;
import com.lumira.saas.modules.competition.dto.CompetitionDTO;
import com.lumira.saas.modules.competition.repository.CompetitionManagementRepository;
import com.lumira.saas.modules.competition.vo.CompetitionVO;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class JdbcCompetitionManagementRepository implements CompetitionManagementRepository {

    private final CompetitionSqlOperations database;

    public JdbcCompetitionManagementRepository(CompetitionSqlOperations database) {
        this.database = database;
    }

    @Override
    public CompetitionPage findCompetitions(CompetitionSearch search) {
        StringBuilder where = new StringBuilder(" from aiadc_competition where deleted = 0");
        List<Object> params = new ArrayList<>();
        if (StringUtils.hasText(search.keyword())) {
            where.append(" and (title like ? or code like ? or organizer like ?)");
            String pattern = "%" + search.keyword().trim() + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        if (StringUtils.hasText(search.category())) {
            where.append(" and category = ?");
            params.add(search.category().trim());
        }
        if (StringUtils.hasText(search.status())) {
            where.append(" and status = ?");
            params.add(search.status());
        }
        if (StringUtils.hasText(search.locale())) {
            where.append(" and find_in_set(?, replace(locale, ' ', '')) > 0");
            params.add(search.locale());
        }
        if (search.featured() != null) {
            where.append(" and featured = ?");
            params.add(Boolean.TRUE.equals(search.featured()) ? 1 : 0);
        }

        Long total = database.queryForObject("select count(1)" + where, Long.class, params.toArray());
        List<Object> selectParams = new ArrayList<>(params);
        selectParams.add(search.offset());
        selectParams.add(search.limit());
        List<CompetitionVO.Competition> records = database.query(
                competitionSelect() + where + " order by sort asc, featured desc, updated_at desc, id desc limit ?, ?",
                new BeanPropertyRowMapper<>(CompetitionVO.Competition.class),
                selectParams.toArray()
        );
        return new CompetitionPage(records, total == null ? 0L : total);
    }

    @Override
    public CompetitionVO.Competition findCompetition(Long id) {
        return first(database.query(
                competitionSelect() + " from aiadc_competition where id = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(CompetitionVO.Competition.class),
                id
        ));
    }

    @Override
    public CompetitionVO.Competition findCompetitionByUuid(String competitionUuid) {
        return first(database.query(
                competitionSelect() + " from aiadc_competition where uuid = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(CompetitionVO.Competition.class),
                competitionUuid
        ));
    }

    @Override
    public CompetitionVO.Competition findPublishedCompetitionByUuid(String competitionUuid) {
        return first(database.query(
                competitionSelect() + " from aiadc_competition where uuid = ? and status = 'published' and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(CompetitionVO.Competition.class),
                competitionUuid
        ));
    }

    @Override
    public CompetitionCreateResult createCompetition(CompetitionCreate command) {
        CompetitionDTO.CompetitionUpsertRequest competition = command.competition();
        Actor actor = command.actor();
        int inserted = database.update(
                """
                        insert into aiadc_competition (
                            uuid, competition_no, code, locale, title, short_name, category, level, competition_level, organizer, organizers_json,
                            registration_start, registration_end, competition_start, competition_end,
                            location, participation_scope, participation_requirement, schedule_json, description, image_url,
                            contact_name, contact_qr_code_url, tags, status, fee_mode, entry_fee_minor, currency, featured, sort,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                command.uuid(),
                command.competitionNo(),
                competition.getCode(),
                competition.getLocale(),
                competition.getTitle(),
                competition.getShortName(),
                competition.getCategory(),
                competition.getLevel(),
                competition.getCompetitionLevel(),
                competition.getOrganizer(),
                competition.getOrganizersJson(),
                competition.getRegistrationStart(),
                competition.getRegistrationEnd(),
                competition.getCompetitionStart(),
                competition.getCompetitionEnd(),
                competition.getLocation(),
                competition.getParticipationScope(),
                competition.getParticipationRequirement(),
                competition.getScheduleJson(),
                competition.getDescription(),
                competition.getImageUrl(),
                competition.getContactName(),
                competition.getContactQrCodeUrl(),
                competition.getTags(),
                competition.getStatus(),
                competition.getFeeMode(),
                competition.getEntryFeeMinor(),
                competition.getCurrency(),
                Boolean.TRUE.equals(competition.getFeatured()) ? 1 : 0,
                competition.getSort(),
                actor.userId(),
                actor.userUuid(),
                actor.userId(),
                actor.userUuid()
        );
        Long competitionId = inserted == 1 ? database.queryForObject("select last_insert_id()", Long.class) : null;
        return new CompetitionCreateResult(competitionId, inserted);
    }

    @Override
    public int updateCompetition(CompetitionUpdate command) {
        CompetitionDTO.CompetitionUpsertRequest competition = command.competition();
        Actor actor = command.actor();
        return database.update(
                """
                        update aiadc_competition
                        set locale = ?, title = ?, short_name = ?, category = ?, level = ?, competition_level = ?, organizer = ?, organizers_json = ?,
                            registration_start = ?, registration_end = ?, competition_start = ?, competition_end = ?,
                            location = ?, participation_scope = ?, participation_requirement = ?, schedule_json = ?,
                            description = ?, image_url = ?, contact_name = ?, contact_qr_code_url = ?, tags = ?, status = ?,
                            fee_mode = ?, entry_fee_minor = ?, currency = ?,
                            featured = ?, sort = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and uuid = ? and competition_no = ? and status = ? and deleted = 0
                        """,
                competition.getLocale(),
                competition.getTitle(),
                competition.getShortName(),
                competition.getCategory(),
                competition.getLevel(),
                competition.getCompetitionLevel(),
                competition.getOrganizer(),
                competition.getOrganizersJson(),
                competition.getRegistrationStart(),
                competition.getRegistrationEnd(),
                competition.getCompetitionStart(),
                competition.getCompetitionEnd(),
                competition.getLocation(),
                competition.getParticipationScope(),
                competition.getParticipationRequirement(),
                competition.getScheduleJson(),
                competition.getDescription(),
                competition.getImageUrl(),
                competition.getContactName(),
                competition.getContactQrCodeUrl(),
                competition.getTags(),
                command.persistedStatus(),
                competition.getFeeMode(),
                competition.getEntryFeeMinor(),
                competition.getCurrency(),
                Boolean.TRUE.equals(competition.getFeatured()) ? 1 : 0,
                competition.getSort(),
                actor.userId(),
                actor.userUuid(),
                command.updatedAt(),
                command.id(),
                command.competitionUuid(),
                command.competitionNo(),
                command.expectedStatus()
        );
    }

    @Override
    public long countActiveRegistrations(Long competitionId) {
        Long count = database.queryForObject(
                "select count(1) from competition_registration where competition_id = ? and deleted = 0",
                Long.class,
                competitionId
        );
        return count == null ? 0L : count;
    }

    @Override
    public int softDeleteCompetition(CompetitionDelete command) {
        Actor actor = command.actor();
        return database.update(
                """
                        update aiadc_competition
                        set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and uuid = ? and competition_no = ? and status = ? and deleted = 0
                        """,
                actor.userId(),
                actor.userUuid(),
                command.updatedAt(),
                command.id(),
                command.competitionUuid(),
                command.competitionNo(),
                command.expectedStatus()
        );
    }

    @Override
    public boolean existsActiveCompetitionNo(String competitionNo) {
        Long count = database.queryForObject(
                "select count(1) from aiadc_competition where competition_no = ? and deleted = 0",
                Long.class,
                competitionNo
        );
        return count != null && count > 0;
    }

    private static <T> T first(List<T> values) {
        return values.isEmpty() ? null : values.get(0);
    }

    private static String competitionSelect() {
        return """
                select id, uuid, competition_no as competitionNo, code, locale, title, short_name as shortName,
                       category, level, competition_level as competitionLevel, organizer, organizers_json as organizersJson,
                       registration_start as registrationStart, registration_end as registrationEnd,
                       competition_start as competitionStart, competition_end as competitionEnd,
                       location, participation_scope as participationScope, participation_requirement as participationRequirement,
                       schedule_json as scheduleJson, description, image_url as imageUrl,
                       contact_name as contactName, contact_qr_code_url as contactQrCodeUrl,
                       tags, status, fee_mode as feeMode, entry_fee_minor as entryFeeMinor, currency, featured, sort,
                       created_at as createdAt, updated_at as updatedAt
                """;
    }
}

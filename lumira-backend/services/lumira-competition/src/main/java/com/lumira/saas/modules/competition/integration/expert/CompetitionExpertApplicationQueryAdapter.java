package com.lumira.saas.modules.competition.integration.expert;

import com.lumira.api.competition.CompetitionExpertApplicationQueryPort;
import com.lumira.saas.modules.competition.infrastructure.persistence.CompetitionSqlOperations;
import java.util.List;
import java.util.Optional;

/** Competition-owned adapter exposing only the published application contract. */
public class CompetitionExpertApplicationQueryAdapter implements CompetitionExpertApplicationQueryPort {
    private final CompetitionSqlOperations database;

    public CompetitionExpertApplicationQueryAdapter(CompetitionSqlOperations database) {
        this.database = database;
    }

    @Override
    public Optional<PublishedApplication> findPublishedApplication(String competitionUuid) {
        Long count = database.queryForObject(
                "select count(1) from aiadc_competition where uuid = ? and status = 'published' and deleted = 0",
                Long.class,
                competitionUuid
        );
        if (count == null || count == 0) {
            return Optional.empty();
        }
        List<Field> fields = database.query("""
                select item.item_key as itemKey, item.title, item.content_json as contentJson,
                       item.required_flag as requiredFlag, item.enabled
                from competition_config_item item
                join competition_config_set config_set
                  on config_set.id = item.config_set_id
                 and config_set.competition_uuid = item.competition_uuid
                 and config_set.status = 'PUBLISHED'
                 and config_set.deleted = 0
                where item.competition_uuid = ?
                  and item.item_type = 'EXPERT_FIELD'
                  and item.deleted = 0
                order by item.sort_order asc, item.id asc
                """, (row, rowNumber) -> new Field(
                stringValue(row.getObject("itemKey")),
                stringValue(row.getObject("title")),
                stringValue(row.getObject("contentJson")),
                numberValue(row.getObject("requiredFlag")) != 0,
                numberValue(row.getObject("enabled")) != 0
        ), competitionUuid);
        return Optional.of(new PublishedApplication(fields));
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int numberValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? 0 : Integer.parseInt(String.valueOf(value));
    }
}

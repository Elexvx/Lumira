package com.lumira.saas.modules.competition.infrastructure;

import com.lumira.saas.modules.competition.infrastructure.persistence.BeanPropertyRowMapper;
import com.lumira.saas.modules.competition.infrastructure.persistence.CompetitionSqlOperations;
import com.lumira.saas.modules.competition.repository.CompetitionSettingsRepository;
import com.lumira.saas.modules.competition.vo.CompetitionVO;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/** MyBatis/JDBC adapter for versioned competition settings and audit records. */
@Repository
public class JdbcCompetitionSettingsRepository implements CompetitionSettingsRepository {

    private final CompetitionSqlOperations database;

    public JdbcCompetitionSettingsRepository(CompetitionSqlOperations database) {
        this.database = database;
    }

    @Override
    public CompetitionVO.ConfigSet findCurrentConfigSet(String competitionUuid) {
        return first(database.query(
                configSetSelect() + """
                        from competition_config_set
                        where competition_uuid = ? and status in ('DRAFT', 'PUBLISHED') and deleted = 0
                        order by id desc
                        limit 1
                        """,
                new BeanPropertyRowMapper<>(CompetitionVO.ConfigSet.class),
                competitionUuid
        ));
    }

    @Override
    public ConfigSetCreateResult createConfigSet(ConfigSetCreate command) {
        Actor actor = command.actor();
        int inserted = database.update(
                """
                        insert into competition_config_set (
                            competition_uuid, version, status, created_by_uuid, created_by, updated_by, updated_by_uuid, deleted
                        ) values (?, 1, 'DRAFT', ?, ?, ?, ?, 0)
                        """,
                command.competitionUuid(),
                actor.userUuid(),
                actor.userId(),
                actor.userId(),
                actor.userUuid()
        );
        Long configSetId = inserted == 1 ? database.queryForObject("select last_insert_id()", Long.class) : null;
        return new ConfigSetCreateResult(configSetId, inserted);
    }

    @Override
    public boolean hasActiveConfigItems(Long configSetId) {
        Long count = database.queryForObject(
                "select count(1) from competition_config_item where config_set_id = ? and deleted = 0",
                Long.class,
                configSetId
        );
        return count != null && count > 0;
    }

    @Override
    public int seedDefaultConfigItems(ConfigTemplateSeed command) {
        Actor actor = command.actor();
        return database.update(
                """
                        insert into competition_config_item (
                            competition_uuid, config_set_id, item_type, item_key, title, content_json, content_text,
                            sort_order, required_flag, enabled, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        )
                        select ?, ?, item_type, item_key, title,
                               case
                                   when item_type in ('REQUIRED_FILE', 'STAGE_MATERIAL')
                                       then json_set(coalesce(nullif(content_json, ''), '{}'), '$.storageKey', ?)
                                   else content_json
                               end,
                               content_text,
                               sort_order, required_flag, enabled, ?, ?, ?, ?, 0
                        from competition_config_item_template
                        where template_code = 'DEFAULT' and deleted = 0
                        order by sort_order asc, id asc
                        """,
                command.competitionUuid(),
                command.configSetId(),
                command.storageKey(),
                actor.userId(),
                actor.userUuid(),
                actor.userId(),
                actor.userUuid()
        );
    }

    @Override
    public CompetitionVO.ConfigSet findConfigSet(Long configSetId) {
        return first(database.query(
                configSetSelect() + " from competition_config_set where id = ? and deleted = 0 limit 1",
                new BeanPropertyRowMapper<>(CompetitionVO.ConfigSet.class),
                configSetId
        ));
    }

    @Override
    public List<CompetitionVO.ConfigItem> findConfigItems(String competitionUuid, Long configSetId, Set<String> itemTypes) {
        if (itemTypes == null || itemTypes.isEmpty()) {
            return List.of();
        }
        return database.query(
                configItemSelect() + " from competition_config_item where competition_uuid = ? and config_set_id = ? and item_type in ("
                        + placeholders(itemTypes.size()) + ") and deleted = 0 order by sort_order asc, id asc",
                new BeanPropertyRowMapper<>(CompetitionVO.ConfigItem.class),
                concat(new Object[]{competitionUuid, configSetId}, itemTypes.toArray())
        );
    }

    @Override
    public int insertConfigItem(ConfigItemInsert command) {
        Actor actor = command.actor();
        return database.update(
                """
                        insert into competition_config_item (
                            competition_uuid, config_set_id, item_type, item_key, title, content_json, content_text,
                            sort_order, required_flag, enabled, created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                command.competitionUuid(),
                command.configSetId(),
                command.itemType(),
                command.itemKey(),
                command.title(),
                command.contentJson(),
                command.contentText(),
                command.sortOrder(),
                command.required() ? 1 : 0,
                command.enabled() ? 1 : 0,
                actor.userId(),
                actor.userUuid(),
                actor.userId(),
                actor.userUuid()
        );
    }

    @Override
    public int updateConfigItem(ConfigItemUpdate command) {
        Actor actor = command.actor();
        return database.update(
                """
                        update competition_config_item
                        set title = ?, content_json = ?, content_text = ?, sort_order = ?,
                            required_flag = ?, enabled = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and competition_uuid = ? and config_set_id = ? and item_type = ? and item_key = ? and deleted = 0
                        """,
                command.title(),
                command.contentJson(),
                command.contentText(),
                command.sortOrder(),
                command.required() ? 1 : 0,
                command.enabled() ? 1 : 0,
                actor.userId(),
                actor.userUuid(),
                command.updatedAt(),
                command.id(),
                command.competitionUuid(),
                command.configSetId(),
                command.itemType(),
                command.itemKey()
        );
    }

    @Override
    public void purgeConfigItemTombstones(Long configSetId, List<ConfigItemIdentity> identities) {
        for (ConfigItemIdentity identity : identities) {
            database.update(
                    "delete from competition_config_item where config_set_id = ? and item_type = ? and item_key = ? and deleted = 1",
                    configSetId,
                    identity.itemType(),
                    identity.itemKey()
            );
        }
    }

    @Override
    public int softDeleteConfigItems(ConfigItemSoftDelete command) {
        if (command.itemIds() == null || command.itemIds().isEmpty()) {
            return 0;
        }
        Actor actor = command.actor();
        List<Object> params = new ArrayList<>();
        params.add(actor.userId());
        params.add(actor.userUuid());
        params.add(command.updatedAt());
        params.add(command.competitionUuid());
        params.add(command.configSetId());
        params.addAll(command.itemIds());
        return database.update(
                """
                        update competition_config_item
                        set deleted = 1, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where competition_uuid = ?
                          and config_set_id = ?
                          and deleted = 0
                          and id in (
                        """ + placeholders(command.itemIds().size()) + ")",
                params.toArray()
        );
    }

    @Override
    public int publishConfigSet(ConfigSetPublish command) {
        Actor actor = command.actor();
        return database.update(
                """
                        update competition_config_set
                        set status = 'PUBLISHED', published_at = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ? and competition_uuid = ? and status = ? and deleted = 0
                        """,
                command.publishedAt(),
                actor.userId(),
                actor.userUuid(),
                command.updatedAt(),
                command.configSetId(),
                command.competitionUuid(),
                command.expectedStatus()
        );
    }

    @Override
    public int archiveOtherConfigSets(ConfigSetArchive command) {
        Actor actor = command.actor();
        return database.update(
                """
                        update competition_config_set
                        set status = 'ARCHIVED', updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where competition_uuid = ? and id <> ? and deleted = 0
                        """,
                actor.userId(),
                actor.userUuid(),
                command.updatedAt(),
                command.competitionUuid(),
                command.currentConfigSetId()
        );
    }

    @Override
    public int insertAuditRecord(ConfigAuditRecord command) {
        Actor actor = command.actor();
        return database.update(
                """
                        insert into competition_config_audit (
                            competition_uuid, operator_user_id, operator_user_uuid, action, module, detail_message,
                            created_by, created_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                command.competitionUuid(),
                actor.userId(),
                actor.userUuid(),
                command.action(),
                command.module(),
                command.detail(),
                actor.userId(),
                actor.userUuid()
        );
    }

    private static <T> T first(List<T> values) {
        return values.isEmpty() ? null : values.getFirst();
    }

    private static String configSetSelect() {
        return """
                select id, competition_uuid as competitionUuid, version, status, published_at as publishedAt,
                       created_at as createdAt, updated_at as updatedAt
                """;
    }

    private static String configItemSelect() {
        return """
                select id, competition_uuid as competitionUuid, config_set_id as configSetId, item_type as itemType,
                       item_key as itemKey, title, content_json as contentJson, content_text as contentText,
                       sort_order as sortOrder, required_flag as requiredFlag, enabled,
                       created_at as createdAt, updated_at as updatedAt
                """;
    }

    private static String placeholders(int count) {
        return java.util.stream.IntStream.range(0, count).mapToObj(index -> "?").collect(Collectors.joining(","));
    }

    private static Object[] concat(Object[] first, Object[] second) {
        Object[] result = new Object[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}

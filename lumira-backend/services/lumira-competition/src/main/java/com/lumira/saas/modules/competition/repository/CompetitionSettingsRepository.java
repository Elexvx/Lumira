package com.lumira.saas.modules.competition.repository;

import com.lumira.saas.modules.competition.vo.CompetitionVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Persistence boundary for versioned competition configuration and its audit trail.
 *
 * <p>Authorization, validation, and module-specific orchestration stay in the
 * application service. This port intentionally owns only configuration-set and
 * configuration-item storage concerns.</p>
 */
public interface CompetitionSettingsRepository {

    CompetitionVO.ConfigSet findCurrentConfigSet(String competitionUuid);

    ConfigSetCreateResult createConfigSet(ConfigSetCreate command);

    boolean hasActiveConfigItems(Long configSetId);

    int seedDefaultConfigItems(ConfigTemplateSeed command);

    CompetitionVO.ConfigSet findConfigSet(Long configSetId);

    List<CompetitionVO.ConfigItem> findConfigItems(String competitionUuid, Long configSetId, Set<String> itemTypes);

    int insertConfigItem(ConfigItemInsert command);

    int updateConfigItem(ConfigItemUpdate command);

    void purgeConfigItemTombstones(Long configSetId, List<ConfigItemIdentity> identities);

    int softDeleteConfigItems(ConfigItemSoftDelete command);

    int publishConfigSet(ConfigSetPublish command);

    int archiveOtherConfigSets(ConfigSetArchive command);

    int insertAuditRecord(ConfigAuditRecord command);

    record Actor(Long userId, String userUuid) {
    }

    record ConfigSetCreate(String competitionUuid, Actor actor) {
    }

    record ConfigSetCreateResult(Long configSetId, int writeCount) {
    }

    record ConfigTemplateSeed(String competitionUuid, Long configSetId, Actor actor) {
    }

    record ConfigItemInsert(
            String competitionUuid,
            Long configSetId,
            String itemType,
            String itemKey,
            String title,
            String contentJson,
            String contentText,
            int sortOrder,
            boolean required,
            boolean enabled,
            Actor actor
    ) {
    }

    record ConfigItemUpdate(
            Long id,
            String competitionUuid,
            Long configSetId,
            String itemType,
            String itemKey,
            String title,
            String contentJson,
            String contentText,
            int sortOrder,
            boolean required,
            boolean enabled,
            Actor actor,
            LocalDateTime updatedAt
    ) {
    }

    record ConfigItemIdentity(String itemType, String itemKey) {
    }

    record ConfigItemSoftDelete(
            String competitionUuid,
            Long configSetId,
            List<Long> itemIds,
            Actor actor,
            LocalDateTime updatedAt
    ) {
    }

    record ConfigSetPublish(
            Long configSetId,
            String competitionUuid,
            String expectedStatus,
            Actor actor,
            LocalDateTime publishedAt,
            LocalDateTime updatedAt
    ) {
    }

    record ConfigSetArchive(
            String competitionUuid,
            Long currentConfigSetId,
            Actor actor,
            LocalDateTime updatedAt
    ) {
    }

    record ConfigAuditRecord(
            String competitionUuid,
            String action,
            String module,
            String detail,
            Actor actor
    ) {
    }
}

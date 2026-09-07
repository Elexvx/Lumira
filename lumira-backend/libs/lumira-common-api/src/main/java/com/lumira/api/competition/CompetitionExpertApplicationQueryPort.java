package com.lumira.api.competition;

import java.util.List;
import java.util.Optional;

/** Competition-owned read contract used when validating Expert applications. */
public interface CompetitionExpertApplicationQueryPort {
    Optional<PublishedApplication> findPublishedApplication(String competitionUuid);

    record PublishedApplication(List<Field> fields) { }

    record Field(String itemKey, String title, String contentJson, boolean required, boolean enabled) { }
}

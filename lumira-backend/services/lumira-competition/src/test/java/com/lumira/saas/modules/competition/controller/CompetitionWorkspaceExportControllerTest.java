package com.lumira.saas.modules.competition.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CompetitionWorkspaceExportControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void taskScopeReadsCompetitionIdFromNestedAsyncRequest() {
        String payload = "{\"request\":{\"competitionId\":42,\"status\":\"CONFIRMED\"},\"fileName\":\"export.xlsx\"}";

        assertThat(CompetitionWorkspaceExportController.belongsToCompetition(payload, 42L, objectMapper))
                .isTrue();
    }

    @Test
    void taskScopeRejectsWrongOrUnscopedPayload() {
        String wrongCompetition = "{\"request\":{\"competitionId\":43}}";
        String unscopedPayload = "{\"competitionId\":42}";

        assertThat(CompetitionWorkspaceExportController.belongsToCompetition(wrongCompetition, 42L, objectMapper))
                .isFalse();
        assertThat(CompetitionWorkspaceExportController.belongsToCompetition(unscopedPayload, 42L, objectMapper))
                .isFalse();
    }
}

package com.lumira.saas.modules.activity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ActivityRegistrationSchemaContractTest {

    @Test
    void bootstrapAndManualUpgradeContainCustomRegistrationColumns() throws IOException {
        String bootstrap = Files.readString(findRepositoryFile("lumira-backend/sql/saas.sql"));
        String upgrade = Files.readString(findRepositoryFile("lumira-backend/sql/upgrade-activity-custom-registration-form-v1.sql"));
        String deploymentMigration = Files.readString(findRepositoryFile(
                "deploy/migrations/V202608160002__add_activity_custom_registration_forms.sql"
        ));

        assertThat(bootstrap)
                .contains("`registration_form_json` longtext")
                .contains("`form_data_json` longtext");
        assertThat(upgrade)
                .contains("information_schema.columns")
                .contains("column_name = 'registration_form_json'")
                .contains("column_name = 'form_data_json'")
                .contains("WHERE `registration_form_json` IS NULL");
        assertThat(deploymentMigration)
                .contains("column_name = 'registration_form_json'")
                .contains("column_name = 'form_data_json'")
                .contains("WHERE `registration_form_json` IS NULL");
    }

    private Path findRepositoryFile(String relativePath) {
        Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Repository file is unavailable: " + relativePath);
    }
}

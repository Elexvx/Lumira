package com.lumira.saas.infrastructure.db;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultAdminPasswordBaselineTest {

    private static final String INITIAL_ADMIN_PASSWORD = "123456";
    private static final Pattern ADMIN_PASSWORD_CREDENTIAL_PATTERN = Pattern.compile(
            "\\((?:\\d+,)?1001,'PASSWORD','([^']+)','BCRYPT',1,"
    );

    @Test
    void freshFlywayBaselineDoesNotUseInitialAdminPassword() throws IOException {
        String hash = extractAdminPasswordHash(Path.of("src/main/resources/db/migration/V1__baseline.sql"));

        assertFalse(new BCryptPasswordEncoder().matches(INITIAL_ADMIN_PASSWORD, hash));
    }

    @Test
    void referenceSaasSqlUsesSameNonDefaultAdminPasswordHash() throws IOException {
        String baselineHash = extractAdminPasswordHash(Path.of("src/main/resources/db/migration/V1__baseline.sql"));
        String referenceHash = extractAdminPasswordHash(Path.of("../../sql/saas.sql"));

        assertEquals(baselineHash, referenceHash);
        assertFalse(new BCryptPasswordEncoder().matches(INITIAL_ADMIN_PASSWORD, referenceHash));
    }

    private String extractAdminPasswordHash(Path path) throws IOException {
        String sql = Files.readString(path, StandardCharsets.UTF_8);
        var matcher = ADMIN_PASSWORD_CREDENTIAL_PATTERN.matcher(sql);
        assertTrue(matcher.find(), () -> "admin PASSWORD credential seed not found in " + path);
        return matcher.group(1);
    }
}

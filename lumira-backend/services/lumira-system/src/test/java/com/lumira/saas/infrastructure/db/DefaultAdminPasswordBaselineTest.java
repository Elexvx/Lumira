package com.lumira.saas.infrastructure.db;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultAdminPasswordBaselineTest {

    private static final Pattern BCRYPT_HASH_PATTERN = Pattern.compile("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");

    @Test
    void consolidatedSaasSqlSeedsDefaultAdminPassword() throws IOException {
        String referenceSql = Files.readString(resolvePath(
                "../../sql/saas.sql",
                "sql/saas.sql"), StandardCharsets.UTF_8);

        assertTrue(referenceSql.contains("INSERT INTO `sys_user`"));
        assertTrue(referenceSql.contains("VALUES (1001, 'admin'"));
        assertTrue(referenceSql.contains("INSERT INTO `iam_user_identity`"));
        assertTrue(referenceSql.contains("INSERT INTO `iam_user_credential`"));
        assertTrue(referenceSql.contains("'PASSWORD'"));
        assertTrue(referenceSql.contains("'BCRYPT'"));
        assertTrue(hasHashMatchingInitialPassword(referenceSql));
    }

    private static boolean hasHashMatchingInitialPassword(String referenceSql) {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        Matcher matcher = BCRYPT_HASH_PATTERN.matcher(referenceSql);
        while (matcher.find()) {
            if (passwordEncoder.matches("123456", matcher.group())) {
                return true;
            }
        }
        return false;
    }

    private static Path resolvePath(String... candidates) {
        for (String candidate : candidates) {
            Path path = Path.of(candidate);
            if (Files.exists(path)) {
                return path;
            }
        }
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            for (String candidate : candidates) {
                Path path = current.resolve(candidate);
                if (Files.exists(path)) {
                    return path;
                }
            }
            current = current.getParent();
        }
        return Path.of(candidates[0]);
    }
}

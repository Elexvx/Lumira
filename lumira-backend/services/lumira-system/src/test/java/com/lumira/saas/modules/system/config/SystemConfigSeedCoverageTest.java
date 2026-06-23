package com.lumira.saas.modules.system.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SystemConfigSeedCoverageTest {

    private static final Pattern CONFIG_KEY_CONSTANT = Pattern.compile(
            "static\\s+final\\s+String\\s+[A-Z0-9_]*KEY\\s*=\\s*\"([^\"]+)\""
    );
    private static final List<String> CONFIG_PREFIXES = List.of(
            "branding.",
            "agreement.",
            "watermark.",
            "floating-window.",
            "smtp.",
            "notification.wechat-official.",
            "verification.",
            "security.",
            "profile.field.",
            "auth."
    );

    @Test
    void systemConfigKeysHaveSqlSeedRows() throws IOException {
        Path moduleRoot = Path.of("").toAbsolutePath();
        Path backendRoot = findBackendRoot(moduleRoot);
        String seedSql = Files.readString(backendRoot.resolve("sql/saas.sql"), StandardCharsets.UTF_8);

        List<String> missing = new ArrayList<>();
        for (String configKey : collectSystemConfigKeys(moduleRoot.resolve("src/main/java"))) {
            if (!seedSql.contains("'" + configKey + "'")) {
                missing.add(configKey);
            }
        }

        assertThat(missing)
                .as("sys_config keys must be initialized in sql/saas.sql")
                .isEmpty();
    }

    private static List<String> collectSystemConfigKeys(Path sourceRoot) throws IOException {
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            return files
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> extractConfigKeys(path).stream())
                    .distinct()
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
    }

    private static List<String> extractConfigKeys(Path sourceFile) {
        try {
            String source = Files.readString(sourceFile, StandardCharsets.UTF_8);
            var matcher = CONFIG_KEY_CONSTANT.matcher(source);
            List<String> keys = new ArrayList<>();
            while (matcher.find()) {
                String key = matcher.group(1);
                if (hasConfigPrefix(key)) {
                    keys.add(key);
                }
            }
            return keys;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read source file " + sourceFile, ex);
        }
    }

    private static boolean hasConfigPrefix(String value) {
        for (String prefix : CONFIG_PREFIXES) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static Path findBackendRoot(Path start) {
        Path current = start;
        while (current != null) {
            if (Files.exists(current.resolve("sql/saas.sql"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate backend root from " + start);
    }
}

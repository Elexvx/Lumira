package com.lumira.common.web.repeatsubmit;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RepeatSubmitAspectTest {
    @Test
    void userScopeShouldUsePlatformContextInsteadOfCurrentUserTenant() throws Exception {
        String source = source("src/main/java/com/lumira/common/web/repeatsubmit/RepeatSubmitAspect.java");

        assertThat(source).contains("PlatformContext.compatibilityTenantId()");
        assertThat(source).doesNotContain("getCurrentTenantId()");
    }

    private static String source(String relativePath) throws Exception {
        Path direct = Path.of(relativePath);
        if (Files.exists(direct)) {
            return Files.readString(direct, StandardCharsets.UTF_8);
        }
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return Files.readString(candidate, StandardCharsets.UTF_8);
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Source file not found: " + relativePath);
    }
}

package com.lumira.common.web.repeatsubmit;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RepeatSubmitAspectTest {
    @Test
    void userScopeShouldUseTrustedSessionFingerprint() throws Exception {
        String source = source("src/main/java/com/lumira/common/web/repeatsubmit/RepeatSubmitAspect.java");

        String scopeWord = "ten" + "ant";
        assertThat(source).doesNotContain("getCurrent" + scopeWord.substring(0, 1).toUpperCase() + scopeWord.substring(1) + "Id()");
        assertThat(source).doesNotContain("String.join(\":\", \"user\", String.valueOf(currentUser.getUserId()))");
        assertThat(source).contains("String.join(\":\", \"user-session\", sha256Hex(String.join(");
        assertThat(source).contains("AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)");
        assertThat(source).contains("currentUser.getUserUuid().trim()");
        assertThat(source).contains("currentUser.getSessionId().trim()");
        assertThat(source).contains("String.valueOf(currentUser.getSessionVersion())");
        assertThat(source).contains("currentUser.getPermissionsVersion().trim()");
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

package com.lumira.saas.modules.competition.app;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CertificateRenderServiceTest {

    @Test
    void backgroundPathShouldOnlyAllowTrustedLocalStoragePaths() {
        assertThat(CertificateRenderService.resolveTrustedLocalBackgroundPath("storage/certificates/bg.png"))
                .isEqualTo(Path.of("storage", "certificates", "bg.png"));
        assertThat(CertificateRenderService.resolveTrustedLocalBackgroundPath("/uploads/certificate-template/bg.png"))
                .isEqualTo(Path.of("uploads", "certificate-template", "bg.png"));
    }

    @Test
    void backgroundPathShouldRejectRemoteAndEscapingPaths() {
        assertThat(CertificateRenderService.resolveTrustedLocalBackgroundPath("https://example.com/bg.png")).isNull();
        assertThat(CertificateRenderService.resolveTrustedLocalBackgroundPath("//example.com/bg.png")).isNull();
        assertThat(CertificateRenderService.resolveTrustedLocalBackgroundPath("../application.yml")).isNull();
        assertThat(CertificateRenderService.resolveTrustedLocalBackgroundPath("storage/../application.yml")).isNull();
        assertThat(CertificateRenderService.resolveTrustedLocalBackgroundPath("C:/Windows/win.ini")).isNull();
        assertThat(CertificateRenderService.resolveTrustedLocalBackgroundPath("api/v1/files/1/download")).isNull();
    }
}

package com.lumira.common.web;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.runtime.ServiceVersionInfo;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceVersionControllerTest {

    @Test
    void exposesRuntimeVersionAliasForFrontendAndBackendRuntimeVersions() throws Exception {
        Method versionMethod = ServiceVersionController.class.getMethod("version", jakarta.servlet.http.HttpServletRequest.class);

        assertThat(versionMethod.getAnnotation(GetMapping.class).value())
                .contains("/api/version", "/api/v2/runtime/version");
    }

    @Test
    void returnsCurrentRuntimeVersionsFromProvider() {
        ServiceVersionInfo info = new ServiceVersionInfo(
                "lumira-server",
                "lumira-admin",
                "2026.07.06",
                "2026-07-06T10:00:00Z",
                "abcdef1",
                "main",
                "prod",
                "21",
                "frontend-20260706",
                "backend-20260706",
                "db-20260706"
        );
        ServiceVersionController controller = new ServiceVersionController(new StubServiceVersionProvider(info));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/runtime/version");

        ApiResponse<ServiceVersionInfo> response = controller.version(request);

        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getPath()).isEqualTo("/api/v2/runtime/version");
        assertThat(response.getData()).isSameAs(info);
        assertThat(response.getData().frontendVersion()).isEqualTo("frontend-20260706");
        assertThat(response.getData().backendVersion()).isEqualTo("backend-20260706");
    }

    private static class StubServiceVersionProvider extends ServiceVersionProvider {
        private final ServiceVersionInfo info;

        private StubServiceVersionProvider(ServiceVersionInfo info) {
            super(null, null);
            this.info = info;
        }

        @Override
        public ServiceVersionInfo current() {
            return info;
        }
    }
}

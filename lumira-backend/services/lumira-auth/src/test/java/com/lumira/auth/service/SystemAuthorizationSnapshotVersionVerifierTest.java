package com.lumira.auth.service;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.port.AuthorizationVersionPort;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthorizationSnapshotVersionVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemAuthorizationSnapshotVersionVerifierTest {

    @Test
    void splitRuntimeComparesAgainstTheSystemAuthorizationBoundary() {
        AuthorizationVersionPort systemInternalApi = mock(AuthorizationVersionPort.class);
        SystemAuthorizationSnapshotVersionVerifier verifier = new SystemAuthorizationSnapshotVersionVerifier(systemInternalApi);
        when(systemInternalApi.isPermissionSnapshotVersionCurrent("v7:data-scope-cache-v4")).thenReturn(Boolean.TRUE);

        assertThat(verifier.isCurrent("v7:data-scope-cache-v4")).isTrue();

        verify(systemInternalApi).isPermissionSnapshotVersionCurrent("v7:data-scope-cache-v4");
    }

    @Test
    void splitRuntimeFailsClosedWhenTheSystemAuthorizationBoundaryIsUnavailable() {
        AuthorizationVersionPort systemInternalApi = mock(AuthorizationVersionPort.class);
        SystemAuthorizationSnapshotVersionVerifier verifier = new SystemAuthorizationSnapshotVersionVerifier(systemInternalApi);
        when(systemInternalApi.isPermissionSnapshotVersionCurrent("v7:data-scope-cache-v4")).thenReturn(null);

        assertThatThrownBy(() -> verifier.isCurrent("v7:data-scope-cache-v4"))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).getErrorCode())
                .isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }

    @Test
    void splitRuntimeRegistersTheRemoteVerifierWhileMonolithDoesNotRegisterIt() {
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withUserConfiguration(TestConfiguration.class);

        contextRunner.withPropertyValues("lumira.monolith=false").run(context ->
                assertThat(context.getBeansOfType(AuthorizationSnapshotVersionVerifier.class)).hasSize(1)
        );
        contextRunner.withPropertyValues("lumira.monolith=true").run(context ->
                assertThat(context.getBeansOfType(AuthorizationSnapshotVersionVerifier.class)).isEmpty()
        );
    }

    @Configuration(proxyBeanMethods = false)
    @Import(SystemAuthorizationSnapshotVersionVerifier.class)
    static class TestConfiguration {

        @Bean
        SystemInternalApi systemInternalApi() {
            return mock(SystemInternalApi.class);
        }
    }
}

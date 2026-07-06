package com.lumira.auth.service;

import com.lumira.api.auth.CurrentUserDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthInternalApiServiceTest {

    @Test
    void currentUserNormalizesSessionAndForwardsExpectedClaims() {
        AuthAppService authAppService = mock(AuthAppService.class);
        AuthInternalApiService service = new AuthInternalApiService(authAppService);
        CurrentUserDTO currentUser = new CurrentUserDTO(
                42L,
                "user-uuid",
                "alice",
                "Alice",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                "session-1",
                "v1",
                3,
                List.of("system:file:view"),
                List.of(1L),
                null,
                List.of(),
                List.of(),
                List.of(),
                false,
                "/"
        );
        when(authAppService.currentUserBySessionId("session-1", 42L, "user-uuid", 3, "v1", 9L)).thenReturn(currentUser);

        CurrentUserDTO result = service.currentUser(" session-1 ", 42L, " user-uuid ", 3, " v1 ", 9L);

        assertThat(result).isSameAs(currentUser);
        verify(authAppService).currentUserBySessionId("session-1", 42L, "user-uuid", 3, "v1", 9L);
    }

    @Test
    void currentUserRejectsBlankSessionBeforeServiceLookup() {
        AuthAppService authAppService = mock(AuthAppService.class);
        AuthInternalApiService service = new AuthInternalApiService(authAppService);

        assertThatThrownBy(() -> service.currentUser(" ", 42L, "user-uuid", 3, "v1", null))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verify(authAppService, never()).currentUserBySessionId(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void currentUserRejectsUnsafeSessionBeforeServiceLookup() {
        AuthAppService authAppService = mock(AuthAppService.class);
        AuthInternalApiService service = new AuthInternalApiService(authAppService);

        assertThatThrownBy(() -> service.currentUser("../session", 42L, "user-uuid", 3, "v1", null))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verify(authAppService, never()).currentUserBySessionId(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void currentUserRejectsInvalidExpectedUserBeforeServiceLookup() {
        AuthAppService authAppService = mock(AuthAppService.class);
        AuthInternalApiService service = new AuthInternalApiService(authAppService);

        assertThatThrownBy(() -> service.currentUser("session-1", 0L, "user-uuid", 3, "v1", null))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verify(authAppService, never()).currentUserBySessionId(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void currentUserRejectsInvalidExpectedSessionVersionBeforeServiceLookup() {
        AuthAppService authAppService = mock(AuthAppService.class);
        AuthInternalApiService service = new AuthInternalApiService(authAppService);

        assertThatThrownBy(() -> service.currentUser("session-1", 42L, "user-uuid", 0, "v1", null))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verify(authAppService, never()).currentUserBySessionId(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void currentUserRejectsMissingExpectedUserUuidBeforeServiceLookup() {
        AuthAppService authAppService = mock(AuthAppService.class);
        AuthInternalApiService service = new AuthInternalApiService(authAppService);

        assertThatThrownBy(() -> service.currentUser("session-1", 42L, " ", 3, "v1", null))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verify(authAppService, never()).currentUserBySessionId(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void currentUserRejectsMissingExpectedPermissionsVersionBeforeServiceLookup() {
        AuthAppService authAppService = mock(AuthAppService.class);
        AuthInternalApiService service = new AuthInternalApiService(authAppService);

        assertThatThrownBy(() -> service.currentUser("session-1", 42L, "user-uuid", 3, " ", null))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verify(authAppService, never()).currentUserBySessionId(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void currentUserRejectsInvalidExpectedSimulatedRoleBeforeServiceLookup() {
        AuthAppService authAppService = mock(AuthAppService.class);
        AuthInternalApiService service = new AuthInternalApiService(authAppService);

        assertThatThrownBy(() -> service.currentUser("session-1", 42L, "user-uuid", 3, "v1", 0L))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verify(authAppService, never()).currentUserBySessionId(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }
}

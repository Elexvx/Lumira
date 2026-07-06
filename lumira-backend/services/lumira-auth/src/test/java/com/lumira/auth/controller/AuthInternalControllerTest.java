package com.lumira.auth.controller;

import com.lumira.api.auth.CurrentUserDTO;
import com.lumira.auth.service.AuthInternalApiService;
import com.lumira.common.security.CurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthInternalControllerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void internalRequestRejectsMissingInternalServicePrincipal() {
        AuthInternalController controller = new AuthInternalController(mock(AuthInternalApiService.class));

        assertThatThrownBy(controller::requireInternalServicePrincipal)
                .isInstanceOf(com.lumira.common.exception.BizException.class);
    }

    @Test
    void internalRequestAcceptsInternalServicePrincipal() {
        AuthInternalController controller = new AuthInternalController(mock(AuthInternalApiService.class));
        authenticateInternalService();

        controller.requireInternalServicePrincipal();
    }

    @Test
    void currentUserDelegatesToLocalInternalApiService() {
        AuthInternalApiService authInternalApiService = mock(AuthInternalApiService.class);
        AuthInternalController controller = new AuthInternalController(authInternalApiService);
        authenticateInternalService();
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
        when(authInternalApiService.currentUser(" session-1 ", 42L, " user-uuid ", 3, " v1 ", 9L)).thenReturn(currentUser);

        CurrentUserDTO result = controller.currentUser(" session-1 ", 42L, " user-uuid ", 3, " v1 ", 9L);

        assertThat(result).isSameAs(currentUser);
        verify(authInternalApiService).currentUser(" session-1 ", 42L, " user-uuid ", 3, " v1 ", 9L);
    }

    @Test
    void currentUserRejectsMissingInternalServicePrincipalBeforeLocalLookup() {
        AuthInternalApiService authInternalApiService = mock(AuthInternalApiService.class);
        AuthInternalController controller = new AuthInternalController(authInternalApiService);

        assertThatThrownBy(() -> controller.currentUser("session-1", 42L, "user-uuid", 3, "v1", null))
                .isInstanceOf(com.lumira.common.exception.BizException.class);

        verify(authInternalApiService, never()).currentUser(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private void authenticateInternalService() {
        CurrentUser internalService = new CurrentUser(0L, "internal-service", null, "internal", 0, false, java.util.Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(internalService, "internal-token", java.util.Set.of())
        );
    }
}

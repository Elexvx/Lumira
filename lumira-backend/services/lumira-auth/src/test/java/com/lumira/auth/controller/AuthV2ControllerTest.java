package com.lumira.auth.controller;

import com.lumira.api.auth.CurrentUserDTO;
import com.lumira.api.auth.LoginEncryptionKeyDTO;
import com.lumira.api.auth.LoginRequest;
import com.lumira.api.auth.LoginResponseDTO;
import com.lumira.api.auth.RefreshTokenRequest;
import com.lumira.api.auth.RefreshTokenResponseDTO;
import com.lumira.api.auth.AuthBootstrapDTO;
import com.lumira.auth.service.AuthAppService;
import com.lumira.auth.service.AuthCookieService;
import com.lumira.api.system.SecuritySettingsDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthV2ControllerTest {

    private AuthAppService authAppService;
    private AuthCookieService authCookieService;
    private AuthV2Controller controller;

    @BeforeEach
    void setUp() {
        authAppService = mock(AuthAppService.class);
        authCookieService = mock(AuthCookieService.class);
        controller = new AuthV2Controller(authAppService, authCookieService);
    }

    @Test
    void loginEncryptionKey_shouldDelegateToApplicationService() {
        LoginEncryptionKeyDTO key = new LoginEncryptionKeyDTO("RSA", "key-1", "public-key");
        when(authAppService.loginEncryptionKey()).thenReturn(key);

        var response = controller.loginEncryptionKey();

        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getData()).isSameAs(key);
        verify(authAppService).loginEncryptionKey();
    }

    @Test
    void bootstrap_shouldDelegateToApplicationService() {
        CurrentUserDTO currentUser = new CurrentUserDTO(
                42L,
                "alice",
                "Alice",
                null,
                null,
                null,
                "alice@example.com",
                null,
                null,
                null,
                null,
                null,
                "zh-CN",
                "session-1",
                "perm-v1",
                1,
                List.of("dashboard:view"),
                List.of(1L),
                10L,
                List.of(10L),
                List.of(),
                List.of(),
                false,
                "/dashboard/home"
        );
        SecuritySettingsDTO securitySettings = new SecuritySettingsDTO(
                1800L,
                7200L,
                129600L,
                true,
                false,
                "IMAGE",
                10L,
                5L,
                20L,
                300L,
                60L
        );
        AuthBootstrapDTO bootstrap = new AuthBootstrapDTO(currentUser, securitySettings);
        when(authAppService.bootstrap()).thenReturn(bootstrap);

        var response = controller.bootstrap();

        assertThat(response.getData()).isSameAs(bootstrap);
        verify(authAppService).bootstrap();
    }

    @Test
    void login_shouldDelegateToApplicationServiceWithRequestContract() {
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        LoginRequest request = new LoginRequest("alice", null, "ciphertext", null, null, null);
        LoginResponseDTO loginResponse = new LoginResponseDTO();
        loginResponse.setAccessToken("access-token");
        loginResponse.setRefreshToken("refresh-token");
        when(authAppService.login(request, httpRequest)).thenReturn(loginResponse);

        var response = controller.login(request, httpRequest, httpResponse);

        assertThat(response.getData()).isSameAs(loginResponse);
        assertThat(response.getData().getRefreshToken()).isNull();
        verify(authAppService).login(request, httpRequest);
        verify(authCookieService).writeRefreshToken(httpResponse, "refresh-token");
    }

    @Test
    void currentUser_shouldDelegateToApplicationService() {
        CurrentUserDTO currentUser = new CurrentUserDTO(
                42L,
                "alice",
                "Alice",
                null,
                null,
                null,
                "alice@example.com",
                null,
                null,
                null,
                null,
                null,
                "zh-CN",
                "session-1",
                "perm-v1",
                1,
                List.of("dashboard:view"),
                List.of(1L),
                10L,
                List.of(10L),
                List.of(),
                List.of(),
                false,
                "/dashboard/home"
        );
        when(authAppService.currentUser()).thenReturn(currentUser);

        var response = controller.currentUser();

        assertThat(response.getData()).isSameAs(currentUser);
        verify(authAppService).currentUser();
    }

    @Test
    void refreshToken_shouldDelegateToApplicationService() {
        RefreshTokenRequest request = new RefreshTokenRequest("refresh-token");
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);
        RefreshTokenResponseDTO refreshResponse = new RefreshTokenResponseDTO("access", "refresh", "Bearer", 7200L);
        when(authAppService.refreshToken(request)).thenReturn(refreshResponse);

        var response = controller.refreshToken(request, httpRequest, httpResponse);

        assertThat(response.getData().accessToken()).isEqualTo("access");
        assertThat(response.getData().refreshToken()).isNull();
        verify(authAppService).refreshToken(request);
        verify(authCookieService).writeRefreshToken(httpResponse, "refresh");
    }

    @Test
    void logout_shouldDelegateToApplicationService() {
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);

        var response = controller.logout(httpRequest, httpResponse);

        assertThat(response.getData()).isTrue();
        verify(authAppService).logout(httpRequest);
        verify(authCookieService).clearRefreshToken(httpResponse);
    }

    @Test
    void keepalive_shouldReturnTrueWithoutDatabaseRoundTrip() {
        var response = controller.keepalive();

        assertThat(response.getData()).isTrue();
    }
}

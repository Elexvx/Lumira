package com.lumira.saas.modules.system.sensitive.security;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.modules.system.sensitive.app.SensitiveWordPluginStateService;
import com.lumira.saas.modules.system.sensitive.app.SensitiveWordService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;

import java.lang.reflect.Type;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SensitiveWordRequestBodyAdviceTest {

    @Test
    void afterBodyReadShouldSkipBlankUsernameBeforePluginAndScan() {
        SensitiveWordService sensitiveWordService = mock(SensitiveWordService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        SensitiveWordPluginStateService pluginStateService = mock(SensitiveWordPluginStateService.class);
        SensitiveWordRequestBodyAdvice advice = new SensitiveWordRequestBodyAdvice(
                sensitiveWordService,
                securityContextFacade,
                pluginStateService
        );
        CurrentUser currentUser = new CurrentUser(1001L, " ", "session-1", 1, true, Set.of("*"));
        Object body = new Object();
        when(securityContextFacade.getCurrentUserOrNull()).thenReturn(currentUser);

        Object result = advice.afterBodyRead(body, mock(HttpInputMessage.class), null, mock(Type.class), null);

        assertThat(result).isSameAs(body);
        verify(pluginStateService, never()).isEnabled(currentUser);
        verify(sensitiveWordService, never()).checkPayloadForSubmission(currentUser, body);
    }

    @Test
    void afterBodyReadShouldSkipMissingSessionVersionBeforePluginAndScan() {
        SensitiveWordService sensitiveWordService = mock(SensitiveWordService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        SensitiveWordPluginStateService pluginStateService = mock(SensitiveWordPluginStateService.class);
        SensitiveWordRequestBodyAdvice advice = new SensitiveWordRequestBodyAdvice(
                sensitiveWordService,
                securityContextFacade,
                pluginStateService
        );
        CurrentUser currentUser = new CurrentUser(1001L, "alice", "session-1", null, true, Set.of("*"));
        Object body = new Object();
        when(securityContextFacade.getCurrentUserOrNull()).thenReturn(currentUser);

        Object result = advice.afterBodyRead(body, mock(HttpInputMessage.class), null, mock(Type.class), null);

        assertThat(result).isSameAs(body);
        verify(pluginStateService, never()).isEnabled(currentUser);
        verify(sensitiveWordService, never()).checkPayloadForSubmission(currentUser, body);
    }

    @Test
    void afterBodyReadShouldRejectTrustedUserWhenPluginResolverIsUnavailable() {
        SensitiveWordService sensitiveWordService = mock(SensitiveWordService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        SensitiveWordPluginStateService pluginStateService = mock(SensitiveWordPluginStateService.class);
        SensitiveWordRequestBodyAdvice advice = new SensitiveWordRequestBodyAdvice(
                sensitiveWordService,
                securityContextFacade,
                pluginStateService
        );
        CurrentUser currentUser = trustedCurrentUser();
        Object body = new Object();
        when(securityContextFacade.getCurrentUserOrNull()).thenReturn(currentUser);
        when(pluginStateService.isEnabled(currentUser))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable"));

        assertThatThrownBy(() -> advice.afterBodyRead(body, mock(HttpInputMessage.class), null, mock(Type.class), null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Trusted user resolver is unavailable");
        verify(sensitiveWordService, never()).checkPayloadForSubmission(currentUser, body);
    }

    private CurrentUser trustedCurrentUser() {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(1001L);
        currentUser.setUserUuid("user-uuid-1001");
        currentUser.setUsername("alice");
        currentUser.setAuthenticated(true);
        currentUser.setSessionId("session-1");
        currentUser.setSessionVersion(1);
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setPermissions(Set.of("*"));
        return currentUser;
    }
}

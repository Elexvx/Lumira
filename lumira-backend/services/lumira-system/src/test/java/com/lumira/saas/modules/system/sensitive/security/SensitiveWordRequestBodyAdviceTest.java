package com.lumira.saas.modules.system.sensitive.security;

import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.modules.system.sensitive.app.SensitiveWordPluginStateService;
import com.lumira.saas.modules.system.sensitive.app.SensitiveWordService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;

import java.lang.reflect.Type;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
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
        CurrentUser currentUser = new CurrentUser(1001L, " ", 1001L, "session-1", 1, true, Set.of("*"));
        Object body = new Object();
        when(securityContextFacade.getCurrentUserOrNull()).thenReturn(currentUser);

        Object result = advice.afterBodyRead(body, mock(HttpInputMessage.class), null, mock(Type.class), null);

        assertThat(result).isSameAs(body);
        verify(pluginStateService, never()).isEnabled(currentUser);
        verify(sensitiveWordService, never()).checkPayload(currentUser, body);
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
        CurrentUser currentUser = new CurrentUser(1001L, "alice", 1001L, "session-1", null, true, Set.of("*"));
        Object body = new Object();
        when(securityContextFacade.getCurrentUserOrNull()).thenReturn(currentUser);

        Object result = advice.afterBodyRead(body, mock(HttpInputMessage.class), null, mock(Type.class), null);

        assertThat(result).isSameAs(body);
        verify(pluginStateService, never()).isEnabled(currentUser);
        verify(sensitiveWordService, never()).checkPayload(currentUser, body);
    }
}

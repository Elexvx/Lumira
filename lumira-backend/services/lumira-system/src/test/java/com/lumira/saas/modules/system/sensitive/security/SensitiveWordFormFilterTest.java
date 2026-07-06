package com.lumira.saas.modules.system.sensitive.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.modules.system.sensitive.app.SensitiveWordPluginStateService;
import com.lumira.saas.modules.system.sensitive.app.SensitiveWordService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SensitiveWordFormFilterTest {

    @Test
    void doFilterShouldSkipBlankUsernameBeforePluginAndScan() throws Exception {
        SensitiveWordService sensitiveWordService = mock(SensitiveWordService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        SensitiveWordPluginStateService pluginStateService = mock(SensitiveWordPluginStateService.class);
        SensitiveWordFormFilter filter = new SensitiveWordFormFilter(
                sensitiveWordService,
                securityContextFacade,
                new ObjectMapper(),
                pluginStateService
        );
        CurrentUser currentUser = new CurrentUser(1001L, " ", 1001L, "session-1", 1, true, Set.of("*"));
        when(securityContextFacade.getCurrentUserOrNull()).thenReturn(currentUser);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/content");
        request.setContentType("multipart/form-data");
        request.addParameter("title", "sensitive");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(pluginStateService, never()).isEnabled(currentUser);
        verify(sensitiveWordService, never()).checkPayload(currentUser, java.util.Map.of("title", "sensitive"));
    }

    @Test
    void doFilterShouldSkipMissingSessionVersionBeforePluginAndScan() throws Exception {
        SensitiveWordService sensitiveWordService = mock(SensitiveWordService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        SensitiveWordPluginStateService pluginStateService = mock(SensitiveWordPluginStateService.class);
        SensitiveWordFormFilter filter = new SensitiveWordFormFilter(
                sensitiveWordService,
                securityContextFacade,
                new ObjectMapper(),
                pluginStateService
        );
        CurrentUser currentUser = new CurrentUser(1001L, "alice", 1001L, "session-1", null, true, Set.of("*"));
        when(securityContextFacade.getCurrentUserOrNull()).thenReturn(currentUser);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/content");
        request.setContentType("multipart/form-data");
        request.addParameter("title", "sensitive");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(pluginStateService, never()).isEnabled(currentUser);
        verify(sensitiveWordService, never()).checkPayload(currentUser, java.util.Map.of("title", "sensitive"));
    }
}

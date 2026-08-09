package com.lumira.ai.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lumira.ai.compat.AiV2CompatibilityFacade;
import com.lumira.ai.vo.AiEmployeeVO;
import com.lumira.ai.vo.PageResponse;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AiV2ControllerTest {

    @Test
    void employeesReturnsCanonicalCompatibilityReadModelWithHistoricalJsonShape() throws Exception {
        AiV2CompatibilityFacade facade = mock(AiV2CompatibilityFacade.class);
        SecurityContextFacade securityContextFacade = trustedSecurityContext(Set.of("ai:view"));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AiV2Controller(
                facade, securityContextFacade, new PermissionGuard()
        )).build();
        PageResponse<AiEmployeeVO> page = new PageResponse<>();
        page.setPageNo(1);
        page.setPageSize(10);
        page.setTotal(1);
        page.setHasMore(false);
        page.setRecords(List.of(employee()));
        when(facade.listEmployees(any(CurrentUser.class), eq(1L), eq(10L))).thenReturn(page);

        mockMvc.perform(get("/api/v2/ai/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].username").value("assistant"))
                .andExpect(jsonPath("$.data.records[0].defaultLlmServiceTitle").value("OpenAI"))
                .andExpect(jsonPath("$.data.hasMore").value(false));
    }

    @Test
    void allExistingV2BusinessRoutesRemainReachableThroughTheCompatibilityFacade() throws Exception {
        AiV2CompatibilityFacade facade = mock(AiV2CompatibilityFacade.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AiV2Controller(
                facade, trustedSecurityContext(Set.of("*")), new PermissionGuard()
        )).build();

        List<RequestBuilder> requests = List.of(
                get("/api/v2/ai/employees"),
                get("/api/v2/ai/assistant"),
                get("/api/v2/ai/conversations"),
                get("/api/v2/ai/conversations/8/messages"),
                post("/api/v2/ai/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hello\"}"),
                get("/api/v2/ai/knowledge-bases"),
                get("/api/v2/ai/knowledge-bases/8"),
                get("/api/v2/ai/knowledge-bases/8/documents"),
                multipart("/api/v2/ai/knowledge-bases/8/documents/upload")
                        .file(new MockMultipartFile("file", "guide.txt", "text/plain", "hello".getBytes())),
                post("/api/v2/ai/knowledge-bases/8/documents/9/reindex"),
                post("/api/v2/ai/knowledge-bases/search").contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"hello\"}"),
                get("/api/v2/ai/tools"),
                post("/api/v2/ai/tools/execute").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeId\":1,\"toolCode\":\"system.user.search\"}"),
                post("/api/v2/ai/tools/propose").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeId\":1,\"toolCode\":\"system.user.search\"}"),
                post("/api/v2/ai/tools/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pendingToolCallId\":1}")
        );

        for (RequestBuilder request : requests) {
            mockMvc.perform(request).andExpect(status().isOk());
        }
    }

    @Test
    void employeesShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        AiV2CompatibilityFacade facade = mock(AiV2CompatibilityFacade.class);
        SecurityContextFacade securityContextFacade = trustedSecurityContext(Set.of("ai:view"));
        AiV2Controller controller = new AiV2Controller(
                facade,
                securityContextFacade,
                new PermissionGuard(),
                null
        );

        assertThatThrownBy(() -> controller.employees(1, 10))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user resolver is unavailable");
        verify(facade, never()).listEmployees(any(CurrentUser.class), eq(1L), eq(10L));
    }

    @Test
    void employeesShouldRejectWhenLiveUsernameIsBlank() {
        AiV2CompatibilityFacade facade = mock(AiV2CompatibilityFacade.class);
        SecurityContextFacade securityContextFacade = trustedSecurityContext(Set.of("ai:view"));
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        when(systemInternalApi.findUserIdentityById(7L))
                .thenReturn(userSnapshot(7L, "user-uuid-7", " ", "ENABLED"));
        AiV2Controller controller = new AiV2Controller(
                facade,
                securityContextFacade,
                new PermissionGuard(),
                systemInternalApi
        );

        assertThatThrownBy(() -> controller.employees(1, 10))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user username is unavailable");
        verify(facade, never()).listEmployees(any(CurrentUser.class), eq(1L), eq(10L));
    }

    private SecurityContextFacade trustedSecurityContext(Set<String> permissions) {
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        CurrentUser user = new CurrentUser(7L, "ai-user", 1001L, "s1", 1, true, permissions);
        user.setUserUuid("user-uuid-7");
        user.setPermissionsVersion("permissions-1");
        when(securityContextFacade.getCurrentUser()).thenReturn(user);
        return securityContextFacade;
    }

    private AiEmployeeVO employee() {
        AiEmployeeVO employee = new AiEmployeeVO();
        employee.setId(1L);
        employee.setUsername("assistant");
        employee.setNickname("AI Assistant");
        employee.setDefaultLlmServiceTitle("OpenAI");
        employee.setEnabled(true);
        employee.setSortOrder(0);
        employee.setCreateTime(LocalDateTime.now());
        employee.setUpdateTime(LocalDateTime.now());
        return employee;
    }

    private SystemUserSnapshotDTO userSnapshot(Long userId, String userUuid, String username, String status) {
        return new SystemUserSnapshotDTO(
                userId, userUuid, username, null, status, null, null, null, null, null, null, null, null, null, null, null
        );
    }
}

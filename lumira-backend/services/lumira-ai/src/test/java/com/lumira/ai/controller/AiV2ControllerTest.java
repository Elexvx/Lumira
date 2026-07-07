package com.lumira.ai.controller;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.ai.app.AiCommandService;
import com.lumira.ai.app.AiReadQueryService;
import com.lumira.ai.vo.AiEmployeeVO;
import com.lumira.ai.vo.PageResponse;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiV2ControllerTest {

    @Test
    void employeesReturnsStandaloneBusinessReadModel() throws Exception {
        AiReadQueryService aiReadQueryService = mock(AiReadQueryService.class);
        AiCommandService aiCommandService = mock(AiCommandService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        PermissionGuard permissionGuard = mock(PermissionGuard.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AiV2Controller(
                aiReadQueryService,
                aiCommandService,
                securityContextFacade,
                permissionGuard
        )).build();
        CurrentUser user = new CurrentUser(7L, "ai-user", 1001L, "s1", 1, true, Set.of("ai:view"));
        user.setUserUuid("user-uuid-7");
        user.setPermissionsVersion("permissions-1");
        when(securityContextFacade.getCurrentUser()).thenReturn(user);
        PageResponse<AiEmployeeVO> page = new PageResponse<>();
        page.setPageNo(1);
        page.setPageSize(10);
        page.setTotal(1);
        page.setHasMore(false);
        page.setRecords(List.of(employee()));
        when(aiReadQueryService.listEmployees(any(CurrentUser.class), eq(1L), eq(10L))).thenReturn(page);

        mockMvc.perform(get("/api/v2/ai/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].username").value("assistant"))
                .andExpect(jsonPath("$.data.records[0].defaultLlmServiceTitle").value("OpenAI"))
                .andExpect(jsonPath("$.data.hasMore").value(false));
    }

    @Test
    void employeesShouldRejectTrustedUserWhenNoTrustedResolverIsAvailableInStrictMode() {
        AiReadQueryService aiReadQueryService = mock(AiReadQueryService.class);
        AiCommandService aiCommandService = mock(AiCommandService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        CurrentUser user = new CurrentUser(7L, "ai-user", 1001L, "s1", 1, true, Set.of("ai:view"));
        user.setUserUuid("user-uuid-7");
        user.setPermissionsVersion("permissions-1");
        when(securityContextFacade.getCurrentUser()).thenReturn(user);
        AiV2Controller controller = new AiV2Controller(
                aiReadQueryService,
                aiCommandService,
                securityContextFacade,
                new PermissionGuard(),
                null
        );

        assertThatThrownBy(() -> controller.employees(1, 10))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user resolver is unavailable");
        verify(aiReadQueryService, never()).listEmployees(any(CurrentUser.class), eq(1L), eq(10L));
    }

    @Test
    void employeesShouldRejectWhenLiveUsernameIsBlank() {
        AiReadQueryService aiReadQueryService = mock(AiReadQueryService.class);
        AiCommandService aiCommandService = mock(AiCommandService.class);
        SecurityContextFacade securityContextFacade = mock(SecurityContextFacade.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        CurrentUser user = new CurrentUser(7L, "ai-user", 1001L, "s1", 1, true, Set.of("ai:view"));
        user.setUserUuid("user-uuid-7");
        user.setPermissionsVersion("permissions-1");
        when(securityContextFacade.getCurrentUser()).thenReturn(user);
        when(systemInternalApi.findUserIdentityById(7L))
                .thenReturn(userSnapshot(7L, "user-uuid-7", " ", "ENABLED"));
        AiV2Controller controller = new AiV2Controller(
                aiReadQueryService,
                aiCommandService,
                securityContextFacade,
                new PermissionGuard(),
                systemInternalApi
        );

        assertThatThrownBy(() -> controller.employees(1, 10))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user username is unavailable");
        verify(aiReadQueryService, never()).listEmployees(any(CurrentUser.class), eq(1L), eq(10L));
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
                userId,
                userUuid,
                username,
                null,
                status,
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
                null
        );
    }
}

package com.lumira.saas.modules.ai.controller;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.PermissionGuard;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.modules.ai.app.AiKnowledgeBaseAppService;
import com.lumira.saas.modules.ai.app.AiManagementAppService;
import com.lumira.saas.modules.ai.app.AiNativeToolRuntimeService;
import com.lumira.saas.modules.ai.app.AiToolOrchestrationService;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.ai.vo.AiVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiV2ControllerTest {

    private AiManagementAppService aiManagementAppService;
    private AiKnowledgeBaseAppService aiKnowledgeBaseAppService;
    private AiNativeToolRuntimeService aiNativeToolRuntimeService;
    private AiToolOrchestrationService aiToolOrchestrationService;
    private SecurityContextFacade securityContextFacade;
    private PermissionGuard permissionGuard;
    private AiV2Controller controller;

    @BeforeEach
    void setUp() {
        aiManagementAppService = mock(AiManagementAppService.class);
        aiKnowledgeBaseAppService = mock(AiKnowledgeBaseAppService.class);
        aiNativeToolRuntimeService = mock(AiNativeToolRuntimeService.class);
        aiToolOrchestrationService = mock(AiToolOrchestrationService.class);
        securityContextFacade = mock(SecurityContextFacade.class);
        permissionGuard = mock(PermissionGuard.class);
        controller = new AiV2Controller(
                aiManagementAppService,
                aiKnowledgeBaseAppService,
                aiNativeToolRuntimeService,
                aiToolOrchestrationService,
                securityContextFacade,
                permissionGuard
        );
    }

    @Test
    void employees_shouldAllowChatPermissionFallbackAndDelegate() {
        CurrentUser currentUser = currentUser("ai:chat:send");
        PageResponse<AiVO.EmployeeVO> page = new PageResponse<>();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        doThrow(new BizException(ErrorCode.FORBIDDEN, "缺少权限: ai:view"))
                .when(permissionGuard).requirePermission(currentUser, "ai:view");
        when(aiManagementAppService.listEmployees(currentUser, 1L, 20L)).thenReturn(page);

        var response = controller.employees(1L, 20L);

        assertThat(response.getData()).isSameAs(page);
        verify(permissionGuard).requirePermission(currentUser, "ai:view");
        verify(permissionGuard).requirePermission(currentUser, "ai:chat:send");
        verify(aiManagementAppService).listEmployees(currentUser, 1L, 20L);
    }

    @Test
    void chat_shouldCheckPermissionAndDelegate() {
        CurrentUser currentUser = currentUser("ai:chat:send");
        AiDTO.ChatRequest request = mock(AiDTO.ChatRequest.class);
        AiVO.ChatResponseVO responseVO = mock(AiVO.ChatResponseVO.class);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(aiManagementAppService.chat(currentUser, request)).thenReturn(responseVO);

        var response = controller.chat(request);

        assertThat(response.getData()).isSameAs(responseVO);
        verify(permissionGuard).requirePermission(currentUser, "ai:chat:send");
        verify(aiManagementAppService).chat(currentUser, request);
    }

    @Test
    void knowledgeBases_shouldPassBoundedQueryToApplicationService() {
        CurrentUser currentUser = currentUser("ai:knowledge:view");
        PageResponse<AiVO.KnowledgeBaseVO> page = new PageResponse<>();
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(aiKnowledgeBaseAppService.listKnowledgeBases(currentUser, "policy", "ENABLED", "TENANT", 2L, 50L))
                .thenReturn(page);

        var response = controller.knowledgeBases("policy", "ENABLED", "TENANT", 2L, 50L);

        assertThat(response.getData()).isSameAs(page);
        verify(permissionGuard).requirePermission(currentUser, "ai:knowledge:view");
        verify(aiKnowledgeBaseAppService).listKnowledgeBases(currentUser, "policy", "ENABLED", "TENANT", 2L, 50L);
    }

    @Test
    void uploadKnowledgeDocument_shouldDelegateToAsyncIndexingOwnerPath() {
        CurrentUser currentUser = currentUser("ai:knowledge:document:upload");
        MultipartFile file = mock(MultipartFile.class);
        AiVO.KnowledgeDocumentVO document = mock(AiVO.KnowledgeDocumentVO.class);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(aiKnowledgeBaseAppService.uploadDocument(currentUser, 10L, file)).thenReturn(document);

        var response = controller.uploadKnowledgeDocument(10L, file);

        assertThat(response.getData()).isSameAs(document);
        verify(permissionGuard).requirePermission(currentUser, "ai:knowledge:document:upload");
        verify(aiKnowledgeBaseAppService).uploadDocument(currentUser, 10L, file);
    }

    @Test
    void searchKnowledge_shouldDefaultLimitToEight() {
        CurrentUser currentUser = currentUser("ai:knowledge:query");
        AiDTO.KnowledgeSearchRequest request = mock(AiDTO.KnowledgeSearchRequest.class);
        AiVO.KnowledgeReferenceVO reference = mock(AiVO.KnowledgeReferenceVO.class);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(request.getQuery()).thenReturn("合同审批");
        when(request.getKnowledgeBaseIds()).thenReturn(List.of(10L, 11L));
        when(request.getLimit()).thenReturn(null);
        when(aiKnowledgeBaseAppService.retrieve(currentUser, "合同审批", List.of(10L, 11L), 8)).thenReturn(List.of(reference));

        var response = controller.searchKnowledge(request);

        assertThat(response.getData()).containsExactly(reference);
        verify(aiKnowledgeBaseAppService).retrieve(currentUser, "合同审批", List.of(10L, 11L), 8);
    }

    @Test
    void executeTool_shouldCheckPermissionAndDelegateToRuntimeService() {
        CurrentUser currentUser = currentUser("ai:tool:execute");
        AiDTO.ToolExecuteRequest request = mock(AiDTO.ToolExecuteRequest.class);
        AiVO.ToolExecuteResultVO result = mock(AiVO.ToolExecuteResultVO.class);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(aiNativeToolRuntimeService.execute(currentUser, request)).thenReturn(result);

        var response = controller.executeTool(request);

        assertThat(response.getData()).isSameAs(result);
        verify(permissionGuard).requirePermission(currentUser, "ai:tool:execute");
        verify(aiNativeToolRuntimeService).execute(currentUser, request);
    }

    @Test
    void confirmTool_shouldDelegateToOrchestrationService() {
        CurrentUser currentUser = currentUser("ai:tool:execute");
        AiDTO.ToolConfirmRequest request = mock(AiDTO.ToolConfirmRequest.class);
        AiVO.ToolExecuteResultVO result = mock(AiVO.ToolExecuteResultVO.class);
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        when(aiToolOrchestrationService.confirm(currentUser, request)).thenReturn(result);

        var response = controller.confirmTool(request);

        assertThat(response.getData()).isSameAs(result);
        verify(aiToolOrchestrationService).confirm(currentUser, request);
    }

    @Test
    void assistant_shouldRejectMissingPermissionBeforeApplicationService() {
        CurrentUser currentUser = currentUser("ai:view");
        when(securityContextFacade.getCurrentUser()).thenReturn(currentUser);
        doThrow(new BizException(ErrorCode.FORBIDDEN, "缺少权限: ai:chat:send"))
                .when(permissionGuard).requirePermission(currentUser, "ai:chat:send");

        assertThatThrownBy(() -> controller.assistant())
                .isInstanceOf(BizException.class)
                .hasMessageContaining("缺少权限");
    }

    private CurrentUser currentUser(String permission) {
        return new CurrentUser(100L, "alice", 1001L, "session-1", 1, true, Set.of(permission));
    }
}

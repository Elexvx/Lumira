package com.lumira.ai.compat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.ai.dto.AiCommandModels.ChatRequest;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.vo.PageResponse;
import com.lumira.saas.modules.ai.app.AiKnowledgeBaseAppService;
import com.lumira.saas.modules.ai.app.AiManagementAppService;
import com.lumira.saas.modules.ai.app.AiNativeToolRuntimeService;
import com.lumira.saas.modules.ai.app.AiToolOrchestrationService;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.ai.vo.AiVO;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiV2CompatibilityFacadeTest {

    @Test
    void convertsCanonicalPageToHistoricalV2ResponseWithoutStartingASecondControlPlane() {
        AiManagementAppService management = mock(AiManagementAppService.class);
        CurrentUser currentUser = new CurrentUser();
        AiVO.EmployeeVO canonicalEmployee = new AiVO.EmployeeVO();
        canonicalEmployee.setId(7L);
        canonicalEmployee.setUsername("assistant");
        canonicalEmployee.setNickname("AI Assistant");
        canonicalEmployee.setSystemPrompt("internal-only");
        canonicalEmployee.setDefaultLlmServiceTitle("OpenAI");
        PageResponse<AiVO.EmployeeVO> canonicalPage = new PageResponse<>();
        canonicalPage.setPageNo(1);
        canonicalPage.setPageSize(10);
        canonicalPage.setTotal(1);
        canonicalPage.setHasMore(false);
        canonicalPage.setRecords(List.of(canonicalEmployee));
        when(management.listEmployees(currentUser, 1, 10)).thenReturn(canonicalPage);

        AiV2CompatibilityFacade facade = facade(management);
        var response = facade.listEmployees(currentUser, 1, 10);

        assertThat(response.getRecords()).singleElement().satisfies(employee -> {
            assertThat(employee.getId()).isEqualTo(7L);
            assertThat(employee.getUsername()).isEqualTo("assistant");
            assertThat(employee.getDefaultLlmServiceTitle()).isEqualTo("OpenAI");
        });
        assertThat(response.getHasMore()).isFalse();
        verify(management).listEmployees(currentUser, 1, 10);
    }

    @Test
    void mapsTheHistoricalChatRequestToCanonicalApplicationInput() {
        AiManagementAppService management = mock(AiManagementAppService.class);
        when(management.chat(any(CurrentUser.class), any(AiDTO.ChatRequest.class))).thenReturn(new AiVO.ChatResponseVO());
        AiV2CompatibilityFacade facade = facade(management);
        CurrentUser currentUser = new CurrentUser();

        facade.chat(currentUser, new ChatRequest(
                9L, List.of(9L, 10L), 11L, 12L, "hello", true, List.of(), List.of("search"), List.of(13L), List.of(), true
        ));

        ArgumentCaptor<AiDTO.ChatRequest> request = ArgumentCaptor.forClass(AiDTO.ChatRequest.class);
        verify(management).chat(eq(currentUser), request.capture());
        assertThat(request.getValue().getEmployeeId()).isEqualTo(9L);
        assertThat(request.getValue().getEmployeeIds()).containsExactly(9L, 10L);
        assertThat(request.getValue().getConversationId()).isEqualTo(11L);
        assertThat(request.getValue().getPendingToolCallId()).isEqualTo(12L);
        assertThat(request.getValue().getMessage()).isEqualTo("hello");
        assertThat(request.getValue().getKnowledgeBaseIds()).containsExactly(13L);
        assertThat(request.getValue().getConfirmed()).isTrue();
    }

    private AiV2CompatibilityFacade facade(AiManagementAppService management) {
        return new AiV2CompatibilityFacade(
                management,
                mock(AiKnowledgeBaseAppService.class),
                mock(AiNativeToolRuntimeService.class),
                mock(AiToolOrchestrationService.class),
                new ObjectMapper()
        );
    }
}

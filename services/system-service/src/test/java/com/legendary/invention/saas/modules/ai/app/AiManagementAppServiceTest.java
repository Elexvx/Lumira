package com.legendary.invention.saas.modules.ai.app;

import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.modules.ai.dto.AiDTO;
import com.legendary.invention.saas.modules.ai.infrastructure.AiSecretCryptoService;
import com.legendary.invention.saas.modules.ai.vo.AiVO;
import com.legendary.invention.saas.modules.audit.app.OperationAuditService;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiManagementAppServiceTest {

    @Test
    void testLlmServiceReturnsSuccessfulProbeResult() {
        AiChatModelFactory chatModelFactory = mock(AiChatModelFactory.class);
        AiChatModelFactory.AiChatClient chatClient = mock(AiChatModelFactory.AiChatClient.class);
        AiManagementAppService service = newService(chatModelFactory);
        AiVO.ChatResponseVO response = new AiVO.ChatResponseVO();
        response.setProvider("deepseek");
        response.setModel("deepseek-chat");
        response.setReplyText("OK");
        when(chatModelFactory.create(any())).thenReturn(chatClient);
        when(chatClient.chat(any(), any(), anyList())).thenReturn(response);

        AiVO.LlmServiceTestResultVO result = service.testLlmService(currentUser(), testRequest());

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("测试通过");
        assertThat(result.getProvider()).isEqualTo("deepseek");
        assertThat(result.getModel()).isEqualTo("deepseek-chat");
        assertThat(result.getReplyText()).isEqualTo("OK");
        assertThat(result.getLatencyMs()).isNotNull();
    }

    @Test
    void testLlmServiceReturnsFailureProbeResult() {
        AiChatModelFactory chatModelFactory = mock(AiChatModelFactory.class);
        AiChatModelFactory.AiChatClient chatClient = mock(AiChatModelFactory.AiChatClient.class);
        AiManagementAppService service = newService(chatModelFactory);
        when(chatModelFactory.create(any())).thenReturn(chatClient);
        when(chatClient.chat(any(), any(), anyList())).thenThrow(new BizException(ErrorCode.BIZ_ERROR, "LLM 调用失败: 401"));

        AiVO.LlmServiceTestResultVO result = service.testLlmService(currentUser(), testRequest());

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("LLM 调用失败: 401");
        assertThat(result.getProvider()).isEqualTo("deepseek");
        assertThat(result.getModel()).isEqualTo("deepseek-chat");
        assertThat(result.getLatencyMs()).isNotNull();
    }

    private AiManagementAppService newService(AiChatModelFactory chatModelFactory) {
        return new AiManagementAppService(
                mock(MyBatisQueryOperations.class),
                mock(OperationAuditService.class),
                mock(AiSecretCryptoService.class),
                mock(AiEmployeeRuntimeService.class),
                chatModelFactory
        );
    }

    private CurrentUser currentUser() {
        return new CurrentUser(100L, "admin", 1001L, "session-1", 1, true, Set.of("ai:llm:update"));
    }

    private AiDTO.LlmServiceTestRequest testRequest() {
        AiDTO.LlmServiceTestRequest request = new AiDTO.LlmServiceTestRequest();
        request.setProvider("deepseek");
        request.setCode("deepseek-test");
        request.setTitle("DeepSeek Test");
        request.setBaseUrl("https://api.deepseek.com");
        request.setApiKey("sk-test");
        request.setDefaultModel("deepseek-chat");
        request.setMaxTokens(16);
        return request;
    }
}

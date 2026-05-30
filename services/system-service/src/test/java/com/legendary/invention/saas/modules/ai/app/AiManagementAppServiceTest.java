package com.legendary.invention.saas.modules.ai.app;

import com.legendary.invention.common.enums.ErrorCode;
import com.legendary.invention.common.exception.BizException;
import com.legendary.invention.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.legendary.invention.saas.infrastructure.persistence.mybatis.RowMapper;
import com.legendary.invention.common.security.CurrentUser;
import com.legendary.invention.saas.modules.ai.dto.AiDTO;
import com.legendary.invention.saas.modules.ai.infrastructure.AiSecretCryptoService;
import com.legendary.invention.saas.modules.ai.vo.AiVO;
import com.legendary.invention.saas.modules.audit.app.OperationAuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiManagementAppServiceTest {

    @Test
    void governanceOverviewUsesAggregateStatsQueries() {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        AiManagementAppService service = new AiManagementAppService(
                jdbcTemplate,
                mock(OperationAuditService.class),
                mock(AiSecretCryptoService.class),
                mock(AiEmployeeRuntimeService.class),
                mock(AiChatModelFactory.class)
        );
        when(jdbcTemplate.queryForList(contains("from ai_employee"), eq(1001L)))
                .thenReturn(List.of(Map.of("employeeCount", 5L, "enabledEmployeeCount", 3L)));
        when(jdbcTemplate.queryForList(contains("from ai_llm_service"), eq(1001L)))
                .thenReturn(List.of(Map.of("llmServiceCount", 4L, "enabledLlmServiceCount", 2L, "missingApiKeyServiceCount", 1L)));
        when(jdbcTemplate.queryForList(contains("from ai_skill")))
                .thenReturn(List.of(Map.of("skillCount", 7L, "highRiskSkillCount", 2L, "confirmationRequiredSkillCount", 4L)));
        when(jdbcTemplate.queryForObject(contains("from ai_employee_skill"), eq(Long.class), eq(1001L))).thenReturn(6L);

        AiVO.GovernanceOverviewVO overview = service.governanceOverview(currentUser());

        assertThat(overview.getEmployeeCount()).isEqualTo(5L);
        assertThat(overview.getEnabledEmployeeCount()).isEqualTo(3L);
        assertThat(overview.getLlmServiceCount()).isEqualTo(4L);
        assertThat(overview.getEnabledLlmServiceCount()).isEqualTo(2L);
        assertThat(overview.getMissingApiKeyServiceCount()).isEqualTo(1L);
        assertThat(overview.getSkillCount()).isEqualTo(7L);
        assertThat(overview.getHighRiskSkillCount()).isEqualTo(2L);
        assertThat(overview.getConfirmationRequiredSkillCount()).isEqualTo(4L);
        assertThat(overview.getHighRiskAllowedBindingCount()).isEqualTo(6L);
        assertThat(overview.getSampledAt()).isNotNull();
        verify(jdbcTemplate).queryForList(contains("from ai_employee"), eq(1001L));
        verify(jdbcTemplate).queryForList(contains("from ai_llm_service"), eq(1001L));
        verify(jdbcTemplate).queryForList(contains("from ai_skill"));
        verify(jdbcTemplate, times(1)).queryForObject(anyString(), eq(Long.class), any());
    }

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
    void testLlmServiceRejectsEndpointOverrideWhenReusingStoredApiKey() throws Exception {
        MyBatisQueryOperations jdbcTemplate = mock(MyBatisQueryOperations.class);
        AiSecretCryptoService secretCryptoService = mock(AiSecretCryptoService.class);
        AiChatModelFactory chatModelFactory = mock(AiChatModelFactory.class);
        AiManagementAppService service = new AiManagementAppService(
                jdbcTemplate,
                mock(OperationAuditService.class),
                secretCryptoService,
                mock(AiEmployeeRuntimeService.class),
                chatModelFactory
        );
        Object existing = llmServiceRecord("deepseek", "https://api.deepseek.com", "encrypted-secret");
        @SuppressWarnings({"rawtypes", "unchecked"})
        List existingServices = List.of(existing);
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(), any())).thenReturn(existingServices);

        AiDTO.LlmServiceTestRequest request = testRequest();
        request.setServiceId(10L);
        request.setApiKey(null);
        request.setBaseUrl("https://attacker.example");

        assertThatThrownBy(() -> service.testLlmService(currentUser(), request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("重新输入 API Key");
    }

    @Test
    void aiChatModelFactoryRejectsPrivateBaseUrl() {
        HttpAiChatModelFactory factory = new HttpAiChatModelFactory(new ObjectMapper());
        AiLlmServiceConfig config = new AiLlmServiceConfig();
        config.setProvider("deepseek");
        config.setDefaultModel("deepseek-chat");
        config.setBaseUrl("http://127.0.0.1:8080");
        config.setApiKey("sk-test");
        AiDTO.ChatRequest chatRequest = new AiDTO.ChatRequest();
        chatRequest.setMessage("ping");
        AiVO.EmployeeDetailVO employee = new AiVO.EmployeeDetailVO();
        employee.setSystemPrompt("system");

        assertThatThrownBy(() -> factory.create(config).chat(chatRequest, employee, List.of()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("内网或本机地址");
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

    private Object llmServiceRecord(String provider, String baseUrl, String apiKeyEncrypted) throws Exception {
        Class<?> recordType = Class.forName("com.legendary.invention.saas.modules.ai.app.AiManagementAppService$AiEntitiesHelper$LlmServiceRecord");
        Constructor<?> constructor = recordType.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object record = constructor.newInstance();
        setRecordValue(recordType, record, "setId", 10L);
        setRecordValue(recordType, record, "setProvider", provider);
        setRecordValue(recordType, record, "setCode", "deepseek");
        setRecordValue(recordType, record, "setTitle", "DeepSeek");
        setRecordValue(recordType, record, "setBaseUrl", baseUrl);
        setRecordValue(recordType, record, "setApiKeyEncrypted", apiKeyEncrypted);
        setRecordValue(recordType, record, "setDefaultModel", "deepseek-chat");
        setRecordValue(recordType, record, "setTimeoutMs", 60000);
        setRecordValue(recordType, record, "setMaxTokens", 64);
        return record;
    }

    private void setRecordValue(Class<?> recordType, Object record, String methodName, Object value) throws Exception {
        Method method = recordType.getMethod(methodName, value.getClass());
        method.setAccessible(true);
        method.invoke(record, value);
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

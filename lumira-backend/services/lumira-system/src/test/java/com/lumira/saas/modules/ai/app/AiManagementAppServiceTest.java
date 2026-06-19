package com.lumira.saas.modules.ai.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.infrastructure.persistence.mybatis.RowMapper;
import com.lumira.saas.infrastructure.persistence.mybatis.SqlRow;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.ai.dto.AiDTO;
import com.lumira.saas.modules.ai.infrastructure.AiSecretCryptoService;
import com.lumira.saas.modules.ai.vo.AiVO;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
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
        AiVO.GovernanceOverviewVO cached = service.governanceOverview(currentUser());

        assertThat(overview.getEmployeeCount()).isEqualTo(5L);
        assertThat(cached.getEmployeeCount()).isEqualTo(5L);
        assertThat(overview.getEnabledEmployeeCount()).isEqualTo(3L);
        assertThat(overview.getLlmServiceCount()).isEqualTo(4L);
        assertThat(overview.getEnabledLlmServiceCount()).isEqualTo(2L);
        assertThat(overview.getMissingApiKeyServiceCount()).isEqualTo(1L);
        assertThat(overview.getSkillCount()).isEqualTo(7L);
        assertThat(overview.getHighRiskSkillCount()).isEqualTo(2L);
        assertThat(overview.getConfirmationRequiredSkillCount()).isEqualTo(4L);
        assertThat(overview.getHighRiskAllowedBindingCount()).isEqualTo(6L);
        assertThat(overview.getSampledAt()).isNotNull();
        assertThat(cached.getSampledAt()).isNotNull();
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
    void listConversationMessagesShouldAttachMessageFiles() {
        ConversationQueryOperations queryOperations = new ConversationQueryOperations();
        AiManagementAppService service = new AiManagementAppService(
                queryOperations,
                mock(OperationAuditService.class),
                mock(AiSecretCryptoService.class),
                mock(AiEmployeeRuntimeService.class),
                mock(AiChatModelFactory.class)
        );

        List<AiVO.MessageVO> messages = service.listConversationMessages(currentUser(), 10L);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getAttachments()).hasSize(1);
        assertThat(messages.get(1).getAttachments()).isEmpty();
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

    @Test
    void createEmployeeShouldRejectDuplicateUsernameViaExistsCheck() {
        RecordingQueryOperations jdbcTemplate = new RecordingQueryOperations();
        jdbcTemplate.employeeUsernameExists = true;
        AiManagementAppService service = new AiManagementAppService(
                jdbcTemplate,
                mock(OperationAuditService.class),
                mock(AiSecretCryptoService.class),
                mock(AiEmployeeRuntimeService.class),
                mock(AiChatModelFactory.class)
        );

        AiDTO.EmployeeUpsertRequest request = new AiDTO.EmployeeUpsertRequest();
        request.setUsername("assistant");
        request.setNickname("助手");
        request.setDefaultLlmServiceId(null);

        assertThatThrownBy(() -> service.createEmployee(currentUser(), request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户名已存在");
        assertThat(jdbcTemplate.employeeExistsChecked).isTrue();
        assertThat(jdbcTemplate.countQueryCalled).isFalse();
    }

    @Test
    void createLlmServiceShouldRejectDuplicateCodeViaExistsCheck() {
        RecordingQueryOperations jdbcTemplate = new RecordingQueryOperations();
        jdbcTemplate.llmServiceCodeExists = true;
        AiManagementAppService service = new AiManagementAppService(
                jdbcTemplate,
                mock(OperationAuditService.class),
                mock(AiSecretCryptoService.class),
                mock(AiEmployeeRuntimeService.class),
                mock(AiChatModelFactory.class)
        );

        AiDTO.LlmServiceUpsertRequest request = new AiDTO.LlmServiceUpsertRequest();
        request.setProvider("deepseek");
        request.setCode("deepseek-chat");
        request.setTitle("DeepSeek");

        assertThatThrownBy(() -> service.createLlmService(currentUser(), request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("LLM 服务标识已存在");
        assertThat(jdbcTemplate.llmServiceExistsChecked).isTrue();
        assertThat(jdbcTemplate.countQueryCalled).isFalse();
    }

    @Test
    void listEmployeesShouldSkipCountForFirstShortPage() {
        RecordingQueryOperations jdbcTemplate = new RecordingQueryOperations();
        AiManagementAppService service = new AiManagementAppService(
                jdbcTemplate,
                mock(OperationAuditService.class),
                mock(AiSecretCryptoService.class),
                mock(AiEmployeeRuntimeService.class),
                mock(AiChatModelFactory.class)
        );

        var response = service.listEmployees(currentUser(), 1, 10);

        assertThat(response.getRecords()).hasSize(1);
        assertThat(response.getTotal()).isEqualTo(1L);
        assertThat(jdbcTemplate.countQueryCalled).isFalse();
    }

    private Object llmServiceRecord(String provider, String baseUrl, String apiKeyEncrypted) throws Exception {
        Class<?> recordType = Class.forName("com.lumira.saas.modules.ai.app.AiManagementAppService$AiEntitiesHelper$LlmServiceRecord");
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

    private static final class ConversationQueryOperations extends MyBatisQueryOperations {
        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            if (sql.contains("from ai_conversation")) {
                Map<String, Object> conversation = new java.util.LinkedHashMap<>();
                conversation.put("id", 10L);
                conversation.put("tenant_id", 1001L);
                conversation.put("owner_user_id", 100L);
                conversation.put("conversation_code", "conv_10");
                conversation.put("title", "测试会话");
                conversation.put("preview", "hello");
                conversation.put("status", "ACTIVE");
                conversation.put("pinned", 0);
                conversation.put("latest_message_at", LocalDateTime.now());
                conversation.put("create_time", LocalDateTime.now());
                conversation.put("update_time", LocalDateTime.now());
                return mapRows(rowMapper, List.of(conversation));
            }
            if (sql.contains("from ai_message_attachment")) {
                Map<String, Object> attachment = new java.util.LinkedHashMap<>();
                attachment.put("id", 1001L);
                attachment.put("file_id", 2001L);
                attachment.put("message_id", 1L);
                attachment.put("original_file_name", "a.txt");
                attachment.put("file_extension", "txt");
                attachment.put("mime_type", "text/plain");
                attachment.put("file_size_bytes", 128L);
                attachment.put("file_size_label", "0.1 KB");
                attachment.put("public_url", "/api/uploads/a.txt");
                attachment.put("preview_url", null);
                attachment.put("download_url", "/api/uploads/download/a.txt");
                attachment.put("preview_mode", "TEXT");
                return mapRows(rowMapper, List.of(
                        attachment
                ));
            }
            if (sql.contains("from ai_message")) {
                return mapRows(rowMapper, List.of(
                        Map.of(
                                "id", 1L,
                                "conversationId", 10L,
                                "role", "USER",
                                "content", "hello",
                                "createTime", LocalDateTime.now()
                        ),
                        Map.of(
                                "id", 2L,
                                "conversationId", 10L,
                                "role", "ASSISTANT",
                                "content", "world",
                                "createTime", LocalDateTime.now()
                        )
                ));
            }
            return List.of();
        }

        private <T> List<T> mapRows(RowMapper<T> rowMapper, List<Map<String, Object>> rows) {
            List<T> mapped = new java.util.ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                try {
                    mapped.add(rowMapper.mapRow(new SqlRow(rows.get(i)), i));
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }
            return mapped;
        }
    }

    private static final class RecordingQueryOperations extends MyBatisQueryOperations {
        private boolean employeeUsernameExists;
        private boolean llmServiceCodeExists;
        private boolean employeeExistsChecked;
        private boolean llmServiceExistsChecked;
        private boolean countQueryCalled;

        @Override
        public boolean exists(String sql, Object... args) {
            if (sql.contains("from ai_employee")) {
                employeeExistsChecked = true;
                return employeeUsernameExists;
            }
            if (sql.contains("from ai_llm_service")) {
                llmServiceExistsChecked = true;
                return llmServiceCodeExists;
            }
            return false;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("count(1)")) {
                countQueryCalled = true;
            }
            if (sql.contains("select last_insert_id()")) {
                return requiredType.cast(1L);
            }
            if (sql.contains("from ai_employee_skill")) {
                return requiredType.cast(0L);
            }
            return null;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            if (sql.contains("from ai_employee")) {
                try {
                    return List.of(rowMapper.mapRow(new SqlRow(Map.of(
                            "id", 1L,
                            "tenantId", 1001L,
                            "username", "assistant",
                            "nickname", "助手",
                            "position", "客服",
                            "enabled", 1,
                            "sortOrder", 1,
                            "createTime", LocalDateTime.now(),
                            "updateTime", LocalDateTime.now()
                    )), 0));
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }
            return List.of();
        }
    }
}

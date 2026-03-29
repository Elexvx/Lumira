package com.yourcompany.saas.common.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.saas.common.enums.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldSerializeUserMessageWithExpectedFieldName() {
        ApiResponse<Void> response = ApiResponse.fail(
                ErrorCode.VALIDATION_ERROR,
                "参数 tenantId 不能为空",
                "提交参数不完整，请检查后重试",
                "req-1",
                "/api/test"
        );

        JsonNode payload = objectMapper.valueToTree(response);

        assertEquals("参数 tenantId 不能为空", payload.get("message").asText());
        assertEquals("提交参数不完整，请检查后重试", payload.get("userMessage").asText());
        assertTrue(payload.has("code"));
        assertFalse(payload.has("errorCode"));
        assertFalse(payload.has("errorMessage"));
        assertFalse(payload.has("userTip"));
    }
}

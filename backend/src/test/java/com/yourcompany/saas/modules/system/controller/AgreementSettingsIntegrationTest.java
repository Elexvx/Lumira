package com.yourcompany.saas.modules.system.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgreementSettingsIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void agreementSettingsShouldBeSharedBetweenPublicAndSystemEndpoints() throws Exception {
        String baseUrl = "http://localhost:" + port;
        disableCaptcha();
        LoginResult loginResult = loginAdmin(baseUrl);
        String originalUserAgreement = readAgreement(baseUrl + "/api/v1/public/agreement-settings", null).path("data").path("userAgreementMarkdown").asText("");
        String originalPrivacyAgreement = readAgreement(baseUrl + "/api/v1/public/agreement-settings", null).path("data").path("privacyAgreementMarkdown").asText("");
        String updatedUserAgreement = "# 更新后的用户协议\n\n- 仅用于测试";
        String updatedPrivacyAgreement = "# 更新后的隐私协议\n\n- 仅用于测试";

        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(loginResult.accessToken());
        authHeaders.add("X-Tenant-Id", String.valueOf(loginResult.tenantId()));
        authHeaders.setContentType(MediaType.APPLICATION_JSON);

        try {
            writeAgreement(baseUrl + "/api/v1/system/agreement-settings", authHeaders, updatedUserAgreement, updatedPrivacyAgreement);

            JsonNode publicBody = readAgreement(baseUrl + "/api/v1/public/agreement-settings", null);
            Assertions.assertEquals("0", publicBody.path("code").asText(), publicBody.toString());
            Assertions.assertEquals(updatedUserAgreement, publicBody.path("data").path("userAgreementMarkdown").asText(), publicBody.toString());
            Assertions.assertEquals(updatedPrivacyAgreement, publicBody.path("data").path("privacyAgreementMarkdown").asText(), publicBody.toString());

            JsonNode systemBody = readAgreement(baseUrl + "/api/v1/system/agreement-settings", authHeaders);
            Assertions.assertEquals("0", systemBody.path("code").asText(), systemBody.toString());
            Assertions.assertEquals(updatedUserAgreement, systemBody.path("data").path("userAgreementMarkdown").asText(), systemBody.toString());
            Assertions.assertEquals(updatedPrivacyAgreement, systemBody.path("data").path("privacyAgreementMarkdown").asText(), systemBody.toString());
        } finally {
            writeAgreement(baseUrl + "/api/v1/system/agreement-settings", authHeaders, originalUserAgreement, originalPrivacyAgreement);
        }
    }

    private LoginResult loginAdmin(String baseUrl) throws Exception {
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> loginResponse = restTemplate.postForEntity(
                baseUrl + "/api/v1/auth/login",
                new HttpEntity<>("{\"username\":\"admin\",\"password\":\"123456\"}", loginHeaders),
                String.class
        );
        Assertions.assertEquals(200, loginResponse.getStatusCode().value(), loginResponse.getBody());

        JsonNode loginBody = objectMapper.readTree(loginResponse.getBody());
        Assertions.assertEquals("0", loginBody.path("code").asText(), loginResponse.getBody());

        String accessToken = loginBody.path("data").path("accessToken").asText();
        Long tenantId = loginBody.path("data").path("currentTenant").path("tenantId").asLong();
        return new LoginResult(accessToken, tenantId);
    }

    private JsonNode readAgreement(String url, HttpHeaders headers) throws Exception {
        HttpHeaders actualHeaders = headers == null ? new HttpHeaders() : headers;
        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(actualHeaders),
                String.class
        );
        Assertions.assertTrue(response.getStatusCode().is2xxSuccessful(), response.getBody());
        return objectMapper.readTree(response.getBody());
    }

    private void writeAgreement(String url, HttpHeaders headers, String userAgreementMarkdown, String privacyAgreementMarkdown) throws Exception {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("userAgreementMarkdown", userAgreementMarkdown);
        payload.put("privacyAgreementMarkdown", privacyAgreementMarkdown);
        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.PUT,
                new HttpEntity<>(objectMapper.writeValueAsString(payload), headers),
                String.class
        );
        Assertions.assertTrue(response.getStatusCode().is2xxSuccessful(), response.getBody());
        JsonNode body = objectMapper.readTree(response.getBody());
        Assertions.assertEquals("0", body.path("code").asText(), response.getBody());
    }

    private record LoginResult(String accessToken, Long tenantId) {
    }

    private void disableCaptcha() {
        jdbcTemplate.update(
                """
                        update sys_config
                        set config_value = '0'
                        where tenant_id in (1001, 1002)
                          and config_key = 'security.captcha-enabled'
                          and deleted = 0
                        """
        );
    }
}

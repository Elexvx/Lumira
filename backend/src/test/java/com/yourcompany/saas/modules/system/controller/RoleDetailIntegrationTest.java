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
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoleDetailIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void roleDetailShouldReturnSuccessForAdmin() throws Exception {
        String baseUrl = "http://localhost:" + port;
        disableCaptcha();

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

        HttpHeaders detailHeaders = new HttpHeaders();
        detailHeaders.setBearerAuth(accessToken);
        detailHeaders.add("X-Tenant-Id", String.valueOf(tenantId));
        ResponseEntity<String> roleResponse = restTemplate.exchange(
                baseUrl + "/api/v1/system/roles/2001",
                HttpMethod.GET,
                new HttpEntity<>(detailHeaders),
                String.class
        );

        if (!roleResponse.getStatusCode().is2xxSuccessful()) {
            System.out.println("Role detail failed response body: " + roleResponse.getBody());
        }
        Assertions.assertTrue(roleResponse.getStatusCode().is2xxSuccessful(), roleResponse.getBody());

        JsonNode roleBody = objectMapper.readTree(roleResponse.getBody());
        Assertions.assertEquals("0", roleBody.path("code").asText(), roleResponse.getBody());
        Assertions.assertEquals(2001L, roleBody.path("data").path("id").asLong(), roleResponse.getBody());
    }

    @Test
    void permissionTreeShouldReturnStructuredNodesForAdmin() throws Exception {
        String baseUrl = "http://localhost:" + port;
        disableCaptcha();

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

        HttpHeaders detailHeaders = new HttpHeaders();
        detailHeaders.setBearerAuth(accessToken);
        detailHeaders.add("X-Tenant-Id", String.valueOf(tenantId));
        ResponseEntity<String> treeResponse = restTemplate.exchange(
                baseUrl + "/api/v1/system/permissions/tree",
                HttpMethod.GET,
                new HttpEntity<>(detailHeaders),
                String.class
        );

        if (!treeResponse.getStatusCode().is2xxSuccessful()) {
            System.out.println("Permission tree failed response body: " + treeResponse.getBody());
        }
        Assertions.assertTrue(treeResponse.getStatusCode().is2xxSuccessful(), treeResponse.getBody());

        JsonNode treeBody = objectMapper.readTree(treeResponse.getBody());
        Assertions.assertEquals("0", treeBody.path("code").asText(), treeResponse.getBody());
        Assertions.assertTrue(treeBody.path("data").isArray(), treeResponse.getBody());
        Assertions.assertTrue(treeBody.path("data").size() > 0, treeResponse.getBody());
        Assertions.assertTrue(treeBody.path("data").get(0).path("pageKey").isTextual(), treeResponse.getBody());
        Assertions.assertTrue(treeBody.path("data").get(0).path("pageName").isTextual(), treeResponse.getBody());
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

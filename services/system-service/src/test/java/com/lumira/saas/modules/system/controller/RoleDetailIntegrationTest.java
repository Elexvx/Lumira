package com.lumira.saas.modules.system.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.cloud.nacos.config.import-check.enabled=false"
)
@Disabled("Requires a seeded local MySQL schema; move to an isolated integration-test profile before enabling in default mvn test.")
class RoleDetailIntegrationTest {

    private static final OAEPParameterSpec OAEP_SPEC = new OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT
    );

    @LocalServerPort
    private int port;

    private final RestTemplate restTemplate = createRestTemplate();

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static RestTemplate createRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(response -> false);
        return restTemplate;
    }

    @Test
    void roleDetailShouldReturnSuccessForAdmin() throws Exception {
        String baseUrl = "http://localhost:" + port;
        disableCaptcha();
        String accessToken = loginAsAdmin(baseUrl);
        HttpHeaders detailHeaders = new HttpHeaders();
        detailHeaders.setBearerAuth(accessToken);
        ResponseEntity<String> roleResponse = restTemplate.exchange(
                baseUrl + "/api/v1/system/roles/2001",
                HttpMethod.GET,
                new HttpEntity<>(detailHeaders),
                String.class
        );

        Assertions.assertTrue(roleResponse.getStatusCode().is2xxSuccessful(), roleResponse.getBody());

        JsonNode roleBody = objectMapper.readTree(roleResponse.getBody());
        Assertions.assertEquals("0", roleBody.path("code").asText(), roleResponse.getBody());
        Assertions.assertEquals(2001L, roleBody.path("data").path("id").asLong(), roleResponse.getBody());
    }

    @Test
    void permissionTreeShouldReturnStructuredNodesForAdmin() throws Exception {
        String baseUrl = "http://localhost:" + port;
        disableCaptcha();
        String accessToken = loginAsAdmin(baseUrl);
        HttpHeaders detailHeaders = new HttpHeaders();
        detailHeaders.setBearerAuth(accessToken);
        ResponseEntity<String> treeResponse = restTemplate.exchange(
                baseUrl + "/api/v1/system/permissions/tree",
                HttpMethod.GET,
                new HttpEntity<>(detailHeaders),
                String.class
        );

        Assertions.assertTrue(treeResponse.getStatusCode().is2xxSuccessful(), treeResponse.getBody());

        JsonNode treeBody = objectMapper.readTree(treeResponse.getBody());
        Assertions.assertEquals("0", treeBody.path("code").asText(), treeResponse.getBody());
        Assertions.assertTrue(treeBody.path("data").isArray(), treeResponse.getBody());
        Assertions.assertTrue(treeBody.path("data").size() > 0, treeResponse.getBody());

        JsonNode settingsRootNode = findNodeByPageName(treeBody.path("data"), "系统设置");
        Assertions.assertNotNull(settingsRootNode, treeResponse.getBody());
        Assertions.assertEquals("CATALOG", settingsRootNode.path("nodeType").asText(), treeResponse.getBody());
        Assertions.assertTrue(settingsRootNode.path("routePath").isNull() || settingsRootNode.path("routePath").asText().isBlank(), treeResponse.getBody());
        Assertions.assertNull(findNodeByPageName(treeBody.path("data"), "系统总览"), treeResponse.getBody());
        Assertions.assertNull(findNodeByPageName(treeBody.path("data"), "系统管理"), treeResponse.getBody());

        ResponseEntity<String> menusResponse = restTemplate.exchange(
                baseUrl + "/api/v1/system/menus",
                HttpMethod.GET,
                new HttpEntity<>(detailHeaders),
                String.class
        );
        Assertions.assertTrue(menusResponse.getStatusCode().is2xxSuccessful(), menusResponse.getBody());

        JsonNode menusBody = objectMapper.readTree(menusResponse.getBody());
        Assertions.assertEquals("0", menusBody.path("code").asText(), menusResponse.getBody());
        Assertions.assertNull(findNodeByMenuCode(menusBody.path("data"), "system.root"), menusResponse.getBody());
        Assertions.assertNull(findNodeByMenuCode(menusBody.path("data"), "settings.root"), menusResponse.getBody());

        JsonNode userCenterNode = findNodeByPageName(treeBody.path("data"), "用户中心");
        Assertions.assertNotNull(userCenterNode, treeResponse.getBody());
        Assertions.assertEquals("CATALOG", userCenterNode.path("nodeType").asText(), treeResponse.getBody());
        Assertions.assertTrue(userCenterNode.path("routePath").isNull() || userCenterNode.path("routePath").asText().isBlank(), treeResponse.getBody());

        JsonNode monitoringNode = findNodeByPageName(treeBody.path("data"), "系统监控");
        Assertions.assertNotNull(monitoringNode, treeResponse.getBody());
        Assertions.assertEquals("PAGE", monitoringNode.path("nodeType").asText(), treeResponse.getBody());
        Assertions.assertEquals("/settings/monitoring", monitoringNode.path("routePath").asText(), treeResponse.getBody());
        Assertions.assertTrue(monitoringNode.path("children").isArray(), treeResponse.getBody());

        Set<String> childNames = new LinkedHashSet<>();
        Set<String> childTypes = new LinkedHashSet<>();
        for (JsonNode child : monitoringNode.path("children")) {
            childNames.add(child.path("pageName").asText());
            childTypes.add(child.path("nodeType").asText());
        }

        Assertions.assertTrue(childNames.contains("接口文档"), treeResponse.getBody());
        Assertions.assertTrue(childNames.contains("审计中心"), treeResponse.getBody());
        Assertions.assertFalse(childNames.contains("服务监控"), treeResponse.getBody());
        Assertions.assertFalse(childNames.contains("Redis监控"), treeResponse.getBody());
        Assertions.assertTrue(childTypes.stream().allMatch("PAGE"::equals), treeResponse.getBody());

        Set<String> monitoringActionPermissions = new LinkedHashSet<>();
        for (JsonNode actionPermission : monitoringNode.path("actionPermissions")) {
            monitoringActionPermissions.add(actionPermission.path("permissionKey").asText());
        }
        Assertions.assertTrue(monitoringActionPermissions.contains("system:monitor:service:view"), treeResponse.getBody());
        Assertions.assertTrue(monitoringActionPermissions.contains("system:monitor:redis:view"), treeResponse.getBody());
        Assertions.assertTrue(monitoringActionPermissions.contains("system:monitor:docs:view"), treeResponse.getBody());
    }

    @Test
    void loginShouldRejectPlaintextPasswordInStrictMode() throws Exception {
        String baseUrl = "http://localhost:" + port;
        disableCaptcha();

        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> loginResponse = restTemplate.postForEntity(
                baseUrl + "/api/v1/auth/login",
                new HttpEntity<>("{\"username\":\"admin\",\"password\":\"123456\"}", loginHeaders),
                String.class
        );

        Assertions.assertTrue(loginResponse.getStatusCode().is4xxClientError(), loginResponse.getBody());
        JsonNode loginBody = objectMapper.readTree(loginResponse.getBody());
        Assertions.assertNotEquals("0", loginBody.path("code").asText(), loginResponse.getBody());
    }

    private JsonNode findNodeByPageName(JsonNode nodes, String pageName) {
        for (JsonNode node : nodes) {
            if (pageName.equals(node.path("pageName").asText())) {
                return node;
            }
            JsonNode children = node.path("children");
            if (children.isArray()) {
                JsonNode childMatch = findNodeByPageName(children, pageName);
                if (childMatch != null) {
                    return childMatch;
                }
            }
        }
        return null;
    }

    private JsonNode findNodeByMenuCode(JsonNode nodes, String menuCode) {
        for (JsonNode node : nodes) {
            if (menuCode.equals(node.path("menuCode").asText())) {
                return node;
            }
            JsonNode children = node.path("children");
            if (children.isArray()) {
                JsonNode childMatch = findNodeByMenuCode(children, menuCode);
                if (childMatch != null) {
                    return childMatch;
                }
            }
        }
        return null;
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

    private String loginAsAdmin(String baseUrl) throws Exception {
        String encryptedPassword = encryptPassword(baseUrl, "123456");
        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> loginResponse = restTemplate.postForEntity(
                baseUrl + "/api/v1/auth/login",
                new HttpEntity<>("{\"username\":\"admin\",\"password\":\"" + encryptedPassword + "\"}", loginHeaders),
                String.class
        );
        Assertions.assertEquals(200, loginResponse.getStatusCode().value(), loginResponse.getBody());

        JsonNode loginBody = objectMapper.readTree(loginResponse.getBody());
        Assertions.assertEquals("0", loginBody.path("code").asText(), loginResponse.getBody());
        return loginBody.path("data").path("accessToken").asText();
    }

    private String encryptPassword(String baseUrl, String password) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/v1/auth/login-encryption-key",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );
        Assertions.assertTrue(response.getStatusCode().is2xxSuccessful(), response.getBody());
        JsonNode body = objectMapper.readTree(response.getBody());
        Assertions.assertEquals("0", body.path("code").asText(), response.getBody());

        String publicKeyBase64 = body.path("data").path("publicKey").asText();
        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64))
        );

        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, OAEP_SPEC);
        return Base64.getEncoder().encodeToString(cipher.doFinal(password.getBytes(StandardCharsets.UTF_8)));
    }
}

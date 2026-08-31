package com.lumira.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import com.lumira.common.security.AuthorizationSnapshotMetricNames;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

/**
 * IAM-01 regression test against the same modular-monolith assembly used by lumira-server.
 *
 * <p>The production seed intentionally leaves the administrator disabled. This class enables
 * that seed only inside its disposable Testcontainers database so that all role/user mutations
 * and all authorization decisions still traverse production HTTP controllers and application
 * services.</p>
 */
@SpringBootTest(
        classes = LumiraServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.flyway.enabled=false",
                "spring.main.keep-alive=false",
                "lumira.monolith=true",
                "lumira.runtime.control-plane-enabled=true",
                "lumira.runtime.async-enabled=false",
                "lumira.dictionary-datasets.bootstrap-enabled=false",
                "security.rate-limit.enabled=false"
        }
)
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Iam01RevocationIntegrationTest {

    private static final long ADMIN_USER_ID = 1001L;
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "Iam01Admin!Secure2026";
    private static final String USER_PASSWORD = "Iam01User!Secure2026";
    private static final String ALERTING_PLUGIN = "builtin-alerting";
    private static final String ALERTING_SILENCE_PERMISSION = "plugin:alerting:silence";
    private static final String USER_VIEW_PERMISSION = "system:user:view";
    private static final String TEST_JWT_SECRET = "iam01-test-jwt-secret-at-least-thirty-two-characters-long";
    private static final String TEST_FIELD_SECRET = "iam01-test-field-secret-at-least-thirty-two-characters-long";
    private static final String MYSQL_PASSWORD = "iam01-mysql-password";

    private static final OAEPParameterSpec OAEP_SPEC = new OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT
    );

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("lumira")
            .withUsername("root")
            .withPassword(MYSQL_PASSWORD)
            .withCopyFileToContainer(
                    MountableFile.forHostPath(resolveSaasSql().toString()),
                    "/docker-entrypoint-initdb.d/001-saas.sql"
            )
            .waitingFor(Wait.forListeningPort());

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort());

    static {
        // Spring resolves datasource conditions before the JUnit Testcontainers extension's
        // beforeAll callback. Start the disposable dependencies before dynamic properties
        // are queried so the default monolith can create its production datasource.
        MYSQL.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
        registry.add("spring.data.redis.password", () -> "");
        registry.add("saas.security.jwt-secret", () -> TEST_JWT_SECRET);
        registry.add("saas.security.field-secret", () -> TEST_FIELD_SECRET);
        registry.add("saas.security.captcha-enabled", () -> "false");
        registry.add("saas.security.allow-plaintext-login-password", () -> "false");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MeterRegistry meterRegistry;

    @MockitoSpyBean
    private ReadModelVersionService readModelVersionService;

    private final RestTemplate restTemplate = createRestTemplate();
    private String baseUrl;

    @BeforeAll
    void prepareProductionSeedForAuthentication() throws Exception {
        baseUrl = "http://localhost:" + port;
        String passwordHash = passwordEncoder.encode(ADMIN_PASSWORD);
        jdbcTemplate.update(
                "update sys_user set status = 'ENABLED', password_hash = ? where id = ? and deleted = 0",
                passwordHash,
                ADMIN_USER_ID
        );
        jdbcTemplate.update("update iam_user set status = 'ENABLED' where id = ? and deleted = 0", ADMIN_USER_ID);
        jdbcTemplate.update("update iam_user_identity set status = 'ENABLED', verified = 1 where user_id = ? and deleted = 0", ADMIN_USER_ID);

        ResponseEntity<String> enableResponse = postJson(
                login(ADMIN_USERNAME, ADMIN_PASSWORD),
                "/api/v2/plugins/enable",
                Map.of("pluginCode", ALERTING_PLUGIN)
        );
        assertApiSuccess(enableResponse);
    }

    @AfterAll
    void verifyDisposableDatabaseOnly() {
        Assertions.assertTrue(MYSQL.isRunning(), "IAM-01 test database stopped before the test completed");
    }

    @Test
    void revokingRolePermissionMustRejectTheUnrefreshedAccessToken() throws Exception {
        Fixture fixture = provisionFixture("permission", "ALL");
        assertProtectedSilenceAllowed(fixture.accessToken(), fixture.beforeProbeName());

        ResponseEntity<String> mutationResponse = putJson(
                login(ADMIN_USERNAME, ADMIN_PASSWORD),
                "/api/v2/iam/roles/" + fixture.roleId() + "/permissions",
                Map.of("permissionKeys", List.of(USER_VIEW_PERMISSION))
        );
        assertApiSuccess(mutationResponse);

        assertOriginalTokenRejectedWithoutWrite(fixture.accessToken(), fixture.afterProbeName());
    }

    @Test
    void removingUserRoleMustRejectTheUnrefreshedAccessToken() throws Exception {
        Fixture fixture = provisionFixture("user-role", "ALL");
        assertProtectedSilenceAllowed(fixture.accessToken(), fixture.beforeProbeName());

        ResponseEntity<String> mutationResponse = putJson(
                login(ADMIN_USERNAME, ADMIN_PASSWORD),
                "/api/v2/iam/users/" + fixture.userId(),
                userPayload(fixture.username(), List.of())
        );
        assertApiSuccess(mutationResponse);

        assertOriginalTokenRejectedWithoutWrite(fixture.accessToken(), fixture.afterProbeName());
    }

    @Test
    void narrowingDataScopeMustRejectTheUnrefreshedAccessTokenAndApplyFreshSelfScope() throws Exception {
        Fixture fixture = provisionFixture("data-scope", "ALL");
        assertProtectedSilenceAllowed(fixture.accessToken(), fixture.beforeProbeName());

        ResponseEntity<String> mutationResponse = putJson(
                login(ADMIN_USERNAME, ADMIN_PASSWORD),
                "/api/v2/iam/roles/" + fixture.roleId(),
                rolePayload(fixture.roleCode(), fixture.roleName(), "SELF")
        );
        assertApiSuccess(mutationResponse);

        assertOriginalTokenRejectedWithoutWrite(fixture.accessToken(), fixture.afterProbeName());
        assertFreshTokenSeesOnlyItsOwnUserRecord(fixture);
    }

    @Test
    void rolePermissionMutationMustRollBackWhenAuthoritativeVersionBumpFails() throws Exception {
        Fixture fixture = provisionFixture("permission-bump-failure", "ALL");
        String roleScope = "authorization:role:" + fixture.roleId();
        long versionBefore = currentAuthorizationVersion(roleScope);
        Assertions.assertEquals(1, activeRolePermissionCount(fixture.roleId(), ALERTING_SILENCE_PERMISSION));

        injectAuthorizationVersionBumpFailure(roleScope);
        ResponseEntity<String> mutationResponse;
        try {
            mutationResponse = putJson(
                    login(ADMIN_USERNAME, ADMIN_PASSWORD),
                    "/api/v2/iam/roles/" + fixture.roleId() + "/permissions",
                    Map.of("permissionKeys", List.of(USER_VIEW_PERMISSION))
            );
        } finally {
            restoreAuthorizationVersionBump();
        }

        assertDependencyUnavailable(mutationResponse);
        Assertions.assertEquals(versionBefore, currentAuthorizationVersion(roleScope),
                "failed mutation advanced the authoritative role version");
        Assertions.assertEquals(1, activeRolePermissionCount(fixture.roleId(), ALERTING_SILENCE_PERMISSION),
                "role permission revocation committed although the authoritative version did not advance");
        assertProtectedSilenceAllowed(fixture.accessToken(), fixture.afterProbeName());
    }

    @Test
    void userRoleMutationMustRollBackWhenAuthoritativeVersionBumpFails() throws Exception {
        Fixture fixture = provisionFixture("user-role-bump-failure", "ALL");
        String userUuid = requiredUserUuid(fixture.userId());
        String subjectScope = "authorization:subject:" + userUuid;
        String bindingScope = "authorization:binding:" + userUuid;
        long subjectVersionBefore = currentAuthorizationVersion(subjectScope);
        long bindingVersionBefore = currentAuthorizationVersion(bindingScope);
        Assertions.assertEquals(1, activeUserRoleCount(fixture.userId(), fixture.roleId()));

        // Subject is bumped first. Failing the second, binding dimension proves that both
        // database changes and the first dimension's eagerly published Redis value roll back.
        injectAuthorizationVersionBumpFailure(bindingScope);
        ResponseEntity<String> mutationResponse;
        try {
            mutationResponse = putJson(
                    login(ADMIN_USERNAME, ADMIN_PASSWORD),
                    "/api/v2/iam/users/" + fixture.userId(),
                    userPayload(fixture.username(), List.of())
            );
        } finally {
            restoreAuthorizationVersionBump();
        }

        assertDependencyUnavailable(mutationResponse);
        Assertions.assertEquals(subjectVersionBefore, currentAuthorizationVersion(subjectScope),
                "failed mutation advanced the authoritative subject version");
        Assertions.assertEquals(bindingVersionBefore, currentAuthorizationVersion(bindingScope),
                "failed mutation advanced the authoritative binding version");
        Assertions.assertEquals(1, activeUserRoleCount(fixture.userId(), fixture.roleId()),
                "user-role removal committed although the authoritative version did not advance");
        assertProtectedSilenceAllowed(fixture.accessToken(), fixture.afterProbeName());
    }

    @Test
    void dataScopeMutationMustRollBackWhenAuthoritativeVersionBumpFails() throws Exception {
        Fixture fixture = provisionFixture("data-scope-bump-failure", "ALL");
        String roleScope = "authorization:role:" + fixture.roleId();
        String dataPolicyScope = "authorization:data-policy:role:" + fixture.roleId();
        long roleVersionBefore = currentAuthorizationVersion(roleScope);
        long dataPolicyVersionBefore = currentAuthorizationVersion(dataPolicyScope);
        Assertions.assertEquals(1, activeDataScopeCount(fixture.roleId(), "ALL"));

        // Role is bumped first. Failing the second, data-policy dimension verifies that
        // the entire multi-dimensional invalidation remains one transaction boundary.
        injectAuthorizationVersionBumpFailure(dataPolicyScope);
        ResponseEntity<String> mutationResponse;
        try {
            mutationResponse = putJson(
                    login(ADMIN_USERNAME, ADMIN_PASSWORD),
                    "/api/v2/iam/roles/" + fixture.roleId(),
                    rolePayload(fixture.roleCode(), fixture.roleName(), "SELF")
            );
        } finally {
            restoreAuthorizationVersionBump();
        }

        assertDependencyUnavailable(mutationResponse);
        Assertions.assertEquals(roleVersionBefore, currentAuthorizationVersion(roleScope),
                "failed mutation advanced the authoritative role version");
        Assertions.assertEquals(dataPolicyVersionBefore, currentAuthorizationVersion(dataPolicyScope),
                "failed mutation advanced the authoritative role data-policy version");
        Assertions.assertEquals(1, activeDataScopeCount(fixture.roleId(), "ALL"),
                "ALL data scope was retired although the authoritative version did not advance");
        Assertions.assertEquals(0, activeDataScopeCount(fixture.roleId(), "SELF"),
                "SELF data scope was committed although the authoritative version did not advance");
        assertTokenCanSeeUser(fixture.accessToken(), ADMIN_USER_ID);
    }

    @Test
    void roleAuthorizationVersionExpiresOnlySessionsContainingThatRole() throws Exception {
        Fixture changedFixture = provisionFixture("role-version-changed", "ALL");
        Fixture unrelatedFixture = provisionFixture("role-version-unrelated", "ALL");
        Assertions.assertEquals(1, activeUserRoleCount(unrelatedFixture.userId(), unrelatedFixture.roleId()));
        Assertions.assertEquals(1, activeRolePermissionCount(unrelatedFixture.roleId(), ALERTING_SILENCE_PERMISSION));
        double staleEventsBefore = metric(AuthorizationSnapshotMetricNames.AUTHZ_VERSION_STALE);
        double revokedEventsBefore = metric(AuthorizationSnapshotMetricNames.AUTHZ_SESSION_REVOKED);

        ResponseEntity<String> mutationResponse = putJson(
                login(ADMIN_USERNAME, ADMIN_PASSWORD),
                "/api/v2/iam/roles/" + changedFixture.roleId() + "/permissions",
                Map.of("permissionKeys", List.of(USER_VIEW_PERMISSION))
        );
        assertApiSuccess(mutationResponse);

        assertOriginalTokenRejectedWithoutWrite(changedFixture.accessToken(), changedFixture.afterProbeName());
        assertProtectedSilenceAllowed(unrelatedFixture.accessToken(), unrelatedFixture.afterProbeName());
        Assertions.assertEquals(staleEventsBefore + 1.0, metric(AuthorizationSnapshotMetricNames.AUTHZ_VERSION_STALE));
        Assertions.assertEquals(revokedEventsBefore + 1.0, metric(AuthorizationSnapshotMetricNames.AUTHZ_SESSION_REVOKED));
        Assertions.assertEquals(1, activeUserRoleCount(unrelatedFixture.userId(), unrelatedFixture.roleId()),
                "unrelated user's role binding changed");
        Assertions.assertEquals(1, activeRolePermissionCount(unrelatedFixture.roleId(), ALERTING_SILENCE_PERMISSION),
                "unrelated user's role permission changed");
    }

    private Fixture provisionFixture(String scenario, String scopeType) throws Exception {
        String suffix = scenario + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String roleCode = "IAM01_" + suffix.replace('-', '_').toUpperCase();
        String roleName = "IAM-01 " + suffix;
        ResponseEntity<String> roleResponse = postJson(
                login(ADMIN_USERNAME, ADMIN_PASSWORD),
                "/api/v2/iam/roles",
                rolePayload(roleCode, roleName, scopeType)
        );
        assertApiSuccess(roleResponse);
        long roleId = requiredId(roleResponse, "role");

        String username = "iam01_" + suffix.replace('-', '_');
        ResponseEntity<String> userResponse = postJson(
                login(ADMIN_USERNAME, ADMIN_PASSWORD),
                "/api/v2/iam/users",
                userPayload(username, List.of(roleId))
        );
        assertApiSuccess(userResponse);
        long userId = requiredId(userResponse, "user");

        String accessToken = login(username, USER_PASSWORD);
        return new Fixture(
                roleId,
                userId,
                roleCode,
                roleName,
                username,
                accessToken,
                "iam01-before-" + suffix,
                "iam01-after-" + suffix
        );
    }

    private Map<String, Object> rolePayload(String roleCode, String roleName, String scopeType) {
        return Map.of(
                "roleCode", roleCode,
                "roleName", roleName,
                "roleType", "CUSTOM",
                "defaultHomePath", "/dashboard/home",
                "permissionKeys", List.of(ALERTING_SILENCE_PERMISSION, USER_VIEW_PERMISSION),
                "dataScopes", List.of(Map.of("resourceCode", "*", "scopeType", scopeType))
        );
    }

    private Map<String, Object> userPayload(String username, List<Long> roleIds) {
        return Map.of(
                "username", username,
                "password", USER_PASSWORD,
                "nickname", username,
                "realName", "IAM-01 Test User",
                "status", "ENABLED",
                "roleIds", roleIds,
                "deptIds", List.of()
        );
    }

    private void assertProtectedSilenceAllowed(String accessToken, String name) throws Exception {
        Assertions.assertEquals(0, activeSilenceCount(name));
        ResponseEntity<String> response = postSilence(accessToken, name);
        Assertions.assertTrue(response.getStatusCode().is2xxSuccessful(), response.getBody());
        assertApiSuccess(response);
        Assertions.assertEquals(1, activeSilenceCount(name), "initial protected write was not committed");
    }

    private void assertOriginalTokenRejectedWithoutWrite(String accessToken, String name) throws Exception {
        Assertions.assertEquals(0, activeSilenceCount(name));
        ResponseEntity<String> response = postSilence(accessToken, name);
        int status = response.getStatusCode().value();
        Assertions.assertTrue(
                status == 401 || status == 403,
                () -> "revoked authorization was still accepted: HTTP " + status + " body=" + response.getBody()
        );
        Assertions.assertEquals(0, activeSilenceCount(name), "revoked token reached the protected write path");
    }

    private void assertFreshTokenSeesOnlyItsOwnUserRecord(Fixture fixture) throws Exception {
        ResponseEntity<String> response = get(
                login(fixture.username(), USER_PASSWORD),
                "/api/v1/system/users?pageNo=1&pageSize=100"
        );
        assertApiSuccess(response);
        JsonNode records = objectMapper.readTree(response.getBody()).path("data").path("records");
        Assertions.assertTrue(records.isArray(), response.getBody());
        Assertions.assertTrue(
                containsUserId(records, fixture.userId()),
                () -> "fresh SELF-scoped user cannot see itself: " + response.getBody()
        );
        Assertions.assertFalse(
                containsUserId(records, ADMIN_USER_ID),
                () -> "fresh SELF-scoped user can still see administrator: " + response.getBody()
        );
    }

    private void assertTokenCanSeeUser(String accessToken, long expectedUserId) throws Exception {
        ResponseEntity<String> response = get(accessToken, "/api/v1/system/users?pageNo=1&pageSize=100");
        assertApiSuccess(response);
        JsonNode records = objectMapper.readTree(response.getBody()).path("data").path("records");
        Assertions.assertTrue(records.isArray(), response.getBody());
        Assertions.assertTrue(
                containsUserId(records, expectedUserId),
                () -> "ALL-scoped session cannot see expected user after rolled-back mutation: " + response.getBody()
        );
    }

    private boolean containsUserId(JsonNode records, long expectedUserId) {
        for (JsonNode record : records) {
            if (record.path("id").asLong() == expectedUserId) {
                return true;
            }
        }
        return false;
    }

    private ResponseEntity<String> postSilence(String accessToken, String name) throws Exception {
        LocalDateTime startsAt = LocalDateTime.now().plusMinutes(5);
        LocalDateTime endsAt = startsAt.plusMinutes(5);
        return postJson(
                accessToken,
                "/api/v2/alerting/silences",
                Map.of(
                        "name", name,
                        "startsAt", startsAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        "endsAt", endsAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        "reason", "IAM-01 authorization revocation probe",
                        "enabled", false
                )
        );
    }

    private int activeSilenceCount(String name) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from alert_silence where name = ? and deleted = 0",
                Integer.class,
                name
        );
        return count == null ? 0 : count;
    }

    private int activeRolePermissionCount(long roleId, String permissionKey) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from sys_role_permission where role_id = ? and permission_key = ? and deleted = 0",
                Integer.class,
                roleId,
                permissionKey
        );
        return count == null ? 0 : count;
    }

    private int activeUserRoleCount(long userId, long roleId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from sys_user_role where user_id = ? and role_id = ? and deleted = 0",
                Integer.class,
                userId,
                roleId
        );
        return count == null ? 0 : count;
    }

    private int activeDataScopeCount(long roleId, String scopeType) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from sys_role_data_scope where role_id = ? and resource_code = '*' and scope_type = ? and deleted = 0",
                Integer.class,
                roleId,
                scopeType
        );
        return count == null ? 0 : count;
    }

    private long currentAuthorizationVersion(String scope) {
        List<Long> versions = jdbcTemplate.query(
                "select version from ddd_read_model_version where context_name = 'IAM' and scope = ?",
                (resultSet, rowNumber) -> resultSet.getLong("version"),
                scope
        );
        return versions.isEmpty() ? 0L : versions.getFirst();
    }

    private double metric(String name) {
        var counter = meterRegistry.find(name).counter();
        return counter == null ? 0.0 : counter.count();
    }

    private String requiredUserUuid(long userId) {
        String userUuid = jdbcTemplate.queryForObject(
                "select uuid from sys_user where id = ? and deleted = 0",
                String.class,
                userId
        );
        Assertions.assertNotNull(userUuid, "fixture user uuid is missing");
        return userUuid;
    }

    private void injectAuthorizationVersionBumpFailure(String scope) {
        doThrow(new IllegalStateException("IAM-01 injected authoritative version bump failure"))
                .when(readModelVersionService)
                .bump(eq("IAM"), eq(scope), anyString());
    }

    private void restoreAuthorizationVersionBump() {
        doCallRealMethod().when(readModelVersionService).bump(anyString(), anyString(), anyString());
    }

    private String login(String username, String password) throws Exception {
        String encryptedPassword = encryptPassword(password);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/api/v1/auth/login",
                new HttpEntity<>(objectMapper.writeValueAsString(Map.of("username", username, "password", encryptedPassword)), headers),
                String.class
        );
        assertApiSuccess(response);
        String accessToken = objectMapper.readTree(response.getBody()).path("data").path("accessToken").asText();
        Assertions.assertFalse(accessToken.isBlank(), "login did not return an access token");
        return accessToken;
    }

    private String encryptPassword(String password) throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl + "/api/v1/auth/login-encryption-key", String.class);
        assertApiSuccess(response);
        String publicKeyBase64 = objectMapper.readTree(response.getBody()).path("data").path("publicKey").asText();
        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64))
        );
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, OAEP_SPEC);
        return Base64.getEncoder().encodeToString(cipher.doFinal(password.getBytes(StandardCharsets.UTF_8)));
    }

    private ResponseEntity<String> postJson(String accessToken, String path, Object body) throws Exception {
        return exchange(accessToken, path, HttpMethod.POST, body);
    }

    private ResponseEntity<String> putJson(String accessToken, String path, Object body) throws Exception {
        return exchange(accessToken, path, HttpMethod.PUT, body);
    }

    private ResponseEntity<String> get(String accessToken, String path) {
        HttpHeaders headers = authorizedHeaders(accessToken);
        return restTemplate.exchange(baseUrl + path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> exchange(String accessToken, String path, HttpMethod method, Object body) throws Exception {
        HttpHeaders headers = authorizedHeaders(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                baseUrl + path,
                method,
                new HttpEntity<>(objectMapper.writeValueAsString(body), headers),
                String.class
        );
    }

    private HttpHeaders authorizedHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
    }

    private void assertApiSuccess(ResponseEntity<String> response) throws Exception {
        Assertions.assertTrue(response.getStatusCode().is2xxSuccessful(), response.getBody());
        Assertions.assertEquals("0", objectMapper.readTree(response.getBody()).path("code").asText(), response.getBody());
    }

    private void assertDependencyUnavailable(ResponseEntity<String> response) throws Exception {
        Assertions.assertEquals(503, response.getStatusCode().value(), response.getBody());
        Assertions.assertEquals("S0002", objectMapper.readTree(response.getBody()).path("code").asText(), response.getBody());
    }

    private long requiredId(ResponseEntity<String> response, String resourceName) throws Exception {
        long id = objectMapper.readTree(response.getBody()).path("data").path("id").asLong();
        Assertions.assertTrue(id > 0, () -> resourceName + " response has no id: " + response.getBody());
        return id;
    }

    private static RestTemplate createRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(response -> false);
        return restTemplate;
    }

    private static Path resolveSaasSql() {
        List<Path> candidates = List.of(
                Path.of(System.getProperty("user.dir"), "sql", "saas.sql"),
                Path.of(System.getProperty("user.dir"), "..", "..", "sql", "saas.sql")
        );
        return candidates.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .filter(Files::isRegularFile)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unable to locate lumira-backend/sql/saas.sql"));
    }

    private record Fixture(
            long roleId,
            long userId,
            String roleCode,
            String roleName,
            String username,
            String accessToken,
            String beforeProbeName,
            String afterProbeName
    ) {
    }
}

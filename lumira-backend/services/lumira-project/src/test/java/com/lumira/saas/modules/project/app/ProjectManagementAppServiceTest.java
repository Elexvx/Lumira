package com.lumira.saas.modules.project.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.TrustedCurrentUserResolver;
import com.lumira.api.dictionary.DictionaryValueNormalizer;
import com.lumira.saas.modules.project.dto.ProjectDTO;
import com.lumira.saas.modules.project.infrastructure.JdbcProjectRepository;
import com.lumira.saas.modules.project.infrastructure.persistence.ProjectSqlOperations;
import com.lumira.saas.modules.project.repository.ProjectRepository;
import com.lumira.saas.modules.project.vo.ProjectVO;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectManagementAppServiceTest {

    private static ProjectRepository repository(RecordingProjectSqlOperations sql) {
        return new JdbcProjectRepository(sql, new TestDictionaryValueNormalizer());
    }

    @Test
    void createProjectRequiresCreateOrRegistrationCreatePermissionAtServiceLayer() {
        RecordingProjectSqlOperations sql = new RecordingProjectSqlOperations();
        ProjectManagementAppService service = new ProjectManagementAppService(repository(sql));

        assertThatThrownBy(() -> service.createProject(user("aiadc:project:view"), request()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(sql.updateCallCount).isZero();
    }

    @Test
    void strictServiceRejectsWhenTrustedResolverIsUnavailable() {
        RecordingProjectSqlOperations sql = new RecordingProjectSqlOperations();
        ProjectManagementAppService service = new ProjectManagementAppService(repository(sql), null, true);

        assertThatThrownBy(() -> service.createProject(user("aiadc:project:create"), request()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("Trusted user resolver is unavailable");
        assertThat(sql.updateCallCount).isZero();
    }

    @Test
    void strictServiceRejectsRevokedOrUntrustedResolverResultBeforeDatabaseWrite() {
        RecordingProjectSqlOperations sql = new RecordingProjectSqlOperations();
        TrustedCurrentUserResolver resolver = ignored -> null;
        ProjectManagementAppService service = new ProjectManagementAppService(repository(sql), resolver, true);

        assertThatThrownBy(() -> service.createProject(user("aiadc:project:create"), request()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        assertThat(sql.updateCallCount).isZero();
    }

    @Test
    void liveResolverPermissionSnapshotWinsOverStaleCallerPermissions() {
        RecordingProjectSqlOperations sql = new RecordingProjectSqlOperations();
        CurrentUser liveUser = user("aiadc:project:view");
        ProjectManagementAppService service = new ProjectManagementAppService(repository(sql), ignored -> liveUser, true);

        assertThatThrownBy(() -> service.createProject(user("aiadc:project:create"), request()))
                .isInstanceOf(BizException.class)
                .satisfies(error -> assertThat(((BizException) error).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(sql.updateCallCount).isZero();
    }

    @Test
    void createProjectAcceptsRegistrationCreateAndWritesTrustedActorIdentity() {
        RecordingProjectSqlOperations sql = new RecordingProjectSqlOperations();
        sql.records = List.of(project());
        sql.lastInsertId = 1L;
        ProjectManagementAppService service = new ProjectManagementAppService(repository(sql), currentUser -> currentUser, true);

        assertThat(service.createProject(user("aiadc:registration:create"), request()).getId()).isEqualTo(1L);
        assertThat(sql.updateCallCount).isEqualTo(1);
        assertThat(sql.lastUpdateSql).contains("created_by_uuid", "updated_by_uuid");
        assertThat(sql.lastUpdateArgs).contains("user-uuid-2001");
    }

    @Test
    void createProjectRejectsInsertMissBeforeGeneratedIdLookup() {
        RecordingProjectSqlOperations sql = new RecordingProjectSqlOperations();
        sql.updateCount = 0;
        ProjectManagementAppService service = new ProjectManagementAppService(repository(sql));

        assertThatThrownBy(() -> service.createProject(user("aiadc:project:create"), request()))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BIZ_ERROR);
                    assertThat(exception.getMessage()).contains("Project changed, please retry");
                });
        assertThat(sql.lastInsertIdQueries).isZero();
    }

    @Test
    void projectWritesRejectInvalidInputBeforeDatabaseAccess() {
        RecordingProjectSqlOperations sql = new RecordingProjectSqlOperations();
        ProjectManagementAppService service = new ProjectManagementAppService(repository(sql));
        ProjectDTO.ProjectUpsertRequest unsafeUrl = request();
        unsafeUrl.setCtaHref("javascript:alert(1)");

        assertThatThrownBy(() -> service.createProject(user("aiadc:project:create"), null))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.createProject(user("aiadc:project:create"), unsafeUrl))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.updateProject(user("aiadc:project:update"), 0L, request()))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThat(sql.updateCallCount).isZero();
        assertThat(sql.queryCallCount).isZero();
    }

    @Test
    void updateProjectBindsOriginalCodeLocaleAndStatusForOptimisticWrite() {
        RecordingProjectSqlOperations sql = new RecordingProjectSqlOperations();
        sql.records = List.of(project());
        ProjectManagementAppService service = new ProjectManagementAppService(repository(sql));

        service.updateProject(user("*"), 1L, request());

        assertThat(sql.lastUpdateSql)
                .contains("where id = ? and code = ? and locale = ? and status = ? and deleted = 0");
        assertThat(sql.lastUpdateArgs).containsSubsequence(1L, "project-001", "zh", "published");
    }

    @Test
    void deleteProjectBindsOriginalCodeLocaleAndStatusForOptimisticWrite() {
        RecordingProjectSqlOperations sql = new RecordingProjectSqlOperations();
        sql.records = List.of(project());
        ProjectManagementAppService service = new ProjectManagementAppService(repository(sql));

        service.deleteProject(user("aiadc:project:delete"), 1L);

        assertThat(sql.lastUpdateSql)
                .contains("where id = ? and code = ? and locale = ? and status = ? and deleted = 0");
        assertThat(sql.lastUpdateArgs).containsSubsequence(1L, "project-001", "zh", "published");
    }

    @Test
    void listProjectsPreservesPagingAndNormalizesAllFilter() {
        RecordingProjectSqlOperations sql = new RecordingProjectSqlOperations();
        sql.records = List.of(project());
        sql.total = 2L;
        ProjectManagementAppService service = new ProjectManagementAppService(repository(sql));

        var page = service.listProjects(user("aiadc:project:view"), "  project ", "all", null,
                "all", "published", "zh", null, 1L, 12L);

        assertThat(page.getRecords()).extracting(ProjectVO.Project::getId).containsExactly(1L);
        assertThat(page.getTotal()).isEqualTo(2L);
        assertThat(page.getHasMore()).isFalse();
        assertThat(sql.lastQuerySql).contains("from aiadc_project where deleted = 0");
    }

    private static CurrentUser user(String permission) {
        CurrentUser currentUser = new CurrentUser();
        currentUser.setUserId(2001L);
        currentUser.setUsername("operator");
        currentUser.setSessionId("session-1");
        currentUser.setSessionVersion(1);
        currentUser.setUserUuid("user-uuid-2001");
        currentUser.setPermissionsVersion("permissions-1");
        currentUser.setAuthenticated(true);
        currentUser.setPermissions(Set.of(permission));
        return currentUser;
    }

    private static ProjectDTO.ProjectUpsertRequest request() {
        ProjectDTO.ProjectUpsertRequest request = new ProjectDTO.ProjectUpsertRequest();
        request.setCode("project-001");
        request.setTitle("Project");
        request.setCategory("demo");
        request.setLocale("zh");
        request.setRating("popular");
        request.setStatus("draft");
        return request;
    }

    private static ProjectVO.Project project() {
        ProjectVO.Project project = new ProjectVO.Project();
        project.setId(1L);
        project.setCode("project-001");
        project.setLocale("zh");
        project.setTitle("Project");
        project.setCategory("demo");
        project.setRating("popular");
        project.setSort(100);
        project.setStatus("published");
        project.setFeatured(false);
        return project;
    }

    private static final class RecordingProjectSqlOperations implements ProjectSqlOperations {
        private int updateCallCount;
        private int queryCallCount;
        private Long lastInsertId;
        private int lastInsertIdQueries;
        private int updateCount = 1;
        private long total;
        private String lastUpdateSql;
        private String lastQuerySql;
        private List<Object> lastUpdateArgs = List.of();
        private List<ProjectVO.Project> records = List.of();

        @Override
        public int update(String sql, Object... args) {
            updateCallCount += 1;
            lastUpdateSql = sql;
            lastUpdateArgs = Arrays.asList(args);
            return updateCount;
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            queryCallCount += 1;
            String dictCode = args.length == 0 ? "" : String.valueOf(args[0]);
            return switch (dictCode) {
                case "aiadc_project_locale" -> List.of(Map.of("itemValue", "zh"), Map.of("itemValue", "en"));
                case "aiadc_project_status" -> List.of(Map.of("itemValue", "draft"), Map.of("itemValue", "published"));
                case "aiadc_project_rating" -> List.of(Map.of("itemValue", "popular"), Map.of("itemValue", "excellent"));
                case "aiadc_project_filter_all" -> List.of(Map.of("itemValue", "all"));
                default -> List.of();
            };
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            queryCallCount += 1;
            lastQuerySql = sql;
            return (List<T>) records;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("last_insert_id")) {
                lastInsertIdQueries += 1;
                return requiredType.cast(lastInsertId);
            }
            return requiredType.cast(total);
        }
    }

    private static final class TestDictionaryValueNormalizer implements DictionaryValueNormalizer {
        @Override
        public List<String> enabledValues(String dictionaryCode) {
            return switch (dictionaryCode) {
                case "aiadc_project_locale" -> List.of("zh", "en");
                case "aiadc_project_status" -> List.of("draft", "published");
                case "aiadc_project_rating" -> List.of("popular", "excellent");
                case "aiadc_project_filter_all" -> List.of("all");
                default -> List.of();
            };
        }

        @Override
        public String normalizeValue(
                String dictionaryCode,
                String value,
                String defaultValue,
                boolean fallbackAllowed,
                String errorMessage
        ) {
            return value == null ? defaultValue : value;
        }
    }
}

package com.lumira.saas.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ArchitecturePersistenceBoundaryTest {
    private static final Pattern IMPORT_PATTERN = Pattern.compile("(?m)^import\\s+([\\w.]+);");
    private static final Pattern SQL_WRITE_PATTERN = Pattern.compile("(?is)\\b(?:insert\\s+into|delete\\s+from|update\\s+(?!requires\\b))\\s+`?[a-zA-Z0-9_]+`?");
    private static final Pattern APP_DIRECT_PERSISTENCE_PATTERN = Pattern.compile(
            "(?is)\\b(?:JdbcTemplate|MyBatisQueryOperations)\\b|\\bjdbcTemplate\\s*\\.\\s*(?:update|batchUpdate)\\s*\\(|\\bMyBatisQueryOperations\\s*\\.\\s*update\\s*\\(|\\bmyBatisQueryOperations\\s*\\.\\s*update\\s*\\(|\\b(?:insert\\s+into|delete\\s+from|update\\s+(?!requires\\b))\\s+`?[a-zA-Z0-9_]+`?"
    );
    private static final Pattern DIRECT_SQL_DEBT_ROW_PATTERN = Pattern.compile("(?m)^\\| `([^`]+)` \\| (?:direct SQL(?: / direct persistence dependency)?|direct persistence dependency) \\|");
    private static final Set<String> NON_APP_DIRECT_SQL_DEBT = Set.of(
            "InternalSystemController"
    );
    private static final Set<String> CONTROLLER_PERSISTENCE_DEBT = Set.of(
            "services/lumira-system/src/main/java/com/lumira/saas/modules/system/controller/InternalSystemController.java"
    );
    private static final Set<String> TEAM_APP_SERVICES = Set.of(
            "services/lumira-team/src/main/java/com/lumira/team/app/TeamAppService.java",
            "services/lumira-team/src/main/java/com/lumira/team/app/TeamInviteService.java"
    );
    private static final Set<String> HISTORICAL_DIRECT_SQL_DEBT = Set.of(
            "SystemManagementAppService",
            "SystemUserManagementAppService",
            "SystemRoleManagementAppService",
            "SystemDepartmentAppService",
            "AccountActivationService",
            "IamUserService",
            "AiAssistantEmployeeResolver",
            "DefaultDelegationGrantEvaluator",
            "AiToolPolicyService",
            "AiConversationService",
            "AiToolOrchestrationService",
            "AiKnowledgeBaseAppService",
            "AiNativeToolRuntimeService",
            "AiEmployeeRuntimeService",
            "AiIamQueryFacade",
            "AiLlmServiceConfigProvider",
            "AiManagementAppService",
            "AiOwnerMetricsService",
            "AiPlatformQueryFacade",
            "AiReadQueryService",
            "AiSkillPermissionChecker",
            "AiToolRegistry",
            "ActivityManagementAppService",
            "OperationAuditService",
            "CertificateAppService",
            "CompetitionManagementAppService",
            "CompetitionRegistrationAppService",
            "ExpertApprovalEventConsumer",
            "ExpertManagementAppService",
            "FileManagementAppService",
            "OnlineSessionManagementAppService",
            "ProjectManagementAppService",
            "DictRuntimeService",
            "SensitiveWordService",
            "SensitiveWordDictionaryCache",
            "SensitiveWordPluginStateService",
            "SystemPlatformSettingsAppService",
            "SystemProfileSettingsAppService",
            "SystemInternalApiService",
            "UserExportTaskWorkerService",
            "TeamInternalApiService",
            "TeamPermissionService",
            "WorkflowAppService",
            "WorkflowSchemaBootstrap",
            "WorkOrderFeedbackService",
            "WorkOrderFeedbackPluginStateService"
    );

    @Test
    void controllersMustNotDependOnPersistenceImplementations() throws IOException {
        Path root = repositoryRoot();
        List<String> violations = new ArrayList<>();
        for (Path file : mainJavaFiles(root).filter(path -> normalized(path).contains("/controller/")).toList()) {
            if (CONTROLLER_PERSISTENCE_DEBT.contains(normalized(root.relativize(file)))) {
                continue;
            }
            String source = Files.readString(file);
            Matcher matcher = IMPORT_PATTERN.matcher(source);
            while (matcher.find()) {
                String imported = matcher.group(1);
                if (imported.contains(".mapper.")
                        || imported.contains(".infrastructure.persistence.")
                        || imported.endsWith(".JdbcTemplate")
                        || imported.endsWith(".MyBatisQueryOperations")) {
                    violations.add(root.relativize(file) + " imports " + imported);
                }
            }
        }
        assertThat(violations)
                .as("controllers must delegate to application services/internal APIs, not persistence")
                .isEmpty();
    }

    @Test
    void appPackagesMustNotAddDirectWriteSqlOutsideDocumentedHistoricalDebt() throws IOException {
        Path root = repositoryRoot();
        Set<String> allowedHistoricalDebt = allowedHistoricalAppDirectSqlDebt();
        List<String> violations = new ArrayList<>();
        for (Path file : serviceMainAppJavaFiles(root).toList()) {
            String className = simpleClassName(file);
            if (allowedHistoricalDebt.contains(className)) {
                continue;
            }
            String source = Files.readString(file);
            if (APP_DIRECT_PERSISTENCE_PATTERN.matcher(source).find()) {
                violations.add(root.relativize(file) + " direct app persistence");
            }
        }
        assertThat(violations)
                .as("app packages must route writes through repositories/persistence adapters; only documented historical debt may keep direct persistence")
                .isEmpty();
    }

    @Test
    void teamApplicationServicesMustNotContainDirectWriteSql() throws IOException {
        Path root = repositoryRoot();
        for (String relativePath : TEAM_APP_SERVICES) {
            Path file = root.resolve(relativePath);
            String source = Files.readString(file);
            assertThat(source)
                    .as("%s must not inject low-level database helpers", relativePath)
                    .doesNotContain("JdbcTemplate")
                    .doesNotContain("MyBatisQueryOperations");
            assertThat(SQL_WRITE_PATTERN.matcher(source).find())
                    .as("%s must express writes through Team repositories", relativePath)
                    .isFalse();
        }
    }

    @Test
    void teamRepositoryImplementationsOwnTeamWriteSql() throws IOException {
        Path root = repositoryRoot();
        List<String> repositories = List.of(
                "services/lumira-team/src/main/java/com/lumira/team/infrastructure/persistence/JdbcTeamRepository.java",
                "services/lumira-team/src/main/java/com/lumira/team/infrastructure/persistence/JdbcTeamMemberRepository.java",
                "services/lumira-team/src/main/java/com/lumira/team/infrastructure/persistence/JdbcTeamInviteRepository.java",
                "services/lumira-team/src/main/java/com/lumira/team/infrastructure/persistence/JdbcTeamJoinRequestRepository.java"
        );
        for (String repository : repositories) {
            String source = Files.readString(root.resolve(repository));
            assertThat(SQL_WRITE_PATTERN.matcher(source).find())
                    .as("%s should be the persistence adapter that owns SQL writes", repository)
                    .isTrue();
        }
    }

    @Test
    void historicalDirectSqlDebtMustStayDocumentedAndTeamMustNotBeListed() throws IOException {
        Set<String> documentedDirectSqlDebt = documentedDirectSqlDebt();
        for (String debtName : HISTORICAL_DIRECT_SQL_DEBT) {
            assertThat(documentedDirectSqlDebt)
                    .as("%s must stay documented until migrated", debtName)
                    .contains(debtName);
        }
        assertThat(documentedDirectSqlDebt)
                .as("Team must not be temporarily allowed back into app direct SQL debt")
                .doesNotContain("TeamAppService", "TeamInviteService");
        assertThat(documentedDirectSqlDebt)
                .as("direct SQL debt list may only contain the known historical app debt plus explicitly tracked non-app debt")
                .isSubsetOf(union(HISTORICAL_DIRECT_SQL_DEBT, NON_APP_DIRECT_SQL_DEBT));
    }

    @Test
    void commonLibrariesMustNotDependOnServiceModules() throws IOException {
        Path root = repositoryRoot();
        List<String> violations = new ArrayList<>();
        for (Path pom : Files.walk(root.resolve("libs")).filter(Files::isRegularFile).filter(path -> path.getFileName().toString().equals("pom.xml")).toList()) {
            String text = Files.readString(pom).toLowerCase(Locale.ROOT);
            if (text.contains("<artifactid>lumira-system</artifactid>")
                    || text.contains("<artifactid>lumira-admin</artifactid>")
                    || text.contains("<artifactid>team-service</artifactid>")) {
                violations.add(root.relativize(pom).toString());
            }
        }
        assertThat(violations)
                .as("common libs may expose contracts, but must not depend on service implementations")
                .isEmpty();
    }

    private static Stream<Path> mainJavaFiles(Path root) throws IOException {
        return Stream.concat(Files.walk(root.resolve("services")), Files.walk(root.resolve("libs")))
                .filter(Files::isRegularFile)
                .filter(path -> normalized(path).contains("/src/main/java/"))
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !normalized(path).contains("/target/"));
    }

    private static Set<String> documentedDirectSqlDebt() throws IOException {
        Path debtFile = repositoryRoot().getParent().resolve("doc/architecture/persistence-boundary-debt.md");
        String debt = Files.readString(debtFile);
        Matcher matcher = DIRECT_SQL_DEBT_ROW_PATTERN.matcher(debt);
        Set<String> debtNames = new java.util.LinkedHashSet<>();
        while (matcher.find()) {
            debtNames.add(matcher.group(1));
        }
        return debtNames;
    }

    private static Set<String> allowedHistoricalAppDirectSqlDebt() throws IOException {
        Set<String> documentedDebt = documentedDirectSqlDebt();
        Set<String> allowedDebt = new java.util.LinkedHashSet<>(documentedDebt);
        allowedDebt.retainAll(HISTORICAL_DIRECT_SQL_DEBT);
        return allowedDebt;
    }

    private static Stream<Path> serviceMainAppJavaFiles(Path root) throws IOException {
        return Files.walk(root.resolve("services"))
                .filter(Files::isRegularFile)
                .filter(path -> normalized(path).contains("/src/main/java/"))
                .filter(path -> normalized(path).contains("/app/"))
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !normalized(path).contains("/target/"));
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        Set<String> result = new java.util.LinkedHashSet<>(first);
        result.addAll(second);
        return result;
    }

    private static String simpleClassName(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.substring(0, fileName.length() - ".java".length());
    }

    private static Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.exists(current.resolve("services")) && Files.exists(current.resolve("libs"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate lumira-backend root");
    }

    private static String normalized(Path path) {
        return path.toString().replace('\\', '/');
    }
}

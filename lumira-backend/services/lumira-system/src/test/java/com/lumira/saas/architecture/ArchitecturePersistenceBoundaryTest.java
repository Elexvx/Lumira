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
            "IamUserService",
            "AiToolPolicyService",
            "AiConversationService",
            "AiToolOrchestrationService",
            "AiKnowledgeBaseAppService",
            "AiNativeToolRuntimeService",
            "SensitiveWordService"
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
        Path debtFile = repositoryRoot().getParent().resolve("doc/architecture/persistence-boundary-debt.md");
        String debt = Files.readString(debtFile);
        for (String debtName : HISTORICAL_DIRECT_SQL_DEBT) {
            assertThat(debt)
                    .as("%s must stay documented until migrated", debtName)
                    .contains(debtName);
        }
        assertThat(debt)
                .doesNotContain("TeamAppService direct SQL")
                .doesNotContain("TeamInviteService direct SQL");
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

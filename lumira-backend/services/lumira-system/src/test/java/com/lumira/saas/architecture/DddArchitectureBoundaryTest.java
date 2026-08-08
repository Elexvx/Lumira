package com.lumira.saas.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DddArchitectureBoundaryTest {

    private static final Path OWNER_TABLE_MANIFEST = Path.of("../doc/27-ddd-owner-table-manifest.csv");
    private static final Set<String> PER_PRODUCER_TABLE_NAMES = Set.of("platform_event_outbox");
    private static final Pattern TABLE_DDL_PATTERN = Pattern.compile(
            "(?i)\\b(?:create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?|alter\\s+table\\s+)`?([a-zA-Z0-9_]+)`?");
    private static final Pattern SQL_WRITE_TABLE_PATTERN = Pattern.compile(
            "(?is)(?:\\b(?:insert\\s+into|delete\\s+from)\\s+|(?:\\A|;)\\s*update\\s+)`?([a-zA-Z0-9_]+)`?");
    private static final Pattern SQL_READ_TABLE_PATTERN = Pattern.compile(
            "(?is)\\b(?:from|join)\\s+`?([a-zA-Z0-9_]+)`?");
    private static final Pattern MYBATIS_WRITE_STATEMENT_PATTERN = Pattern.compile(
            "(?is)<(?:insert|update|delete)\\b[^>]*>(.*?)</(?:insert|update|delete)>");
    private static final Pattern MESSAGE_FORBIDDEN_OWNER_READ_PATTERN = Pattern.compile(
            "(?is)\\b(?:from|join)\\s+`?(sys_user|sys_user_role|sys_role|sys_config|audit_operation_log)`?");
    private static final Set<String> CROSS_OWNER_SQL_GUARDED_MODULES = Set.of("lumira-competition");
    private static final Set<String> RUNTIME_SQL_OWNERSHIP_GUARDED_MODULES = Set.of(
            "lumira-activity",
            "lumira-competition",
            "lumira-project",
            "lumira-expert",
            "lumira-workflow",
            "lumira-export",
            "lumira-event-catalog"
    );
    private static final Map<String, Set<String>> EXPLICIT_RUNTIME_TABLE_ACCESS_MODULES = Map.of(
            "platform_event_outbox", Set.of("lumira-system", "lumira-file"),
            "event_consumer_receipt", Set.of("lumira-message")
    );
    private static final Pattern TABLE_NAME_ENTITY_PATTERN = Pattern.compile(
            "(?s)@TableName\\(\"([^\"]+)\"\\)\\s+public\\s+(?:static\\s+)?class\\s+(\\w+)");
    private static final Pattern IMPORT_PATTERN = Pattern.compile("(?m)^import\\s+([\\w.]+);");
    private static final Pattern BASE_MAPPER_ENTITY_PATTERN = Pattern.compile("BaseMapper<([\\w]+)>");
    private static final Pattern MAPPER_VARIABLE_PATTERN = Pattern.compile(
            "\\b(\\w+Mapper)\\s+(\\w+)\\s*(?:[;,)=]|$)");
    private static final Set<String> BASE_MAPPER_WRITE_METHODS = Set.of(
            "insert",
            "delete",
            "deleteById",
            "deleteBatchIds",
            "deleteByMap",
            "update",
            "updateById",
            "insertOrUpdate"
    );
    private static final Set<String> EXPECTED_CONTEXTS = Set.of(
            "AUTH",
            "IAM",
            "PLATFORM",
            "MESSAGE",
            "FILE",
            "PLUGIN",
            "LOCALIZATION",
            "PAYMENT",
            "AI",
            "TEAM",
            "ACTIVITY",
            "COMPETITION",
            "CATALOG",
            "PROJECT",
            "EXPERT",
            "WORKFLOW",
            "EXPORT",
            "JOB"
    );
    private static final Map<String, List<String>> SPLIT_TARGET_PERSISTENCE_IMPORT_PREFIXES = Map.ofEntries(
            Map.entry("lumira-auth", List.of("com.lumira.auth.mapper.", "com.lumira.auth.entity.")),
            Map.entry("lumira-message", List.of("com.lumira.message.mapper.", "com.lumira.message.entity.")),
            Map.entry("lumira-file", List.of("com.lumira.file.mapper.", "com.lumira.file.entity.")),
            Map.entry("lumira-plugin", List.of("com.lumira.saas.modules.plugin.mapper.", "com.lumira.saas.modules.plugin.entity.")),
            Map.entry("lumira-localization", List.of("com.lumira.localization.mapper.", "com.lumira.localization.entity.")),
            Map.entry("lumira-payment", List.of("com.lumira.payment.mapper.", "com.lumira.payment.entity.")),
            Map.entry("lumira-ai", List.of("com.lumira.ai.mapper.", "com.lumira.ai.entity.")),
            Map.entry("lumira-team", List.of("com.lumira.team.mapper.", "com.lumira.team.entity.")),
            Map.entry("lumira-activity", List.of("com.lumira.saas.modules.activity.mapper.", "com.lumira.saas.modules.activity.entity.")),
            Map.entry("lumira-competition", List.of(
                    "com.lumira.saas.modules.competition.mapper.",
                    "com.lumira.saas.modules.competition.entity.",
                    "com.lumira.saas.modules.review.mapper.",
                    "com.lumira.saas.modules.review.entity.")),
            Map.entry("lumira-project", List.of("com.lumira.saas.modules.project.mapper.", "com.lumira.saas.modules.project.entity.")),
            Map.entry("lumira-expert", List.of("com.lumira.saas.modules.expert.mapper.", "com.lumira.saas.modules.expert.entity.")),
            Map.entry("lumira-export", List.of("com.lumira.saas.modules.export."))
    );
    private static final Map<String, String> BOUNDED_CONTEXT_SERVICE_ARTIFACT_IDS = Map.ofEntries(
            Map.entry("lumira-system", "system-service"),
            Map.entry("lumira-auth", "auth-service"),
            Map.entry("lumira-message", "message-service"),
            Map.entry("lumira-file", "file-service"),
            Map.entry("lumira-plugin", "plugin-service"),
            Map.entry("lumira-localization", "localization-service"),
            Map.entry("lumira-payment", "payment-service"),
            Map.entry("lumira-ai", "ai-service"),
            Map.entry("lumira-team", "team-service"),
            Map.entry("lumira-activity", "activity-service"),
            Map.entry("lumira-competition", "competition-service"),
            Map.entry("lumira-event-catalog", "event-catalog-service"),
            Map.entry("lumira-project", "project-service"),
            Map.entry("lumira-expert", "expert-service"),
            Map.entry("lumira-workflow", "workflow-service"),
            Map.entry("lumira-export", "export-service"),
            Map.entry("lumira-quartz", "job-executor")
    );
    private static final Pattern MAVEN_DEPENDENCY_PATTERN = Pattern.compile(
            "(?s)<dependency>.*?<groupId>com\\.lumira</groupId>.*?<artifactId>([^<]+)</artifactId>.*?</dependency>");
    private static final Pattern PARENT_POM_PATTERN = Pattern.compile("(?s)<parent>.*?</parent>");
    private static final Pattern PROJECT_ARTIFACT_ID_PATTERN = Pattern.compile("<artifactId>\\s*([^<\\s]+)\\s*</artifactId>");

    private static final List<String> FORBIDDEN_DOMAIN_IMPORTS = List.of(
            "org.springframework.",
            "com.baomidou.",
            "jakarta.servlet.",
            "javax.servlet.",
            "JdbcTemplate",
            "RestTemplate",
            "WebClient",
            "FeignClient",
            ".mapper.",
            "Mapper;",
            "RedisTemplate",
            "StringRedisTemplate",
            "@Service",
            "@Component",
            "@Mapper",
            "@RestController"
    );
    private static final List<String> ACTIVITY_FORBIDDEN_SYSTEM_IMPLEMENTATION_IMPORT_PREFIXES = List.of(
            "com.lumira.saas.common.",
            "com.lumira.saas.infrastructure.",
            "com.lumira.saas.modules.iam.",
            "com.lumira.saas.modules.system."
    );
    private static final List<String> COMPETITION_FORBIDDEN_SYSTEM_IMPLEMENTATION_IMPORT_PREFIXES = List.of(
            "com.lumira.saas.common.",
            "com.lumira.saas.infrastructure.",
            "com.lumira.saas.modules.iam.",
            "com.lumira.saas.modules.system."
    );
    private static final List<String> WORKFLOW_FORBIDDEN_SYSTEM_IMPLEMENTATION_IMPORT_PREFIXES = List.of(
            "com.lumira.saas.common.",
            "com.lumira.saas.infrastructure.",
            "com.lumira.saas.modules.iam.",
            "com.lumira.saas.modules.system."
    );
    private static final List<String> PROJECT_FORBIDDEN_SYSTEM_IMPLEMENTATION_IMPORT_PREFIXES = List.of(
            "com.lumira.saas.common.",
            "com.lumira.saas.infrastructure.",
            "com.lumira.saas.modules.iam.",
            "com.lumira.saas.modules.system."
    );
    private static final List<String> EVENT_CATALOG_FORBIDDEN_OWNER_IMPORT_PREFIXES = List.of(
            "com.lumira.saas.modules.activity.",
            "com.lumira.saas.modules.competition.",
            "com.lumira.saas.modules.system.",
            "com.lumira.saas.infrastructure."
    );
    private static final List<String> EXPERT_FORBIDDEN_SYSTEM_IMPLEMENTATION_IMPORT_PREFIXES = List.of(
            "com.lumira.saas.common.",
            "com.lumira.saas.infrastructure.",
            "com.lumira.saas.modules.iam.",
            "com.lumira.saas.modules.system."
    );
    private static final List<String> EXPORT_FORBIDDEN_SYSTEM_IMPLEMENTATION_IMPORT_PREFIXES = List.of(
            "com.lumira.saas.common.",
            "com.lumira.saas.infrastructure.",
            "com.lumira.saas.modules.iam.",
            "com.lumira.saas.modules.system."
    );
    private static final List<String> AI_FORBIDDEN_SYSTEM_IMPLEMENTATION_IMPORT_PREFIXES = List.of(
            "com.lumira.saas.common.",
            "com.lumira.saas.infrastructure.",
            "com.lumira.saas.modules.account.",
            "com.lumira.saas.modules.iam.",
            "com.lumira.saas.modules.platform.",
            "com.lumira.saas.modules.system."
    );

    @Test
    void dddDomainModelPackagesStayFrameworkFree() throws IOException {
        Path root = repositoryRoot();
        List<Path> domainFiles = javaFiles(root)
                .filter(path -> normalized(path).contains("/src/main/java/"))
                .filter(path -> {
                    String normalized = normalized(path);
                    return normalized.contains("/domain/model/")
                            || normalized.contains("/domain/event/")
                            || normalized.contains("/domain/repository/")
                            || normalized.contains("/domain/valueobject/");
                })
                .toList();

        assertThat(domainFiles).isNotEmpty();
        for (Path file : domainFiles) {
            String source = Files.readString(file);
            for (String forbidden : FORBIDDEN_DOMAIN_IMPORTS) {
                assertThat(source)
                        .as("%s must not contain %s", root.relativize(file), forbidden)
                        .doesNotContain(forbidden);
            }
        }
    }

    @Test
    void allBackendBoundedContextsHaveDomainModelAnchors() {
        Path root = repositoryRoot();
        List<String> anchors = List.of(
                "services/lumira-system/src/main/java/com/lumira/saas/modules/iam/domain/model/IamDomainModels.java",
                "services/lumira-auth/src/main/java/com/lumira/auth/domain/model/AuthDomainModels.java",
                "services/lumira-system/src/main/java/com/lumira/saas/modules/platform/domain/model/PlatformDomainModels.java",
                "services/lumira-message/src/main/java/com/lumira/message/domain/model/MessageDomainModels.java",
                "services/lumira-file/src/main/java/com/lumira/file/domain/model/FileDomainModels.java",
                "services/lumira-plugin/src/main/java/com/lumira/saas/modules/plugin/domain/model/PluginDomainModels.java",
                "services/lumira-localization/src/main/java/com/lumira/localization/domain/model/LocalizationDomainModels.java",
                "services/lumira-payment/src/main/java/com/lumira/payment/domain/model/PaymentDomainModels.java",
                "services/lumira-team/src/main/java/com/lumira/team/domain/model/TeamDomainModels.java",
                "services/lumira-event-catalog/src/main/java/com/lumira/saas/modules/eventcatalog/domain/model/EventCatalogDomainModels.java",
                "services/lumira-ai/src/main/java/com/lumira/saas/modules/ai/domain/model/AiAssistantDomainModels.java",
                "services/lumira-quartz/src/main/java/com/lumira/job/domain/model/JobDomainModels.java"
        );

        assertThat(anchors)
                .allSatisfy(anchor -> assertThat(root.resolve(anchor)).exists());
    }

    @Test
    void futureInterfacesLayerMustNotImportMappers() throws IOException {
        Path root = repositoryRoot();
        List<Path> interfaceFiles = javaFiles(root)
                .filter(path -> normalized(path).contains("/src/main/java/"))
                .filter(path -> normalized(path).contains("/interfaces/"))
                .toList();

        for (Path file : interfaceFiles) {
            String source = Files.readString(file).toLowerCase(Locale.ROOT);
            assertThat(source)
                    .as("%s must not import persistence mappers", root.relativize(file))
                    .doesNotContain(".mapper.")
                    .doesNotContain("mapper;");
        }
    }

    @Test
    void controllersMustDelegateToApplicationServicesInsteadOfMappers() throws IOException {
        Path root = repositoryRoot();
        List<Path> controllerFiles = javaFiles(root)
                .filter(path -> normalized(path).contains("/src/main/java/"))
                .filter(path -> normalized(path).contains("/controller/")
                        || normalized(path).contains("/interfaces/rest/"))
                .filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                .toList();

        assertThat(controllerFiles).isNotEmpty();
        for (Path file : controllerFiles) {
            String source = Files.readString(file);
            Matcher importMatcher = IMPORT_PATTERN.matcher(source);
            List<String> mapperImports = new ArrayList<>();
            while (importMatcher.find()) {
                String fqcn = importMatcher.group(1);
                if (fqcn.contains(".mapper.")) {
                    mapperImports.add(fqcn);
                }
            }
        assertThat(mapperImports)
                    .as("%s must delegate through application/internal APIs instead of importing persistence mappers", root.relativize(file))
                    .isEmpty();
        }
    }

    @Test
    void splitTargetModulesMustNotImportOtherContextsPersistenceTypes() throws IOException {
        Path root = repositoryRoot();
        List<String> violations = new ArrayList<>();

        for (Map.Entry<String, List<String>> moduleRule : SPLIT_TARGET_PERSISTENCE_IMPORT_PREFIXES.entrySet()) {
            Path moduleRoot = root.resolve("services").resolve(moduleRule.getKey()).resolve("src/main/java");
            if (!Files.exists(moduleRoot)) {
                continue;
            }
            List<Path> files = javaFiles(moduleRoot).toList();
            for (Path file : files) {
                Matcher importMatcher = IMPORT_PATTERN.matcher(Files.readString(file));
                while (importMatcher.find()) {
                    String fqcn = importMatcher.group(1);
                    if (!fqcn.startsWith("com.lumira.")) {
                        continue;
                    }
                    if (!fqcn.contains(".mapper.") && !fqcn.contains(".entity.")) {
                        continue;
                    }
                    boolean allowed = moduleRule.getValue().stream().anyMatch(fqcn::startsWith);
                    if (!allowed) {
                        violations.add(root.relativize(file) + " imports " + fqcn
                                + "; use owner API, event projection, or local read model");
                    }
                }
            }
        }

        assertThat(violations).isEmpty();
    }

    @Test
    void boundedContextServiceArtifactMappingsMustMatchTheirPoms() throws IOException {
        Path root = repositoryRoot();
        List<String> declaredArtifactIds = new ArrayList<>();

        for (Map.Entry<String, String> moduleRule : BOUNDED_CONTEXT_SERVICE_ARTIFACT_IDS.entrySet()) {
            Path pom = root.resolve("services").resolve(moduleRule.getKey()).resolve("pom.xml");
            assertThat(pom)
                    .as("bounded context module %s must have a Maven POM", moduleRule.getKey())
                    .exists();
            String artifactId = projectArtifactId(Files.readString(pom));
            declaredArtifactIds.add(artifactId);
            assertThat(artifactId)
                    .as("%s must use the artifactId recorded by the architecture guard", root.relativize(pom))
                    .isEqualTo(moduleRule.getValue());
        }

        assertThat(declaredArtifactIds)
                .as("bounded context service artifactIds must remain unique")
                .doesNotHaveDuplicates();
    }

    @Test
    void serviceDependencyInspectionUsesActualPomArtifactIds() {
        String fixturePom = """
                <project>
                  <dependencies>
                    <dependency>
                      <groupId>com.lumira</groupId>
                      <artifactId>auth-service</artifactId>
                    </dependency>
                  </dependencies>
                </project>
                """;

        assertThat(serviceImplementationDependencies(fixturePom))
                .containsExactly("auth-service");
    }

    @Test
    void boundedContextModulesMustNotDependOnOtherServiceImplementations() throws IOException {
        Path root = repositoryRoot();
        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, String> moduleRule : BOUNDED_CONTEXT_SERVICE_ARTIFACT_IDS.entrySet()) {
            Path pom = root.resolve("services").resolve(moduleRule.getKey()).resolve("pom.xml");
            for (String artifactId : serviceImplementationDependencies(Files.readString(pom))) {
                if (!artifactId.equals(moduleRule.getValue())) {
                    violations.add(root.relativize(pom) + " depends on service implementation " + artifactId
                            + "; use lumira-api/plugin-api contracts, internal HTTP, or event projection");
                }
            }
        }

        assertThat(violations).isEmpty();
    }

    @Test
    void activityModuleMustNotImportSystemServiceImplementations() throws IOException {
        Path root = repositoryRoot();
        List<String> violations = new ArrayList<>();
        List<Path> activitySources = javaFiles(root)
                .filter(path -> normalized(path).contains("/services/lumira-activity/src/main/java/"))
                .toList();

        assertThat(activitySources).isNotEmpty();
        for (Path activitySource : activitySources) {
            Matcher matcher = IMPORT_PATTERN.matcher(Files.readString(activitySource));
            while (matcher.find()) {
                String imported = matcher.group(1);
                for (String forbiddenPrefix : ACTIVITY_FORBIDDEN_SYSTEM_IMPLEMENTATION_IMPORT_PREFIXES) {
                    if (imported.startsWith(forbiddenPrefix)) {
                        violations.add(root.relativize(activitySource) + " imports " + imported);
                    }
                }
            }
        }

        assertThat(violations)
                .as("activity-service must depend on shared contracts/ports, not system-service implementation packages")
                .isEmpty();
    }

    @Test
    void competitionModuleMustNotImportSystemServiceImplementations() throws IOException {
        Path root = repositoryRoot();
        List<String> violations = new ArrayList<>();
        List<Path> competitionSources = javaFiles(root)
                .filter(path -> normalized(path).contains("/services/lumira-competition/src/main/java/"))
                .toList();

        assertThat(competitionSources).isNotEmpty();
        for (Path competitionSource : competitionSources) {
            Matcher matcher = IMPORT_PATTERN.matcher(Files.readString(competitionSource));
            while (matcher.find()) {
                String imported = matcher.group(1);
                for (String forbiddenPrefix : COMPETITION_FORBIDDEN_SYSTEM_IMPLEMENTATION_IMPORT_PREFIXES) {
                    if (imported.startsWith(forbiddenPrefix)) {
                        violations.add(root.relativize(competitionSource) + " imports " + imported);
                    }
                }
            }
        }

        assertThat(violations)
                .as("competition-service must depend on shared contracts/ports, not system-service implementation packages")
                .isEmpty();
    }

    @Test
    void eventCatalogModuleMustDependOnlyOnSharedContractsNotOwnerImplementations() throws IOException {
        Path root = repositoryRoot();
        List<String> violations = new ArrayList<>();
        List<Path> catalogSources = javaFiles(root)
                .filter(path -> normalized(path).contains("/services/lumira-event-catalog/src/main/java/"))
                .toList();

        assertThat(catalogSources).isNotEmpty();
        for (Path catalogSource : catalogSources) {
            Matcher matcher = IMPORT_PATTERN.matcher(Files.readString(catalogSource));
            while (matcher.find()) {
                String imported = matcher.group(1);
                for (String forbiddenPrefix : EVENT_CATALOG_FORBIDDEN_OWNER_IMPORT_PREFIXES) {
                    if (imported.startsWith(forbiddenPrefix)) {
                        violations.add(root.relativize(catalogSource) + " imports " + imported);
                    }
                }
            }
        }

        assertThat(violations)
                .as("event-catalog-service must use only shared event/query/source ports, never source-owner implementations")
                .isEmpty();
    }

    @Test
    void competitionModuleMustNotAccessExportOwnedTaskTableDirectly() throws IOException {
        Path root = repositoryRoot();
        List<String> violations = javaFiles(root.resolve("services/lumira-competition/src/main/java"))
                .filter(path -> {
                    try {
                        return Files.readString(path).contains("sys_export_task");
                    } catch (IOException exception) {
                        throw new IllegalStateException("Unable to inspect " + path, exception);
                    }
                })
                .map(root::relativize)
                .map(Path::toString)
                .toList();

        assertThat(violations)
                .as("competition-service must use ExportTaskQueuePort rather than accessing Export-owned sys_export_task")
                .isEmpty();
    }

    @Test
    void workflowModuleMustNotImportSystemServiceImplementations() throws IOException {
        Path root = repositoryRoot();
        List<String> violations = new ArrayList<>();
        List<Path> workflowSources = javaFiles(root)
                .filter(path -> normalized(path).contains("/services/lumira-workflow/src/main/java/"))
                .toList();

        assertThat(workflowSources).isNotEmpty();
        for (Path workflowSource : workflowSources) {
            Matcher matcher = IMPORT_PATTERN.matcher(Files.readString(workflowSource));
            while (matcher.find()) {
                String imported = matcher.group(1);
                for (String forbiddenPrefix : WORKFLOW_FORBIDDEN_SYSTEM_IMPLEMENTATION_IMPORT_PREFIXES) {
                    if (imported.startsWith(forbiddenPrefix)) {
                        violations.add(root.relativize(workflowSource) + " imports " + imported);
                    }
                }
            }
        }

        assertThat(violations)
                .as("workflow-service must depend on shared contracts/ports, not system-service implementation packages")
                .isEmpty();
    }

    @Test
    void projectModuleMustNotImportSystemServiceImplementations() throws IOException {
        Path root = repositoryRoot();
        List<String> violations = new ArrayList<>();
        List<Path> projectSources = javaFiles(root)
                .filter(path -> normalized(path).contains("/services/lumira-project/src/main/java/"))
                .toList();

        assertThat(projectSources).isNotEmpty();
        for (Path projectSource : projectSources) {
            Matcher matcher = IMPORT_PATTERN.matcher(Files.readString(projectSource));
            while (matcher.find()) {
                String imported = matcher.group(1);
                for (String forbiddenPrefix : PROJECT_FORBIDDEN_SYSTEM_IMPLEMENTATION_IMPORT_PREFIXES) {
                    if (imported.startsWith(forbiddenPrefix)) {
                        violations.add(root.relativize(projectSource) + " imports " + imported);
                    }
                }
            }
        }

        assertThat(violations)
                .as("project-service must depend on shared contracts/ports, not system-service implementation packages")
                .isEmpty();
    }

    @Test
    void expertModuleMustNotImportSystemOrWorkflowServiceImplementations() throws IOException {
        Path root = repositoryRoot();
        List<String> violations = new ArrayList<>();
        List<Path> expertSources = javaFiles(root)
                .filter(path -> normalized(path).contains("/services/lumira-expert/src/main/java/"))
                .toList();

        assertThat(expertSources).isNotEmpty();
        for (Path expertSource : expertSources) {
            Matcher matcher = IMPORT_PATTERN.matcher(Files.readString(expertSource));
            while (matcher.find()) {
                String imported = matcher.group(1);
                for (String forbiddenPrefix : EXPERT_FORBIDDEN_SYSTEM_IMPLEMENTATION_IMPORT_PREFIXES) {
                    if (imported.startsWith(forbiddenPrefix)) {
                        violations.add(root.relativize(expertSource) + " imports " + imported);
                    }
                }
                if (imported.startsWith("com.lumira.saas.modules.workflow.")) {
                    violations.add(root.relativize(expertSource) + " imports " + imported);
                }
            }
        }

        assertThat(violations)
                .as("expert-service must use common contracts rather than System or Workflow implementation packages")
                .isEmpty();
    }

    @Test
    void exportModuleMustNotImportSystemServiceImplementations() throws IOException {
        Path root = repositoryRoot();
        List<String> violations = new ArrayList<>();
        List<Path> exportSources = javaFiles(root)
                .filter(path -> normalized(path).contains("/services/lumira-export/src/main/java/"))
                .toList();

        assertThat(exportSources).isNotEmpty();
        for (Path exportSource : exportSources) {
            Matcher matcher = IMPORT_PATTERN.matcher(Files.readString(exportSource));
            while (matcher.find()) {
                String imported = matcher.group(1);
                for (String forbiddenPrefix : EXPORT_FORBIDDEN_SYSTEM_IMPLEMENTATION_IMPORT_PREFIXES) {
                    if (imported.startsWith(forbiddenPrefix)) {
                        violations.add(root.relativize(exportSource) + " imports " + imported);
                    }
                }
            }
        }

        assertThat(violations)
                .as("export-service must depend on shared contracts/ports, not system-service implementation packages")
                .isEmpty();
    }

    @Test
    void aiModuleMustNotImportSystemServiceImplementations() throws IOException {
        Path root = repositoryRoot();
        List<String> violations = new ArrayList<>();
        List<Path> aiSources = javaFiles(root)
                .filter(path -> normalized(path).contains("/services/lumira-ai/src/main/java/"))
                .toList();

        assertThat(aiSources).isNotEmpty();
        for (Path aiSource : aiSources) {
            Matcher matcher = IMPORT_PATTERN.matcher(Files.readString(aiSource));
            while (matcher.find()) {
                String imported = matcher.group(1);
                for (String forbiddenPrefix : AI_FORBIDDEN_SYSTEM_IMPLEMENTATION_IMPORT_PREFIXES) {
                    if (imported.startsWith(forbiddenPrefix)) {
                        violations.add(root.relativize(aiSource) + " imports " + imported);
                    }
                }
            }
        }

        assertThat(violations)
                .as("ai-service must use shared contracts and System adapters rather than System implementation packages")
                .isEmpty();
    }

    @Test
    void ownerTableManifestDefinesEveryBoundedContextAndNoBusinessTableIsOwnedByAdapters() throws IOException {
        List<OwnerTableRule> rules = ownerTableRules(repositoryRoot());

        assertThat(rules)
                .extracting(OwnerTableRule::context)
                .containsExactlyInAnyOrderElementsOf(EXPECTED_CONTEXTS);
        assertThat(rules)
                .filteredOn(rule -> !rule.tablePatterns().equals(List.of("-")))
                .allSatisfy(rule -> assertThat(rule.ownerModule())
                        .as("%s must have a real bounded-context owner module", rule.context())
                        .isNotIn("lumira-admin", "lumira-quartz"));

        Map<String, String> activeOwnersByPattern = new LinkedHashMap<>();
        for (OwnerTableRule rule : rules) {
            if (rule.tablePatterns().equals(List.of("-"))) {
                continue;
            }
            for (String pattern : rule.tablePatterns()) {
                if (PER_PRODUCER_TABLE_NAMES.contains(pattern)) {
                    continue;
                }
                assertThat(activeOwnersByPattern)
                        .as("table pattern %s has more than one active owner", pattern)
                        .doesNotContainKey(pattern);
                activeOwnersByPattern.put(pattern, rule.ownerModule());
            }
        }
    }

    @Test
    void migrationTableWritesMustBeDeclaredInOwnerManifest() throws IOException {
        Path root = repositoryRoot();
        List<OwnerTableRule> rules = ownerTableRules(root);
        List<Path> activeMigrationFiles = Files.walk(root.resolve("services"))
                .filter(Files::isRegularFile)
                .filter(path -> normalized(path).contains("/src/main/resources/db/migration/"))
                .filter(path -> path.toString().endsWith(".sql"))
                .toList();

        assertThat(activeMigrationFiles)
                .as("Flyway is disabled before launch; active service migrations should be empty")
                .isEmpty();
        List<String> violations = new ArrayList<>();

        assertThat(violations).isEmpty();
    }

    @Test
    void explicitSqlWritePathsMustStayInsideRuntimeOwnerOrNarrowTableException() throws IOException {
        Path root = repositoryRoot();
        List<OwnerTableRule> rules = ownerTableRules(root);
        List<String> violations = new ArrayList<>();

        List<Path> mapperXmlFiles = Files.walk(root.resolve("services"))
                .filter(Files::isRegularFile)
                .filter(path -> normalized(path).contains("/src/main/resources/mapper/"))
                .filter(path -> path.toString().endsWith(".xml"))
                .toList();
        for (Path mapperXmlFile : mapperXmlFiles) {
            String module = serviceModuleName(root, mapperXmlFile);
            Matcher matcher = MYBATIS_WRITE_STATEMENT_PATTERN.matcher(Files.readString(mapperXmlFile));
            while (matcher.find()) {
                assertExplicitSqlWriteAllowed(root, rules, violations, module, mapperXmlFile, matcher.group(1));
            }
        }

        List<Path> javaFiles = javaFiles(root)
                .filter(path -> normalized(path).contains("/src/main/java/"))
                .filter(path -> normalized(path).contains("/services/"))
                .filter(path -> RUNTIME_SQL_OWNERSHIP_GUARDED_MODULES.contains(serviceModuleName(root, path)))
                .filter(path -> !normalized(path).contains("/target/"))
                .toList();
        for (Path javaFile : javaFiles) {
            String source = Files.readString(javaFile);
            List<SqlTableAccess> writes = sqlTableAccessesInJavaSource(source).stream()
                    .filter(access -> access.kind() == SqlAccessKind.WRITE)
                    .toList();
            assertRuntimeSqlAccessesAllowed(
                    root,
                    rules,
                    violations,
                    serviceModuleName(root, javaFile),
                    javaFile,
                    writes
            );
        }

        assertThat(violations).isEmpty();
    }

    @Test
    void competitionRuntimeSqlMustUseOwnerPortsOrReadModels() throws IOException {
        Path root = repositoryRoot();
        List<OwnerTableRule> rules = ownerTableRules(root);
        List<String> violations = new ArrayList<>();

        for (String module : CROSS_OWNER_SQL_GUARDED_MODULES) {
            Path sourceRoot = root.resolve("services").resolve(module).resolve("src/main");
            assertThat(sourceRoot).as("SQL ownership guard source root for %s", module).exists();
            try (Stream<Path> files = Files.walk(sourceRoot)) {
                for (Path sourceFile : files.filter(Files::isRegularFile).toList()) {
                    String fileName = sourceFile.getFileName().toString();
                    String source = Files.readString(sourceFile);
                    List<SqlTableAccess> accesses = fileName.endsWith(".java")
                            ? sqlTableAccessesInJavaSource(source)
                            : fileName.endsWith(".xml")
                                    ? sqlTableAccessesInSqlText(source)
                                    : List.of();
                    assertRuntimeSqlAccessesAllowed(root, rules, violations, module, sourceFile, accesses);
                }
            }
        }

        assertThat(violations)
                .as("competition-service must use a shared owner port API or local projection instead of cross-owner SQL")
                .isEmpty();
    }

    @Test
    void crossOwnerSqlScannerRejectsJdbcFacadeTextBlocksAndAnnotatedSql() throws IOException {
        Path root = repositoryRoot();
        List<OwnerTableRule> rules = ownerTableRules(root);
        String fixture = """
                jdbcTemplate.queryForList("select id from payment_order where order_no = ?");
                @Select("select id from team_member where team_id = ?")
                interface TeamLookup { }
                @Select("select id from sys_user where id = ?")
                interface UserLookup { }
                """ + "competitionSql.update(\"\"\"\nupdate aiadc_project set status = 'ARCHIVED'\n\"\"\");"
                + "\"\"\"\nselect id from aiadc_expert where deleted = 0\n\"\"\"";
        List<String> violations = new ArrayList<>();

        assertRuntimeSqlAccessesAllowed(
                root,
                rules,
                violations,
                "lumira-competition",
                root.resolve("services/lumira-competition/src/testFixtures/java/CompetitionCrossOwnerSqlFixture.java"),
                sqlTableAccessesInJavaSource(fixture)
        );

        assertThat(violations)
                .anySatisfy(violation -> assertThat(violation).contains("reads payment_order"))
                .anySatisfy(violation -> assertThat(violation).contains("reads team_member"))
                .anySatisfy(violation -> assertThat(violation).contains("reads sys_user"))
                .anySatisfy(violation -> assertThat(violation).contains("writes aiadc_project"))
                .anySatisfy(violation -> assertThat(violation).contains("reads aiadc_expert"));
    }

    @Test
    void baseMapperWriteCallsMustStayInsideOwnerOrCompatibleModules() throws IOException {
        Path root = repositoryRoot();
        List<OwnerTableRule> rules = ownerTableRules(root);
        Map<String, String> mapperTables = baseMapperTableNames(root);
        assertThat(mapperTables).isNotEmpty();

        List<String> violations = new ArrayList<>();
        List<Path> javaFiles = javaFiles(root)
                .filter(path -> normalized(path).contains("/src/main/java/"))
                .filter(path -> normalized(path).contains("/services/"))
                .toList();
        for (Path javaFile : javaFiles) {
            String source = Files.readString(javaFile);
            Map<String, String> mapperVariables = mapperVariables(source, mapperTables);
            if (mapperVariables.isEmpty()) {
                continue;
            }
            String module = serviceModuleName(root, javaFile);
            for (Map.Entry<String, String> variable : mapperVariables.entrySet()) {
                String table = mapperTables.get(variable.getValue());
                if (table == null) {
                    continue;
                }
                for (String method : BASE_MAPPER_WRITE_METHODS) {
                    if (containsMethodCall(source, variable.getKey(), method)) {
                        assertWriteAllowed(root, rules, violations, module, javaFile, table);
                    }
                }
            }
        }

        assertThat(violations).isEmpty();
    }

    @Test
    void baseMapperManagedTablesMustBelongToDeclaringModule() throws IOException {
        Path root = repositoryRoot();
        List<OwnerTableRule> rules = ownerTableRules(root);
        Map<String, String> mapperTables = baseMapperTableNames(root);
        assertThat(mapperTables).isNotEmpty();

        List<String> violations = new ArrayList<>();
        List<Path> mapperFiles = javaFiles(root)
                .filter(path -> normalized(path).contains("/services/"))
                .filter(path -> normalized(path).contains("/src/main/java/"))
                .filter(path -> normalized(path).contains("/mapper/"))
                .filter(path -> Files.exists(path))
                .toList();
        for (Path mapperFile : mapperFiles) {
            String mapperName = simpleClassName(mapperFile);
            String table = mapperTables.get(mapperName);
            if (table == null) {
                continue;
            }
            assertWriteAllowed(root, rules, violations, serviceModuleName(root, mapperFile), mapperFile, table);
        }

        assertThat(violations).isEmpty();
    }

    @Test
    void messageMapperQueriesMustUseOwnerContractsForIamAndPlatformReads() throws IOException {
        Path root = repositoryRoot();
        Path mapperRoot = root.resolve("services/lumira-message/src/main/resources/mapper");
        List<String> violations = new ArrayList<>();
        if (!Files.exists(mapperRoot)) {
            return;
        }
        List<Path> mapperXmlFiles = Files.walk(mapperRoot)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".xml"))
                .toList();
        for (Path mapperXmlFile : mapperXmlFiles) {
            Matcher matcher = MESSAGE_FORBIDDEN_OWNER_READ_PATTERN.matcher(Files.readString(mapperXmlFile));
            while (matcher.find()) {
                violations.add(root.relativize(mapperXmlFile) + " reads owner table " + matcher.group(1)
                        + "; use SystemInternalApi, event projection, or a Message-owned read model");
            }
        }

        assertThat(violations).isEmpty();
    }

    private static Stream<Path> javaFiles(Path root) throws IOException {
        if (Files.isDirectory(root.resolve("services")) && Files.isDirectory(root.resolve("libs"))) {
            return Stream.concat(Files.walk(root.resolve("services")), Files.walk(root.resolve("libs")))
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !normalized(path).contains("/target/"));
        }
        return Files.walk(root)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !normalized(path).contains("/target/"));
    }

    @Test
    void systemModuleMustNotContainTeamImplementationPackage() throws IOException {
        Path root = repositoryRoot();
        Path forbiddenPackage = root.resolve("services/lumira-system/src/main/java/com/lumira/saas/modules/team");

        assertThat(forbiddenPackage)
                .as("Team is a Business module and must live in services/lumira-team")
                .doesNotExist();
    }

    @Test
    void systemModuleMustNotContainAiImplementationSources() throws IOException {
        Path root = repositoryRoot();
        List<Path> forbiddenPackages = List.of(
                root.resolve("services/lumira-system/src/main/java/com/lumira/saas/modules/ai"),
                root.resolve("services/lumira-system/src/test/java/com/lumira/saas/modules/ai")
        );
        List<Path> remainingSources = new ArrayList<>();
        for (Path forbiddenPackage : forbiddenPackages) {
            if (!Files.exists(forbiddenPackage)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(forbiddenPackage)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .forEach(remainingSources::add);
            }
        }

        assertThat(root.resolve("services/lumira-ai/src/main/java/com/lumira/saas/modules/ai"))
                .as("AI implementation must live in the lumira-ai owner module")
                .exists();
        assertThat(remainingSources)
                .as("AI sources must not remain in lumira-system after owner convergence")
                .isEmpty();
    }

    @Test
    void systemModuleMustNotContainWorkflowImplementationPackage() {
        Path root = repositoryRoot();
        List<Path> forbiddenPackages = List.of(
                root.resolve("services/lumira-system/src/main/java/com/lumira/saas/modules/workflow"),
                root.resolve("services/lumira-system/src/test/java/com/lumira/saas/modules/workflow")
        );

        assertThat(forbiddenPackages)
                .as("Workflow is a Business module and must live in services/lumira-workflow")
                .allSatisfy(path -> assertThat(path).doesNotExist());
    }

    @Test
    void systemModuleMustNotContainExportImplementationSources() throws IOException {
        Path root = repositoryRoot();
        List<Path> forbiddenPackages = List.of(
                root.resolve("services/lumira-system/src/main/java/com/lumira/saas/modules/system/export"),
                root.resolve("services/lumira-system/src/test/java/com/lumira/saas/modules/system/export")
        );
        List<Path> remainingSources = new ArrayList<>();
        for (Path forbiddenPackage : forbiddenPackages) {
            if (!Files.exists(forbiddenPackage)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(forbiddenPackage)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .forEach(remainingSources::add);
            }
        }

        assertThat(remainingSources)
                .as("Export sources must live in services/lumira-export")
                .isEmpty();
    }

    @Test
    void systemModuleMustNotContainProjectImplementationSources() throws IOException {
        Path root = repositoryRoot();
        List<Path> forbiddenPackages = List.of(
                root.resolve("services/lumira-system/src/main/java/com/lumira/saas/modules/project"),
                root.resolve("services/lumira-system/src/test/java/com/lumira/saas/modules/project")
        );
        List<Path> remainingSources = new ArrayList<>();
        for (Path forbiddenPackage : forbiddenPackages) {
            if (!Files.exists(forbiddenPackage)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(forbiddenPackage)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .forEach(remainingSources::add);
            }
        }

        assertThat(remainingSources)
                .as("Project sources must live in services/lumira-project")
                .isEmpty();
    }

    @Test
    void systemModuleMustNotContainExpertImplementationSources() throws IOException {
        Path root = repositoryRoot();
        List<Path> forbiddenPackages = List.of(
                root.resolve("services/lumira-system/src/main/java/com/lumira/saas/modules/expert"),
                root.resolve("services/lumira-system/src/test/java/com/lumira/saas/modules/expert")
        );
        List<Path> remainingSources = new ArrayList<>();
        for (Path forbiddenPackage : forbiddenPackages) {
            if (!Files.exists(forbiddenPackage)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(forbiddenPackage)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .forEach(remainingSources::add);
            }
        }

        assertThat(remainingSources)
                .as("Expert sources must live in services/lumira-expert")
                .isEmpty();
    }

    @Test
    void systemModuleMustNotContainCompetitionOrReviewImplementationSources() throws IOException {
        Path root = repositoryRoot();
        List<Path> forbiddenPackages = List.of(
                root.resolve("services/lumira-system/src/main/java/com/lumira/saas/modules/competition"),
                root.resolve("services/lumira-system/src/test/java/com/lumira/saas/modules/competition"),
                root.resolve("services/lumira-system/src/main/java/com/lumira/saas/modules/review"),
                root.resolve("services/lumira-system/src/test/java/com/lumira/saas/modules/review")
        );
        List<Path> remainingSources = new ArrayList<>();
        for (Path forbiddenPackage : forbiddenPackages) {
            if (!Files.exists(forbiddenPackage)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(forbiddenPackage)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java"))
                        .forEach(remainingSources::add);
            }
        }

        assertThat(remainingSources)
                .as("Competition, Registration, Certificate, and Review sources must live in services/lumira-competition")
                .isEmpty();
    }

    @Test
    void workflowTablesAreOwnedByLumiraWorkflow() throws IOException {
        List<OwnerTableRule> rules = ownerTableRules(repositoryRoot());
        Optional<OwnerTableRule> workflowRule = rules.stream()
                .filter(rule -> rule.context().equals("WORKFLOW"))
                .findFirst();

        assertThat(workflowRule).isPresent();
        assertThat(workflowRule.get().ownerModule()).isEqualTo("lumira-workflow");
        assertThat(workflowRule.get().bootstrapSchemaPaths()).containsExactly("sql/saas.sql");
        assertThat(workflowRule.get().runtimeWriterModules()).isEmpty();
        assertThat(workflowRule.get().tablePatterns()).containsExactly("workflow_*");
    }

    @Test
    void exportTablesAreOwnedByLumiraExport() throws IOException {
        List<OwnerTableRule> rules = ownerTableRules(repositoryRoot());
        Optional<OwnerTableRule> exportRule = rules.stream()
                .filter(rule -> rule.context().equals("EXPORT"))
                .findFirst();

        assertThat(exportRule).isPresent();
        assertThat(exportRule.get().ownerModule()).isEqualTo("lumira-export");
        assertThat(exportRule.get().bootstrapSchemaPaths()).containsExactly("sql/saas.sql");
        assertThat(exportRule.get().runtimeWriterModules()).isEmpty();
        assertThat(exportRule.get().tablePatterns()).containsExactly("sys_export_task");
    }

    @Test
    void projectTablesAreOwnedByLumiraProject() throws IOException {
        List<OwnerTableRule> rules = ownerTableRules(repositoryRoot());
        Optional<OwnerTableRule> projectRule = rules.stream()
                .filter(rule -> rule.context().equals("PROJECT"))
                .findFirst();

        assertThat(projectRule).isPresent();
        assertThat(projectRule.get().ownerModule()).isEqualTo("lumira-project");
        assertThat(projectRule.get().bootstrapSchemaPaths()).containsExactly("sql/saas.sql");
        assertThat(projectRule.get().runtimeWriterModules()).isEmpty();
        assertThat(projectRule.get().tablePatterns()).containsExactly("aiadc_project");
    }

    @Test
    void expertTablesAreOwnedByLumiraExpert() throws IOException {
        List<OwnerTableRule> rules = ownerTableRules(repositoryRoot());
        Optional<OwnerTableRule> expertRule = rules.stream()
                .filter(rule -> rule.context().equals("EXPERT"))
                .findFirst();

        assertThat(expertRule).isPresent();
        assertThat(expertRule.get().ownerModule()).isEqualTo("lumira-expert");
        assertThat(expertRule.get().bootstrapSchemaPaths()).containsExactly("sql/saas.sql");
        assertThat(expertRule.get().runtimeWriterModules()).isEmpty();
        assertThat(expertRule.get().tablePatterns()).containsExactly("aiadc_expert");
    }

    @Test
    void aiTablesAreOwnedByLumiraAi() throws IOException {
        List<OwnerTableRule> rules = ownerTableRules(repositoryRoot());
        Optional<OwnerTableRule> aiRule = rules.stream()
                .filter(rule -> rule.context().equals("AI"))
                .findFirst();

        assertThat(aiRule).isPresent();
        assertThat(aiRule.get().ownerModule()).isEqualTo("lumira-ai");
        assertThat(aiRule.get().bootstrapSchemaPaths()).containsExactly("sql/saas.sql");
        assertThat(aiRule.get().runtimeWriterModules()).isEmpty();
        assertThat(aiRule.get().tablePatterns()).containsExactly("ai_*");
    }

    @Test
    void competitionTablesAreOwnedByLumiraCompetition() throws IOException {
        List<OwnerTableRule> rules = ownerTableRules(repositoryRoot());
        Optional<OwnerTableRule> competitionRule = rules.stream()
                .filter(rule -> rule.context().equals("COMPETITION"))
                .findFirst();

        assertThat(competitionRule).isPresent();
        assertThat(competitionRule.get().ownerModule()).isEqualTo("lumira-competition");
        assertThat(competitionRule.get().bootstrapSchemaPaths()).containsExactly("sql/saas.sql");
        assertThat(competitionRule.get().runtimeWriterModules()).isEmpty();
        assertThat(competitionRule.get().tablePatterns()).containsExactly(
                "aiadc_competition",
                "competition_*",
                "registration_material_submission",
                "registration_material_value",
                "registration_material_value_revision",
                "certificate_*");
    }

    @Test
    void teamTablesAreOwnedByLumiraTeam() throws IOException {
        List<OwnerTableRule> rules = ownerTableRules(repositoryRoot());
        Optional<OwnerTableRule> teamRule = rules.stream()
                .filter(rule -> rule.context().equals("TEAM"))
                .findFirst();

        assertThat(teamRule).isPresent();
        assertThat(teamRule.get().ownerModule()).isEqualTo("lumira-team");
        assertThat(teamRule.get().bootstrapSchemaPaths()).containsExactly("sql/saas.sql");
        assertThat(teamRule.get().runtimeWriterModules()).isEmpty();
        assertThat(teamRule.get().tablePatterns())
                .containsExactly("team", "team_member", "team_invite", "team_join_request");
    }

    @Test
    void bootstrapSchemaPathsMustNotCreateRuntimeSqlWriterPermissions() throws IOException {
        Set<String> migratedContexts = Set.of(
                "ACTIVITY",
                "COMPETITION",
                "PROJECT",
                "EXPERT",
                "WORKFLOW",
                "EXPORT"
        );

        List<OwnerTableRule> rules = ownerTableRules(repositoryRoot()).stream()
                .filter(rule -> migratedContexts.contains(rule.context()))
                .toList();

        assertThat(rules).hasSize(migratedContexts.size());
        assertThat(rules)
                .allSatisfy(rule -> assertThat(rule.bootstrapSchemaPaths())
                        .as("%s must be created through the disabled-Flyway bootstrap schema only", rule.context())
                        .containsExactly("sql/saas.sql"));
        assertThat(rules)
                .allSatisfy(rule -> assertThat(rule.runtimeWriterModules())
                        .as("%s bootstrap history must not grant lumira-system runtime SQL access", rule.context())
                        .isEmpty());
    }

    @Test
    void manifestBootstrapSchemaPathsMustResolveWithoutBecomingRuntimeModules() throws IOException {
        Path root = repositoryRoot();
        List<String> bootstrapSchemaPaths = ownerTableRules(root).stream()
                .flatMap(rule -> rule.bootstrapSchemaPaths().stream())
                .distinct()
                .toList();

        assertThat(bootstrapSchemaPaths).containsExactly("sql/saas.sql");
        assertThat(bootstrapSchemaPaths)
                .allSatisfy(path -> assertThat(root.resolve(path))
                        .as("manifest bootstrap schema path %s", path)
                        .exists());
        assertThat(bootstrapSchemaPaths)
                .allSatisfy(path -> assertThat(path).doesNotStartWith("lumira-"));
    }

    @Test
    void runtimeSharedTableExceptionsMustStayTableScoped() {
        assertThat(EXPLICIT_RUNTIME_TABLE_ACCESS_MODULES)
                .containsEntry("platform_event_outbox", Set.of("lumira-system", "lumira-file"))
                .containsEntry("event_consumer_receipt", Set.of("lumira-message"));
        assertThat(EXPLICIT_RUNTIME_TABLE_ACCESS_MODULES.values())
                .allSatisfy(modules -> assertThat(modules).doesNotContain("lumira-admin", "lumira-async", "lumira-quartz"));
    }

    private static Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null && !Files.exists(current.resolve("pom.xml"))) {
            current = current.getParent();
        }
        if (current != null && Files.exists(current.resolve("services")) && Files.exists(current.resolve("libs"))) {
            return current;
        }
        Path parent = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (parent != null) {
            if (Files.exists(parent.resolve("services")) && Files.exists(parent.resolve("libs"))) {
                return parent;
            }
            parent = parent.getParent();
        }
        throw new IllegalStateException("Cannot locate repository root");
    }

    private static String normalized(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String projectArtifactId(String pom) {
        Matcher matcher = PROJECT_ARTIFACT_ID_PATTERN.matcher(PARENT_POM_PATTERN.matcher(pom).replaceFirst(""));
        assertThat(matcher.find()).as("Maven POM must declare a project artifactId").isTrue();
        return matcher.group(1).trim();
    }

    private static List<String> serviceImplementationDependencies(String pom) {
        Set<String> serviceArtifactIds = Set.copyOf(BOUNDED_CONTEXT_SERVICE_ARTIFACT_IDS.values());
        List<String> dependencies = new ArrayList<>();
        Matcher matcher = MAVEN_DEPENDENCY_PATTERN.matcher(pom);
        while (matcher.find()) {
            String artifactId = matcher.group(1).trim();
            if (serviceArtifactIds.contains(artifactId)) {
                dependencies.add(artifactId);
            }
        }
        return dependencies;
    }

    private static List<OwnerTableRule> ownerTableRules(Path root) throws IOException {
        Path manifest = root.resolve(OWNER_TABLE_MANIFEST);
        assertThat(manifest).exists();
        List<String> lines = Files.readAllLines(manifest).stream()
                .filter(line -> !line.isBlank())
                .toList();
        assertThat(lines).isNotEmpty();
        assertThat(lines.get(0)).isEqualTo(
                "context,owner_module,owned_table_patterns,bootstrap_schema_paths,runtime_writer_modules,notes");

        List<OwnerTableRule> rules = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] columns = lines.get(i).split(",", 6);
            assertThat(columns)
                    .as("%s line %s must keep the manifest column shape", OWNER_TABLE_MANIFEST, i + 1)
                    .hasSize(6);
            rules.add(new OwnerTableRule(
                    columns[0],
                    columns[1],
                    splitPipeList(columns[2]),
                    splitPipeList(columns[3]),
                    splitPipeList(columns[4])
            ));
        }
        return rules;
    }

    private static List<String> splitPipeList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\\|"))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private static String serviceModuleName(Path root, Path file) {
        Path relative = root.relativize(file);
        assertThat(relative.getName(0).toString()).isEqualTo("services");
        return relative.getName(1).toString();
    }

    private static String archivedMigrationModuleName(Path archiveRoot, Path file) {
        Path relative = archiveRoot.relativize(file);
        return relative.getName(0).toString();
    }

    private static void assertMigrationDdlAllowed(
            Path root,
            List<OwnerTableRule> rules,
            List<String> violations,
            String module,
            Path migrationFile
    ) throws IOException {
        Matcher matcher = TABLE_DDL_PATTERN.matcher(Files.readString(migrationFile));
        while (matcher.find()) {
            String table = matcher.group(1);
            List<OwnerTableRule> matchingRules = rules.stream()
                    .filter(rule -> rule.matches(table))
                    .toList();
            if (matchingRules.isEmpty()) {
                violations.add(root.relativize(migrationFile) + " declares " + table + " but no owner rule matches");
                continue;
            }
            if (matchingRules.stream().noneMatch(rule -> rule.ownerModule().equals(module))) {
                violations.add(root.relativize(migrationFile) + " declares " + table
                        + " from " + module + " but matching owner rules are " + matchingRules);
            }
        }
    }

    private static void assertExplicitSqlWriteAllowed(
            Path root,
            List<OwnerTableRule> rules,
            List<String> violations,
            String module,
            Path sourceFile,
            String sqlSource
    ) {
        Matcher matcher = SQL_WRITE_TABLE_PATTERN.matcher(sqlSource);
        List<SqlTableAccess> accesses = new ArrayList<>();
        while (matcher.find()) {
            accesses.add(new SqlTableAccess(SqlAccessKind.WRITE, matcher.group(1)));
        }
        assertRuntimeSqlAccessesAllowed(root, rules, violations, module, sourceFile, accesses);
    }

    private static void assertWriteAllowed(
            Path root,
            List<OwnerTableRule> rules,
            List<String> violations,
            String module,
            Path sourceFile,
            String table
    ) {
        assertRuntimeSqlAccessAllowed(
                root,
                rules,
                violations,
                module,
                sourceFile,
                new SqlTableAccess(SqlAccessKind.WRITE, table)
        );
    }

    private static void assertRuntimeSqlAccessesAllowed(
            Path root,
            List<OwnerTableRule> rules,
            List<String> violations,
            String module,
            Path sourceFile,
            List<SqlTableAccess> accesses
    ) {
        for (SqlTableAccess access : accesses) {
            assertRuntimeSqlAccessAllowed(root, rules, violations, module, sourceFile, access);
        }
    }

    private static void assertRuntimeSqlAccessAllowed(
            Path root,
            List<OwnerTableRule> rules,
            List<String> violations,
            String module,
            Path sourceFile,
            SqlTableAccess access
    ) {
        String table = access.table();
        List<OwnerTableRule> matchingRules = rules.stream()
                .filter(rule -> rule.matches(table))
                .toList();
        if (matchingRules.isEmpty()) {
            if (access.kind() == SqlAccessKind.WRITE) {
                violations.add(root.relativize(sourceFile) + " writes " + table + " but no owner rule matches");
            }
            return;
        }
        boolean explicitlyShared = EXPLICIT_RUNTIME_TABLE_ACCESS_MODULES
                .getOrDefault(table, Set.of())
                .contains(module);
        boolean ownerAccess = matchingRules.stream().anyMatch(rule -> rule.ownerModule().equals(module));
        boolean runtimeWriteAccess = access.kind() == SqlAccessKind.WRITE
                && matchingRules.stream().anyMatch(rule -> rule.canRuntimeWrite(module));
        if (!explicitlyShared && !ownerAccess && !runtimeWriteAccess) {
            violations.add(root.relativize(sourceFile) + " " + access.verb() + " " + table
                    + " from " + module + " but matching owner rules are " + matchingRules);
        }
    }

    private static List<SqlTableAccess> sqlTableAccessesInJavaSource(String source) {
        List<String> sqlFragments = javaStringFragments(source);
        List<SqlTableAccess> accesses = new ArrayList<>();
        for (String fragment : sqlFragments) {
            accesses.addAll(sqlTableAccessesInSqlText(fragment));
        }
        return accesses;
    }

    private static List<String> javaStringFragments(String source) {
        List<String> fragments = new ArrayList<>();
        int index = 0;
        while (index < source.length()) {
            if (source.startsWith("\"\"\"", index)) {
                int contentStart = index + 3;
                int end = source.indexOf("\"\"\"", contentStart);
                if (end < 0) {
                    break;
                }
                fragments.add(source.substring(contentStart, end));
                index = end + 3;
                continue;
            }
            if (source.charAt(index) != '\"') {
                index++;
                continue;
            }
            StringBuilder fragment = new StringBuilder();
            index++;
            while (index < source.length()) {
                char character = source.charAt(index++);
                if (character == '\\' && index < source.length()) {
                    fragment.append(source.charAt(index++));
                } else if (character == '\"') {
                    break;
                } else {
                    fragment.append(character);
                }
            }
            fragments.add(fragment.toString());
        }
        return fragments;
    }

    private static List<SqlTableAccess> sqlTableAccessesInSqlText(String sqlSource) {
        List<SqlTableAccess> accesses = new ArrayList<>();
        Matcher readMatcher = SQL_READ_TABLE_PATTERN.matcher(sqlSource);
        while (readMatcher.find()) {
            accesses.add(new SqlTableAccess(SqlAccessKind.READ, readMatcher.group(1)));
        }
        Matcher writeMatcher = SQL_WRITE_TABLE_PATTERN.matcher(sqlSource);
        while (writeMatcher.find()) {
            accesses.add(new SqlTableAccess(SqlAccessKind.WRITE, writeMatcher.group(1)));
        }
        return accesses;
    }

    private static Map<String, String> baseMapperTableNames(Path root) throws IOException {
        Map<String, String> entityTables = entityTableNames(root);
        Map<String, String> mapperTables = new LinkedHashMap<>();
        List<Path> mapperFiles = javaFiles(root)
                .filter(path -> normalized(path).contains("/src/main/java/"))
                .filter(path -> normalized(path).contains("/mapper/"))
                .toList();
        for (Path mapperFile : mapperFiles) {
            String source = Files.readString(mapperFile);
            Matcher baseMapperMatcher = BASE_MAPPER_ENTITY_PATTERN.matcher(source);
            if (!baseMapperMatcher.find()) {
                continue;
            }
            String entityName = baseMapperMatcher.group(1);
            String table = entityTables.get(entityName);
            assertThat(table)
                    .as("%s BaseMapper entity %s must have a @TableName entry", root.relativize(mapperFile), entityName)
                    .isNotBlank();
            mapperTables.put(simpleClassName(mapperFile), table);
        }
        return mapperTables;
    }

    private static Map<String, String> entityTableNames(Path root) throws IOException {
        Map<String, String> entityTables = new LinkedHashMap<>();
        List<Path> entityFiles = javaFiles(root)
                .filter(path -> normalized(path).contains("/src/main/java/"))
                .filter(path -> {
                    try {
                        return Files.readString(path).contains("@TableName");
                    } catch (IOException exception) {
                        throw new IllegalStateException("Failed to read " + path, exception);
                    }
                })
                .toList();
        for (Path entityFile : entityFiles) {
            String source = Files.readString(entityFile);
            Matcher matcher = TABLE_NAME_ENTITY_PATTERN.matcher(source);
            while (matcher.find()) {
                entityTables.put(matcher.group(2), matcher.group(1));
            }
        }
        return entityTables;
    }

    private static Map<String, String> mapperVariables(String source, Map<String, String> mapperTables) {
        Map<String, String> imports = new LinkedHashMap<>();
        Matcher importMatcher = IMPORT_PATTERN.matcher(source);
        while (importMatcher.find()) {
            String fqcn = importMatcher.group(1);
            imports.put(fqcn.substring(fqcn.lastIndexOf('.') + 1), fqcn);
        }
        Map<String, String> variables = new LinkedHashMap<>();
        Matcher variableMatcher = MAPPER_VARIABLE_PATTERN.matcher(source);
        while (variableMatcher.find()) {
            String mapperType = variableMatcher.group(1);
            if (mapperTables.containsKey(mapperType) || imports.containsKey(mapperType)) {
                variables.put(variableMatcher.group(2), mapperType);
            }
        }
        return variables;
    }

    private static boolean containsMethodCall(String source, String variable, String method) {
        try {
            return Pattern.compile("\\b" + Pattern.quote(variable) + "\\s*\\.\\s*" + Pattern.quote(method) + "\\s*\\(")
                    .matcher(source)
                    .find();
        } catch (PatternSyntaxException exception) {
            return false;
        }
    }

    private static String simpleClassName(Path javaFile) {
        String fileName = javaFile.getFileName().toString();
        return fileName.substring(0, fileName.length() - ".java".length());
    }

    private record OwnerTableRule(
            String context,
            String ownerModule,
            List<String> tablePatterns,
            List<String> bootstrapSchemaPaths,
            List<String> runtimeWriterModules
    ) {
        boolean matches(String table) {
            return tablePatterns.stream().anyMatch(pattern -> patternMatches(pattern, table));
        }

        boolean canRuntimeWrite(String module) {
            return ownerModule.equals(module) || runtimeWriterModules.contains(module);
        }

        private static boolean patternMatches(String pattern, String table) {
            if ("-".equals(pattern)) {
                return false;
            }
            if (pattern.endsWith("*")) {
                return table.startsWith(pattern.substring(0, pattern.length() - 1));
            }
            return table.equals(pattern);
        }
    }

    private enum SqlAccessKind {
        READ,
        WRITE
    }

    private record SqlTableAccess(SqlAccessKind kind, String table) {
        String verb() {
            return kind == SqlAccessKind.WRITE ? "writes" : "reads";
        }
    }
}

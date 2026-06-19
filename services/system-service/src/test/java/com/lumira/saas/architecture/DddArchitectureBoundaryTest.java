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

    private static final Path OWNER_TABLE_MANIFEST = Path.of("docs/27-ddd-owner-table-manifest.csv");
    private static final Set<String> PER_PRODUCER_TABLE_NAMES = Set.of("platform_event_outbox");
    private static final Pattern TABLE_DDL_PATTERN = Pattern.compile(
            "(?i)\\b(?:create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?|alter\\s+table\\s+)`?([a-zA-Z0-9_]+)`?");
    private static final Pattern SQL_WRITE_TABLE_PATTERN = Pattern.compile(
            "(?is)\\b(?:insert\\s+into|update|delete\\s+from)\\s+`?([a-zA-Z0-9_]+)`?");
    private static final Pattern MYBATIS_WRITE_STATEMENT_PATTERN = Pattern.compile(
            "(?is)<(?:insert|update|delete)\\b[^>]*>(.*?)</(?:insert|update|delete)>");
    private static final Pattern MESSAGE_FORBIDDEN_OWNER_READ_PATTERN = Pattern.compile(
            "(?is)\\b(?:from|join)\\s+`?(sys_user|sys_user_tenant|sys_user_role|sys_role|sys_config|audit_operation_log)`?");
    private static final Pattern JDBC_TEMPLATE_UPDATE_PATTERN = Pattern.compile(
            "(?s)jdbcTemplate\\.update\\s*\\(\\s*\"([^\"]+)\"");
    private static final Pattern JDBC_TEMPLATE_TEXT_BLOCK_UPDATE_PATTERN = Pattern.compile(
            "(?s)jdbcTemplate\\.update\\s*\\(\\s*\"\"\"(.*?)\"\"\"");
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
            "JOB"
    );
    private static final Map<String, List<String>> SPLIT_TARGET_PERSISTENCE_IMPORT_PREFIXES = Map.of(
            "auth-service", List.of("com.lumira.auth.mapper.", "com.lumira.auth.entity."),
            "message-service", List.of("com.lumira.message.mapper.", "com.lumira.message.entity."),
            "file-service", List.of("com.lumira.file.mapper.", "com.lumira.file.entity."),
            "plugin-service", List.of("com.lumira.saas.modules.plugin.mapper.", "com.lumira.saas.modules.plugin.entity."),
            "localization-service", List.of("com.lumira.saas.modules.localization.mapper.", "com.lumira.saas.modules.localization.entity."),
            "payment-service", List.of("com.lumira.payment.mapper.", "com.lumira.payment.entity."),
            "ai-service", List.of("com.lumira.ai.mapper.", "com.lumira.ai.entity.")
    );
    private static final Set<String> SERVICE_ARTIFACT_IDS = Set.of(
            "system-service",
            "auth-service",
            "message-service",
            "file-service",
            "plugin-service",
            "localization-service",
            "payment-service",
            "ai-service",
            "job-executor"
    );
    private static final Pattern MAVEN_DEPENDENCY_PATTERN = Pattern.compile(
            "(?s)<dependency>.*?<groupId>com\\.lumira</groupId>.*?<artifactId>([^<]+)</artifactId>.*?</dependency>");

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
                "services/system-service/src/main/java/com/lumira/saas/modules/iam/domain/model/IamDomainModels.java",
                "services/auth-service/src/main/java/com/lumira/auth/domain/model/AuthDomainModels.java",
                "services/system-service/src/main/java/com/lumira/saas/modules/platform/domain/model/PlatformDomainModels.java",
                "services/message-service/src/main/java/com/lumira/message/domain/model/MessageDomainModels.java",
                "services/file-service/src/main/java/com/lumira/file/domain/model/FileDomainModels.java",
                "services/plugin-service/src/main/java/com/lumira/saas/modules/plugin/domain/model/PluginDomainModels.java",
                "services/localization-service/src/main/java/com/lumira/saas/modules/localization/domain/model/LocalizationDomainModels.java",
                "services/payment-service/src/main/java/com/lumira/payment/domain/model/PaymentDomainModels.java",
                "services/system-service/src/main/java/com/lumira/saas/modules/ai/domain/model/AiAssistantDomainModels.java",
                "services/job-executor/src/main/java/com/lumira/job/domain/model/JobDomainModels.java"
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
    void splitTargetModulesMustNotDependOnOtherServiceModules() throws IOException {
        Path root = repositoryRoot();
        List<String> violations = new ArrayList<>();
        for (String module : SERVICE_ARTIFACT_IDS) {
            Path pom = root.resolve("services").resolve(module).resolve("pom.xml");
            if (!Files.exists(pom)) {
                continue;
            }
            Matcher matcher = MAVEN_DEPENDENCY_PATTERN.matcher(Files.readString(pom));
            while (matcher.find()) {
                String artifactId = matcher.group(1);
                if (SERVICE_ARTIFACT_IDS.contains(artifactId)) {
                    violations.add(root.relativize(pom) + " depends on service module " + artifactId
                            + "; use lumira-api/plugin-api contracts, internal HTTP, or event projection");
                }
            }
        }

        assertThat(violations).isEmpty();
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
                        .isNotIn("lumira-server", "job-executor"));

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
        List<Path> migrationFiles = Files.walk(root.resolve("services"))
                .filter(Files::isRegularFile)
                .filter(path -> normalized(path).contains("/src/main/resources/db/migration/"))
                .filter(path -> path.toString().endsWith(".sql"))
                .toList();

        assertThat(migrationFiles).isNotEmpty();
        List<String> violations = new ArrayList<>();
        for (Path migrationFile : migrationFiles) {
            String module = serviceModuleName(root, migrationFile);
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
                Optional<OwnerTableRule> writableRule = matchingRules.stream()
                        .filter(rule -> rule.canWrite(module))
                        .findFirst();
                if (writableRule.isEmpty()) {
                    violations.add(root.relativize(migrationFile) + " declares " + table
                            + " from " + module + " but matching owner rules are " + matchingRules);
                }
            }
        }

        assertThat(violations).isEmpty();
    }

    @Test
    void explicitSqlWritePathsMustStayInsideOwnerOrCompatibleModules() throws IOException {
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
                .filter(path -> !normalized(path).contains("/target/"))
                .toList();
        for (Path javaFile : javaFiles) {
            String source = Files.readString(javaFile);
            Matcher matcher = JDBC_TEMPLATE_UPDATE_PATTERN.matcher(source);
            while (matcher.find()) {
                assertExplicitSqlWriteAllowed(root, rules, violations, serviceModuleName(root, javaFile), javaFile, matcher.group(1));
            }
            Matcher textBlockMatcher = JDBC_TEMPLATE_TEXT_BLOCK_UPDATE_PATTERN.matcher(source);
            while (textBlockMatcher.find()) {
                assertExplicitSqlWriteAllowed(root, rules, violations, serviceModuleName(root, javaFile), javaFile, textBlockMatcher.group(1));
            }
        }

        assertThat(violations).isEmpty();
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
        Path mapperRoot = root.resolve("services/message-service/src/main/resources/mapper");
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

    private static List<OwnerTableRule> ownerTableRules(Path root) throws IOException {
        Path manifest = root.resolve(OWNER_TABLE_MANIFEST);
        assertThat(manifest).exists();
        List<String> lines = Files.readAllLines(manifest).stream()
                .filter(line -> !line.isBlank())
                .toList();
        assertThat(lines).isNotEmpty();
        assertThat(lines.get(0)).isEqualTo("context,owner_module,owned_table_patterns,compatible_writer_modules,notes");

        List<OwnerTableRule> rules = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] columns = lines.get(i).split(",", 5);
            assertThat(columns)
                    .as("%s line %s must keep the manifest column shape", OWNER_TABLE_MANIFEST, i + 1)
                    .hasSize(5);
            rules.add(new OwnerTableRule(
                    columns[0],
                    columns[1],
                    splitPipeList(columns[2]),
                    splitPipeList(columns[3])
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

    private static void assertExplicitSqlWriteAllowed(
            Path root,
            List<OwnerTableRule> rules,
            List<String> violations,
            String module,
            Path sourceFile,
            String sqlSource
    ) {
        Matcher matcher = SQL_WRITE_TABLE_PATTERN.matcher(sqlSource);
        if (!matcher.find()) {
            return;
        }
        String table = matcher.group(1);
        assertWriteAllowed(root, rules, violations, module, sourceFile, table);
    }

    private static void assertWriteAllowed(
            Path root,
            List<OwnerTableRule> rules,
            List<String> violations,
            String module,
            Path sourceFile,
            String table
    ) {
        List<OwnerTableRule> matchingRules = rules.stream()
                .filter(rule -> rule.matches(table))
                .toList();
        if (matchingRules.isEmpty()) {
            violations.add(root.relativize(sourceFile) + " writes " + table + " but no owner rule matches");
            return;
        }
        Optional<OwnerTableRule> writableRule = matchingRules.stream()
                .filter(rule -> rule.canWrite(module))
                .findFirst();
        if (writableRule.isEmpty()) {
            violations.add(root.relativize(sourceFile) + " writes " + table
                    + " from " + module + " but matching owner rules are " + matchingRules);
        }
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
            List<String> compatibleWriterModules
    ) {
        boolean matches(String table) {
            return tablePatterns.stream().anyMatch(pattern -> patternMatches(pattern, table));
        }

        boolean canWrite(String module) {
            return ownerModule.equals(module) || compatibleWriterModules.contains(module);
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
}

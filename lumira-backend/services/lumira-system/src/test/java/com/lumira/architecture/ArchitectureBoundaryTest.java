package com.lumira.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ArchitectureBoundaryTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.lumira");
    private static final String[] SERVICE_IMPLEMENTATION_PACKAGES = {
            "com.lumira.saas.modules..",
            "com.lumira.auth..",
            "com.lumira.file..",
            "com.lumira.message..",
            "com.lumira.payment..",
            "com.lumira.ai..",
            "com.lumira.job..",
            "com.lumira.team.app..",
            "com.lumira.team.controller..",
            "com.lumira.team.domain..",
            "com.lumira.team.entity..",
            "com.lumira.team.infrastructure..",
            "com.lumira.team.mapper.."
    };
    private static final Pattern LUMIRA_DEPENDENCY_PATTERN = Pattern.compile(
            "(?s)<dependency>.*?<groupId>com\\.lumira</groupId>.*?<artifactId>([^<]+)</artifactId>.*?</dependency>");
    private static final List<String> SERVICE_ARTIFACT_IDS = List.of(
            "system-service",
            "auth-service",
            "file-service",
            "message-service",
            "plugin-service",
            "localization-service",
            "payment-service",
            "ai-service",
            "team-service",
            "event-catalog-service",
            "job-executor",
            "lumira-admin");

    @Test
    void foundationCommonPackagesMustNotDependOnServiceImplementations() {
        noClasses()
                .that()
                .resideInAnyPackage("com.lumira.common..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(SERVICE_IMPLEMENTATION_PACKAGES)
                .because("Foundation common libraries must not depend on Platform or Business implementations")
                .check(CLASSES);
    }

    @Test
    void commonApiContractsMustNotDependOnServiceImplementations() {
        noClasses()
                .that()
                .resideInAnyPackage("com.lumira.api..", "com.lumira.team.api..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(SERVICE_IMPLEMENTATION_PACKAGES)
                .because("common-api is an API contract module, not a service implementation adapter")
                .check(CLASSES);
    }

    @Test
    void controllersMustNotDependOnMappers() {
        noClasses()
                .that()
                .resideInAnyPackage("..controller..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..mapper..")
                .because("controllers delegate to app services/facades and must not call persistence mappers")
                .check(CLASSES);
    }

    @Test
    void controllersMustNotDependOnEntitiesExceptDocumentedLegacySystemInternalController() {
        noClasses()
                .that()
                .resideInAnyPackage("..controller..")
                .and()
                .doNotHaveSimpleName("InternalSystemController")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..entity..")
                .because("controllers use DTO/VO contracts; InternalSystemController -> SysUserEntity is historical debt")
                .check(CLASSES);
    }

    @Test
    void controllersMustNotDeclareTransactionalBoundariesExceptDocumentedLegacySystemInternalController() throws IOException {
        Path controllerRoot = repositoryRoot().resolve("services");
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(controllerRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith("Controller.java"))
                    .filter(path -> !path.getFileName().toString().equals("InternalSystemController.java"))
                    .forEach(path -> {
                        try {
                            String source = Files.readString(path);
                            if (source.contains("@Transactional")) {
                                violations.add(repositoryRoot().relativize(path).toString());
                            }
                        } catch (IOException exception) {
                            throw new IllegalStateException("Failed to read " + path, exception);
                        }
                    });
        }

        assertThat(violations)
                .as("transactions belong in app services; InternalSystemController has legacy transactional endpoints")
                .isEmpty();
    }

    @Test
    void apiContractModulesMustNotDependOnServiceArtifacts() throws IOException {
        Path root = repositoryRoot();
        List<Path> contractPoms = List.of(
                root.resolve("libs/lumira-common-api/pom.xml"),
                root.resolve("libs/lumira-plugin-api/pom.xml"),
                root.resolve("libs/lumira-team-api/pom.xml"));
        List<String> violations = new ArrayList<>();
        for (Path pom : contractPoms) {
            assertThat(pom).exists();
            Matcher matcher = LUMIRA_DEPENDENCY_PATTERN.matcher(Files.readString(pom));
            while (matcher.find()) {
                String artifactId = matcher.group(1);
                if (SERVICE_ARTIFACT_IDS.contains(artifactId)) {
                    violations.add(root.relativize(pom) + " depends on service artifact " + artifactId);
                }
            }
        }

        assertThat(violations)
                .as("common-api/plugin-api must stay independent of service implementation artifacts")
                .isEmpty();
    }

    private static Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null && !(Files.exists(current.resolve("services")) && Files.exists(current.resolve("libs")))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Cannot locate lumira-backend root");
        }
        return current;
    }
}

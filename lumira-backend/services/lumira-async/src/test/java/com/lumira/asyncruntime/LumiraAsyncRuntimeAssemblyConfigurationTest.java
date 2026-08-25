package com.lumira.asyncruntime;

import com.lumira.api.competition.CompetitionPaymentEventHandler;
import com.lumira.api.event.OwnerOutboxRelayPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LumiraAsyncRuntimeAssemblyConfigurationTest {

    private final ApplicationContextRunner ownerRelayContext = new ApplicationContextRunner()
            .withUserConfiguration(LumiraAsyncOwnerRelayAssemblyConfiguration.class)
            .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
            .withPropertyValues(
                    "lumira.event.payment-consumer.enabled=false",
                    "lumira.async.owner-relay.control-plane-base-url=http://control-plane.test:8080",
                    "saas.internal.job-token=test-job-token",
                    "saas.internal.file-token=test-file-token",
                    "saas.internal.message-token=test-message-token",
                    "saas.internal.payment-token=test-payment-token",
                    "saas.internal.plugin-token=test-plugin-token"
            );

    @Test
    void runtimeImportsOnlyCommonAndNarrowOwnerRelayAssemblies() {
        Set<Class<?>> imports = importsOf(LumiraAsyncRuntimeAssemblyConfiguration.class);

        assertThat(imports).containsExactlyInAnyOrder(
                LumiraAsyncCommonRuntimeAssemblyConfiguration.class,
                LumiraAsyncOwnerRelayAssemblyConfiguration.class
        );
        assertThat(imports.stream().map(Class::getName))
                .noneMatch(name -> name.startsWith("com.lumira.file.")
                        || name.startsWith("com.lumira.message.")
                        || name.startsWith("com.lumira.payment.")
                        || name.startsWith("com.lumira.plugin.")
                        || name.startsWith("com.lumira.saas.modules.system.")
                        || name.startsWith("com.lumira.saas.modules.competition.")
                        || name.startsWith("com.lumira.saas.modules.eventcatalog."));
    }

    @Test
    void applicationKeepsComponentScanningInsideTheAsyncRuntimePackage() {
        SpringBootApplication application = LumiraAsyncApplication.class.getAnnotation(SpringBootApplication.class);

        assertThat(application).isNotNull();
        assertThat(application.scanBasePackages()).isEmpty();
        assertThat(application.scanBasePackageClasses()).isEmpty();
    }

    @Test
    void ownerRelayContextContainsOnlyPortsAndRemoteCompetitionHandler() {
        ownerRelayContext.run(context -> {
            assertThat(context.getBeansOfType(OwnerOutboxRelayPort.class))
                    .containsOnlyKeys(
                            "systemOwnerOutboxRelay",
                            "fileOwnerOutboxRelay",
                            "messageOwnerOutboxRelay",
                            "paymentOwnerOutboxRelay",
                            "pluginOwnerOutboxRelay"
                    );
            assertThat(context).hasSingleBean(RemoteCompetitionPaymentEventHandler.class);
            assertThat(context).hasSingleBean(CompetitionPaymentEventHandler.class);
            assertThat(context).hasSingleBean(AsyncOutboxRelayController.class);
            assertThat(context).hasSingleBean(AlertingWorkerLoop.class);
            assertThat(context).doesNotHaveBean("paymentDomainEventPublisher");
            assertThat(context).doesNotHaveBean("dataSource");
            assertThat(context.getBeanDefinitionNames())
                    .noneMatch(name -> name.contains("paymentRuntime")
                            || name.contains("fileRuntime")
                            || name.contains("messageControlPlane")
                            || name.contains("pluginControlPlane")
                            || name.contains("competitionControlPlane")
                            || name.toLowerCase().contains("datasource")
                            || name.toLowerCase().contains("mybatis"));
        });
    }

    @Test
    void disabledAsyncRuntimeDoesNotExposeOwnerRelayOrCompetitionConsumerBeans() {
        ownerRelayContext
                .withPropertyValues("lumira.runtime.async-enabled=false")
                .run(context -> {
                    assertThat(context.getBeansOfType(OwnerOutboxRelayPort.class)).isEmpty();
                    assertThat(context).doesNotHaveBean(OutboxRelayCoordinator.class);
                    assertThat(context).doesNotHaveBean(AsyncOutboxRelayController.class);
                    assertThat(context).doesNotHaveBean(RemoteCompetitionPaymentEventHandler.class);
                    assertThat(context).doesNotHaveBean(AlertingWorkerLoop.class);
                });
    }

    @Test
    void asyncPomDoesNotPullOwnerServiceArtifacts() throws Exception {
        String pom = Files.readString(asyncPom());

        assertThat(pom).contains("<artifactId>lumira-api</artifactId>");
        assertThat(pom).contains("<artifactId>common-web</artifactId>");
        assertThat(pom).doesNotContain(
                "<artifactId>system-service</artifactId>",
                "<artifactId>competition-service</artifactId>",
                "<artifactId>event-catalog-service</artifactId>",
                "<artifactId>file-service</artifactId>",
                "<artifactId>message-service</artifactId>",
                "<artifactId>plugin-service</artifactId>",
                "<artifactId>payment-service</artifactId>",
                "<artifactId>spring-boot-starter-jdbc</artifactId>",
                "<artifactId>spring-boot-starter-data-jpa</artifactId>",
                "<artifactId>mybatis-plus-spring-boot3-starter</artifactId>",
                "<artifactId>spring-boot-starter-cache</artifactId>",
                "<artifactId>caffeine</artifactId>"
        );
    }

    private Set<Class<?>> importsOf(Class<?> configurationClass) {
        Import annotation = configurationClass.getAnnotation(Import.class);
        assertThat(annotation)
                .as("%s must explicitly declare its runtime assembly", configurationClass.getSimpleName())
                .isNotNull();
        return Set.copyOf(Arrays.asList(annotation.value()));
    }

    private Path asyncPom() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path nested = current.resolve("services/lumira-async/pom.xml");
            if (Files.isRegularFile(nested)) {
                return nested;
            }
            Path direct = current.resolve("pom.xml");
            if (Files.isRegularFile(direct)) {
                try {
                    if (Files.readString(direct).contains("<artifactId>lumira-async</artifactId>")) {
                        return direct;
                    }
                } catch (java.io.IOException ignored) {
                    // Continue upward and fail with a clear assertion below.
                }
            }
            current = current.getParent();
        }
        throw new AssertionError("lumira-async pom.xml was not found");
    }
}

package com.lumira.saas.modules.project.assembly;

import com.lumira.saas.modules.project.integration.ProjectSnapshotPortAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectControlPlaneAssemblyConfigurationTest {

    @Test
    void explicitlyAssemblesProjectSnapshotOwnerAdapter() {
        Import imported = ProjectControlPlaneAssemblyConfiguration.class.getAnnotation(Import.class);

        assertThat(imported).isNotNull();
        assertThat(java.util.Arrays.asList(imported.value())).contains(ProjectSnapshotPortAdapter.class);
    }

    @Test
    void projectSourcesStayIndependentFromSystemImplementationPackages() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        try (var files = Files.walk(sourceRoot)) {
            var sources = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();

            assertThat(sources).isNotEmpty();
            for (Path source : sources) {
                String content = Files.readString(source);
                assertThat(content)
                        .as("%s must not depend on system-service implementations", source)
                        .doesNotContain("import com.lumira.saas.common.")
                        .doesNotContain("import com.lumira.saas.infrastructure.")
                        .doesNotContain("import com.lumira.saas.modules.iam.")
                        .doesNotContain("import com.lumira.saas.modules.system.");
            }
        }
    }
}

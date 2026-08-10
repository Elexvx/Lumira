package com.lumira.file.upload;

import com.lumira.common.exception.BizException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileUploadDirectoryTest {

    @Test
    void resolvesCompetitionDirectoryInsideConfiguredStorageSpace() {
        Path storageRoot = Path.of("storage/uploads").toAbsolutePath().normalize();

        FileUploadDirectory.Scope scope = FileUploadDirectory.resolve(
                storageRoot,
                "/api/uploads/",
                "competitions/ca5e4e825be14d068aba3c9cb45acad1"
        );

        assertThat(scope.storageRoot()).isEqualTo(
                storageRoot.resolve("competitions/ca5e4e825be14d068aba3c9cb45acad1")
        );
        assertThat(scope.publicPath()).isEqualTo(
                "/api/uploads/competitions/ca5e4e825be14d068aba3c9cb45acad1"
        );
        assertThat(FileUploadDirectory.qualifyObjectKey(scope.directory(), "2026/08/10/work.zip"))
                .isEqualTo("competitions/ca5e4e825be14d068aba3c9cb45acad1/2026/08/10/work.zip");
    }

    @Test
    void rejectsTraversalAndUnsafeDirectorySegments() {
        Path storageRoot = Path.of("storage/uploads").toAbsolutePath().normalize();

        assertThatThrownBy(() -> FileUploadDirectory.resolve(storageRoot, "/api/uploads", "../other"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> FileUploadDirectory.resolve(storageRoot, "/api/uploads", "competitions/a:b"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> FileUploadDirectory.resolve(storageRoot, "/api/uploads", "competitions//abc"))
                .isInstanceOf(BizException.class);
    }
}

package com.lumira.file.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lumira.file.domain.model.FileDomainModels.FileObjectAggregate;
import com.lumira.file.domain.model.FileDomainModels.StorageSpace;
import org.junit.jupiter.api.Test;

class FileDomainModelsTest {

    @Test
    void fileObjectAggregateEmitsUploadAndDeleteEvents() {
        FileObjectAggregate file = new FileObjectAggregate(100L, 1L, 4096L);

        file.recordUploaded("application/pdf");
        file.delete();
        file.delete();

        assertThat(file.domainEvents()).hasSize(2);
        assertThat(file.domainEvents().get(0).eventType()).isEqualTo("FILE_OBJECT_UPLOADED");
        assertThat(file.domainEvents().get(1).eventType()).isEqualTo("FILE_OBJECT_DELETED");
    }

    @Test
    void storageSpaceRejectsUploadsBeyondQuota() {
        StorageSpace space = new StorageSpace(1L, 1L, 100L, 80L);

        assertThat(space.canAccept(20L)).isTrue();
        assertThat(space.canAccept(21L)).isFalse();
        assertThat(space.canAccept(-1L)).isFalse();
    }

    @Test
    void fileObjectRejectsNegativeSize() {
        assertThatThrownBy(() -> new FileObjectAggregate(100L, 1L, -1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sizeBytes");
    }
}

package com.lumira.file.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lumira.file.domain.model.FileDomainModels.FileObjectAggregate;
import com.lumira.file.domain.model.FileDomainModels.StorageSpace;
import org.junit.jupiter.api.Test;

class FileDomainModelsTest {

    @Test
    void fileObjectAggregateEmitsUploadAndDeleteEvents() {
        FileObjectAggregate file = new FileObjectAggregate(100L, 4096L);

        file.recordUploaded("application/pdf");
        file.delete();
        file.delete();

        assertThat(file.domainEvents()).hasSize(2);
        assertThat(file.domainEvents().get(0).eventType()).isEqualTo("FILE_OBJECT_UPLOADED");
        assertThat(file.domainEvents().get(1).eventType()).isEqualTo("FILE_OBJECT_DELETED");
    }

    @Test
    void fileObjectAggregateShouldCarryTrustedActorWhenPresent() {
        FileObjectAggregate file = new FileObjectAggregate(100L, 4096L);

        file.recordUploaded("application/pdf", 2001L, " user-uuid-2001 ");

        assertThat(file.domainEvents()).hasSize(1);
        assertThat(file.domainEvents().getFirst().attributes())
                .containsEntry("userId", 2001L)
                .containsEntry("userUuid", "user-uuid-2001");
    }

    @Test
    void fileObjectAggregateShouldRejectActorUserIdWithoutUserUuid() {
        FileObjectAggregate file = new FileObjectAggregate(100L, 4096L);

        assertThatThrownBy(() -> file.recordUploaded("application/pdf", 2001L, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(file.domainEvents()).isEmpty();
    }

    @Test
    void storageSpaceRejectsUploadsBeyondQuota() {
        StorageSpace space = new StorageSpace(1L, 100L, 80L);

        assertThat(space.canAccept(20L)).isTrue();
        assertThat(space.canAccept(21L)).isFalse();
        assertThat(space.canAccept(-1L)).isFalse();
    }

    @Test
    void fileObjectRejectsNegativeSize() {
        assertThatThrownBy(() -> new FileObjectAggregate(100L, -1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sizeBytes");
    }
}

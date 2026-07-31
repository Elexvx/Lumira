package com.lumira.saas.infrastructure.readmodel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReadModelEventKeyTest {

    @Test
    void uniqueShouldCreateDifferentKeysForRepeatedMutationTypes() {
        String first = ReadModelEventKey.unique("security-update");
        String second = ReadModelEventKey.unique("security-update");

        assertThat(first).startsWith("security-update:");
        assertThat(second).startsWith("security-update:");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void uniqueShouldRejectBlankEventType() {
        assertThatThrownBy(() -> ReadModelEventKey.unique(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

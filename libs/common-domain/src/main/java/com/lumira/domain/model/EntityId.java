package com.lumira.domain.model;

import java.io.Serializable;
import java.util.Objects;

public record EntityId<T extends Serializable>(T value) implements Serializable {

    public EntityId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static <T extends Serializable> EntityId<T> of(T value) {
        return new EntityId<>(value);
    }
}

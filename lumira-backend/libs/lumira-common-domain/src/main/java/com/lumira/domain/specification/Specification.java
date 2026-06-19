package com.lumira.domain.specification;

import java.util.Objects;

@FunctionalInterface
public interface Specification<T> {

    boolean isSatisfiedBy(T candidate);

    default Specification<T> and(Specification<T> other) {
        Objects.requireNonNull(other, "other must not be null");
        return candidate -> isSatisfiedBy(candidate) && other.isSatisfiedBy(candidate);
    }

    default Specification<T> or(Specification<T> other) {
        Objects.requireNonNull(other, "other must not be null");
        return candidate -> isSatisfiedBy(candidate) || other.isSatisfiedBy(candidate);
    }

    default Specification<T> negate() {
        return candidate -> !isSatisfiedBy(candidate);
    }
}

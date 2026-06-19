package com.lumira.domain.repository;

import java.io.Serializable;
import java.util.Optional;

public interface Repository<T, ID extends Serializable> {

    Optional<T> findById(ID id);

    T save(T aggregate);
}

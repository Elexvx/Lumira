package com.lumira.domain.model;

import com.lumira.domain.event.DomainEvent;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public abstract class AggregateRoot<ID extends Serializable> implements Serializable {

    private final EntityId<ID> id;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    protected AggregateRoot(EntityId<ID> id) {
        this.id = Objects.requireNonNull(id, "id must not be null");
    }

    public EntityId<ID> id() {
        return id;
    }

    protected void registerEvent(DomainEvent event) {
        domainEvents.add(Objects.requireNonNull(event, "event must not be null"));
    }

    public List<DomainEvent> domainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> pulled = List.copyOf(domainEvents);
        domainEvents.clear();
        return pulled;
    }
}

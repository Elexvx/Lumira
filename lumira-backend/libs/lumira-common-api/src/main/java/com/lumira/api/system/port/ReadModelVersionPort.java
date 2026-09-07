package com.lumira.api.system.port;

public interface ReadModelVersionPort {
    Boolean bumpReadModelVersion(String contextName, String scope, String eventKey);
    Long readModelVersion(String contextName, String scope);
}

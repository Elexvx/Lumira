package com.lumira.api.system.port;

/** User/authorization capability plus a versioned read-model check for business projections. */
public interface SystemUserAuthorizationReadModelPort
        extends SystemUserAuthorizationPort, ReadModelVersionPort {
}

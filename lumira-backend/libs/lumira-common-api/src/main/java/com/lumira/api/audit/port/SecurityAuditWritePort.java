package com.lumira.api.audit.port;

import com.lumira.api.audit.SecurityAuditRecord;

/** Platform-owned write boundary for security audit events. */
public interface SecurityAuditWritePort {
    void write(SecurityAuditRecord record);
}

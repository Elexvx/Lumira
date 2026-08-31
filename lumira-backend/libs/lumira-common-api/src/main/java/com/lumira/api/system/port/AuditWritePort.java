package com.lumira.api.system.port;

import com.lumira.api.system.LoginAuditRecordRequestDTO;
import com.lumira.api.system.OperationAuditRecordRequestDTO;

public interface AuditWritePort {
    Boolean recordLoginAudit(LoginAuditRecordRequestDTO request);
    Boolean recordOperationAudit(OperationAuditRecordRequestDTO request);
}

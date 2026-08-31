package com.lumira.saas.modules.system.internal.app;

import com.lumira.api.system.port.AuditWritePort;
import com.lumira.api.system.port.AuthorizationVersionPort;
import com.lumira.api.system.port.MenuCatalogPort;
import com.lumira.api.system.port.PasskeyPort;
import com.lumira.api.system.port.PermissionSnapshotPort;
import com.lumira.api.system.port.PluginPermissionRegistrationPort;
import com.lumira.api.system.port.ReadModelVersionPort;
import com.lumira.api.system.port.RuntimeConfigurationPort;
import com.lumira.api.system.port.UserIdentityQueryPort;
import com.lumira.api.system.port.VerificationPort;

/** In-process application boundary shared by the HTTP and local adapters. */
public interface SystemInternalApplicationPort extends
        UserIdentityQueryPort,
        PermissionSnapshotPort,
        AuthorizationVersionPort,
        VerificationPort,
        PasskeyPort,
        AuditWritePort,
        RuntimeConfigurationPort,
        MenuCatalogPort,
        PluginPermissionRegistrationPort,
        ReadModelVersionPort {
}

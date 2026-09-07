package com.lumira.saas.modules.system.internal.app;

import com.lumira.api.system.port.AuditWritePort;
import com.lumira.api.system.port.AuthorizationVersionPort;
import com.lumira.api.system.port.MenuCatalogPort;
import com.lumira.api.system.port.PasskeyPort;
import com.lumira.api.system.port.PermissionSnapshotPort;
import com.lumira.api.system.port.PluginPermissionRegistrationPort;
import com.lumira.api.system.port.ReadModelVersionPort;
import com.lumira.api.system.port.RuntimeConfigurationPort;
import com.lumira.api.system.port.SystemAuthenticationPort;
import com.lumira.api.system.port.SystemPasskeyAuthenticationPort;
import com.lumira.api.system.port.SystemPluginManagementPort;
import com.lumira.api.system.port.SystemSecurityConfigurationPort;
import com.lumira.api.system.port.SystemUserAuthorizationPort;
import com.lumira.api.system.port.SystemUserAuthorizationReadModelPort;
import com.lumira.api.system.port.UserIdentityQueryPort;
import com.lumira.api.system.port.UserDirectoryQueryPort;
import com.lumira.api.system.port.VerificationPort;

/** In-process application boundary shared by the HTTP and local adapters. */
public interface SystemInternalApplicationPort extends
        SystemUserAuthorizationPort,
        SystemAuthenticationPort,
        SystemPasskeyAuthenticationPort,
        SystemPluginManagementPort,
        SystemSecurityConfigurationPort,
        SystemUserAuthorizationReadModelPort,
        UserIdentityQueryPort,
        PermissionSnapshotPort,
        AuthorizationVersionPort,
        VerificationPort,
        PasskeyPort,
        AuditWritePort,
        RuntimeConfigurationPort,
        UserDirectoryQueryPort,
        MenuCatalogPort,
        PluginPermissionRegistrationPort,
        ReadModelVersionPort {
}

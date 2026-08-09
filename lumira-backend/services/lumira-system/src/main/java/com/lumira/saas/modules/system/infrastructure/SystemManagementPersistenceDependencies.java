package com.lumira.saas.modules.system.infrastructure;

import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import com.lumira.saas.modules.system.audit.repository.SystemAuditQueryRepository;
import com.lumira.saas.modules.system.config.repository.SystemConfigurationManagementRepository;
import com.lumira.saas.modules.system.dict.repository.SystemDictionaryManagementRepository;
import com.lumira.saas.modules.system.menu.repository.SystemMenuManagementRepository;
import com.lumira.saas.modules.system.profile.repository.SystemCurrentUserProfileRepository;
import org.springframework.stereotype.Component;

/** Wiring bundle only: application code still talks to the five focused persistence ports. */
@Component
public record SystemManagementPersistenceDependencies(
        SystemCurrentUserProfileRepository currentUserProfiles,
        SystemMenuManagementRepository menus,
        SystemDictionaryManagementRepository dictionaries,
        SystemConfigurationManagementRepository configurations,
        SystemAuditQueryRepository auditQueries,
        ReadModelVersionService readModelVersions
) {}

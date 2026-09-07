package com.lumira.saas.modules.plugin.service;

import com.lumira.saas.modules.plugin.entity.PluginEntities.PluginMigrationRequestEntity;
import com.lumira.saas.modules.plugin.entity.PluginEntities.PluginVersionEntity;
import com.lumira.saas.modules.plugin.mapper.PluginPersistenceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class PluginMigrationRequestService {

    private final PluginPersistenceMapper mapper;

    public PluginMigrationRequestService(PluginPersistenceMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public PluginMigrationRequestEntity enqueue(PluginMigrationRequestEntity request) {
        PluginMigrationRequestEntity existing = mapper.findMigrationRequestByDigest(
                request.getPluginCode(), request.getPluginVersion(), request.getMigrationDigest());
        if (existing != null) {
            return existing;
        }
        request.setOperationEpoch(mapper.nextMigrationOperationEpoch(request.getPluginCode()));
        mapper.insertMigrationRequest(request);
        PluginVersionEntity version = mapper.findVersion(request.getPluginCode(), request.getPluginVersion());
        if (version == null) {
            throw new IllegalStateException("Plugin version disappeared before migration enqueue");
        }
        PluginLifecycleStateMachine.State from = PluginLifecycleStateMachine.State.valueOf(version.getLifecycleStatus());
        PluginLifecycleStateMachine.requireTransition(from, PluginLifecycleStateMachine.State.MIGRATION_PENDING);
        int updated = mapper.updateMigrationLifecycle(
                request.getPluginCode(), request.getPluginVersion(), from.name(), "MIGRATION_PENDING", "PENDING");
        if (updated != 1) {
            throw new IllegalStateException("Plugin version state changed before migration request was persisted");
        }
        return request;
    }

    public Optional<PluginMigrationRequestEntity> find(String pluginCode, String pluginVersion, String migrationDigest) {
        return Optional.ofNullable(mapper.findMigrationRequestByDigest(pluginCode, pluginVersion, migrationDigest));
    }

}

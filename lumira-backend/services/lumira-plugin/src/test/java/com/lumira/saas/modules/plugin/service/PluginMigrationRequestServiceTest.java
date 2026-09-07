package com.lumira.saas.modules.plugin.service;

import com.lumira.saas.modules.plugin.mapper.PluginPersistenceMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PluginMigrationRequestServiceTest {
    @Test
    void applicationServiceDoesNotExposePrivilegedMigratorOperations() {
        PluginMigrationRequestService service = new PluginMigrationRequestService(mock(PluginPersistenceMapper.class));

        assertThat(service).isNotInstanceOf(CentralPluginMigratorPort.class);
        assertThat(PluginMigrationRequestService.class.getDeclaredMethods())
                .extracting("name")
                .containsExactlyInAnyOrder("enqueue", "find");
    }
}

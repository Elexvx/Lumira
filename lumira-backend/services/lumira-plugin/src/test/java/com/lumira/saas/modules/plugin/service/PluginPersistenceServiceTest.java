package com.lumira.saas.modules.plugin.service;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.PluginPermissionRegistrationRequestDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.plugin.entity.PluginEntities.PluginPermissionRelEntity;
import com.lumira.saas.modules.plugin.mapper.PluginPersistenceMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PluginPersistenceServiceTest {

    @Test
    void registerPluginPermissionsDelegatesIamWritesToSystemOwnerApi() {
        PluginPersistenceMapper mapper = mock(PluginPersistenceMapper.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        PluginPersistenceService service = new PluginPersistenceService(mapper, systemInternalApi);
        PluginPermissionRelEntity permission = new PluginPermissionRelEntity();
        permission.setPermissionKey("plugin:sms:view");
        permission.setPermissionName("SMS View");
        permission.setPermissionGroup("sms");
        when(mapper.listPermissionRelations("sms", "1.0.0")).thenReturn(List.of(permission));

        service.registerPluginPermissions("sms", "1.0.0");

        var captor = forClass(PluginPermissionRegistrationRequestDTO.class);
        verify(systemInternalApi).registerPluginPermissions(captor.capture());
        PluginPermissionRegistrationRequestDTO request = captor.getValue();
        assertThat(request.pluginCode()).isEqualTo("sms");
        assertThat(request.permissions()).singleElement().satisfies(item -> {
            assertThat(item.permissionKey()).isEqualTo("plugin:sms:view");
            assertThat(item.permissionName()).isEqualTo("SMS View");
            assertThat(item.permissionGroup()).isEqualTo("sms");
        });
    }

    @Test
    void bumpBootstrapVersionDelegatesReadModelWriteToSystemOwnerApi() {
        PluginPersistenceMapper mapper = mock(PluginPersistenceMapper.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        PluginPersistenceService service = new PluginPersistenceService(mapper, systemInternalApi);

        service.bumpBootstrapVersion("plugin.enabled");

        verify(systemInternalApi).bumpReadModelVersion("plugin", "bootstrap", "plugin.enabled");
    }

    @Test
    void versionStateUpdatesDelegateTrustedOperatorUuidToMapper() {
        PluginPersistenceMapper mapper = mock(PluginPersistenceMapper.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        PluginPersistenceService service = new PluginPersistenceService(mapper, systemInternalApi);
        when(systemInternalApi.findUserById(100L)).thenReturn(userSnapshot(100L, "user-uuid-100", "ENABLED"));
        when(mapper.updateVersionStatus("sms", "1.0.0", "LOADED", "LOADED", "HEALTHY", "ENABLED", "READY", 100L, "user-uuid-100")).thenReturn(1);
        when(mapper.activateVersion("sms", "1.0.0", 100L, "user-uuid-100")).thenReturn(1);

        service.updateVersionStatus("sms", "1.0.0", "LOADED", "LOADED", "HEALTHY", "ENABLED", "READY", 100L, " user-uuid-100 ");
        service.activateVersion("sms", "1.0.0", 100L, " user-uuid-100 ");

        verify(mapper).updateVersionStatus("sms", "1.0.0", "LOADED", "LOADED", "HEALTHY", "ENABLED", "READY", 100L, "user-uuid-100");
        verify(mapper).deactivateOtherVersions("sms", "1.0.0", 100L, "user-uuid-100");
        verify(mapper).activateVersion("sms", "1.0.0", 100L, "user-uuid-100");
    }

    @Test
    void versionStateUpdatesRejectMissingOperatorUuidBeforeMapperWrite() {
        PluginPersistenceMapper mapper = mock(PluginPersistenceMapper.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        PluginPersistenceService service = new PluginPersistenceService(mapper, systemInternalApi);

        assertThatThrownBy(() -> service.activateVersion("sms", "1.0.0", 100L, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trusted plugin operator identity");
        verifyNoInteractions(mapper, systemInternalApi);
    }

    @Test
    void versionStateUpdatesRejectDisabledOperatorBeforeMapperWrite() {
        PluginPersistenceMapper mapper = mock(PluginPersistenceMapper.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        PluginPersistenceService service = new PluginPersistenceService(mapper, systemInternalApi);
        when(systemInternalApi.findUserById(100L)).thenReturn(userSnapshot(100L, "user-uuid-100", "DISABLED"));

        assertThatThrownBy(() -> service.activateVersion("sms", "1.0.0", 100L, "user-uuid-100"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trusted plugin operator identity");
        verifyNoInteractions(mapper);
    }

    @Test
    void versionStateUpdatesRejectStaleFinalWrite() {
        PluginPersistenceMapper mapper = mock(PluginPersistenceMapper.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        PluginPersistenceService service = new PluginPersistenceService(mapper, systemInternalApi);
        when(systemInternalApi.findUserById(100L)).thenReturn(userSnapshot(100L, "user-uuid-100", "ENABLED"));
        when(mapper.activateVersion("sms", "1.0.0", 100L, "user-uuid-100")).thenReturn(0);

        assertThatThrownBy(() -> service.activateVersion("sms", "1.0.0", 100L, "user-uuid-100"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Plugin state changed");
    }

    @Test
    void uninstallRejectsStaleOrBuiltinDefinitionFinalWrite() {
        PluginPersistenceMapper mapper = mock(PluginPersistenceMapper.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        PluginPersistenceService service = new PluginPersistenceService(mapper, systemInternalApi);
        when(systemInternalApi.findUserById(100L)).thenReturn(userSnapshot(100L, "user-uuid-100", "ENABLED"));
        when(mapper.markDefinitionDeletedByPlugin("sms", 100L, "user-uuid-100")).thenReturn(0);

        assertThatThrownBy(() -> service.uninstallPlugin("sms", 100L, "user-uuid-100"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("Plugin state changed");
    }

    private static SystemUserSnapshotDTO userSnapshot(Long userId, String userUuid, String status) {
        return new SystemUserSnapshotDTO(
                userId,
                userUuid,
                "operator",
                null,
                status,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}

package com.lumira.saas.modules.plugin.service;

import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.PluginPermissionRegistrationRequestDTO;
import com.lumira.saas.modules.plugin.entity.PluginEntities.PluginPermissionRelEntity;
import com.lumira.saas.modules.plugin.mapper.PluginPersistenceMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
}

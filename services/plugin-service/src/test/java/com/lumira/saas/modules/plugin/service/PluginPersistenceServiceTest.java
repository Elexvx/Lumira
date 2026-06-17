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
    void registerTenantPermissionsDelegatesIamWritesToSystemOwnerApi() {
        PluginPersistenceMapper mapper = mock(PluginPersistenceMapper.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        PluginPersistenceService service = new PluginPersistenceService(mapper, systemInternalApi);
        PluginPermissionRelEntity permission = new PluginPermissionRelEntity();
        permission.setPermissionKey("plugin:sms:view");
        permission.setPermissionName("短信插件查看");
        permission.setPermissionGroup("sms");
        when(mapper.listPermissionRelations("sms", "1.0.0")).thenReturn(List.of(permission));

        service.registerTenantPermissions(1001L, "sms", "1.0.0");

        var captor = forClass(PluginPermissionRegistrationRequestDTO.class);
        verify(systemInternalApi).registerPluginPermissions(captor.capture());
        PluginPermissionRegistrationRequestDTO request = captor.getValue();
        assertThat(request.tenantId()).isEqualTo(1001L);
        assertThat(request.pluginCode()).isEqualTo("sms");
        assertThat(request.permissions()).singleElement().satisfies(item -> {
            assertThat(item.permissionKey()).isEqualTo("plugin:sms:view");
            assertThat(item.permissionName()).isEqualTo("短信插件查看");
            assertThat(item.permissionGroup()).isEqualTo("sms");
        });
    }

    @Test
    void bumpBootstrapVersionDelegatesReadModelWriteToSystemOwnerApi() {
        PluginPersistenceMapper mapper = mock(PluginPersistenceMapper.class);
        SystemInternalApi systemInternalApi = mock(SystemInternalApi.class);
        PluginPersistenceService service = new PluginPersistenceService(mapper, systemInternalApi);

        service.bumpBootstrapVersion(1001L, "plugin.enabled");

        verify(systemInternalApi).bumpReadModelVersion(1001L, "plugin", "bootstrap", "plugin.enabled");
    }
}

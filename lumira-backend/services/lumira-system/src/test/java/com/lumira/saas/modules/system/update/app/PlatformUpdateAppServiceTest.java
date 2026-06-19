package com.lumira.saas.modules.system.update.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.saas.modules.system.update.mapper.PlatformUpdateTaskMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformUpdateAppServiceTest {

    @Test
    void fromManifestShouldRejectUnpinnedImageReferences() throws Exception {
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class),
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                mock(PlatformUpdateTaskMapper.class)
        );
        Method method = PlatformUpdateAppService.class.getDeclaredMethod(
                "fromManifest",
                com.fasterxml.jackson.databind.JsonNode.class
        );
        method.setAccessible(true);
        var manifest = new ObjectMapper().readTree("""
                {
                  "version": "1.2.3",
                  "commit": "abc123",
                  "serverImage": "ghcr.io/example/lumira-server:latest"
                }
                """);

        assertThrows(InvocationTargetException.class, () -> method.invoke(service, manifest));
    }

    @Test
    void validateManifestSourceUrlShouldRequireHttps() throws Exception {
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                mock(Environment.class),
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                mock(PlatformUpdateTaskMapper.class)
        );
        Method method = PlatformUpdateAppService.class.getDeclaredMethod("validateManifestSourceUrl", String.class);
        method.setAccessible(true);

        assertThrows(InvocationTargetException.class, () -> method.invoke(service, "http://updates.example.com/manifest.json"));
    }

    @Test
    void validateManifestSourceUrlShouldRejectHostsOutsideAllowlist() throws Exception {
        Environment environment = mock(Environment.class);
        when(environment.getProperty("PLATFORM_UPDATE_ALLOWED_HOSTS")).thenReturn("updates.example.com");
        PlatformUpdateAppService service = new PlatformUpdateAppService(
                environment,
                mockBuildPropertiesProvider(),
                new ObjectMapper(),
                mock(PlatformUpdateTaskMapper.class)
        );
        Method method = PlatformUpdateAppService.class.getDeclaredMethod("validateManifestSourceUrl", String.class);
        method.setAccessible(true);

        assertThrows(InvocationTargetException.class, () -> method.invoke(service, "https://evil.example.com/manifest.json"));
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<BuildProperties> mockBuildPropertiesProvider() {
        return mock(ObjectProvider.class);
    }
}

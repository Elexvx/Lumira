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

    @SuppressWarnings("unchecked")
    private ObjectProvider<BuildProperties> mockBuildPropertiesProvider() {
        return mock(ObjectProvider.class);
    }
}

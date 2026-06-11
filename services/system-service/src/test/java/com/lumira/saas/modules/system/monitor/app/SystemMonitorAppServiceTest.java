package com.lumira.saas.modules.system.monitor.app;

import com.lumira.saas.modules.system.monitor.vo.SystemMonitorVO;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SystemMonitorAppServiceTest {

    @Test
    void parsesCommandStatsAndSortsByCallsDescending() {
        Properties info = new Properties();
        info.setProperty("cmdstat_get", "calls=10,usec=100,usec_per_call=10.00,rejected_calls=1,failed_calls=0");
        info.setProperty("cmdstat_set", "calls=42,usec=210,usec_per_call=5.00,rejected_calls=0,failed_calls=0");

        var stats = SystemMonitorAppService.parseCommandStats(info);
        assertEquals("set", stats.getFirst().getCommand());
        assertEquals(42L, stats.getFirst().getCalls());
    }

    @Test
    void parsesKeyspaceStats() {
        Properties info = new Properties();
        info.setProperty("db0", "keys=4,expires=2,avg_ttl=55776312");
        info.setProperty("db2", "keys=9,expires=0,avg_ttl=0");

        var keyspaces = SystemMonitorAppService.parseKeyspaceStats(info);
        assertEquals(2, keyspaces.size());
        assertEquals("db0", keyspaces.getFirst().getDatabase());
        assertEquals(4L, keyspaces.getFirst().getKeys());
        assertFalse(keyspaces.isEmpty());
    }

    @Test
    void calculatesHitRateAndMemoryUsage() {
        assertEquals(66.67D, SystemMonitorAppService.calculateHitRate(2, 1), 0.0001D);

        Properties info = new Properties();
        info.setProperty("used_memory", "100");
        info.setProperty("maxmemory", "200");
        assertEquals(50.0D, SystemMonitorAppService.calculateMemoryUsagePercent(info), 0.0001D);
    }

    @Test
    void resolvesContainerServiceBaseUrlFromMonitorEnvironment() {
        var endpoint = new SystemMonitorAppService.ServiceEndpoint("auth-service", "http://localhost:8082");

        String baseUrl = SystemMonitorAppService.resolveBaseUrl(endpoint, Map.of(
                "MONITOR_AUTH_SERVICE_BASE_URL", "http://auth-service:8082/"
        ));

        assertEquals("http://auth-service:8082", baseUrl);
    }

    @Test
    void resolvesServiceBaseUrlFromGatewayEnvironmentForCompatibility() {
        var endpoint = new SystemMonitorAppService.ServiceEndpoint("file-service", "http://localhost:8084");

        String baseUrl = SystemMonitorAppService.resolveBaseUrl(endpoint, Map.of(
                "GATEWAY_FILE_SERVICE_URI", "http://file-service:8084"
        ));

        assertEquals("http://file-service:8084", baseUrl);
    }

    @Test
    void fallsBackToLocalBaseUrlWhenMonitorEnvironmentIsMissing() {
        var endpoint = new SystemMonitorAppService.ServiceEndpoint("lumira-server", "http://localhost:8080");

        String baseUrl = SystemMonitorAppService.resolveBaseUrl(endpoint, Map.of());

        assertEquals("http://localhost:8080", baseUrl);
    }
}

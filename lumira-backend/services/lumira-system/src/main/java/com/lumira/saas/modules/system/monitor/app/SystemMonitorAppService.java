package com.lumira.saas.modules.system.monitor.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.management.OperatingSystemMXBean;
import com.lumira.saas.modules.system.monitor.vo.SystemMonitorVO;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.RedisClientInfo;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SystemMonitorAppService {

    private static final Pattern CMD_STAT_PATTERN = Pattern.compile("^cmdstat_(.+)$");
    private static final Pattern KEYSPACE_PATTERN = Pattern.compile("^(db\\d+)$");
    private static final List<ServiceEndpoint> SERVICE_ENDPOINTS = List.of(
            new ServiceEndpoint("lumira-server", "http://localhost:8080")
    );
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
    private final Instant applicationStartInstant = Instant.now();

    public SystemMonitorAppService(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public SystemMonitorVO.ServiceMonitorVO getServiceMonitor() {
        java.lang.management.OperatingSystemMXBean rawOsBean = ManagementFactory.getOperatingSystemMXBean();
        OperatingSystemMXBean osBean = rawOsBean instanceof OperatingSystemMXBean sunOsBean ? sunOsBean : null;
        Runtime runtime = Runtime.getRuntime();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapMemoryUsage = memoryMXBean.getNonHeapMemoryUsage();
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

        long totalPhysicalMemoryBytes = osBean == null ? -1L : Math.max(osBean.getTotalMemorySize(), 0L);
        long freePhysicalMemoryBytes = osBean == null ? -1L : Math.max(osBean.getFreeMemorySize(), 0L);
        long usedPhysicalMemoryBytes = Math.max(totalPhysicalMemoryBytes - freePhysicalMemoryBytes, 0L);
        HostMemory hostMemory = readHostMemory();
        Double processCpuLoad = osBean == null ? null : normalizePercent(osBean.getProcessCpuLoad());
        Double systemCpuLoad = osBean == null ? null : normalizePercent(osBean.getCpuLoad());
        Double idlePercent = systemCpuLoad == null ? null : clampPercent(100D - systemCpuLoad);
        double loadAverage = osBean == null ? -1D : osBean.getSystemLoadAverage();
        long uptimeSeconds = Duration.between(applicationStartInstant, Instant.now()).toSeconds();

        SystemMonitorVO.CpuVO cpu = new SystemMonitorVO.CpuVO();
        cpu.setCoreCount(runtime.availableProcessors());
        cpu.setProcessUsagePercent(processCpuLoad);
        cpu.setSystemUsagePercent(systemCpuLoad);
        cpu.setIdlePercent(idlePercent);
        cpu.setLoadAverage(loadAverage < 0 ? null : round(loadAverage, 2));

        SystemMonitorVO.MemoryVO memory = new SystemMonitorVO.MemoryVO();
        memory.setTotalBytes(totalPhysicalMemoryBytes);
        memory.setUsedBytes(usedPhysicalMemoryBytes);
        memory.setFreeBytes(freePhysicalMemoryBytes);
        memory.setUsagePercent(totalPhysicalMemoryBytes <= 0 ? null : round((usedPhysicalMemoryBytes * 100D) / totalPhysicalMemoryBytes, 2));
        memory.setHeapMaxBytes(heapMemoryUsage.getMax() < 0 ? null : heapMemoryUsage.getMax());
        memory.setHeapUsedBytes(Math.max(heapMemoryUsage.getUsed(), 0L));
        memory.setHeapCommittedBytes(Math.max(heapMemoryUsage.getCommitted(), 0L));
        memory.setNonHeapUsedBytes(Math.max(nonHeapMemoryUsage.getUsed(), 0L));
        memory.setHostTotalBytes(hostMemory.totalBytes());
        memory.setHostUsedBytes(hostMemory.usedBytes());
        memory.setHostFreeBytes(hostMemory.freeBytes());
        memory.setHostUsagePercent(hostMemory.usagePercent());

        SystemMonitorVO.ServerVO server = new SystemMonitorVO.ServerVO();
        server.setServerName(resolveHostName());
        server.setServerIp(resolveHostAddress());
        server.setOsName(System.getProperty("os.name"));
        server.setOsArch(System.getProperty("os.arch"));
        server.setOsVersion(System.getProperty("os.version"));
        server.setProjectPath(System.getProperty("user.dir"));
        server.setInstallPath(Path.of("").toAbsolutePath().toString());
        server.setUserHome(System.getProperty("user.home"));
        server.setTempDir(System.getProperty("java.io.tmpdir"));

        SystemMonitorVO.JvmVO jvm = new SystemMonitorVO.JvmVO();
        jvm.setVmName(runtimeMXBean.getVmName());
        jvm.setVmVersion(runtimeMXBean.getVmVersion());
        jvm.setVmVendor(runtimeMXBean.getVmVendor());
        jvm.setJavaVersion(System.getProperty("java.version"));
        jvm.setJavaHome(System.getProperty("java.home"));
        jvm.setPid(resolvePid());
        jvm.setStartTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(runtimeMXBean.getStartTime()), ZoneId.systemDefault()));
        jvm.setUptimeSeconds(runtimeMXBean.getUptime() / 1000L);
        jvm.setThreadCount(threadMXBean.getThreadCount());
        jvm.setDaemonThreadCount(threadMXBean.getDaemonThreadCount());
        jvm.setPeakThreadCount(threadMXBean.getPeakThreadCount());
        jvm.setInputArguments(runtimeMXBean.getInputArguments());

        SystemMonitorVO.ServiceMonitorVO serviceMonitorVO = new SystemMonitorVO.ServiceMonitorVO();
        serviceMonitorVO.setCpu(cpu);
        serviceMonitorVO.setMemory(memory);
        serviceMonitorVO.setServer(server);
        serviceMonitorVO.setJvm(jvm);
        serviceMonitorVO.setServices(probeServices());
        serviceMonitorVO.setApiDocs(serviceMonitorVO.getServices().stream().map(this::toApiDoc).toList());
        return serviceMonitorVO;
    }

    private HostMemory readHostMemory() {
        Path memInfoPath = Path.of("/proc/meminfo");
        if (!Files.isReadable(memInfoPath)) {
            return HostMemory.empty();
        }
        try {
            Map<String, Long> values = Files.readAllLines(memInfoPath).stream()
                    .map(line -> line.split("\\s+"))
                    .filter(parts -> parts.length >= 2)
                    .collect(Collectors.toMap(parts -> parts[0].replace(":", ""), parts -> parseLong(parts[1], 0L) * 1024L, (left, right) -> left));
            Long totalBytes = values.get("MemTotal");
            Long availableBytes = values.getOrDefault("MemAvailable", values.get("MemFree"));
            if (totalBytes == null || availableBytes == null || totalBytes <= 0) {
                return HostMemory.empty();
            }
            long safeAvailableBytes = Math.max(availableBytes, 0L);
            long usedBytes = Math.max(totalBytes - safeAvailableBytes, 0L);
            return new HostMemory(totalBytes, usedBytes, safeAvailableBytes, round((usedBytes * 100D) / totalBytes, 2));
        } catch (Exception ignored) {
            return HostMemory.empty();
        }
    }

    private List<SystemMonitorVO.ServiceInstanceVO> probeServices() {
        return SERVICE_ENDPOINTS.stream().map(this::probeService).toList();
    }

    private SystemMonitorVO.ServiceInstanceVO probeService(ServiceEndpoint endpoint) {
        String baseUrl = resolveBaseUrl(endpoint);
        String healthUrl = baseUrl + "/actuator/health";
        SystemMonitorVO.ServiceInstanceVO service = new SystemMonitorVO.ServiceInstanceVO();
        service.setServiceName(endpoint.serviceName());
        service.setBaseUrl(baseUrl);
        service.setHealthUrl(healthUrl);
        service.setCheckedAt(LocalDateTime.now());
        long startedAt = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(healthUrl))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            service.setResponseTimeMs(Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
            boolean healthy = response.statusCode() >= 200
                    && response.statusCode() < 300
                    && response.body() != null
                    && response.body().contains("\"UP\"");
            service.setStatus(healthy ? "UP" : "DOWN");
            service.setVersion(healthy ? probeServiceVersion(baseUrl) : null);
            if (!"UP".equals(service.getStatus())) {
                service.setErrorMessage("HTTP " + response.statusCode());
            }
        } catch (Exception ex) {
            service.setResponseTimeMs(Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
            service.setStatus("DOWN");
            service.setErrorMessage(ex.getMessage());
        }
        return service;
    }

    private String probeServiceVersion(String baseUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/version"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null) {
                return null;
            }
            JsonNode versionNode = objectMapper.readTree(response.body()).path("data").path("version");
            return versionNode.isTextual() && !versionNode.asText().isBlank() ? versionNode.asText() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resolveBaseUrl(ServiceEndpoint endpoint) {
        return resolveBaseUrl(endpoint, System.getenv());
    }

    static String resolveBaseUrl(ServiceEndpoint endpoint, Map<String, String> environment) {
        String serviceKey = endpoint.serviceName().replace("-", "_").toUpperCase(Locale.ROOT);
        List<String> candidateKeys = List.of(
                "MONITOR_" + serviceKey + "_BASE_URL",
                serviceKey + "_BASE_URL",
                "GATEWAY_" + serviceKey + "_URI"
        );
        for (String key : candidateKeys) {
            String value = environment.get(key);
            if (value != null && !value.isBlank()) {
                return stripTrailingSlash(value.trim());
            }
        }
        return endpoint.defaultBaseUrl();
    }

    private static String stripTrailingSlash(String value) {
        String normalized = value;
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private SystemMonitorVO.ApiDocVO toApiDoc(SystemMonitorVO.ServiceInstanceVO service) {
        SystemMonitorVO.ApiDocVO doc = new SystemMonitorVO.ApiDocVO();
        doc.setServiceName(service.getServiceName());
        doc.setUrl(service.getBaseUrl() + "/api-docs");
        doc.setStatus(service.getStatus());
        return doc;
    }

    public SystemMonitorVO.RedisMonitorVO getRedisMonitor() {
        return stringRedisTemplate.execute((RedisConnection connection) -> {
            RedisServerCommands serverCommands = connection.serverCommands();
            Properties info = serverCommands.info();
            List<RedisClientInfo> clientInfos = serverCommands.getClientList();
            Long keyCount = serverCommands.dbSize();

            SystemMonitorVO.OverviewVO overview = new SystemMonitorVO.OverviewVO();
            overview.setVersion(stringValue(info, "redis_version"));
            overview.setMode(stringValue(info, "redis_mode"));
            overview.setPort(intValue(info, "tcp_port"));
            overview.setConnectedClients(intValue(info, "connected_clients"));
            overview.setUptimeSeconds(longValue(info, "uptime_in_seconds"));
            overview.setUptimeDays(longValue(info, "uptime_in_days"));
            overview.setKeyCount(keyCount);
            overview.setTotalConnectionsReceived(longValue(info, "total_connections_received"));
            overview.setTotalCommandsProcessed(longValue(info, "total_commands_processed"));
            overview.setInstantaneousOpsPerSec(longValue(info, "instantaneous_ops_per_sec"));
            overview.setMemoryUsedBytes(longValue(info, "used_memory"));
            overview.setMemoryPeakBytes(longValue(info, "used_memory_peak"));
            overview.setMemoryMaxBytes(longValue(info, "maxmemory"));
            overview.setMemoryUsagePercent(calculateMemoryUsagePercent(info));
            long hits = longValue(info, "keyspace_hits");
            long misses = longValue(info, "keyspace_misses");
            overview.setHits(hits);
            overview.setMisses(misses);
            overview.setHitRate(calculateHitRate(hits, misses));

            SystemMonitorVO.RedisMonitorVO redisMonitorVO = new SystemMonitorVO.RedisMonitorVO();
            redisMonitorVO.setOverview(overview);
            redisMonitorVO.setCommandStats(parseCommandStats(info));
            redisMonitorVO.setKeyspaces(parseKeyspaceStats(info));
            redisMonitorVO.setClients(clientInfos.stream().map(SystemMonitorAppService::toClientVO).collect(Collectors.toList()));
            redisMonitorVO.setSampleTime(LocalDateTime.now());
            return redisMonitorVO;
        });
    }

    static List<SystemMonitorVO.CommandStatVO> parseCommandStats(Properties info) {
        List<SystemMonitorVO.CommandStatVO> commandStats = new ArrayList<>();
        for (String key : info.stringPropertyNames()) {
            Matcher matcher = CMD_STAT_PATTERN.matcher(key);
            if (!matcher.matches()) {
                continue;
            }
            String commandName = matcher.group(1);
            String[] parts = info.getProperty(key, "").split(",");
            Map<String, String> values = new java.util.LinkedHashMap<>();
            for (String part : parts) {
                String[] pair = part.split("=", 2);
                if (pair.length == 2) {
                    values.put(pair[0].trim(), pair[1].trim());
                }
            }

            SystemMonitorVO.CommandStatVO statVO = new SystemMonitorVO.CommandStatVO();
            statVO.setCommand(commandName);
            statVO.setCalls(parseLong(values.get("calls"), 0L));
            statVO.setTotalUsec(parseLong(values.get("usec"), 0L));
            statVO.setAvgUsec(parseDouble(values.get("usec_per_call"), 0D));
            statVO.setRejectedCalls(parseLong(values.get("rejected_calls"), 0L));
            statVO.setFailedCalls(parseLong(values.get("failed_calls"), 0L));
            commandStats.add(statVO);
        }

        commandStats.sort((left, right) -> Long.compare(
                right.getCalls() == null ? 0L : right.getCalls(),
                left.getCalls() == null ? 0L : left.getCalls()
        ));
        return commandStats;
    }

    static List<SystemMonitorVO.KeyspaceVO> parseKeyspaceStats(Properties info) {
        List<SystemMonitorVO.KeyspaceVO> keyspaces = new ArrayList<>();
        for (String key : info.stringPropertyNames()) {
            if (!KEYSPACE_PATTERN.matcher(key).matches()) {
                continue;
            }
            String[] parts = info.getProperty(key, "").split(",");
            Map<String, String> values = new java.util.LinkedHashMap<>();
            for (String part : parts) {
                String[] pair = part.split("=", 2);
                if (pair.length == 2) {
                    values.put(pair[0].trim(), pair[1].trim());
                }
            }

            SystemMonitorVO.KeyspaceVO keyspaceVO = new SystemMonitorVO.KeyspaceVO();
            keyspaceVO.setDatabase(key);
            keyspaceVO.setKeys(parseLong(values.get("keys"), 0L));
            keyspaceVO.setExpires(parseLong(values.get("expires"), 0L));
            keyspaceVO.setAvgTtl(parseLong(values.get("avg_ttl"), 0L));
            keyspaces.add(keyspaceVO);
        }
        keyspaces.sort(Comparator.comparing(SystemMonitorVO.KeyspaceVO::getDatabase));
        return keyspaces;
    }

    static Double calculateMemoryUsagePercent(Properties info) {
        long usedMemory = longValue(info, "used_memory");
        long maxMemory = longValue(info, "maxmemory");
        if (maxMemory <= 0) {
            return null;
        }
        return round((usedMemory * 100D) / maxMemory, 2);
    }

    static double calculateHitRate(long hits, long misses) {
        long total = hits + misses;
        if (total <= 0) {
            return 0D;
        }
        return round((hits * 100D) / total, 2);
    }

    static String stringValue(Properties info, String key) {
        return info.getProperty(key);
    }

    static int intValue(Properties info, String key) {
        return Math.toIntExact(longValue(info, key));
    }

    static long longValue(Properties info, String key) {
        return parseLong(info.getProperty(key), 0L);
    }

    static long parseLong(String value, long defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    static double parseDouble(String value, double defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    static Double normalizePercent(double value) {
        if (value < 0) {
            return null;
        }
        return round(value * 100D, 2);
    }

    static double clampPercent(double value) {
        return round(Math.max(0D, Math.min(100D, value)), 2);
    }

    static double round(double value, int scale) {
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }

    static SystemMonitorVO.ClientVO toClientVO(RedisClientInfo clientInfo) {
        SystemMonitorVO.ClientVO clientVO = new SystemMonitorVO.ClientVO();
        clientVO.setAddressPort(clientInfo.getAddressPort());
        clientVO.setName(clientInfo.getName());
        clientVO.setAge(clientInfo.getAge());
        clientVO.setIdle(clientInfo.getIdle());
        clientVO.setFlags(clientInfo.getFlags());
        clientVO.setDatabaseId(clientInfo.getDatabaseId());
        clientVO.setLastCommand(clientInfo.getLastCommand());
        return clientVO;
    }

    private Integer resolvePid() {
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        if (runtimeName == null || runtimeName.isBlank()) {
            return null;
        }
        String pidPart = runtimeName.split("@", 2)[0];
        try {
            return Integer.parseInt(pidPart);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String resolveHostName() {
        String hostName = System.getenv("HOSTNAME");
        if (hostName != null && !hostName.isBlank()) {
            return hostName;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private String resolveHostAddress() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            if (networkInterfaces != null) {
                while (networkInterfaces.hasMoreElements()) {
                    NetworkInterface networkInterface = networkInterfaces.nextElement();
                    if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                        continue;
                    }
                    Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress address = addresses.nextElement();
                        if (!address.isLoopbackAddress() && address instanceof Inet4Address) {
                            return address.getHostAddress();
                        }
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (SocketException | java.net.UnknownHostException ignored) {
            return "unknown";
        }
    }

    record ServiceEndpoint(String serviceName, String defaultBaseUrl) {
    }

    private record HostMemory(Long totalBytes, Long usedBytes, Long freeBytes, Double usagePercent) {
        private static HostMemory empty() {
            return new HostMemory(null, null, null, null);
        }
    }
}

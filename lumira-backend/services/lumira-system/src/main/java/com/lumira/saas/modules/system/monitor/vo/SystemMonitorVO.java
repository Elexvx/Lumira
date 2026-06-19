package com.lumira.saas.modules.system.monitor.vo;

import java.time.LocalDateTime;
import java.util.List;

public final class SystemMonitorVO {

    private SystemMonitorVO() {
    }

    public static class ServiceMonitorVO {
        private CpuVO cpu;
        private MemoryVO memory;
        private ServerVO server;
        private JvmVO jvm;
        private List<ServiceInstanceVO> services;
        private List<ApiDocVO> apiDocs;

        public CpuVO getCpu() {
            return cpu;
        }

        public void setCpu(CpuVO cpu) {
            this.cpu = cpu;
        }

        public MemoryVO getMemory() {
            return memory;
        }

        public void setMemory(MemoryVO memory) {
            this.memory = memory;
        }

        public ServerVO getServer() {
            return server;
        }

        public void setServer(ServerVO server) {
            this.server = server;
        }

        public JvmVO getJvm() {
            return jvm;
        }

        public void setJvm(JvmVO jvm) {
            this.jvm = jvm;
        }

        public List<ServiceInstanceVO> getServices() {
            return services;
        }

        public void setServices(List<ServiceInstanceVO> services) {
            this.services = services;
        }

        public List<ApiDocVO> getApiDocs() {
            return apiDocs;
        }

        public void setApiDocs(List<ApiDocVO> apiDocs) {
            this.apiDocs = apiDocs;
        }
    }

    public static class ServiceInstanceVO {
        private String serviceName;
        private String baseUrl;
        private String healthUrl;
        private String status;
        private Long responseTimeMs;
        private String version;
        private LocalDateTime checkedAt;
        private String errorMessage;

        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getHealthUrl() { return healthUrl; }
        public void setHealthUrl(String healthUrl) { this.healthUrl = healthUrl; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Long getResponseTimeMs() { return responseTimeMs; }
        public void setResponseTimeMs(Long responseTimeMs) { this.responseTimeMs = responseTimeMs; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public LocalDateTime getCheckedAt() { return checkedAt; }
        public void setCheckedAt(LocalDateTime checkedAt) { this.checkedAt = checkedAt; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }

    public static class ApiDocVO {
        private String serviceName;
        private String url;
        private String status;

        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class CpuVO {
        private Integer coreCount;
        private Double processUsagePercent;
        private Double systemUsagePercent;
        private Double idlePercent;
        private Double loadAverage;

        public Integer getCoreCount() {
            return coreCount;
        }

        public void setCoreCount(Integer coreCount) {
            this.coreCount = coreCount;
        }

        public Double getProcessUsagePercent() {
            return processUsagePercent;
        }

        public void setProcessUsagePercent(Double processUsagePercent) {
            this.processUsagePercent = processUsagePercent;
        }

        public Double getSystemUsagePercent() {
            return systemUsagePercent;
        }

        public void setSystemUsagePercent(Double systemUsagePercent) {
            this.systemUsagePercent = systemUsagePercent;
        }

        public Double getIdlePercent() {
            return idlePercent;
        }

        public void setIdlePercent(Double idlePercent) {
            this.idlePercent = idlePercent;
        }

        public Double getLoadAverage() {
            return loadAverage;
        }

        public void setLoadAverage(Double loadAverage) {
            this.loadAverage = loadAverage;
        }
    }

    public static class MemoryVO {
        private Long totalBytes;
        private Long usedBytes;
        private Long freeBytes;
        private Double usagePercent;
        private Long heapMaxBytes;
        private Long heapUsedBytes;
        private Long heapCommittedBytes;
        private Long nonHeapUsedBytes;
        private Long hostTotalBytes;
        private Long hostUsedBytes;
        private Long hostFreeBytes;
        private Double hostUsagePercent;

        public Long getTotalBytes() {
            return totalBytes;
        }

        public void setTotalBytes(Long totalBytes) {
            this.totalBytes = totalBytes;
        }

        public Long getUsedBytes() {
            return usedBytes;
        }

        public void setUsedBytes(Long usedBytes) {
            this.usedBytes = usedBytes;
        }

        public Long getFreeBytes() {
            return freeBytes;
        }

        public void setFreeBytes(Long freeBytes) {
            this.freeBytes = freeBytes;
        }

        public Double getUsagePercent() {
            return usagePercent;
        }

        public void setUsagePercent(Double usagePercent) {
            this.usagePercent = usagePercent;
        }

        public Long getHeapMaxBytes() {
            return heapMaxBytes;
        }

        public void setHeapMaxBytes(Long heapMaxBytes) {
            this.heapMaxBytes = heapMaxBytes;
        }

        public Long getHeapUsedBytes() {
            return heapUsedBytes;
        }

        public void setHeapUsedBytes(Long heapUsedBytes) {
            this.heapUsedBytes = heapUsedBytes;
        }

        public Long getHeapCommittedBytes() {
            return heapCommittedBytes;
        }

        public void setHeapCommittedBytes(Long heapCommittedBytes) {
            this.heapCommittedBytes = heapCommittedBytes;
        }

        public Long getNonHeapUsedBytes() {
            return nonHeapUsedBytes;
        }

        public void setNonHeapUsedBytes(Long nonHeapUsedBytes) {
            this.nonHeapUsedBytes = nonHeapUsedBytes;
        }

        public Long getHostTotalBytes() {
            return hostTotalBytes;
        }

        public void setHostTotalBytes(Long hostTotalBytes) {
            this.hostTotalBytes = hostTotalBytes;
        }

        public Long getHostUsedBytes() {
            return hostUsedBytes;
        }

        public void setHostUsedBytes(Long hostUsedBytes) {
            this.hostUsedBytes = hostUsedBytes;
        }

        public Long getHostFreeBytes() {
            return hostFreeBytes;
        }

        public void setHostFreeBytes(Long hostFreeBytes) {
            this.hostFreeBytes = hostFreeBytes;
        }

        public Double getHostUsagePercent() {
            return hostUsagePercent;
        }

        public void setHostUsagePercent(Double hostUsagePercent) {
            this.hostUsagePercent = hostUsagePercent;
        }
    }

    public static class ServerVO {
        private String serverName;
        private String serverIp;
        private String osName;
        private String osArch;
        private String osVersion;
        private String projectPath;
        private String installPath;
        private String userHome;
        private String tempDir;

        public String getServerName() {
            return serverName;
        }

        public void setServerName(String serverName) {
            this.serverName = serverName;
        }

        public String getServerIp() {
            return serverIp;
        }

        public void setServerIp(String serverIp) {
            this.serverIp = serverIp;
        }

        public String getOsName() {
            return osName;
        }

        public void setOsName(String osName) {
            this.osName = osName;
        }

        public String getOsArch() {
            return osArch;
        }

        public void setOsArch(String osArch) {
            this.osArch = osArch;
        }

        public String getOsVersion() {
            return osVersion;
        }

        public void setOsVersion(String osVersion) {
            this.osVersion = osVersion;
        }

        public String getProjectPath() {
            return projectPath;
        }

        public void setProjectPath(String projectPath) {
            this.projectPath = projectPath;
        }

        public String getInstallPath() {
            return installPath;
        }

        public void setInstallPath(String installPath) {
            this.installPath = installPath;
        }

        public String getUserHome() {
            return userHome;
        }

        public void setUserHome(String userHome) {
            this.userHome = userHome;
        }

        public String getTempDir() {
            return tempDir;
        }

        public void setTempDir(String tempDir) {
            this.tempDir = tempDir;
        }
    }

    public static class JvmVO {
        private String vmName;
        private String vmVersion;
        private String vmVendor;
        private String javaVersion;
        private String javaHome;
        private Integer pid;
        private LocalDateTime startTime;
        private Long uptimeSeconds;
        private Integer threadCount;
        private Integer daemonThreadCount;
        private Integer peakThreadCount;
        private List<String> inputArguments;

        public String getVmName() {
            return vmName;
        }

        public void setVmName(String vmName) {
            this.vmName = vmName;
        }

        public String getVmVersion() {
            return vmVersion;
        }

        public void setVmVersion(String vmVersion) {
            this.vmVersion = vmVersion;
        }

        public String getVmVendor() {
            return vmVendor;
        }

        public void setVmVendor(String vmVendor) {
            this.vmVendor = vmVendor;
        }

        public String getJavaVersion() {
            return javaVersion;
        }

        public void setJavaVersion(String javaVersion) {
            this.javaVersion = javaVersion;
        }

        public String getJavaHome() {
            return javaHome;
        }

        public void setJavaHome(String javaHome) {
            this.javaHome = javaHome;
        }

        public Integer getPid() {
            return pid;
        }

        public void setPid(Integer pid) {
            this.pid = pid;
        }

        public LocalDateTime getStartTime() {
            return startTime;
        }

        public void setStartTime(LocalDateTime startTime) {
            this.startTime = startTime;
        }

        public Long getUptimeSeconds() {
            return uptimeSeconds;
        }

        public void setUptimeSeconds(Long uptimeSeconds) {
            this.uptimeSeconds = uptimeSeconds;
        }

        public Integer getThreadCount() {
            return threadCount;
        }

        public void setThreadCount(Integer threadCount) {
            this.threadCount = threadCount;
        }

        public Integer getDaemonThreadCount() {
            return daemonThreadCount;
        }

        public void setDaemonThreadCount(Integer daemonThreadCount) {
            this.daemonThreadCount = daemonThreadCount;
        }

        public Integer getPeakThreadCount() {
            return peakThreadCount;
        }

        public void setPeakThreadCount(Integer peakThreadCount) {
            this.peakThreadCount = peakThreadCount;
        }

        public List<String> getInputArguments() {
            return inputArguments;
        }

        public void setInputArguments(List<String> inputArguments) {
            this.inputArguments = inputArguments;
        }
    }

    public static class RedisMonitorVO {
        private OverviewVO overview;
        private List<CommandStatVO> commandStats;
        private List<KeyspaceVO> keyspaces;
        private List<ClientVO> clients;
        private LocalDateTime sampleTime;

        public OverviewVO getOverview() {
            return overview;
        }

        public void setOverview(OverviewVO overview) {
            this.overview = overview;
        }

        public List<CommandStatVO> getCommandStats() {
            return commandStats;
        }

        public void setCommandStats(List<CommandStatVO> commandStats) {
            this.commandStats = commandStats;
        }

        public List<KeyspaceVO> getKeyspaces() {
            return keyspaces;
        }

        public void setKeyspaces(List<KeyspaceVO> keyspaces) {
            this.keyspaces = keyspaces;
        }

        public List<ClientVO> getClients() {
            return clients;
        }

        public void setClients(List<ClientVO> clients) {
            this.clients = clients;
        }

        public LocalDateTime getSampleTime() {
            return sampleTime;
        }

        public void setSampleTime(LocalDateTime sampleTime) {
            this.sampleTime = sampleTime;
        }
    }

    public static class OverviewVO {
        private String version;
        private String mode;
        private Integer port;
        private Integer connectedClients;
        private Long uptimeSeconds;
        private Long uptimeDays;
        private Long keyCount;
        private Long totalConnectionsReceived;
        private Long totalCommandsProcessed;
        private Long instantaneousOpsPerSec;
        private Long memoryUsedBytes;
        private Long memoryPeakBytes;
        private Long memoryMaxBytes;
        private Double memoryUsagePercent;
        private Long hits;
        private Long misses;
        private Double hitRate;

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public Integer getPort() {
            return port;
        }

        public void setPort(Integer port) {
            this.port = port;
        }

        public Integer getConnectedClients() {
            return connectedClients;
        }

        public void setConnectedClients(Integer connectedClients) {
            this.connectedClients = connectedClients;
        }

        public Long getUptimeSeconds() {
            return uptimeSeconds;
        }

        public void setUptimeSeconds(Long uptimeSeconds) {
            this.uptimeSeconds = uptimeSeconds;
        }

        public Long getUptimeDays() {
            return uptimeDays;
        }

        public void setUptimeDays(Long uptimeDays) {
            this.uptimeDays = uptimeDays;
        }

        public Long getKeyCount() {
            return keyCount;
        }

        public void setKeyCount(Long keyCount) {
            this.keyCount = keyCount;
        }

        public Long getTotalConnectionsReceived() {
            return totalConnectionsReceived;
        }

        public void setTotalConnectionsReceived(Long totalConnectionsReceived) {
            this.totalConnectionsReceived = totalConnectionsReceived;
        }

        public Long getTotalCommandsProcessed() {
            return totalCommandsProcessed;
        }

        public void setTotalCommandsProcessed(Long totalCommandsProcessed) {
            this.totalCommandsProcessed = totalCommandsProcessed;
        }

        public Long getInstantaneousOpsPerSec() {
            return instantaneousOpsPerSec;
        }

        public void setInstantaneousOpsPerSec(Long instantaneousOpsPerSec) {
            this.instantaneousOpsPerSec = instantaneousOpsPerSec;
        }

        public Long getMemoryUsedBytes() {
            return memoryUsedBytes;
        }

        public void setMemoryUsedBytes(Long memoryUsedBytes) {
            this.memoryUsedBytes = memoryUsedBytes;
        }

        public Long getMemoryPeakBytes() {
            return memoryPeakBytes;
        }

        public void setMemoryPeakBytes(Long memoryPeakBytes) {
            this.memoryPeakBytes = memoryPeakBytes;
        }

        public Long getMemoryMaxBytes() {
            return memoryMaxBytes;
        }

        public void setMemoryMaxBytes(Long memoryMaxBytes) {
            this.memoryMaxBytes = memoryMaxBytes;
        }

        public Double getMemoryUsagePercent() {
            return memoryUsagePercent;
        }

        public void setMemoryUsagePercent(Double memoryUsagePercent) {
            this.memoryUsagePercent = memoryUsagePercent;
        }

        public Long getHits() {
            return hits;
        }

        public void setHits(Long hits) {
            this.hits = hits;
        }

        public Long getMisses() {
            return misses;
        }

        public void setMisses(Long misses) {
            this.misses = misses;
        }

        public Double getHitRate() {
            return hitRate;
        }

        public void setHitRate(Double hitRate) {
            this.hitRate = hitRate;
        }
    }

    public static class CommandStatVO {
        private String command;
        private Long calls;
        private Long totalUsec;
        private Double avgUsec;
        private Long rejectedCalls;
        private Long failedCalls;

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public Long getCalls() {
            return calls;
        }

        public void setCalls(Long calls) {
            this.calls = calls;
        }

        public Long getTotalUsec() {
            return totalUsec;
        }

        public void setTotalUsec(Long totalUsec) {
            this.totalUsec = totalUsec;
        }

        public Double getAvgUsec() {
            return avgUsec;
        }

        public void setAvgUsec(Double avgUsec) {
            this.avgUsec = avgUsec;
        }

        public Long getRejectedCalls() {
            return rejectedCalls;
        }

        public void setRejectedCalls(Long rejectedCalls) {
            this.rejectedCalls = rejectedCalls;
        }

        public Long getFailedCalls() {
            return failedCalls;
        }

        public void setFailedCalls(Long failedCalls) {
            this.failedCalls = failedCalls;
        }
    }

    public static class KeyspaceVO {
        private String database;
        private Long keys;
        private Long expires;
        private Long avgTtl;

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }

        public Long getKeys() {
            return keys;
        }

        public void setKeys(Long keys) {
            this.keys = keys;
        }

        public Long getExpires() {
            return expires;
        }

        public void setExpires(Long expires) {
            this.expires = expires;
        }

        public Long getAvgTtl() {
            return avgTtl;
        }

        public void setAvgTtl(Long avgTtl) {
            this.avgTtl = avgTtl;
        }
    }

    public static class ClientVO {
        private String addressPort;
        private String name;
        private Long age;
        private Long idle;
        private String flags;
        private Long databaseId;
        private String lastCommand;

        public String getAddressPort() {
            return addressPort;
        }

        public void setAddressPort(String addressPort) {
            this.addressPort = addressPort;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Long getAge() {
            return age;
        }

        public void setAge(Long age) {
            this.age = age;
        }

        public Long getIdle() {
            return idle;
        }

        public void setIdle(Long idle) {
            this.idle = idle;
        }

        public String getFlags() {
            return flags;
        }

        public void setFlags(String flags) {
            this.flags = flags;
        }

        public Long getDatabaseId() {
            return databaseId;
        }

        public void setDatabaseId(Long databaseId) {
            this.databaseId = databaseId;
        }

        public String getLastCommand() {
            return lastCommand;
        }

        public void setLastCommand(String lastCommand) {
            this.lastCommand = lastCommand;
        }
    }
}

package com.lumira.saas.modules.system.update.app;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lumira.saas.modules.system.update.entity.PlatformUpdateTaskEntity;
import com.lumira.saas.modules.system.update.mapper.PlatformUpdateTaskMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Exposes the short-lived maintenance window owned by the blue-green updater.
 *
 * <p>The administrator's branding maintenance switch remains independent. The
 * update window starts with the persisted task, before the updater is called,
 * and is renewed by task reconciliation. This ordering makes task creation the
 * maintenance barrier while the heartbeat lease prevents a stopped backend or
 * updater from leaving the public site in maintenance forever.</p>
 */
@Service
public class PlatformUpdateMaintenanceService {

    static final String ACTIVE_TASK_KEY = "GLOBAL";
    static final Set<String> ACTIVE_STATUSES = Set.of("PENDING", "RUNNING");
    static final Duration DEFAULT_LEASE_TTL = Duration.ofSeconds(30);
    private static final Duration MIN_LEASE_TTL = Duration.ofSeconds(5);
    private static final Duration MAX_LEASE_TTL = Duration.ofMinutes(5);
    private static final Logger log = LoggerFactory.getLogger(PlatformUpdateMaintenanceService.class);

    private final PlatformUpdateTaskMapper taskMapper;
    private final Clock clock;
    private final Duration leaseTtl;

    @Autowired
    public PlatformUpdateMaintenanceService(PlatformUpdateTaskMapper taskMapper, Environment environment) {
        this(taskMapper, Clock.systemDefaultZone(), resolveLeaseTtl(environment));
    }

    PlatformUpdateMaintenanceService(PlatformUpdateTaskMapper taskMapper, Clock clock, Duration leaseTtl) {
        this.taskMapper = taskMapper;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
        this.leaseTtl = normalizeLeaseTtl(leaseTtl);
    }

    public boolean isAutomaticMaintenanceActive() {
        return !"NORMAL".equals(currentMode().mode());
    }

    public MaintenanceState currentMode() {
        try {
            LocalDateTime heartbeatCutoff = LocalDateTime.now(clock).minus(leaseTtl);
            PlatformUpdateTaskEntity activeTask = taskMapper.selectOne(new QueryWrapper<PlatformUpdateTaskEntity>()
                    .orderByDesc("created_at")
                    .last("LIMIT 1"));
            if (activeTask == null) {
                return MaintenanceState.normal();
            }
            String mode = normalizeMode(activeTask.getMaintenanceMode());
            if (("READ_ONLY".equals(mode) || "FULL_MAINTENANCE".equals(mode))
                    && (ACTIVE_STATUSES.contains(activeTask.getStatus()) || "FAILED".equals(activeTask.getStatus()))) {
                return new MaintenanceState(activeTask.getId(), mode, activeTask.getMaintenanceReason(), activeTask.getPhase(), true);
            }
            if (!ACTIVE_TASK_KEY.equals(activeTask.getActiveKey()) || !ACTIVE_STATUSES.contains(activeTask.getStatus())) {
                return MaintenanceState.normal();
            }
            boolean leaseFresh = activeTask.getUpdatedAt() != null && !activeTask.getUpdatedAt().isBefore(heartbeatCutoff);
            if (!leaseFresh) {
                return MaintenanceState.normal();
            }
            if (activeTask.getMaintenanceMode() == null) {
                return new MaintenanceState(activeTask.getId(), "FULL_MAINTENANCE", "Legacy update task is active", activeTask.getPhase(), false);
            }
            return new MaintenanceState(activeTask.getId(), mode, activeTask.getMaintenanceReason(), activeTask.getPhase(), false);
        } catch (RuntimeException exception) {
            // Operational maintenance is fail-open. The explicit administrator
            // switch is loaded separately and remains authoritative.
            log.debug("Unable to resolve automatic platform update maintenance state", exception);
            return MaintenanceState.normal();
        }
    }

    private String normalizeMode(String mode) {
        return mode != null && Set.of("NORMAL", "WRITE_DRAIN", "READ_ONLY", "FULL_MAINTENANCE").contains(mode) ? mode : "NORMAL";
    }

    public record MaintenanceState(Long taskId, String mode, String reason, String phase, boolean requiresReconciliation) {
        static MaintenanceState normal() { return new MaintenanceState(null, "NORMAL", null, null, false); }
    }

    Duration leaseTtl() {
        return leaseTtl;
    }

    private static Duration resolveLeaseTtl(Environment environment) {
        Long configured = environment == null
                ? null
                : environment.getProperty("platform.update.maintenance-lease-ttl-ms", Long.class);
        return configured == null ? DEFAULT_LEASE_TTL : Duration.ofMillis(configured);
    }

    private static Duration normalizeLeaseTtl(Duration configured) {
        Duration candidate = configured == null || configured.isNegative() || configured.isZero()
                ? DEFAULT_LEASE_TTL
                : configured;
        if (candidate.compareTo(MIN_LEASE_TTL) < 0) {
            return MIN_LEASE_TTL;
        }
        if (candidate.compareTo(MAX_LEASE_TTL) > 0) {
            return MAX_LEASE_TTL;
        }
        return candidate;
    }
}

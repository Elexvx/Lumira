package com.lumira.saas.modules.system.update.app;

import com.lumira.saas.modules.system.update.entity.PlatformUpdateTaskEntity;
import com.lumira.saas.modules.system.update.mapper.PlatformUpdateTaskMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformUpdateMaintenanceServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-08T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @ParameterizedTest
    @MethodSource("updatePhases")
    void freshActiveTaskEnablesAutomaticMaintenanceBeforeAndDuringCutover(String phase) {
        PlatformUpdateTaskMapper taskMapper = mock(PlatformUpdateTaskMapper.class);
        when(taskMapper.selectOne(any())).thenReturn(task("RUNNING", phase, LocalDateTime.now(CLOCK).minusSeconds(29)));
        PlatformUpdateMaintenanceService service = new PlatformUpdateMaintenanceService(
                taskMapper,
                CLOCK,
                Duration.ofSeconds(30)
        );

        assertThat(service.isAutomaticMaintenanceActive()).isTrue();
    }

    @Test
    void pendingTaskIsTheMaintenanceBarrierBeforeTheUpdaterIsCalled() {
        PlatformUpdateTaskMapper taskMapper = mock(PlatformUpdateTaskMapper.class);
        when(taskMapper.selectOne(any())).thenReturn(task("PENDING", "PREFLIGHT", LocalDateTime.now(CLOCK)));
        PlatformUpdateMaintenanceService service = new PlatformUpdateMaintenanceService(
                taskMapper,
                CLOCK,
                Duration.ofSeconds(30)
        );

        assertThat(service.isAutomaticMaintenanceActive()).isTrue();
        assertThat(service.currentMode().taskId()).isEqualTo(42L);
    }

    @Test
    void expiredHeartbeatFailsOpenEvenWhenTheLastKnownPhaseWasCutover() {
        PlatformUpdateTaskMapper taskMapper = mock(PlatformUpdateTaskMapper.class);
        when(taskMapper.selectOne(any())).thenReturn(task(
                "RUNNING",
                "VERIFYING_ACTIVE",
                LocalDateTime.now(CLOCK).minusSeconds(31)
        ));
        PlatformUpdateMaintenanceService service = new PlatformUpdateMaintenanceService(
                taskMapper,
                CLOCK,
                Duration.ofSeconds(30)
        );

        assertThat(service.isAutomaticMaintenanceActive()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("terminalStatuses")
    void everyTerminalStatusReleasesAutomaticMaintenance(String status) {
        PlatformUpdateTaskMapper taskMapper = mock(PlatformUpdateTaskMapper.class);
        when(taskMapper.selectOne(any())).thenReturn(task(status, "VERIFYING_ACTIVE", LocalDateTime.now(CLOCK)));
        PlatformUpdateMaintenanceService service = new PlatformUpdateMaintenanceService(
                taskMapper,
                CLOCK,
                Duration.ofSeconds(30)
        );

        assertThat(service.isAutomaticMaintenanceActive()).isFalse();
    }

    @Test
    void stateLookupFailureCannotHoldThePublicSiteInAutomaticMaintenance() {
        PlatformUpdateTaskMapper taskMapper = mock(PlatformUpdateTaskMapper.class);
        when(taskMapper.selectOne(any())).thenThrow(new IllegalStateException("database unavailable"));
        PlatformUpdateMaintenanceService service = new PlatformUpdateMaintenanceService(
                taskMapper,
                CLOCK,
                Duration.ofSeconds(30)
        );

        assertThat(service.isAutomaticMaintenanceActive()).isFalse();
    }

    @Test
    void taskWithoutTheGlobalLeaseCannotEnableAutomaticMaintenance() {
        PlatformUpdateTaskMapper taskMapper = mock(PlatformUpdateTaskMapper.class);
        PlatformUpdateTaskEntity task = task("RUNNING", "SWITCHING_TRAFFIC", LocalDateTime.now(CLOCK));
        task.setActiveKey(null);
        when(taskMapper.selectOne(any())).thenReturn(task);
        PlatformUpdateMaintenanceService service = new PlatformUpdateMaintenanceService(
                taskMapper,
                CLOCK,
                Duration.ofSeconds(30)
        );

        assertThat(service.isAutomaticMaintenanceActive()).isFalse();
    }

    @Test
    void criticalFailureRemainsReadOnlyAfterHeartbeatAndTaskCompletion() {
        PlatformUpdateTaskMapper taskMapper = mock(PlatformUpdateTaskMapper.class);
        PlatformUpdateTaskEntity task = task("FAILED", "MIGRATING", LocalDateTime.now(CLOCK).minusHours(1));
        task.setActiveKey(null);
        task.setMaintenanceMode("READ_ONLY");
        task.setMaintenanceReason("database migration outcome is unknown");
        when(taskMapper.selectOne(any())).thenReturn(task);
        PlatformUpdateMaintenanceService service = new PlatformUpdateMaintenanceService(taskMapper, CLOCK, Duration.ofSeconds(30));

        assertThat(service.currentMode().mode()).isEqualTo("READ_ONLY");
        assertThat(service.currentMode().requiresReconciliation()).isTrue();
    }

    @Test
    void leaseTtlIsBoundedToPreventZeroOrIndefiniteMaintenance() {
        PlatformUpdateTaskMapper taskMapper = mock(PlatformUpdateTaskMapper.class);

        assertThat(new PlatformUpdateMaintenanceService(taskMapper, CLOCK, Duration.ofMillis(1)).leaseTtl())
                .isEqualTo(Duration.ofSeconds(5));
        assertThat(new PlatformUpdateMaintenanceService(taskMapper, CLOCK, Duration.ofHours(1)).leaseTtl())
                .isEqualTo(Duration.ofMinutes(5));
    }

    private static PlatformUpdateTaskEntity task(String status, String phase, LocalDateTime updatedAt) {
        PlatformUpdateTaskEntity task = new PlatformUpdateTaskEntity();
        task.setId(42L);
        task.setActiveKey(PlatformUpdateMaintenanceService.ACTIVE_TASK_KEY);
        task.setStatus(status);
        task.setPhase(phase);
        task.setUpdatedAt(updatedAt);
        return task;
    }

    private static Stream<String> updatePhases() {
        return Stream.of(
                "PREFLIGHT",
                "BACKUP",
                "PULLING",
                "MIGRATING",
                "STARTING_INACTIVE",
                "VERIFYING_INACTIVE",
                "SWITCHING_TRAFFIC",
                "VERIFYING_ACTIVE",
                "DRAINING_OLD",
                "UPDATING_WORKERS",
                "FINALIZING"
        );
    }

    private static Stream<String> terminalStatuses() {
        return Stream.of("SUCCEEDED", "FAILED", "ROLLED_BACK", "CANCELLED");
    }
}

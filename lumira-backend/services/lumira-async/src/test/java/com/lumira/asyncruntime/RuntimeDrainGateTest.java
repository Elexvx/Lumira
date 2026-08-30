package com.lumira.asyncruntime;

import static org.assertj.core.api.Assertions.assertThat;

import com.lumira.common.runtime.RuntimeDrainGate;
import org.junit.jupiter.api.Test;

class RuntimeDrainGateTest {

    @Test
    void quiesceRejectsNewWorkWhileAllowingInflightLeaseToFinish() {
        RuntimeDrainGate gate = new RuntimeDrainGate();
        RuntimeDrainGate.Lease lease = gate.tryAcquire();
        assertThat(lease).isNotNull();

        gate.quiesce();
        assertThat(gate.tryAcquire()).isNull();
        assertThat(gate.snapshot().inflightTasks()).isEqualTo(1);
        assertThat(gate.snapshot().safeToStop()).isFalse();

        lease.close();
        assertThat(gate.snapshot().safeToStop()).isTrue();
        gate.resume();
        assertThat(gate.snapshot().acceptingNewWork()).isTrue();
    }
}

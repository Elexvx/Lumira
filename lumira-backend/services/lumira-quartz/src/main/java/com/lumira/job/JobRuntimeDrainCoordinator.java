package com.lumira.job;

import com.lumira.common.runtime.RuntimeDrainGate;
import org.springframework.stereotype.Component;

@Component
public class JobRuntimeDrainCoordinator {
    private final RuntimeDrainGate gate = new RuntimeDrainGate();

    public RuntimeDrainGate.Lease tryAcquire() {
        return gate.tryAcquire();
    }

    public void quiesce() {
        gate.quiesce();
    }

    public void resume() {
        gate.resume();
    }

    public RuntimeDrainGate.Snapshot snapshot() {
        return gate.snapshot();
    }
}

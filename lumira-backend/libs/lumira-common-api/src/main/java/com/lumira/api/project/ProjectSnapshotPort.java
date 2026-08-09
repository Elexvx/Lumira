package com.lumira.api.project;

/** Narrow, owner-backed lookup boundary for a persisted project snapshot. */
public interface ProjectSnapshotPort {

    ProjectSnapshot findProjectSnapshot(Long projectId);
}

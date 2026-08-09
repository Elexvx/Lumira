package com.lumira.saas.modules.project.integration;

import com.lumira.api.project.ProjectSnapshot;
import com.lumira.api.project.ProjectSnapshotPort;
import com.lumira.saas.modules.project.repository.ProjectRepository;
import com.lumira.saas.modules.project.vo.ProjectVO;

/** Project-owned implementation of the cross-context project snapshot boundary. */
public class ProjectSnapshotPortAdapter implements ProjectSnapshotPort {
    private final ProjectRepository projectRepository;

    public ProjectSnapshotPortAdapter(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Override
    public ProjectSnapshot findProjectSnapshot(Long projectId) {
        if (projectId == null || projectId <= 0) {
            return null;
        }
        return projectRepository.findById(projectId).map(this::toSnapshot).orElse(null);
    }

    private ProjectSnapshot toSnapshot(ProjectVO.Project project) {
        return new ProjectSnapshot(
                project.getId(), project.getCode(), project.getLocale(), project.getTitle(), project.getCategory(),
                project.getDescription(), project.getImageUrl(), project.getOwnerName(), project.getStatus(), project.getTags()
        );
    }
}

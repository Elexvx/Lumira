package com.lumira.saas.modules.project.repository;

import com.lumira.saas.modules.project.dto.ProjectDTO;
import com.lumira.saas.modules.project.vo.ProjectVO;
import java.util.List;
import java.util.Optional;

public interface ProjectRepository {
    List<String> findEnabledDictValues(String dictCode);
    PageData search(String keyword, String category, String ownerName, String rating, String status,
                    String locale, Boolean featured, long offset, long limit);
    Optional<ProjectVO.Project> findById(Long id);
    Long create(ProjectDTO.ProjectUpsertRequest project, Long userId, String userUuid);
    int update(Long id, ProjectVO.Project expected, ProjectDTO.ProjectUpsertRequest project, Long userId, String userUuid);
    int delete(Long id, ProjectVO.Project expected, Long userId, String userUuid);

    record PageData(List<ProjectVO.Project> records, long total) { }
}

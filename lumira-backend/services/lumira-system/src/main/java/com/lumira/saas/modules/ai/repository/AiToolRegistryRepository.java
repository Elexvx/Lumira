package com.lumira.saas.modules.ai.repository;

import com.lumira.saas.modules.ai.vo.AiVO;
import java.util.List;

public interface AiToolRegistryRepository {

    List<AiVO.SkillVO> findGrantedSkills(Long employeeId);
}

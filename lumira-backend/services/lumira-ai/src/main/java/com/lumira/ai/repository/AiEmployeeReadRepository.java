package com.lumira.ai.repository;

import com.lumira.ai.vo.AiEmployeeVO;
import java.util.List;
import java.util.Optional;

public interface AiEmployeeReadRepository {

    List<AiEmployeeVO> findPage(long limit, long offset);

    Optional<AiEmployeeVO> findFirstEnabled();

    boolean existsEnabled(Long employeeId);
}

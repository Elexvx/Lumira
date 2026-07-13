package com.lumira.ai.repository;

import com.lumira.ai.vo.AiToolVO;
import java.util.List;

public interface AiToolCatalogRepository {

    List<AiToolVO> findEnabledTools();
}

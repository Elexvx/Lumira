package com.lumira.saas.modules.system.dict.repository;

import java.util.List;
import com.lumira.saas.modules.system.vo.SystemVO;

public interface DictRuntimeRepository {
    List<SystemVO.DictItemVO> findEnabledItems(String dictCode);
}

package com.lumira.saas.modules.system.dict.repository;

import java.util.List;
import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.modules.system.vo.SystemVO;

public interface DictRuntimeRepository {
    List<SystemVO.DictItemVO> findEnabledItems(String dictCode);

    PageResponse<SystemVO.DictItemVO> searchEnabledItems(ItemSearch search);

    record ItemSearch(String dictCode, String keyword, String parentItemValue, boolean rootOnly,
                      List<String> values, long pageNo, long pageSize) {}
}

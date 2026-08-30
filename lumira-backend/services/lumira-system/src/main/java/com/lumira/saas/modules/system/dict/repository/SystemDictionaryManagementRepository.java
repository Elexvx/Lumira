package com.lumira.saas.modules.system.dict.repository;

import com.lumira.saas.common.vo.PageResponse;
import com.lumira.saas.modules.system.vo.SystemVO;
import java.time.LocalDateTime;
import java.util.List;

/** Dedicated read/write boundary for dictionary administration. */
public interface SystemDictionaryManagementRepository {
    PageResponse<SystemVO.DictTypeVO> findTypes(TypeSearch search);

    SystemVO.DictTypeVO findActiveType(Long typeId);

    DictionaryWriteResult saveType(TypeWrite command);

    int softDeleteType(TypeVersion type, Actor actor, LocalDateTime updatedAt);

    void retireItemsForType(Long typeId, Actor actor, LocalDateTime updatedAt);

    List<SystemVO.DictItemVO> findActiveItems(Long typeId);

    PageResponse<SystemVO.DictItemVO> findItems(ItemSearch search);

    List<SystemVO.DictItemVO> findEnabledItemsByCode(String dictCode);

    List<SystemVO.DictTypeVO> findEnabledTypes();

    SystemVO.DictItemVO findActiveItem(Long typeId, Long itemId);

    DictionaryWriteResult saveItem(ItemWrite command);

    int softDeleteItem(ItemVersion item, Actor actor, LocalDateTime updatedAt);

    record Actor(Long userId, String userUuid) {}

    record TypeSearch(String dictCode, String dictName, String status, long pageNo, long pageSize) {}

    record ItemSearch(Long typeId, String keyword, String parentItemValue, long pageNo, long pageSize) {}

    record TypeVersion(Long id, String dictCode, Integer isSystem) {}

    record TypeWrite(TypeVersion existing, String dictCode, String dictName, String status, Integer isSystem, String remark, String structureType, Actor actor, LocalDateTime updatedAt) {}

    record ItemVersion(Long id, Long typeId, String itemValue, String status) {}

    record ItemWrite(ItemVersion existing, Long typeId, String itemLabel, String itemValue, Integer sortNo, String status, String remark,
                     String parentItemValue, Integer levelNo, Boolean leaf, Actor actor, LocalDateTime updatedAt) {}

    record DictionaryWriteResult(int writeCount, Long id) {}
}

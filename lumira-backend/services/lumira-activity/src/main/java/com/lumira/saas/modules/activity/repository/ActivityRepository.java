package com.lumira.saas.modules.activity.repository;

import com.lumira.saas.modules.activity.dto.ActivityDTO;
import com.lumira.saas.modules.activity.vo.ActivityVO;
import java.util.List;
import java.util.Optional;

public interface ActivityRepository {
    List<String> findEnabledDictValues(String dictCode);
    PageData search(String keyword, String status, String locale, Boolean featured, long offset, long limit);
    Optional<ActivityVO.Activity> findById(Long id);
    Long create(ActivityDTO.ActivityUpsertRequest activity, Long userId, String userUuid);
    int update(Long id, ActivityVO.Activity expected, ActivityDTO.ActivityUpsertRequest activity, Long userId, String userUuid);
    int delete(Long id, ActivityVO.Activity expected, Long userId, String userUuid);
    record PageData(List<ActivityVO.Activity> records, long total) { }
}

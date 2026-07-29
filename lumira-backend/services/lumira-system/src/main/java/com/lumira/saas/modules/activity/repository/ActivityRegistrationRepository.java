package com.lumira.saas.modules.activity.repository;

import com.lumira.saas.modules.activity.dto.ActivityRegistrationDTO;
import com.lumira.saas.modules.activity.vo.ActivityRegistrationVO;
import java.util.List;

public interface ActivityRegistrationRepository {
    ActivityRegistrationVO create(Long userId, String userUuid, String username, ActivityRegistrationDTO.CreateRequest request);
    List<ActivityRegistrationVO> listVisible(Long userId, String userUuid, boolean viewAll);
}

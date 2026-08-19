package com.lumira.saas.modules.activity.repository;

import com.lumira.saas.modules.activity.model.ActivityRegistrationAnswer;
import com.lumira.saas.modules.activity.model.ActivityRegistrationField;
import com.lumira.saas.modules.activity.vo.ActivityRegistrationVO;
import java.util.List;
import java.util.Optional;

public interface ActivityRegistrationRepository {
    Optional<RegistrationForm> findPublishedRegistrationForm(Long activityId);
    ActivityRegistrationVO create(Long userId, String userUuid, String username, RegistrationSubmission submission);
    List<ActivityRegistrationVO> listVisible(Long userId, String userUuid, boolean viewAll);

    record RegistrationForm(Long activityId, String activityTitle, List<ActivityRegistrationField> fields) { }

    record RegistrationSubmission(
            Long activityId,
            String name,
            String mobile,
            String email,
            String organization,
            String position,
            String remark,
            List<ActivityRegistrationAnswer> answers
    ) { }
}

package com.lumira.saas.modules.activity.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.activity.dto.ActivityRegistrationDTO;
import com.lumira.saas.modules.activity.repository.ActivityRegistrationRepository;
import com.lumira.saas.modules.activity.vo.ActivityRegistrationVO;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ActivityRegistrationAppService {
    private static final String VIEW_ALL = "aiadc:activity:view";
    private final ActivityRegistrationRepository repository;
    public ActivityRegistrationAppService(ActivityRegistrationRepository repository) { this.repository = repository; }

    public List<ActivityRegistrationVO> list(CurrentUser user) {
        requireUser(user);
        boolean viewAll = user.getPermissions() != null && user.getPermissions().contains(VIEW_ALL);
        return repository.listVisible(user.getUserId(), viewAll);
    }

    @Transactional
    public ActivityRegistrationVO create(CurrentUser user, ActivityRegistrationDTO.CreateRequest request) {
        requireUser(user);
        return repository.create(user.getUserId(), user.getUserUuid(), user.getUsername(), request);
    }

    private void requireUser(CurrentUser user) {
        if (user == null || user.getUserId() == null || user.getUserId() <= 0 || !StringUtils.hasText(user.getUserUuid())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
    }
}

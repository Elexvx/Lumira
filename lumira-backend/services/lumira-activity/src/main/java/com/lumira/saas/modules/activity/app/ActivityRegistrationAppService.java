package com.lumira.saas.modules.activity.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.TrustedCurrentUserResolver;
import com.lumira.common.security.data.DataPermissionDecision;
import com.lumira.common.security.data.DataPermissionResolver;
import com.lumira.common.security.data.DataPermissionRule;
import com.lumira.common.security.data.DataScopeType;
import com.lumira.saas.modules.activity.dto.ActivityRegistrationDTO;
import com.lumira.saas.modules.activity.repository.ActivityRegistrationRepository;
import com.lumira.saas.modules.activity.vo.ActivityRegistrationVO;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ActivityRegistrationAppService {
    private static final String DATA_SCOPE_RESOURCE = "activity:registration";
    private final ActivityRegistrationRepository repository;
    private final TrustedCurrentUserResolver trustedCurrentUserResolver;
    private final boolean enforceTrustedUserResolution;

    @org.springframework.beans.factory.annotation.Autowired
    public ActivityRegistrationAppService(
            ActivityRegistrationRepository repository,
            TrustedCurrentUserResolver trustedCurrentUserResolver
    ) {
        this(repository, trustedCurrentUserResolver, true);
    }

    public ActivityRegistrationAppService(
            ActivityRegistrationRepository repository,
            TrustedCurrentUserResolver trustedCurrentUserResolver,
            boolean enforceTrustedUserResolution
    ) {
        this.repository = repository;
        this.trustedCurrentUserResolver = trustedCurrentUserResolver;
        this.enforceTrustedUserResolution = enforceTrustedUserResolution;
    }

    public ActivityRegistrationAppService(ActivityRegistrationRepository repository) {
        this(repository, null, false);
    }

    public List<ActivityRegistrationVO> list(CurrentUser user) {
        CurrentUser trustedUser = requireUser(user);
        DataPermissionDecision decision = resolveDataPermission(trustedUser);
        boolean viewAll = decision.scopeType() == DataScopeType.ALL;
        return repository.listVisible(trustedUser.getUserId(), trustedUser.getUserUuid().trim(), viewAll);
    }

    @Transactional
    public ActivityRegistrationVO create(CurrentUser user, ActivityRegistrationDTO.CreateRequest request) {
        CurrentUser trustedUser = requireUser(user);
        return repository.create(trustedUser.getUserId(), trustedUser.getUserUuid(), trustedUser.getUsername(), request);
    }

    private CurrentUser requireUser(CurrentUser user) {
        if (user == null || user.getUserId() == null || user.getUserId() <= 0 || !StringUtils.hasText(user.getUserUuid())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(user)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        if (trustedCurrentUserResolver == null) {
            if (enforceTrustedUserResolution) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user resolver is unavailable");
            }
            return user;
        }
        CurrentUser trustedUser = trustedCurrentUserResolver.resolve(user);
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(trustedUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Login required");
        }
        return trustedUser;
    }

    private DataPermissionDecision resolveDataPermission(CurrentUser user) {
        return DataPermissionResolver.resolve(
                DATA_SCOPE_RESOURCE,
                user.getUserId(),
                user.getDeptIds() == null ? Set.of() : user.getDeptIds(),
                user.getDescendantDeptIds() == null ? Set.of() : user.getDescendantDeptIds(),
                user.getDataScopes() == null ? List.<DataPermissionRule>of() : user.getDataScopes(),
                user.getPermissions() == null ? Set.of() : user.getPermissions()
        );
    }
}

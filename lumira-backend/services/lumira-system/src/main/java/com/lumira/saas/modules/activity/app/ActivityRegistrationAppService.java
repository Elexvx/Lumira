package com.lumira.saas.modules.activity.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.data.DataPermissionDecision;
import com.lumira.common.security.data.DataPermissionResolver;
import com.lumira.common.security.data.DataPermissionRule;
import com.lumira.common.security.data.DataScopeType;
import com.lumira.saas.modules.activity.dto.ActivityRegistrationDTO;
import com.lumira.saas.modules.activity.repository.ActivityRegistrationRepository;
import com.lumira.saas.modules.activity.vo.ActivityRegistrationVO;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ActivityRegistrationAppService {
    private static final String DATA_SCOPE_RESOURCE = "activity:registration";
    private final ActivityRegistrationRepository repository;
    private final PermissionSnapshotService permissionSnapshotService;

    public ActivityRegistrationAppService(
            ActivityRegistrationRepository repository,
            PermissionSnapshotService permissionSnapshotService
    ) {
        this.repository = repository;
        this.permissionSnapshotService = permissionSnapshotService;
    }

    public List<ActivityRegistrationVO> list(CurrentUser user) {
        requireUser(user);
        DataPermissionDecision decision = resolveDataPermission(user);
        boolean viewAll = decision.scopeType() == DataScopeType.ALL;
        return repository.listVisible(user.getUserId(), user.getUserUuid().trim(), viewAll);
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

    private DataPermissionDecision resolveDataPermission(CurrentUser user) {
        String userUuid = user.getUserUuid().trim();
        if (!permissionSnapshotService.isTrustedActiveUser(user.getUserId(), userUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
        }
        Long simulatedRoleId = normalizeSimulatedRoleId(user.getSimulatedRoleId());
        PermissionSnapshotService.PermissionSnapshot snapshot = simulatedRoleId == null
                ? permissionSnapshotService.loadSnapshot(user.getUserId(), userUuid)
                : permissionSnapshotService.loadGrantedRoleSnapshot(user.getUserId(), userUuid, simulatedRoleId);
        if (snapshot == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user permission snapshot is unavailable");
        }
        return DataPermissionResolver.resolve(
                DATA_SCOPE_RESOURCE,
                user.getUserId(),
                snapshot.getDeptIds() == null ? Set.of() : snapshot.getDeptIds(),
                snapshot.getDescendantDeptIds() == null ? Set.of() : snapshot.getDescendantDeptIds(),
                snapshot.getDataScopes() == null ? List.<DataPermissionRule>of() : snapshot.getDataScopes(),
                snapshot.getPermissions() == null ? Set.of() : snapshot.getPermissions()
        );
    }

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
    }
}

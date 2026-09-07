package com.lumira.api.system.port;

import com.lumira.api.system.CurrentUserRoleOptionDTO;
import com.lumira.api.system.SystemRoleSnapshotDTO;
import com.lumira.api.system.SystemUserEmailRecipientDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.api.system.SystemUserWechatRecipientDTO;
import com.lumira.api.system.WechatLoginUserRequestDTO;
import java.util.List;

public interface UserIdentityQueryPort {
    SystemUserSnapshotDTO findUserIdentityById(Long id);
    SystemUserSnapshotDTO findUserProfileById(Long id);
    Boolean userHasEmail(Long userId, String userUuid);
    Boolean requiresInitialPasswordChange(Long userId, String userUuid);
    SystemUserSnapshotDTO findUserById(Long id);
    String findTargetUserUuidById(Long id);
    List<SystemUserSnapshotDTO> userIdentitiesByIds(List<Long> userIds);
    List<CurrentUserRoleOptionDTO> userRoleOptions(Long userId, String userUuid);
    List<SystemRoleSnapshotDTO> roleNamesByIds(List<Long> roleIds);
    List<SystemUserEmailRecipientDTO> userEmailRecipientsByIds(List<Long> userIds);
    List<SystemUserWechatRecipientDTO> userWechatRecipientsByIds(List<Long> userIds);
    List<SystemUserEmailRecipientDTO> userEmailRecipientsByRole(Long roleId);
    List<SystemUserWechatRecipientDTO> userWechatRecipientsByRole(Long roleId);
    List<SystemUserEmailRecipientDTO> platformUserEmailRecipients();
    List<SystemUserWechatRecipientDTO> platformUserWechatRecipients();
    List<SystemUserSnapshotDTO> roleUserIdentities(Long roleId);
    SystemUserSnapshotDTO resolveWechatLoginUser(WechatLoginUserRequestDTO request);
}

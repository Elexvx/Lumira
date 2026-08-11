package com.lumira.saas.modules.system.profile.repository;

import com.lumira.saas.modules.auth.vo.CurrentUserVO;
import java.time.LocalDateTime;
import java.util.List;

/** Persistence boundary for an authenticated user's own profile and role display data. */
public interface SystemCurrentUserProfileRepository {
    int updateBasicProfile(BasicProfile command);

    int updateAvatar(Long userId, String userUuid, String avatarUrl, Actor actor, LocalDateTime updatedAt);

    /** Initializes the generated default only while the user has no avatar. */
    int initializeAvatarIfAbsent(Long userId, String userUuid, String avatarUrl, Actor actor, LocalDateTime updatedAt);

    boolean hasActiveWechatBinding(Long userId, String userUuid);

    int updateContact(Long userId, String userUuid, String contactType, String value, Actor actor, LocalDateTime updatedAt);

    void upsertLocale(LocaleProfile command);

    int updatePasswordHash(Long userId, String userUuid, String passwordHash, Actor actor, LocalDateTime updatedAt);

    void mergeExtraProfileJson(Long userId, String userUuid, String extraJson);

    String findExtraProfileJson(Long userId, String userUuid);

    String findLocale(Long userId, String userUuid);

    List<CurrentUserVO.RoleOptionVO> findAvailableRoles(Long userId, String userUuid);

    List<String> findRoleNames(Long userId, String userUuid);

    record Actor(Long userId, String userUuid) {}

    record BasicProfile(
            Long userId,
            String userUuid,
            String avatarUrl,
            String nickname,
            String realName,
            String mobile,
            String email,
            String birthMonth,
            String gender,
            String region,
            String availableTime,
            String idCardNumber,
            Actor actor,
            LocalDateTime updatedAt
    ) {}

    record LocaleProfile(
            Long userId,
            String userUuid,
            String nickname,
            String realName,
            String gender,
            String birthMonth,
            String region,
            String locale
    ) {}
}

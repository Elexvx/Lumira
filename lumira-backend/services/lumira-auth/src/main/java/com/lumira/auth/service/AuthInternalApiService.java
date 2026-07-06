package com.lumira.auth.service;

import com.lumira.api.auth.CurrentUserDTO;
import com.lumira.api.client.AuthInternalApi;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

@Service("authInternalApi")
@Primary
public class AuthInternalApiService implements AuthInternalApi {

    private static final int MAX_SESSION_ID_LENGTH = 128;
    private static final Pattern SAFE_SESSION_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._:@/-]{1,128}$");

    private final AuthAppService authAppService;

    public AuthInternalApiService(AuthAppService authAppService) {
        this.authAppService = authAppService;
    }

    @Override
    public CurrentUserDTO currentUser(
            String sessionId,
            Long expectedUserId,
            String expectedUserUuid,
            Integer expectedSessionVersion,
            String expectedPermissionsVersion,
            Long expectedSimulatedRoleId
    ) {
        return authAppService.currentUserBySessionId(
                requireSessionId(sessionId),
                normalizeExpectedUserId(expectedUserId),
                requireExpectedText(expectedUserUuid, "Expected user uuid is required"),
                normalizeExpectedSessionVersion(expectedSessionVersion),
                requireExpectedText(expectedPermissionsVersion, "Expected permissions version is required"),
                normalizeExpectedSimulatedRoleId(expectedSimulatedRoleId)
        );
    }

    private String requireSessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Session id is required");
        }
        String normalized = sessionId.trim();
        if (normalized.length() > MAX_SESSION_ID_LENGTH
                || !SAFE_SESSION_ID_PATTERN.matcher(normalized).matches()
                || normalized.contains("..")
                || normalized.contains("//")) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Session id is invalid");
        }
        return normalized;
    }

    private Long normalizeExpectedUserId(Long expectedUserId) {
        if (expectedUserId == null) {
            return null;
        }
        if (expectedUserId <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Expected user id is invalid");
        }
        return expectedUserId;
    }

    private Integer normalizeExpectedSessionVersion(Integer expectedSessionVersion) {
        if (expectedSessionVersion == null) {
            return null;
        }
        if (expectedSessionVersion <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Expected session version is invalid");
        }
        return expectedSessionVersion;
    }

    private Long normalizeExpectedSimulatedRoleId(Long expectedSimulatedRoleId) {
        if (expectedSimulatedRoleId == null) {
            return null;
        }
        if (expectedSimulatedRoleId <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Expected simulated role id is invalid");
        }
        return expectedSimulatedRoleId;
    }

    private String requireExpectedText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, message);
        }
        return value.trim();
    }
}

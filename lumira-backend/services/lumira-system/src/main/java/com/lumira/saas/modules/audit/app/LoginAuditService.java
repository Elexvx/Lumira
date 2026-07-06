package com.lumira.saas.modules.audit.app;

import com.lumira.common.web.TraceContext;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.modules.audit.entity.AuditLoginLogEntity;
import com.lumira.saas.modules.audit.mapper.AuditLoginLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class LoginAuditService {

    private final AuditLoginLogMapper auditLoginLogMapper;

    public LoginAuditService(AuditLoginLogMapper auditLoginLogMapper) {
        this.auditLoginLogMapper = auditLoginLogMapper;
    }

    public void log(
            Long userId,
            String userUuid,
            String username,
            String loginType,
            String loginResult,
            String failReason,
            String loginIp,
            String userAgent
    ) {
        AuditLoginLogEntity entity = new AuditLoginLogEntity();
        entity.setUserId(userId);
        entity.setUserUuid(requireAuditUserUuid(userId, userUuid));
        entity.setUsername(username);
        entity.setLoginType(loginType);
        entity.setLoginResult(loginResult);
        entity.setFailReason(failReason);
        entity.setLoginIp(loginIp);
        entity.setUserAgent(userAgent);
        entity.setRequestId(TraceContext.getRequestId());
        entity.setTraceId(TraceContext.getTraceId());
        entity.setCreatedAt(LocalDateTime.now());
        int inserted = auditLoginLogMapper.insert(entity);
        if (inserted != 1) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Login audit changed, please retry");
        }
    }

    private String requireAuditUserUuid(Long userId, String userUuid) {
        if (userId == null) {
            return StringUtils.hasText(userUuid) ? userUuid.trim() : null;
        }
        if (userId <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted login audit user is required");
        }
        if (!StringUtils.hasText(userUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted login audit user uuid is required");
        }
        return userUuid.trim();
    }
}

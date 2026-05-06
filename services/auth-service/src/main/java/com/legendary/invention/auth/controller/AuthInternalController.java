package com.legendary.invention.auth.controller;

import com.legendary.invention.api.auth.CurrentUserDTO;
import com.legendary.invention.auth.model.AuthSession;
import com.legendary.invention.auth.service.AuthAppService;
import com.legendary.invention.auth.service.AuthSessionStore;
import com.legendary.invention.common.enums.ErrorCode;
import com.legendary.invention.common.exception.BizException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/auth")
public class AuthInternalController {

    private final AuthSessionStore authSessionStore;
    private final AuthAppService authAppService;

    public AuthInternalController(AuthSessionStore authSessionStore, AuthAppService authAppService) {
        this.authSessionStore = authSessionStore;
        this.authAppService = authAppService;
    }

    @GetMapping("/sessions/{sessionId}/current-user")
    public CurrentUserDTO currentUser(@PathVariable String sessionId) {
        AuthSession session = authSessionStore.findBySessionId(sessionId).orElseThrow(() -> new BizException(ErrorCode.SESSION_EXPIRED, "会话已失效"));
        return authAppService.currentUserBySessionId(session.getSessionId());
    }

    @PostMapping("/sessions/{sessionId}/tenant")
    public Boolean switchSessionTenant(@PathVariable String sessionId, @RequestParam("tenantId") Long tenantId) {
        AuthSession session = authSessionStore.findBySessionId(sessionId).orElseThrow(() -> new BizException(ErrorCode.SESSION_EXPIRED, "会话已失效"));
        session.setCurrentTenantId(tenantId);
        authSessionStore.save(session, true);
        return Boolean.TRUE;
    }
}

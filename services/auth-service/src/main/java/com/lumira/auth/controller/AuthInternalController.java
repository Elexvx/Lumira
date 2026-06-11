package com.lumira.auth.controller;

import com.lumira.api.auth.CurrentUserDTO;
import com.lumira.auth.model.AuthSession;
import com.lumira.auth.service.AuthAppService;
import com.lumira.auth.service.AuthSessionStore;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/auth")
public class AuthInternalController implements com.lumira.api.client.AuthInternalApi {

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

}

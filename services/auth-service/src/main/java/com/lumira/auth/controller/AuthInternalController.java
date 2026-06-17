package com.lumira.auth.controller;

import com.lumira.api.auth.CurrentUserDTO;
import com.lumira.auth.service.AuthAppService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/auth")
public class AuthInternalController implements com.lumira.api.client.AuthInternalApi {

    private final AuthAppService authAppService;

    public AuthInternalController(AuthAppService authAppService) {
        this.authAppService = authAppService;
    }

    @GetMapping("/sessions/{sessionId}/current-user")
    public CurrentUserDTO currentUser(@PathVariable String sessionId) {
        return authAppService.currentUserBySessionId(sessionId);
    }

}

package com.yourcompany.saas.modules.auth.controller;

import com.yourcompany.saas.common.api.ApiResponse;
import com.yourcompany.saas.infrastructure.observability.TraceContext;
import com.yourcompany.saas.infrastructure.security.CurrentUser;
import com.yourcompany.saas.infrastructure.security.SecurityContextFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SecurityContextFacade securityContextFacade;

    @GetMapping("/current-user")
    public ApiResponse<CurrentUser> currentUser() {
        return ApiResponse.success(securityContextFacade.getCurrentUser(), TraceContext.getRequestId());
    }

    @GetMapping("/login")
    public ApiResponse<String> loginEndpointPlaceholder() {
        return ApiResponse.success("login endpoint placeholder", TraceContext.getRequestId());
    }
}

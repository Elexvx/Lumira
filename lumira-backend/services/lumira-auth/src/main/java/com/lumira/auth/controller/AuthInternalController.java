package com.lumira.auth.controller;

import com.lumira.api.auth.CurrentUserDTO;
import com.lumira.auth.service.AuthInternalApiService;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/auth")
@ConditionalOnProperty(name = "lumira.monolith", havingValue = "false", matchIfMissing = true)
public class AuthInternalController {

    private final AuthInternalApiService authInternalApiService;

    public AuthInternalController(AuthInternalApiService authInternalApiService) {
        this.authInternalApiService = authInternalApiService;
    }

    @ModelAttribute
    void requireInternalServicePrincipal() {
        if (!AuthenticationTrustSupport.isInternalServiceAuthentication(SecurityContextHolder.getContext().getAuthentication())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Internal service token is required");
        }
    }

    @GetMapping("/sessions/{sessionId}/current-user")
    public CurrentUserDTO currentUser(
            @PathVariable String sessionId,
            @RequestParam(name = "expectedUserId", required = false) Long expectedUserId,
            @RequestParam(name = "expectedUserUuid", required = false) String expectedUserUuid,
            @RequestParam(name = "expectedSessionVersion", required = false) Integer expectedSessionVersion,
            @RequestParam(name = "expectedPermissionsVersion", required = false) String expectedPermissionsVersion,
            @RequestParam(name = "expectedSimulatedRoleId", required = false) Long expectedSimulatedRoleId
    ) {
        requireInternalServicePrincipal();
        return authInternalApiService.currentUser(
                sessionId,
                expectedUserId,
                expectedUserUuid,
                expectedSessionVersion,
                expectedPermissionsVersion,
                expectedSimulatedRoleId
        );
    }
}

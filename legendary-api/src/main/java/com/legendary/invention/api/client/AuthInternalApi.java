package com.legendary.invention.api.client;

import com.legendary.invention.api.auth.CurrentUserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "auth-service", contextId = "authInternalApi", path = "/internal/auth")
public interface AuthInternalApi {

    @GetMapping("/sessions/{sessionId}/current-user")
    CurrentUserDTO currentUser(@PathVariable("sessionId") String sessionId);

    @PostMapping("/sessions/{sessionId}/tenant")
    Boolean switchSessionTenant(@PathVariable("sessionId") String sessionId, @RequestParam("tenantId") Long tenantId);
}

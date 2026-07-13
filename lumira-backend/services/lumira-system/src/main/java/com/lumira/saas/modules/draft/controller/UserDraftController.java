package com.lumira.saas.modules.draft.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.security.SecurityContextFacade;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.modules.draft.app.UserDraftAppService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/user-drafts")
public class UserDraftController {
    private final UserDraftAppService service;
    private final SecurityContextFacade securityContext;

    public UserDraftController(UserDraftAppService service, SecurityContextFacade securityContext) {
        this.service = service;
        this.securityContext = securityContext;
    }

    @GetMapping("/{draftKey}")
    public ApiResponse<UserDraftAppService.Draft> find(@PathVariable String draftKey) {
        return ApiResponse.success(service.find(securityContext.getCurrentUser(), draftKey).orElse(null), TraceContext.getRequestId());
    }

    @PutMapping("/{draftKey}")
    public ApiResponse<UserDraftAppService.Draft> save(@PathVariable String draftKey, @RequestBody Object payload) {
        return ApiResponse.success(service.save(securityContext.getCurrentUser(), draftKey, payload), TraceContext.getRequestId());
    }

    @DeleteMapping("/{draftKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String draftKey) {
        service.delete(securityContext.getCurrentUser(), draftKey);
    }
}

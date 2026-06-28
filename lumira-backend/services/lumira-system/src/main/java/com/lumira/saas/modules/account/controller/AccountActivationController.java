package com.lumira.saas.modules.account.controller;

import com.lumira.common.api.ApiResponse;
import com.lumira.common.web.TraceContext;
import com.lumira.saas.common.annotation.RepeatSubmit;
import com.lumira.saas.modules.account.app.AccountActivationService;
import com.lumira.saas.modules.account.dto.AccountActivationDTO;
import com.lumira.saas.modules.account.vo.AccountActivationVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/account-activation")
public class AccountActivationController {

    private final AccountActivationService accountActivationService;

    public AccountActivationController(AccountActivationService accountActivationService) {
        this.accountActivationService = accountActivationService;
    }

    @GetMapping("/verify")
    public ApiResponse<AccountActivationVO.TokenInfo> verify(@RequestParam("token") String token) {
        return ApiResponse.success(accountActivationService.verify(token), TraceContext.getRequestId());
    }

    @PostMapping("/complete")
    @RepeatSubmit
    public ApiResponse<Boolean> complete(@Valid @RequestBody AccountActivationDTO.CompleteRequest request) {
        return ApiResponse.success(accountActivationService.complete(request.getToken(), request.getPassword()), TraceContext.getRequestId());
    }
}

package com.legendary.invention.api.client;

import com.legendary.invention.api.system.CaptchaValidationRequestDTO;
import com.legendary.invention.api.system.LoginCapabilitiesDTO;
import com.legendary.invention.api.system.MenuNodeDTO;
import com.legendary.invention.api.system.PermissionSnapshotDTO;
import com.legendary.invention.api.system.SystemUserSnapshotDTO;
import com.legendary.invention.api.system.VerificationChallengeDTO;
import com.legendary.invention.api.system.VerificationProviderDTO;
import com.legendary.invention.api.system.VerificationVerificationDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "system-service", contextId = "systemInternalApi", path = "/internal/system")
public interface SystemInternalApi {

    @GetMapping("/users/login/{account}")
    SystemUserSnapshotDTO findLoginUser(@PathVariable("account") String account);

    @GetMapping("/users/{id}")
    SystemUserSnapshotDTO findUserById(@PathVariable("id") Long id);

    @GetMapping("/permissions/snapshot")
    PermissionSnapshotDTO permissionSnapshot(@RequestParam("tenantId") Long tenantId, @RequestParam("userId") Long userId);

    @PostMapping("/permissions/invalidate")
    Boolean invalidatePermissionSnapshot(@RequestParam("tenantId") Long tenantId);

    @PostMapping("/captcha/validate")
    Boolean validateCaptcha(@RequestBody CaptchaValidationRequestDTO request);

    @GetMapping("/verification/login-capabilities")
    LoginCapabilitiesDTO loginCapabilities(@RequestParam("tenantId") Long tenantId);

    @GetMapping("/verification/providers")
    List<VerificationProviderDTO> listVerificationProviders(@RequestParam("tenantId") Long tenantId, @RequestParam("userId") Long userId);

    @GetMapping("/verification/providers/{factorCode}")
    VerificationProviderDTO verificationProvider(
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("userId") Long userId,
            @PathVariable("factorCode") String factorCode
    );

    @PostMapping("/verification/providers/{factorCode}/bind")
    VerificationChallengeDTO bindVerificationProvider(
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("userId") Long userId,
            @PathVariable("factorCode") String factorCode
    );

    @PostMapping("/verification/providers/{factorCode}/unbind")
    Boolean unbindVerificationProvider(
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("userId") Long userId,
            @PathVariable("factorCode") String factorCode
    );

    @PostMapping("/verification/providers/{factorCode}/challenge")
    VerificationChallengeDTO verificationChallenge(
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("userId") Long userId,
            @PathVariable("factorCode") String factorCode
    );

    @PostMapping("/verification/providers/{factorCode}/verify")
    VerificationVerificationDTO verificationVerify(
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("userId") Long userId,
            @PathVariable("factorCode") String factorCode,
            @RequestParam("challengeId") String challengeId,
            @RequestParam("verificationCode") String verificationCode
    );

    @PostMapping("/verification/login-code/challenge")
    com.legendary.invention.api.auth.LoginCodeChallengeDTO loginCodeChallenge(
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("userId") Long userId,
            @RequestParam("account") String account,
            @RequestParam("loginType") String loginType
    );

    @PostMapping("/verification/login-code/complete")
    VerificationVerificationDTO completeLoginCodeLogin(@RequestBody com.legendary.invention.api.auth.LoginCodeCompleteRequest request);

    @PostMapping("/verification/second-factor/complete")
    VerificationVerificationDTO completeSecondFactorLogin(@RequestBody com.legendary.invention.api.auth.SecondFactorCompleteRequest request);

    @GetMapping("/menus/builtin")
    java.util.List<MenuNodeDTO> builtinMenus();
}

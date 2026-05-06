package com.legendary.invention.saas.modules.system.controller;

import com.legendary.invention.api.auth.LoginCodeChallengeDTO;
import com.legendary.invention.api.auth.LoginCodeCompleteRequest;
import com.legendary.invention.api.auth.SecondFactorCompleteRequest;
import com.legendary.invention.api.system.CaptchaValidationRequestDTO;
import com.legendary.invention.api.system.LoginCapabilitiesDTO;
import com.legendary.invention.api.system.MenuNodeDTO;
import com.legendary.invention.api.system.PermissionSnapshotDTO;
import com.legendary.invention.api.system.SystemUserSnapshotDTO;
import com.legendary.invention.api.system.VerificationChallengeDTO;
import com.legendary.invention.api.system.VerificationProviderDTO;
import com.legendary.invention.api.system.VerificationVerificationDTO;
import com.legendary.invention.saas.modules.system.app.SystemRouteCatalog;
import com.legendary.invention.saas.infrastructure.security.service.CaptchaService;
import com.legendary.invention.saas.modules.iam.service.PermissionSnapshotService;
import com.legendary.invention.saas.modules.system.verification.SystemVerificationAppService;
import com.legendary.invention.saas.modules.user.domain.UserDomainService;
import com.legendary.invention.saas.modules.user.entity.SysUserEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/system")
public class InternalSystemController {

    private final UserDomainService userDomainService;
    private final PermissionSnapshotService permissionSnapshotService;
    private final CaptchaService captchaService;
    private final SystemVerificationAppService verificationAppService;

    public InternalSystemController(
            UserDomainService userDomainService,
            PermissionSnapshotService permissionSnapshotService,
            CaptchaService captchaService,
            SystemVerificationAppService verificationAppService
    ) {
        this.userDomainService = userDomainService;
        this.permissionSnapshotService = permissionSnapshotService;
        this.captchaService = captchaService;
        this.verificationAppService = verificationAppService;
    }

    @GetMapping("/users/login/{account}")
    public SystemUserSnapshotDTO findLoginUser(@PathVariable("account") String account) {
        return userDomainService.findLoginUser(account).map(this::toSnapshot).orElse(null);
    }

    @GetMapping("/users/{id}")
    public SystemUserSnapshotDTO findUserById(@PathVariable("id") Long id) {
        return userDomainService.findById(id).map(this::toSnapshot).orElse(null);
    }

    @GetMapping("/permissions/snapshot")
    public PermissionSnapshotDTO permissionSnapshot(@RequestParam("tenantId") Long tenantId, @RequestParam("userId") Long userId) {
        PermissionSnapshotService.PermissionSnapshot snapshot = permissionSnapshotService.loadSnapshot(tenantId, userId);
        return new PermissionSnapshotDTO(snapshot.getVersion(), snapshot.getPermissionList());
    }

    @PostMapping("/permissions/invalidate")
    public Boolean invalidatePermissionSnapshot(@RequestParam("tenantId") Long tenantId) {
        permissionSnapshotService.invalidateTenant(tenantId);
        return Boolean.TRUE;
    }

    @PostMapping("/captcha/validate")
    public Boolean validateCaptcha(@Valid @RequestBody CaptchaValidationRequestDTO request) {
        captchaService.validateCaptcha(request.captchaId(), request.captchaCode(), request.captchaProof());
        return Boolean.TRUE;
    }

    @GetMapping("/verification/login-capabilities")
    public LoginCapabilitiesDTO loginCapabilities(@RequestParam("tenantId") Long tenantId) {
        var capabilities = verificationAppService.loadLoginCapabilities(tenantId);
        return new LoginCapabilitiesDTO(
                Boolean.TRUE.equals(capabilities.getPasswordLoginAvailable()),
                Boolean.TRUE.equals(capabilities.getSmsLoginAvailable()),
                Boolean.TRUE.equals(capabilities.getEmailLoginAvailable())
        );
    }

    @GetMapping("/verification/providers")
    public List<VerificationProviderDTO> listVerificationProviders(@RequestParam("tenantId") Long tenantId, @RequestParam("userId") Long userId) {
        return verificationAppService.listProviders(tenantId, userId).stream().map(this::toProvider).toList();
    }

    @GetMapping("/verification/providers/{factorCode}")
    public VerificationProviderDTO verificationProvider(
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("userId") Long userId,
            @PathVariable("factorCode") String factorCode
    ) {
        return toProvider(verificationAppService.provider(tenantId, userId, factorCode));
    }

    @PostMapping("/verification/providers/{factorCode}/bind")
    public VerificationChallengeDTO bindVerificationProvider(
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("userId") Long userId,
            @PathVariable("factorCode") String factorCode
    ) {
        return toChallenge(verificationAppService.bind(tenantId, userId, factorCode));
    }

    @PostMapping("/verification/providers/{factorCode}/unbind")
    public Boolean unbindVerificationProvider(
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("userId") Long userId,
            @PathVariable("factorCode") String factorCode
    ) {
        return verificationAppService.unbind(tenantId, userId, factorCode);
    }

    @PostMapping("/verification/providers/{factorCode}/challenge")
    public VerificationChallengeDTO verificationChallenge(
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("userId") Long userId,
            @PathVariable("factorCode") String factorCode
    ) {
        return toChallenge(verificationAppService.challenge(tenantId, userId, factorCode));
    }

    @PostMapping("/verification/providers/{factorCode}/verify")
    public VerificationVerificationDTO verificationVerify(
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("userId") Long userId,
            @PathVariable("factorCode") String factorCode,
            @RequestParam("challengeId") String challengeId,
            @RequestParam("verificationCode") String verificationCode
    ) {
        return toVerification(verificationAppService.completeBind(tenantId, userId, factorCode, challengeId, verificationCode), factorCode);
    }

    @PostMapping("/verification/login-code/challenge")
    public LoginCodeChallengeDTO loginCodeChallenge(
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("userId") Long userId,
            @RequestParam("account") String account,
            @RequestParam("loginType") String loginType
    ) {
        SysUserEntity user = userDomainService.findLoginUser(account)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (!user.getId().equals(userId)) {
            throw new IllegalArgumentException("账号与用户ID不匹配");
        }
        var challenge = verificationAppService.startLoginCodeChallenge(user, tenantId, loginType);
        LoginCodeChallengeDTO dto = new LoginCodeChallengeDTO();
        dto.setLoginType(challenge.getLoginType());
        dto.setFactorName(challenge.getFactorName());
        dto.setChallengeId(challenge.getChallengeId());
        dto.setMaskedContact(challenge.getMaskedContact());
        dto.setPromptMessage(challenge.getPromptMessage());
        dto.setExpiresInSeconds(challenge.getExpiresInSeconds());
        dto.setDebugCode(challenge.getDebugCode());
        return dto;
    }

    @PostMapping("/verification/login-code/complete")
    public VerificationVerificationDTO completeLoginCodeLogin(@Valid @RequestBody LoginCodeCompleteRequest request) {
        com.legendary.invention.saas.modules.auth.dto.LoginCodeCompleteRequest backendRequest = new com.legendary.invention.saas.modules.auth.dto.LoginCodeCompleteRequest();
        backendRequest.setChallengeId(request.challengeId());
        backendRequest.setVerificationCode(request.verificationCode());
        return toVerification(verificationAppService.completeLoginCodeLogin(backendRequest), null);
    }

    @PostMapping("/verification/second-factor/complete")
    public VerificationVerificationDTO completeSecondFactorLogin(@Valid @RequestBody SecondFactorCompleteRequest request) {
        com.legendary.invention.saas.modules.auth.dto.SecondFactorCompleteRequest backendRequest = new com.legendary.invention.saas.modules.auth.dto.SecondFactorCompleteRequest();
        backendRequest.setFactorCode(request.factorCode());
        backendRequest.setChallengeId(request.challengeId());
        backendRequest.setVerificationCode(request.verificationCode());
        return toVerification(verificationAppService.completeSecondFactorLogin(backendRequest, null, null), request.factorCode());
    }

    @GetMapping("/menus/builtin")
    public List<MenuNodeDTO> builtinMenus() {
        return SystemRouteCatalog.buildBuiltinPermissionMenus().stream().map(this::toMenuNode).toList();
    }

    private SystemUserSnapshotDTO toSnapshot(SysUserEntity user) {
        return new SystemUserSnapshotDTO(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getStatus(),
                user.getMobile(),
                user.getEmail(),
                user.getNickname(),
                user.getRealName(),
                user.getAvatarUrl(),
                user.getBirthMonth(),
                user.getGender(),
                user.getRegion(),
                user.getAvailableTime(),
                user.getIdCardNumber(),
                null
        );
    }

    private VerificationProviderDTO toProvider(com.legendary.invention.saas.modules.system.vo.SystemVO.VerificationProviderVO provider) {
        VerificationProviderDTO dto = new VerificationProviderDTO();
        dto.setFactorCode(provider.getFactorCode());
        dto.setFactorName(provider.getFactorName());
        dto.setEnabled(Boolean.TRUE.equals(provider.getEnabled()));
        dto.setBound(Boolean.TRUE.equals(provider.getBound()));
        dto.setStatus(provider.getStatusMessage());
        dto.setPromptMessage(provider.getStatusMessage());
        dto.setMaskedContact(provider.getMaskedContact());
        return dto;
    }

    private VerificationChallengeDTO toChallenge(com.legendary.invention.saas.modules.system.vo.SystemVO.VerificationChallengeVO challenge) {
        VerificationChallengeDTO dto = new VerificationChallengeDTO();
        dto.setFactorCode(challenge.getFactorCode());
        dto.setFactorName(challenge.getFactorName());
        dto.setChallengeId(challenge.getChallengeId());
        dto.setMaskedContact(challenge.getMaskedContact());
        dto.setPromptMessage(challenge.getPromptMessage());
        dto.setExpiresInSeconds(null);
        dto.setDebugCode(challenge.getDebugCode());
        return dto;
    }

    private VerificationVerificationDTO toVerification(com.legendary.invention.saas.modules.system.vo.SystemVO.VerificationVerificationVO verification, String factorCode) {
        return new VerificationVerificationDTO(
                Boolean.TRUE.equals(verification.getVerified()),
                verification.getMessage(),
                verification.getUserId(),
                verification.getTenantId(),
                factorCode
        );
    }

    private MenuNodeDTO toMenuNode(com.legendary.invention.saas.modules.system.vo.SystemVO.MenuVO menu) {
        MenuNodeDTO dto = new MenuNodeDTO();
        dto.setId(menu.getId());
        dto.setParentId(menu.getParentId());
        dto.setMenuCode(menu.getMenuCode());
        dto.setName(menu.getMenuName());
        dto.setPath(menu.getPath());
        dto.setComponent(menu.getComponent());
        dto.setIcon(menu.getIcon());
        dto.setPermissionKey(menu.getPermissionKey());
        dto.setSortNo(menu.getSortNo());
        dto.setChildren(menu.getChildren() == null ? List.of() : menu.getChildren().stream().map(this::toMenuNode).toList());
        return dto;
    }
}

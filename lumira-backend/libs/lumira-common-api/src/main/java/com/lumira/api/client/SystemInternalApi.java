package com.lumira.api.client;

import com.lumira.api.auth.LoginCodeChallengeDTO;
import com.lumira.api.auth.LoginCodeCompleteRequest;
import com.lumira.api.auth.LoginResponseDTO;
import com.lumira.api.auth.PasswordResetChallengeRequest;
import com.lumira.api.auth.PasswordResetCompleteRequest;
import com.lumira.api.auth.RegistrationCodeChallengeRequest;
import com.lumira.api.auth.RegistrationCompleteInternalRequest;
import com.lumira.api.auth.RegistrationContactAvailabilityDTO;
import com.lumira.api.auth.RegistrationContactAvailabilityRequest;
import com.lumira.api.auth.SecondFactorCompleteRequest;
import com.lumira.api.auth.VerificationBindRequest;
import com.lumira.api.system.CaptchaValidationRequestDTO;
import com.lumira.api.system.CurrentUserRoleOptionDTO;
import com.lumira.api.system.LoginAuditRecordRequestDTO;
import com.lumira.api.system.LoginCapabilitiesDTO;
import com.lumira.api.system.MaintenanceLoginPolicyDTO;
import com.lumira.api.system.MenuNodeDTO;
import com.lumira.api.system.OperationAuditRecordRequestDTO;
import com.lumira.api.system.PasskeyCredentialAssertionDTO;
import com.lumira.api.system.PasskeyCredentialDescriptorDTO;
import com.lumira.api.system.PasskeyCredentialDTO;
import com.lumira.api.system.PasskeyCredentialSaveRequestDTO;
import com.lumira.api.system.PasskeyCredentialUsageRequestDTO;
import com.lumira.api.system.PasskeySettingsDTO;
import com.lumira.api.system.PasswordLoginVerificationDTO;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.PluginPermissionRegistrationRequestDTO;
import com.lumira.api.system.SecuritySettingsDTO;
import com.lumira.api.system.SystemRoleSnapshotDTO;
import com.lumira.api.system.SystemUserEmailRecipientDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.api.system.SystemUserWechatRecipientDTO;
import com.lumira.api.system.VerificationBindingChallengeDTO;
import com.lumira.api.system.VerificationChallengeDTO;
import com.lumira.api.system.VerificationProviderDTO;
import com.lumira.api.system.VerificationVerificationDTO;
import com.lumira.api.system.WechatLoginSettingsDTO;
import com.lumira.api.system.WechatLoginUserRequestDTO;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(accept = MediaType.APPLICATION_JSON_VALUE)
public interface SystemInternalApi {

    @PostExchange("/internal/system/users/login/verify")
    PasswordLoginVerificationDTO verifyPasswordLogin(
            @RequestParam("account") String account,
            @RequestParam("password") String password
    );

    @GetExchange("/internal/system/users/{id}/identity")
    SystemUserSnapshotDTO findUserIdentityById(@PathVariable("id") Long id);

    @GetExchange("/internal/system/users/{id}/profile")
    SystemUserSnapshotDTO findUserProfileById(@PathVariable("id") Long id);

    @GetExchange("/internal/system/users/{id}/email-available")
    Boolean userHasEmail(
            @PathVariable("id") Long userId,
            @RequestParam("userUuid") String userUuid
    );

    @GetExchange("/internal/system/users/{id}/requires-password-change")
    Boolean requiresInitialPasswordChange(
            @PathVariable("id") Long userId,
            @RequestParam("userUuid") String userUuid
    );

    @GetExchange("/internal/system/users/{id}")
    SystemUserSnapshotDTO findUserById(@PathVariable("id") Long id);

    @GetExchange("/internal/system/users/{id}/target-uuid")
    String findTargetUserUuidById(@PathVariable("id") Long id);

    @GetExchange("/internal/system/users/identities-by-ids")
    List<SystemUserSnapshotDTO> userIdentitiesByIds(@RequestParam("ids") List<Long> userIds);

    @GetExchange("/internal/system/users/{id}/role-options")
    List<CurrentUserRoleOptionDTO> userRoleOptions(
            @PathVariable("id") Long userId,
            @RequestParam("userUuid") String userUuid
    );

    @GetExchange("/internal/system/roles/names-by-ids")
    List<SystemRoleSnapshotDTO> roleNamesByIds(@RequestParam("ids") List<Long> roleIds);

    @GetExchange("/internal/system/users/email-recipients")
    List<SystemUserEmailRecipientDTO> userEmailRecipientsByIds(@RequestParam("ids") List<Long> userIds);

    @GetExchange("/internal/system/users/wechat-recipients")
    List<SystemUserWechatRecipientDTO> userWechatRecipientsByIds(@RequestParam("ids") List<Long> userIds);

    @GetExchange("/internal/system/roles/{roleId}/email-recipients")
    List<SystemUserEmailRecipientDTO> userEmailRecipientsByRole(@PathVariable("roleId") Long roleId);

    @GetExchange("/internal/system/roles/{roleId}/wechat-recipients")
    List<SystemUserWechatRecipientDTO> userWechatRecipientsByRole(@PathVariable("roleId") Long roleId);

    @GetExchange("/internal/system/platform/email-recipients")
    List<SystemUserEmailRecipientDTO> platformUserEmailRecipients();

    @GetExchange("/internal/system/platform/wechat-recipients")
    List<SystemUserWechatRecipientDTO> platformUserWechatRecipients();

    @GetExchange("/internal/system/roles/{roleId}/identities")
    List<SystemUserSnapshotDTO> roleUserIdentities(@PathVariable("roleId") Long roleId);

    @PostExchange("/internal/system/users/wechat-login")
    SystemUserSnapshotDTO resolveWechatLoginUser(@RequestBody WechatLoginUserRequestDTO request);

    @GetExchange("/internal/system/permissions/snapshot")
    PermissionSnapshotDTO permissionSnapshot(
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid
    );

    @GetExchange("/internal/system/permissions/snapshot/current")
    Boolean isPermissionSnapshotVersionCurrent(@RequestParam("version") String version);

    @GetExchange("/internal/system/permissions/role-snapshot")
    PermissionSnapshotDTO permissionRoleSnapshot(
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid
    );

    @GetExchange("/internal/system/permissions/simulated-role-snapshot")
    PermissionSnapshotDTO simulatedRolePermissionSnapshot(
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid,
            @RequestParam("roleId") Long roleId
    );

    @PostExchange("/internal/system/permissions/invalidate")
    Boolean invalidatePermissionSnapshot();

    @PostExchange("/internal/system/permissions/plugin")
    Boolean registerPluginPermissions(@RequestBody PluginPermissionRegistrationRequestDTO request);

    @PostExchange("/internal/system/read-model-version/bump")
    Boolean bumpReadModelVersion(
            @RequestParam("contextName") String contextName,
            @RequestParam("scope") String scope,
            @RequestParam("eventKey") String eventKey
    );

    @GetExchange("/internal/system/read-model-version")
    Long readModelVersion(
            @RequestParam("contextName") String contextName,
            @RequestParam("scope") String scope
    );

    @PostExchange("/internal/system/captcha/validate")
    Boolean validateCaptcha(@RequestBody CaptchaValidationRequestDTO request);

    @PostExchange("/internal/system/audit/login")
    Boolean recordLoginAudit(@RequestBody LoginAuditRecordRequestDTO request);

    @PostExchange("/internal/system/audit/operation")
    Boolean recordOperationAudit(@RequestBody OperationAuditRecordRequestDTO request);

    @GetExchange("/internal/system/verification/login-capabilities")
    LoginCapabilitiesDTO loginCapabilities();

    @GetExchange("/internal/system/security/settings")
    SecuritySettingsDTO securitySettings();

    @GetExchange("/internal/system/config/runtime/smtp")
    Map<String, String> smtpRuntimeConfigValues();

    @GetExchange("/internal/system/config/runtime/wechat-official")
    Map<String, String> wechatOfficialRuntimeConfigValues();

    @GetExchange("/internal/system/verification/wechat-settings")
    WechatLoginSettingsDTO wechatLoginSettings();

    @GetExchange("/internal/system/maintenance-login-policy")
    MaintenanceLoginPolicyDTO maintenanceLoginPolicy();

    @GetExchange("/internal/system/verification/passkey-settings")
    PasskeySettingsDTO passkeySettings();

    @GetExchange("/internal/system/passkeys/assertion")
    PasskeyCredentialAssertionDTO passkeyCredentialAssertion(@RequestParam("credentialId") String credentialId);

    @GetExchange("/internal/system/passkeys/descriptors")
    List<PasskeyCredentialDescriptorDTO> passkeyCredentialDescriptors(
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid
    );

    @GetExchange("/internal/system/passkeys")
    List<PasskeyCredentialDTO> passkeyCredentials(
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid
    );

    @PostExchange("/internal/system/passkeys")
    PasskeyCredentialDTO savePasskeyCredential(@RequestBody PasskeyCredentialSaveRequestDTO request);

    @PostExchange("/internal/system/passkeys/usage")
    Boolean updatePasskeyCredentialUsage(@RequestBody PasskeyCredentialUsageRequestDTO request);

    @PostExchange("/internal/system/passkeys/{id}/label")
    PasskeyCredentialDTO renamePasskeyCredential(
            @PathVariable("id") Long id,
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid,
            @RequestParam("label") String label
    );

    @PostExchange("/internal/system/passkeys/{id}/delete")
    Boolean deletePasskeyCredential(
            @PathVariable("id") Long id,
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid
    );

    @GetExchange("/internal/system/verification/providers")
    List<VerificationProviderDTO> listVerificationProviders(
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid
    );

    @GetExchange("/internal/system/verification/login-options")
    List<LoginResponseDTO.SecondFactorOptionDTO> listLoginSecondFactorOptions(
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid
    );

    @GetExchange("/internal/system/verification/providers/{factorCode}")
    VerificationProviderDTO verificationProvider(
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid,
            @PathVariable("factorCode") String factorCode
    );

    @PostExchange("/internal/system/verification/providers/{factorCode}/bind")
    VerificationBindingChallengeDTO bindVerificationProvider(
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid,
            @PathVariable("factorCode") String factorCode,
            @RequestBody VerificationBindRequest request
    );

    @PostExchange("/internal/system/verification/providers/{factorCode}/unbind")
    Boolean unbindVerificationProvider(
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid,
            @PathVariable("factorCode") String factorCode,
            @RequestBody SecondFactorCompleteRequest request
    );

    @PostExchange("/internal/system/verification/providers/{factorCode}/challenge")
    VerificationChallengeDTO verificationChallenge(
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid,
            @PathVariable("factorCode") String factorCode
    );

    @PostExchange("/internal/system/verification/providers/{factorCode}/verify")
    VerificationVerificationDTO verificationVerify(
            @RequestParam("userId") Long userId,
            @RequestParam("userUuid") String userUuid,
            @PathVariable("factorCode") String factorCode,
            @RequestParam("challengeId") String challengeId,
            @RequestParam("verificationCode") String verificationCode
    );

    @PostExchange("/internal/system/verification/login-code/challenge")
    LoginCodeChallengeDTO loginCodeChallenge(
            @RequestParam("account") String account,
            @RequestParam("loginType") String loginType
    );

    @PostExchange("/internal/system/verification/login-code/complete")
    VerificationVerificationDTO completeLoginCodeLogin(@RequestBody LoginCodeCompleteRequest request);

    @PostExchange("/internal/system/registration/contact/availability")
    RegistrationContactAvailabilityDTO registrationContactAvailability(
            @RequestBody RegistrationContactAvailabilityRequest request
    );

    @PostExchange("/internal/system/registration/code/challenge")
    LoginCodeChallengeDTO registrationCodeChallenge(@RequestBody RegistrationCodeChallengeRequest request);

    @PostExchange("/internal/system/registration/complete")
    VerificationVerificationDTO completeRegistration(@RequestBody RegistrationCompleteInternalRequest request);

    @PostExchange("/internal/system/verification/password-reset/challenge")
    LoginCodeChallengeDTO passwordResetChallenge(@RequestBody PasswordResetChallengeRequest request);

    @PostExchange("/internal/system/verification/password-reset/complete")
    Boolean completePasswordReset(@RequestBody PasswordResetCompleteRequest request);

    @PostExchange("/internal/system/verification/second-factor/complete")
    VerificationVerificationDTO completeSecondFactorLogin(@RequestBody SecondFactorCompleteRequest request);

    @GetExchange("/internal/system/menus/builtin")
    List<MenuNodeDTO> builtinMenus();
}

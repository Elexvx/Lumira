package com.lumira.saas.modules.system.app;

import com.lumira.api.auth.LoginCodeChallengeDTO;
import com.lumira.api.auth.LoginCodeCompleteRequest;
import com.lumira.api.auth.LoginResponseDTO;
import com.lumira.api.auth.PasswordResetChallengeRequest;
import com.lumira.api.auth.PasswordResetCompleteRequest;
import com.lumira.api.auth.SecondFactorCompleteRequest;
import com.lumira.api.auth.VerificationBindRequest;
import com.lumira.api.client.SystemInternalApi;
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
import com.lumira.common.security.CurrentUser;
import com.lumira.saas.modules.system.controller.InternalSystemController;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service("systemInternalApi")
@Primary
public class SystemInternalApiService implements SystemInternalApi {

    private static final Authentication INTERNAL_SERVICE_AUTHENTICATION = internalServiceAuthentication();

    private final InternalSystemController delegate;

    public SystemInternalApiService(InternalSystemController delegate) {
        this.delegate = delegate;
    }

    @Override
    public PasswordLoginVerificationDTO verifyPasswordLogin(String account, String password) {
        return call(() -> delegate.verifyPasswordLogin(account, password));
    }

    @Override
    public SystemUserSnapshotDTO findUserIdentityById(Long id) {
        return call(() -> delegate.findUserIdentityById(id));
    }

    @Override
    public SystemUserSnapshotDTO findUserProfileById(Long id) {
        return call(() -> delegate.findUserProfileById(id));
    }

    @Override
    public Boolean userHasEmail(Long userId, String userUuid) {
        return call(() -> delegate.userHasEmail(userId, userUuid));
    }

    @Override
    public Boolean requiresInitialPasswordChange(Long userId, String userUuid) {
        return call(() -> delegate.requiresInitialPasswordChange(userId, userUuid));
    }

    @Override
    public SystemUserSnapshotDTO findUserById(Long id) {
        return call(() -> delegate.findUserById(id));
    }

    @Override
    public String findTargetUserUuidById(Long id) {
        return call(() -> delegate.findTargetUserUuidById(id));
    }

    @Override
    public List<SystemUserSnapshotDTO> userIdentitiesByIds(List<Long> userIds) {
        return call(() -> delegate.userIdentitiesByIds(userIds));
    }

    @Override
    public List<CurrentUserRoleOptionDTO> userRoleOptions(Long userId, String userUuid) {
        return call(() -> delegate.userRoleOptions(userId, userUuid));
    }

    @Override
    public List<SystemRoleSnapshotDTO> roleNamesByIds(List<Long> roleIds) {
        return call(() -> delegate.roleNamesByIds(roleIds));
    }

    @Override
    public List<SystemUserEmailRecipientDTO> userEmailRecipientsByIds(List<Long> userIds) {
        return call(() -> delegate.userEmailRecipientsByIds(userIds));
    }

    @Override
    public List<SystemUserWechatRecipientDTO> userWechatRecipientsByIds(List<Long> userIds) {
        return call(() -> delegate.userWechatRecipientsByIds(userIds));
    }

    @Override
    public List<SystemUserEmailRecipientDTO> userEmailRecipientsByRole(Long roleId) {
        return call(() -> delegate.userEmailRecipientsByRole(roleId));
    }

    @Override
    public List<SystemUserWechatRecipientDTO> userWechatRecipientsByRole(Long roleId) {
        return call(() -> delegate.userWechatRecipientsByRole(roleId));
    }

    @Override
    public List<SystemUserEmailRecipientDTO> platformUserEmailRecipients() {
        return call(delegate::platformUserEmailRecipients);
    }

    @Override
    public List<SystemUserWechatRecipientDTO> platformUserWechatRecipients() {
        return call(delegate::platformUserWechatRecipients);
    }

    @Override
    public List<SystemUserSnapshotDTO> roleUserIdentities(Long roleId) {
        return call(() -> delegate.roleUserIdentities(roleId));
    }

    @Override
    @Transactional
    public SystemUserSnapshotDTO resolveWechatLoginUser(WechatLoginUserRequestDTO request) {
        return call(() -> delegate.resolveWechatLoginUser(request));
    }

    @Override
    public PermissionSnapshotDTO permissionSnapshot(Long userId, String userUuid) {
        return call(() -> delegate.permissionSnapshot(userId, userUuid));
    }

    @Override
    public PermissionSnapshotDTO permissionRoleSnapshot(Long userId, String userUuid) {
        return call(() -> delegate.permissionRoleSnapshot(userId, userUuid));
    }

    @Override
    public PermissionSnapshotDTO simulatedRolePermissionSnapshot(Long userId, String userUuid, Long roleId) {
        return call(() -> delegate.simulatedRolePermissionSnapshot(userId, userUuid, roleId));
    }

    @Override
    public Boolean invalidatePermissionSnapshot() {
        return call(delegate::invalidatePermissionSnapshot);
    }

    @Override
    @Transactional
    public Boolean registerPluginPermissions(PluginPermissionRegistrationRequestDTO request) {
        return call(() -> delegate.registerPluginPermissions(request));
    }

    @Override
    public Boolean bumpReadModelVersion(String contextName, String scope, String eventKey) {
        return call(() -> delegate.bumpReadModelVersion(contextName, scope, eventKey));
    }

    @Override
    public Long readModelVersion(String contextName, String scope) {
        return call(() -> delegate.readModelVersion(contextName, scope));
    }

    @Override
    public Boolean validateCaptcha(CaptchaValidationRequestDTO request) {
        return call(() -> delegate.validateCaptcha(request));
    }

    @Override
    public Boolean recordLoginAudit(LoginAuditRecordRequestDTO request) {
        return call(() -> delegate.recordLoginAudit(request));
    }

    @Override
    public Boolean recordOperationAudit(OperationAuditRecordRequestDTO request) {
        return call(() -> delegate.recordOperationAudit(request));
    }

    @Override
    public LoginCapabilitiesDTO loginCapabilities() {
        return call(delegate::loginCapabilities);
    }

    @Override
    public SecuritySettingsDTO securitySettings() {
        return call(delegate::securitySettings);
    }

    @Override
    public Map<String, String> smtpRuntimeConfigValues() {
        return call(delegate::smtpRuntimeConfigValues);
    }

    @Override
    public Map<String, String> wechatOfficialRuntimeConfigValues() {
        return call(delegate::wechatOfficialRuntimeConfigValues);
    }

    @Override
    public WechatLoginSettingsDTO wechatLoginSettings() {
        return call(delegate::wechatLoginSettings);
    }

    @Override
    public MaintenanceLoginPolicyDTO maintenanceLoginPolicy() {
        return call(delegate::maintenanceLoginPolicy);
    }

    @Override
    public PasskeySettingsDTO passkeySettings() {
        return call(delegate::passkeySettings);
    }

    @Override
    public PasskeyCredentialAssertionDTO passkeyCredentialAssertion(String credentialId) {
        return call(() -> delegate.passkeyCredentialAssertion(credentialId));
    }

    @Override
    public List<PasskeyCredentialDescriptorDTO> passkeyCredentialDescriptors(Long userId, String userUuid) {
        return call(() -> delegate.passkeyCredentialDescriptors(userId, userUuid));
    }

    @Override
    public List<PasskeyCredentialDTO> passkeyCredentials(Long userId, String userUuid) {
        return call(() -> delegate.passkeyCredentials(userId, userUuid));
    }

    @Override
    public PasskeyCredentialDTO savePasskeyCredential(PasskeyCredentialSaveRequestDTO request) {
        return call(() -> delegate.savePasskeyCredential(request));
    }

    @Override
    public Boolean updatePasskeyCredentialUsage(PasskeyCredentialUsageRequestDTO request) {
        return call(() -> delegate.updatePasskeyCredentialUsage(request));
    }

    @Override
    public PasskeyCredentialDTO renamePasskeyCredential(Long id, Long userId, String userUuid, String label) {
        return call(() -> delegate.renamePasskeyCredential(id, userId, userUuid, label));
    }

    @Override
    public Boolean deletePasskeyCredential(Long id, Long userId, String userUuid) {
        return call(() -> delegate.deletePasskeyCredential(id, userId, userUuid));
    }

    @Override
    public List<VerificationProviderDTO> listVerificationProviders(Long userId, String userUuid) {
        return call(() -> delegate.listVerificationProviders(userId, userUuid));
    }

    @Override
    public List<LoginResponseDTO.SecondFactorOptionDTO> listLoginSecondFactorOptions(Long userId, String userUuid) {
        return call(() -> delegate.listLoginSecondFactorOptions(userId, userUuid));
    }

    @Override
    public VerificationProviderDTO verificationProvider(Long userId, String userUuid, String factorCode) {
        return call(() -> delegate.verificationProvider(userId, userUuid, factorCode));
    }

    @Override
    public VerificationBindingChallengeDTO bindVerificationProvider(Long userId, String userUuid, String factorCode, VerificationBindRequest request) {
        return call(() -> delegate.bindVerificationProvider(userId, userUuid, factorCode, request));
    }

    @Override
    public Boolean unbindVerificationProvider(Long userId, String userUuid, String factorCode, SecondFactorCompleteRequest request) {
        return call(() -> delegate.unbindVerificationProvider(userId, userUuid, factorCode, request));
    }

    @Override
    public VerificationChallengeDTO verificationChallenge(Long userId, String userUuid, String factorCode) {
        return call(() -> delegate.verificationChallenge(userId, userUuid, factorCode));
    }

    @Override
    public VerificationVerificationDTO verificationVerify(
            Long userId,
            String userUuid,
            String factorCode,
            String challengeId,
            String verificationCode
    ) {
        return call(() -> delegate.verificationVerify(userId, userUuid, factorCode, challengeId, verificationCode));
    }

    @Override
    @Transactional
    public LoginCodeChallengeDTO loginCodeChallenge(String account, String loginType) {
        return call(() -> delegate.loginCodeChallenge(account, loginType));
    }

    @Override
    public VerificationVerificationDTO completeLoginCodeLogin(LoginCodeCompleteRequest request) {
        return call(() -> delegate.completeLoginCodeLogin(request));
    }

    @Override
    @Transactional
    public LoginCodeChallengeDTO passwordResetChallenge(PasswordResetChallengeRequest request) {
        return call(() -> delegate.passwordResetChallenge(request));
    }

    @Override
    @Transactional
    public Boolean completePasswordReset(PasswordResetCompleteRequest request) {
        return call(() -> delegate.completePasswordReset(request));
    }

    @Override
    public VerificationVerificationDTO completeSecondFactorLogin(SecondFactorCompleteRequest request) {
        return call(() -> delegate.completeSecondFactorLogin(request));
    }

    @Override
    public List<MenuNodeDTO> builtinMenus() {
        return call(delegate::builtinMenus);
    }

    private <T> T call(SystemCall<T> call) {
        SecurityContext previousContext = SecurityContextHolder.getContext();
        SecurityContext temporaryContext = SecurityContextHolder.createEmptyContext();
        temporaryContext.setAuthentication(INTERNAL_SERVICE_AUTHENTICATION);
        SecurityContextHolder.setContext(temporaryContext);
        try {
            return call.execute();
        } finally {
            SecurityContextHolder.setContext(previousContext);
        }
    }

    private static Authentication internalServiceAuthentication() {
        CurrentUser internalService = new CurrentUser(0L, "internal-service", null, "internal", 0, false, java.util.Set.of());
        return new UsernamePasswordAuthenticationToken(internalService, "internal-token", java.util.Set.of());
    }

    @FunctionalInterface
    private interface SystemCall<T> {
        T execute();
    }
}

package com.lumira.api.client;

import com.lumira.api.auth.LoginResponseDTO;
import com.lumira.api.system.CaptchaValidationRequestDTO;
import com.lumira.api.system.LoginAuditRecordRequestDTO;
import com.lumira.api.system.LoginCapabilitiesDTO;
import com.lumira.api.system.MenuNodeDTO;
import com.lumira.api.system.OperationAuditRecordRequestDTO;
import com.lumira.api.system.PasskeyCredentialDTO;
import com.lumira.api.system.PasskeyCredentialSaveRequestDTO;
import com.lumira.api.system.PasskeyCredentialUsageRequestDTO;
import com.lumira.api.system.PasskeySettingsDTO;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.PluginPermissionRegistrationRequestDTO;
import com.lumira.api.system.SecuritySettingsDTO;
import com.lumira.api.system.SystemRoleSnapshotDTO;
import com.lumira.api.system.SystemUserContactSnapshotDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.api.system.VerificationChallengeDTO;
import com.lumira.api.system.VerificationProviderDTO;
import com.lumira.api.system.VerificationVerificationDTO;
import com.lumira.api.system.WechatLoginSettingsDTO;
import com.lumira.api.system.WechatLoginUserRequestDTO;

import java.util.List;
import java.util.Map;

public interface SystemInternalApi {

    
    SystemUserSnapshotDTO findLoginUser( String account);

    
    SystemUserSnapshotDTO findUserById( Long id);


    List<SystemUserSnapshotDTO> usersByIds( Long tenantId, List<Long> userIds);


    List<SystemRoleSnapshotDTO> rolesByIds( Long tenantId, List<Long> roleIds);


    List<SystemUserContactSnapshotDTO> userContactsByIds( Long tenantId, List<Long> userIds);


    List<SystemUserContactSnapshotDTO> userContactsByRole( Long tenantId, Long roleId);


    List<SystemUserContactSnapshotDTO> tenantUserContacts( Long tenantId);


    List<Long> userIdsByRole( Long tenantId, Long roleId);

    
    SystemUserSnapshotDTO resolveWechatLoginUser( WechatLoginUserRequestDTO request);

    
    PermissionSnapshotDTO permissionSnapshot( Long tenantId,  Long userId);

    
    Boolean invalidatePermissionSnapshot( Long tenantId);


    Boolean registerPluginPermissions( PluginPermissionRegistrationRequestDTO request);


    Boolean bumpReadModelVersion( Long tenantId, String contextName, String scope, String eventKey);


    Long readModelVersion( Long tenantId, String contextName, String scope);

    
    Boolean validateCaptcha( CaptchaValidationRequestDTO request);

    
    Boolean recordLoginAudit( LoginAuditRecordRequestDTO request);


    Boolean recordOperationAudit( OperationAuditRecordRequestDTO request);

    
    LoginCapabilitiesDTO loginCapabilities( Long tenantId);

    
    SecuritySettingsDTO securitySettings( Long tenantId);


    Map<String, String> platformConfigValues( Long tenantId, List<String> keys);

    
    WechatLoginSettingsDTO wechatLoginSettings( Long tenantId);

    
    PasskeySettingsDTO passkeySettings( Long tenantId);

    
    PasskeyCredentialDTO passkeyCredentialByCredentialId( String credentialId);

    
    List<PasskeyCredentialDTO> passkeyCredentials( Long tenantId,  Long userId);

    
    PasskeyCredentialDTO savePasskeyCredential( PasskeyCredentialSaveRequestDTO request);

    
    Boolean updatePasskeyCredentialUsage( PasskeyCredentialUsageRequestDTO request);

    
    PasskeyCredentialDTO renamePasskeyCredential(
             Long id,
             Long tenantId,
             Long userId,
             String label
    );

    
    Boolean deletePasskeyCredential( Long id,  Long tenantId,  Long userId);

    
    List<VerificationProviderDTO> listVerificationProviders( Long tenantId,  Long userId);

    
    List<com.lumira.api.auth.LoginResponseDTO.SecondFactorOptionDTO> listLoginSecondFactorOptions(
             Long tenantId,
             Long userId
    );

    
    VerificationProviderDTO verificationProvider(
             Long tenantId,
             Long userId,
             String factorCode
    );

    
    VerificationChallengeDTO bindVerificationProvider(
             Long tenantId,
             Long userId,
             String factorCode
    );

    
    Boolean unbindVerificationProvider(
             Long tenantId,
             Long userId,
             String factorCode
    );

    
    VerificationChallengeDTO verificationChallenge(
             Long tenantId,
             Long userId,
             String factorCode
    );

    
    VerificationVerificationDTO verificationVerify(
             Long tenantId,
             Long userId,
             String factorCode,
             String challengeId,
             String verificationCode
    );

    
    com.lumira.api.auth.LoginCodeChallengeDTO loginCodeChallenge(
             Long tenantId,
             String account,
             String loginType
    );

    
    VerificationVerificationDTO completeLoginCodeLogin( com.lumira.api.auth.LoginCodeCompleteRequest request);

    
    VerificationVerificationDTO completeSecondFactorLogin( com.lumira.api.auth.SecondFactorCompleteRequest request);

    
    java.util.List<MenuNodeDTO> builtinMenus();
}

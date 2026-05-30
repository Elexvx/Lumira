package com.legendary.invention.api.client;

import com.legendary.invention.api.auth.LoginResponseDTO;
import com.legendary.invention.api.system.CaptchaValidationRequestDTO;
import com.legendary.invention.api.system.LoginAuditRecordRequestDTO;
import com.legendary.invention.api.system.LoginCapabilitiesDTO;
import com.legendary.invention.api.system.MenuNodeDTO;
import com.legendary.invention.api.system.PasskeyCredentialDTO;
import com.legendary.invention.api.system.PasskeyCredentialSaveRequestDTO;
import com.legendary.invention.api.system.PasskeyCredentialUsageRequestDTO;
import com.legendary.invention.api.system.PasskeySettingsDTO;
import com.legendary.invention.api.system.PermissionSnapshotDTO;
import com.legendary.invention.api.system.SecuritySettingsDTO;
import com.legendary.invention.api.system.SystemUserSnapshotDTO;
import com.legendary.invention.api.system.VerificationChallengeDTO;
import com.legendary.invention.api.system.VerificationProviderDTO;
import com.legendary.invention.api.system.VerificationVerificationDTO;
import com.legendary.invention.api.system.WechatLoginSettingsDTO;
import com.legendary.invention.api.system.WechatLoginUserRequestDTO;

import java.util.List;

public interface SystemInternalApi {

    
    SystemUserSnapshotDTO findLoginUser( String account);

    
    SystemUserSnapshotDTO findUserById( Long id);

    
    SystemUserSnapshotDTO resolveWechatLoginUser( WechatLoginUserRequestDTO request);

    
    PermissionSnapshotDTO permissionSnapshot( Long tenantId,  Long userId);

    
    Boolean invalidatePermissionSnapshot( Long tenantId);

    
    Boolean validateCaptcha( CaptchaValidationRequestDTO request);

    
    Boolean recordLoginAudit( LoginAuditRecordRequestDTO request);

    
    LoginCapabilitiesDTO loginCapabilities( Long tenantId);

    
    SecuritySettingsDTO securitySettings( Long tenantId);

    
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

    
    List<com.legendary.invention.api.auth.LoginResponseDTO.SecondFactorOptionDTO> listLoginSecondFactorOptions(
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

    
    com.legendary.invention.api.auth.LoginCodeChallengeDTO loginCodeChallenge(
             Long tenantId,
             String account,
             String loginType
    );

    
    VerificationVerificationDTO completeLoginCodeLogin( com.legendary.invention.api.auth.LoginCodeCompleteRequest request);

    
    VerificationVerificationDTO completeSecondFactorLogin( com.legendary.invention.api.auth.SecondFactorCompleteRequest request);

    
    java.util.List<MenuNodeDTO> builtinMenus();
}

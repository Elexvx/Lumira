package com.lumira.api.system.port;

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
import com.lumira.api.system.LoginCapabilitiesDTO;
import com.lumira.api.system.PasswordLoginVerificationDTO;
import com.lumira.api.system.PasswordLoginVerificationRequest;
import com.lumira.api.system.VerificationCodeCheckRequest;
import com.lumira.api.system.VerificationBindingChallengeDTO;
import com.lumira.api.system.VerificationChallengeDTO;
import com.lumira.api.system.VerificationProviderDTO;
import com.lumira.api.system.VerificationVerificationDTO;
import com.lumira.api.system.WechatLoginSettingsDTO;
import java.util.List;

public interface VerificationPort {
    PasswordLoginVerificationDTO verifyPasswordLogin(PasswordLoginVerificationRequest request);
    default PasswordLoginVerificationDTO verifyPasswordLogin(String account, String password) {
        return verifyPasswordLogin(new PasswordLoginVerificationRequest(account, password));
    }
    Boolean validateCaptcha(CaptchaValidationRequestDTO request);
    LoginCapabilitiesDTO loginCapabilities();
    WechatLoginSettingsDTO wechatLoginSettings();
    List<VerificationProviderDTO> listVerificationProviders(Long userId, String userUuid);
    List<LoginResponseDTO.SecondFactorOptionDTO> listLoginSecondFactorOptions(Long userId, String userUuid);
    VerificationProviderDTO verificationProvider(Long userId, String userUuid, String factorCode);
    VerificationBindingChallengeDTO bindVerificationProvider(Long userId, String userUuid, String factorCode, VerificationBindRequest request);
    Boolean unbindVerificationProvider(Long userId, String userUuid, String factorCode, SecondFactorCompleteRequest request);
    VerificationChallengeDTO verificationChallenge(Long userId, String userUuid, String factorCode);
    VerificationVerificationDTO verificationVerify(Long userId, String userUuid, String factorCode, VerificationCodeCheckRequest request);
    default VerificationVerificationDTO verificationVerify(
            Long userId, String userUuid, String factorCode, String challengeId, String verificationCode
    ) {
        return verificationVerify(userId, userUuid, factorCode, new VerificationCodeCheckRequest(challengeId, verificationCode));
    }
    LoginCodeChallengeDTO loginCodeChallenge(String account, String loginType);
    VerificationVerificationDTO completeLoginCodeLogin(LoginCodeCompleteRequest request);
    RegistrationContactAvailabilityDTO registrationContactAvailability(RegistrationContactAvailabilityRequest request);
    LoginCodeChallengeDTO registrationCodeChallenge(RegistrationCodeChallengeRequest request);
    VerificationVerificationDTO completeRegistration(RegistrationCompleteInternalRequest request);
    LoginCodeChallengeDTO passwordResetChallenge(PasswordResetChallengeRequest request);
    Boolean completePasswordReset(PasswordResetCompleteRequest request);
    VerificationVerificationDTO completeSecondFactorLogin(SecondFactorCompleteRequest request);
}

package com.legendary.invention.saas.modules.auth.app;

import com.legendary.invention.saas.infrastructure.security.service.LoginProtectionService;
import com.legendary.invention.saas.infrastructure.security.service.PasswordPolicyService;
import com.legendary.invention.saas.modules.audit.app.LoginAuditService;
import com.legendary.invention.saas.modules.auth.dto.LoginCodeChallengeRequest;
import com.legendary.invention.saas.modules.auth.vo.LoginCodeChallengeVO;
import com.legendary.invention.saas.modules.system.verification.SystemVerificationAppService;
import com.legendary.invention.saas.modules.tenant.domain.TenantDomainService;
import com.legendary.invention.saas.modules.tenant.entity.TenantInfoEntity;
import com.legendary.invention.saas.modules.user.domain.UserDomainService;
import com.legendary.invention.saas.modules.user.entity.SysUserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthAppServiceLoginCodeRegistrationTest {

    @Test
    void smsLoginCodeChallengeShouldAutoRegisterMissingMobileUser() {
        String mobile = "13405825198";
        UserDomainService userDomainService = mock(UserDomainService.class);
        TenantDomainService tenantDomainService = mock(TenantDomainService.class);
        LoginAuditService loginAuditService = mock(LoginAuditService.class);
        LoginProtectionService loginProtectionService = mock(LoginProtectionService.class);
        PasswordPolicyService passwordPolicyService = mock(PasswordPolicyService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        SystemVerificationAppService verificationAppService = mock(SystemVerificationAppService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        SysUserEntity registeredUser = new SysUserEntity();
        registeredUser.setId(3001L);
        registeredUser.setUsername(mobile);
        registeredUser.setMobile(mobile);
        registeredUser.setNickname(mobile);
        registeredUser.setStatus("ENABLED");

        TenantInfoEntity platformTenant = new TenantInfoEntity();
        platformTenant.setId(1001L);
        platformTenant.setTenantName("平台租户");

        LoginCodeChallengeVO expectedChallenge = new LoginCodeChallengeVO();
        expectedChallenge.setLoginType("sms");
        expectedChallenge.setChallengeId("challenge-1");

        when(userDomainService.findLoginUser(mobile))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(registeredUser));
        when(tenantDomainService.findTenantById(1001L)).thenReturn(Optional.of(platformTenant));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
        when(verificationAppService.startLoginCodeChallenge(registeredUser, 1001L, "sms")).thenReturn(expectedChallenge);

        AuthAppService service = new AuthAppService(
                userDomainService,
                tenantDomainService,
                loginAuditService,
                null,
                null,
                loginProtectionService,
                null,
                null,
                null,
                passwordPolicyService,
                passwordEncoder,
                null,
                verificationAppService,
                null,
                jdbcTemplate
        );

        LoginCodeChallengeRequest request = new LoginCodeChallengeRequest();
        request.setLoginType("sms");
        request.setAccount(mobile);

        LoginCodeChallengeVO challenge = service.loginCodeChallenge(request, "127.0.0.1", "JUnit");

        assertEquals("challenge-1", challenge.getChallengeId());
        verify(jdbcTemplate).update(
                anyString(),
                eq(mobile),
                eq("encoded-password"),
                eq(mobile),
                eq(mobile),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq("ENABLED"),
                eq(0L),
                eq(0L)
        );
        verify(jdbcTemplate).update(anyString(), eq(1001L), eq(3001L), eq(1), eq(0L), eq(0L));
        verify(verificationAppService).startLoginCodeChallenge(registeredUser, 1001L, "sms");
        verify(loginProtectionService).ensureCanAttempt(mobile, "127.0.0.1");
        verify(loginProtectionService).recordAttempt(mobile, "127.0.0.1");
    }
}

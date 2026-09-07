package com.lumira.saas.modules.system.controller;

import com.lumira.saas.infrastructure.readmodel.ReadModelVersionService;
import com.lumira.saas.infrastructure.security.service.AuthSessionStore;
import com.lumira.saas.infrastructure.security.service.CaptchaService;
import com.lumira.saas.infrastructure.security.service.PasswordPolicyService;
import com.lumira.saas.infrastructure.security.service.SecuritySettingsService;
import com.lumira.saas.modules.audit.app.LoginAuditService;
import com.lumira.saas.modules.audit.app.OperationAuditService;
import com.lumira.saas.modules.iam.service.IamUserService;
import com.lumira.saas.modules.iam.service.PermissionSnapshotService;
import com.lumira.saas.modules.system.app.SystemInternalApplicationService;
import com.lumira.saas.modules.system.internal.app.InternalSystemApplicationService;
import com.lumira.saas.modules.system.passkey.PasskeyCredentialAppService;
import com.lumira.saas.modules.system.verification.SystemVerificationAppService;
import com.lumira.saas.modules.system.verification.WechatLoginSettingsService;
import com.lumira.saas.modules.user.app.UserAccountQueryService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * HTTP transport adapter for the internal System application port.
 *
 * <p>Endpoint annotations and implementation remain on the application
 * service for compatibility with the existing internal contract. This class
 * owns only the HTTP route prefix and dependency wiring; local callers use
 * {@link SystemInternalApplicationService} directly.</p>
 */
@RestController
@RequestMapping("/internal/system")
public class InternalSystemController extends SystemInternalApplicationService {

    public InternalSystemController(
            UserAccountQueryService userDomainService,
            IamUserService iamUserService,
            PermissionSnapshotService permissionSnapshotService,
            CaptchaService captchaService,
            SystemVerificationAppService verificationAppService,
            WechatLoginSettingsService wechatLoginSettingsService,
            PasskeyCredentialAppService passkeyCredentialAppService,
            InternalSystemApplicationService internalSystemApplicationService,
            PasswordEncoder passwordEncoder,
            LoginAuditService loginAuditService,
            OperationAuditService operationAuditService,
            SecuritySettingsService securitySettingsService,
            PasswordPolicyService passwordPolicyService,
            AuthSessionStore authSessionStore,
            ReadModelVersionService readModelVersionService
    ) {
        super(
                userDomainService,
                iamUserService,
                permissionSnapshotService,
                captchaService,
                verificationAppService,
                wechatLoginSettingsService,
                passkeyCredentialAppService,
                internalSystemApplicationService,
                passwordEncoder,
                loginAuditService,
                operationAuditService,
                securitySettingsService,
                passwordPolicyService,
                authSessionStore,
                readModelVersionService
        );
    }
}

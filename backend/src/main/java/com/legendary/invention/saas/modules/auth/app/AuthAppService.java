package com.legendary.invention.saas.modules.auth.app;

import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.saas.infrastructure.security.CurrentUser;
import com.legendary.invention.saas.infrastructure.security.model.AuthSession;
import com.legendary.invention.saas.infrastructure.security.model.TokenClaims;
import com.legendary.invention.saas.infrastructure.security.model.TokenType;
import com.legendary.invention.saas.infrastructure.security.service.AuthSessionStore;
import com.legendary.invention.saas.infrastructure.security.service.CaptchaService;
import com.legendary.invention.saas.infrastructure.security.service.PasswordPolicyService;
import com.legendary.invention.saas.infrastructure.security.service.JwtTokenService;
import com.legendary.invention.saas.infrastructure.security.service.LoginProtectionService;
import com.legendary.invention.saas.infrastructure.security.service.SecuritySettingsService;
import com.legendary.invention.saas.modules.audit.app.LoginAuditService;
import com.legendary.invention.saas.modules.auth.dto.LoginRequest;
import com.legendary.invention.saas.modules.auth.dto.LoginCodeChallengeRequest;
import com.legendary.invention.saas.modules.auth.dto.LoginCodeCompleteRequest;
import com.legendary.invention.saas.modules.auth.dto.SimulatedRoleRequest;
import com.legendary.invention.saas.modules.auth.dto.SecondFactorCompleteRequest;
import com.legendary.invention.saas.modules.auth.dto.RefreshTokenRequest;
import com.legendary.invention.saas.modules.auth.dto.WechatLoginRequest;
import com.legendary.invention.saas.modules.auth.vo.AuthUserVO;
import com.legendary.invention.saas.modules.auth.vo.CurrentUserVO;
import com.legendary.invention.saas.modules.auth.vo.LoginCodeChallengeVO;
import com.legendary.invention.saas.modules.auth.vo.LoginResponseVO;
import com.legendary.invention.saas.modules.auth.vo.RefreshTokenResponseVO;
import com.legendary.invention.saas.modules.auth.vo.WechatAuthorizeUrlVO;
import com.legendary.invention.saas.modules.iam.service.PermissionSnapshotService;
import com.legendary.invention.saas.modules.system.verification.SystemVerificationAppService;
import com.legendary.invention.saas.modules.tenant.domain.TenantDomainService;
import com.legendary.invention.saas.modules.tenant.entity.TenantInfoEntity;
import com.legendary.invention.saas.modules.tenant.vo.TenantSummaryVO;
import com.legendary.invention.saas.modules.user.domain.UserDomainService;
import com.legendary.invention.saas.modules.user.entity.SysUserEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AuthAppService {

    private static final Long PLATFORM_TENANT_ID = com.legendary.invention.common.constant.PlatformConstants.PLATFORM_TENANT_ID;
    private static final String DEFAULT_LOGIN_ROLE_CODE = "commonuser";
    private static final String DEFAULT_REGISTRATION_ROLE_CODE_KEY = "auth.default-registration-role-code";
    private static final Pattern MOBILE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserDomainService userDomainService;
    private final TenantDomainService tenantDomainService;
    private final LoginAuditService loginAuditService;
    private final AuthSessionStore authSessionStore;
    private final CaptchaService captchaService;
    private final LoginProtectionService loginProtectionService;
    private final LoginEncryptionService loginEncryptionService;
    private final JwtTokenService jwtTokenService;
    private final SecuritySettingsService securitySettingsService;
    private final PasswordPolicyService passwordPolicyService;
    private final PasswordEncoder passwordEncoder;
    private final PermissionSnapshotService permissionSnapshotService;
    private final SystemVerificationAppService systemVerificationAppService;
    private final WechatLoginService wechatLoginService;
    private final JdbcTemplate jdbcTemplate;

    public AuthAppService(
            UserDomainService userDomainService,
            TenantDomainService tenantDomainService,
            LoginAuditService loginAuditService,
            AuthSessionStore authSessionStore,
            CaptchaService captchaService,
            LoginProtectionService loginProtectionService,
            LoginEncryptionService loginEncryptionService,
            JwtTokenService jwtTokenService,
            SecuritySettingsService securitySettingsService,
            PasswordPolicyService passwordPolicyService,
            PasswordEncoder passwordEncoder,
            PermissionSnapshotService permissionSnapshotService,
            SystemVerificationAppService systemVerificationAppService,
            WechatLoginService wechatLoginService,
            JdbcTemplate jdbcTemplate
    ) {
        this.userDomainService = userDomainService;
        this.tenantDomainService = tenantDomainService;
        this.loginAuditService = loginAuditService;
        this.authSessionStore = authSessionStore;
        this.captchaService = captchaService;
        this.loginProtectionService = loginProtectionService;
        this.loginEncryptionService = loginEncryptionService;
        this.jwtTokenService = jwtTokenService;
        this.securitySettingsService = securitySettingsService;
        this.passwordPolicyService = passwordPolicyService;
        this.passwordEncoder = passwordEncoder;
        this.permissionSnapshotService = permissionSnapshotService;
        this.systemVerificationAppService = systemVerificationAppService;
        this.wechatLoginService = wechatLoginService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public LoginResponseVO login(LoginRequest request, String loginIp, String userAgent) {
        String account = request.account();
        loginProtectionService.ensureCanAttempt(account, loginIp);
        loginProtectionService.recordAttempt(account, loginIp);
        validateCaptchaIfRequired(request, account, loginIp, userAgent);
        String loginPassword = loginEncryptionService.decryptPassword(request.getPassword());

        SysUserEntity user = resolveLoginUserForPassword(account, loginPassword, loginIp, userAgent);
        if (user == null) {
            loginProtectionService.recordFailure(account, loginIp);
            loginAuditService.log(null, null, account, "PASSWORD", "FAIL", "用户不存在", loginIp, userAgent);
            throw new BizException(
                    ErrorCode.ACCOUNT_NOT_FOUND,
                    "登录失败，账号不存在: " + account,
                    ErrorCode.LOGIN_FAILED.getDefaultUserMessage()
            );
        }

        if (isUserDisabled(user)) {
            loginProtectionService.recordFailure(account, loginIp);
            loginAuditService.log(user.getId(), null, user.getUsername(), "PASSWORD", "FAIL", "账号已禁用", loginIp, userAgent);
            throw new BizException(
                    ErrorCode.ACCOUNT_DISABLED,
                    "登录失败，账号已禁用: " + user.getUsername(),
                    ErrorCode.ACCOUNT_DISABLED.getDefaultUserMessage()
            );
        }

        if (!passwordEncoder.matches(loginPassword, user.getPasswordHash())) {
            loginProtectionService.recordFailure(account, loginIp);
            loginAuditService.log(user.getId(), null, user.getUsername(), "PASSWORD", "FAIL", "密码错误", loginIp, userAgent);
            throw new BizException(
                    ErrorCode.PASSWORD_ERROR,
                    "登录失败，密码错误: " + user.getUsername(),
                    ErrorCode.LOGIN_FAILED.getDefaultUserMessage()
            );
        }

        TenantInfoEntity currentTenant = platformTenant();
        Long tenantId = currentTenant == null ? null : currentTenant.getId();
        LoginResponseVO response = new LoginResponseVO();
        response.setUser(toAuthUser(user, tenantId));
        response.setTenants(List.of());
        response.setCurrentTenant(tenantDomainService.toTenantSummary(currentTenant));
        List<LoginResponseVO.SecondFactorOptionVO> secondFactorOptions = collectSecondFactorOptions(user, tenantId);
        if (!secondFactorOptions.isEmpty()) {
            response.setRequiresSecondFactor(Boolean.TRUE);
            response.setSecondFactorOptions(secondFactorOptions);
            loginAuditService.log(user.getId(), tenantId, user.getUsername(), "PASSWORD", "PENDING", "SECOND_FACTOR_REQUIRED", loginIp, userAgent);
            return response;
        }

        AuthSession session = buildNewSession(user, tenantId, loginIp, userAgent);
        String refreshTokenId = UUID.randomUUID().toString();
        session.setRefreshTokenId(refreshTokenId);

        if (!securitySettingsService.isAllowMultiDeviceLogin()) {
            authSessionStore.revokeUserSessions(user.getId(), true);
        }
        authSessionStore.save(session, true);
        loginProtectionService.clearFailureState(account, loginIp);

        response.setAccessToken(jwtTokenService.generateAccessToken(session));
        response.setRefreshToken(jwtTokenService.generateRefreshToken(session, refreshTokenId));
        response.setTokenType("Bearer");
        response.setExpiresIn(jwtTokenService.getAccessTokenExpireSeconds());
        response.setRequiresCaptcha(Boolean.FALSE);

        loginAuditService.log(user.getId(), tenantId, user.getUsername(), "PASSWORD", "SUCCESS", null, loginIp, userAgent);
        return response;
    }

    public LoginCodeChallengeVO loginCodeChallenge(LoginCodeChallengeRequest request, String loginIp, String userAgent) {
        String account = request.getAccount();
        loginProtectionService.ensureCanAttempt(account, loginIp);
        loginProtectionService.recordAttempt(account, loginIp);

        SysUserEntity user = resolveLoginUserForCodeChallenge(request.getLoginType(), account, loginIp, userAgent);
        if (user == null) {
            loginProtectionService.recordFailure(account, loginIp);
            loginAuditService.log(null, null, account, request.getLoginType().toUpperCase(), "FAIL", "用户不存在", loginIp, userAgent);
            throw new BizException(
                    ErrorCode.ACCOUNT_NOT_FOUND,
                    "登录失败，账号不存在: " + account,
                    ErrorCode.LOGIN_FAILED.getDefaultUserMessage()
            );
        }

        if (isUserDisabled(user)) {
            loginProtectionService.recordFailure(account, loginIp);
            loginAuditService.log(user.getId(), null, user.getUsername(), request.getLoginType().toUpperCase(), "FAIL", "账号已禁用", loginIp, userAgent);
            throw new BizException(
                    ErrorCode.ACCOUNT_DISABLED,
                    "登录失败，账号已禁用: " + user.getUsername(),
                    ErrorCode.ACCOUNT_DISABLED.getDefaultUserMessage()
            );
        }

        TenantInfoEntity currentTenant = platformTenant();
        Long tenantId = currentTenant == null ? null : currentTenant.getId();
        LoginCodeChallengeVO challenge = systemVerificationAppService.startLoginCodeChallenge(user, tenantId, request.getLoginType());
        loginAuditService.log(user.getId(), tenantId, user.getUsername(), challenge.getLoginType().toUpperCase(), "PENDING", "LOGIN_CODE_REQUIRED", loginIp, userAgent);
        return challenge;
    }

    public LoginResponseVO completeLoginCodeLogin(LoginCodeCompleteRequest request, String loginIp, String userAgent) {
        com.legendary.invention.saas.modules.system.vo.SystemVO.VerificationVerificationVO verification =
                systemVerificationAppService.completeLoginCodeLogin(request);
        if (!Boolean.TRUE.equals(verification.getVerified())) {
            throw new BizException(ErrorCode.BIZ_ERROR, verification.getMessage() == null ? "验证码校验失败" : verification.getMessage());
        }

        SysUserEntity user = userDomainService.findById(verification.getUserId())
                .orElseThrow(() -> new BizException(ErrorCode.ACCOUNT_NOT_FOUND, "用户不存在"));
        TenantInfoEntity currentTenant = tenantDomainService.findTenantById(verification.getTenantId()).orElse(null);
        AuthSession session = buildNewSession(user, currentTenant == null ? null : currentTenant.getId(), loginIp, userAgent);
        String refreshTokenId = UUID.randomUUID().toString();
        session.setRefreshTokenId(refreshTokenId);

        if (!securitySettingsService.isAllowMultiDeviceLogin()) {
            authSessionStore.revokeUserSessions(user.getId(), true);
        }
        authSessionStore.save(session, true);

        LoginResponseVO response = new LoginResponseVO();
        response.setAccessToken(jwtTokenService.generateAccessToken(session));
        response.setRefreshToken(jwtTokenService.generateRefreshToken(session, refreshTokenId));
        response.setTokenType("Bearer");
        response.setExpiresIn(jwtTokenService.getAccessTokenExpireSeconds());
        response.setUser(toAuthUser(user, currentTenant == null ? null : currentTenant.getId()));
        response.setTenants(List.of());
        response.setCurrentTenant(tenantDomainService.toTenantSummary(currentTenant));
        response.setRequiresCaptcha(Boolean.FALSE);
        loginAuditService.log(
                user.getId(),
                currentTenant == null ? null : currentTenant.getId(),
                user.getUsername(),
                "LOGIN_CODE",
                "SUCCESS",
                null,
                loginIp,
                userAgent
        );
        return response;
    }

    public WechatAuthorizeUrlVO wechatAuthorizeUrl() {
        return wechatLoginService.createAuthorizeUrl();
    }

    public LoginResponseVO wechatLogin(WechatLoginRequest request, String loginIp, String userAgent) {
        WechatLoginService.WechatOAuthUser wechatUser = wechatLoginService.exchangeCode(request.getCode(), request.getState());
        SysUserEntity user = resolveWechatLoginUser(wechatUser);
        if (isUserDisabled(user)) {
            loginAuditService.log(user.getId(), null, user.getUsername(), "WECHAT", "FAIL", "账号已禁用", loginIp, userAgent);
            throw new BizException(
                    ErrorCode.ACCOUNT_DISABLED,
                    "登录失败，账号已禁用: " + user.getUsername(),
                    ErrorCode.ACCOUNT_DISABLED.getDefaultUserMessage()
            );
        }

        TenantInfoEntity currentTenant = platformTenant();
        Long tenantId = currentTenant == null ? null : currentTenant.getId();
        LoginResponseVO response = issueLoginTokens(user, currentTenant, loginIp, userAgent);
        loginAuditService.log(user.getId(), tenantId, user.getUsername(), "WECHAT", "SUCCESS", null, loginIp, userAgent);
        return response;
    }

    private void validateCaptchaIfRequired(LoginRequest request, String account, String loginIp, String userAgent) {
        if (!securitySettingsService.isCaptchaEnabled()) {
            return;
        }

        try {
            captchaService.validateCaptcha(request.getCaptchaId(), request.getCaptchaCode(), request.getCaptchaProof());
        } catch (BizException ex) {
            loginProtectionService.recordFailure(account, loginIp);
            loginAuditService.log(null, null, account, "CAPTCHA", "FAIL", ex.getErrorMessage(), loginIp, userAgent);
            throw ex;
        }
    }

    public LoginResponseVO completeSecondFactorLogin(SecondFactorCompleteRequest request, String loginIp, String userAgent) {
        if (!StringUtils.hasText(request.getFactorCode())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "验证方式不能为空");
        }
        com.legendary.invention.saas.modules.system.vo.SystemVO.VerificationVerificationVO verification = systemVerificationAppService.completeSecondFactorLogin(
                request,
                loginIp,
                userAgent
        );
        if (!Boolean.TRUE.equals(verification.getVerified())) {
            throw new BizException(ErrorCode.BIZ_ERROR, verification.getMessage() == null ? "二次验证失败" : verification.getMessage());
        }
        SysUserEntity user = userDomainService.findById(verification.getUserId())
                .orElseThrow(() -> new BizException(ErrorCode.ACCOUNT_NOT_FOUND, "用户不存在"));
        TenantInfoEntity currentTenant = tenantDomainService.findTenantById(verification.getTenantId()).orElse(null);
        AuthSession session = buildNewSession(user, currentTenant == null ? null : currentTenant.getId(), loginIp, userAgent);
        String refreshTokenId = UUID.randomUUID().toString();
        session.setRefreshTokenId(refreshTokenId);

        if (!securitySettingsService.isAllowMultiDeviceLogin()) {
            authSessionStore.revokeUserSessions(user.getId(), true);
        }
        authSessionStore.save(session, true);

        LoginResponseVO response = new LoginResponseVO();
        response.setAccessToken(jwtTokenService.generateAccessToken(session));
        response.setRefreshToken(jwtTokenService.generateRefreshToken(session, refreshTokenId));
        response.setTokenType("Bearer");
        response.setExpiresIn(jwtTokenService.getAccessTokenExpireSeconds());
        response.setUser(toAuthUser(user, currentTenant == null ? null : currentTenant.getId()));
        response.setTenants(List.of());
        response.setCurrentTenant(tenantDomainService.toTenantSummary(currentTenant));
        response.setRequiresCaptcha(Boolean.FALSE);
        loginAuditService.log(user.getId(), currentTenant == null ? null : currentTenant.getId(), user.getUsername(), "PASSWORD", "SUCCESS", null, loginIp, userAgent);
        return response;
    }

    public void logout(CurrentUser currentUser, String loginIp, String userAgent) {
        if (currentUser.getSessionId() == null) {
            return;
        }
        authSessionStore.findBySessionId(currentUser.getSessionId()).ifPresent(session -> authSessionStore.remove(session, true));
        loginAuditService.log(
                currentUser.getUserId(),
                currentUser.getCurrentTenantId(),
                currentUser.getUsername(),
                "LOGOUT",
                "SUCCESS",
                null,
                loginIp,
                userAgent
        );
    }

    private SysUserEntity resolveLoginUserForPassword(String account, String rawPassword, String loginIp, String userAgent) {
        SysUserEntity user = userDomainService.findLoginUser(account).orElse(null);
        if (user != null) {
            return user;
        }
        if (!shouldAutoRegister(account, "PASSWORD")) {
            return null;
        }
        return registerLoginUser(account, rawPassword, "PASSWORD", loginIp, userAgent);
    }

    private SysUserEntity resolveLoginUserForCodeChallenge(String loginType, String account, String loginIp, String userAgent) {
        SysUserEntity user = userDomainService.findLoginUser(account).orElse(null);
        if (user != null) {
            return user;
        }
        if (!shouldAutoRegister(account, loginType)) {
            return null;
        }
        return registerLoginUser(account, null, loginType, loginIp, userAgent);
    }

    private SysUserEntity registerLoginUser(String account, String rawPassword, String loginType, String loginIp, String userAgent) {
        String normalizedAccount = normalizeRegistrationAccount(account);
        String password = StringUtils.hasText(rawPassword) ? rawPassword : UUID.randomUUID().toString();
        if (StringUtils.hasText(rawPassword)) {
            passwordPolicyService.validatePassword(rawPassword);
        }

        Long tenantId = PLATFORM_TENANT_ID;
        String encodedPassword = passwordEncoder.encode(password);
        String username = normalizedAccount;
        String mobile = isMobileAccount(normalizedAccount) ? normalizedAccount : null;
        String email = isEmailAccount(normalizedAccount) ? normalizedAccount : null;

        try {
            jdbcTemplate.update(
                    """
                            insert into sys_user (
                                username, password_hash, mobile, nickname, real_name, avatar_url, email, birth_month, gender, region,
                                available_time, id_card_number, status,
                                created_by, updated_by, deleted
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                            """,
                    username,
                    encodedPassword,
                    mobile,
                    normalizedAccount,
                    null,
                    null,
                    email,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "ENABLED",
                    0L,
                    0L
            );
        } catch (DuplicateKeyException ex) {
            SysUserEntity existing = userDomainService.findLoginUser(normalizedAccount).orElse(null);
            if (existing != null) {
                return existing;
            }
            throw ex;
        }

        SysUserEntity user = userDomainService.findLoginUser(normalizedAccount)
                .orElseThrow(() -> new BizException(ErrorCode.SYSTEM_ERROR, "自动注册用户失败"));
        upsertUserTenantRelation(user.getId(), tenantId, true, 0L);
        grantDefaultLoginRole(user.getId(), tenantId, 0L);
        return user;
    }

    private SysUserEntity resolveWechatLoginUser(WechatLoginService.WechatOAuthUser wechatUser) {
        SysUserEntity boundUser = findWechatBoundUser(wechatUser.unionid(), wechatUser.openid());
        if (boundUser != null) {
            upsertWechatBinding(boundUser.getId(), wechatUser);
            return boundUser;
        }

        SysUserEntity user = registerWechatUser(wechatUser);
        upsertWechatBinding(user.getId(), wechatUser);
        return user;
    }

    private SysUserEntity findWechatBoundUser(String unionid, String openid) {
        List<SysUserEntity> users = jdbcTemplate.query(
                """
                        select u.id, u.username, u.nickname, u.real_name, u.avatar_url, u.birth_month, u.gender, u.region,
                               u.available_time, u.id_card_number, u.password_hash, u.mobile, u.email, u.status, u.deleted
                        from sys_user_wechat_binding b
                        join sys_user u on u.id = b.user_id and u.deleted = 0
                        where b.deleted = 0
                          and ((? <> '' and b.unionid = ?) or b.openid = ?)
                        order by case when ? <> '' and b.unionid = ? then 0 else 1 end, b.id desc
                        limit 1
                        """,
                (rs, rowNum) -> {
                    SysUserEntity user = new SysUserEntity();
                    user.setId(rs.getLong("id"));
                    user.setUsername(rs.getString("username"));
                    user.setNickname(rs.getString("nickname"));
                    user.setRealName(rs.getString("real_name"));
                    user.setAvatarUrl(rs.getString("avatar_url"));
                    user.setBirthMonth(rs.getString("birth_month"));
                    user.setGender(rs.getString("gender"));
                    user.setRegion(rs.getString("region"));
                    user.setAvailableTime(rs.getString("available_time"));
                    user.setIdCardNumber(rs.getString("id_card_number"));
                    user.setPasswordHash(rs.getString("password_hash"));
                    user.setMobile(rs.getString("mobile"));
                    user.setEmail(rs.getString("email"));
                    user.setStatus(rs.getString("status"));
                    user.setDeleted(rs.getInt("deleted"));
                    return user;
                },
                defaultIfBlank(wechatUserId(unionid), ""),
                defaultIfBlank(wechatUserId(unionid), ""),
                openid,
                defaultIfBlank(wechatUserId(unionid), ""),
                defaultIfBlank(wechatUserId(unionid), "")
        );
        return users.isEmpty() ? null : users.get(0);
    }

    private SysUserEntity registerWechatUser(WechatLoginService.WechatOAuthUser wechatUser) {
        Long tenantId = PLATFORM_TENANT_ID;
        String username = nextWechatUsername(wechatUser);
        String password = UUID.randomUUID().toString();

        jdbcTemplate.update(
                """
                        insert into sys_user (
                            username, password_hash, mobile, nickname, real_name, avatar_url, email, birth_month, gender, region,
                            available_time, id_card_number, status,
                            created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                username,
                passwordEncoder.encode(password),
                null,
                "微信用户",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "ENABLED",
                0L,
                0L
        );

        SysUserEntity user = userDomainService.findLoginUser(username)
                .orElseThrow(() -> new BizException(ErrorCode.SYSTEM_ERROR, "微信登录自动注册用户失败"));
        upsertUserTenantRelation(user.getId(), tenantId, true, 0L);
        grantDefaultLoginRole(user.getId(), tenantId, 0L);
        return user;
    }

    private String nextWechatUsername(WechatLoginService.WechatOAuthUser wechatUser) {
        String sourceId = StringUtils.hasText(wechatUser.unionid()) ? wechatUser.unionid() : wechatUser.openid();
        String normalized = sourceId == null ? UUID.randomUUID().toString() : sourceId.replaceAll("[^A-Za-z0-9_]", "");
        if (normalized.length() > 24) {
            normalized = normalized.substring(0, 24);
        }
        String baseUsername = "wx_" + (StringUtils.hasText(normalized) ? normalized : UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        String username = baseUsername;
        int suffix = 1;
        while (userDomainService.findLoginUser(username).isPresent()) {
            username = baseUsername + "_" + suffix;
            suffix++;
        }
        return username;
    }

    private void upsertWechatBinding(Long userId, WechatLoginService.WechatOAuthUser wechatUser) {
        jdbcTemplate.update(
                """
                        insert into sys_user_wechat_binding (
                            user_id, openid, unionid, scope, created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, 0)
                        on duplicate key update user_id = values(user_id),
                                                unionid = values(unionid),
                                                scope = values(scope),
                                                updated_by = values(updated_by),
                                                updated_at = current_timestamp,
                                                deleted = 0
                        """,
                userId,
                wechatUser.openid(),
                StringUtils.hasText(wechatUser.unionid()) ? wechatUser.unionid() : null,
                wechatUser.scope(),
                0L,
                0L
        );
    }

    private String wechatUserId(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private void upsertUserTenantRelation(Long userId, Long tenantId, boolean isDefault, Long operatorId) {
        jdbcTemplate.update(
                """
                        insert into sys_user_tenant (tenant_id, user_id, is_default, status, created_by, updated_by, deleted)
                        values (?, ?, ?, 'ENABLED', ?, ?, 0)
                        on duplicate key update is_default = values(is_default),
                                                 status = values(status),
                                                 updated_by = values(updated_by),
                                                 updated_at = current_timestamp,
                                                 deleted = 0
                        """,
                tenantId,
                userId,
                isDefault ? 1 : 0,
                operatorId,
                operatorId
        );
    }

    private void grantDefaultLoginRole(Long userId, Long tenantId, Long operatorId) {
        String roleCode = resolveDefaultRegistrationRoleCode(tenantId);
        Long roleId = jdbcTemplate.query(
                """
                        select id
                        from sys_role
                        where tenant_id = ? and role_code = ? and deleted = 0
                        order by id desc
                        limit 1
                        """,
                rs -> rs.next() ? rs.getLong("id") : null,
                tenantId,
                roleCode
        );
        if (roleId == null) {
            return;
        }

        jdbcTemplate.update(
                """
                        insert into sys_user_role (tenant_id, user_id, role_id, created_by, updated_by, deleted)
                        values (?, ?, ?, ?, ?, 0)
                        on duplicate key update updated_by = values(updated_by), updated_at = current_timestamp, deleted = 0
                        """,
                tenantId,
                userId,
                roleId,
                operatorId,
                operatorId
        );
    }

    private String resolveDefaultRegistrationRoleCode(Long tenantId) {
        try {
            String configuredRoleCode = jdbcTemplate.queryForObject(
                    """
                            select config_value
                            from sys_config
                            where deleted = 0
                              and config_scope = 'PLATFORM'
                              and config_key = ?
                              and (tenant_id = ? or tenant_id is null)
                            order by case when tenant_id = ? then 0 else 1 end, id desc
                            limit 1
                            """,
                    String.class,
                    DEFAULT_REGISTRATION_ROLE_CODE_KEY,
                    tenantId,
                    tenantId
            );
            return StringUtils.hasText(configuredRoleCode) ? configuredRoleCode.trim() : DEFAULT_LOGIN_ROLE_CODE;
        } catch (EmptyResultDataAccessException ex) {
            return DEFAULT_LOGIN_ROLE_CODE;
        }
    }

    private boolean shouldAutoRegister(String account, String loginType) {
        if (!StringUtils.hasText(account)) {
            return false;
        }
        String normalizedLoginType = loginType == null ? "" : loginType.trim().toLowerCase(Locale.ROOT);
        if ("sms".equals(normalizedLoginType)) {
            return isMobileAccount(account);
        }
        if ("email".equals(normalizedLoginType)) {
            return isEmailAccount(account);
        }
        return isMobileAccount(account) || isEmailAccount(account);
    }

    private String normalizeRegistrationAccount(String account) {
        String normalizedAccount = account == null ? "" : account.trim();
        if (isEmailAccount(normalizedAccount)) {
            return normalizedAccount.toLowerCase(Locale.ROOT);
        }
        return normalizedAccount;
    }

    private boolean isMobileAccount(String account) {
        return StringUtils.hasText(account) && MOBILE_PATTERN.matcher(account.trim()).matches();
    }

    private boolean isEmailAccount(String account) {
        return StringUtils.hasText(account) && EMAIL_PATTERN.matcher(account.trim()).matches();
    }

    private LoginResponseVO issueLoginTokens(SysUserEntity user, TenantInfoEntity currentTenant, String loginIp, String userAgent) {
        Long tenantId = currentTenant == null ? null : currentTenant.getId();
        AuthSession session = buildNewSession(user, tenantId, loginIp, userAgent);
        String refreshTokenId = UUID.randomUUID().toString();
        session.setRefreshTokenId(refreshTokenId);

        if (!securitySettingsService.isAllowMultiDeviceLogin()) {
            authSessionStore.revokeUserSessions(user.getId(), true);
        }
        authSessionStore.save(session, true);

        LoginResponseVO response = new LoginResponseVO();
        response.setAccessToken(jwtTokenService.generateAccessToken(session));
        response.setRefreshToken(jwtTokenService.generateRefreshToken(session, refreshTokenId));
        response.setTokenType("Bearer");
        response.setExpiresIn(jwtTokenService.getAccessTokenExpireSeconds());
        response.setUser(toAuthUser(user, tenantId));
        response.setTenants(List.of());
        response.setCurrentTenant(tenantDomainService.toTenantSummary(currentTenant));
        response.setRequiresCaptcha(Boolean.FALSE);
        return response;
    }

    public RefreshTokenResponseVO refreshToken(RefreshTokenRequest request) {
        TokenClaims tokenClaims = jwtTokenService.parseToken(request.getRefreshToken());
        if (tokenClaims.getTokenType() != TokenType.REFRESH) {
            throw new BizException(
                    ErrorCode.SESSION_EXPIRED,
                    "refreshToken非法",
                    ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
            );
        }

        AuthSession session = authSessionStore.findBySessionId(tokenClaims.getSessionId())
                .orElseThrow(() -> new BizException(
                        ErrorCode.SESSION_EXPIRED,
                        "刷新失败，会话已失效",
                        ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
                ));

        validateSessionForRefresh(session, tokenClaims);

        String newRefreshTokenId = UUID.randomUUID().toString();
        session.setRefreshTokenId(newRefreshTokenId);
        session.setExpireTime(jwtTokenService.createRefreshTokenExpireAt());
        session.setLastActivityAt(Instant.now());
        authSessionStore.save(session, false);

        RefreshTokenResponseVO response = new RefreshTokenResponseVO();
        response.setAccessToken(jwtTokenService.generateAccessToken(session));
        response.setRefreshToken(jwtTokenService.generateRefreshToken(session, newRefreshTokenId));
        response.setTokenType("Bearer");
        response.setExpiresIn(jwtTokenService.getAccessTokenExpireSeconds());
        response.setSessionVersion(session.getSessionVersion());
        response.setPermissionsVersion(resolvePermissionSnapshot(session.getCurrentTenantId(), session.getUserId(), session.getSimulatedRoleId()).getVersion());
        return response;
    }

    public CurrentUserVO currentUser(CurrentUser currentUser) {
        SysUserEntity user = userDomainService.findById(currentUser.getUserId())
                .orElseThrow(() -> new BizException(
                        ErrorCode.SESSION_EXPIRED,
                        "会话关联用户不存在: " + currentUser.getUserId(),
                        ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
                ));

        Long tenantId = currentTenantId(currentUser);
        TenantSummaryVO currentTenant = tenantDomainService.findTenantById(tenantId)
                .map(tenantDomainService::toTenantSummary)
                .orElse(null);
        PermissionSnapshotService.PermissionSnapshot snapshot = resolvePermissionSnapshot(tenantId, currentUser.getUserId(), currentUser.getSimulatedRoleId());

        CurrentUserVO response = new CurrentUserVO();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setRealName(user.getRealName());
        response.setAvatarUrl(user.getAvatarUrl());
        response.setMobile(user.getMobile());
        response.setEmail(user.getEmail());
        response.setBirthMonth(user.getBirthMonth());
        response.setGender(user.getGender());
        response.setRegion(user.getRegion());
        response.setAvailableTime(user.getAvailableTime());
        response.setIdCardNumber(user.getIdCardNumber());
        response.setLocale(resolveLocale(tenantId, user.getId()));
        response.setCurrentTenant(currentTenant);
        response.setSimulatedRoleId(currentUser.getSimulatedRoleId());
        response.setAvailableRoles(listAvailableRoles(currentUser.getUserId(), tenantId));
        response.setSessionId(currentUser.getSessionId());
        response.setPermissionsVersion(snapshot.getVersion());
        response.setSessionVersion(currentUser.getSessionVersion());
        response.setPermissions(snapshot.getPermissionList());
        return response;
    }

    public CurrentUserVO updateSimulatedRole(CurrentUser currentUser, SimulatedRoleRequest request) {
        Long tenantId = currentTenantId(currentUser);
        Long roleId = request.getRoleId();
        if (roleId != null) {
            boolean roleExists = listAvailableRoles(currentUser.getUserId(), tenantId).stream()
                    .anyMatch(role -> role.getId() != null && role.getId().equals(roleId));
            if (!roleExists) {
                throw new BizException(ErrorCode.FORBIDDEN, "当前账号没有该角色权限");
            }
        }

        AuthSession session = authSessionStore.findBySessionId(currentUser.getSessionId())
                .orElseThrow(() -> new BizException(ErrorCode.SESSION_EXPIRED, "会话不存在或已失效"));
        session.setSimulatedRoleId(roleId);
        authSessionStore.save(session, true);
        currentUser.setSimulatedRoleId(roleId);
        return currentUser(currentUser);
    }

    private void validateSessionForRefresh(AuthSession session, TokenClaims tokenClaims) {
        if (!session.getUserId().equals(tokenClaims.getUserId())) {
            invalidateSession(session, "refreshToken与会话不匹配");
        }
        if (session.getSessionVersion() == null || !session.getSessionVersion().equals(tokenClaims.getSessionVersion())) {
            invalidateSession(session, "会话版本已变更，请重新登录");
        }
        if (!securitySettingsService.isAllowMultiDeviceLogin()) {
            String latestSessionId = authSessionStore.findLatestActiveUserSessionId(session.getUserId()).orElse(null);
            if (latestSessionId == null || !session.getSessionId().equals(latestSessionId)) {
                invalidateSession(session, "当前账号已在其他设备登录，请重新登录");
            }
        }
        if (session.getRefreshTokenId() == null || !session.getRefreshTokenId().equals(tokenClaims.getTokenId())) {
            invalidateSession(session, "refreshToken已失效");
        }
        if (jwtTokenService.isExpired(session.getExpireTime())) {
            invalidateSession(session, "会话已过期，请重新登录");
        }
        Instant lastActivityAt = session.getLastActivityAt() != null ? session.getLastActivityAt() : session.getLoginTime();
        long idleTimeoutSeconds = jwtTokenService.getIdleTimeoutSeconds();
        if (lastActivityAt != null && idleTimeoutSeconds > 0) {
            Duration idleDuration = Duration.between(lastActivityAt, Instant.now());
            if (idleDuration.compareTo(Duration.ofSeconds(idleTimeoutSeconds)) >= 0) {
                invalidateSession(session, "会话空闲超时，请重新登录");
            }
        }
    }

    private void invalidateSession(AuthSession session, String message) {
        authSessionStore.remove(session, true);
        throw new BizException(
                ErrorCode.SESSION_EXPIRED,
                message,
                ErrorCode.SESSION_EXPIRED.getDefaultUserMessage()
        );
    }

    private boolean isUserDisabled(SysUserEntity user) {
        return user.getStatus() != null && !"ENABLED".equalsIgnoreCase(user.getStatus());
    }

    private void validateCaptchaIfRequired(LoginRequest request) {
        SecuritySettingsService.SecuritySettingsSnapshot settings = securitySettingsService.loadSettings();
        if (!settings.isCaptchaEnabled()) {
            return;
        }
        captchaService.validateCaptcha(request.getCaptchaId(), request.getCaptchaCode(), request.getCaptchaProof());
    }

    private TenantInfoEntity platformTenant() {
        return tenantDomainService.findTenantById(PLATFORM_TENANT_ID).orElse(null);
    }

    private AuthSession buildNewSession(SysUserEntity user, Long currentTenantId, String loginIp, String userAgent) {
        Instant now = Instant.now();
        AuthSession session = new AuthSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUserId(user.getId());
        session.setUsername(user.getUsername());
        session.setCurrentTenantId(currentTenantId);
        session.setLoginTime(now);
        session.setLastActivityAt(now);
        session.setExpireTime(now.plusSeconds(jwtTokenService.getRefreshTokenExpireSeconds()));
        session.setSessionVersion(1);
        session.setClientType("WEB");
        session.setLoginIp(loginIp);
        session.setUserAgent(userAgent);
        return session;
    }

    private AuthUserVO toAuthUser(SysUserEntity user, Long tenantId) {
        AuthUserVO authUserVO = new AuthUserVO();
        authUserVO.setUserId(user.getId());
        authUserVO.setUsername(user.getUsername());
        authUserVO.setNickname(user.getNickname());
        authUserVO.setRealName(user.getRealName());
        authUserVO.setAvatarUrl(user.getAvatarUrl());
        authUserVO.setMobile(user.getMobile());
        authUserVO.setEmail(user.getEmail());
        authUserVO.setBirthMonth(user.getBirthMonth());
        authUserVO.setGender(user.getGender());
        authUserVO.setRegion(user.getRegion());
        authUserVO.setAvailableTime(user.getAvailableTime());
        authUserVO.setIdCardNumber(user.getIdCardNumber());
        authUserVO.setLocale(resolveLocale(tenantId, user.getId()));
        return authUserVO;
    }

    private PermissionSnapshotService.PermissionSnapshot resolvePermissionSnapshot(Long tenantId, Long userId, Long simulatedRoleId) {
        if (tenantId == null || userId == null) {
            return PermissionSnapshotService.PermissionSnapshot.empty();
        }
        if (simulatedRoleId != null) {
            return permissionSnapshotService.loadRoleSnapshot(tenantId, simulatedRoleId);
        }
        return permissionSnapshotService.loadSnapshot(tenantId, userId);
    }

    private List<CurrentUserVO.RoleOptionVO> listAvailableRoles(Long userId, Long tenantId) {
        if (userId == null || tenantId == null) {
            return List.of();
        }

        return jdbcTemplate.query(
                """
                        select r.id as id,
                               r.role_code as roleCode,
                               r.role_name as roleName,
                               r.role_type as roleType,
                               count(rp.permission_key) as permissionCount
                        from sys_user_role ur
                        join sys_role r on r.id = ur.role_id and r.tenant_id = ur.tenant_id and r.deleted = 0
                        left join sys_role_permission rp on rp.role_id = r.id and rp.tenant_id = r.tenant_id and rp.deleted = 0
                        where ur.tenant_id = ? and ur.user_id = ? and ur.deleted = 0
                        group by r.id, r.role_code, r.role_name, r.role_type
                        order by r.id desc
                        """,
                (rs, rowNum) -> {
                    CurrentUserVO.RoleOptionVO role = new CurrentUserVO.RoleOptionVO();
                    role.setId(rs.getLong("id"));
                    role.setRoleCode(rs.getString("roleCode"));
                    role.setRoleName(rs.getString("roleName"));
                    role.setRoleType(rs.getString("roleType"));
                    role.setPermissionCount(rs.getInt("permissionCount"));
                    return role;
                },
                tenantId,
                userId
        );
    }

    private String resolveLocale(Long tenantId, Long userId) {
        if (tenantId == null || userId == null) {
            return "zh-CN";
        }

        try {
            String locale = jdbcTemplate.queryForObject(
                    """
                            select locale
                            from sys_user_tenant_profile
                            where tenant_id = ? and user_id = ? and deleted = 0
                            """,
                    String.class,
                    tenantId,
                    userId
            );
            return normalizeLocale(locale);
        } catch (EmptyResultDataAccessException ex) {
            return "zh-CN";
        }
    }

    private String normalizeLocale(String locale) {
        if (!StringUtils.hasText(locale)) {
            return "zh-CN";
        }

        String normalized = locale.trim();
        if ("zh".equalsIgnoreCase(normalized) || "zh-CN".equalsIgnoreCase(normalized)) {
            return "zh-CN";
        }
        if ("en".equalsIgnoreCase(normalized) || "en-US".equalsIgnoreCase(normalized)) {
            return "en-US";
        }
        return "zh-CN";
    }

    private List<LoginResponseVO.SecondFactorOptionVO> collectSecondFactorOptions(SysUserEntity user, Long tenantId) {
        if (tenantId == null) {
            return List.of();
        }
        List<LoginResponseVO.SecondFactorOptionVO> result = new ArrayList<>();
        systemVerificationAppService.collectSecondFactorOptions(user, tenantId).forEach(result::add);
        return result;
    }

    private Long currentTenantId(CurrentUser currentUser) {
        if (currentUser == null || currentUser.getCurrentTenantId() == null) {
            return PLATFORM_TENANT_ID;
        }
        return currentUser.getCurrentTenantId();
    }
}

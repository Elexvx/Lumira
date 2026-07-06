package com.lumira.saas.modules.account.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.saas.infrastructure.persistence.mybatis.MyBatisQueryOperations;
import com.lumira.saas.modules.account.vo.AccountActivationVO;
import com.lumira.saas.modules.iam.service.IamUserService;
import com.lumira.saas.modules.system.support.SmtpMailService;
import com.lumira.saas.infrastructure.security.service.PasswordPolicyService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@ConditionalOnLumiraControlPlaneEnabled
public class AccountActivationService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final long TOKEN_EXPIRE_HOURS = 72L;
    private static final int TOKEN_LENGTH = 43;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private final MyBatisQueryOperations jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyService passwordPolicyService;
    private final IamUserService iamUserService;
    private final SmtpMailService smtpMailService;

    public AccountActivationService(
            MyBatisQueryOperations jdbcTemplate,
            PasswordEncoder passwordEncoder,
            PasswordPolicyService passwordPolicyService,
            IamUserService iamUserService,
            SmtpMailService smtpMailService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyService = passwordPolicyService;
        this.iamUserService = iamUserService;
        this.smtpMailService = smtpMailService;
    }

    @Transactional
    public String createActivationToken(Long userId, Long expertId, Long operatorUserId, String operatorUserUuid) {
        Long trustedUserId = requirePositiveId(userId, "activation user is required");
        requireTrustedOperatorArguments(operatorUserId, operatorUserUuid);
        String trustedUserUuid = resolveActivationUserUuid(trustedUserId);
        Long operatorId = requireTrustedOperator(operatorUserId, operatorUserUuid);
        Long trustedExpertId = expertId == null ? null : requirePositiveId(expertId, "activation expert is invalid");
        String token = randomToken();
        String tokenHash = hashToken(token);
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        update sys_account_activation_token
                        set consumed_at = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where user_id = ? and user_uuid = ? and consumed_at is null and deleted = 0
                        """,
                now,
                operatorId,
                operatorUserUuid.trim(),
                now,
                trustedUserId,
                trustedUserUuid
        );
        int inserted = jdbcTemplate.update(
                """
                        insert into sys_account_activation_token (
                            token_hash, user_id, user_uuid, expert_id, expires_at,
                            created_by, created_by_uuid, updated_by, updated_by_uuid, deleted
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """,
                tokenHash,
                trustedUserId,
                trustedUserUuid,
                trustedExpertId,
                now.plusHours(TOKEN_EXPIRE_HOURS),
                operatorId,
                operatorUserUuid.trim(),
                operatorId,
                operatorUserUuid.trim()
        );
        if (inserted != 1) {
            throw biz(ErrorCode.BIZ_ERROR, "Activation token changed, please retry");
        }
        return token;
    }

    public void sendActivationEmail(String email, String username, String token) {
        if (!StringUtils.hasText(email)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Expert email is required before account activation");
        }
        if (!smtpMailService.isConfigured()) {
            throw biz(ErrorCode.BIZ_ERROR, "SMTP is not configured");
        }
        String link = activationBaseUrl() + "?token=" + token;
        smtpMailService.sendPlainText(
                email,
                "Lumira account activation",
                "Your Lumira expert account has been approved.\n\n"
                        + "Username: " + username + "\n"
                        + "Activation link: " + link + "\n\n"
                        + "The link expires in " + TOKEN_EXPIRE_HOURS + " hours."
        );
    }

    public AccountActivationVO.TokenInfo verify(String token) {
        AccountActivationVO.TokenInfo info = new AccountActivationVO.TokenInfo();
        String normalizedToken = normalizePublicToken(token);
        if (normalizedToken == null) {
            info.setValid(false);
            info.setReason("Token is invalid, expired, or already used");
            return info;
        }
        TokenRow row = findValidToken(normalizedToken);
        if (row == null) {
            info.setValid(false);
            info.setReason("Token is invalid, expired, or already used");
            return info;
        }
        info.setValid(true);
        info.setUsername(row.username());
        info.setEmail(row.email());
        return info;
    }

    @Transactional
    public boolean complete(String token, String password) {
        String normalizedToken = normalizePublicToken(token);
        if (normalizedToken == null) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Token is invalid, expired, or already used");
        }
        passwordPolicyService.validatePassword(password);
        TokenRow row = findValidToken(normalizedToken);
        if (row == null) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Token is invalid, expired, or already used");
        }
        String passwordHash = passwordEncoder.encode(password);
        LocalDateTime now = LocalDateTime.now();
        int consumed = jdbcTemplate.update(
                """
                        update sys_account_activation_token
                        set consumed_at = ?, updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ?
                          and token_hash = ?
                          and user_id = ?
                          and user_uuid = ?
                          and consumed_at is null
                          and deleted = 0
                        """,
                now,
                row.userId(),
                row.userUuid(),
                now,
                row.id(),
                row.tokenHash(),
                row.userId(),
                row.userUuid()
        );
        if (consumed == 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Token is invalid, expired, or already used");
        }
        int userUpdated = jdbcTemplate.update(
                """
                        update sys_user
                        set password_hash = ?, status = 'ENABLED', updated_by = ?, updated_by_uuid = ?, updated_at = ?
                        where id = ?
                          and uuid = ?
                          and deleted = 0
                          and exists (
                              select 1
                              from sys_account_activation_token t
                              where t.id = ?
                                and t.token_hash = ?
                                and t.user_id = sys_user.id
                                and t.user_uuid = sys_user.uuid
                                and t.consumed_at = ?
                                and t.deleted = 0
                          )
                        """,
                passwordHash,
                row.userId(),
                row.userUuid(),
                now,
                row.userId(),
                row.userUuid(),
                row.id(),
                row.tokenHash(),
                now
        );
        if (userUpdated <= 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Activation user changed, please retry");
        }
        iamUserService.upsertPasswordCredential(row.userId(), row.userUuid(), passwordHash);
        if (row.expertId() != null) {
            int expertUpdated = jdbcTemplate.update(
                    """
                            update aiadc_expert
                            set initial_password_reset_required = 0,
                                account_status = 'ENABLED',
                                updated_by = ?,
                                updated_by_uuid = ?,
                                updated_at = ?
                            where id = ?
                              and user_id = ?
                              and user_uuid = ?
                              and deleted = 0
                            """,
                    row.userId(),
                    row.userUuid(),
                    now,
                    row.expertId(),
                    row.userId(),
                    row.userUuid()
            );
            if (expertUpdated <= 0) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Activation expert changed, please retry");
            }
        }
        return true;
    }

    private TokenRow findValidToken(String token) {
        List<TokenRow> rows = jdbcTemplate.query(
                """
                        select t.id, t.token_hash as tokenHash, t.user_id as userId, t.user_uuid as userUuid,
                               t.expert_id as expertId, u.username, u.email
                        from sys_account_activation_token t
                        join sys_user u on u.id = t.user_id and u.uuid = t.user_uuid and u.deleted = 0
                        where t.token_hash = ? and t.consumed_at is null and t.expires_at > ? and t.deleted = 0
                        limit 1
                        """,
                (rs, rowNum) -> new TokenRow(
                        rs.getLong("id"),
                        rs.getString("tokenHash"),
                        rs.getLong("userId"),
                        rs.getString("userUuid"),
                        rs.getObject("expertId", Long.class),
                        rs.getString("username"),
                        rs.getString("email")
                ),
                hashToken(token),
                LocalDateTime.now()
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String resolveActivationUserUuid(Long userId) {
        String userUuid = jdbcTemplate.queryForObject(
                "select uuid from sys_user where id = ? and deleted = 0 limit 1",
                String.class,
                userId
        );
        if (!StringUtils.hasText(userUuid)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "activation user is invalid");
        }
        return userUuid.trim();
    }

    private String activationBaseUrl() {
        try {
            String configured = jdbcTemplate.queryForObject(
                    """
                            select config_value from sys_config
                            where config_key = 'account.activation.url' and config_scope = 'PLATFORM' and deleted = 0
                            limit 1
                            """,
                    String.class
            );
            if (StringUtils.hasText(configured)) {
                return configured.trim();
            }
        } catch (RuntimeException ignored) {
            // Fall through to local default.
        }
        return "http://localhost:8000/account-activation";
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizePublicToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String normalized = token.trim();
        return normalized.length() == TOKEN_LENGTH && TOKEN_PATTERN.matcher(normalized).matches() ? normalized : null;
    }

    private Long requireTrustedOperator(Long operatorUserId, String operatorUserUuid) {
        requireTrustedOperatorArguments(operatorUserId, operatorUserUuid);
        String normalizedOperatorUserUuid = operatorUserUuid.trim();
        String resolvedUuid = jdbcTemplate.queryForObject(
                "select uuid from sys_user where id = ? and deleted = 0 limit 1",
                String.class,
                operatorUserId
        );
        if (!StringUtils.hasText(resolvedUuid) || !resolvedUuid.trim().equals(normalizedOperatorUserUuid)) {
            throw new IllegalArgumentException("trusted account activation operator identity mismatch");
        }
        String resolvedStatus = jdbcTemplate.queryForObject(
                "select status from sys_user where id = ? and uuid = ? and deleted = 0 limit 1",
                String.class,
                operatorUserId,
                normalizedOperatorUserUuid
        );
        if (!StringUtils.hasText(resolvedStatus) || !"ENABLED".equalsIgnoreCase(resolvedStatus.trim())) {
            throw new IllegalArgumentException("trusted account activation operator is disabled");
        }
        return operatorUserId;
    }

    private void requireTrustedOperatorArguments(Long operatorUserId, String operatorUserUuid) {
        if (operatorUserId == null || operatorUserId <= 0) {
            throw new IllegalArgumentException("trusted account activation operator is required");
        }
        if (!StringUtils.hasText(operatorUserUuid)) {
            throw new IllegalArgumentException("trusted account activation operator uuid is required");
        }
    }

    private Long requirePositiveId(Long id, String message) {
        if (id == null || id <= 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, message);
        }
        return id;
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash activation token", exception);
        }
    }

    private static BizException biz(ErrorCode code, String message) {
        return new BizException(code, message, message);
    }

    private record TokenRow(Long id, String tokenHash, Long userId, String userUuid, Long expertId, String username, String email) {}
}

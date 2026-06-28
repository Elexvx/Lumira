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

@Service
@ConditionalOnLumiraControlPlaneEnabled
public class AccountActivationService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final long TOKEN_EXPIRE_HOURS = 72L;

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
    public String createActivationToken(Long userId, Long expertId, Long operatorUserId) {
        String token = randomToken();
        String tokenHash = hashToken(token);
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        update sys_account_activation_token
                        set consumed_at = ?, updated_by = ?, updated_at = ?
                        where user_id = ? and consumed_at is null and deleted = 0
                        """,
                now,
                operatorUserId == null ? 0L : operatorUserId,
                now,
                userId
        );
        jdbcTemplate.update(
                """
                        insert into sys_account_activation_token (
                            token_hash, user_id, expert_id, expires_at, created_by, updated_by, deleted
                        ) values (?, ?, ?, ?, ?, ?, 0)
                        """,
                tokenHash,
                userId,
                expertId,
                now.plusHours(TOKEN_EXPIRE_HOURS),
                operatorUserId == null ? 0L : operatorUserId,
                operatorUserId == null ? 0L : operatorUserId
        );
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
        TokenRow row = findValidToken(token);
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
        passwordPolicyService.validatePassword(password);
        TokenRow row = findValidToken(token);
        if (row == null) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Token is invalid, expired, or already used");
        }
        String passwordHash = passwordEncoder.encode(password);
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "update sys_user set password_hash = ?, status = 'ENABLED', updated_by = ?, updated_at = ? where id = ? and deleted = 0",
                passwordHash,
                row.userId(),
                now,
                row.userId()
        );
        iamUserService.upsertPasswordCredential(row.userId(), passwordHash);
        jdbcTemplate.update(
                "update sys_account_activation_token set consumed_at = ?, updated_by = ?, updated_at = ? where id = ? and deleted = 0",
                now,
                row.userId(),
                now,
                row.id()
        );
        if (row.expertId() != null) {
            jdbcTemplate.update(
                    "update aiadc_expert set initial_password_reset_required = 0, account_status = 'ENABLED', updated_by = ?, updated_at = ? where id = ? and deleted = 0",
                    row.userId(),
                    now,
                    row.expertId()
            );
        }
        return true;
    }

    private TokenRow findValidToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        List<TokenRow> rows = jdbcTemplate.query(
                """
                        select t.id, t.user_id as userId, t.expert_id as expertId, u.username, u.email
                        from sys_account_activation_token t
                        join sys_user u on u.id = t.user_id and u.deleted = 0
                        where t.token_hash = ? and t.consumed_at is null and t.expires_at > ? and t.deleted = 0
                        limit 1
                        """,
                (rs, rowNum) -> new TokenRow(
                        rs.getLong("id"),
                        rs.getLong("userId"),
                        rs.getObject("expertId", Long.class),
                        rs.getString("username"),
                        rs.getString("email")
                ),
                hashToken(token),
                LocalDateTime.now()
        );
        return rows.isEmpty() ? null : rows.get(0);
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

    private record TokenRow(Long id, Long userId, Long expertId, String username, String email) {}
}

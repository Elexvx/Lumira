package com.lumira.saas.modules.account.app;

import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.runtime.ConditionalOnLumiraControlPlaneEnabled;
import com.lumira.saas.modules.account.repository.AccountActivationRepository;
import com.lumira.saas.modules.account.repository.AccountActivationRepository.TokenRecord;
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
import java.util.regex.Pattern;

@Service
@ConditionalOnLumiraControlPlaneEnabled
public class AccountActivationService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final long TOKEN_EXPIRE_HOURS = 72L;
    private static final int TOKEN_LENGTH = 43;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private final AccountActivationRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyService passwordPolicyService;
    private final IamUserService iamUserService;
    private final SmtpMailService smtpMailService;

    public AccountActivationService(
            AccountActivationRepository repository,
            PasswordEncoder passwordEncoder,
            PasswordPolicyService passwordPolicyService,
            IamUserService iamUserService,
            SmtpMailService smtpMailService
    ) {
        this.repository = repository;
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
        repository.invalidateOpenTokens(trustedUserId, trustedUserUuid, operatorId, operatorUserUuid.trim(), now);
        int inserted = repository.insertToken(tokenHash, trustedUserId, trustedUserUuid, trustedExpertId,
                now.plusHours(TOKEN_EXPIRE_HOURS), operatorId, operatorUserUuid.trim());
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
        TokenRecord row = findValidToken(normalizedToken);
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
        TokenRecord row = findValidToken(normalizedToken);
        if (row == null) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Token is invalid, expired, or already used");
        }
        String passwordHash = passwordEncoder.encode(password);
        LocalDateTime now = LocalDateTime.now();
        int consumed = repository.consumeToken(row, now);
        if (consumed == 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Token is invalid, expired, or already used");
        }
        int userUpdated = repository.activateUser(row, passwordHash, now);
        if (userUpdated <= 0) {
            throw biz(ErrorCode.VALIDATION_ERROR, "Activation user changed, please retry");
        }
        iamUserService.upsertPasswordCredential(row.userId(), row.userUuid(), passwordHash);
        if (row.expertId() != null) {
            int expertUpdated = repository.activateExpert(row, now);
            if (expertUpdated <= 0) {
                throw biz(ErrorCode.VALIDATION_ERROR, "Activation expert changed, please retry");
            }
        }
        return true;
    }

    private TokenRecord findValidToken(String token) {
        return repository.findValidToken(hashToken(token), LocalDateTime.now()).orElse(null);
    }

    private String resolveActivationUserUuid(Long userId) {
        String userUuid = repository.findUser(userId).map(AccountActivationRepository.UserIdentity::uuid).orElse(null);
        if (!StringUtils.hasText(userUuid)) {
            throw biz(ErrorCode.VALIDATION_ERROR, "activation user is invalid");
        }
        return userUuid.trim();
    }

    private String activationBaseUrl() {
        try {
            String configured = repository.findPlatformConfig("account.activation.url").orElse(null);
            if (StringUtils.hasText(configured)) {
                return configured.trim();
            }
        } catch (BizException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw biz(ErrorCode.DEPENDENCY_UNAVAILABLE, "Account activation URL configuration is unavailable");
        }
        throw biz(ErrorCode.BIZ_ERROR, "Account activation URL is not configured");
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
        AccountActivationRepository.UserIdentity identity = repository.findUser(operatorUserId).orElse(null);
        String resolvedUuid = identity == null ? null : identity.uuid();
        if (!StringUtils.hasText(resolvedUuid) || !resolvedUuid.trim().equals(normalizedOperatorUserUuid)) {
            throw new IllegalArgumentException("trusted account activation operator identity mismatch");
        }
        String resolvedStatus = identity == null ? null : identity.status();
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

}

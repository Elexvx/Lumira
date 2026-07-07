package com.lumira.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.lumira.api.auth.LoginResponseDTO;
import com.lumira.api.auth.PasskeyAuthenticationCompleteRequest;
import com.lumira.api.auth.PasskeyCredentialRenameRequest;
import com.lumira.api.auth.PasskeyOperationVerificationRequest;
import com.lumira.api.auth.PasskeyOptionsDTO;
import com.lumira.api.auth.PasskeyRegistrationCompleteRequest;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.LoginCapabilitiesDTO;
import com.lumira.api.system.PermissionSnapshotDTO;
import com.lumira.api.system.PasskeyCredentialAssertionDTO;
import com.lumira.api.system.PasskeyCredentialDTO;
import com.lumira.api.system.PasskeyCredentialSaveRequestDTO;
import com.lumira.api.system.PasskeyCredentialUsageRequestDTO;
import com.lumira.api.system.PasskeySettingsDTO;
import com.lumira.api.system.PasswordLoginVerificationDTO;
import com.lumira.api.system.SystemUserSnapshotDTO;
import com.lumira.api.system.VerificationProviderDTO;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.common.security.AuthenticationTrustSupport;
import com.lumira.common.security.CurrentUser;
import com.lumira.common.security.SecurityContextFacade;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

@Service
public class PasskeyAuthService {
    private static final String CHALLENGE_PREFIX = "lumira:auth:passkey:challenge:";
    private static final String TYPE_REGISTRATION = "registration";
    private static final String TYPE_AUTHENTICATION = "authentication";
    private static final int FLAG_UP = 0x01;
    private static final int FLAG_UV = 0x04;
    private static final int FLAG_BE = 0x08;
    private static final int FLAG_BS = 0x10;
    private static final int FLAG_AT = 0x40;

    private final SystemInternalApi systemInternalApi;
    private final AuthAppService authAppService;
    private final SecurityContextFacade securityContextFacade;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ObjectMapper cborMapper = new ObjectMapper(new CBORFactory());

    public PasskeyAuthService(
            SystemInternalApi systemInternalApi,
            AuthAppService authAppService,
            SecurityContextFacade securityContextFacade,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.systemInternalApi = systemInternalApi;
        this.authAppService = authAppService;
        this.securityContextFacade = securityContextFacade;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public PasskeyOptionsDTO registrationOptions(PasskeyOperationVerificationRequest request) {
        CurrentUser currentUser = requireCurrentUser();
        Long actorUserId = currentUser.getUserId();
        String actorUsername = currentUser.getUsername();
        verifyCurrentUserForSensitivePasskeyChange(currentUser, request);
        PasskeySettingsDTO settings = enabledSettings();
        if (!Boolean.TRUE.equals(settings.selfBindingEnabled())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "当前不允许自助绑定通行密钥", "当前不允许自助绑定通行密钥");
        }
        String challenge = randomBase64Url(32);
        String userHandle = randomBase64Url(32);
        saveChallenge(new ChallengeRecord(
                TYPE_REGISTRATION,
                challenge,
                actorUserId,
                userHandle,
                currentUser.getUserUuid(),
                currentUser.getSessionId(),
                currentUser.getSessionVersion(),
                currentUser.getPermissionsVersion()
        ), settings.challengeTtlSeconds());

        List<Map<String, Object>> excludeCredentials = systemInternalApi.passkeyCredentialDescriptors(actorUserId, currentUser.getUserUuid()).stream()
                .map(item -> credentialDescriptor(item.credentialId(), item.transports()))
                .toList();
        Map<String, Object> publicKey = new LinkedHashMap<>();
        publicKey.put("challenge", challenge);
        publicKey.put("rp", Map.of("id", settings.rpId(), "name", settings.rpName()));
        publicKey.put("user", Map.of(
                "id", userHandle,
                "name", actorUsername,
                "displayName", actorUsername
        ));
        publicKey.put("pubKeyCredParams", List.of(Map.of("type", "public-key", "alg", -7), Map.of("type", "public-key", "alg", -257)));
        publicKey.put("timeout", settings.challengeTtlSeconds() * 1000);
        publicKey.put("excludeCredentials", excludeCredentials);
        publicKey.put("authenticatorSelection", Map.of(
                "residentKey", "required",
                "requireResidentKey", true,
                "userVerification", "required"
        ));
        publicKey.put("attestation", "none");
        return new PasskeyOptionsDTO(challenge, publicKey);
    }

    public PasskeyCredentialDTO completeRegistration(PasskeyRegistrationCompleteRequest request, HttpServletRequest httpServletRequest) {
        ChallengeRecord challenge = consumeChallenge(request.challengeId(), TYPE_REGISTRATION);
        requireCurrentUserIdMatchesChallenge(challenge);
        PasskeySettingsDTO settings = enabledSettings();
        ClientData clientData = parseClientData(request.response().clientDataJSON(), "webauthn.create", challenge.challenge(), settings);
        AttestationData attestation = parseAttestationObject(request.response().attestationObject(), settings);
        if (!request.rawId().equals(attestation.credentialId())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "通行密钥凭据不匹配");
        }
        if (!MessageDigest.isEqual(attestation.rpIdHash(), sha256(settings.rpId().getBytes(StandardCharsets.UTF_8)))) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "通行密钥 RP ID 不匹配");
        }
        ensureUserVerified(attestation.flags());
        if (systemInternalApi.passkeyCredentialAssertion(attestation.credentialId()) != null) {
            throw new BizException(ErrorCode.BIZ_ERROR, "该通行密钥已绑定");
        }
        String label = StringUtils.hasText(request.label()) ? request.label() : browserLabel(httpServletRequest);
        return systemInternalApi.savePasskeyCredential(new PasskeyCredentialSaveRequestDTO(
                challenge.userId(),
                challenge.userUuid(),
                challenge.userHandle(),
                attestation.credentialId(),
                base64Url(attestation.publicKeyCose()),
                attestation.signCount(),
                request.transports() == null ? "" : String.join(",", request.transports()),
                (attestation.flags() & FLAG_BE) != 0,
                (attestation.flags() & FLAG_BS) != 0,
                label
        ));
    }

    public PasskeyOptionsDTO authenticationOptions() {
        PasskeySettingsDTO settings = enabledSettings();
        if (!Boolean.TRUE.equals(settings.passwordlessEnabled())) {
            throw new BizException(ErrorCode.FORBIDDEN, "当前未开启通行密钥无账号登录");
        }
        String challenge = randomBase64Url(32);
        saveChallenge(new ChallengeRecord(TYPE_AUTHENTICATION, challenge, null, null, null, null, null, null), settings.challengeTtlSeconds());
        Map<String, Object> publicKey = new LinkedHashMap<>();
        publicKey.put("challenge", challenge);
        publicKey.put("rpId", settings.rpId());
        publicKey.put("timeout", settings.challengeTtlSeconds() * 1000);
        publicKey.put("userVerification", "required");
        return new PasskeyOptionsDTO(challenge, publicKey);
    }

    public LoginResponseDTO completeAuthentication(PasskeyAuthenticationCompleteRequest request, HttpServletRequest httpServletRequest) {
        ChallengeRecord challenge = consumeChallenge(request.challengeId(), TYPE_AUTHENTICATION);
        PasskeySettingsDTO settings = enabledSettings();
        parseClientData(request.response().clientDataJSON(), "webauthn.get", challenge.challenge(), settings);
        byte[] authenticatorData = base64UrlDecode(request.response().authenticatorData());
        if (authenticatorData.length < 37) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "通行密钥认证数据无效");
        }
        if (!MessageDigest.isEqual(authenticatorDataRpIdHash(authenticatorData), sha256(settings.rpId().getBytes(StandardCharsets.UTF_8)))) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "通行密钥 RP ID 不匹配");
        }
        int flags = authenticatorData[32] & 0xff;
        ensureUserVerified(flags);
        PasskeyCredentialAssertionDTO credential = systemInternalApi.passkeyCredentialAssertion(request.rawId());
        if (credential == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未找到通行密钥");
        }
        ensureUserHandleMatches(request.response().userHandle(), credential.userHandle());
        verifySignature(credential.publicKeyCose(), authenticatorData, base64UrlDecode(request.response().clientDataJSON()), base64UrlDecode(request.response().signature()));
        long signCount = readSignCount(authenticatorData);
        if (credential.signCount() != null && credential.signCount() > 0 && signCount > 0 && signCount <= credential.signCount()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "通行密钥计数器异常，请重新绑定");
        }
        systemInternalApi.updatePasskeyCredentialUsage(new PasskeyCredentialUsageRequestDTO(
                credential.id(),
                credential.userId(),
                credential.userUuid(),
                signCount,
                (flags & FLAG_BE) != 0,
                (flags & FLAG_BS) != 0
        ));
        return authAppService.loginVerifiedUser(credential.userId(), credential.userUuid(), httpServletRequest);
    }

    public List<PasskeyCredentialDTO> listCredentials() {
        CurrentUser currentUser = requireCurrentUser();
        return systemInternalApi.passkeyCredentials(currentUser.getUserId(), currentUser.getUserUuid());
    }

    public PasskeyCredentialDTO renameCredential(Long id, PasskeyCredentialRenameRequest request) {
        requirePositiveId(id, "Passkey credential id is required");
        CurrentUser currentUser = requireCurrentUser();
        verifyCurrentUserForSensitivePasskeyChange(
                currentUser,
                new PasskeyOperationVerificationRequest(
                        request.currentPassword(),
                        request.currentFactorCode(),
                        request.currentChallengeId(),
                        request.currentVerificationCode()
                )
        );
        return systemInternalApi.renamePasskeyCredential(id, currentUser.getUserId(), currentUser.getUserUuid(), request.label());
    }

    public Boolean deleteCredential(Long id, PasskeyOperationVerificationRequest request) {
        requirePositiveId(id, "Passkey credential id is required");
        CurrentUser currentUser = requireCurrentUser();
        verifyCurrentUserForSensitivePasskeyChange(currentUser, request);
        return systemInternalApi.deletePasskeyCredential(id, currentUser.getUserId(), currentUser.getUserUuid());
    }

    private void verifyCurrentUserForSensitivePasskeyChange(CurrentUser currentUser, PasskeyOperationVerificationRequest request) {
        SystemUserSnapshotDTO user = systemInternalApi.findUserProfileById(currentUser.getUserId());
        if (user == null || !normalizedEquals(user.userUuid(), currentUser.getUserUuid())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Current user context is invalid");
        }
        String currentPassword = request == null ? null : normalizeOptionalText(request.currentPassword());
        if (StringUtils.hasText(currentPassword)) {
            verifyCurrentPassword(currentUser, currentPassword);
            return;
        }
        List<String> availableFactors = availableSensitivePasskeyVerificationFactors(currentUser, user);
        if (!availableFactors.isEmpty()) {
            requireCurrentFactorVerification(currentUser, request, availableFactors);
            return;
        }
        throw new BizException(ErrorCode.VALIDATION_ERROR, "Please enter your current password before changing passkeys");
    }

    private void verifyCurrentPassword(CurrentUser currentUser, String currentPassword) {
        PasswordLoginVerificationDTO verification = systemInternalApi.verifyPasswordLogin(currentUser.getUsername(), currentPassword);
        if (verification == null
                || verification.user() == null
                || !Boolean.TRUE.equals(verification.passwordMatched())
                || !currentUser.getUserId().equals(verification.user().userId())
                || !normalizedEquals(currentUser.getUserUuid(), verification.user().userUuid())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Current password is incorrect");
        }
    }

    private List<String> availableSensitivePasskeyVerificationFactors(CurrentUser currentUser, SystemUserSnapshotDTO user) {
        List<String> factors = new ArrayList<>();
        List<VerificationProviderDTO> providers = systemInternalApi.listVerificationProviders(currentUser.getUserId(), currentUser.getUserUuid());
        if (providers != null && providers.stream().anyMatch(provider ->
                "totp".equalsIgnoreCase(provider.getFactorCode())
                        && provider.isBound()
                        && provider.isEnabled())) {
            factors.add("totp");
        }
        LoginCapabilitiesDTO loginCapabilities = systemInternalApi.loginCapabilities();
        if (loginCapabilities != null && loginCapabilities.smsLoginAvailable() && StringUtils.hasText(user.mobile())) {
            factors.add("sms");
        }
        if (loginCapabilities != null && loginCapabilities.emailLoginAvailable() && StringUtils.hasText(user.email())) {
            factors.add("email");
        }
        return factors;
    }

    private void requireCurrentFactorVerification(CurrentUser currentUser, PasskeyOperationVerificationRequest request, List<String> availableFactors) {
        String currentFactorCode = request == null ? null : normalizeOptionalText(request.currentFactorCode());
        String currentChallengeId = request == null ? null : normalizeOptionalText(request.currentChallengeId());
        String currentVerificationCode = request == null ? null : normalizeOptionalText(request.currentVerificationCode());
        if (!StringUtils.hasText(currentFactorCode) || !StringUtils.hasText(currentChallengeId) || !StringUtils.hasText(currentVerificationCode)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Please verify your current sign-in method before changing passkeys");
        }
        String normalizedFactorCode = currentFactorCode.toLowerCase(Locale.ROOT);
        if (!availableFactors.contains(normalizedFactorCode)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Current verification method is not available");
        }
        systemInternalApi.verificationVerify(
                currentUser.getUserId(),
                currentUser.getUserUuid(),
                normalizedFactorCode,
                currentChallengeId,
                currentVerificationCode
        );
    }

    private String normalizeOptionalText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private Long requireCurrentUserId() {
        return requireCurrentUser().getUserId();
    }

    private CurrentUser requireCurrentUser() {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        if (!AuthenticationTrustSupport.isTrustedCurrentUser(currentUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "User context is required");
        }
        refreshTrustedCurrentUser(currentUser);
        return currentUser;
    }

    private void refreshTrustedCurrentUser(CurrentUser currentUser) {
        Long userId = currentUser.getUserId();
        String normalizedUserUuid = StringUtils.hasText(currentUser.getUserUuid()) ? currentUser.getUserUuid().trim() : null;
        if (userId == null || userId <= 0 || !StringUtils.hasText(normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "User context is required");
        }
        SystemUserSnapshotDTO user = systemInternalApi.findUserIdentityById(userId);
        if (user == null || user.userId() == null || !user.userId().equals(userId)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        if (!normalizedEquals(user.userUuid(), normalizedUserUuid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user identity is required");
        }
        if (!"ENABLED".equalsIgnoreCase(user.status())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user is disabled or no longer active");
        }
        String trustedUsername = StringUtils.hasText(user.username()) ? user.username().trim() : null;
        if (!StringUtils.hasText(trustedUsername)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user username is unavailable");
        }
        Long simulatedRoleId = normalizeSimulatedRoleId(currentUser.getSimulatedRoleId());
        PermissionSnapshotDTO snapshot = simulatedRoleId == null
                ? systemInternalApi.permissionSnapshot(userId, normalizedUserUuid)
                : systemInternalApi.simulatedRolePermissionSnapshot(userId, normalizedUserUuid, simulatedRoleId);
        if (snapshot == null || !StringUtils.hasText(snapshot.version())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Trusted user permissions are unavailable");
        }
        currentUser.setUserId(user.userId());
        currentUser.setUserUuid(user.userUuid().trim());
        currentUser.setUsername(trustedUsername);
        currentUser.setPermissions(snapshot.permissions() == null ? Set.of() : Set.copyOf(snapshot.permissions()));
        currentUser.setRoleIds(snapshot.roleIds() == null ? Set.of() : Set.copyOf(snapshot.roleIds()));
        currentUser.setPrimaryDeptId(snapshot.primaryDeptId());
        currentUser.setDeptIds(snapshot.deptIds() == null ? Set.of() : Set.copyOf(snapshot.deptIds()));
        currentUser.setDescendantDeptIds(snapshot.descendantDeptIds() == null ? Set.of() : Set.copyOf(snapshot.descendantDeptIds()));
        currentUser.setDataScopes(snapshot.dataScopes() == null ? List.of() : List.copyOf(snapshot.dataScopes()));
        currentUser.setPermissionsVersion(snapshot.version().trim());
        currentUser.setDefaultHomePath(snapshot.defaultHomePath());
        currentUser.setSimulatedRoleId(simulatedRoleId);
    }

    private Long normalizeSimulatedRoleId(Long simulatedRoleId) {
        return simulatedRoleId == null || simulatedRoleId <= 0 ? null : simulatedRoleId;
    }

    private void requireCurrentUserIdMatchesChallenge(ChallengeRecord challenge) {
        CurrentUser currentUser = requireCurrentUser();
        if (challenge.userId() == null
                || !challenge.userId().equals(currentUser.getUserId())
                || !normalizedEquals(challenge.userUuid(), currentUser.getUserUuid())
                || !normalizedEquals(challenge.sessionId(), currentUser.getSessionId())
                || challenge.sessionVersion() == null
                || !challenge.sessionVersion().equals(currentUser.getSessionVersion())
                || !normalizedEquals(challenge.permissionsVersion(), currentUser.getPermissionsVersion())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Passkey registration challenge does not belong to current user");
        }
    }

    private boolean normalizedEquals(String expected, String actual) {
        return StringUtils.hasText(expected)
                && StringUtils.hasText(actual)
                && expected.trim().equals(actual.trim());
    }

    private void requirePositiveId(Long id, String message) {
        if (id == null || id <= 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, message);
        }
    }

    private PasskeySettingsDTO enabledSettings() {
        PasskeySettingsDTO settings = systemInternalApi.passkeySettings();
        if (settings == null || !Boolean.TRUE.equals(settings.enabled())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "当前未开启通行密钥登录", "当前未开启通行密钥登录");
        }
        if (!StringUtils.hasText(settings.rpId()) || settings.allowedOrigins() == null || settings.allowedOrigins().isEmpty()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "通行密钥 RP ID 或 Origin 未配置");
        }
        if (settings.challengeTtlSeconds() <= 0 || settings.challengeTtlSeconds() > 300) {
            throw new BizException(ErrorCode.BIZ_ERROR, "Passkey challenge TTL is invalid");
        }
        return settings;
    }

    private void saveChallenge(ChallengeRecord challenge, int ttlSeconds) {
        try {
            redisTemplate.opsForValue().set(CHALLENGE_PREFIX + challenge.challenge(), objectMapper.writeValueAsString(challenge), Duration.ofSeconds(ttlSeconds));
        } catch (Exception ex) {
            throw new BizException(ErrorCode.BIZ_ERROR, "通行密钥 challenge 创建失败");
        }
    }

    private ChallengeRecord consumeChallenge(String challengeId, String expectedType) {
        if (!StringUtils.hasText(challengeId)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "Passkey challenge is required");
        }
        String key = CHALLENGE_PREFIX + challengeId.trim();
        String value = redisTemplate.opsForValue().get(key);
        redisTemplate.delete(key);
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "通行密钥 challenge 已过期");
        }
        try {
            ChallengeRecord record = objectMapper.readValue(value, ChallengeRecord.class);
            if (!expectedType.equals(record.type())) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "通行密钥 challenge 类型不匹配");
            }
            return record;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "通行密钥 challenge 无效");
        }
    }

    private ClientData parseClientData(String clientDataJsonBase64, String expectedType, String expectedChallenge, PasskeySettingsDTO settings) {
        try {
            JsonNode node = objectMapper.readTree(base64UrlDecode(clientDataJsonBase64));
            String type = node.path("type").asText();
            String challenge = node.path("challenge").asText();
            String origin = node.path("origin").asText();
            if (!expectedType.equals(type) || !expectedChallenge.equals(challenge)) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "通行密钥 challenge 校验失败");
            }
            if (!settings.allowedOrigins().contains(origin)) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "通行密钥 Origin 不允许");
            }
            return new ClientData(type, challenge, origin);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "通行密钥客户端数据无效");
        }
    }

    private AttestationData parseAttestationObject(String attestationObjectBase64, PasskeySettingsDTO settings) {
        try {
            JsonNode node = cborMapper.readTree(base64UrlDecode(attestationObjectBase64));
            byte[] authData = node.path("authData").binaryValue();
            if (authData == null || authData.length < 55 || (authData[32] & FLAG_AT) == 0) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "通行密钥注册数据无效");
            }
            byte[] rpIdHash = authenticatorDataRpIdHash(authData);
            int flags = authData[32] & 0xff;
            long signCount = readSignCount(authData);
            int credentialIdLength = ((authData[53] & 0xff) << 8) | (authData[54] & 0xff);
            int credentialIdOffset = 55;
            int coseOffset = credentialIdOffset + credentialIdLength;
            if (authData.length <= coseOffset) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "通行密钥凭据数据无效");
            }
            byte[] credentialId = slice(authData, credentialIdOffset, credentialIdLength);
            byte[] cose = slice(authData, coseOffset, authData.length - coseOffset);
            return new AttestationData(rpIdHash, flags, signCount, base64Url(credentialId), cose);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "通行密钥注册数据无法解析");
        }
    }

    private void verifySignature(String publicKeyCoseBase64, byte[] authenticatorData, byte[] clientDataJson, byte[] signatureBytes) {
        try {
            PublicKey publicKey = publicKeyFromCose(base64UrlDecode(publicKeyCoseBase64));
            byte[] signedData = ByteBuffer.allocate(authenticatorData.length + 32)
                    .put(authenticatorData)
                    .put(sha256(clientDataJson))
                    .array();
            Signature signature = Signature.getInstance(publicKey instanceof ECPublicKey ? "SHA256withECDSA" : "SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(signedData);
            if (!signature.verify(signatureBytes)) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "通行密钥签名校验失败");
            }
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "通行密钥签名无法校验");
        }
    }

    private PublicKey publicKeyFromCose(byte[] cose) throws Exception {
        JsonNode node = cborMapper.readTree(cose);
        int kty = node.path("1").asInt();
        if (kty == 2) {
            byte[] x = node.path("-2").binaryValue();
            byte[] y = node.path("-3").binaryValue();
            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec("secp256r1"));
            ECParameterSpec ecSpec = params.getParameterSpec(ECParameterSpec.class);
            return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(new BigInteger(1, x), new BigInteger(1, y)), ecSpec));
        }
        if (kty == 3) {
            byte[] n = node.path("-1").binaryValue();
            byte[] e = node.path("-2").binaryValue();
            return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(new BigInteger(1, n), new BigInteger(1, e)));
        }
        throw new BizException(ErrorCode.VALIDATION_ERROR, "不支持的通行密钥算法");
    }

    private void ensureUserVerified(int flags) {
        if ((flags & FLAG_UP) == 0 || (flags & FLAG_UV) == 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "需要通过本机生物识别或设备验证");
        }
    }

    private void ensureUserHandleMatches(String responseUserHandle, String credentialUserHandle) {
        if (StringUtils.hasText(responseUserHandle)
                && (!StringUtils.hasText(credentialUserHandle) || !responseUserHandle.trim().equals(credentialUserHandle.trim()))) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "Passkey user handle does not match");
        }
    }

    private Map<String, Object> credentialDescriptor(String credentialId, String transports) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("type", "public-key");
        descriptor.put("id", credentialId);
        if (StringUtils.hasText(transports)) {
            descriptor.put("transports", Arrays.stream(transports.split(",")).map(String::trim).filter(StringUtils::hasText).toList());
        }
        return descriptor;
    }

    private String browserLabel(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return StringUtils.hasText(userAgent) ? "通行密钥 - " + userAgent.substring(0, Math.min(userAgent.length(), 40)) : "通行密钥";
    }

    private byte[] authenticatorDataRpIdHash(byte[] authData) {
        return slice(authData, 0, 32);
    }

    private long readSignCount(byte[] authData) {
        return ((long) (authData[33] & 0xff) << 24)
                | ((long) (authData[34] & 0xff) << 16)
                | ((long) (authData[35] & 0xff) << 8)
                | (authData[36] & 0xff);
    }

    private byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception ex) {
            throw new BizException(ErrorCode.BIZ_ERROR, "SHA-256 不可用");
        }
    }

    private byte[] slice(byte[] source, int offset, int length) {
        byte[] result = new byte[length];
        System.arraycopy(source, offset, result, 0, length);
        return result;
    }

    private String randomBase64Url(int bytes) {
        byte[] data = new byte[bytes];
        new java.security.SecureRandom().nextBytes(data);
        return base64Url(data);
    }

    private String base64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private byte[] base64UrlDecode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private record ChallengeRecord(
            String type,
            String challenge,
            Long userId,
            String userHandle,
            String userUuid,
            String sessionId,
            Integer sessionVersion,
            String permissionsVersion
    ) {
    }

    private record ClientData(String type, String challenge, String origin) {
    }

    private record AttestationData(byte[] rpIdHash, int flags, long signCount, String credentialId, byte[] publicKeyCose) {
    }
}

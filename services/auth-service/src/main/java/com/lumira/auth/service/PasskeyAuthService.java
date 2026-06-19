package com.lumira.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.lumira.api.auth.LoginResponseDTO;
import com.lumira.api.auth.PasskeyAuthenticationCompleteRequest;
import com.lumira.api.auth.PasskeyCredentialLabelRequest;
import com.lumira.api.auth.PasskeyOptionsDTO;
import com.lumira.api.auth.PasskeyRegistrationCompleteRequest;
import com.lumira.api.client.SystemInternalApi;
import com.lumira.api.system.PasskeyCredentialDTO;
import com.lumira.api.system.PasskeyCredentialSaveRequestDTO;
import com.lumira.api.system.PasskeyCredentialUsageRequestDTO;
import com.lumira.api.system.PasskeySettingsDTO;
import com.lumira.common.constant.PlatformConstants;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

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

    public PasskeyOptionsDTO registrationOptions() {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        Long tenantId = PlatformConstants.PLATFORM_TENANT_ID;
        PasskeySettingsDTO settings = enabledSettings(tenantId);
        if (!Boolean.TRUE.equals(settings.selfBindingEnabled())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "当前不允许自助绑定通行密钥", "当前不允许自助绑定通行密钥");
        }
        String challenge = randomBase64Url(32);
        String userHandle = randomBase64Url(32);
        saveChallenge(new ChallengeRecord(TYPE_REGISTRATION, challenge, tenantId, currentUser.getUserId(), userHandle), settings.challengeTtlSeconds());

        List<Map<String, Object>> excludeCredentials = systemInternalApi.passkeyCredentials(tenantId, currentUser.getUserId()).stream()
                .map(item -> credentialDescriptor(item.credentialId(), item.transports()))
                .toList();
        Map<String, Object> publicKey = new LinkedHashMap<>();
        publicKey.put("challenge", challenge);
        publicKey.put("rp", Map.of("id", settings.rpId(), "name", settings.rpName()));
        publicKey.put("user", Map.of(
                "id", userHandle,
                "name", currentUser.getUsername(),
                "displayName", currentUser.getUsername()
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
        PasskeySettingsDTO settings = enabledSettings(challenge.tenantId());
        ClientData clientData = parseClientData(request.response().clientDataJSON(), "webauthn.create", challenge.challenge(), settings);
        AttestationData attestation = parseAttestationObject(request.response().attestationObject(), settings);
        if (!request.rawId().equals(attestation.credentialId())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "通行密钥凭据不匹配");
        }
        if (!MessageDigest.isEqual(attestation.rpIdHash(), sha256(settings.rpId().getBytes(StandardCharsets.UTF_8)))) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "通行密钥 RP ID 不匹配");
        }
        ensureUserVerified(attestation.flags());
        if (systemInternalApi.passkeyCredentialByCredentialId(attestation.credentialId()) != null) {
            throw new BizException(ErrorCode.BIZ_ERROR, "该通行密钥已绑定");
        }
        String label = StringUtils.hasText(request.label()) ? request.label() : browserLabel(httpServletRequest);
        return systemInternalApi.savePasskeyCredential(new PasskeyCredentialSaveRequestDTO(
                challenge.tenantId(),
                challenge.userId(),
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
        Long tenantId = PlatformConstants.PLATFORM_TENANT_ID;
        PasskeySettingsDTO settings = enabledSettings(tenantId);
        if (!Boolean.TRUE.equals(settings.passwordlessEnabled())) {
            throw new BizException(ErrorCode.FORBIDDEN, "当前未开启通行密钥无账号登录");
        }
        String challenge = randomBase64Url(32);
        saveChallenge(new ChallengeRecord(TYPE_AUTHENTICATION, challenge, tenantId, null, null), settings.challengeTtlSeconds());
        Map<String, Object> publicKey = new LinkedHashMap<>();
        publicKey.put("challenge", challenge);
        publicKey.put("rpId", settings.rpId());
        publicKey.put("timeout", settings.challengeTtlSeconds() * 1000);
        publicKey.put("userVerification", "required");
        return new PasskeyOptionsDTO(challenge, publicKey);
    }

    public LoginResponseDTO completeAuthentication(PasskeyAuthenticationCompleteRequest request, HttpServletRequest httpServletRequest) {
        ChallengeRecord challenge = consumeChallenge(request.challengeId(), TYPE_AUTHENTICATION);
        PasskeySettingsDTO settings = enabledSettings(challenge.tenantId());
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
        PasskeyCredentialDTO credential = systemInternalApi.passkeyCredentialByCredentialId(request.rawId());
        if (credential == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未找到通行密钥");
        }
        verifySignature(credential.publicKeyCose(), authenticatorData, base64UrlDecode(request.response().clientDataJSON()), base64UrlDecode(request.response().signature()));
        long signCount = readSignCount(authenticatorData);
        if (credential.signCount() != null && credential.signCount() > 0 && signCount > 0 && signCount <= credential.signCount()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "通行密钥计数器异常，请重新绑定");
        }
        systemInternalApi.updatePasskeyCredentialUsage(new PasskeyCredentialUsageRequestDTO(
                credential.id(),
                signCount,
                (flags & FLAG_BE) != 0,
                (flags & FLAG_BS) != 0
        ));
        return authAppService.loginVerifiedUser(credential.userId(), credential.tenantId(), httpServletRequest);
    }

    public List<PasskeyCredentialDTO> listCredentials() {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        return systemInternalApi.passkeyCredentials(PlatformConstants.PLATFORM_TENANT_ID, currentUser.getUserId());
    }

    public PasskeyCredentialDTO renameCredential(Long id, PasskeyCredentialLabelRequest request) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        return systemInternalApi.renamePasskeyCredential(id, PlatformConstants.PLATFORM_TENANT_ID, currentUser.getUserId(), request.label());
    }

    public Boolean deleteCredential(Long id) {
        CurrentUser currentUser = securityContextFacade.getCurrentUser();
        return systemInternalApi.deletePasskeyCredential(id, PlatformConstants.PLATFORM_TENANT_ID, currentUser.getUserId());
    }

    private PasskeySettingsDTO enabledSettings(Long tenantId) {
        PasskeySettingsDTO settings = systemInternalApi.passkeySettings(tenantId);
        if (settings == null || !Boolean.TRUE.equals(settings.enabled())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "当前未开启通行密钥登录", "当前未开启通行密钥登录");
        }
        if (!StringUtils.hasText(settings.rpId()) || settings.allowedOrigins() == null || settings.allowedOrigins().isEmpty()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "通行密钥 RP ID 或 Origin 未配置");
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
        String key = CHALLENGE_PREFIX + challengeId;
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

    private record ChallengeRecord(String type, String challenge, Long tenantId, Long userId, String userHandle) {
    }

    private record ClientData(String type, String challenge, String origin) {
    }

    private record AttestationData(byte[] rpIdHash, int flags, long signCount, String credentialId, byte[] publicKeyCose) {
    }
}

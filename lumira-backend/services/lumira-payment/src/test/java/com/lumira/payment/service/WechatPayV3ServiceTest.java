package com.lumira.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumira.api.payment.PaymentProviderSettingsDTO;
import com.lumira.common.exception.BizException;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class WechatPayV3ServiceTest {

    private static final String API_V3_KEY = "0123456789abcdef0123456789abcdef";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WechatPayV3Service service = new WechatPayV3Service(objectMapper);

    @Test
    void buildAuthorizationShouldSignTheWechatPayCanonicalRequest() throws Exception {
        KeyPair merchantKeyPair = rsaKeyPair();
        PaymentProviderSettingsDTO settings = settings(merchantKeyPair, merchantKeyPair, "merchant-serial-1");
        String body = "{\"mchid\":\"1707316690\"}";

        String authorization = service.buildAuthorization(
                settings,
                "POST",
                "/v3/pay/transactions/native",
                body,
                "nonce-123",
                1_785_096_000L
        );

        assertThat(authorization)
                .startsWith("WECHATPAY2-SHA256-RSA2048 ")
                .contains("mchid=\"1707316690\"")
                .contains("serial_no=\"merchant-serial-1\"");
        String encodedSignature = authorization
                .substring(authorization.indexOf("signature=\"") + "signature=\"".length())
                .replaceFirst("\"$", "");
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(merchantKeyPair.getPublic());
        verifier.update(("""
                POST
                /v3/pay/transactions/native
                1785096000
                nonce-123
                %s
                """.formatted(body)).getBytes(StandardCharsets.UTF_8));
        assertThat(verifier.verify(Base64.getDecoder().decode(encodedSignature))).isTrue();
    }

    @Test
    void providerSignErrorShouldExposeSafeActionableMessage() {
        BizException exception = service.providerRequestFailure(
                401,
                """
                        {
                          "code": "SIGN_ERROR",
                          "message": "sign not match"
                        }
                        """
        );

        assertThat(exception.getMessage())
                .contains("status 401")
                .contains("SIGN_ERROR")
                .contains("sign not match");
        assertThat(exception.getUserMessage())
                .isEqualTo("微信支付签名校验未通过，请检查商户证书序列号与商户私钥是否匹配");
    }

    @Test
    void providerParameterErrorShouldRetainWechatValidationDetail() {
        BizException exception = service.providerRequestFailure(
                400,
                """
                        {
                          "code": "PARAM_ERROR",
                          "message": "notify_url is invalid"
                        }
                        """
        );

        assertThat(exception.getUserMessage())
                .isEqualTo("微信支付请求参数错误（PARAM_ERROR）：notify_url is invalid");
    }

    @Test
    void unknownProviderErrorShouldExposeOnlyProviderCode() {
        BizException exception = service.providerRequestFailure(
                403,
                """
                        {
                          "code": "RULE_LIMIT",
                          "message": "merchant-specific internal policy detail"
                        }
                        """
        );

        assertThat(exception.getMessage()).contains("merchant-specific internal policy detail");
        assertThat(exception.getUserMessage())
                .isEqualTo("微信支付下单失败（RULE_LIMIT），请检查微信商户配置后重试");
    }

    @Test
    void notificationShouldRequirePlatformRsaSignatureAndDecryptTheResource() throws Exception {
        KeyPair merchantKeyPair = rsaKeyPair();
        KeyPair platformKeyPair = rsaKeyPair();
        PaymentProviderSettingsDTO settings = settings(merchantKeyPair, platformKeyPair, "platform-serial-1");
        String plaintext = """
                {
                  "appid":"wx049954bec19fc00a",
                  "mchid":"1707316690",
                  "out_trade_no":"REG-1001",
                  "transaction_id":"4200000001202607270001",
                  "trade_state":"SUCCESS",
                  "amount":{"total":3702,"currency":"CNY"}
                }
                """;
        String notification = encryptedNotification(plaintext);
        String timestamp = "1785096000";
        String nonce = "callback-nonce";
        String signature = sign(
                platformKeyPair,
                timestamp + "\n" + nonce + "\n" + notification + "\n"
        );

        assertThat(service.verifyNotificationSignature(
                settings,
                timestamp,
                nonce,
                signature,
                "platform-serial-1",
                notification
        )).isTrue();
        assertThat(service.verifyNotificationSignature(
                settings,
                timestamp,
                nonce,
                signature,
                "other-serial",
                notification
        )).isFalse();

        WechatPayV3Service.WechatNotification decrypted = service.decryptNotification(settings, notification);
        JsonNode normalized = objectMapper.readTree(decrypted.normalizedPayload());
        assertThat(decrypted.eventId()).isEqualTo("evt-wechat-1");
        assertThat(decrypted.eventType()).isEqualTo("TRANSACTION.SUCCESS");
        assertThat(normalized.path("orderNo").asText()).isEqualTo("REG-1001");
        assertThat(normalized.path("providerTxnId").asText()).isEqualTo("4200000001202607270001");
        assertThat(normalized.path("amountMinor").asLong()).isEqualTo(3702L);
        assertThat(normalized.path("currency").asText()).isEqualTo("CNY");
    }

    private String encryptedNotification(String plaintext) throws Exception {
        String resourceNonce = "resource1234";
        String associatedData = "transaction";
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.ENCRYPT_MODE,
                new SecretKeySpec(API_V3_KEY.getBytes(StandardCharsets.UTF_8), "AES"),
                new GCMParameterSpec(128, resourceNonce.getBytes(StandardCharsets.UTF_8))
        );
        cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
        String ciphertext = Base64.getEncoder().encodeToString(
                cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8))
        );

        ObjectNode root = objectMapper.createObjectNode();
        root.put("id", "evt-wechat-1");
        root.put("event_type", "TRANSACTION.SUCCESS");
        ObjectNode resource = root.putObject("resource");
        resource.put("algorithm", "AEAD_AES_256_GCM");
        resource.put("nonce", resourceNonce);
        resource.put("associated_data", associatedData);
        resource.put("ciphertext", ciphertext);
        return objectMapper.writeValueAsString(root);
    }

    private PaymentProviderSettingsDTO settings(
            KeyPair merchantKeyPair,
            KeyPair platformKeyPair,
            String platformSerial
    ) {
        PaymentProviderSettingsDTO settings = new PaymentProviderSettingsDTO();
        settings.setAppId("wx049954bec19fc00a");
        settings.setMerchantId("1707316690");
        settings.setMerchantSerialNo("merchant-serial-1");
        settings.setPlatformCertSerialNo(platformSerial);
        settings.setApiV3Key(API_V3_KEY);
        settings.setPrivateKey(toPrivateKeyPem(merchantKeyPair));
        settings.setPublicKey(toPublicKeyPem(platformKeyPair));
        return settings;
    }

    private KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private String sign(KeyPair keyPair, String content) throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(content.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    private String toPrivateKeyPem(KeyPair keyPair) {
        return "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";
    }

    private String toPublicKeyPem(KeyPair keyPair) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(keyPair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }
}

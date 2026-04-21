package com.legendary.invention.saas.modules.auth.app;

import com.legendary.invention.saas.common.enums.ErrorCode;
import com.legendary.invention.saas.common.exception.BizException;
import com.legendary.invention.saas.modules.auth.vo.LoginEncryptionKeyVO;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;

@Service
public class LoginEncryptionService {

    private static final Logger log = LoggerFactory.getLogger(LoginEncryptionService.class);

    private static final String ALGORITHM = "RSA-OAEP-256";
    private static final String TRANSFORMATION = "RSA/ECB/OAEPPadding";
    private static final int KEY_SIZE_BITS = 4096;
    private static final OAEPParameterSpec OAEP_SPEC = new OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT
    );

    private final KeyPair keyPair;
    private final LoginEncryptionKeyVO publicKeyInfo;

    public LoginEncryptionService() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(KEY_SIZE_BITS, new SecureRandom());
            this.keyPair = generator.generateKeyPair();
            this.publicKeyInfo = buildPublicKeyInfo(keyPair);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("初始化登录加密密钥失败", ex);
        }
    }

    public LoginEncryptionKeyVO getPublicKeyInfo() {
        return copyPublicKeyInfo();
    }

    public String decryptPassword(String encryptedPassword) {
        if (encryptedPassword == null || encryptedPassword.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "登录密码不能为空");
        }

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate(), OAEP_SPEC);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedPassword));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException ex) {
            log.warn("登录密码解密失败: {}", ex.getClass().getSimpleName(), ex);
            throw new BizException(
                    ErrorCode.BAD_REQUEST,
                    "登录密码解密失败",
                    ErrorCode.BAD_REQUEST.getDefaultUserMessage()
            );
        }
    }

    private LoginEncryptionKeyVO buildPublicKeyInfo(KeyPair keyPair) throws NoSuchAlgorithmException {
        LoginEncryptionKeyVO vo = new LoginEncryptionKeyVO();
        vo.setAlgorithm(ALGORITHM);
        vo.setKeyId(buildKeyId(keyPair));
        vo.setPublicKey(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        return vo;
    }

    private LoginEncryptionKeyVO copyPublicKeyInfo() {
        LoginEncryptionKeyVO vo = new LoginEncryptionKeyVO();
        vo.setAlgorithm(publicKeyInfo.getAlgorithm());
        vo.setKeyId(publicKeyInfo.getKeyId());
        vo.setPublicKey(publicKeyInfo.getPublicKey());
        return vo;
    }

    private String buildKeyId(KeyPair keyPair) throws NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(keyPair.getPublic().getEncoded());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }
}

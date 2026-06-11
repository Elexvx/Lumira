package com.lumira.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.security.FieldCryptoService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PaymentConfigCryptoService {

    private final ObjectMapper objectMapper;
    private final FieldCryptoService fieldCryptoService;

    public PaymentConfigCryptoService(
            ObjectMapper objectMapper,
            FieldCryptoService fieldCryptoService
    ) {
        this.objectMapper = objectMapper;
        this.fieldCryptoService = fieldCryptoService;
    }

    public String encryptJson(Object payload) {
        try {
            return fieldCryptoService.encrypt(objectMapper.writeValueAsString(payload));
        } catch (Exception ex) {
            throw new IllegalStateException("支付配置加密失败", ex);
        }
    }

    public <T> T decryptJson(String payload, Class<T> valueType) {
        try {
            if (!StringUtils.hasText(payload)) {
                return objectMapper.readValue("{}", valueType);
            }
            return objectMapper.readValue(fieldCryptoService.decrypt(payload), valueType);
        } catch (Exception ex) {
            throw new IllegalStateException("支付配置解密失败", ex);
        }
    }
}

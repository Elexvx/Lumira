package com.lumira.saas.modules.system.support;

import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
public class SmsVerificationSender {

    private static final String PROVIDER_ALIYUN = "aliyun";
    private static final String DEFAULT_ALIYUN_ENDPOINT = "dysmsapi.aliyuncs.com";

    private final ObjectMapper objectMapper;

    public SmsVerificationSender(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SmsSendResult send(SmsSettings settings, String phoneNumber, String verificationCode) {
        if (settings == null || !settings.configured()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "短信服务未配置完整");
        }
        if (!PROVIDER_ALIYUN.equalsIgnoreCase(settings.provider())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "暂不支持的短信服务商: " + settings.provider());
        }
        return sendAliyun(settings, phoneNumber, verificationCode);
    }

    private SmsSendResult sendAliyun(SmsSettings settings, String phoneNumber, String verificationCode) {
        try {
            Config config = new Config()
                    .setAccessKeyId(settings.accessKeyId())
                    .setAccessKeySecret(settings.accessKeySecret());
            config.endpoint = normalizeEndpoint(settings.endpoint());
            SendSmsRequest request = new SendSmsRequest()
                    .setPhoneNumbers(phoneNumber)
                    .setSignName(settings.signName())
                    .setTemplateCode(settings.templateCode())
                    .setTemplateParam(objectMapper.writeValueAsString(Map.of("code", verificationCode)));
            SendSmsResponse response = new com.aliyun.dysmsapi20170525.Client(config).sendSms(request);
            String code = response.getBody() == null ? "" : response.getBody().getCode();
            String message = response.getBody() == null ? "" : response.getBody().getMessage();
            String requestId = response.getBody() == null ? "" : response.getBody().getRequestId();
            String bizId = response.getBody() == null ? "" : response.getBody().getBizId();
            if (!"OK".equalsIgnoreCase(code)) {
                throw new BizException(ErrorCode.BIZ_ERROR, "短信发送失败: " + defaultIfBlank(message, code));
            }
            return new SmsSendResult(code, message, requestId, bizId);
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(ErrorCode.BIZ_ERROR, "短信发送失败，请检查短信服务配置");
        }
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String normalizeEndpoint(String endpoint) {
        if (!StringUtils.hasText(endpoint)) {
            return DEFAULT_ALIYUN_ENDPOINT;
        }
        String normalized = endpoint.trim()
                .replaceFirst("^https?://", "")
                .replaceFirst("/+$", "");
        return StringUtils.hasText(normalized) ? normalized : DEFAULT_ALIYUN_ENDPOINT;
    }

    public record SmsSettings(
            String provider,
            String signName,
            String templateCode,
            String accessKeyId,
            String accessKeySecret,
            String endpoint,
            String region,
            boolean configured
    ) {
    }

    public record SmsSendResult(String code, String message, String requestId, String bizId) {
    }
}

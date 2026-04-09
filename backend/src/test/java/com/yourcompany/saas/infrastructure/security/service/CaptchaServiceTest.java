package com.yourcompany.saas.infrastructure.security.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.saas.modules.system.dto.SystemDTO;
import com.yourcompany.saas.modules.system.vo.SystemVO;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptchaServiceTest {

    @Test
    void imageCaptchaShouldGenerateMixedCodeAndValidateCaseInsensitively() throws Exception {
        InMemoryCacheTemplate cacheTemplate = new InMemoryCacheTemplate();
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        CaptchaService service = new CaptchaService(cacheTemplate, objectMapper);

        SystemVO.CaptchaChallengeVO challenge = service.createChallenge("IMAGE");
        CaptchaService.CaptchaChallengeRecord record = cacheTemplate.read(challenge.getCaptchaId());

        assertNotNull(challenge.getImageUrl());
        assertTrue(challenge.getImageUrl().startsWith("data:image/svg+xml;base64,"));
        assertNotNull(record);
        assertEquals("IMAGE", record.getCaptchaType());
        assertTrue(record.getAnswer().matches("^[A-Z0-9]{5}$"));

        assertDoesNotThrow(() -> service.validateImageCaptcha(challenge.getCaptchaId(), record.getAnswer().toLowerCase(Locale.ROOT)));
        assertThrows(Exception.class, () -> service.validateImageCaptcha(challenge.getCaptchaId(), record.getAnswer()));
    }

    @Test
    void sliderCaptchaShouldIssueProofAndAllowSingleUseValidation() throws Exception {
        InMemoryCacheTemplate cacheTemplate = new InMemoryCacheTemplate();
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        CaptchaService service = new CaptchaService(cacheTemplate, objectMapper);

        SystemVO.CaptchaChallengeVO challenge = service.createChallenge("SLIDER");
        CaptchaService.CaptchaChallengeRecord record = cacheTemplate.read(challenge.getCaptchaId());

        assertNotNull(challenge.getBgUrl());
        assertNotNull(challenge.getPuzzleUrl());
        assertEquals("SLIDER", challenge.getCaptchaType());
        assertNotNull(record);

        SystemDTO.CaptchaSliderVerifyRequest request = new SystemDTO.CaptchaSliderVerifyRequest();
        request.setCaptchaId(challenge.getCaptchaId());
        request.setX(record.getExpectedX().doubleValue());
        request.setY(record.getExpectedY().doubleValue());
        request.setSliderOffsetX(0d);
        request.setDuration(240L);
        request.setTrail(List.of(List.of(0d, 0d), List.of(12d, 1d), List.of(24d, 2d), List.of(36d, 2d)));

        SystemVO.CaptchaVerifyVO verifyVO = assertDoesNotThrow(() -> service.verifySliderChallenge(request));
        assertNotNull(verifyVO.getCaptchaProof());
        assertDoesNotThrow(() -> service.validateSliderCaptcha(challenge.getCaptchaId(), verifyVO.getCaptchaProof()));
        assertThrows(Exception.class, () -> service.validateSliderCaptcha(challenge.getCaptchaId(), verifyVO.getCaptchaProof()));
    }

    @Test
    void validateCaptchaShouldDispatchBasedOnChallengeType() throws Exception {
        InMemoryCacheTemplate cacheTemplate = new InMemoryCacheTemplate();
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        CaptchaService service = new CaptchaService(cacheTemplate, objectMapper);

        SystemVO.CaptchaChallengeVO imageChallenge = service.createChallenge("IMAGE");
        CaptchaService.CaptchaChallengeRecord imageRecord = cacheTemplate.read(imageChallenge.getCaptchaId());
        assertDoesNotThrow(() -> service.validateCaptcha(imageChallenge.getCaptchaId(), imageRecord.getAnswer(), null));

        SystemVO.CaptchaChallengeVO sliderChallenge = service.createChallenge("SLIDER");
        CaptchaService.CaptchaChallengeRecord sliderRecord = cacheTemplate.read(sliderChallenge.getCaptchaId());
        SystemDTO.CaptchaSliderVerifyRequest request = new SystemDTO.CaptchaSliderVerifyRequest();
        request.setCaptchaId(sliderChallenge.getCaptchaId());
        request.setX(sliderRecord.getExpectedX().doubleValue());
        request.setY(sliderRecord.getExpectedY().doubleValue());
        request.setSliderOffsetX(0d);
        request.setDuration(240L);
        request.setTrail(List.of(List.of(0d, 0d), List.of(12d, 1d), List.of(24d, 2d)));

        SystemVO.CaptchaVerifyVO verifyVO = service.verifySliderChallenge(request);
        assertDoesNotThrow(() -> service.validateCaptcha(sliderChallenge.getCaptchaId(), null, verifyVO.getCaptchaProof()));
    }

    @Test
    void invalidCaptchaTypeShouldFallbackToImage() {
        InMemoryCacheTemplate cacheTemplate = new InMemoryCacheTemplate();
        CaptchaService service = new CaptchaService(cacheTemplate, new ObjectMapper().findAndRegisterModules());

        SystemVO.CaptchaChallengeVO challenge = service.createChallenge("unknown");

        assertEquals("IMAGE", challenge.getCaptchaType());
    }

    private static final class InMemoryCacheTemplate extends com.yourcompany.saas.infrastructure.redis.CacheTemplate {
        private final Map<String, String> values = new HashMap<>();

        private InMemoryCacheTemplate() {
            super((StringRedisTemplate) null);
        }

        @Override
        public void put(String key, String value, Duration ttl) {
            values.put(key, value);
        }

        @Override
        public String get(String key) {
            return values.get(key);
        }

        @Override
        public void remove(String key) {
            values.remove(key);
        }

        private CaptchaService.CaptchaChallengeRecord read(String captchaId) throws Exception {
            String payload = values.get(com.yourcompany.saas.common.constant.CacheKeyConstants.captchaKey(captchaId));
            if (payload == null) {
                return null;
            }
            return new ObjectMapper().findAndRegisterModules().readValue(payload, CaptchaService.CaptchaChallengeRecord.class);
        }
    }
}

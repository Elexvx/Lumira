package com.yourcompany.saas.infrastructure.security.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.saas.common.constant.CacheKeyConstants;
import com.yourcompany.saas.common.enums.ErrorCode;
import com.yourcompany.saas.common.exception.BizException;
import com.yourcompany.saas.infrastructure.redis.CacheTemplate;
import com.yourcompany.saas.modules.system.dto.SystemDTO;
import com.yourcompany.saas.modules.system.vo.SystemVO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class CaptchaService {

    private static final Duration CAPTCHA_TTL = Duration.ofMinutes(5);
    private static final int IMAGE_WIDTH = 160;
    private static final int IMAGE_HEIGHT = 56;
    private static final int SLIDER_WIDTH = 320;
    private static final int SLIDER_HEIGHT = 160;
    private static final int SLIDER_PUZZLE_WIDTH = 60;
    private static final int SLIDER_PUZZLE_HEIGHT = 160;
    private static final int SLIDER_TOLERANCE_PX = 10;
    private static final int SLIDER_MIN_DURATION_MS = 180;
    private static final String CAPTCHA_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final CacheTemplate cacheTemplate;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public CaptchaService(CacheTemplate cacheTemplate, ObjectMapper objectMapper) {
        this.cacheTemplate = cacheTemplate;
        this.objectMapper = objectMapper;
    }

    public SystemVO.CaptchaChallengeVO createChallenge(String captchaType) {
        String normalizedType = normalizeCaptchaType(captchaType);
        CaptchaChallengeRecord record = new CaptchaChallengeRecord();
        record.setCaptchaId(UUID.randomUUID().toString());
        record.setCaptchaType(normalizedType);
        record.setCreatedAt(Instant.now());
        record.setVerified(false);

        SystemVO.CaptchaChallengeVO response = new SystemVO.CaptchaChallengeVO();
        response.setCaptchaId(record.getCaptchaId());
        response.setCaptchaType(normalizedType);
        response.setExpiresInSeconds((int) CAPTCHA_TTL.toSeconds());

        if ("SLIDER".equals(normalizedType)) {
            buildSliderChallenge(record, response);
        } else {
            buildImageChallenge(record, response);
        }

        save(record);
        return response;
    }

    public void validateCaptcha(String captchaId, String captchaCode, String captchaProof) {
        CaptchaChallengeRecord record = load(captchaId);
        if (record == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "验证码已过期，请刷新后重试");
        }

        if ("SLIDER".equals(record.getCaptchaType())) {
            validateSliderCaptcha(record, captchaProof);
            return;
        }

        validateImageCaptcha(record, captchaCode);
    }

    public SystemVO.CaptchaVerifyVO verifySliderChallenge(SystemDTO.CaptchaSliderVerifyRequest request) {
        CaptchaChallengeRecord record = load(request.getCaptchaId());
        if (record == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "验证码已过期，请刷新后重试");
        }
        if (!"SLIDER".equals(record.getCaptchaType())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "验证码类型不匹配");
        }
        if (record.isVerified()) {
            return toVerifyVO(record);
        }

        validateSliderMove(record, request);
        record.setVerified(true);
        record.setProofToken(UUID.randomUUID().toString().replace("-", ""));
        record.setVerifiedAt(Instant.now());
        save(record);
        return toVerifyVO(record);
    }

    public void validateImageCaptcha(String captchaId, String captchaCode) {
        CaptchaChallengeRecord record = load(captchaId);
        if (record == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "验证码已过期，请刷新后重试");
        }
        validateImageCaptcha(record, captchaCode);
    }

    public void validateSliderCaptcha(String captchaId, String captchaProof) {
        CaptchaChallengeRecord record = load(captchaId);
        if (record == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "验证码已过期，请刷新后重试");
        }
        validateSliderCaptcha(record, captchaProof);
    }

    private void validateImageCaptcha(CaptchaChallengeRecord record, String captchaCode) {
        if (!"IMAGE".equals(record.getCaptchaType())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "验证码类型不匹配");
        }

        String expected = normalizeCaptchaCode(record.getAnswer());
        String actual = normalizeCaptchaCode(captchaCode);
        if (!StringUtils.hasText(actual) || !expected.equals(actual)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "验证码错误，请重新输入");
        }
        remove(record.getCaptchaId());
    }

    private void validateSliderCaptcha(CaptchaChallengeRecord record, String captchaProof) {
        if (!"SLIDER".equals(record.getCaptchaType())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "验证码类型不匹配");
        }
        if (!record.isVerified()) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "请先完成拖动验证码");
        }
        if (!StringUtils.hasText(record.getProofToken()) || !record.getProofToken().equals(captchaProof)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "验证码已失效，请重新验证");
        }
        remove(record.getCaptchaId());
    }

    private void buildImageChallenge(CaptchaChallengeRecord record, SystemVO.CaptchaChallengeVO response) {
        String answer = generateCaptchaCode(5);
        record.setAnswer(answer);
        response.setImageUrl(buildImageCaptchaDataUrl(answer));
    }

    private void buildSliderChallenge(CaptchaChallengeRecord record, SystemVO.CaptchaChallengeVO response) {
        String backgroundElements = buildSliderBackgroundElements();
        record.setExpectedX(generateSliderExpectedX());
        record.setExpectedY(0);
        record.setPuzzleWidth(SLIDER_PUZZLE_WIDTH);
        record.setPuzzleHeight(SLIDER_PUZZLE_HEIGHT);
        record.setBgWidth(SLIDER_WIDTH);
        record.setBgHeight(SLIDER_HEIGHT);
        record.setPuzzleLeft(0);
        record.setPuzzleTop(0);
        response.setBgWidth(SLIDER_WIDTH);
        response.setBgHeight(SLIDER_HEIGHT);
        response.setPuzzleWidth(SLIDER_PUZZLE_WIDTH);
        response.setPuzzleHeight(SLIDER_PUZZLE_HEIGHT);
        response.setPuzzleLeft(0);
        response.setPuzzleTop(0);
        response.setBgUrl(buildSliderBackgroundDataUrl(backgroundElements));
        response.setPuzzleUrl(buildSliderPuzzleDataUrl(record.getExpectedX(), backgroundElements));
    }

    private void validateSliderMove(CaptchaChallengeRecord record, SystemDTO.CaptchaSliderVerifyRequest request) {
        if (request.getDuration() == null || request.getDuration() < SLIDER_MIN_DURATION_MS) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "拖动验证失败，请重新尝试");
        }
        if (request.getTrail() == null || request.getTrail().size() < 3) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "拖动轨迹异常，请重新尝试");
        }
        double actualX = request.getX() == null ? Double.NaN : request.getX();
        double actualY = request.getY() == null ? Double.NaN : request.getY();
        double expectedX = record.getExpectedX();
        double expectedY = record.getExpectedY();
        if (Double.isNaN(actualX) || Double.isNaN(actualY)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "拖动验证失败，请重新尝试");
        }
        if (Math.abs(actualX - expectedX) > SLIDER_TOLERANCE_PX || Math.abs(actualY - expectedY) > SLIDER_TOLERANCE_PX) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "拖动验证失败，请重新尝试");
        }
    }

    private int generateSliderExpectedX() {
        int min = 76;
        int max = SLIDER_WIDTH - SLIDER_PUZZLE_WIDTH - 20;
        return min + secureRandom.nextInt(Math.max(max - min + 1, 1));
    }

    private String generateCaptchaCode(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            builder.append(CAPTCHA_CHARS.charAt(secureRandom.nextInt(CAPTCHA_CHARS.length())));
        }
        return builder.toString();
    }

    private String buildImageCaptchaDataUrl(String answer) {
        List<String> characters = new ArrayList<>();
        for (int index = 0; index < answer.length(); index++) {
            char character = answer.charAt(index);
            characters.add(renderCaptchaCharacter(character, index));
        }
        String noiseLines = String.join(
                "",
                renderNoiseLine(8, 12 + secureRandom.nextInt(IMAGE_HEIGHT - 16), IMAGE_WIDTH - 8, 10 + secureRandom.nextInt(IMAGE_HEIGHT - 10)),
                renderNoiseLine(4, 10 + secureRandom.nextInt(IMAGE_HEIGHT - 10), IMAGE_WIDTH - 10, 10 + secureRandom.nextInt(IMAGE_HEIGHT - 10)),
                renderNoiseLine(30 + secureRandom.nextInt(IMAGE_WIDTH - 60), 6, 20 + secureRandom.nextInt(IMAGE_WIDTH - 30), IMAGE_HEIGHT - 6),
                renderNoiseLine(10 + secureRandom.nextInt(IMAGE_WIDTH - 20), 10 + secureRandom.nextInt(IMAGE_HEIGHT - 10), 10 + secureRandom.nextInt(IMAGE_WIDTH - 20), IMAGE_HEIGHT - 10)
        );
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d">
                  <defs>
                    <linearGradient id="bg" x1="0%%" y1="0%%" x2="100%%" y2="100%%">
                      <stop offset="0%%" stop-color="#f8fbff"/>
                      <stop offset="100%%" stop-color="#eaf2ff"/>
                    </linearGradient>
                  </defs>
                  <rect width="100%%" height="100%%" fill="url(#bg)"/>
                  <g stroke="#94a3b8" stroke-width="1.2" stroke-linecap="round" opacity="0.5">
                    %s
                  </g>
                  <g fill="#334155" font-family="Arial, Helvetica, sans-serif" font-size="28" font-weight="700">
                    %s
                  </g>
                </svg>
                """.formatted(
                IMAGE_WIDTH,
                IMAGE_HEIGHT,
                IMAGE_WIDTH,
                IMAGE_HEIGHT,
                noiseLines,
                String.join("", characters)
        );
        return toDataUrl(svg);
    }

    private String renderNoiseLine(int x1, int y1, int x2, int y2) {
        return """
                <line x1="%d" y1="%d" x2="%d" y2="%d" />
                """.formatted(x1, y1, x2, y2);
    }

    private String renderCaptchaCharacter(char character, int index) {
        int x = 26 + index * 28 + secureRandom.nextInt(4);
        int y = 36 + secureRandom.nextInt(8);
        int rotate = secureRandom.nextInt(31) - 15;
        String fill = secureRandom.nextBoolean() ? "#1d4ed8" : "#0f172a";
        return """
                <text x="%d" y="%d" fill="%s" transform="rotate(%d %d %d)">%s</text>
                """.formatted(x, y, fill, rotate, x, y, escapeXml(String.valueOf(character)));
    }

    private String buildSliderBackgroundDataUrl(String backgroundElements) {
        String svg = buildSliderBackgroundSvg(backgroundElements);
        return toDataUrl(svg);
    }

    private String buildSliderPuzzleDataUrl(int expectedX, String backgroundElements) {
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d">
                  <defs>
                    <clipPath id="piece">
                      <path d="%s"/>
                    </clipPath>
                  </defs>
                  <g clip-path="url(#piece)">
                    <g transform="translate(-%d, 0)">
                      %s
                    </g>
                  </g>
                </svg>
                """.formatted(
                SLIDER_PUZZLE_WIDTH,
                SLIDER_PUZZLE_HEIGHT,
                SLIDER_PUZZLE_WIDTH,
                SLIDER_PUZZLE_HEIGHT,
                puzzlePath(),
                expectedX,
                backgroundElements
        );
        return toDataUrl(svg);
    }

    private String buildSliderBackgroundSvg(String backgroundElements) {
        return """
                <svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d">
                  %s
                </svg>
                """.formatted(
                SLIDER_WIDTH,
                SLIDER_HEIGHT,
                SLIDER_WIDTH,
                SLIDER_HEIGHT,
                backgroundElements
        );
    }

    private String buildSliderBackgroundElements() {
        StringBuilder shapes = new StringBuilder();
        shapes.append("""
                <defs>
                  <linearGradient id="slider-bg" x1="0%%" y1="0%%" x2="100%%" y2="100%%">
                    <stop offset="0%%" stop-color="#dbeafe"/>
                    <stop offset="100%%" stop-color="#eff6ff"/>
                  </linearGradient>
                  <linearGradient id="slider-accent" x1="0%%" y1="0%%" x2="100%%" y2="0%%">
                    <stop offset="0%%" stop-color="#93c5fd"/>
                    <stop offset="100%%" stop-color="#1d4ed8"/>
                  </linearGradient>
                </defs>
                <rect width="100%%" height="100%%" fill="url(#slider-bg)"/>
                <circle cx="48" cy="42" r="28" fill="#ffffff" opacity="0.38"/>
                <circle cx="280" cy="32" r="34" fill="#bfdbfe" opacity="0.5"/>
                <rect x="30" y="102" width="260" height="2" fill="url(#slider-accent)" opacity="0.22"/>
                <text x="26" y="92" fill="#0f172a" font-family="Arial, Helvetica, sans-serif" font-size="20" font-weight="700" opacity="0.2">人机验证</text>
                <text x="154" y="132" fill="#1e293b" font-family="Arial, Helvetica, sans-serif" font-size="14" opacity="0.16">Drag to verify</text>
                """);

        for (int index = 0; index < 8; index++) {
            int radius = 6 + secureRandom.nextInt(18);
            int cx = 10 + secureRandom.nextInt(SLIDER_WIDTH - 20);
            int cy = 12 + secureRandom.nextInt(SLIDER_HEIGHT - 24);
            String fill = index % 2 == 0 ? "#ffffff" : "#93c5fd";
            shapes.append("""
                    <circle cx="%d" cy="%d" r="%d" fill="%s" opacity="0.18"/>
                    """.formatted(cx, cy, radius, fill));
        }

        for (int index = 0; index < 5; index++) {
            int x = 20 + secureRandom.nextInt(SLIDER_WIDTH - 40);
            int y = 24 + secureRandom.nextInt(SLIDER_HEIGHT - 48);
            int width = 26 + secureRandom.nextInt(48);
            int height = 4 + secureRandom.nextInt(8);
            shapes.append("""
                    <rect x="%d" y="%d" width="%d" height="%d" rx="%d" fill="#ffffff" opacity="0.3"/>
                    """.formatted(x, y, width, height, Math.max(2, height / 2)));
        }

        return shapes.toString();
    }

    private String puzzlePath() {
        return "M0 12 " +
                "Q0 0 12 0 " +
                "H22 " +
                "Q30 0 30 8 " +
                "Q30 16 38 16 " +
                "Q46 16 46 8 " +
                "Q46 0 54 0 " +
                "H60 " +
                "V148 " +
                "Q60 160 48 160 " +
                "H38 " +
                "Q30 160 30 152 " +
                "Q30 144 22 144 " +
                "Q14 144 14 152 " +
                "Q14 160 6 160 " +
                "H0 Z";
    }

    private String normalizeCaptchaCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String normalizeCaptchaType(String value) {
        if (!StringUtils.hasText(value)) {
            return "IMAGE";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return "SLIDER".equals(normalized) ? "SLIDER" : "IMAGE";
    }

    private String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String toDataUrl(String svg) {
        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
    }

    private void save(CaptchaChallengeRecord record) {
        try {
            cacheTemplate.put(CacheKeyConstants.captchaKey(record.getCaptchaId()), objectMapper.writeValueAsString(record), CAPTCHA_TTL);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "验证码生成失败");
        }
    }

    private CaptchaChallengeRecord load(String captchaId) {
        String payload = cacheTemplate.get(CacheKeyConstants.captchaKey(captchaId));
        if (!StringUtils.hasText(payload)) {
            return null;
        }
        try {
            return objectMapper.readValue(payload, CaptchaChallengeRecord.class);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "验证码读取失败");
        }
    }

    private void remove(String captchaId) {
        cacheTemplate.remove(CacheKeyConstants.captchaKey(captchaId));
    }

    private SystemVO.CaptchaVerifyVO toVerifyVO(CaptchaChallengeRecord record) {
        SystemVO.CaptchaVerifyVO response = new SystemVO.CaptchaVerifyVO();
        response.setCaptchaId(record.getCaptchaId());
        response.setCaptchaProof(record.getProofToken());
        response.setExpiresInSeconds((int) CAPTCHA_TTL.toSeconds());
        return response;
    }

    public static class CaptchaChallengeRecord {
        private String captchaId;
        private String captchaType;
        private String answer;
        private Integer expectedX;
        private Integer expectedY;
        private Integer bgWidth;
        private Integer bgHeight;
        private Integer puzzleWidth;
        private Integer puzzleHeight;
        private Integer puzzleLeft;
        private Integer puzzleTop;
        private boolean verified;
        private String proofToken;
        private Instant createdAt;
        private Instant verifiedAt;

        public String getCaptchaId() {
            return captchaId;
        }

        public void setCaptchaId(String captchaId) {
            this.captchaId = captchaId;
        }

        public String getCaptchaType() {
            return captchaType;
        }

        public void setCaptchaType(String captchaType) {
            this.captchaType = captchaType;
        }

        public String getAnswer() {
            return answer;
        }

        public void setAnswer(String answer) {
            this.answer = answer;
        }

        public Integer getExpectedX() {
            return expectedX;
        }

        public void setExpectedX(Integer expectedX) {
            this.expectedX = expectedX;
        }

        public Integer getExpectedY() {
            return expectedY;
        }

        public void setExpectedY(Integer expectedY) {
            this.expectedY = expectedY;
        }

        public Integer getBgWidth() {
            return bgWidth;
        }

        public void setBgWidth(Integer bgWidth) {
            this.bgWidth = bgWidth;
        }

        public Integer getBgHeight() {
            return bgHeight;
        }

        public void setBgHeight(Integer bgHeight) {
            this.bgHeight = bgHeight;
        }

        public Integer getPuzzleWidth() {
            return puzzleWidth;
        }

        public void setPuzzleWidth(Integer puzzleWidth) {
            this.puzzleWidth = puzzleWidth;
        }

        public Integer getPuzzleHeight() {
            return puzzleHeight;
        }

        public void setPuzzleHeight(Integer puzzleHeight) {
            this.puzzleHeight = puzzleHeight;
        }

        public Integer getPuzzleLeft() {
            return puzzleLeft;
        }

        public void setPuzzleLeft(Integer puzzleLeft) {
            this.puzzleLeft = puzzleLeft;
        }

        public Integer getPuzzleTop() {
            return puzzleTop;
        }

        public void setPuzzleTop(Integer puzzleTop) {
            this.puzzleTop = puzzleTop;
        }

        public boolean isVerified() {
            return verified;
        }

        public void setVerified(boolean verified) {
            this.verified = verified;
        }

        public String getProofToken() {
            return proofToken;
        }

        public void setProofToken(String proofToken) {
            this.proofToken = proofToken;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
        }

        public Instant getVerifiedAt() {
            return verifiedAt;
        }

        public void setVerifiedAt(Instant verifiedAt) {
            this.verifiedAt = verifiedAt;
        }
    }
}

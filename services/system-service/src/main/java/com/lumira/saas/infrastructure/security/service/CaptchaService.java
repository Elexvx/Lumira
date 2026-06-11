package com.lumira.saas.infrastructure.security.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumira.saas.common.constant.CacheKeyConstants;
import com.lumira.common.enums.ErrorCode;
import com.lumira.common.exception.BizException;
import com.lumira.saas.infrastructure.redis.CacheTemplate;
import com.lumira.saas.modules.system.dto.SystemDTO;
import com.lumira.saas.modules.system.vo.SystemVO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
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
    private static final int SLIDER_PUZZLE_WIDTH = 58;
    private static final int SLIDER_PUZZLE_HEIGHT = 58;
    private static final int SLIDER_TOLERANCE_PX = 10;
    private static final int SLIDER_MIN_DURATION_MS = 180;
    private static final String CAPTCHA_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final List<String> SLIDER_BACKGROUND_RESOURCES = List.of(
            "captcha/slider-backgrounds/mountain-valley.png",
            "captcha/slider-backgrounds/neon-city.png",
            "captcha/slider-backgrounds/botanical-glasshouse.png",
            "captcha/slider-backgrounds/coastal-harbor.png"
    );

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
        record.setExpectedX(generateSliderExpectedX());
        int puzzleTop = generateSliderPuzzleTop();
        String sceneElements = buildSliderSceneElements();
        record.setExpectedY(0);
        record.setPuzzleWidth(SLIDER_PUZZLE_WIDTH);
        record.setPuzzleHeight(SLIDER_PUZZLE_HEIGHT);
        record.setBgWidth(SLIDER_WIDTH);
        record.setBgHeight(SLIDER_HEIGHT);
        record.setPuzzleLeft(0);
        record.setPuzzleTop(puzzleTop);
        response.setBgWidth(SLIDER_WIDTH);
        response.setBgHeight(SLIDER_HEIGHT);
        response.setPuzzleWidth(SLIDER_PUZZLE_WIDTH);
        response.setPuzzleHeight(SLIDER_PUZZLE_HEIGHT);
        response.setPuzzleLeft(0);
        response.setPuzzleTop(puzzleTop);
        response.setBgUrl(buildSliderBackgroundDataUrl(record.getExpectedX(), puzzleTop, sceneElements));
        response.setPuzzleUrl(buildSliderPuzzleDataUrl(record.getExpectedX(), puzzleTop, sceneElements));
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
        if (Double.isNaN(actualX) || Double.isNaN(actualY)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "拖动验证失败，请重新尝试");
        }
        if (Math.abs(actualX - expectedX) > SLIDER_TOLERANCE_PX) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "拖动验证失败，请重新尝试");
        }
    }

    private int generateSliderExpectedX() {
        int min = 76;
        int max = SLIDER_WIDTH - SLIDER_PUZZLE_WIDTH - 20;
        return min + secureRandom.nextInt(Math.max(max - min + 1, 1));
    }

    private int generateSliderPuzzleTop() {
        int min = 36;
        int max = SLIDER_HEIGHT - SLIDER_PUZZLE_HEIGHT - 18;
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

    private String buildSliderBackgroundDataUrl(int expectedX, int puzzleTop, String sceneElements) {
        String svg = buildSliderBackgroundSvg(expectedX, puzzleTop, sceneElements);
        return toDataUrl(svg);
    }

    private String buildSliderPuzzleDataUrl(int expectedX, int puzzleTop, String sceneElements) {
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d">
                  <defs>
                    <clipPath id="piece">
                      <path d="%s"/>
                    </clipPath>
                    <filter id="piece-depth" x="-20%%" y="-20%%" width="140%%" height="140%%">
                      <feDropShadow dx="0" dy="2" stdDeviation="2" flood-color="#0f172a" flood-opacity="0.32"/>
                    </filter>
                  </defs>
                  <g clip-path="url(#piece)" filter="url(#piece-depth)">
                    <g transform="translate(-%d, -%d)">
                      %s
                    </g>
                    <path d="%s" fill="none" stroke="#ffffff" stroke-width="2" opacity="0.82"/>
                    <path d="%s" fill="none" stroke="#2563eb" stroke-width="1.2" opacity="0.48"/>
                  </g>
                </svg>
                """.formatted(
                SLIDER_PUZZLE_WIDTH,
                SLIDER_PUZZLE_HEIGHT,
                SLIDER_PUZZLE_WIDTH,
                SLIDER_PUZZLE_HEIGHT,
                puzzlePath(),
                expectedX,
                puzzleTop,
                sceneElements,
                puzzlePath(),
                puzzlePath()
        );
        return toDataUrl(svg);
    }

    private String buildSliderBackgroundSvg(int expectedX, int puzzleTop, String sceneElements) {
        return """
                <svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d">
                  %s
                  <defs>
                    <clipPath id="piece-hole">
                      <path d="%s"/>
                    </clipPath>
                    <filter id="hole-shadow" x="-20%%" y="-20%%" width="140%%" height="140%%">
                      <feDropShadow dx="0" dy="1" stdDeviation="1.5" flood-color="#0f172a" flood-opacity="0.36"/>
                    </filter>
                  </defs>
                  <g transform="translate(%d, %d)" filter="url(#hole-shadow)">
                    <path d="%s" fill="#0f172a" opacity="0.22"/>
                    <path d="%s" fill="none" stroke="#ffffff" stroke-width="2.2" opacity="0.78"/>
                    <path d="%s" fill="none" stroke="#1d4ed8" stroke-width="1.2" opacity="0.52"/>
                  </g>
                </svg>
                """.formatted(
                SLIDER_WIDTH,
                SLIDER_HEIGHT,
                SLIDER_WIDTH,
                SLIDER_HEIGHT,
                sceneElements,
                puzzlePath(),
                expectedX,
                puzzleTop,
                puzzlePath(),
                puzzlePath(),
                puzzlePath()
        );
    }

    private String buildSliderSceneElements() {
        String imageDataUrl = loadRandomSliderBackgroundDataUrl();
        if (StringUtils.hasText(imageDataUrl)) {
            return """
                    <image x="0" y="0" width="%d" height="%d" href="%s" preserveAspectRatio="none"/>
                    """.formatted(SLIDER_WIDTH, SLIDER_HEIGHT, imageDataUrl);
        }
        return buildFallbackSliderSceneElements();
    }

    private String loadRandomSliderBackgroundDataUrl() {
        String resourcePath = SLIDER_BACKGROUND_RESOURCES.get(secureRandom.nextInt(SLIDER_BACKGROUND_RESOURCES.size()));
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = CaptchaService.class.getClassLoader();
        }
        try (InputStream inputStream = classLoader == null ? null : classLoader.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                return "";
            }
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(inputStream.readAllBytes());
        } catch (IOException ex) {
            return "";
        }
    }

    private String buildFallbackSliderSceneElements() {
        StringBuilder shapes = new StringBuilder();
        shapes.append("""
                <defs>
                  <linearGradient id="sky" x1="0%%" y1="0%%" x2="0%%" y2="100%%">
                    <stop offset="0%%" stop-color="#8ec5ff"/>
                    <stop offset="55%%" stop-color="#d8efff"/>
                    <stop offset="100%%" stop-color="#f8fafc"/>
                  </linearGradient>
                  <linearGradient id="field" x1="0%%" y1="0%%" x2="100%%" y2="0%%">
                    <stop offset="0%%" stop-color="#67c587"/>
                    <stop offset="52%%" stop-color="#a3d977"/>
                    <stop offset="100%%" stop-color="#4f9f7b"/>
                  </linearGradient>
                </defs>
                <rect width="100%%" height="100%%" fill="url(#sky)"/>
                <circle cx="268" cy="34" r="19" fill="#fff7ad" opacity="0.94"/>
                <ellipse cx="58" cy="38" rx="30" ry="11" fill="#ffffff" opacity="0.72"/>
                <ellipse cx="86" cy="35" rx="24" ry="9" fill="#ffffff" opacity="0.6"/>
                <ellipse cx="205" cy="48" rx="34" ry="12" fill="#ffffff" opacity="0.52"/>
                <path d="M0 104 C34 78 62 84 96 58 C132 30 168 48 206 30 C244 12 282 26 320 8 L320 160 L0 160 Z" fill="#4f8f99" opacity="0.82"/>
                <path d="M0 118 C46 96 72 102 112 86 C154 68 190 78 228 58 C270 36 296 42 320 30 L320 160 L0 160 Z" fill="#7fb68d" opacity="0.92"/>
                <path d="M0 132 C54 114 102 126 148 108 C202 86 242 104 320 78 L320 160 L0 160 Z" fill="url(#field)"/>
                <path d="M0 148 C54 136 112 148 166 132 C224 116 270 130 320 112 L320 160 L0 160 Z" fill="#2f7d58" opacity="0.76"/>
                """);

        for (int index = 0; index < 18; index++) {
            int radius = 2 + secureRandom.nextInt(5);
            int cx = 8 + secureRandom.nextInt(SLIDER_WIDTH - 16);
            int cy = 96 + secureRandom.nextInt(SLIDER_HEIGHT - 102);
            String fill = index % 3 == 0 ? "#fef3c7" : (index % 3 == 1 ? "#ffffff" : "#14532d");
            shapes.append("""
                    <circle cx="%d" cy="%d" r="%d" fill="%s" opacity="0.18"/>
                    """.formatted(cx, cy, radius, fill));
        }

        for (int index = 0; index < 10; index++) {
            int x = 10 + secureRandom.nextInt(SLIDER_WIDTH - 20);
            int y = 110 + secureRandom.nextInt(SLIDER_HEIGHT - 116);
            int width = 18 + secureRandom.nextInt(42);
            int height = 2 + secureRandom.nextInt(5);
            shapes.append("""
                    <rect x="%d" y="%d" width="%d" height="%d" rx="%d" fill="#ffffff" opacity="0.16"/>
                    """.formatted(x, y, width, height, Math.max(2, height / 2)));
        }

        return shapes.toString();
    }

    private String puzzlePath() {
        return "M0 8 " +
                "Q0 0 8 0 " +
                "H20 " +
                "Q28 0 28 8 " +
                "Q28 16 36 16 " +
                "Q44 16 44 8 " +
                "Q44 0 52 0 " +
                "H58 " +
                "V20 " +
                "Q58 28 50 28 " +
                "Q42 28 42 36 " +
                "Q42 44 50 44 " +
                "Q58 44 58 52 " +
                "V58 " +
                "H38 " +
                "Q30 58 30 50 " +
                "Q30 42 22 42 " +
                "Q14 42 14 50 " +
                "Q14 58 6 58 " +
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

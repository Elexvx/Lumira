package com.lumira.saas.modules.competition.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;

@Service
public class CertificateRenderService {
    private final ObjectMapper objectMapper;

    public CertificateRenderService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void renderPng(String canvasJson, String backgroundUrl, Map<String, Object> data, Path outputPath) {
        try {
            JsonNode root = objectMapper.readTree(canvasJson);
            JsonNode page = root.path("page");
            int width = page.path("width").asInt(3508);
            int height = page.path("height").asInt(2480);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            paintBackground(graphics, backgroundUrl, width, height);
            for (JsonNode element : root.path("elements")) {
                String type = element.path("type").asText();
                if ("qrcode".equalsIgnoreCase(type)) {
                    paintQrPlaceholder(graphics, element, resolveValue(element, data));
                } else {
                    paintText(graphics, element, resolveValue(element, data));
                }
            }
            graphics.dispose();
            Files.createDirectories(outputPath.getParent());
            ImageIO.write(image, "png", outputPath.toFile());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to render certificate PNG", exception);
        }
    }

    private void paintBackground(Graphics2D graphics, String backgroundUrl, int width, int height) {
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        if (!StringUtils.hasText(backgroundUrl)) {
            return;
        }
        try {
            Path localBackground = resolveTrustedLocalBackgroundPath(backgroundUrl);
            if (localBackground == null) {
                return;
            }
            BufferedImage background = ImageIO.read(localBackground.toFile());
            if (background != null) {
                graphics.drawImage(background, 0, 0, width, height, null);
            }
        } catch (Exception ignored) {
            graphics.setColor(new Color(248, 250, 252));
            graphics.fillRect(0, 0, width, height);
        }
    }

    static Path resolveTrustedLocalBackgroundPath(String backgroundUrl) {
        if (!StringUtils.hasText(backgroundUrl)) {
            return null;
        }
        String normalizedValue = backgroundUrl.trim().replace('\\', '/');
        if (normalizedValue.contains("://") || normalizedValue.startsWith("//")) {
            return null;
        }
        if (normalizedValue.startsWith("/")) {
            normalizedValue = normalizedValue.substring(1);
        }
        try {
            Path path = Path.of(normalizedValue).normalize();
            if (path.isAbsolute() || path.startsWith("..")) {
                return null;
            }
            return path.startsWith("storage") || path.startsWith("uploads") ? path : null;
        } catch (InvalidPathException exception) {
            return null;
        }
    }

    private String resolveValue(JsonNode element, Map<String, Object> data) {
        String fieldKey = element.path("fieldKey").asText("");
        if (StringUtils.hasText(fieldKey) && data != null && data.containsKey(fieldKey)) {
            Object value = data.get(fieldKey);
            return value == null ? "" : String.valueOf(value);
        }
        String text = element.path("text").asText(element.path("placeholder").asText(""));
        if (text.startsWith("${") && text.endsWith("}") && text.length() > 3) {
            String key = text.substring(2, text.length() - 1);
            Object value = data == null ? null : data.get(key);
            return value == null ? "" : String.valueOf(value);
        }
        return text;
    }

    private void paintText(Graphics2D graphics, JsonNode element, String value) {
        int x = element.path("x").asInt();
        int y = element.path("y").asInt();
        int width = element.path("width").asInt(800);
        int height = element.path("height").asInt(120);
        int fontSize = element.path("fontSize").asInt(56);
        String fontFamily = element.path("fontFamily").asText("Microsoft YaHei");
        int style = "bold".equalsIgnoreCase(element.path("fontWeight").asText()) ? Font.BOLD : Font.PLAIN;
        graphics.setColor(parseColor(element.path("color").asText("#222222")));
        graphics.setFont(new Font(fontFamily, style, fontSize));
        FontMetrics metrics = graphics.getFontMetrics();
        String text = value == null ? "" : value;
        String align = element.path("textAlign").asText("left");
        int textX = x;
        if ("center".equalsIgnoreCase(align)) {
            textX = x + Math.max(0, (width - metrics.stringWidth(text)) / 2);
        } else if ("right".equalsIgnoreCase(align)) {
            textX = x + Math.max(0, width - metrics.stringWidth(text));
        }
        int textY = y + Math.max(metrics.getAscent(), (height + metrics.getAscent() - metrics.getDescent()) / 2);
        graphics.drawString(text, textX, textY);
    }

    private void paintQrPlaceholder(Graphics2D graphics, JsonNode element, String value) {
        int x = element.path("x").asInt();
        int y = element.path("y").asInt();
        int width = element.path("width").asInt(220);
        int height = element.path("height").asInt(width);
        try {
            Map<EncodeHintType, Object> hints = Map.of(EncodeHintType.MARGIN, 1);
            BufferedImage qr = MatrixToImageWriter.toBufferedImage(
                    new QRCodeWriter().encode(StringUtils.hasText(value) ? value : "about:blank", BarcodeFormat.QR_CODE, width, height, hints)
            );
            graphics.drawImage(qr, x, y, width, height, null);
        } catch (Exception exception) {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(x, y, width, height);
            graphics.setColor(Color.BLACK);
            graphics.drawRect(x, y, width, height);
        }
    }

    private Color parseColor(String value) {
        try {
            return Color.decode(value);
        } catch (NumberFormatException ignored) {
            return new Color(34, 34, 34);
        }
    }
}

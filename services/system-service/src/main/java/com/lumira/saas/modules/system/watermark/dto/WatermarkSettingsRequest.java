package com.lumira.saas.modules.system.watermark.dto;

import java.util.List;

public class WatermarkSettingsRequest {

    private Boolean enabled;
    private String mode;
    private List<String> textLines;
    private String imageUrl;
    private String fontColor;
    private Integer fontSize;
    private String fontWeight;
    private Integer rotate;
    private Integer gapX;
    private Integer gapY;
    private Integer offsetX;
    private Integer offsetY;
    private Integer zIndex;
    private Double opacity;

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public List<String> getTextLines() { return textLines; }
    public void setTextLines(List<String> textLines) { this.textLines = textLines; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getFontColor() { return fontColor; }
    public void setFontColor(String fontColor) { this.fontColor = fontColor; }
    public Integer getFontSize() { return fontSize; }
    public void setFontSize(Integer fontSize) { this.fontSize = fontSize; }
    public String getFontWeight() { return fontWeight; }
    public void setFontWeight(String fontWeight) { this.fontWeight = fontWeight; }
    public Integer getRotate() { return rotate; }
    public void setRotate(Integer rotate) { this.rotate = rotate; }
    public Integer getGapX() { return gapX; }
    public void setGapX(Integer gapX) { this.gapX = gapX; }
    public Integer getGapY() { return gapY; }
    public void setGapY(Integer gapY) { this.gapY = gapY; }
    public Integer getOffsetX() { return offsetX; }
    public void setOffsetX(Integer offsetX) { this.offsetX = offsetX; }
    public Integer getOffsetY() { return offsetY; }
    public void setOffsetY(Integer offsetY) { this.offsetY = offsetY; }
    public Integer getZIndex() { return zIndex; }
    public void setZIndex(Integer zIndex) { this.zIndex = zIndex; }
    public Double getOpacity() { return opacity; }
    public void setOpacity(Double opacity) { this.opacity = opacity; }
}

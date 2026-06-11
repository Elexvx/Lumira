package com.lumira.saas.modules.system.captcha.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public class CaptchaSliderVerifyRequest {

    @NotBlank
    private String captchaId;
    @NotNull
    private Double x;
    @NotNull
    private Double y;
    @NotNull
    private Double sliderOffsetX;
    @NotNull
    @Positive
    private Long duration;
    private List<List<Double>> trail;
    private String targetType;
    private Integer errorCount;

    public String getCaptchaId() { return captchaId; }
    public void setCaptchaId(String captchaId) { this.captchaId = captchaId; }
    public Double getX() { return x; }
    public void setX(Double x) { this.x = x; }
    public Double getY() { return y; }
    public void setY(Double y) { this.y = y; }
    public Double getSliderOffsetX() { return sliderOffsetX; }
    public void setSliderOffsetX(Double sliderOffsetX) { this.sliderOffsetX = sliderOffsetX; }
    public Long getDuration() { return duration; }
    public void setDuration(Long duration) { this.duration = duration; }
    public List<List<Double>> getTrail() { return trail; }
    public void setTrail(List<List<Double>> trail) { this.trail = trail; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public Integer getErrorCount() { return errorCount; }
    public void setErrorCount(Integer errorCount) { this.errorCount = errorCount; }
}

package com.lumira.saas.modules.system.captcha.vo;

public class CaptchaChallengeVO {

    private String captchaId;
    private String captchaType;
    private String imageUrl;
    private String bgUrl;
    private String puzzleUrl;
    private Integer bgWidth;
    private Integer bgHeight;
    private Integer puzzleWidth;
    private Integer puzzleHeight;
    private Integer puzzleLeft;
    private Integer puzzleTop;
    private Integer expiresInSeconds;

    public String getCaptchaId() { return captchaId; }
    public void setCaptchaId(String captchaId) { this.captchaId = captchaId; }
    public String getCaptchaType() { return captchaType; }
    public void setCaptchaType(String captchaType) { this.captchaType = captchaType; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getBgUrl() { return bgUrl; }
    public void setBgUrl(String bgUrl) { this.bgUrl = bgUrl; }
    public String getPuzzleUrl() { return puzzleUrl; }
    public void setPuzzleUrl(String puzzleUrl) { this.puzzleUrl = puzzleUrl; }
    public Integer getBgWidth() { return bgWidth; }
    public void setBgWidth(Integer bgWidth) { this.bgWidth = bgWidth; }
    public Integer getBgHeight() { return bgHeight; }
    public void setBgHeight(Integer bgHeight) { this.bgHeight = bgHeight; }
    public Integer getPuzzleWidth() { return puzzleWidth; }
    public void setPuzzleWidth(Integer puzzleWidth) { this.puzzleWidth = puzzleWidth; }
    public Integer getPuzzleHeight() { return puzzleHeight; }
    public void setPuzzleHeight(Integer puzzleHeight) { this.puzzleHeight = puzzleHeight; }
    public Integer getPuzzleLeft() { return puzzleLeft; }
    public void setPuzzleLeft(Integer puzzleLeft) { this.puzzleLeft = puzzleLeft; }
    public Integer getPuzzleTop() { return puzzleTop; }
    public void setPuzzleTop(Integer puzzleTop) { this.puzzleTop = puzzleTop; }
    public Integer getExpiresInSeconds() { return expiresInSeconds; }
    public void setExpiresInSeconds(Integer expiresInSeconds) { this.expiresInSeconds = expiresInSeconds; }
}

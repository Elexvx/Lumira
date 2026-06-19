package com.lumira.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lumira.file.ocr")
public class FileOcrProperties {

    public enum Mode {
        DISABLED,
        TESSERACT
    }

    private Mode mode = Mode.DISABLED;
    private String tesseractCommand = "tesseract";
    private String languages = "eng+chi_sim";
    private int timeoutMillis = 5000;

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode == null ? Mode.DISABLED : mode;
    }

    public String getTesseractCommand() {
        return hasText(tesseractCommand) ? tesseractCommand : "tesseract";
    }

    public void setTesseractCommand(String tesseractCommand) {
        this.tesseractCommand = tesseractCommand;
    }

    public String getLanguages() {
        return hasText(languages) ? languages : "eng+chi_sim";
    }

    public void setLanguages(String languages) {
        this.languages = languages;
    }

    public int getTimeoutMillis() {
        return timeoutMillis <= 0 ? 5000 : timeoutMillis;
    }

    public void setTimeoutMillis(int timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

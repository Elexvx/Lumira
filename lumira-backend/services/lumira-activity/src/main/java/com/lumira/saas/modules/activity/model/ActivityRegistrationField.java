package com.lumira.saas.modules.activity.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public class ActivityRegistrationField {
    @NotBlank
    @Size(max = 64)
    private String fieldKey;
    @NotBlank
    @Size(max = 128)
    private String label;
    @NotBlank
    @Size(max = 32)
    private String fieldType;
    @Size(max = 255)
    private String placeholder;
    @Size(max = 500)
    private String description;
    private Boolean required;
    @Size(max = 100)
    private List<@NotBlank @Size(max = 128) String> options;

    public String getFieldKey() { return fieldKey; }
    public void setFieldKey(String fieldKey) { this.fieldKey = fieldKey; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getFieldType() { return fieldType; }
    public void setFieldType(String fieldType) { this.fieldType = fieldType; }
    public String getPlaceholder() { return placeholder; }
    public void setPlaceholder(String placeholder) { this.placeholder = placeholder; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Boolean getRequired() { return required; }
    public void setRequired(Boolean required) { this.required = required; }
    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = options; }
}

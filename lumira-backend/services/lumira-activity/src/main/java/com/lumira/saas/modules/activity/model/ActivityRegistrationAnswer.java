package com.lumira.saas.modules.activity.model;

public class ActivityRegistrationAnswer {
    private String fieldKey;
    private String label;
    private String fieldType;
    private Object value;

    public ActivityRegistrationAnswer() {
    }

    public ActivityRegistrationAnswer(String fieldKey, String label, String fieldType, Object value) {
        this.fieldKey = fieldKey;
        this.label = label;
        this.fieldType = fieldType;
        this.value = value;
    }

    public String getFieldKey() { return fieldKey; }
    public void setFieldKey(String fieldKey) { this.fieldKey = fieldKey; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getFieldType() { return fieldType; }
    public void setFieldType(String fieldType) { this.fieldType = fieldType; }
    public Object getValue() { return value; }
    public void setValue(Object value) { this.value = value; }
}

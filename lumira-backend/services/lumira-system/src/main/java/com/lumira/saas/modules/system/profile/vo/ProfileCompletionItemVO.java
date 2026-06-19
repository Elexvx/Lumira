package com.lumira.saas.modules.system.profile.vo;

public class ProfileCompletionItemVO {

    private String fieldKey;
    private String fieldLabel;
    private String fieldDescription;
    private String groupKey;
    private String groupLabel;
    private Boolean completed;
    private Integer weight;
    private Integer scoreContribution;
    private String valueText;
    private String actionType;
    private Boolean actionAvailable;
    private String actionTarget;
    private String actionLabel;
    private String actionHint;

    public String getFieldKey() {
        return fieldKey;
    }

    public void setFieldKey(String fieldKey) {
        this.fieldKey = fieldKey;
    }

    public String getFieldLabel() {
        return fieldLabel;
    }

    public void setFieldLabel(String fieldLabel) {
        this.fieldLabel = fieldLabel;
    }

    public String getFieldDescription() {
        return fieldDescription;
    }

    public void setFieldDescription(String fieldDescription) {
        this.fieldDescription = fieldDescription;
    }

    public String getGroupKey() {
        return groupKey;
    }

    public void setGroupKey(String groupKey) {
        this.groupKey = groupKey;
    }

    public String getGroupLabel() {
        return groupLabel;
    }

    public void setGroupLabel(String groupLabel) {
        this.groupLabel = groupLabel;
    }

    public Boolean getCompleted() {
        return completed;
    }

    public void setCompleted(Boolean completed) {
        this.completed = completed;
    }

    public Integer getWeight() {
        return weight;
    }

    public void setWeight(Integer weight) {
        this.weight = weight;
    }

    public Integer getScoreContribution() {
        return scoreContribution;
    }

    public void setScoreContribution(Integer scoreContribution) {
        this.scoreContribution = scoreContribution;
    }

    public String getValueText() {
        return valueText;
    }

    public void setValueText(String valueText) {
        this.valueText = valueText;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public Boolean getActionAvailable() {
        return actionAvailable;
    }

    public void setActionAvailable(Boolean actionAvailable) {
        this.actionAvailable = actionAvailable;
    }

    public String getActionTarget() {
        return actionTarget;
    }

    public void setActionTarget(String actionTarget) {
        this.actionTarget = actionTarget;
    }

    public String getActionLabel() {
        return actionLabel;
    }

    public void setActionLabel(String actionLabel) {
        this.actionLabel = actionLabel;
    }

    public String getActionHint() {
        return actionHint;
    }

    public void setActionHint(String actionHint) {
        this.actionHint = actionHint;
    }
}

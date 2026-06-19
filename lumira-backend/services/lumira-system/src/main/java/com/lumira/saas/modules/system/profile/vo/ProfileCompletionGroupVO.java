package com.lumira.saas.modules.system.profile.vo;

import java.util.List;

public class ProfileCompletionGroupVO {

    private String groupKey;
    private String groupLabel;
    private Integer score;
    private Integer maxScore;
    private Integer completionRate;
    private Integer totalWeight;
    private Integer earnedWeight;
    private List<ProfileCompletionItemVO> items;

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

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public Integer getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(Integer maxScore) {
        this.maxScore = maxScore;
    }

    public Integer getCompletionRate() {
        return completionRate;
    }

    public void setCompletionRate(Integer completionRate) {
        this.completionRate = completionRate;
    }

    public Integer getTotalWeight() {
        return totalWeight;
    }

    public void setTotalWeight(Integer totalWeight) {
        this.totalWeight = totalWeight;
    }

    public Integer getEarnedWeight() {
        return earnedWeight;
    }

    public void setEarnedWeight(Integer earnedWeight) {
        this.earnedWeight = earnedWeight;
    }

    public List<ProfileCompletionItemVO> getItems() {
        return items;
    }

    public void setItems(List<ProfileCompletionItemVO> items) {
        this.items = items;
    }
}

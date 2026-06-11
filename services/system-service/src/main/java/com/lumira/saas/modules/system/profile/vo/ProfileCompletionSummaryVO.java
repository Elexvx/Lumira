package com.lumira.saas.modules.system.profile.vo;

import java.util.List;

public class ProfileCompletionSummaryVO {

    private Integer score;
    private Integer maxScore;
    private Integer completionRate;
    private Integer totalWeight;
    private Integer earnedWeight;
    private List<ProfileCompletionGroupVO> groups;
    private List<ProfileCompletionItemVO> incompleteItems;

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

    public List<ProfileCompletionGroupVO> getGroups() {
        return groups;
    }

    public void setGroups(List<ProfileCompletionGroupVO> groups) {
        this.groups = groups;
    }

    public List<ProfileCompletionItemVO> getIncompleteItems() {
        return incompleteItems;
    }

    public void setIncompleteItems(List<ProfileCompletionItemVO> incompleteItems) {
        this.incompleteItems = incompleteItems;
    }
}

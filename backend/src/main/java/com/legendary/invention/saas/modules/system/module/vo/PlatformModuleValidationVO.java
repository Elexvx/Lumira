package com.legendary.invention.saas.modules.system.module.vo;

import java.util.List;

public class PlatformModuleValidationVO {

    private boolean valid;
    private boolean duplicateModuleCode;
    private List<String> issues;
    private List<String> warnings;
    private List<String> missingDependencies;
    private List<String> inactiveDependencies;
    private List<String> cyclePath;

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public boolean isDuplicateModuleCode() {
        return duplicateModuleCode;
    }

    public void setDuplicateModuleCode(boolean duplicateModuleCode) {
        this.duplicateModuleCode = duplicateModuleCode;
    }

    public List<String> getIssues() {
        return issues;
    }

    public void setIssues(List<String> issues) {
        this.issues = issues;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public List<String> getMissingDependencies() {
        return missingDependencies;
    }

    public void setMissingDependencies(List<String> missingDependencies) {
        this.missingDependencies = missingDependencies;
    }

    public List<String> getInactiveDependencies() {
        return inactiveDependencies;
    }

    public void setInactiveDependencies(List<String> inactiveDependencies) {
        this.inactiveDependencies = inactiveDependencies;
    }

    public List<String> getCyclePath() {
        return cyclePath;
    }

    public void setCyclePath(List<String> cyclePath) {
        this.cyclePath = cyclePath;
    }
}

package com.lumira.saas.modules.competition.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

public final class CompetitionRegistrationDTO {
    private CompetitionRegistrationDTO() {
    }

    public static class RegistrationCreateRequest {
        @NotNull
        private Long competitionId;
        @NotNull
        private Long teamId;
        @NotNull
        private Long projectId;

        public Long getCompetitionId() { return competitionId; }
        public void setCompetitionId(Long competitionId) { this.competitionId = competitionId; }
        public Long getTeamId() { return teamId; }
        public void setTeamId(Long teamId) { this.teamId = teamId; }
        public Long getProjectId() { return projectId; }
        public void setProjectId(Long projectId) { this.projectId = projectId; }
    }

    public static class StageUpsertRequest {
        @NotBlank
        @Size(max = 32)
        private String stageCode;
        @NotBlank
        @Size(max = 128)
        private String stageName;
        @Size(max = 32)
        private String status;
        private Integer sort;

        public String getStageCode() { return stageCode; }
        public void setStageCode(String stageCode) { this.stageCode = stageCode; }
        public String getStageName() { return stageName; }
        public void setStageName(String stageName) { this.stageName = stageName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Integer getSort() { return sort; }
        public void setSort(Integer sort) { this.sort = sort; }
    }

    public static class StageFormUpsertRequest {
        @NotBlank
        @Size(max = 128)
        private String formName;
        @NotBlank
        private String formSchemaJson;
        private Integer version;
        @Size(max = 32)
        private String status;

        public String getFormName() { return formName; }
        public void setFormName(String formName) { this.formName = formName; }
        public String getFormSchemaJson() { return formSchemaJson; }
        public void setFormSchemaJson(String formSchemaJson) { this.formSchemaJson = formSchemaJson; }
        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class MaterialSubmitRequest {
        @NotNull
        private Long stageId;
        @Valid
        private List<MaterialValueRequest> values = new ArrayList<>();

        public Long getStageId() { return stageId; }
        public void setStageId(Long stageId) { this.stageId = stageId; }
        public List<MaterialValueRequest> getValues() { return values; }
        public void setValues(List<MaterialValueRequest> values) { this.values = values == null ? new ArrayList<>() : values; }
    }

    public static class MaterialValueRequest {
        @NotBlank
        @Size(max = 128)
        private String fieldKey;
        @NotBlank
        @Size(max = 32)
        private String fieldType;
        private String textValue;
        private Long fileId;
        private String jsonValue;

        public String getFieldKey() { return fieldKey; }
        public void setFieldKey(String fieldKey) { this.fieldKey = fieldKey; }
        public String getFieldType() { return fieldType; }
        public void setFieldType(String fieldType) { this.fieldType = fieldType; }
        public String getTextValue() { return textValue; }
        public void setTextValue(String textValue) { this.textValue = textValue; }
        public Long getFileId() { return fileId; }
        public void setFileId(Long fileId) { this.fileId = fileId; }
        public String getJsonValue() { return jsonValue; }
        public void setJsonValue(String jsonValue) { this.jsonValue = jsonValue; }
    }

    public static class PaymentOrderRequest {
        @Size(max = 64)
        private String providerCode;
        @Size(max = 64)
        private String clientIp;
        @Size(max = 1024)
        private String notifyUrl;
        @Size(max = 1024)
        private String returnUrl;

        public String getProviderCode() { return providerCode; }
        public void setProviderCode(String providerCode) { this.providerCode = providerCode; }
        public String getClientIp() { return clientIp; }
        public void setClientIp(String clientIp) { this.clientIp = clientIp; }
        public String getNotifyUrl() { return notifyUrl; }
        public void setNotifyUrl(String notifyUrl) { this.notifyUrl = notifyUrl; }
        public String getReturnUrl() { return returnUrl; }
        public void setReturnUrl(String returnUrl) { this.returnUrl = returnUrl; }
    }
}

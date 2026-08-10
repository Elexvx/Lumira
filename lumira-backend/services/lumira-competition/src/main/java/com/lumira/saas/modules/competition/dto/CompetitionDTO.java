package com.lumira.saas.modules.competition.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;

import java.util.List;

public final class CompetitionDTO {
    private CompetitionDTO() {
    }

    public static class CompetitionUpsertRequest {
        /**
         * Full create requires the fields that identify a publishable
         * competition. The group extends {@link Default} so the shared shape
         * constraints continue to apply.
         */
        public interface Create extends Default {
        }

        /**
         * Full updates may rely on persisted values for required fields, but
         * still validate supplied field sizes.
         */
        public interface Update extends Default {
        }

        /**
         * Draft saves validate supplied field shapes without imposing formal
         * create requirements, so an incomplete draft remains resumable.
         */
        public interface Draft extends Default {
        }

        @Size(max = 64)
        private String code;
        @Size(max = 64)
        private String locale;
        @NotBlank(groups = Create.class)
        @Size(max = 128)
        private String title;
        @Size(max = 128)
        private String shortName;
        @NotBlank(groups = Create.class)
        @Size(max = 64)
        private String category;
        @Size(max = 64)
        private String level;
        @Size(max = 64)
        private String competitionLevel;
        @Size(max = 128)
        private String organizer;
        private String organizersJson;
        @Size(max = 64)
        private String registrationStart;
        @Size(max = 64)
        private String registrationEnd;
        @NotBlank(groups = Create.class)
        @Size(max = 64)
        private String competitionStart;
        @Size(max = 64)
        private String competitionEnd;
        @NotBlank(groups = Create.class)
        @Size(max = 255)
        private String location;
        @Size(max = 255)
        private String participationScope;
        private String participationRequirement;
        private String scheduleJson;
        @Size(max = 1000)
        private String description;
        @Size(max = 512)
        private String imageUrl;
        @Size(max = 128)
        private String contactName;
        @Size(max = 512)
        private String contactQrCodeUrl;
        @Size(max = 1000)
        private String tags;
        @Size(max = 32)
        private String status;
        @Size(max = 16)
        private String feeMode;
        private Long entryFeeMinor;
        @Size(max = 16)
        private String currency;
        private Boolean featured;
        private Integer sort;

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getLocale() { return locale; }
        public void setLocale(String locale) { this.locale = locale; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getShortName() { return shortName; }
        public void setShortName(String shortName) { this.shortName = shortName; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
        public String getCompetitionLevel() { return competitionLevel; }
        public void setCompetitionLevel(String competitionLevel) { this.competitionLevel = competitionLevel; }
        public String getOrganizer() { return organizer; }
        public void setOrganizer(String organizer) { this.organizer = organizer; }
        public String getOrganizersJson() { return organizersJson; }
        public void setOrganizersJson(String organizersJson) { this.organizersJson = organizersJson; }
        public String getRegistrationStart() { return registrationStart; }
        public void setRegistrationStart(String registrationStart) { this.registrationStart = registrationStart; }
        public String getRegistrationEnd() { return registrationEnd; }
        public void setRegistrationEnd(String registrationEnd) { this.registrationEnd = registrationEnd; }
        public String getCompetitionStart() { return competitionStart; }
        public void setCompetitionStart(String competitionStart) { this.competitionStart = competitionStart; }
        public String getCompetitionEnd() { return competitionEnd; }
        public void setCompetitionEnd(String competitionEnd) { this.competitionEnd = competitionEnd; }
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        public String getParticipationScope() { return participationScope; }
        public void setParticipationScope(String participationScope) { this.participationScope = participationScope; }
        public String getParticipationRequirement() { return participationRequirement; }
        public void setParticipationRequirement(String participationRequirement) { this.participationRequirement = participationRequirement; }
        public String getScheduleJson() { return scheduleJson; }
        public void setScheduleJson(String scheduleJson) { this.scheduleJson = scheduleJson; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
        public String getContactName() { return contactName; }
        public void setContactName(String contactName) { this.contactName = contactName; }
        public String getContactQrCodeUrl() { return contactQrCodeUrl; }
        public void setContactQrCodeUrl(String contactQrCodeUrl) { this.contactQrCodeUrl = contactQrCodeUrl; }
        public String getTags() { return tags; }
        public void setTags(String tags) { this.tags = tags; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getFeeMode() { return feeMode; }
        public void setFeeMode(String feeMode) { this.feeMode = feeMode; }
        public Long getEntryFeeMinor() { return entryFeeMinor; }
        public void setEntryFeeMinor(Long entryFeeMinor) { this.entryFeeMinor = entryFeeMinor; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public Boolean getFeatured() { return featured; }
        public void setFeatured(Boolean featured) { this.featured = featured; }
        public Integer getSort() { return sort; }
        public void setSort(Integer sort) { this.sort = sort; }
    }

    public static class ConfigItemRequest {
        @NotBlank
        @Size(max = 64)
        private String itemType;
        @NotBlank
        @Size(max = 128)
        private String itemKey;
        @NotBlank
        @Size(max = 255)
        private String title;
        private String contentJson;
        private String contentText;
        private Integer sortOrder;
        private Boolean requiredFlag;
        private Boolean enabled;

        public String getItemType() { return itemType; }
        public void setItemType(String itemType) { this.itemType = itemType; }
        public String getItemKey() { return itemKey; }
        public void setItemKey(String itemKey) { this.itemKey = itemKey; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getContentJson() { return contentJson; }
        public void setContentJson(String contentJson) { this.contentJson = contentJson; }
        public String getContentText() { return contentText; }
        public void setContentText(String contentText) { this.contentText = contentText; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
        public Boolean getRequiredFlag() { return requiredFlag; }
        public void setRequiredFlag(Boolean requiredFlag) { this.requiredFlag = requiredFlag; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    }

    public static class SettingsModuleRequest {
        private List<ConfigItemRequest> items = List.of();

        public List<ConfigItemRequest> getItems() { return items; }
        public void setItems(List<ConfigItemRequest> items) { this.items = items == null ? List.of() : items; }
    }
}

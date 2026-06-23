package com.lumira.saas.modules.system.workorder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class WorkOrderFeedbackDTO {

    public static class CreateRequest {
        @NotBlank
        @Size(max = 160)
        private String title;

        @NotBlank
        private String detailHtml;

        @Size(max = 32)
        private String priority;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDetailHtml() {
            return detailHtml;
        }

        public void setDetailHtml(String detailHtml) {
            this.detailHtml = detailHtml;
        }

        public String getPriority() {
            return priority;
        }

        public void setPriority(String priority) {
            this.priority = priority;
        }
    }

    public static class StatusRequest {
        private String status;
        private String adminReply;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getAdminReply() {
            return adminReply;
        }

        public void setAdminReply(String adminReply) {
            this.adminReply = adminReply;
        }
    }
}

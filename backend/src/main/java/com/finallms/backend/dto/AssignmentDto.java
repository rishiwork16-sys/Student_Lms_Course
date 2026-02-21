package com.finallms.backend.dto;

import com.finallms.backend.enums.AssignmentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class AssignmentDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateAssignmentRequest {
        private String title;
        private String description;
        private String textContent;
        private AssignmentType type;
        private Long moduleId;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public AssignmentType getType() {
            return type;
        }

        public void setType(AssignmentType type) {
            this.type = type;
        }

        public Long getModuleId() {
            return moduleId;
        }

        public void setModuleId(Long moduleId) {
            this.moduleId = moduleId;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AssignmentResponse {
        private Long id;
        private String title;
        private String description;
        private String textContent;
        private AssignmentType type;
        private String fileKey;
        private boolean completed; // submitted

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getTextContent() {
            return textContent;
        }

        public void setTextContent(String textContent) {
            this.textContent = textContent;
        }

        public AssignmentType getType() {
            return type;
        }

        public void setType(AssignmentType type) {
            this.type = type;
        }

        public String getFileKey() {
            return fileKey;
        }

        public void setFileKey(String fileKey) {
            this.fileKey = fileKey;
        }

        public boolean isCompleted() {
            return completed;
        }

        public void setCompleted(boolean completed) {
            this.completed = completed;
        }
    }
}

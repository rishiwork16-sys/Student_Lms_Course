package com.finallms.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class VideoDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateVideoRequest {
        private String title;
        private String s3Key;
        private Long moduleId;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getS3Key() {
            return s3Key;
        }

        public void setS3Key(String s3Key) {
            this.s3Key = s3Key;
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
    public static class VideoResponse {
        private Long id;
        private String title;
        private String url; // Signed URL
        private boolean completed;
        private boolean locked;

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

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public boolean isCompleted() {
            return completed;
        }

        public void setCompleted(boolean completed) {
            this.completed = completed;
        }

        public boolean isLocked() {
            return locked;
        }

        public void setLocked(boolean locked) {
            this.locked = locked;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SignedUrlResponse {
        private Long videoId;
        private String title;
        private String signedUrl;
        private Long expiresAt; // Timestamp in milliseconds

        public Long getVideoId() {
            return videoId;
        }

        public void setVideoId(Long videoId) {
            this.videoId = videoId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getSignedUrl() {
            return signedUrl;
        }

        public void setSignedUrl(String signedUrl) {
            this.signedUrl = signedUrl;
        }

        public Long getExpiresAt() {
            return expiresAt;
        }

        public void setExpiresAt(Long expiresAt) {
            this.expiresAt = expiresAt;
        }
    }
}

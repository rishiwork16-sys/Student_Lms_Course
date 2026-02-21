package com.finallms.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

public class ModuleDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateModuleRequest {
        private String title;
        private Long courseId;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public Long getCourseId() {
            return courseId;
        }

        public void setCourseId(Long courseId) {
            this.courseId = courseId;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ModuleResponse {
        private Long id;
        private String title;
        private List<VideoDto.VideoResponse> videos;
        private List<AssignmentDto.AssignmentResponse> assignments;
        private List<ExamDto.ExamResponse> exams;

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

        public List<VideoDto.VideoResponse> getVideos() {
            return videos;
        }

        public void setVideos(List<VideoDto.VideoResponse> videos) {
            this.videos = videos;
        }

        public List<AssignmentDto.AssignmentResponse> getAssignments() {
            return assignments;
        }

        public void setAssignments(List<AssignmentDto.AssignmentResponse> assignments) {
            this.assignments = assignments;
        }

        public List<ExamDto.ExamResponse> getExams() {
            return exams;
        }

        public void setExams(List<ExamDto.ExamResponse> exams) {
            this.exams = exams;
        }
    }
}

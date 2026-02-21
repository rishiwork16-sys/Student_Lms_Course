package com.finallms.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

public class ExamDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateExamRequest {
        private String title;
        private String description;
        private int durationMinutes;
        private int passingMarks;
        private Long moduleId;
        private List<QuestionDto.CreateQuestionRequest> questions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExamResponse {
        private Long id;
        private String title;
        private String description;
        private int durationMinutes;
        private int passingMarks;
        private List<QuestionDto.QuestionResponse> questions;
        private boolean completed;
        private boolean passed;
        private boolean locked;
    }
}

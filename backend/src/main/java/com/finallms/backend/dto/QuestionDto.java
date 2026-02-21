package com.finallms.backend.dto;

import com.finallms.backend.enums.QuestionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

public class QuestionDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateQuestionRequest {
        private String questionText;
        private QuestionType type;
        private int marks;
        private List<String> options; // For MCQ
        private String correctAnswer; // For MCQ
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuestionResponse {
        private Long id;
        private String questionText;
        private QuestionType type;
        private int marks;
        private List<String> options;
        // correctAnswer is NOT included for student view
    }
}

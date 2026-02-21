package com.finallms.backend.dto;

import com.finallms.backend.enums.SubmissionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

public class ExamSubmissionDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StartExamResponse {
        private Long submissionId;
        private Long examId;
        private String title;
        private int durationMinutes;
        private List<QuestionDto.QuestionResponse> questions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SubmitAnswerRequest {
        private Long questionId;
        private String answer; // Option key for MCQ, or text/file key
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SubmitExamRequest {
        private Long submissionId;
        private List<SubmitAnswerRequest> answers;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AnswerResponse {
        private Long questionId;
        private int marksObtained;
        private String remarks;
        private String studentAnswer;
        private String correctAnswer;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExamResultResponse {
        private Long submissionId;
        private int obtainedMarks;
        private int totalMarks;
        private SubmissionStatus status;
        private boolean passed;
        private List<AnswerResponse> answers;
    }
}

package com.finallms.backend.entity;

import javax.persistence.*;
import com.finallms.backend.enums.QuestionType;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "questions")
public class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "exam_id")
    @ToString.Exclude
    private Exam exam;

    @Column(length = 5000)
    private String questionText;

    @Enumerated(EnumType.STRING)
    private QuestionType type; // MCQ, FILE_UPLOAD

    private int marks;

    // For MCQ, store options as JSON or comma-separated
    @Lob
    private String optionsJson;

    private String correctAnswer; // For MCQ auto-check
}

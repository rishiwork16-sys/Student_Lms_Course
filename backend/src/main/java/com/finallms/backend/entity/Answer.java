package com.finallms.backend.entity;

import javax.persistence.*;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "answers")
public class Answer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @ToString.Exclude
    private ExamSubmission submission;

    @ManyToOne
    @ToString.Exclude
    private Question question;

    @Column(length = 2000)
    private String studentAnswer;

    private int marksObtained;

    private String adminRemarks; // For manual or auto feedback
}

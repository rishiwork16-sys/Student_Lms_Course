package com.finallms.backend.entity;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import com.finallms.backend.enums.SubmissionStatus;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @ToString.Exclude
    private Exam exam;

    @ManyToOne
    @ToString.Exclude
    private User student;

    private int totalObtainedMarks;

    private LocalDateTime submittedAt;

    @Enumerated(EnumType.STRING)
    private SubmissionStatus status;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL)
    @ToString.Exclude
    private List<Answer> answers;
}

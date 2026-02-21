package com.finallms.backend.repository;

import com.finallms.backend.entity.ExamSubmission;
import com.finallms.backend.entity.Exam;
import com.finallms.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExamSubmissionRepository extends JpaRepository<ExamSubmission, Long> {
    Optional<ExamSubmission> findByExamAndStudent(Exam exam, User student);

    List<ExamSubmission> findByExam(Exam exam);

    List<ExamSubmission> findByStudent(User student);

    void deleteByExam(Exam exam);
}

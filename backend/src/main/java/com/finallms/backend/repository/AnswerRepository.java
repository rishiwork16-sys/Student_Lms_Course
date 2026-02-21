package com.finallms.backend.repository;

import com.finallms.backend.entity.Answer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnswerRepository extends JpaRepository<Answer, Long> {
    java.util.List<Answer> findBySubmissionId(Long submissionId);
}

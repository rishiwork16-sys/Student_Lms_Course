package com.finallms.backend.repository;

import com.finallms.backend.entity.AssignmentSubmission;
import com.finallms.backend.entity.Assignment;
import com.finallms.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AssignmentSubmissionRepository extends JpaRepository<AssignmentSubmission, Long> {
    Optional<AssignmentSubmission> findByAssignmentAndUser(Assignment assignment, User user);

    long countByUserAndAssignment_Module_Course(User user, com.finallms.backend.entity.Course course);

    java.util.List<AssignmentSubmission> findByUser(User user);

    java.util.List<AssignmentSubmission> findByAssignment(Assignment assignment);

    void deleteByAssignment(Assignment assignment);
}

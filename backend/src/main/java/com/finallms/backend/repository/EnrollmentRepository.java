package com.finallms.backend.repository;

import com.finallms.backend.entity.Enrollment;
import com.finallms.backend.entity.User;
import com.finallms.backend.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    List<Enrollment> findByUser(User user);

    Optional<Enrollment> findByUserAndCourseId(User user, Long courseId);

    List<Enrollment> findByCourse(Course course);
}

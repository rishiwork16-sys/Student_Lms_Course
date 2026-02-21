package com.finallms.backend.repository;

import com.finallms.backend.entity.Certificate;
import com.finallms.backend.entity.Course;
import com.finallms.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    Optional<Certificate> findByUserAndCourse(User user, Course course);

    Optional<Certificate> findByCertificateId(String certificateId);

    List<Certificate> findByUser(User user);

    boolean existsByCertificateId(String certificateId);

    void deleteByCourse(Course course);
}

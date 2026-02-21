package com.finallms.backend.repository;

import com.finallms.backend.entity.Exam;
import com.finallms.backend.entity.Module;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ExamRepository extends JpaRepository<Exam, Long> {
    List<Exam> findByModule(Module module);
}

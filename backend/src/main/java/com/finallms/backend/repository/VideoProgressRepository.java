package com.finallms.backend.repository;

import com.finallms.backend.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface VideoProgressRepository extends JpaRepository<VideoProgress, Long> {
    Optional<VideoProgress> findByUserAndVideo(User user, Video video);

    long countByUserAndVideo_Module_CourseAndCompleted(User user, Course course, boolean completed);

    java.util.List<VideoProgress> findByUser(User user);

    void deleteByVideo(Video video);
}

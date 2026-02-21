package com.finallms.backend.service;

import com.finallms.backend.entity.Enrollment;
import com.finallms.backend.repository.CourseRepository;
import com.finallms.backend.repository.EnrollmentRepository;
import com.finallms.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private EnrollmentRepository enrollmentRepository;

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userRepository.count());
        stats.put("totalCourses", courseRepository.count());

        List<Enrollment> enrollments = enrollmentRepository.findAll();
        stats.put("totalEnrollments", enrollments.size());

        double totalRevenue = enrollments.stream()
                .mapToDouble(e -> e.getCourse().getPrice() != null ? e.getCourse().getPrice() : 0.0)
                .sum();

        stats.put("revenue", totalRevenue);

        return stats;
    }
}

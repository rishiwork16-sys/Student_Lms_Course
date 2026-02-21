package com.finallms.backend.controller;

import com.finallms.backend.entity.*;
import com.finallms.backend.repository.*;
import com.finallms.backend.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/certificates")
public class CertificateController {

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private StudentService studentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    @GetMapping("/download/{courseId}")
    public ResponseEntity<byte[]> downloadCertificate(@PathVariable Long courseId, Authentication authentication) {
        System.out.println("DEBUG: Received certificate download request for course " + courseId);
        try {
            String identifier = authentication.getName();
            System.out.println("DEBUG: Authenticated user identifier: " + identifier);

            User user;
            if (identifier.contains("@")) {
                user = userRepository.findByEmail(identifier)
                        .orElseThrow(() -> new RuntimeException("User not found with email: " + identifier));
            } else {
                user = userRepository.findByPhone(identifier)
                        .orElseThrow(() -> new RuntimeException("User not found with phone: " + identifier));
            }

            Course course = courseRepository.findById(courseId)
                    .orElseThrow(() -> new RuntimeException("Course not found"));

            System.out.println("DEBUG: User Name from DB: '" + user.getName() + "', User ID: " + user.getId()
                    + ", Role: " + user.getRole());

            // Check if course is completed
            if (!studentService.isCourseCompleted(user, course)) {
                return new ResponseEntity<>(
                        "You must complete all modules, videos, assignments and exams to download the certificate"
                                .getBytes(),
                        HttpStatus.FORBIDDEN);
            }

            System.out.println(
                    "DEBUG: Generating certificate for user " + user.getId() + " and course " + course.getId());

            byte[] pdfBytes = certificateService.generatePdfCertificate(user, course);
            System.out.println("DEBUG: Certificate generated, size: " + pdfBytes.length);

            String filename = "Certificate_" + user.getName().replaceAll("\\s+", "_") + ".pdf";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            System.err.println("DEBUG: Controller caught exception:");
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}

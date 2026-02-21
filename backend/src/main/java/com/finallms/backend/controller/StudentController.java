package com.finallms.backend.controller;

import com.finallms.backend.dto.*;
import com.finallms.backend.entity.Answer;
import com.finallms.backend.entity.ExamSubmission;
import com.finallms.backend.entity.User;
import com.finallms.backend.repository.ExamSubmissionRepository;
import com.finallms.backend.repository.UserRepository;
import com.finallms.backend.service.*;
import com.finallms.backend.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Collections;

@RestController
@RequestMapping("/api/student")
public class StudentController {

    @Autowired
    private StudentService studentService;

    @Autowired
    private JwtUtil jwtUtil;

    // Helper to get username from Principal (which is set by JwtFilter)
    // The Principal.getName() returns the subject (email or phone)

    @Autowired
    private com.finallms.backend.service.PaymentService paymentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExamSubmissionRepository examSubmissionRepository;

    @PostMapping("/enroll/{courseId}")
    public ResponseEntity<?> enrollFree(Principal principal, @PathVariable Long courseId) {
        if (principal == null)
            return ResponseEntity.status(401).body("Please login to enroll");
        studentService.enrollFree(principal.getName(), courseId);
        return ResponseEntity.ok("Enrolled successfully (Free)");
    }

    @PostMapping("/payment/create-order")
    public ResponseEntity<?> createOrder(Principal principal,
            @RequestBody com.finallms.backend.dto.PaymentDto.OrderRequest request) {
        if (principal == null)
            return ResponseEntity.status(401).body("Please login to create order");
        return ResponseEntity.ok(paymentService.createOrder(principal.getName(), request.getCourseId()));
    }

    @PostMapping("/payment/verify")
    public ResponseEntity<?> verifyPayment(Principal principal,
            @RequestBody com.finallms.backend.dto.PaymentDto.VerifyRequest request) {
        if (principal == null)
            return ResponseEntity.status(401).body("Please login to verify payment");
        studentService.enrollPaid(principal.getName(), request);
        return ResponseEntity.ok("Payment verified and Enrolled successfully");
    }

    @GetMapping("/courses")
    public ResponseEntity<List<com.finallms.backend.dto.CourseDto.CourseResponse>> getEnrolledCourses(
            Principal principal) {
        if (principal == null)
            return ResponseEntity.status(401).body(java.util.Collections.emptyList());
        return ResponseEntity.ok(studentService.getEnrolledCourses(principal.getName()));
    }

    @GetMapping("/all-courses")
    public ResponseEntity<List<com.finallms.backend.dto.CourseDto.CourseResponse>> getAllCourses() {
        // Reuse service method or add new one.
        // studentService doesn't have getAllCourses. using courseRepo directly or
        // adding method to studentService.
        // Better to add to StudentService.
        return ResponseEntity.ok(studentService.getAllCourses());
    }

    @GetMapping("/videos/{id}/play")
    public ResponseEntity<VideoDto.SignedUrlResponse> getVideoSignedUrl(Principal principal, @PathVariable Long id,
            @RequestParam(required = false) String quality) {
        if (principal == null)
            return ResponseEntity.status(401).build();
        if (quality == null || quality.isBlank()) {
            return ResponseEntity.ok(studentService.getVideoSignedUrl(principal.getName(), id));
        }
        return ResponseEntity.ok(studentService.getVideoSignedUrl(principal.getName(), id, quality));
    }

    @PostMapping("/videos/{id}/complete")
    public ResponseEntity<?> markVideoComplete(Principal principal, @PathVariable Long id) {
        if (principal == null)
            return ResponseEntity.status(401).body(java.util.Map.of("status", "unauthorized"));
        studentService.completeVideo(principal.getName(), id);
        return ResponseEntity.ok(java.util.Map.of("status", "ok"));
    }

    @GetMapping("/courses/{courseId}")
    public ResponseEntity<CourseDto.CourseResponse> getCourseContent(Principal principal, @PathVariable Long courseId) {
        if (principal == null)
            return ResponseEntity.status(401).build();
        return ResponseEntity.ok(studentService.getCourseContent(principal.getName(), courseId));
    }

    @GetMapping("/assignments/{id}/file")
    public ResponseEntity<?> getAssignmentFile(Principal principal, @PathVariable Long id) {
        if (principal == null)
            return ResponseEntity.status(401).body(java.util.Map.of("status", "unauthorized"));
        String fileUrl = studentService.getAssignmentFileUrl(principal.getName(), id);
        return ResponseEntity.ok(java.util.Map.of("fileUrl", fileUrl));
    }

    @PostMapping("/assignments/{id}/submit-text")
    public ResponseEntity<?> submitTextAssignment(Principal principal, @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body) {
        if (principal == null)
            return ResponseEntity.status(401).body("Unauthorized");
        String text = body != null ? body.get("text") : null;
        studentService.submitTextAssignment(principal.getName(), id, text);
        return ResponseEntity.ok("Submitted");
    }

    // EXAM OPERATIONS
    @Autowired
    private com.finallms.backend.service.ExamService examService;

    @PostMapping("/exams/{id}/start")
    public ResponseEntity<?> startExam(Principal principal, @PathVariable Long id) {
        if (principal == null)
            return ResponseEntity.status(401).body("Unauthorized");
        return ResponseEntity.ok(examService.startExam(id, principal.getName()));
    }

    @PostMapping("/exams/submit")
    public ResponseEntity<?> submitExam(Principal principal,
            @RequestBody com.finallms.backend.dto.ExamSubmissionDto.SubmitExamRequest request) {
        if (principal == null)
            return ResponseEntity.status(401).body("Unauthorized");
        return ResponseEntity.ok(examService.submitExam(request));
    }

    @PostMapping("/exams/upload")
    public ResponseEntity<?> uploadExamFile(Principal principal,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        if (principal == null)
            return ResponseEntity.status(401).body("Unauthorized");
        return ResponseEntity.ok(java.util.Map.of("fileKey", studentService.uploadExamFile(file)));
    }

    // MY EXAM RESULTS
    @GetMapping("/my-results")
    public ResponseEntity<?> getMyResults(Principal principal) {
        if (principal == null)
            return ResponseEntity.status(401).body("Unauthorized");
        User student = userRepository.findByPhone(principal.getName())
                .or(() -> userRepository.findByEmail(principal.getName()))
                .orElseThrow(() -> new RuntimeException("User not found"));
        List<ExamSubmission> submissions = examSubmissionRepository.findByStudent(student);
        var result = submissions.stream().map(sub -> {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("submissionId", sub.getId());
            m.put("examId", sub.getExam().getId());
            m.put("examTitle", sub.getExam().getTitle() != null ? sub.getExam().getTitle() : "Exam");
            m.put("moduleTitle", sub.getExam().getModule() != null && sub.getExam().getModule().getTitle() != null
                    ? sub.getExam().getModule().getTitle()
                    : "");
            m.put("courseName", sub.getExam().getModule() != null && sub.getExam().getModule().getCourse() != null
                    ? sub.getExam().getModule().getCourse().getTitle()
                    : "");
            m.put("obtainedMarks", sub.getTotalObtainedMarks());
            m.put("totalQuestions", sub.getExam().getQuestions() != null ? sub.getExam().getQuestions().size() : 0);
            m.put("passingMarks", sub.getExam().getPassingMarks());
            m.put("status", sub.getStatus() != null ? sub.getStatus().name() : "SUBMITTED");
            m.put("passed", sub.getTotalObtainedMarks() >= sub.getExam().getPassingMarks());
            m.put("submittedAt", sub.getSubmittedAt() != null ? sub.getSubmittedAt().toString() : "");
            return m;
        }).collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // EXAM REVIEW — shows which questions were right/wrong, but NOT the correct
    // answer
    @GetMapping("/my-results/{submissionId}/review")
    public ResponseEntity<?> getSubmissionReview(Principal principal, @PathVariable Long submissionId) {
        if (principal == null)
            return ResponseEntity.status(401).body("Unauthorized");
        User student = userRepository.findByPhone(principal.getName())
                .or(() -> userRepository.findByEmail(principal.getName()))
                .orElseThrow(() -> new RuntimeException("User not found"));
        com.finallms.backend.entity.ExamSubmission sub = examSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Submission not found"));
        // Ensure this submission belongs to this student
        if (!sub.getStudent().getId().equals(student.getId())) {
            return ResponseEntity.status(403).body("Forbidden");
        }
        // Build summary
        java.util.Map<String, Object> summary = new java.util.LinkedHashMap<>();
        summary.put("examTitle", sub.getExam().getTitle() != null ? sub.getExam().getTitle() : "Exam");
        summary.put("obtainedMarks", sub.getTotalObtainedMarks());
        summary.put("totalQuestions", sub.getAnswers() != null ? sub.getAnswers().size() : 0);
        summary.put("passingMarks", sub.getExam().getPassingMarks());
        summary.put("passed", sub.getTotalObtainedMarks() >= sub.getExam().getPassingMarks());
        summary.put("submittedAt", sub.getSubmittedAt() != null ? sub.getSubmittedAt().toString() : "");

        // Build per-question review — NO correctAnswer exposed
        List<Answer> answers = sub.getAnswers() != null ? sub.getAnswers() : java.util.Collections.emptyList();
        var questions = answers.stream().map(ans -> {
            java.util.Map<String, Object> q = new java.util.LinkedHashMap<>();
            q.put("questionText", ans.getQuestion().getQuestionText());
            q.put("questionType",
                    ans.getQuestion().getType() != null ? ans.getQuestion().getType().name() : "MCQ");
            q.put("studentAnswer", ans.getStudentAnswer() != null ? ans.getStudentAnswer() : "(No answer)");
            q.put("marksObtained", ans.getMarksObtained());
            q.put("maxMarks", ans.getQuestion().getMarks());
            // correct = full marks obtained, incorrect = 0 marks
            q.put("isCorrect", ans.getMarksObtained() > 0);
            q.put("adminRemarks", ans.getAdminRemarks() != null ? ans.getAdminRemarks() : "");
            // optionsJson for MCQ display (so student can see what options were)
            q.put("optionsJson", ans.getQuestion().getType() != null
                    && ans.getQuestion().getType().name().equals("MCQ")
                            ? ans.getQuestion().getOptionsJson()
                            : null);
            // correctAnswer is intentionally OMITTED
            return q;
        }).collect(java.util.stream.Collectors.toList());

        summary.put("questions", questions);
        return ResponseEntity.ok(summary);
    }
}

package com.finallms.backend.service;

import com.finallms.backend.dto.CourseDto;
import com.finallms.backend.dto.ModuleDto;
import com.finallms.backend.dto.VideoDto;
import com.finallms.backend.dto.AssignmentDto;
import com.finallms.backend.entity.*;
import com.finallms.backend.exception.BadRequestException;
import com.finallms.backend.exception.ResourceNotFoundException;
import com.finallms.backend.repository.CourseRepository;
import com.finallms.backend.repository.EnrollmentRepository;
import com.finallms.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StudentService {

        @Autowired
        private EnrollmentRepository enrollmentRepository;
        @Autowired
        private CourseRepository courseRepository;
        @Autowired
        private UserRepository userRepository;
        @Autowired
        private S3Service s3Service;
        @Autowired
        private PaymentService paymentService;
        @Autowired
        private com.finallms.backend.repository.PaymentRepository paymentRepository;
        @Autowired
        private com.finallms.backend.repository.VideoRepository videoRepository;
        @Autowired
        private com.finallms.backend.repository.AssignmentRepository assignmentRepository;
        @Autowired
        private com.finallms.backend.repository.VideoProgressRepository videoProgressRepository;
        @Autowired
        private com.finallms.backend.repository.AssignmentSubmissionRepository assignmentSubmissionRepository;
        @Autowired
        private com.finallms.backend.repository.ExamRepository examRepository;
        @Autowired
        private com.finallms.backend.repository.ExamSubmissionRepository examSubmissionRepository;

        public void enrollFree(String userEmailOrPhone, Long courseId) {
                User user = userRepository.findByEmail(userEmailOrPhone)
                                .or(() -> userRepository.findByPhone(userEmailOrPhone))
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                Course course = courseRepository.findById(courseId)
                                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
                if (!course.isActive()) {
                        throw new BadRequestException("Course is inactive");
                }

                if (enrollmentRepository.findByUserAndCourseId(user, courseId).isPresent()) {
                        throw new BadRequestException("Already enrolled");
                }

                if (course.getPrice() != null && course.getPrice() > 0) {
                        throw new RuntimeException("This is a paid course. Please complete payment.");
                }

                Enrollment enrollment = new Enrollment();
                enrollment.setUser(user);
                enrollment.setCourse(course);
                enrollment.setEnrolledAt(LocalDateTime.now());

                enrollmentRepository.save(enrollment);
        }

        public void enrollPaid(String userEmailOrPhone, com.finallms.backend.dto.PaymentDto.VerifyRequest request) {
                // Verify Signature using PaymentService
                boolean isValid = paymentService.verifySignature(request.getRazorpayOrderId(),
                                request.getRazorpayPaymentId(), request.getRazorpaySignature());
                com.finallms.backend.entity.Payment p = paymentRepository.findByOrderId(request.getRazorpayOrderId())
                                .orElse(null);
                if (!isValid) {
                        if (p != null) {
                                p.setStatus(com.finallms.backend.enums.PaymentStatus.FAILED);
                                p.setPaymentId(request.getRazorpayPaymentId());
                                p.setSignature(request.getRazorpaySignature());
                                p.setErrorReason("Payment verification failed");
                                p.setUpdatedAt(java.time.LocalDateTime.now());
                                paymentRepository.save(p);
                        }
                        throw new RuntimeException("Payment verification failed");
                }

                User user = userRepository.findByEmail(userEmailOrPhone)
                                .or(() -> userRepository.findByPhone(userEmailOrPhone))
                                .orElseThrow(() -> new RuntimeException("User not found"));

                Course course = courseRepository.findById(request.getCourseId())
                                .orElseThrow(() -> new RuntimeException("Course not found"));

                if (p == null) {
                        throw new RuntimeException("Payment record not found");
                }
                if (p.getUser() == null || !p.getUser().getId().equals(user.getId())) {
                        p.setStatus(com.finallms.backend.enums.PaymentStatus.FAILED);
                        p.setPaymentId(request.getRazorpayPaymentId());
                        p.setSignature(request.getRazorpaySignature());
                        p.setErrorReason("Payment user mismatch");
                        p.setUpdatedAt(java.time.LocalDateTime.now());
                        paymentRepository.save(p);
                        throw new RuntimeException("Payment user mismatch");
                }
                if (p.getCourse() == null || !p.getCourse().getId().equals(course.getId())) {
                        p.setStatus(com.finallms.backend.enums.PaymentStatus.FAILED);
                        p.setPaymentId(request.getRazorpayPaymentId());
                        p.setSignature(request.getRazorpaySignature());
                        p.setErrorReason("Payment course mismatch");
                        p.setUpdatedAt(java.time.LocalDateTime.now());
                        paymentRepository.save(p);
                        throw new RuntimeException("Payment course mismatch");
                }

                if (p.getAmount() == null || Math.round(p.getAmount() * 100) != Math.round(course.getPrice() * 100)) {
                        p.setStatus(com.finallms.backend.enums.PaymentStatus.FAILED);
                        p.setPaymentId(request.getRazorpayPaymentId());
                        p.setSignature(request.getRazorpaySignature());
                        p.setErrorReason("Amount mismatch");
                        p.setUpdatedAt(java.time.LocalDateTime.now());
                        paymentRepository.save(p);
                        throw new RuntimeException("Amount mismatch");
                }

                // MARK PAYMENT SUCCESS FIRST (Idempotency)
                if (p != null) {
                        p.setStatus(com.finallms.backend.enums.PaymentStatus.SUCCESS);
                        p.setPaymentId(request.getRazorpayPaymentId());
                        p.setSignature(request.getRazorpaySignature());
                        p.setUpdatedAt(java.time.LocalDateTime.now());
                        paymentRepository.save(p);
                }

                if (enrollmentRepository.findByUserAndCourseId(user, request.getCourseId()).isPresent()) {
                        // Already enrolled, but payment is now recorded as success. Return gracefully.
                        return;
                }

                Enrollment enrollment = new Enrollment();
                enrollment.setUser(user);
                enrollment.setCourse(course);
                enrollment.setEnrolledAt(LocalDateTime.now());
                // enrollment.setPaymentReference(request.getRazorpayPaymentId());

                enrollmentRepository.save(enrollment);
        }

        public List<CourseDto.CourseResponse> getEnrolledCourses(String userEmailOrPhone) {
                User user = userRepository.findByEmail(userEmailOrPhone)
                                .or(() -> userRepository.findByPhone(userEmailOrPhone))
                                .orElseThrow(() -> new RuntimeException("User not found"));

                return enrollmentRepository.findByUser(user).stream()
                                .map(Enrollment::getCourse)
                                .filter(Course::isActive)
                                .map(course -> {
                                        CourseDto.CourseResponse cr = new CourseDto.CourseResponse();
                                        cr.setId(course.getId());
                                        cr.setTitle(course.getTitle());
                                        cr.setDescription(course.getDescription());
                                        cr.setPrice(course.getPrice());
                                        cr.setThumbnail(course.getThumbnail() != null
                                                        ? s3Service.generatePresignedUrl(course.getThumbnail())
                                                        : null);
                                        cr.setActive(course.isActive());
                                        cr.setDuration(course.getDuration());
                                        cr.setCategory(course.getCategory());
                                        cr.setLanguage(course.getLanguage());
                                        cr.setMrp(course.getMrp());
                                        cr.setInstructor(course.getInstructor());

                                        // Calculate Progress
                                        double progress = calculateCourseProgress(user, course);
                                        cr.setProgressPercent(progress);
                                        cr.setCompleted(isCourseCompleted(user, course));

                                        cr.setModules(java.util.Collections.emptyList());
                                        return cr;
                                })
                                .collect(Collectors.toList());
        }

        public double calculateCourseProgress(User user, Course course) {
                long totalItems = 0;
                long completedItems = 0;

                for (com.finallms.backend.entity.Module module : course.getModules()) {
                        // Videos
                        totalItems += module.getVideos().size();
                        completedItems += videoProgressRepository.findByUser(user).stream()
                                        .filter(vp -> vp.isCompleted() && vp.getVideo().getModule().getId()
                                                        .equals(module.getId()))
                                        .count();

                        // Assignments
                        totalItems += module.getAssignments().size();
                        completedItems += assignmentSubmissionRepository.findByUser(user).stream()
                                        .filter(as -> as.getAssignment().getModule().getId()
                                                        .equals(module.getId()))
                                        .count();

                        // Exams
                        List<Exam> exams = examRepository.findByModule(module);
                        totalItems += exams.size();
                        for (Exam exam : exams) {
                                var sub = examSubmissionRepository.findByExamAndStudent(exam, user).orElse(null);
                                if (sub != null && sub.getTotalObtainedMarks() >= exam.getPassingMarks()) {
                                        completedItems++;
                                }
                        }
                }

                if (totalItems == 0)
                        return 0.0;
                return Math.min(100.0, (double) completedItems / totalItems * 100.0);
        }

        public List<CourseDto.CourseResponse> getAllCourses() {
                return courseRepository.findAll().stream()
                                .filter(Course::isActive)
                                .map(course -> {
                                        CourseDto.CourseResponse cr = new CourseDto.CourseResponse();
                                        cr.setId(course.getId());
                                        cr.setTitle(course.getTitle());
                                        cr.setDescription(course.getDescription());
                                        cr.setPrice(course.getPrice());
                                        cr.setThumbnail(course.getThumbnail() != null
                                                        ? s3Service.generatePresignedUrl(course.getThumbnail())
                                                        : null);
                                        cr.setActive(course.isActive());
                                        cr.setDuration(course.getDuration());
                                        cr.setCategory(course.getCategory());
                                        cr.setLanguage(course.getLanguage());
                                        cr.setMrp(course.getMrp());
                                        cr.setInstructor(course.getInstructor());
                                        cr.setModules(java.util.Collections.emptyList());
                                        return cr;
                                })
                                .collect(Collectors.toList());
        }

        public CourseDto.CourseResponse getCourseContent(String userEmailOrPhone, Long courseId) {
                User user = userRepository.findByEmail(userEmailOrPhone)
                                .or(() -> userRepository.findByPhone(userEmailOrPhone))
                                .orElseThrow(() -> new RuntimeException("User not found"));

                // Verify enrollment
                enrollmentRepository.findByUserAndCourseId(user, courseId)
                                .orElseThrow(() -> new RuntimeException("Not enrolled in this course"));

                Course course = courseRepository.findById(courseId)
                                .orElseThrow(() -> new RuntimeException("Course not found"));

                // Fetch User Progress
                List<com.finallms.backend.entity.VideoProgress> completedVideos = videoProgressRepository
                                .findByUser(user);
                java.util.Set<Long> completedVideoIds = completedVideos.stream()
                                .filter(vp -> vp.isCompleted()
                                                && vp.getVideo().getModule().getCourse().getId().equals(courseId))
                                .map(vp -> vp.getVideo().getId())
                                .collect(Collectors.toSet());

                List<com.finallms.backend.entity.AssignmentSubmission> submittedAssignments = assignmentSubmissionRepository
                                .findByUser(user);
                java.util.Set<Long> submittedAssignmentIds = submittedAssignments.stream()
                                .filter(as -> as.getAssignment().getModule().getCourse().getId().equals(courseId))
                                .map(as -> as.getAssignment().getId())
                                .collect(Collectors.toSet());

                // Build Response with Signed URLs
                boolean lastVideoCompleted = true; // First video of the first module is always unlocked
                List<ModuleDto.ModuleResponse> moduleResponses = new java.util.ArrayList<>();

                // Sort modules by ID for consistent ordering
                List<com.finallms.backend.entity.Module> sortedModules = course.getModules().stream()
                                .sorted(java.util.Comparator.comparing(com.finallms.backend.entity.Module::getId))
                                .collect(Collectors.toList());

                for (com.finallms.backend.entity.Module module : sortedModules) {
                        final boolean[] allVideosInModuleCompleted = { true };

                        // Videos
                        List<VideoDto.VideoResponse> videoResponses = new java.util.ArrayList<>();
                        List<Video> sortedVideos = module.getVideos().stream()
                                        .sorted(java.util.Comparator.comparing(Video::getId))
                                        .collect(Collectors.toList());

                        for (Video video : sortedVideos) {
                                VideoDto.VideoResponse vr = new VideoDto.VideoResponse();
                                vr.setId(video.getId());
                                vr.setTitle(video.getTitle());
                                vr.setUrl(s3Service.generatePresignedUrl(video.getS3Key()));
                                boolean isCompleted = completedVideoIds.contains(video.getId());
                                vr.setCompleted(isCompleted);

                                // Gating Logic: Locked if the previous video was not completed
                                vr.setLocked(!lastVideoCompleted);

                                // Update for the next video in sequence
                                lastVideoCompleted = isCompleted;

                                if (!isCompleted) {
                                        allVideosInModuleCompleted[0] = false;
                                }
                                videoResponses.add(vr);
                        }

                        // Assignments
                        List<AssignmentDto.AssignmentResponse> assignmentResponses = module.getAssignments().stream()
                                        .map(assignment -> {
                                                AssignmentDto.AssignmentResponse ar = new AssignmentDto.AssignmentResponse();
                                                ar.setId(assignment.getId());
                                                ar.setTitle(assignment.getTitle());
                                                ar.setDescription(assignment.getDescription());
                                                ar.setTextContent(assignment.getTextContent());
                                                ar.setType(assignment.getType());
                                                ar.setFileKey(assignment.getFileKey());
                                                ar.setCompleted(submittedAssignmentIds.contains(assignment.getId()));
                                                return ar;
                                        })
                                        .collect(Collectors.toList());

                        // Exams
                        List<com.finallms.backend.dto.ExamDto.ExamResponse> examResponses = examRepository
                                        .findByModule(module).stream()
                                        .map(exam -> {
                                                com.finallms.backend.dto.ExamDto.ExamResponse er = new com.finallms.backend.dto.ExamDto.ExamResponse();
                                                er.setId(exam.getId());
                                                er.setTitle(exam.getTitle());
                                                er.setDurationMinutes(exam.getDurationMinutes());
                                                er.setPassingMarks(exam.getPassingMarks());

                                                // Check submission status
                                                examSubmissionRepository.findByExamAndStudent(exam, user)
                                                                .ifPresent(sub -> {
                                                                        er.setCompleted(true);
                                                                        boolean isPassed = sub
                                                                                        .getTotalObtainedMarks() >= exam
                                                                                                        .getPassingMarks();
                                                                        er.setPassed(isPassed);
                                                                });

                                                // Gating Logic: Exam locked if any video in this module is not
                                                // completed
                                                er.setLocked(!allVideosInModuleCompleted[0]);

                                                return er;
                                        })
                                        .collect(Collectors.toList());

                        ModuleDto.ModuleResponse mr = new ModuleDto.ModuleResponse();
                        mr.setId(module.getId());
                        mr.setTitle(module.getTitle());
                        mr.setVideos(videoResponses);
                        mr.setAssignments(assignmentResponses);
                        mr.setExams(examResponses);
                        moduleResponses.add(mr);
                }

                CourseDto.CourseResponse cr = new CourseDto.CourseResponse();
                cr.setId(course.getId());
                cr.setTitle(course.getTitle());
                cr.setDescription(course.getDescription());
                cr.setPrice(course.getPrice());
                cr.setThumbnail(course.getThumbnail() != null ? s3Service.generatePresignedUrl(course.getThumbnail())
                                : null);
                cr.setActive(course.isActive());
                cr.setModules(moduleResponses);
                return cr;
        }

        public void updateVideoProgress(Long videoId, String userEmailOrPhone, Double position) {
                User user = userRepository.findByEmail(userEmailOrPhone)
                                .or(() -> userRepository.findByPhone(userEmailOrPhone))
                                .orElseThrow(() -> new RuntimeException("User not found"));

                Video video = videoRepository.findById(videoId)
                                .orElseThrow(() -> new RuntimeException("Video not found"));

                VideoProgress vp = videoProgressRepository.findByUserAndVideo(user, video)
                                .orElseGet(() -> {
                                        VideoProgress newVp = new VideoProgress();
                                        newVp.setUser(user);
                                        newVp.setVideo(video);
                                        return newVp;
                                });

                vp.setLastWatchedPosition(position);
                videoProgressRepository.save(vp);
        }

        // Get signed URL for specific video (for student video playback)
        public VideoDto.SignedUrlResponse getVideoSignedUrl(String userEmailOrPhone, Long videoId) {
                User user = userRepository.findByEmail(userEmailOrPhone)
                                .or(() -> userRepository.findByPhone(userEmailOrPhone))
                                .orElseThrow(() -> new RuntimeException("User not found"));

                // Find the video
                Video video = videoRepository.findById(videoId)
                                .orElseThrow(() -> new RuntimeException("Video not found"));

                // Get the course through module
                Course course = video.getModule().getCourse();
                if (!course.isActive()) {
                        throw new RuntimeException("Course is inactive");
                }

                // Verify enrollment
                enrollmentRepository.findByUserAndCourseId(user, course.getId())
                                .orElseThrow(() -> new RuntimeException("Not enrolled in this course"));

                // Generate signed URL
                String signedUrl = s3Service.generatePresignedUrl(video.getS3Key());
                Long expiresAt = System.currentTimeMillis() + (2 * 60 * 60 * 1000); // 2 hours from now

                VideoDto.SignedUrlResponse response = new VideoDto.SignedUrlResponse();
                response.setVideoId(video.getId());
                response.setTitle(video.getTitle());
                response.setSignedUrl(signedUrl);
                response.setExpiresAt(expiresAt);
                return response;
        }

        public VideoDto.SignedUrlResponse getVideoSignedUrl(String userEmailOrPhone, Long videoId, String quality) {
                User user = userRepository.findByEmail(userEmailOrPhone)
                                .or(() -> userRepository.findByPhone(userEmailOrPhone))
                                .orElseThrow(() -> new RuntimeException("User not found"));
                Video video = videoRepository.findById(videoId)
                                .orElseThrow(() -> new RuntimeException("Video not found"));
                Course course = video.getModule().getCourse();
                if (!course.isActive()) {
                        throw new RuntimeException("Course is inactive");
                }
                enrollmentRepository.findByUserAndCourseId(user, course.getId())
                                .orElseThrow(() -> new RuntimeException("Not enrolled in this course"));

                String key = video.getS3Key();
                if (quality != null && !quality.isBlank()) {
                        int dot = key.lastIndexOf('.');
                        String base = dot > 0 ? key.substring(0, dot) : key;
                        String ext = dot > 0 ? key.substring(dot + 1) : "mp4";
                        String candidate = base + "_" + quality + "." + ext;
                        if (s3Service.fileExists(candidate)) {
                                key = candidate;
                        }
                }
                String signedUrl = s3Service.generatePresignedUrl(key);
                Long expiresAt = System.currentTimeMillis() + (2 * 60 * 60 * 1000);
                VideoDto.SignedUrlResponse response = new VideoDto.SignedUrlResponse();
                response.setVideoId(video.getId());
                response.setTitle(video.getTitle());
                response.setSignedUrl(signedUrl);
                response.setExpiresAt(expiresAt);
                return response;
        }

        public void completeVideo(String userEmailOrPhone, Long videoId) {
                User user = userRepository.findByEmail(userEmailOrPhone)
                                .or(() -> userRepository.findByPhone(userEmailOrPhone))
                                .orElseThrow(() -> new RuntimeException("User not found"));
                Video video = videoRepository.findById(videoId)
                                .orElseThrow(() -> new RuntimeException("Video not found"));
                Course course = video.getModule().getCourse();
                enrollmentRepository.findByUserAndCourseId(user, course.getId())
                                .orElseThrow(() -> new RuntimeException("Not enrolled in this course"));
                com.finallms.backend.entity.VideoProgress vp = videoProgressRepository.findByUserAndVideo(user, video)
                                .orElseGet(() -> {
                                        com.finallms.backend.entity.VideoProgress np = new com.finallms.backend.entity.VideoProgress();
                                        np.setUser(user);
                                        np.setVideo(video);
                                        return np;
                                });
                vp.setCompleted(true);
                vp.setCompletedAt(java.time.LocalDateTime.now());
                videoProgressRepository.save(vp);
        }

        // ASSIGNMENT FILE (Student download/view)
        public String getAssignmentFileUrl(String userEmailOrPhone, Long assignmentId) {
                User user = userRepository.findByEmail(userEmailOrPhone)
                                .or(() -> userRepository.findByPhone(userEmailOrPhone))
                                .orElseThrow(() -> new RuntimeException("User not found"));

                Assignment assignment = assignmentRepository.findById(assignmentId)
                                .orElseThrow(() -> new RuntimeException("Assignment not found"));

                Course course = assignment.getModule().getCourse();
                if (!course.isActive()) {
                        throw new RuntimeException("Course is inactive");
                }

                enrollmentRepository.findByUserAndCourseId(user, course.getId())
                                .orElseThrow(() -> new RuntimeException("Not enrolled in this course"));

                if (assignment.getType() != com.finallms.backend.enums.AssignmentType.FILE
                                || assignment.getFileKey() == null) {
                        throw new RuntimeException("Assignment does not have a file");
                }

                return s3Service.generatePresignedUrl(assignment.getFileKey());
        }

        public void submitTextAssignment(String userEmailOrPhone, Long assignmentId, String text) {
                if (text == null || text.trim().isEmpty()) {
                        throw new RuntimeException("Answer text is required");
                }
                User user = userRepository.findByEmail(userEmailOrPhone)
                                .or(() -> userRepository.findByPhone(userEmailOrPhone))
                                .orElseThrow(() -> new RuntimeException("User not found"));
                Assignment assignment = assignmentRepository.findById(assignmentId)
                                .orElseThrow(() -> new RuntimeException("Assignment not found"));
                Course course = assignment.getModule().getCourse();
                if (!course.isActive()) {
                        throw new RuntimeException("Course is inactive");
                }
                enrollmentRepository.findByUserAndCourseId(user, course.getId())
                                .orElseThrow(() -> new RuntimeException("Not enrolled in this course"));
                if (assignment.getType() != com.finallms.backend.enums.AssignmentType.TEXT) {
                        throw new RuntimeException("Assignment is not of TEXT type");
                }
                var existing = assignmentSubmissionRepository.findByAssignmentAndUser(assignment, user).orElse(null);
                if (existing == null) {
                        existing = new com.finallms.backend.entity.AssignmentSubmission();
                        existing.setAssignment(assignment);
                        existing.setUser(user);
                }
                existing.setTextAnswer(text.trim());
                existing.setSubmittedAt(java.time.LocalDateTime.now());
                assignmentSubmissionRepository.save(existing);
        }
        // STUDENT MANAGEMENT METHODS (for Admin)

        public List<User> getAllStudents() {
                return userRepository.findAll().stream()
                                .filter(u -> u.getRole() == com.finallms.backend.enums.Role.STUDENT)
                                .collect(Collectors.toList());
        }

        public List<User> getCourseStudents(Long courseId) {
                Course course = courseRepository.findById(courseId)
                                .orElseThrow(() -> new RuntimeException("Course not found"));

                return enrollmentRepository.findByCourse(course).stream()
                                .map(Enrollment::getUser)
                                .collect(Collectors.toList());
        }

        public void removeEnrollment(Long enrollmentId) {
                enrollmentRepository.deleteById(enrollmentId);
        }

        public String uploadExamFile(org.springframework.web.multipart.MultipartFile file) {
                try {
                        return s3Service.uploadFile(file);
                } catch (java.io.IOException e) {
                        throw new RuntimeException("Failed to upload exam file", e);
                }
        }

        public boolean isCourseCompleted(User user, Course course) {
                // 1. Check Videos
                long totalVideos = course.getModules().stream()
                                .flatMap(m -> m.getVideos().stream())
                                .count();

                long completedVideos = videoProgressRepository.findByUser(user).stream()
                                .filter(vp -> vp.isCompleted() && vp.getVideo().getModule().getCourse().getId()
                                                .equals(course.getId()))
                                .count();

                if (completedVideos < totalVideos)
                        return false;

                // 2. Check Assignments
                long totalAssignments = course.getModules().stream()
                                .flatMap(m -> m.getAssignments().stream())
                                .count();

                long submittedAssignments = assignmentSubmissionRepository.findByUser(user).stream()
                                .filter(as -> as.getAssignment().getModule().getCourse().getId().equals(course.getId()))
                                .count();

                if (submittedAssignments < totalAssignments)
                        return false;

                // 3. Check Exams
                for (com.finallms.backend.entity.Module module : course.getModules()) {
                        List<Exam> exams = examRepository.findByModule(module);
                        for (Exam exam : exams) {
                                var submission = examSubmissionRepository.findByExamAndStudent(exam, user).orElse(null);
                                if (submission == null || submission.getTotalObtainedMarks() < exam.getPassingMarks()) {
                                        return false;
                                }
                        }
                }

                return true;
        }

        public com.finallms.backend.dto.ProfileDto.ProfileResponse getProfile(String userEmailOrPhone) {
                User user = userRepository.findByEmail(userEmailOrPhone)
                                .or(() -> userRepository.findByPhone(userEmailOrPhone))
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                com.finallms.backend.dto.ProfileDto.ProfileResponse pr = new com.finallms.backend.dto.ProfileDto.ProfileResponse();
                pr.setId(user.getId());
                pr.setName(user.getName());
                pr.setEmail(user.getEmail());
                pr.setPhone(user.getPhone());
                pr.setAddress(user.getAddress());
                pr.setRole(user.getRole().name());
                pr.setProfilePictureUrl(user.getProfilePictureUrl() != null
                                ? s3Service.generatePresignedUrl(user.getProfilePictureUrl())
                                : null);
                return pr;
        }

        public void updateProfile(String userEmailOrPhone,
                        com.finallms.backend.dto.ProfileDto.ProfileUpdateRequest request) {
                User user = userRepository.findByEmail(userEmailOrPhone)
                                .or(() -> userRepository.findByPhone(userEmailOrPhone))
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                if (request.getName() != null)
                        user.setName(request.getName());
                if (request.getEmail() != null)
                        user.setEmail(request.getEmail());
                if (request.getAddress() != null)
                        user.setAddress(request.getAddress());

                userRepository.save(user);
        }

        public void updateProfilePicture(String userEmailOrPhone,
                        org.springframework.web.multipart.MultipartFile file) {
                User user = userRepository.findByEmail(userEmailOrPhone)
                                .or(() -> userRepository.findByPhone(userEmailOrPhone))
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                try {
                        String key = s3Service.uploadFile(file);
                        user.setProfilePictureUrl(key);
                        userRepository.save(user);
                } catch (java.io.IOException e) {
                        throw new RuntimeException("Failed to upload profile picture", e);
                }
        }

        public void requestPhoneChange(String userEmailOrPhone, String newPhone, OtpService otpService) {
                // Ensure new phone is not already in use
                if (userRepository.findByPhone(newPhone).isPresent()) {
                        throw new BadRequestException("Phone number already registered with another account");
                }
                otpService.generateAndSendOtp(newPhone);
        }

        public void verifyPhoneChange(String userEmailOrPhone, String newPhone, String otp, OtpService otpService) {
                User user = userRepository.findByEmail(userEmailOrPhone)
                                .or(() -> userRepository.findByPhone(userEmailOrPhone))
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                boolean isValid = otpService.validateOtp(newPhone, otp);
                if (!isValid) {
                        throw new BadRequestException("Invalid or expired OTP");
                }

                user.setPhone(newPhone);
                userRepository.save(user);
        }
}

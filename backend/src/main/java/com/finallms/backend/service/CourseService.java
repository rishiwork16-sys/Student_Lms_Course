package com.finallms.backend.service;

import com.finallms.backend.dto.*;
import com.finallms.backend.entity.*;
import com.finallms.backend.entity.Module;
import com.finallms.backend.enums.AssignmentType;
import com.finallms.backend.exception.BadRequestException;
import com.finallms.backend.exception.ResourceNotFoundException;
import com.finallms.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.ArrayList;

@Service
public class CourseService {

        @Autowired
        private CourseRepository courseRepository;
        @Autowired
        private ModuleRepository moduleRepository;
        @Autowired
        private VideoRepository videoRepository;
        @Autowired
        private AssignmentRepository assignmentRepository;
        @Autowired
        private S3Service s3Service;
        @Autowired
        private EnrollmentRepository enrollmentRepository;
        @Autowired
        private ExamRepository examRepository;
        @Autowired
        private AssignmentSubmissionRepository assignmentSubmissionRepository;
        @Autowired
        private PaymentRepository paymentRepository;
        @Autowired
        private CertificateRepository certificateRepository;
        @Autowired
        private ExamSubmissionRepository examSubmissionRepository;
        @Autowired
        private VideoProgressRepository videoProgressRepository;

        @Transactional
        public Course createCourse(CourseDto.CreateCourseRequest request, MultipartFile thumbnail) throws IOException {
                String thumbnailKey = null;
                if (thumbnail != null && !thumbnail.isEmpty()) {
                        thumbnailKey = s3Service.uploadFile(thumbnail);
                }

                Course course = new Course();
                course.setTitle(request.getTitle());
                course.setDescription(request.getDescription());
                course.setPrice(request.getPrice());
                course.setThumbnail(thumbnailKey);
                course.setActive(request.isActive());
                course.setDuration(request.getDuration());
                course.setCategory(request.getCategory());
                course.setLanguage(request.getLanguage());
                course.setMrp(request.getMrp());
                course.setInstructor(request.getInstructor());

                return courseRepository.save(course);
        }

        public Course getCourseEntity(Long courseId) {
                return courseRepository.findById(courseId)
                                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
        }

        public List<CourseDto.CourseResponse> getAllCourses() {
                return courseRepository.findAll().stream().map(course -> {
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
                        cr.setModules(new ArrayList<>());
                        return cr;
                }).collect(Collectors.toList());
        }

        public CourseDto.CourseResponse getCourseDetails(Long courseId) {
                Course course = courseRepository.findById(courseId)
                                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));

                List<ModuleDto.ModuleResponse> moduleResponses = course.getModules().stream().map(module -> {
                        List<VideoDto.VideoResponse> videoResponses = module.getVideos().stream()
                                        .map(video -> {
                                                VideoDto.VideoResponse vr = new VideoDto.VideoResponse();
                                                vr.setId(video.getId());
                                                vr.setTitle(video.getTitle());
                                                vr.setUrl(null);
                                                return vr;
                                        })
                                        .collect(Collectors.toList());

                        List<AssignmentDto.AssignmentResponse> assignmentResponses = module.getAssignments().stream()
                                        .map(assignment -> {
                                                AssignmentDto.AssignmentResponse ar = new AssignmentDto.AssignmentResponse();
                                                ar.setId(assignment.getId());
                                                ar.setTitle(assignment.getTitle());
                                                ar.setDescription(assignment.getDescription());
                                                ar.setTextContent(assignment.getTextContent());
                                                ar.setType(assignment.getType());
                                                ar.setFileKey(assignment.getFileKey());
                                                return ar;
                                        })
                                        .collect(Collectors.toList());

                        List<ExamDto.ExamResponse> examResponses = examRepository.findByModule(module).stream()
                                        .map(exam -> {
                                                ExamDto.ExamResponse er = new ExamDto.ExamResponse();
                                                er.setId(exam.getId());
                                                er.setTitle(exam.getTitle());
                                                er.setDurationMinutes(exam.getDurationMinutes());
                                                er.setPassingMarks(exam.getPassingMarks());
                                                return er;
                                        })
                                        .collect(Collectors.toList());

                        ModuleDto.ModuleResponse mr = new ModuleDto.ModuleResponse();
                        mr.setId(module.getId());
                        mr.setTitle(module.getTitle());
                        mr.setVideos(videoResponses);
                        mr.setAssignments(assignmentResponses);
                        mr.setExams(examResponses);
                        return mr;
                }).collect(Collectors.toList());

                CourseDto.CourseResponse cr = new CourseDto.CourseResponse();
                cr.setId(course.getId());
                cr.setTitle(course.getTitle());
                cr.setDescription(course.getDescription());
                cr.setPrice(course.getPrice());
                cr.setThumbnail(course.getThumbnail() != null ? s3Service.generatePresignedUrl(course.getThumbnail())
                                : null);
                cr.setActive(course.isActive());
                cr.setDuration(course.getDuration());
                cr.setCategory(course.getCategory());
                cr.setLanguage(course.getLanguage());
                cr.setMrp(course.getMrp());
                cr.setInstructor(course.getInstructor());
                cr.setModules(moduleResponses);
                return cr;
        }

        public Module createModule(ModuleDto.CreateModuleRequest request) {
                Course course = courseRepository.findById(request.getCourseId())
                                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));

                Module module = new Module();
                module.setTitle(request.getTitle());
                module.setCourse(course);
                return moduleRepository.save(module);
        }

        public Video addVideo(Long moduleId, String title, MultipartFile file) throws IOException {
                Module module = moduleRepository.findById(moduleId)
                                .orElseThrow(() -> new ResourceNotFoundException("Module not found"));

                String s3Key = s3Service.uploadFile(file);

                Video video = new Video();
                video.setTitle(title);
                video.setS3Key(s3Key);
                video.setModule(module);
                return videoRepository.save(video);
        }

        public Assignment addAssignment(Long moduleId, String title, String description, String textContent,
                        AssignmentType type, MultipartFile file, String createdBy) throws IOException {
                Module module = moduleRepository.findById(moduleId)
                                .orElseThrow(() -> new ResourceNotFoundException("Module not found"));

                Assignment assignment = new Assignment();
                assignment.setTitle(title);
                assignment.setDescription(description);
                assignment.setType(type);
                assignment.setModule(module);
                assignment.setCreatedBy(createdBy);

                // For TEXT type, save textContent
                if (type == AssignmentType.TEXT) {
                        if (textContent == null || textContent.trim().isEmpty()) {
                                throw new BadRequestException("Text content is required for TEXT assignments");
                        }
                        assignment.setTextContent(textContent.trim());
                }
                // For FILE type, upload file to S3
                else if (type == AssignmentType.FILE) {
                        if (file == null || file.isEmpty()) {
                                throw new BadRequestException("File is required for FILE assignments");
                        }
                        String s3Key = s3Service.uploadFile(file);
                        assignment.setFileKey(s3Key);
                        assignment.setDescription("File: " + file.getOriginalFilename());
                }

                return assignmentRepository.save(assignment);
        }

        // UPDATE OPERATIONS

        @Transactional
        public Course updateCourse(Long courseId, CourseDto.UpdateCourseRequest request, MultipartFile thumbnail)
                        throws IOException {
                Course course = courseRepository.findById(courseId)
                                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));

                if (request.getTitle() != null)
                        course.setTitle(request.getTitle());
                if (request.getDescription() != null)
                        course.setDescription(request.getDescription());
                if (request.getPrice() != null)
                        course.setPrice(request.getPrice());
                course.setActive(request.isActive());
                if (request.getDuration() != null)
                        course.setDuration(request.getDuration());

                if (request.getCategory() != null)
                        course.setCategory(request.getCategory());
                if (request.getLanguage() != null)
                        course.setLanguage(request.getLanguage());
                if (request.getMrp() != null)
                        course.setMrp(request.getMrp());
                if (request.getInstructor() != null)
                        course.setInstructor(request.getInstructor());

                if (thumbnail != null && !thumbnail.isEmpty()) {
                        // Delete old thumbnail if exists
                        if (course.getThumbnail() != null) {
                                s3Service.deleteFile(course.getThumbnail());
                        }
                        String newThumbnailKey = s3Service.uploadFile(thumbnail);
                        course.setThumbnail(newThumbnailKey);
                }

                return courseRepository.save(course);
        }

        @Transactional
        public void updateModule(Long moduleId, String title) {
                Module module = moduleRepository.findById(moduleId)
                                .orElseThrow(() -> new ResourceNotFoundException("Module not found"));
                module.setTitle(title);
                moduleRepository.save(module);
        }

        @Transactional
        public Video updateVideo(Long videoId, String title, MultipartFile file) throws IOException {
                Video video = videoRepository.findById(videoId)
                                .orElseThrow(() -> new ResourceNotFoundException("Video not found"));

                if (title != null) {
                        video.setTitle(title);
                }

                if (file != null && !file.isEmpty()) {
                        // Delete old video file
                        if (video.getS3Key() != null) {
                                s3Service.deleteFile(video.getS3Key());
                        }
                        String newS3Key = s3Service.uploadFile(file);
                        video.setS3Key(newS3Key);
                }

                return videoRepository.save(video);
        }

        @Transactional
        public void updateAssignment(Long assignmentId, String title, String description, String textContent,
                        MultipartFile file)
                        throws IOException {
                Assignment assignment = assignmentRepository.findById(assignmentId)
                                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found"));

                if (title != null)
                        assignment.setTitle(title);
                if (description != null)
                        assignment.setDescription(description);

                // Update based on type
                if (assignment.getType() == AssignmentType.TEXT && textContent != null) {
                        assignment.setTextContent(textContent);
                } else if (assignment.getType() == AssignmentType.FILE && file != null) {
                        // Delete old file if exists
                        if (assignment.getFileKey() != null) {
                                s3Service.deleteFile(assignment.getFileKey());
                        }
                        String newS3Key = s3Service.uploadFile(file);
                        assignment.setFileKey(newS3Key);
                        assignment.setDescription("File: " + file.getOriginalFilename());
                }

                assignmentRepository.save(assignment);
        }

        // DELETE OPERATIONS

        @Transactional
        public void deleteCourse(Long courseId) {
                Course course = courseRepository.findById(courseId)
                                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));

                // 1. Delete certificates
                certificateRepository.deleteByCourse(course);

                // 2. Delete enrollments
                enrollmentRepository.findByCourse(course).forEach(enrollmentRepository::delete);

                // 3. Delete payments
                paymentRepository.deleteByCourse(course);

                // 4. Delete thumbnail from S3
                if (course.getThumbnail() != null) {
                        try {
                                s3Service.deleteFile(course.getThumbnail());
                        } catch (Exception e) {
                                System.err.println("Failed to delete thumbnail from S3: " + e.getMessage());
                        }
                }

                // 5. Clean up module content
                for (Module module : course.getModules()) {
                        // Delete assignments and their data
                        for (Assignment assignment : module.getAssignments()) {
                                assignmentSubmissionRepository.deleteByAssignment(assignment);
                                if (assignment.getType() == AssignmentType.FILE && assignment.getFileKey() != null) {
                                        try {
                                                s3Service.deleteFile(assignment.getFileKey());
                                        } catch (Exception e) {
                                                System.err.println(
                                                                "Failed to delete assignment file: " + e.getMessage());
                                        }
                                }
                        }

                        // Delete videos and their data
                        for (Video video : module.getVideos()) {
                                videoProgressRepository.deleteByVideo(video);
                                if (video.getS3Key() != null) {
                                        try {
                                                s3Service.deleteFile(video.getS3Key());
                                        } catch (Exception e) {
                                                System.err.println("Failed to delete video file: " + e.getMessage());
                                        }
                                }
                        }

                        // Delete exams and their data
                        for (Exam exam : module.getExams()) {
                                examSubmissionRepository.deleteByExam(exam);
                        }
                }

                // 6. Delete the course (will cascade delete modules, assignments, videos, and
                // exams due to CascadeType.ALL)
                courseRepository.delete(course);
        }

        @Transactional
        public void deleteModule(Long moduleId) {
                Module module = moduleRepository.findById(moduleId)
                                .orElseThrow(() -> new ResourceNotFoundException("Module not found"));

                // Delete assignment submissions and assignment files for this module
                for (Assignment assignment : module.getAssignments()) {
                        assignmentSubmissionRepository.deleteByAssignment(assignment);
                        if (assignment.getType() == AssignmentType.FILE && assignment.getFileKey() != null) {
                                s3Service.deleteFile(assignment.getFileKey());
                        }
                }

                // Delete all videos from S3
                for (Video video : module.getVideos()) {
                        if (video.getS3Key() != null) {
                                s3Service.deleteFile(video.getS3Key());
                        }
                }

                moduleRepository.delete(module);
        }

        @Transactional
        public void deleteVideo(Long videoId) {
                Video video = videoRepository.findById(videoId)
                                .orElseThrow(() -> new ResourceNotFoundException("Video not found"));

                // Delete from S3
                if (video.getS3Key() != null) {
                        s3Service.deleteFile(video.getS3Key());
                }

                videoRepository.delete(video);
        }

        @Transactional
        public void deleteAssignment(Long assignmentId) {
                Assignment assignment = assignmentRepository.findById(assignmentId)
                                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found"));

                // Delete file from S3 if it's a FILE type assignment
                if (assignment.getType() == AssignmentType.FILE && assignment.getFileKey() != null) {
                        s3Service.deleteFile(assignment.getFileKey());
                }

                // Delete all student submissions referencing this assignment
                assignmentSubmissionRepository.deleteByAssignment(assignment);

                assignmentRepository.deleteById(assignmentId);
        }

        // VIDEO PREVIEW (for Admin)

        public VideoDto.SignedUrlResponse getVideoPreviewUrl(Long videoId) {
                Video video = videoRepository.findById(videoId)
                                .orElseThrow(() -> new ResourceNotFoundException("Video not found"));

                String signedUrl = s3Service.generatePresignedUrl(video.getS3Key());
                Long expiresAt = System.currentTimeMillis() + (2 * 60 * 60 * 1000); // 2 hours from now

                VideoDto.SignedUrlResponse response = new VideoDto.SignedUrlResponse();
                response.setVideoId(video.getId());
                response.setTitle(video.getTitle());
                response.setSignedUrl(signedUrl);
                response.setExpiresAt(expiresAt);
                return response;
        }

        // ASSIGNMENT FILE URL (for viewing uploaded files)

        public String getAssignmentFileUrl(Long assignmentId) {
                Assignment assignment = assignmentRepository.findById(assignmentId)
                                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found"));

                if (assignment.getType() != AssignmentType.FILE || assignment.getFileKey() == null) {
                        throw new BadRequestException("Assignment does not have a file");
                }

                return s3Service.generatePresignedUrl(assignment.getFileKey());
        }
}

package com.finallms.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finallms.backend.dto.ExamDto;
import com.finallms.backend.dto.QuestionDto;
import com.finallms.backend.dto.ExamSubmissionDto;
import com.finallms.backend.entity.*;
import com.finallms.backend.entity.Module;
import com.finallms.backend.enums.QuestionType;
import com.finallms.backend.enums.SubmissionStatus;
import com.finallms.backend.exception.BadRequestException;
import com.finallms.backend.exception.ResourceNotFoundException;
import com.finallms.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExamService {

    @Autowired
    private ExamRepository examRepository;
    @Autowired
    private QuestionRepository questionRepository;
    @Autowired
    private ModuleRepository moduleRepository;
    @Autowired
    private ExamSubmissionRepository submissionRepository;
    @Autowired
    private AnswerRepository answerRepository;
    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 1. Create Exam
    @Transactional
    public ExamDto.ExamResponse createExam(ExamDto.CreateExamRequest request) {
        Module module = moduleRepository.findById(request.getModuleId())
                .orElseThrow(() -> new ResourceNotFoundException("Module not found"));

        Exam exam = new Exam();
        exam.setTitle(request.getTitle());
        exam.setDescription(request.getDescription());
        exam.setDurationMinutes(request.getDurationMinutes());
        exam.setPassingMarks(request.getPassingMarks());
        exam.setModule(module);

        Exam savedExam = examRepository.save(exam);

        List<Question> questions = new ArrayList<>();
        if (request.getQuestions() != null) {
            for (QuestionDto.CreateQuestionRequest qReq : request.getQuestions()) {
                Question q = new Question();
                q.setQuestionText(qReq.getQuestionText());
                q.setType(qReq.getType());
                q.setMarks(qReq.getMarks());
                q.setCorrectAnswer(qReq.getCorrectAnswer()); // For MCQ

                if (qReq.getOptions() != null) {
                    try {
                        q.setOptionsJson(objectMapper.writeValueAsString(qReq.getOptions()));
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException("Error processing options JSON", e);
                    }
                }
                q.setExam(savedExam);
                questions.add(q);
            }
            questionRepository.saveAll(questions);
        }

        return mapToExamResponse(savedExam, questions);
    }

    public ExamDto.ExamResponse getExam(Long id) {
        Exam exam = examRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Exam not found"));
        List<Question> qs = exam.getQuestions();
        return mapToExamResponse(exam, qs);
    }

    @Transactional
    public ExamDto.ExamResponse updateExam(Long id, ExamDto.CreateExamRequest request) {
        Exam exam = examRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Exam not found"));
        if (request.getTitle() != null)
            exam.setTitle(request.getTitle());
        if (request.getDescription() != null)
            exam.setDescription(request.getDescription());
        if (request.getDurationMinutes() > 0)
            exam.setDurationMinutes(request.getDurationMinutes());
        if (request.getPassingMarks() >= 0)
            exam.setPassingMarks(request.getPassingMarks());
        Exam saved = examRepository.save(exam);
        List<Question> updatedQuestions = null;
        if (request.getQuestions() != null) {
            if (saved.getQuestions() != null && !saved.getQuestions().isEmpty()) {
                questionRepository.deleteAll(saved.getQuestions());
            }
            updatedQuestions = new java.util.ArrayList<>();
            for (QuestionDto.CreateQuestionRequest qReq : request.getQuestions()) {
                Question q = new Question();
                q.setQuestionText(qReq.getQuestionText());
                q.setType(qReq.getType());
                q.setMarks(qReq.getMarks());
                q.setCorrectAnswer(qReq.getCorrectAnswer());
                q.setExam(saved);
                if (qReq.getOptions() != null) {
                    try {
                        q.setOptionsJson(objectMapper.writeValueAsString(qReq.getOptions()));
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException("Error processing options JSON", e);
                    }
                }
                updatedQuestions.add(q);
            }
            questionRepository.saveAll(updatedQuestions);
        }
        return mapToExamResponse(saved, updatedQuestions != null ? updatedQuestions : saved.getQuestions());
    }

    public com.finallms.backend.entity.Question addQuestion(Long examId, QuestionDto.CreateQuestionRequest request) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found"));
        Question q = new Question();
        q.setQuestionText(request.getQuestionText());
        q.setType(request.getType());
        q.setMarks(request.getMarks());
        q.setCorrectAnswer(request.getCorrectAnswer());
        q.setExam(exam);
        if (request.getOptions() != null) {
            try {
                q.setOptionsJson(objectMapper.writeValueAsString(request.getOptions()));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Error processing options JSON", e);
            }
        }
        return questionRepository.save(q);
    }

    // 2. Start Exam
    @Transactional
    public ExamSubmissionDto.StartExamResponse startExam(Long examId, String userEmailOrPhone) {
        User user = userRepository.findByEmail(userEmailOrPhone)
                .or(() -> userRepository.findByPhone(userEmailOrPhone))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found"));

        java.util.Optional<ExamSubmission> existing = submissionRepository.findByExamAndStudent(exam, user);
        if (existing.isPresent()) {
            ExamSubmission sub = existing.get();
            if (sub.getStatus() != null) {
                Long subId = sub.getId();
                if (subId != null) {
                    java.util.List<Answer> prevAnswers = answerRepository.findBySubmissionId(subId);
                    if (prevAnswers != null && !prevAnswers.isEmpty()) {
                        answerRepository.deleteAll(prevAnswers);
                    }
                }
                sub.setStatus(null);
                sub.setTotalObtainedMarks(0);
                sub.setSubmittedAt(null);
                submissionRepository.save(sub);
            }
            return mapToStartExamResponse(sub, exam);
        }

        ExamSubmission submission = new ExamSubmission();
        submission.setExam(exam);
        submission.setStudent(user);
        submission.setStatus(null);

        submission = submissionRepository.save(submission);
        return mapToStartExamResponse(submission, exam);
    }

    private ExamSubmissionDto.StartExamResponse mapToStartExamResponse(ExamSubmission submission, Exam exam) {
        List<QuestionDto.QuestionResponse> questions = exam.getQuestions().stream().map(q -> {
            QuestionDto.QuestionResponse qr = new QuestionDto.QuestionResponse();
            qr.setId(q.getId());
            qr.setQuestionText(q.getQuestionText());
            qr.setType(q.getType());
            qr.setMarks(q.getMarks());
            if (q.getOptionsJson() != null) {
                try {
                    qr.setOptions(objectMapper.readValue(q.getOptionsJson(), List.class));
                } catch (Exception e) {
                    qr.setOptions(new ArrayList<>());
                }
            }
            return qr;
        }).collect(java.util.stream.Collectors.toList());

        return ExamSubmissionDto.StartExamResponse.builder()
                .submissionId(submission.getId())
                .examId(exam.getId())
                .title(exam.getTitle())
                .durationMinutes(exam.getDurationMinutes())
                .questions(questions)
                .build();
    }

    // 3. Submit Exam
    @Transactional
    public ExamSubmissionDto.ExamResultResponse submitExam(ExamSubmissionDto.SubmitExamRequest request) {
        ExamSubmission submission = submissionRepository.findById(request.getSubmissionId())
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found"));

        if (submission.getStatus() == SubmissionStatus.SUBMITTED || submission.getStatus() == SubmissionStatus.GRADED) {
            throw new BadRequestException("Exam already submitted.");
        }

        int totalScore = 0;
        int maxMarks = 0;
        boolean manualGradingNeeded = false;

        List<Answer> answers = new ArrayList<>();

        for (ExamSubmissionDto.SubmitAnswerRequest ansReq : request.getAnswers()) {
            Question question = questionRepository.findById(ansReq.getQuestionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Question not found"));

            Answer answer = new Answer();
            answer.setQuestion(question);
            answer.setStudentAnswer(ansReq.getAnswer());
            answer.setSubmission(submission);

            if (question.getType() == QuestionType.MCQ) {
                if (ansReq.getAnswer() != null
                        && ansReq.getAnswer().trim().equalsIgnoreCase(question.getCorrectAnswer().trim())) {
                    answer.setMarksObtained(question.getMarks());
                    totalScore += question.getMarks();
                } else {
                    answer.setMarksObtained(0);
                }
            } else if (question.getType() == QuestionType.FILE_UPLOAD) {
                submission.setStatus(SubmissionStatus.PENDING); // Pending manual grading
                answer.setMarksObtained(0);
                manualGradingNeeded = true;
            }
            answers.add(answer);
            maxMarks += question.getMarks();
        }

        answerRepository.saveAll(answers);

        submission.setSubmittedAt(LocalDateTime.now());
        submission.setTotalObtainedMarks(totalScore);

        if (manualGradingNeeded && submission.getStatus() == null) {
            submission.setStatus(SubmissionStatus.SUBMITTED);
        } else if (!manualGradingNeeded) {
            submission.setStatus(SubmissionStatus.GRADED);
        }

        ExamSubmission savedSubmission = submissionRepository.save(submission);

        return mapToResultResponse(savedSubmission);
    }

    @Transactional
    public void deleteExam(Long id) {
        Exam exam = examRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Exam not found"));
        // Delete submissions and their answers
        java.util.List<ExamSubmission> submissions = submissionRepository.findByExam(exam);
        for (ExamSubmission sub : submissions) {
            Long subId = sub.getId();
            if (subId != null) {
                java.util.List<Answer> answers = answerRepository.findBySubmissionId(subId);
                if (answers != null && !answers.isEmpty()) {
                    answerRepository.deleteAll(answers);
                }
            }
            submissionRepository.delete(sub);
        }
        // Delete questions
        java.util.List<Question> questions = exam.getQuestions();
        if (questions != null && !questions.isEmpty()) {
            questionRepository.deleteAll(questions);
        }
        // Delete exam
        examRepository.delete(exam);
    }

    private ExamDto.ExamResponse mapToExamResponse(Exam exam, List<Question> questions) {
        List<QuestionDto.QuestionResponse> qResps = new ArrayList<>();
        if (questions != null) {
            for (Question q : questions) {
                List<String> options = null;
                if (q.getOptionsJson() != null) {
                    try {
                        @SuppressWarnings("unchecked")
                        List<String> list = objectMapper.readValue(q.getOptionsJson(), List.class);
                        options = list;
                    } catch (Exception e) {
                        options = new ArrayList<>();
                    }
                }
                qResps.add(QuestionDto.QuestionResponse.builder()
                        .id(q.getId())
                        .questionText(q.getQuestionText())
                        .type(q.getType())
                        .marks(q.getMarks())
                        .options(options)
                        .build());
            }
        }

        return ExamDto.ExamResponse.builder()
                .id(exam.getId())
                .title(exam.getTitle())
                .description(exam.getDescription())
                .durationMinutes(exam.getDurationMinutes())
                .passingMarks(exam.getPassingMarks())
                .questions(qResps)
                .build();
    }

    private ExamSubmissionDto.ExamResultResponse mapToResultResponse(ExamSubmission submission) {
        List<ExamSubmissionDto.AnswerResponse> ansDtos = new ArrayList<>();
        List<Answer> answers = answerRepository.findBySubmissionId(submission.getId());

        if (answers != null) {
            for (Answer a : answers) {
                ExamSubmissionDto.AnswerResponse aDto = new ExamSubmissionDto.AnswerResponse();
                aDto.setQuestionId(a.getQuestion().getId());
                aDto.setMarksObtained(a.getMarksObtained());
                aDto.setRemarks(a.getAdminRemarks());
                aDto.setStudentAnswer(a.getStudentAnswer());
                aDto.setCorrectAnswer(a.getQuestion().getCorrectAnswer());
                ansDtos.add(aDto);
            }
        }

        ExamSubmissionDto.ExamResultResponse result = new ExamSubmissionDto.ExamResultResponse();
        result.setSubmissionId(submission.getId());
        result.setObtainedMarks(submission.getTotalObtainedMarks());
        // result.setTotalMarks(maxMarks); // Need to calculate or store
        result.setStatus(submission.getStatus());
        result.setPassed(submission.getTotalObtainedMarks() >= submission.getExam().getPassingMarks());
        result.setAnswers(ansDtos);

        return result;
    }
}

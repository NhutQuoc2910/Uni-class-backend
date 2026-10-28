package vn.edu.uit.edulens.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.uit.edulens.dto.request.CreateAssignmentRequest;
import vn.edu.uit.edulens.dto.request.GradeSubmissionRequest;
import vn.edu.uit.edulens.dto.request.SubmitAssignmentRequest;
import vn.edu.uit.edulens.dto.response.AssignmentDetailResponse;
import vn.edu.uit.edulens.dto.response.AssignmentSubmissionListResponse;
import vn.edu.uit.edulens.dto.response.ClassDetailResponse;
import vn.edu.uit.edulens.entity.Assignment;
import vn.edu.uit.edulens.entity.ClassMember;
import vn.edu.uit.edulens.entity.CourseClass;
import vn.edu.uit.edulens.entity.Submission;
import vn.edu.uit.edulens.entity.User;
import vn.edu.uit.edulens.entity.enums.ClassStatus;
import vn.edu.uit.edulens.exception.AppException;
import vn.edu.uit.edulens.exception.ErrorCode;
import vn.edu.uit.edulens.repository.AssignmentRepository;
import vn.edu.uit.edulens.repository.ClassMemberRepository;
import vn.edu.uit.edulens.repository.CourseClassRepository;
import vn.edu.uit.edulens.repository.SubmissionRepository;
import vn.edu.uit.edulens.repository.UserRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final CourseClassRepository courseClassRepository;
    private final ClassMemberRepository classMemberRepository;

    @Transactional(readOnly = true)
    public AssignmentDetailResponse getAssignmentDetail(UUID assignmentId, UUID studentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        Optional<Submission> subOpt = studentId != null
                ? submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId)
                : Optional.empty();

        return buildAssignmentDetailResponse(assignment, subOpt);
    }

    @Transactional
    public ClassDetailResponse.ClassAssignmentItem createAssignment(UUID classId, CreateAssignmentRequest request) {
        if (request == null || request.getTitle() == null || request.getTitle().isBlank()) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Tiêu đề bài tập không được để trống");
        }

        CourseClass cc = courseClassRepository.findById(classId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));

        if (cc.getStatus() == ClassStatus.ARCHIVED) {
            throw new AppException(ErrorCode.CLASS_ARCHIVED);
        }

        Instant deadlineInstant = parseDeadline(request.getDeadline());

        Assignment assignment = Assignment.builder()
                .id(UUID.randomUUID())
                .courseClass(cc)
                .title(request.getTitle().trim())
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .deadline(deadlineInstant)
                .attachmentUrl(request.getAttachmentUrl() != null && !request.getAttachmentUrl().isBlank()
                        ? request.getAttachmentUrl().trim()
                        : null)
                .createdAt(Instant.now())
                .build();

        Assignment saved = assignmentRepository.save(assignment);
        Assignment effective = saved != null ? saved : assignment;

        return new ClassDetailResponse.ClassAssignmentItem(
                effective.getId(),
                effective.getTitle(),
                effective.getDescription(),
                effective.getDeadline() != null ? effective.getDeadline().toString() : "",
                effective.getAttachmentUrl(),
                false,
                null,
                null
        );
    }

    @Transactional(readOnly = true)
    public AssignmentSubmissionListResponse getAssignmentSubmissions(UUID assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        CourseClass cc = assignment.getCourseClass();
        String classCode = cc != null ? cc.getCode() : "";
        String deadlineStr = assignment.getDeadline() != null ? assignment.getDeadline().toString() : "";

        List<ClassMember> members = (cc != null && cc.getId() != null)
                ? classMemberRepository.findByCourseClassId(cc.getId())
                : Collections.emptyList();
        List<Submission> existingSubmissions = submissionRepository.findByAssignmentId(assignmentId);

        Map<UUID, Submission> submissionByStudent = new LinkedHashMap<>();
        if (existingSubmissions != null) {
            for (Submission sub : existingSubmissions) {
                if (sub != null && sub.getStudent() != null && sub.getStudent().getId() != null) {
                    submissionByStudent.put(sub.getStudent().getId(), sub);
                }
            }
        }

        List<AssignmentSubmissionListResponse.SubmissionItem> items = new ArrayList<>();
        int submittedCount = 0;
        int gradedCount = 0;
        int onTimeCount = 0;
        int lateCount = 0;
        int notSubmittedCount = 0;

        if (members != null) {
            for (ClassMember member : members) {
                User student = member.getStudent();
                if (student == null || student.getId() == null) continue;

                Submission sub = submissionByStudent.remove(student.getId());
                if (sub == null) {
                    sub = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, student.getId()).orElse(null);
                }

                AssignmentSubmissionListResponse.SubmissionItem item = buildSubmissionItem(assignment, student, sub);
                items.add(item);

                if (item.isSubmitted()) {
                    submittedCount++;
                    if (item.isLate()) {
                        lateCount++;
                    } else {
                        onTimeCount++;
                    }
                    if (item.getScore() != null) {
                        gradedCount++;
                    }
                } else {
                    notSubmittedCount++;
                }
            }
        }

        for (Submission remainingSub : submissionByStudent.values()) {
            User student = remainingSub.getStudent();
            AssignmentSubmissionListResponse.SubmissionItem item = buildSubmissionItem(assignment, student, remainingSub);
            items.add(item);
            if (item.isSubmitted()) {
                submittedCount++;
                if (item.isLate()) {
                    lateCount++;
                } else {
                    onTimeCount++;
                }
                if (item.getScore() != null) {
                    gradedCount++;
                }
            } else {
                notSubmittedCount++;
            }
        }

        return AssignmentSubmissionListResponse.builder()
                .assignmentId(assignment.getId())
                .assignmentTitle(assignment.getTitle())
                .classCode(classCode)
                .deadline(deadlineStr)
                .totalStudents(items.size())
                .submittedCount(submittedCount)
                .gradedCount(gradedCount)
                .onTimeCount(onTimeCount)
                .lateCount(lateCount)
                .notSubmittedCount(notSubmittedCount)
                .submissions(items)
                .build();
    }

    @Transactional
    public AssignmentSubmissionListResponse gradeSubmission(
            UUID assignmentId,
            UUID studentId,
            GradeSubmissionRequest request
    ) {
        if (request == null || request.getScore() == null
                || request.getScore() < 0.0 || request.getScore() > 10.0) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Điểm số phải nằm trong khoảng từ 0.0 đến 10.0");
        }

        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        Optional<Submission> existingOpt = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId);
        Submission submission;
        if (existingOpt.isPresent()) {
            submission = existingOpt.get();
        } else {
            User student = userRepository.findById(studentId)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
            submission = Submission.builder()
                    .id(UUID.randomUUID())
                    .assignment(assignment)
                    .student(student)
                    .fileUrl("graded-by-lecturer")
                    .submittedAt(Instant.now())
                    .build();
        }

        double roundedScore = Math.round(request.getScore() * 10.0) / 10.0;
        submission.setScore(roundedScore);
        submission.setFeedback(request.getFeedback() != null ? request.getFeedback().trim() : null);

        submissionRepository.save(submission);
        return getAssignmentSubmissions(assignmentId);
    }

    @Transactional
    public AssignmentDetailResponse submitAssignment(UUID assignmentId, UUID studentId, SubmitAssignmentRequest request) {
        if (request == null || request.getFileUrl() == null || request.getFileUrl().isBlank()) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Vui lòng chọn tệp bài nộp");
        }

        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        String trimmedUrl = request.getFileUrl().trim();
        Instant now = Instant.now();

        Optional<Submission> existingOpt = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId);
        Submission submission;
        if (existingOpt.isPresent()) {
            submission = existingOpt.get();
            submission.setFileUrl(trimmedUrl);
            submission.setSubmittedAt(now);
        } else {
            submission = Submission.builder()
                    .id(UUID.randomUUID())
                    .assignment(assignment)
                    .student(student)
                    .fileUrl(trimmedUrl)
                    .submittedAt(now)
                    .build();
        }

        Submission saved = submissionRepository.save(submission);
        Submission effective = saved != null ? saved : submission;

        return buildAssignmentDetailResponse(assignment, Optional.of(effective));
    }

    private AssignmentSubmissionListResponse.SubmissionItem buildSubmissionItem(
            Assignment assignment,
            User student,
            Submission sub
    ) {
        boolean submitted = sub != null;
        boolean late = submitted
                && sub.getSubmittedAt() != null
                && assignment.getDeadline() != null
                && sub.getSubmittedAt().isAfter(assignment.getDeadline());

        String fullName = student != null ? student.getFullName() : "";
        return AssignmentSubmissionListResponse.SubmissionItem.builder()
                .submissionId(sub != null ? sub.getId() : null)
                .studentId(student != null ? student.getId() : null)
                .studentName(fullName)
                .studentCode(ClassService.extractStudentCode(student))
                .avatarInitial(ClassService.extractLecturerInitial(fullName))
                .submitted(submitted)
                .late(late)
                .submittedAt(sub != null && sub.getSubmittedAt() != null ? sub.getSubmittedAt().toString() : null)
                .fileUrl(sub != null ? sub.getFileUrl() : null)
                .score(sub != null ? sub.getScore() : null)
                .feedback(sub != null ? sub.getFeedback() : null)
                .build();
    }

    private Instant parseDeadline(String rawDeadline) {
        if (rawDeadline == null || rawDeadline.isBlank()) {
            return Instant.now().plus(7, ChronoUnit.DAYS);
        }
        String trimmed = rawDeadline.trim();
        try {
            return Instant.parse(trimmed);
        } catch (Exception ignored) {
        }
        try {
            return OffsetDateTime.parse(trimmed).toInstant();
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(trimmed).atZone(VN_ZONE).toInstant();
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(trimmed, DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                    .atZone(VN_ZONE)
                    .toInstant();
        } catch (Exception ignored) {
        }
        try {
            return LocalDate.parse(trimmed).atTime(23, 59, 59).atZone(VN_ZONE).toInstant();
        } catch (Exception ignored) {
        }
        try {
            return LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    .atTime(23, 59, 59)
                    .atZone(VN_ZONE)
                    .toInstant();
        } catch (Exception ignored) {
        }
        return Instant.now().plus(7, ChronoUnit.DAYS);
    }

    private AssignmentDetailResponse buildAssignmentDetailResponse(Assignment assignment, Optional<Submission> subOpt) {
        CourseClass cc = assignment.getCourseClass();
        UUID classId = cc != null ? cc.getId() : null;
        String classCode = cc != null ? cc.getCode() : "";
        String className = cc != null ? cc.getName() : "";
        String lecturerName = (cc != null && cc.getLecturer() != null) ? cc.getLecturer().getFullName() : "";
        String deadlineStr = assignment.getDeadline() != null ? assignment.getDeadline().toString() : "";

        boolean submitted = subOpt.isPresent();
        String submittedFileUrl = subOpt.map(Submission::getFileUrl).orElse(null);
        String submittedAt = subOpt
                .filter(s -> s.getSubmittedAt() != null)
                .map(s -> s.getSubmittedAt().toString())
                .orElse(null);
        Double score = subOpt.map(Submission::getScore).orElse(null);
        String feedback = subOpt.map(Submission::getFeedback).orElse(null);

        return AssignmentDetailResponse.builder()
                .id(assignment.getId())
                .classId(classId)
                .classCode(classCode)
                .className(className)
                .lecturerName(lecturerName)
                .title(assignment.getTitle())
                .description(assignment.getDescription())
                .deadline(deadlineStr)
                .attachmentUrl(assignment.getAttachmentUrl())
                .submitted(submitted)
                .submittedFileUrl(submittedFileUrl)
                .submittedAt(submittedAt)
                .score(score)
                .feedback(feedback)
                .build();
    }
}

package vn.edu.uit.edulens.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.uit.edulens.dto.request.SubmitAssignmentRequest;
import vn.edu.uit.edulens.dto.response.AssignmentDetailResponse;
import vn.edu.uit.edulens.entity.Assignment;
import vn.edu.uit.edulens.entity.CourseClass;
import vn.edu.uit.edulens.entity.Submission;
import vn.edu.uit.edulens.entity.User;
import vn.edu.uit.edulens.exception.AppException;
import vn.edu.uit.edulens.exception.ErrorCode;
import vn.edu.uit.edulens.repository.AssignmentRepository;
import vn.edu.uit.edulens.repository.SubmissionRepository;
import vn.edu.uit.edulens.repository.UserRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;

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

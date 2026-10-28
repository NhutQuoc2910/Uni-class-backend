package vn.edu.uit.edulens.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.uit.edulens.dto.response.ClassDetailResponse;
import vn.edu.uit.edulens.dto.response.CourseClassItem;
import vn.edu.uit.edulens.dto.response.NotificationItemResponse;
import vn.edu.uit.edulens.entity.*;
import vn.edu.uit.edulens.entity.enums.AttendanceStatus;
import vn.edu.uit.edulens.entity.enums.Role;
import vn.edu.uit.edulens.entity.enums.SessionStatus;
import vn.edu.uit.edulens.exception.AppException;
import vn.edu.uit.edulens.exception.ErrorCode;
import vn.edu.uit.edulens.repository.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClassService {

    private final CourseClassRepository courseClassRepository;
    private final ClassMemberRepository classMemberRepository;
    private final UserRepository userRepository;
    private final MaterialRepository materialRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;

    @Transactional(readOnly = true)
    public List<CourseClassItem> getClasses(UUID userId, Role role) {
        List<CourseClassItem> result = new ArrayList<>();
        if (role == Role.ROLE_STUDENT) {
            List<ClassMember> memberships = classMemberRepository.findByStudentId(userId);
            for (ClassMember member : memberships) {
                CourseClass cc = member.getCourseClass();
                if (cc == null) continue;
                result.add(buildCourseClassItem(cc, userId, role));
            }
        } else {
            List<CourseClass> classes = courseClassRepository.findByLecturerId(userId);
            for (CourseClass cc : classes) {
                result.add(buildCourseClassItem(cc, userId, role));
            }
        }
        return result;
    }

    @Transactional
    public CourseClassItem joinClass(UUID studentId, String inviteCode) {
        String normalizedCode = inviteCode != null ? inviteCode.trim().toUpperCase() : "";
        if (normalizedCode.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Vui lòng nhập mã lớp");
        }

        CourseClass cc = courseClassRepository.findByInviteCode(normalizedCode)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));

        if (classMemberRepository.existsByCourseClassIdAndStudentId(cc.getId(), studentId)) {
            throw new AppException(ErrorCode.ALREADY_ENROLLED);
        }

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        ClassMember member = ClassMember.builder()
                .courseClass(cc)
                .student(student)
                .build();
        classMemberRepository.save(member);

        return buildCourseClassItem(cc, studentId, Role.ROLE_STUDENT);
    }

    @Transactional(readOnly = true)
    public ClassDetailResponse getClassDetail(UUID classId, UUID userId, Role role) {
        CourseClass cc = courseClassRepository.findById(classId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));

        Instant now = Instant.now();
        int enrolledCount = (int) classMemberRepository.countByCourseClassId(classId);
        String lecturerName = cc.getLecturer() != null ? cc.getLecturer().getFullName() : "";

        // 1. Materials
        List<Material> materialEntities = materialRepository.findByCourseClassIdOrderByCreatedAtDesc(classId);
        List<ClassDetailResponse.MaterialItem> materials = new ArrayList<>();
        for (Material m : materialEntities) {
            materials.add(new ClassDetailResponse.MaterialItem(
                    m.getId(),
                    m.getTitle(),
                    m.getFileUrl(),
                    m.getFileType() != null ? m.getFileType() : "PDF",
                    m.getCreatedAt() != null ? m.getCreatedAt().toString() : ""
            ));
        }

        // 2. Assignments & Announcements
        List<Assignment> assignmentEntities = assignmentRepository.findByCourseClassIdOrderByDeadlineAsc(classId);
        List<ClassDetailResponse.ClassAssignmentItem> assignments = new ArrayList<>();
        List<ClassDetailResponse.AnnouncementItem> announcements = new ArrayList<>();

        for (Assignment a : assignmentEntities) {
            Optional<Submission> subOpt = role == Role.ROLE_STUDENT
                    ? submissionRepository.findByAssignmentIdAndStudentId(a.getId(), userId)
                    : Optional.empty();
            boolean submitted = subOpt.isPresent();
            Double score = subOpt.map(Submission::getScore).orElse(null);
            String feedback = subOpt.map(Submission::getFeedback).orElse(null);
            String dueDateStr = a.getDeadline() != null ? a.getDeadline().toString() : "";

            assignments.add(new ClassDetailResponse.ClassAssignmentItem(
                    a.getId(),
                    a.getTitle(),
                    a.getDescription(),
                    dueDateStr,
                    a.getAttachmentUrl(),
                    submitted,
                    score,
                    feedback
            ));

            boolean dueSoon = a.getDeadline() != null
                    && a.getDeadline().isAfter(now)
                    && a.getDeadline().isBefore(now.plus(72, ChronoUnit.HOURS));
            boolean urgent = dueSoon && !submitted;
            String annTitle = urgent ? "Nhắc hạn nộp: " + a.getTitle() : "Bài tập: " + a.getTitle();
            String annContent = a.getDescription() != null && !a.getDescription().isBlank()
                    ? a.getDescription()
                    : "Hạn nộp: " + dueDateStr;
            String annCreatedAt = a.getCreatedAt() != null ? a.getCreatedAt().toString() : dueDateStr;

            announcements.add(new ClassDetailResponse.AnnouncementItem(
                    "assign-" + a.getId(),
                    annTitle,
                    annContent,
                    annCreatedAt,
                    urgent,
                    !submitted
            ));
        }

        for (Material m : materialEntities) {
            String fileType = m.getFileType() != null ? m.getFileType() : "PDF";
            announcements.add(new ClassDetailResponse.AnnouncementItem(
                    "mat-" + m.getId(),
                    "Tài liệu mới: " + m.getTitle(),
                    "Giảng viên đã đăng tải tài liệu học tập mới (" + fileType + ").",
                    m.getCreatedAt() != null ? m.getCreatedAt().toString() : "",
                    false,
                    false
            ));
        }

        // 3. Attendance Sessions & Records
        List<AttendanceSession> sessions = attendanceSessionRepository.findByCourseClassIdOrderByCreatedAtDesc(classId);
        List<ClassDetailResponse.AttendanceSessionRecordItem> attendanceRecords = new ArrayList<>();
        int presentCount = 0;
        int absentCount = 0;

        for (int i = 0; i < sessions.size(); i++) {
            AttendanceSession s = sessions.get(i);
            int sessionNumber = sessions.size() - i;
            String dateStr = s.getCreatedAt() != null ? s.getCreatedAt().toString() : "";

            Optional<AttendanceRecord> recordOpt = attendanceRecordRepository.findBySessionIdAndStudentId(s.getId(), userId);
            boolean isPresent = recordOpt.isPresent() && recordOpt.get().getStatus() == AttendanceStatus.PRESENT;
            String status = isPresent ? "PRESENT" : "ABSENT";
            String scannedAt = isPresent && recordOpt.get().getScannedAt() != null
                    ? recordOpt.get().getScannedAt().toString()
                    : null;

            if (isPresent) {
                presentCount++;
            } else {
                absentCount++;
            }

            attendanceRecords.add(new ClassDetailResponse.AttendanceSessionRecordItem(
                    s.getId(),
                    sessionNumber,
                    dateStr,
                    status,
                    scannedAt
            ));
        }

        int totalSessions = sessions.size();
        int attendanceRate = totalSessions > 0
                ? (int) Math.round((presentCount * 100.0) / totalSessions)
                : 0;

        return new ClassDetailResponse(
                cc.getId(),
                cc.getCode(),
                cc.getName(),
                cc.getSemester(),
                cc.getRoom(),
                lecturerName,
                cc.getInviteCode(),
                enrolledCount,
                attendanceRate,
                presentCount,
                absentCount,
                totalSessions,
                announcements,
                materials,
                assignments,
                attendanceRecords
        );
    }

    @Transactional(readOnly = true)
    public List<NotificationItemResponse> getNotifications(UUID userId, Role role) {
        Instant now = Instant.now();
        List<CourseClass> userClasses = new ArrayList<>();
        if (role == Role.ROLE_STUDENT) {
            List<ClassMember> memberships = classMemberRepository.findByStudentId(userId);
            for (ClassMember member : memberships) {
                if (member.getCourseClass() != null) {
                    userClasses.add(member.getCourseClass());
                }
            }
        } else {
            userClasses.addAll(courseClassRepository.findByLecturerId(userId));
        }

        List<NotificationItemResponse> notifications = new ArrayList<>();

        for (CourseClass cc : userClasses) {
            List<Assignment> assignments = assignmentRepository.findByCourseClassIdOrderByDeadlineAsc(cc.getId());
            for (Assignment a : assignments) {
                Optional<Submission> subOpt = role == Role.ROLE_STUDENT
                        ? submissionRepository.findByAssignmentIdAndStudentId(a.getId(), userId)
                        : Optional.empty();
                boolean submitted = subOpt.isPresent();

                if (subOpt.isPresent() && subOpt.get().getScore() != null) {
                    Submission sub = subOpt.get();
                    String feedbackPart = sub.getFeedback() != null && !sub.getFeedback().isBlank()
                            ? " · Nhận xét: " + sub.getFeedback()
                            : "";
                    notifications.add(new NotificationItemResponse(
                            "grade-" + sub.getId(),
                            "Có điểm mới: " + a.getTitle(),
                            "Điểm: " + sub.getScore() + feedbackPart,
                            cc.getCode(),
                            sub.getSubmittedAt() != null ? sub.getSubmittedAt().toString() : "",
                            "GRADE",
                            true
                    ));
                }

                boolean dueWithin72h = a.getDeadline() != null
                        && a.getDeadline().isAfter(now)
                        && a.getDeadline().isBefore(now.plus(72, ChronoUnit.HOURS));

                if (dueWithin72h) {
                    notifications.add(new NotificationItemResponse(
                            "urgent-" + a.getId(),
                            "Sắp hết hạn: " + a.getTitle(),
                            "Lớp " + cc.getCode() + " - Hạn nộp: " + a.getDeadline().toString(),
                            cc.getCode(),
                            a.getCreatedAt() != null ? a.getCreatedAt().toString() : a.getDeadline().toString(),
                            "URGENT",
                            !submitted
                    ));
                } else {
                    String body = a.getDescription() != null && !a.getDescription().isBlank()
                            ? a.getDescription()
                            : "Lớp " + cc.getCode() + " - " + cc.getName();
                    notifications.add(new NotificationItemResponse(
                            "assign-" + a.getId(),
                            "Bài tập mới: " + a.getTitle(),
                            body,
                            cc.getCode(),
                            a.getCreatedAt() != null ? a.getCreatedAt().toString() : "",
                            "ASSIGNMENT",
                            !submitted
                    ));
                }
            }

            List<Material> materials = materialRepository.findByCourseClassIdOrderByCreatedAtDesc(cc.getId());
            for (Material m : materials) {
                notifications.add(new NotificationItemResponse(
                        "mat-" + m.getId(),
                        "Tài liệu mới: " + m.getTitle(),
                        "Lớp " + cc.getCode() + " - " + cc.getName(),
                        cc.getCode(),
                        m.getCreatedAt() != null ? m.getCreatedAt().toString() : "",
                        "GENERAL",
                        false
                ));
            }
        }

        return notifications;
    }

    private CourseClassItem buildCourseClassItem(CourseClass cc, UUID userId, Role role) {
        int enrolled = (int) classMemberRepository.countByCourseClassId(cc.getId());
        long closedSessions = attendanceSessionRepository.countByCourseClassIdAndStatus(cc.getId(), SessionStatus.CLOSED);
        int progress;
        if (role == Role.ROLE_STUDENT) {
            long presentSessions = closedSessions > 0
                    ? attendanceRecordRepository.countPresentInClosedSessionsByStudent(cc.getId(), userId)
                    : 0;
            progress = closedSessions > 0 ? (int) Math.round((presentSessions * 100.0) / closedSessions) : 0;
        } else {
            long classPossible = closedSessions * enrolled;
            long classPresent = classPossible > 0
                    ? attendanceRecordRepository.countPresentInClosedSessions(cc.getId())
                    : 0;
            progress = classPossible > 0 ? (int) Math.round((classPresent * 100.0) / classPossible) : 0;
        }

        String lecturerName = cc.getLecturer() != null ? cc.getLecturer().getFullName() : "";
        return new CourseClassItem(
                cc.getId(),
                cc.getCode(),
                cc.getName(),
                cc.getSemester(),
                cc.getRoom(),
                lecturerName,
                progress,
                enrolled
        );
    }
}

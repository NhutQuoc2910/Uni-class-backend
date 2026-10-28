package vn.edu.uit.edulens.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.uit.edulens.dto.request.CreateClassRequest;
import vn.edu.uit.edulens.dto.request.CreateMaterialRequest;
import vn.edu.uit.edulens.dto.response.*;
import vn.edu.uit.edulens.entity.*;
import vn.edu.uit.edulens.entity.enums.AttendanceStatus;
import vn.edu.uit.edulens.entity.enums.ClassStatus;
import vn.edu.uit.edulens.entity.enums.Role;
import vn.edu.uit.edulens.entity.enums.SessionStatus;
import vn.edu.uit.edulens.exception.AppException;
import vn.edu.uit.edulens.exception.ErrorCode;
import vn.edu.uit.edulens.repository.*;

import java.security.SecureRandom;
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

    private static final String INVITE_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

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
    public CourseClassItem createClass(UUID lecturerId, CreateClassRequest request) {
        if (request == null
                || request.getName() == null || request.getName().isBlank()
                || request.getCode() == null || request.getCode().isBlank()
                || request.getSemester() == null || request.getSemester().isBlank()) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Vui lòng nhập đầy đủ tên môn học, mã lớp và học kỳ");
        }

        User lecturer = userRepository.findById(lecturerId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        String inviteCode = generateUniqueInviteCode();
        String room = request.getRoom() != null && !request.getRoom().isBlank()
                ? request.getRoom().trim()
                : "P.B1.12";

        CourseClass courseClass = CourseClass.builder()
                .id(UUID.randomUUID())
                .code(request.getCode().trim())
                .name(request.getName().trim())
                .semester(request.getSemester().trim())
                .room(room)
                .inviteCode(inviteCode)
                .lecturer(lecturer)
                .status(ClassStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();

        CourseClass saved = courseClassRepository.save(courseClass);
        CourseClass effective = saved != null ? saved : courseClass;
        return buildCourseClassItem(effective, lecturerId, Role.ROLE_LECTURER);
    }

    @Transactional
    public CourseClassItem archiveClass(UUID classId, UUID lecturerId) {
        CourseClass cc = courseClassRepository.findById(classId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));

        cc.setStatus(ClassStatus.ARCHIVED);
        CourseClass saved = courseClassRepository.save(cc);
        CourseClass effective = saved != null ? saved : cc;
        return buildCourseClassItem(effective, lecturerId, Role.ROLE_LECTURER);
    }

    @Transactional
    public CourseClassItem joinClass(UUID studentId, String inviteCode) {
        String normalizedCode = inviteCode != null ? inviteCode.trim().toUpperCase() : "";
        if (normalizedCode.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Vui lòng nhập mã lớp");
        }

        CourseClass cc = courseClassRepository.findByInviteCode(normalizedCode)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));

        if (cc.getStatus() == ClassStatus.ARCHIVED) {
            throw new AppException(ErrorCode.CLASS_ARCHIVED);
        }

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
    public List<ClassMemberItemResponse> getClassMembers(UUID classId) {
        courseClassRepository.findById(classId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));

        List<ClassMember> members = classMemberRepository.findByCourseClassId(classId);
        List<AttendanceSession> sessions = attendanceSessionRepository.findByCourseClassIdOrderByCreatedAtDesc(classId);
        List<Assignment> assignments = assignmentRepository.findByCourseClassIdOrderByDeadlineAsc(classId);

        List<ClassMemberItemResponse> result = new ArrayList<>();
        int totalSessions = sessions != null ? sessions.size() : 0;
        int totalAssignments = assignments != null ? assignments.size() : 0;

        if (members == null) {
            return result;
        }

        for (ClassMember member : members) {
            User student = member.getStudent();
            if (student == null) continue;

            int presentSessions = 0;
            if (sessions != null) {
                for (AttendanceSession session : sessions) {
                    Optional<AttendanceRecord> recOpt =
                            attendanceRecordRepository.findBySessionIdAndStudentId(session.getId(), student.getId());
                    if (recOpt.isPresent() && recOpt.get().getStatus() == AttendanceStatus.PRESENT) {
                        presentSessions++;
                    }
                }
            }
            int absentSessions = Math.max(0, totalSessions - presentSessions);
            int attendanceRate = totalSessions > 0
                    ? (int) Math.round((presentSessions * 100.0) / totalSessions)
                    : 100;

            int submittedAssignments = 0;
            double scoreSum = 0.0;
            int gradedCount = 0;
            List<ClassMemberItemResponse.MemberAssignmentSummary> recentAssignments = new ArrayList<>();

            if (assignments != null) {
                for (Assignment a : assignments) {
                    Optional<Submission> subOpt =
                            submissionRepository.findByAssignmentIdAndStudentId(a.getId(), student.getId());
                    boolean submitted = subOpt.isPresent();
                    if (submitted) {
                        submittedAssignments++;
                    }
                    Double score = subOpt.map(Submission::getScore).orElse(null);
                    if (score != null) {
                        scoreSum += score;
                        gradedCount++;
                    }
                    String submittedAt = subOpt
                            .filter(s -> s.getSubmittedAt() != null)
                            .map(s -> s.getSubmittedAt().toString())
                            .orElse(null);

                    recentAssignments.add(ClassMemberItemResponse.MemberAssignmentSummary.builder()
                            .assignmentId(a.getId())
                            .title(a.getTitle())
                            .deadline(a.getDeadline() != null ? a.getDeadline().toString() : "")
                            .submitted(submitted)
                            .submittedAt(submittedAt)
                            .score(score)
                            .build());
                }
            }

            Double averageScore = gradedCount > 0
                    ? Math.round((scoreSum / gradedCount) * 10.0) / 10.0
                    : null;

            result.add(ClassMemberItemResponse.builder()
                    .studentId(student.getId())
                    .fullName(student.getFullName())
                    .email(student.getEmail())
                    .studentCode(extractStudentCode(student))
                    .avatarInitial(extractLecturerInitial(student.getFullName()))
                    .attendanceRate(attendanceRate)
                    .absentSessions(absentSessions)
                    .totalSessions(totalSessions)
                    .submittedAssignments(submittedAssignments)
                    .totalAssignments(totalAssignments)
                    .averageScore(averageScore)
                    .recentAssignments(recentAssignments)
                    .build());
        }

        return result;
    }

    @Transactional
    public void removeClassMember(UUID classId, UUID studentId) {
        ClassMember member = classMemberRepository.findByCourseClassIdAndStudentId(classId, studentId)
                .orElseThrow(() -> new AppException(ErrorCode.MEMBER_NOT_FOUND));
        classMemberRepository.delete(member);
    }

    @Transactional
    public ClassDetailResponse.MaterialItem createMaterial(UUID classId, CreateMaterialRequest request) {
        if (request == null
                || request.getTitle() == null || request.getTitle().isBlank()
                || request.getFileUrl() == null || request.getFileUrl().isBlank()) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Vui lòng nhập tiêu đề và nội dung/đường dẫn tài liệu");
        }

        CourseClass cc = courseClassRepository.findById(classId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));

        if (cc.getStatus() == ClassStatus.ARCHIVED) {
            throw new AppException(ErrorCode.CLASS_ARCHIVED);
        }

        String fileType;
        if (request.getFileType() != null && !request.getFileType().isBlank()) {
            fileType = request.getFileType().trim().toUpperCase();
        } else if (Boolean.TRUE.equals(request.getUrgentAnnouncement())) {
            fileType = "URGENT_ANNOUNCEMENT";
        } else {
            fileType = "PDF";
        }

        Material material = Material.builder()
                .id(UUID.randomUUID())
                .courseClass(cc)
                .title(request.getTitle().trim())
                .fileUrl(request.getFileUrl().trim())
                .fileType(fileType)
                .createdAt(Instant.now())
                .build();

        Material saved = materialRepository.save(material);
        Material effective = saved != null ? saved : material;

        return new ClassDetailResponse.MaterialItem(
                effective.getId(),
                effective.getTitle(),
                effective.getFileUrl(),
                effective.getFileType() != null ? effective.getFileType() : "PDF",
                effective.getCreatedAt() != null ? effective.getCreatedAt().toString() : Instant.now().toString()
        );
    }

    @Transactional(readOnly = true)
    public ClassStatisticsResponse getClassStatistics(UUID classId) {
        CourseClass cc = courseClassRepository.findById(classId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));

        List<ClassMemberItemResponse> members = getClassMembers(classId);
        int enrolledCount = members.size();

        List<ClassMemberItemResponse> warningStudents = new ArrayList<>();
        int attendanceSum = 0;
        List<Double> allScores = new ArrayList<>();

        for (ClassMemberItemResponse m : members) {
            attendanceSum += m.getAttendanceRate();
            boolean exceedsAbsenceThreshold = m.getTotalSessions() > 0
                    ? ((m.getAbsentSessions() * 100.0) / m.getTotalSessions()) > 20.0
                    : m.getAttendanceRate() < 80;
            if (exceedsAbsenceThreshold) {
                warningStudents.add(m);
            }

            if (m.getRecentAssignments() != null && !m.getRecentAssignments().isEmpty()) {
                for (ClassMemberItemResponse.MemberAssignmentSummary summary : m.getRecentAssignments()) {
                    if (summary.getScore() != null) {
                        allScores.add(summary.getScore());
                    }
                }
            } else if (m.getAverageScore() != null) {
                allScores.add(m.getAverageScore());
            }
        }

        int classAttendanceRate = enrolledCount > 0
                ? (int) Math.round((double) attendanceSum / enrolledCount)
                : 0;

        double averageScore = 0.0;
        if (!allScores.isEmpty()) {
            double sum = 0.0;
            for (Double s : allScores) {
                sum += s;
            }
            averageScore = Math.round((sum / allScores.size()) * 10.0) / 10.0;
        }

        int[] bucketCounts = new int[6];
        for (Double score : allScores) {
            if (score == null) continue;
            double v = Math.max(0.0, Math.min(10.0, score));
            if (v < 2.0) {
                bucketCounts[0]++;
            } else if (v < 4.0) {
                bucketCounts[1]++;
            } else if (v < 6.0) {
                bucketCounts[2]++;
            } else if (v < 8.0) {
                bucketCounts[3]++;
            } else if (v < 9.0) {
                bucketCounts[4]++;
            } else {
                bucketCounts[5]++;
            }
        }

        String[] labels = {"0-2", "2-4", "4-6", "6-8", "8-9", "9-10"};
        int totalScores = allScores.size();
        List<ClassStatisticsResponse.ScoreBucket> scoreDistribution = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            int pct = totalScores > 0 ? (int) Math.round((bucketCounts[i] * 100.0) / totalScores) : 0;
            scoreDistribution.add(ClassStatisticsResponse.ScoreBucket.builder()
                    .label(labels[i])
                    .count(bucketCounts[i])
                    .percentage(pct)
                    .build());
        }

        String statusStr = cc.getStatus() != null ? cc.getStatus().name() : ClassStatus.ACTIVE.name();

        return ClassStatisticsResponse.builder()
                .classId(cc.getId())
                .classCode(cc.getCode())
                .className(cc.getName())
                .semester(cc.getSemester())
                .status(statusStr)
                .enrolledCount(enrolledCount)
                .attendanceRate(classAttendanceRate)
                .warningAbsentCount(warningStudents.size())
                .averageScore(averageScore)
                .warningStudents(warningStudents)
                .scoreDistribution(scoreDistribution)
                .build();
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
            boolean isUrgentAnn = "URGENT_ANNOUNCEMENT".equalsIgnoreCase(fileType);
            boolean isAnn = isUrgentAnn || "ANNOUNCEMENT".equalsIgnoreCase(fileType);
            String annTitle = isAnn ? m.getTitle() : "Tài liệu mới: " + m.getTitle();
            String annContent = isAnn
                    ? m.getFileUrl()
                    : "Giảng viên đã đăng tải tài liệu học tập mới (" + fileType + ").";
            announcements.add(new ClassDetailResponse.AnnouncementItem(
                    "mat-" + m.getId(),
                    annTitle,
                    annContent,
                    m.getCreatedAt() != null ? m.getCreatedAt().toString() : "",
                    isUrgentAnn,
                    isUrgentAnn
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
        int attendanceRate;
        if (role == Role.ROLE_LECTURER) {
            long closedSessions = attendanceSessionRepository.countByCourseClassIdAndStatus(classId, SessionStatus.CLOSED);
            long classPossible = closedSessions * enrolledCount;
            long classPresent = classPossible > 0
                    ? attendanceRecordRepository.countPresentInClosedSessions(classId)
                    : 0;
            attendanceRate = classPossible > 0
                    ? (int) Math.round((classPresent * 100.0) / classPossible)
                    : (totalSessions > 0 ? (int) Math.round((presentCount * 100.0) / totalSessions) : 0);
        } else {
            attendanceRate = totalSessions > 0
                    ? (int) Math.round((presentCount * 100.0) / totalSessions)
                    : 0;
        }

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
    public ClassGradesResponse getClassGrades(UUID classId, UUID studentId) {
        CourseClass cc = courseClassRepository.findById(classId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));

        String lecturerName = cc.getLecturer() != null ? cc.getLecturer().getFullName() : "";
        String lecturerInitial = extractLecturerInitial(lecturerName);

        List<Assignment> assignments = assignmentRepository.findByCourseClassIdOrderByDeadlineAsc(classId);
        List<ClassGradesResponse.GradeItem> gradeItems = new ArrayList<>();

        double scoreSum = 0.0;
        int gradedCount = 0;

        for (Assignment a : assignments) {
            Optional<Submission> subOpt = studentId != null
                    ? submissionRepository.findByAssignmentIdAndStudentId(a.getId(), studentId)
                    : Optional.empty();
            boolean submitted = subOpt.isPresent();
            Double score = subOpt.map(Submission::getScore).orElse(null);
            String feedback = subOpt.map(Submission::getFeedback).orElse(null);
            String submittedAt = subOpt
                    .filter(s -> s.getSubmittedAt() != null)
                    .map(s -> s.getSubmittedAt().toString())
                    .orElse(null);

            if (score != null) {
                scoreSum += score;
                gradedCount++;
            }

            gradeItems.add(ClassGradesResponse.GradeItem.builder()
                    .assignmentId(a.getId())
                    .assignmentTitle(a.getTitle())
                    .submittedAt(submittedAt)
                    .submitted(submitted)
                    .score(score)
                    .feedback(feedback)
                    .lecturerName(lecturerName)
                    .lecturerInitial(lecturerInitial)
                    .build());
        }

        double averageScore = gradedCount > 0
                ? Math.round((scoreSum / gradedCount) * 10.0) / 10.0
                : 0.0;

        return ClassGradesResponse.builder()
                .classId(cc.getId())
                .classCode(cc.getCode())
                .className(cc.getName())
                .lecturerName(lecturerName)
                .averageScore(averageScore)
                .gradedCount(gradedCount)
                .totalAssignments(assignments.size())
                .grades(gradeItems)
                .build();
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
        String statusStr = cc.getStatus() != null ? cc.getStatus().name() : ClassStatus.ACTIVE.name();
        return new CourseClassItem(
                cc.getId(),
                cc.getCode(),
                cc.getName(),
                cc.getSemester(),
                cc.getRoom(),
                lecturerName,
                cc.getInviteCode(),
                statusStr,
                progress,
                enrolled
        );
    }

    private String generateUniqueInviteCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder(7);
            for (int i = 0; i < 6; i++) {
                if (i == 3) {
                    sb.append('-');
                }
                sb.append(INVITE_CODE_CHARS.charAt(RANDOM.nextInt(INVITE_CODE_CHARS.length())));
            }
            String candidate = sb.toString();
            if (!courseClassRepository.existsByInviteCode(candidate)) {
                return candidate;
            }
        }
        return UUID.randomUUID().toString().substring(0, 3).toUpperCase()
                + "-"
                + UUID.randomUUID().toString().substring(4, 7).toUpperCase();
    }

    static String extractStudentCode(User student) {
        if (student != null && student.getEmail() != null && !student.getEmail().isBlank()) {
            String email = student.getEmail().trim();
            int atIdx = email.indexOf('@');
            String prefix = atIdx > 0 ? email.substring(0, atIdx) : email;
            if (!prefix.isBlank()) {
                return prefix.toUpperCase();
            }
        }
        if (student != null && student.getId() != null) {
            return "SV-" + student.getId().toString().substring(0, 6).toUpperCase();
        }
        return "SV-UIT";
    }

    static String extractLecturerInitial(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "GV";
        }
        String[] parts = fullName.trim().split("\\s+");
        String lastPart = parts[parts.length - 1];
        return !lastPart.isEmpty() ? lastPart.substring(0, 1).toUpperCase() : "GV";
    }
}

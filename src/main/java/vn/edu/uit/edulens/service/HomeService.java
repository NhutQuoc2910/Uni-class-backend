package vn.edu.uit.edulens.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.uit.edulens.dto.response.AssignmentItem;
import vn.edu.uit.edulens.dto.response.CourseClassItem;
import vn.edu.uit.edulens.dto.response.HomeSummaryResponse;
import vn.edu.uit.edulens.entity.Assignment;
import vn.edu.uit.edulens.entity.ClassMember;
import vn.edu.uit.edulens.entity.CourseClass;
import vn.edu.uit.edulens.entity.enums.Role;
import vn.edu.uit.edulens.entity.enums.SessionStatus;
import vn.edu.uit.edulens.repository.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HomeService {

    private final ClassMemberRepository classMemberRepository;
    private final CourseClassRepository courseClassRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;

    @Transactional(readOnly = true)
    public HomeSummaryResponse getSummary(UUID userId, Role role) {
        Instant now = Instant.now();
        List<CourseClassItem> courses = new ArrayList<>();
        List<AssignmentItem> allActiveAssignments = new ArrayList<>();

        long totalPossibleAttendance = 0;
        long totalPresentAttendance = 0;

        if (role == Role.ROLE_STUDENT) {
            List<ClassMember> memberships = classMemberRepository.findByStudentId(userId);
            for (ClassMember member : memberships) {
                CourseClass cc = member.getCourseClass();
                if (cc == null) continue;

                int enrolled = (int) classMemberRepository.countByCourseClassId(cc.getId());
                long closedSessions = attendanceSessionRepository.countByCourseClassIdAndStatus(cc.getId(), SessionStatus.CLOSED);
                long presentSessions = closedSessions > 0
                        ? attendanceRecordRepository.countPresentInClosedSessionsByStudent(cc.getId(), userId)
                        : 0;

                int progress = closedSessions > 0 ? (int) Math.round((presentSessions * 100.0) / closedSessions) : 0;
                totalPossibleAttendance += closedSessions;
                totalPresentAttendance += presentSessions;

                String lecturerName = cc.getLecturer() != null ? cc.getLecturer().getFullName() : "";
                String statusStr = cc.getStatus() != null ? cc.getStatus().name() : "ACTIVE";
                courses.add(new CourseClassItem(
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
                ));

                List<Assignment> activeAssignments = assignmentRepository.findByCourseClassIdAndDeadlineAfterOrderByDeadlineAsc(cc.getId(), now);
                for (Assignment a : activeAssignments) {
                    boolean submitted = submissionRepository.existsByAssignmentIdAndStudentId(a.getId(), userId);
                    allActiveAssignments.add(new AssignmentItem(
                            a.getId(),
                            a.getTitle(),
                            cc.getName(),
                            a.getDeadline() != null ? a.getDeadline().toString() : "",
                            submitted
                    ));
                }
            }
        } else {
            List<CourseClass> classes = courseClassRepository.findByLecturerId(userId);
            for (CourseClass cc : classes) {
                int enrolled = (int) classMemberRepository.countByCourseClassId(cc.getId());
                long closedSessions = attendanceSessionRepository.countByCourseClassIdAndStatus(cc.getId(), SessionStatus.CLOSED);
                long classPossible = closedSessions * enrolled;
                long classPresent = classPossible > 0
                        ? attendanceRecordRepository.countPresentInClosedSessions(cc.getId())
                        : 0;

                int progress = classPossible > 0 ? (int) Math.round((classPresent * 100.0) / classPossible) : 0;
                totalPossibleAttendance += classPossible;
                totalPresentAttendance += classPresent;

                String lecturerName = cc.getLecturer() != null ? cc.getLecturer().getFullName() : "";
                String statusStr = cc.getStatus() != null ? cc.getStatus().name() : "ACTIVE";
                courses.add(new CourseClassItem(
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
                ));

                List<Assignment> activeAssignments = assignmentRepository.findByCourseClassIdAndDeadlineAfterOrderByDeadlineAsc(cc.getId(), now);
                for (Assignment a : activeAssignments) {
                    allActiveAssignments.add(new AssignmentItem(
                            a.getId(),
                            a.getTitle(),
                            cc.getName(),
                            a.getDeadline() != null ? a.getDeadline().toString() : "",
                            false
                    ));
                }
            }
        }

        int overallAttendanceRate = totalPossibleAttendance > 0
                ? (int) Math.round((totalPresentAttendance * 100.0) / totalPossibleAttendance)
                : 0;

        int pendingCount = 0;
        for (AssignmentItem item : allActiveAssignments) {
            if (!item.isSubmitted()) {
                pendingCount++;
            }
        }

        allActiveAssignments.sort(Comparator.comparing(AssignmentItem::getDueDate, Comparator.nullsLast(String::compareTo)));
        List<AssignmentItem> top5DueSoon = allActiveAssignments.stream()
                .limit(5)
                .collect(Collectors.toList());

        return new HomeSummaryResponse(
                courses.size(),
                pendingCount,
                overallAttendanceRate,
                courses,
                top5DueSoon
        );
    }
}

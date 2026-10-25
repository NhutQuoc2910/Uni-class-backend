package vn.edu.uit.edulens.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.uit.edulens.dto.response.HomeSummaryResponse;
import vn.edu.uit.edulens.entity.Assignment;
import vn.edu.uit.edulens.entity.ClassMember;
import vn.edu.uit.edulens.entity.CourseClass;
import vn.edu.uit.edulens.entity.User;
import vn.edu.uit.edulens.entity.enums.Role;
import vn.edu.uit.edulens.entity.enums.SessionStatus;
import vn.edu.uit.edulens.repository.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeServiceTest {

    @Mock
    private ClassMemberRepository classMemberRepository;
    @Mock
    private CourseClassRepository courseClassRepository;
    @Mock
    private AssignmentRepository assignmentRepository;
    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private AttendanceSessionRepository attendanceSessionRepository;
    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @InjectMocks
    private HomeService homeService;

    private UUID studentId;
    private UUID lecturerId;
    private CourseClass sampleClass;

    @BeforeEach
    void setUp() {
        studentId = UUID.randomUUID();
        lecturerId = UUID.randomUUID();

        sampleClass = CourseClass.builder()
                .id(UUID.randomUUID())
                .code("NT118.Q11")
                .name("Phát triển ứng dụng trên thiết bị di động")
                .semester("HK1 2026-2027")
                .inviteCode("NT118DEMO")
                .build();
    }

    @Test
    @DisplayName("Sinh viên: Tính đúng số lớp, % chuyên cần 100% và số bài tập còn hạn chưa nộp")
    void getSummary_Student_CalculatesAttendanceAndPendingAssignments() {
        ClassMember membership = ClassMember.builder()
                .id(UUID.randomUUID())
                .courseClass(sampleClass)
                .student(User.builder().id(studentId).build())
                .build();

        Assignment assignment = Assignment.builder()
                .id(UUID.randomUUID())
                .courseClass(sampleClass)
                .title("Bài tập 2")
                .deadline(Instant.now().plus(2, ChronoUnit.DAYS))
                .build();

        when(classMemberRepository.findByStudentId(studentId)).thenReturn(List.of(membership));
        when(classMemberRepository.countByCourseClassId(sampleClass.getId())).thenReturn(3L);
        when(attendanceSessionRepository.countByCourseClassIdAndStatus(sampleClass.getId(), SessionStatus.CLOSED)).thenReturn(5L);
        when(attendanceRecordRepository.countPresentInClosedSessionsByStudent(sampleClass.getId(), studentId)).thenReturn(5L);
        when(assignmentRepository.findByCourseClassIdAndDeadlineAfterOrderByDeadlineAsc(eq(sampleClass.getId()), any())).thenReturn(List.of(assignment));
        when(submissionRepository.existsByAssignmentIdAndStudentId(assignment.getId(), studentId)).thenReturn(false);

        HomeSummaryResponse response = homeService.getSummary(studentId, Role.ROLE_STUDENT);

        assertNotNull(response);
        assertEquals(1, response.getActiveCoursesCount());
        assertEquals(1, response.getPendingAssignmentsCount());
        assertEquals(100, response.getAttendanceRate());
        assertEquals(100, response.getCourses().get(0).getProgress());
        assertEquals(1, response.getDueSoonAssignments().size());
        assertFalse(response.getDueSoonAssignments().get(0).isSubmitted());
    }

    @Test
    @DisplayName("Giảng viên: Tính đúng số lớp phụ trách và tổng bài tập còn hạn")
    void getSummary_Lecturer_CalculatesClassAverageAndPendingAssignments() {
        Assignment assignment = Assignment.builder()
                .id(UUID.randomUUID())
                .courseClass(sampleClass)
                .title("Bài tập 2")
                .deadline(Instant.now().plus(2, ChronoUnit.DAYS))
                .build();

        when(courseClassRepository.findByLecturerId(lecturerId)).thenReturn(List.of(sampleClass));
        when(classMemberRepository.countByCourseClassId(sampleClass.getId())).thenReturn(3L);
        // 5 buổi điểm danh * 3 sinh viên = 15 lượt. Giả sử có mặt 12 lượt -> 80%
        when(attendanceSessionRepository.countByCourseClassIdAndStatus(sampleClass.getId(), SessionStatus.CLOSED)).thenReturn(5L);
        when(attendanceRecordRepository.countPresentInClosedSessions(sampleClass.getId())).thenReturn(12L);
        when(assignmentRepository.findByCourseClassIdAndDeadlineAfterOrderByDeadlineAsc(eq(sampleClass.getId()), any())).thenReturn(List.of(assignment));

        HomeSummaryResponse response = homeService.getSummary(lecturerId, Role.ROLE_LECTURER);

        assertNotNull(response);
        assertEquals(1, response.getActiveCoursesCount());
        assertEquals(1, response.getPendingAssignmentsCount());
        assertEquals(80, response.getAttendanceRate());
        assertEquals(80, response.getCourses().get(0).getProgress());
        assertEquals(1, response.getDueSoonAssignments().size());
    }

    @Test
    @DisplayName("Xử lý an toàn mẫu số bằng 0 (lớp mới chưa có buổi điểm danh)")
    void getSummary_ZeroDivision_ReturnsZeroSafely() {
        ClassMember membership = ClassMember.builder()
                .id(UUID.randomUUID())
                .courseClass(sampleClass)
                .student(User.builder().id(studentId).build())
                .build();

        when(classMemberRepository.findByStudentId(studentId)).thenReturn(List.of(membership));
        when(classMemberRepository.countByCourseClassId(sampleClass.getId())).thenReturn(0L);
        when(attendanceSessionRepository.countByCourseClassIdAndStatus(sampleClass.getId(), SessionStatus.CLOSED)).thenReturn(0L);
        when(assignmentRepository.findByCourseClassIdAndDeadlineAfterOrderByDeadlineAsc(eq(sampleClass.getId()), any())).thenReturn(Collections.emptyList());

        HomeSummaryResponse response = homeService.getSummary(studentId, Role.ROLE_STUDENT);

        assertNotNull(response);
        assertEquals(0, response.getAttendanceRate());
        assertEquals(0, response.getCourses().get(0).getProgress());
        assertEquals(0, response.getPendingAssignmentsCount());
    }
}
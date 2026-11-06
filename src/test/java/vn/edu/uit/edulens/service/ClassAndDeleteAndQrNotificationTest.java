package vn.edu.uit.edulens.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.uit.edulens.dto.response.AssignmentItem;
import vn.edu.uit.edulens.dto.response.ClassDetailResponse;
import vn.edu.uit.edulens.dto.response.NotificationItemResponse;
import vn.edu.uit.edulens.entity.*;
import vn.edu.uit.edulens.entity.enums.AttendanceStatus;
import vn.edu.uit.edulens.entity.enums.Role;
import vn.edu.uit.edulens.entity.enums.SessionStatus;
import vn.edu.uit.edulens.exception.AppException;
import vn.edu.uit.edulens.exception.ErrorCode;
import vn.edu.uit.edulens.repository.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassAndDeleteAndQrNotificationTest {

    @Mock
    private CourseClassRepository courseClassRepository;
    @Mock
    private ClassMemberRepository classMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MaterialRepository materialRepository;
    @Mock
    private AssignmentRepository assignmentRepository;
    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private AttendanceSessionRepository attendanceSessionRepository;
    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;
    @Mock
    private ClassScheduleRepository classScheduleRepository;
    @Mock
    private ScheduleExceptionRepository scheduleExceptionRepository;

    private ClassService classService;
    private AssignmentService assignmentService;
    private ScheduleService scheduleService;
    private HomeService homeService;

    private UUID lecturerId;
    private UUID otherLecturerId;
    private UUID studentId;
    private UUID classId;
    private User lecturer;
    private User otherLecturer;
    private User student;
    private CourseClass courseClass;

    @BeforeEach
    void setUp() {
        classService = new ClassService(
                courseClassRepository,
                classMemberRepository,
                userRepository,
                materialRepository,
                assignmentRepository,
                submissionRepository,
                attendanceSessionRepository,
                attendanceRecordRepository
        );

        assignmentService = new AssignmentService(
                assignmentRepository,
                submissionRepository,
                userRepository,
                courseClassRepository,
                classMemberRepository
        );

        scheduleService = new ScheduleService(
                courseClassRepository,
                classScheduleRepository,
                scheduleExceptionRepository,
                classMemberRepository
        );

        homeService = new HomeService(
                classMemberRepository,
                courseClassRepository,
                assignmentRepository,
                submissionRepository,
                attendanceSessionRepository,
                attendanceRecordRepository
        );

        lecturerId = UUID.randomUUID();
        otherLecturerId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        classId = UUID.randomUUID();

        lecturer = User.builder()
                .id(lecturerId)
                .fullName("TS. Giảng Viên Chính")
                .role(Role.ROLE_LECTURER)
                .build();

        otherLecturer = User.builder()
                .id(otherLecturerId)
                .fullName("ThS. Giảng Viên Khác")
                .role(Role.ROLE_LECTURER)
                .build();

        student = User.builder()
                .id(studentId)
                .fullName("Nguyễn Văn Học")
                .role(Role.ROLE_STUDENT)
                .build();

        courseClass = CourseClass.builder()
                .id(classId)
                .code("NT118.Q11")
                .name("Phát triển ứng dụng di động")
                .semester("HK1 2026-2027")
                .room("C302")
                .lecturer(lecturer)
                .build();
    }

    // ==========================================
    // 1. DELETE MATERIAL TESTS
    // ==========================================
    @Test
    @DisplayName("deleteMaterial: Giảng viên phụ trách xóa tài liệu thành công")
    void deleteMaterial_Success() {
        UUID materialId = UUID.randomUUID();
        Material material = Material.builder()
                .id(materialId)
                .courseClass(courseClass)
                .title("Slide Bài 1")
                .fileUrl("https://uit.edu.vn/slide1.pdf")
                .build();

        when(courseClassRepository.findById(classId)).thenReturn(Optional.of(courseClass));
        when(materialRepository.findById(materialId)).thenReturn(Optional.of(material));

        classService.deleteMaterial(classId, materialId, lecturerId);

        verify(materialRepository, times(1)).delete(material);
    }

    @Test
    @DisplayName("deleteMaterial: Bị từ chối khi giảng viên không sở hữu lớp")
    void deleteMaterial_ForbiddenWhenNotLecturer() {
        UUID materialId = UUID.randomUUID();

        when(courseClassRepository.findById(classId)).thenReturn(Optional.of(courseClass));

        AppException ex = assertThrows(AppException.class, () ->
                classService.deleteMaterial(classId, materialId, otherLecturerId)
        );
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(materialRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteMaterial: Báo lỗi khi tài liệu không thuộc lớp chỉ định")
    void deleteMaterial_ForbiddenWhenClassMismatch() {
        UUID materialId = UUID.randomUUID();
        CourseClass otherClass = CourseClass.builder()
                .id(UUID.randomUUID())
                .code("IT001")
                .lecturer(lecturer)
                .build();
        Material material = Material.builder()
                .id(materialId)
                .courseClass(otherClass)
                .title("Tài liệu lớp khác")
                .build();

        when(courseClassRepository.findById(classId)).thenReturn(Optional.of(courseClass));
        when(materialRepository.findById(materialId)).thenReturn(Optional.of(material));

        AppException ex = assertThrows(AppException.class, () ->
                classService.deleteMaterial(classId, materialId, lecturerId)
        );
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(materialRepository, never()).delete(any());
    }

    // ==========================================
    // 2. DELETE ASSIGNMENT TESTS
    // ==========================================
    @Test
    @DisplayName("deleteAssignment: Xóa bài tập và các bài nộp liên quan thành công")
    void deleteAssignment_Success() {
        UUID assignmentId = UUID.randomUUID();
        Assignment assignment = Assignment.builder()
                .id(assignmentId)
                .courseClass(courseClass)
                .title("Bài tập 1")
                .build();

        Submission sub1 = Submission.builder().id(UUID.randomUUID()).assignment(assignment).build();
        Submission sub2 = Submission.builder().id(UUID.randomUUID()).assignment(assignment).build();
        List<Submission> subs = List.of(sub1, sub2);

        when(courseClassRepository.findById(classId)).thenReturn(Optional.of(courseClass));
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(submissionRepository.findByAssignmentId(assignmentId)).thenReturn(subs);

        assignmentService.deleteAssignment(classId, assignmentId, lecturerId);

        verify(submissionRepository, times(1)).deleteAll(subs);
        verify(assignmentRepository, times(1)).delete(assignment);
    }

    @Test
    @DisplayName("deleteAssignment: Bị từ chối khi giảng viên không sở hữu lớp")
    void deleteAssignment_ForbiddenWhenNotLecturer() {
        UUID assignmentId = UUID.randomUUID();

        when(courseClassRepository.findById(classId)).thenReturn(Optional.of(courseClass));

        AppException ex = assertThrows(AppException.class, () ->
                assignmentService.deleteAssignment(classId, assignmentId, otherLecturerId)
        );
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(assignmentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteAssignment: Ném lỗi ASSIGNMENT_NOT_FOUND khi bài tập không tồn tại")
    void deleteAssignment_NotFound() {
        UUID assignmentId = UUID.randomUUID();

        when(courseClassRepository.findById(classId)).thenReturn(Optional.of(courseClass));
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                assignmentService.deleteAssignment(classId, assignmentId, lecturerId)
        );
        assertEquals(ErrorCode.ASSIGNMENT_NOT_FOUND, ex.getErrorCode());
    }

    // ==========================================
    // 3. DELETE SCHEDULE TESTS
    // ==========================================
    @Test
    @DisplayName("deleteSchedule: Xóa lịch học và dọn sạch các ScheduleException phụ thuộc")
    void deleteSchedule_Success() {
        UUID scheduleId = UUID.randomUUID();
        ClassSchedule schedule = ClassSchedule.builder()
                .id(scheduleId)
                .courseClass(courseClass)
                .dayOfWeek(2)
                .startTime(LocalTime.of(7, 30))
                .endTime(LocalTime.of(11, 30))
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(3))
                .build();

        ScheduleException ex1 = ScheduleException.builder()
                .id(UUID.randomUUID())
                .courseClass(courseClass)
                .classSchedule(schedule)
                .build();

        ScheduleException dependentEx = ScheduleException.builder()
                .id(UUID.randomUUID())
                .courseClass(courseClass)
                .relatedException(ex1)
                .build();

        when(courseClassRepository.findById(classId)).thenReturn(Optional.of(courseClass));
        when(classScheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));
        when(scheduleExceptionRepository.findByClassScheduleId(scheduleId)).thenReturn(new ArrayList<>(List.of(ex1)));
        when(scheduleExceptionRepository.findByRelatedExceptionId(ex1.getId())).thenReturn(new ArrayList<>(List.of(dependentEx)));

        scheduleService.deleteSchedule(classId, scheduleId, lecturerId);

        assertNull(dependentEx.getRelatedException());
        assertNull(ex1.getRelatedException());
        verify(scheduleExceptionRepository, times(1)).saveAll(List.of(dependentEx));
        verify(scheduleExceptionRepository, times(1)).deleteAll(List.of(ex1));
        verify(classScheduleRepository, times(1)).delete(schedule);
    }

    @Test
    @DisplayName("deleteSchedule: Ném lỗi FORBIDDEN khi người gọi không phải giảng viên của lớp")
    void deleteSchedule_ForbiddenWhenNotLecturer() {
        UUID scheduleId = UUID.randomUUID();
        when(courseClassRepository.findById(classId)).thenReturn(Optional.of(courseClass));

        AppException ex = assertThrows(AppException.class, () ->
                scheduleService.deleteSchedule(classId, scheduleId, otherLecturerId)
        );
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(classScheduleRepository, never()).delete(any());
    }

    // ==========================================
    // 4. ALL GRADING ASSIGNMENTS TESTS
    // ==========================================
    @Test
    @DisplayName("getAllGradingAssignments: Trả về tất cả bài tập cần chấm điểm không bị giới hạn 5 mục")
    void getAllGradingAssignments_ReturnsAllWithoutLimit() {
        CourseClass cc1 = CourseClass.builder().id(UUID.randomUUID()).name("Lớp 1").build();
        CourseClass cc2 = CourseClass.builder().id(UUID.randomUUID()).name("Lớp 2").build();

        when(courseClassRepository.findByLecturerId(lecturerId)).thenReturn(List.of(cc1, cc2));

        List<Assignment> class1Assignments = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            class1Assignments.add(Assignment.builder()
                    .id(UUID.randomUUID())
                    .title("Assignment 1-" + i)
                    .deadline(Instant.now().plus(i, ChronoUnit.DAYS))
                    .build());
        }

        List<Assignment> class2Assignments = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            class2Assignments.add(Assignment.builder()
                    .id(UUID.randomUUID())
                    .title("Assignment 2-" + i)
                    .deadline(Instant.now().plus(i + 5, ChronoUnit.DAYS))
                    .build());
        }

        when(assignmentRepository.findByCourseClassIdOrderByDeadlineAsc(cc1.getId())).thenReturn(class1Assignments);
        when(assignmentRepository.findByCourseClassIdOrderByDeadlineAsc(cc2.getId())).thenReturn(class2Assignments);

        // Tất cả bài tập đều chưa được chấm điểm
        when(submissionRepository.existsByAssignmentIdAndScoreIsNull(any(UUID.class))).thenReturn(true);

        List<AssignmentItem> result = homeService.getAllGradingAssignments(lecturerId);

        assertEquals(8, result.size());
        // Kiểm tra sắp xếp theo dueDate tăng dần
        for (int i = 0; i < result.size() - 1; i++) {
            assertTrue(result.get(i).getDueDate().compareTo(result.get(i + 1).getDueDate()) <= 0);
        }
    }

    // ==========================================
    // 5. OPEN ATTENDANCE SESSION NOTIFICATIONS
    // ==========================================
    @Test
    @DisplayName("getClassDetail: Khi có phiên điểm danh OPEN, tạo thông báo khẩn cấp đầu danh sách")
    void getClassDetail_OpenAttendanceSession_PrependsAnnouncement() {
        AttendanceSession openSession = AttendanceSession.builder()
                .id(UUID.randomUUID())
                .courseClass(courseClass)
                .status(SessionStatus.OPEN)
                .createdAt(Instant.now())
                .build();

        when(courseClassRepository.findById(classId)).thenReturn(Optional.of(courseClass));
        when(classMemberRepository.countByCourseClassId(classId)).thenReturn(30L);
        when(materialRepository.findByCourseClassIdOrderByCreatedAtDesc(classId)).thenReturn(List.of());
        when(assignmentRepository.findByCourseClassIdOrderByDeadlineAsc(classId)).thenReturn(List.of());
        when(attendanceSessionRepository.findByCourseClassIdOrderByCreatedAtDesc(classId)).thenReturn(List.of(openSession));
        when(attendanceRecordRepository.findBySessionIdAndStudentId(openSession.getId(), studentId)).thenReturn(Optional.empty());

        ClassDetailResponse detail = classService.getClassDetail(classId, studentId, Role.ROLE_STUDENT);

        assertNotNull(detail);
        assertFalse(detail.getAnnouncements().isEmpty());
        ClassDetailResponse.AnnouncementItem topItem = detail.getAnnouncements().get(0);
        assertEquals("att-" + openSession.getId(), topItem.getId());
        assertTrue(topItem.isUrgent());
        assertTrue(topItem.isUnread());
        assertTrue(topItem.getTitle().contains("Điểm danh đang mở"));
        assertTrue(topItem.getContent().contains("Tải mã QR"));
    }

    @Test
    @DisplayName("getClassDetail: Khi sinh viên đã điểm danh (PRESENT) trong phiên OPEN, unread = false")
    void getClassDetail_OpenAttendanceSession_AlreadyPresent_UnreadIsFalse() {
        AttendanceSession openSession = AttendanceSession.builder()
                .id(UUID.randomUUID())
                .courseClass(courseClass)
                .status(SessionStatus.OPEN)
                .createdAt(Instant.now())
                .build();

        AttendanceRecord record = AttendanceRecord.builder()
                .session(openSession)
                .student(student)
                .status(AttendanceStatus.PRESENT)
                .scannedAt(Instant.now())
                .build();

        when(courseClassRepository.findById(classId)).thenReturn(Optional.of(courseClass));
        when(classMemberRepository.countByCourseClassId(classId)).thenReturn(30L);
        when(materialRepository.findByCourseClassIdOrderByCreatedAtDesc(classId)).thenReturn(List.of());
        when(assignmentRepository.findByCourseClassIdOrderByDeadlineAsc(classId)).thenReturn(List.of());
        when(attendanceSessionRepository.findByCourseClassIdOrderByCreatedAtDesc(classId)).thenReturn(List.of(openSession));
        when(attendanceRecordRepository.findBySessionIdAndStudentId(openSession.getId(), studentId)).thenReturn(Optional.of(record));

        ClassDetailResponse detail = classService.getClassDetail(classId, studentId, Role.ROLE_STUDENT);

        assertNotNull(detail);
        ClassDetailResponse.AnnouncementItem topItem = detail.getAnnouncements().get(0);
        assertEquals("att-" + openSession.getId(), topItem.getId());
        assertTrue(topItem.isUrgent());
        assertFalse(topItem.isUnread());
    }

    @Test
    @DisplayName("getNotifications: Khi có phiên điểm danh OPEN, tạo NotificationItem URGENT đầu danh sách")
    void getNotifications_OpenAttendanceSession_PrependsNotification() {
        AttendanceSession openSession = AttendanceSession.builder()
                .id(UUID.randomUUID())
                .courseClass(courseClass)
                .status(SessionStatus.OPEN)
                .createdAt(Instant.now())
                .build();

        ClassMember member = ClassMember.builder()
                .courseClass(courseClass)
                .student(student)
                .build();

        when(classMemberRepository.findByStudentId(studentId)).thenReturn(List.of(member));
        when(attendanceSessionRepository.findByCourseClassIdOrderByCreatedAtDesc(courseClass.getId())).thenReturn(List.of(openSession));
        when(attendanceRecordRepository.findBySessionIdAndStudentId(openSession.getId(), studentId)).thenReturn(Optional.empty());
        when(assignmentRepository.findByCourseClassIdOrderByDeadlineAsc(courseClass.getId())).thenReturn(List.of());
        when(materialRepository.findByCourseClassIdOrderByCreatedAtDesc(courseClass.getId())).thenReturn(List.of());

        List<NotificationItemResponse> notifications = classService.getNotifications(studentId, Role.ROLE_STUDENT);

        assertFalse(notifications.isEmpty());
        NotificationItemResponse topNotif = notifications.get(0);
        assertEquals("att-" + openSession.getId(), topNotif.getId());
        assertEquals("URGENT", topNotif.getCategory());
        assertTrue(topNotif.isUnread());
        assertTrue(topNotif.getTitle().contains("Điểm danh đang mở"));
        assertTrue(topNotif.getBody().contains("Tải mã QR"));
    }
}

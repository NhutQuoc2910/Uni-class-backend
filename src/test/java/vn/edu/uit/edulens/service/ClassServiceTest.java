package vn.edu.uit.edulens.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassServiceTest {

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

    @InjectMocks
    private ClassService classService;

    private UUID studentId;
    private UUID classId;
    private User lecturer;
    private User student;
    private CourseClass sampleClass;

    @BeforeEach
    void setUp() {
        studentId = UUID.randomUUID();
        classId = UUID.randomUUID();

        lecturer = User.builder()
                .id(UUID.randomUUID())
                .fullName("TS. Nguyễn Văn Giảng")
                .role(Role.ROLE_LECTURER)
                .build();

        student = User.builder()
                .id(studentId)
                .fullName("Sinh Viên UIT")
                .role(Role.ROLE_STUDENT)
                .build();

        sampleClass = CourseClass.builder()
                .id(classId)
                .code("NT118.Q11")
                .name("Phát triển ứng dụng trên thiết bị di động")
                .semester("HK1 2026-2027")
                .room("C302")
                .inviteCode("NT118DEMO")
                .lecturer(lecturer)
                .build();
    }

    @Test
    @DisplayName("getClasses: Trả về danh sách lớp của sinh viên kèm phòng học, giảng viên và tiến độ")
    void getClasses_Student_ReturnsEnrolledClasses() {
        ClassMember member = ClassMember.builder()
                .id(UUID.randomUUID())
                .courseClass(sampleClass)
                .student(student)
                .build();

        when(classMemberRepository.findByStudentId(studentId)).thenReturn(List.of(member));
        when(classMemberRepository.countByCourseClassId(classId)).thenReturn(35L);
        when(attendanceSessionRepository.countByCourseClassIdAndStatus(classId, SessionStatus.CLOSED)).thenReturn(4L);
        when(attendanceRecordRepository.countPresentInClosedSessionsByStudent(classId, studentId)).thenReturn(3L);

        List<CourseClassItem> classes = classService.getClasses(studentId, Role.ROLE_STUDENT);

        assertEquals(1, classes.size());
        CourseClassItem item = classes.get(0);
        assertEquals(classId, item.getId());
        assertEquals("NT118.Q11", item.getCode());
        assertEquals("C302", item.getRoom());
        assertEquals("TS. Nguyễn Văn Giảng", item.getLecturerName());
        assertEquals(75, item.getProgress());
        assertEquals(35, item.getEnrolledCount());
    }

    @Test
    @DisplayName("joinClass: Tham gia lớp thành công với mã mời hợp lệ")
    void joinClass_Success() {
        when(courseClassRepository.findByInviteCode("NT118DEMO")).thenReturn(Optional.of(sampleClass));
        when(classMemberRepository.existsByCourseClassIdAndStudentId(classId, studentId)).thenReturn(false);
        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(classMemberRepository.countByCourseClassId(classId)).thenReturn(10L);
        when(attendanceSessionRepository.countByCourseClassIdAndStatus(classId, SessionStatus.CLOSED)).thenReturn(0L);

        CourseClassItem joined = classService.joinClass(studentId, "  nt118demo ");

        assertNotNull(joined);
        assertEquals("NT118.Q11", joined.getCode());
        assertEquals("C302", joined.getRoom());
        verify(classMemberRepository, times(1)).save(any(ClassMember.class));
    }

    @Test
    @DisplayName("joinClass: Ném lỗi CLASS_NOT_FOUND khi mã mời không tồn tại")
    void joinClass_ClassNotFound_ThrowsAppException() {
        when(courseClassRepository.findByInviteCode("INVALID")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> classService.joinClass(studentId, "INVALID"));
        assertEquals(ErrorCode.CLASS_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("joinClass: Ném lỗi ALREADY_ENROLLED khi sinh viên đã trong lớp")
    void joinClass_AlreadyEnrolled_ThrowsAppException() {
        when(courseClassRepository.findByInviteCode("NT118DEMO")).thenReturn(Optional.of(sampleClass));
        when(classMemberRepository.existsByCourseClassIdAndStudentId(classId, studentId)).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> classService.joinClass(studentId, "NT118DEMO"));
        assertEquals(ErrorCode.ALREADY_ENROLLED, ex.getErrorCode());
    }

    @Test
    @DisplayName("getClassDetail: Trả về đầy đủ 4 tab (Thông báo, Tài liệu, Bài tập, Điểm danh)")
    void getClassDetail_ReturnsAllFourTabs() {
        Material mat = Material.builder()
                .id(UUID.randomUUID())
                .courseClass(sampleClass)
                .title("Slide Chương 1")
                .fileUrl("https://uit.edu.vn/slide1.pdf")
                .fileType("PDF")
                .createdAt(Instant.now().minus(3, ChronoUnit.DAYS))
                .build();

        Assignment assign = Assignment.builder()
                .id(UUID.randomUUID())
                .courseClass(sampleClass)
                .title("Lab 1: UI Layout")
                .description("Thiết kế màn hình bằng ConstraintLayout")
                .deadline(Instant.now().plus(1, ChronoUnit.DAYS))
                .createdAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        Submission sub = Submission.builder()
                .id(UUID.randomUUID())
                .assignment(assign)
                .student(student)
                .score(9.5)
                .feedback("Làm rất tốt")
                .build();

        AttendanceSession session1 = AttendanceSession.builder()
                .id(UUID.randomUUID())
                .courseClass(sampleClass)
                .status(SessionStatus.CLOSED)
                .createdAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        AttendanceSession session2 = AttendanceSession.builder()
                .id(UUID.randomUUID())
                .courseClass(sampleClass)
                .status(SessionStatus.CLOSED)
                .createdAt(Instant.now().minus(8, ChronoUnit.DAYS))
                .build();

        AttendanceRecord rec1 = AttendanceRecord.builder()
                .id(UUID.randomUUID())
                .session(session1)
                .student(student)
                .status(AttendanceStatus.PRESENT)
                .scannedAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        when(courseClassRepository.findById(classId)).thenReturn(Optional.of(sampleClass));
        when(classMemberRepository.countByCourseClassId(classId)).thenReturn(40L);
        when(materialRepository.findByCourseClassIdOrderByCreatedAtDesc(classId)).thenReturn(List.of(mat));
        when(assignmentRepository.findByCourseClassIdOrderByDeadlineAsc(classId)).thenReturn(List.of(assign));
        when(submissionRepository.findByAssignmentIdAndStudentId(assign.getId(), studentId)).thenReturn(Optional.of(sub));
        when(attendanceSessionRepository.findByCourseClassIdOrderByCreatedAtDesc(classId)).thenReturn(List.of(session1, session2));
        when(attendanceRecordRepository.findBySessionIdAndStudentId(session1.getId(), studentId)).thenReturn(Optional.of(rec1));
        when(attendanceRecordRepository.findBySessionIdAndStudentId(session2.getId(), studentId)).thenReturn(Optional.empty());

        ClassDetailResponse detail = classService.getClassDetail(classId, studentId, Role.ROLE_STUDENT);

        assertEquals("NT118.Q11", detail.getCode());
        assertEquals(40, detail.getEnrolledCount());
        assertEquals(2, detail.getTotalSessions());
        assertEquals(1, detail.getPresentCount());
        assertEquals(1, detail.getAbsentCount());
        assertEquals(50, detail.getAttendanceRate());
        assertEquals(1, detail.getMaterials().size());
        assertEquals(1, detail.getAssignments().size());
        assertTrue(detail.getAssignments().get(0).isSubmitted());
        assertEquals(9.5, detail.getAssignments().get(0).getScore());
        assertEquals(2, detail.getAnnouncements().size());
        assertEquals(2, detail.getAttendanceRecords().size());
        assertEquals("PRESENT", detail.getAttendanceRecords().get(0).getStatus());
        assertEquals("ABSENT", detail.getAttendanceRecords().get(1).getStatus());
    }

    @Test
    @DisplayName("getNotifications: Tổng hợp thông báo Điểm, Sắp hết hạn và Tài liệu mới")
    void getNotifications_AggregatesCategories() {
        ClassMember member = ClassMember.builder()
                .id(UUID.randomUUID())
                .courseClass(sampleClass)
                .student(student)
                .build();

        Assignment urgentAssign = Assignment.builder()
                .id(UUID.randomUUID())
                .courseClass(sampleClass)
                .title("Đồ án giữa kỳ")
                .deadline(Instant.now().plus(24, ChronoUnit.HOURS))
                .createdAt(Instant.now().minus(2, ChronoUnit.DAYS))
                .build();

        Submission gradedSub = Submission.builder()
                .id(UUID.randomUUID())
                .assignment(urgentAssign)
                .student(student)
                .score(9.0)
                .feedback("Tốt")
                .submittedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        Material mat = Material.builder()
                .id(UUID.randomUUID())
                .courseClass(sampleClass)
                .title("Đề cương môn học")
                .fileUrl("https://uit.edu.vn/syllabus.pdf")
                .createdAt(Instant.now().minus(5, ChronoUnit.DAYS))
                .build();

        when(classMemberRepository.findByStudentId(studentId)).thenReturn(List.of(member));
        when(assignmentRepository.findByCourseClassIdOrderByDeadlineAsc(classId)).thenReturn(List.of(urgentAssign));
        when(submissionRepository.findByAssignmentIdAndStudentId(urgentAssign.getId(), studentId)).thenReturn(Optional.of(gradedSub));
        when(materialRepository.findByCourseClassIdOrderByCreatedAtDesc(classId)).thenReturn(List.of(mat));

        List<NotificationItemResponse> notifications = classService.getNotifications(studentId, Role.ROLE_STUDENT);

        assertEquals(3, notifications.size());
        assertTrue(notifications.stream().anyMatch(n -> "GRADE".equals(n.getCategory())));
        assertTrue(notifications.stream().anyMatch(n -> "URGENT".equals(n.getCategory())));
        assertTrue(notifications.stream().anyMatch(n -> "GENERAL".equals(n.getCategory())));
    }
}

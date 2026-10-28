package vn.edu.uit.edulens.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.uit.edulens.dto.request.*;
import vn.edu.uit.edulens.dto.response.*;
import vn.edu.uit.edulens.entity.*;
import vn.edu.uit.edulens.entity.enums.AttendanceStatus;
import vn.edu.uit.edulens.entity.enums.ClassStatus;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LecturerFlowServiceTest {

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

    @InjectMocks
    private AssignmentService assignmentService;

    @InjectMocks
    private AttendanceService attendanceService;

    private UUID lecturerId;
    private UUID classId;
    private UUID student1Id;
    private UUID student2Id;
    private User lecturer;
    private User student1;
    private User student2;
    private CourseClass activeClass;

    @BeforeEach
    void setUp() {
        lecturerId = UUID.randomUUID();
        classId = UUID.randomUUID();
        student1Id = UUID.randomUUID();
        student2Id = UUID.randomUUID();

        lecturer = User.builder()
                .id(lecturerId)
                .fullName("TS. Trần Minh Khoa")
                .email("khoatm@uit.edu.vn")
                .role(Role.ROLE_LECTURER)
                .build();

        student1 = User.builder()
                .id(student1Id)
                .fullName("Nguyễn Văn An")
                .email("22520001@gm.uit.edu.vn")
                .role(Role.ROLE_STUDENT)
                .build();

        student2 = User.builder()
                .id(student2Id)
                .fullName("Lê Thị Bình")
                .email("22520045@gm.uit.edu.vn")
                .role(Role.ROLE_STUDENT)
                .build();

        activeClass = CourseClass.builder()
                .id(classId)
                .code("NT118.Q11")
                .name("Phát triển ứng dụng trên thiết bị di động")
                .semester("HK1 2026-2027")
                .room("P.B1.12")
                .inviteCode("K7M-2QX")
                .lecturer(lecturer)
                .status(ClassStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("createClass & archiveClass: Giảng viên tạo lớp mới sinh mã mời 7 ký tự và lưu trữ lớp cuối kỳ")
    void createAndArchiveClass_Success() {
        when(userRepository.findById(lecturerId)).thenReturn(Optional.of(lecturer));
        when(courseClassRepository.existsByInviteCode(anyString())).thenReturn(false);
        when(courseClassRepository.save(any(CourseClass.class))).thenAnswer(inv -> inv.getArgument(0));
        when(classMemberRepository.countByCourseClassId(any())).thenReturn(0L);
        when(attendanceSessionRepository.countByCourseClassIdAndStatus(any(), eq(SessionStatus.CLOSED))).thenReturn(0L);

        CreateClassRequest req = CreateClassRequest.builder()
                .name("Phát triển ứng dụng trên thiết bị di động")
                .code("NT118.Q11")
                .semester("HK1 2026-2027")
                .room("P.B1.12")
                .build();

        CourseClassItem created = classService.createClass(lecturerId, req);

        assertNotNull(created);
        assertEquals("NT118.Q11", created.getCode());
        assertEquals("ACTIVE", created.getStatus());
        assertNotNull(created.getInviteCode());
        assertEquals(7, created.getInviteCode().length());
        assertEquals('-', created.getInviteCode().charAt(3));

        when(courseClassRepository.findById(classId)).thenReturn(Optional.of(activeClass));
        CourseClassItem archived = classService.archiveClass(classId, lecturerId);
        assertEquals("ARCHIVED", archived.getStatus());
        assertEquals(ClassStatus.ARCHIVED, activeClass.getStatus());
    }

    @Test
    @DisplayName("getClassMembers & removeClassMember: Liệt kê thành viên kèm chuyên cần/điểm TB và xóa sinh viên")
    void getClassMembersAndRemove_Success() {
        ClassMember cm1 = ClassMember.builder().id(UUID.randomUUID()).courseClass(activeClass).student(student1).build();
        ClassMember cm2 = ClassMember.builder().id(UUID.randomUUID()).courseClass(activeClass).student(student2).build();

        AttendanceSession s1 = AttendanceSession.builder().id(UUID.randomUUID()).courseClass(activeClass).status(SessionStatus.CLOSED).build();
        AttendanceSession s2 = AttendanceSession.builder().id(UUID.randomUUID()).courseClass(activeClass).status(SessionStatus.CLOSED).build();

        AttendanceRecord r1 = AttendanceRecord.builder().session(s1).student(student1).status(AttendanceStatus.PRESENT).build();
        AttendanceRecord r2 = AttendanceRecord.builder().session(s2).student(student1).status(AttendanceStatus.PRESENT).build();

        Assignment a1 = Assignment.builder()
                .id(UUID.randomUUID())
                .courseClass(activeClass)
                .title("Lab 1")
                .deadline(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        Submission sub1 = Submission.builder()
                .id(UUID.randomUUID())
                .assignment(a1)
                .student(student1)
                .fileUrl("https://uit.edu.vn/lab1.pdf")
                .score(9.0)
                .submittedAt(Instant.now().minus(2, ChronoUnit.DAYS))
                .build();

        when(courseClassRepository.findById(classId)).thenReturn(Optional.of(activeClass));
        when(classMemberRepository.findByCourseClassId(classId)).thenReturn(List.of(cm1, cm2));
        when(attendanceSessionRepository.findByCourseClassIdOrderByCreatedAtDesc(classId)).thenReturn(List.of(s1, s2));
        when(assignmentRepository.findByCourseClassIdOrderByDeadlineAsc(classId)).thenReturn(List.of(a1));

        when(attendanceRecordRepository.findBySessionIdAndStudentId(s1.getId(), student1Id)).thenReturn(Optional.of(r1));
        when(attendanceRecordRepository.findBySessionIdAndStudentId(s2.getId(), student1Id)).thenReturn(Optional.of(r2));
        when(attendanceRecordRepository.findBySessionIdAndStudentId(s1.getId(), student2Id)).thenReturn(Optional.empty());
        when(attendanceRecordRepository.findBySessionIdAndStudentId(s2.getId(), student2Id)).thenReturn(Optional.empty());

        when(submissionRepository.findByAssignmentIdAndStudentId(a1.getId(), student1Id)).thenReturn(Optional.of(sub1));
        when(submissionRepository.findByAssignmentIdAndStudentId(a1.getId(), student2Id)).thenReturn(Optional.empty());

        List<ClassMemberItemResponse> members = classService.getClassMembers(classId);

        assertEquals(2, members.size());
        ClassMemberItemResponse m1 = members.get(0);
        assertEquals("22520001", m1.getStudentCode());
        assertEquals(100, m1.getAttendanceRate());
        assertEquals(0, m1.getAbsentSessions());
        assertEquals(1, m1.getSubmittedAssignments());
        assertEquals(9.0, m1.getAverageScore());

        ClassMemberItemResponse m2 = members.get(1);
        assertEquals("22520045", m2.getStudentCode());
        assertEquals(0, m2.getAttendanceRate());
        assertEquals(2, m2.getAbsentSessions());
        assertEquals(0, m2.getSubmittedAssignments());
        assertNull(m2.getAverageScore());

        when(classMemberRepository.findByCourseClassIdAndStudentId(classId, student2Id)).thenReturn(Optional.of(cm2));
        classService.removeClassMember(classId, student2Id);
        verify(classMemberRepository, times(1)).delete(cm2);
    }

    @Test
    @DisplayName("createMaterial: Tạo tài liệu mới và chặn thao tác khi lớp đã lưu trữ (CLASS_ARCHIVED)")
    void createMaterial_ActiveAndArchivedCheck() {
        when(courseClassRepository.findById(classId)).thenReturn(Optional.of(activeClass));
        when(materialRepository.save(any(Material.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateMaterialRequest req = CreateMaterialRequest.builder()
                .title("Slide Chương 3 - Navigation")
                .fileUrl("content://edulens/materials/ch3_nav.pdf")
                .fileType("PDF")
                .build();

        ClassDetailResponse.MaterialItem item = classService.createMaterial(classId, req);
        assertNotNull(item);
        assertEquals("Slide Chương 3 - Navigation", item.getTitle());
        assertEquals("PDF", item.getFileType());

        activeClass.setStatus(ClassStatus.ARCHIVED);
        AppException ex = assertThrows(AppException.class, () -> classService.createMaterial(classId, req));
        assertEquals(ErrorCode.CLASS_ARCHIVED, ex.getErrorCode());
    }

    @Test
    @DisplayName("getClassStatistics: Tính cảnh báo vắng >20% và phổ điểm 6 cột (0-2, 2-4, 4-6, 6-8, 8-9, 9-10)")
    void getClassStatistics_ComputesWarningAndScoreBuckets() {
        ClassMember cm1 = ClassMember.builder().id(UUID.randomUUID()).courseClass(activeClass).student(student1).build();
        ClassMember cm2 = ClassMember.builder().id(UUID.randomUUID()).courseClass(activeClass).student(student2).build();

        AttendanceSession s1 = AttendanceSession.builder().id(UUID.randomUUID()).courseClass(activeClass).status(SessionStatus.CLOSED).build();
        AttendanceRecord r1 = AttendanceRecord.builder().session(s1).student(student1).status(AttendanceStatus.PRESENT).build();

        Assignment a1 = Assignment.builder().id(UUID.randomUUID()).courseClass(activeClass).title("Lab 1").build();
        Submission sub1 = Submission.builder().id(UUID.randomUUID()).assignment(a1).student(student1).score(9.5).build();
        Submission sub2 = Submission.builder().id(UUID.randomUUID()).assignment(a1).student(student2).score(7.5).build();

        when(courseClassRepository.findById(classId)).thenReturn(Optional.of(activeClass));
        when(classMemberRepository.findByCourseClassId(classId)).thenReturn(List.of(cm1, cm2));
        when(attendanceSessionRepository.findByCourseClassIdOrderByCreatedAtDesc(classId)).thenReturn(List.of(s1));
        when(assignmentRepository.findByCourseClassIdOrderByDeadlineAsc(classId)).thenReturn(List.of(a1));

        when(attendanceRecordRepository.findBySessionIdAndStudentId(s1.getId(), student1Id)).thenReturn(Optional.of(r1));
        when(attendanceRecordRepository.findBySessionIdAndStudentId(s1.getId(), student2Id)).thenReturn(Optional.empty());

        when(submissionRepository.findByAssignmentIdAndStudentId(a1.getId(), student1Id)).thenReturn(Optional.of(sub1));
        when(submissionRepository.findByAssignmentIdAndStudentId(a1.getId(), student2Id)).thenReturn(Optional.of(sub2));

        ClassStatisticsResponse stats = classService.getClassStatistics(classId);

        assertEquals(2, stats.getEnrolledCount());
        assertEquals(50, stats.getAttendanceRate());
        assertEquals(1, stats.getWarningAbsentCount());
        assertEquals("Lê Thị Bình", stats.getWarningStudents().get(0).getFullName());
        assertEquals(8.5, stats.getAverageScore(), 0.001);
        assertEquals(6, stats.getScoreDistribution().size());
        assertEquals("6-8", stats.getScoreDistribution().get(3).getLabel());
        assertEquals(1, stats.getScoreDistribution().get(3).getCount());
        assertEquals("9-10", stats.getScoreDistribution().get(5).getLabel());
        assertEquals(1, stats.getScoreDistribution().get(5).getCount());
    }

    @Test
    @DisplayName("createAssignment, getAssignmentSubmissions & gradeSubmission: Tạo bài tập, thống kê nộp đúng hạn/trễ và chấm điểm")
    void assignmentCreateAndGradeFlow_Success() {
        when(courseClassRepository.findById(classId)).thenReturn(Optional.of(activeClass));
        when(assignmentRepository.save(any(Assignment.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateAssignmentRequest createReq = CreateAssignmentRequest.builder()
                .title("Bài tập 2: Thiết kế UI")
                .description("Thiết kế Figma & XML")
                .deadline("2026-11-15T23:59:00Z")
                .build();

        ClassDetailResponse.ClassAssignmentItem createdAssign = assignmentService.createAssignment(classId, createReq);
        assertNotNull(createdAssign);
        assertEquals("Bài tập 2: Thiết kế UI", createdAssign.getTitle());

        UUID assignmentId = createdAssign.getId();
        Instant deadline = Instant.parse("2026-11-15T23:59:00Z");
        Assignment assignmentEntity = Assignment.builder()
                .id(assignmentId)
                .courseClass(activeClass)
                .title("Bài tập 2: Thiết kế UI")
                .deadline(deadline)
                .build();

        ClassMember cm1 = ClassMember.builder().courseClass(activeClass).student(student1).build();
        ClassMember cm2 = ClassMember.builder().courseClass(activeClass).student(student2).build();

        Submission onTimeSub = Submission.builder()
                .id(UUID.randomUUID())
                .assignment(assignmentEntity)
                .student(student1)
                .fileUrl("content://edulens/sub1.zip")
                .submittedAt(deadline.minus(2, ChronoUnit.HOURS))
                .build();

        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignmentEntity));
        when(classMemberRepository.findByCourseClassId(classId)).thenReturn(List.of(cm1, cm2));
        when(submissionRepository.findByAssignmentId(assignmentId)).thenReturn(List.of(onTimeSub));
        when(submissionRepository.findByAssignmentIdAndStudentId(assignmentId, student2Id)).thenReturn(Optional.empty());

        AssignmentSubmissionListResponse listBefore = assignmentService.getAssignmentSubmissions(assignmentId);
        assertEquals(2, listBefore.getTotalStudents());
        assertEquals(1, listBefore.getSubmittedCount());
        assertEquals(1, listBefore.getOnTimeCount());
        assertEquals(0, listBefore.getLateCount());
        assertEquals(1, listBefore.getNotSubmittedCount());
        assertEquals(0, listBefore.getGradedCount());

        when(submissionRepository.findByAssignmentIdAndStudentId(assignmentId, student1Id)).thenReturn(Optional.of(onTimeSub));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        GradeSubmissionRequest gradeReq = GradeSubmissionRequest.builder()
                .score(9.5)
                .feedback("Thiết kế xuất sắc, đúng chuẩn Material 3")
                .publishAll(true)
                .build();

        AssignmentSubmissionListResponse listAfter = assignmentService.gradeSubmission(assignmentId, student1Id, gradeReq);
        assertEquals(1, listAfter.getGradedCount());
        assertEquals(9.5, listAfter.getSubmissions().get(0).getScore());
        assertEquals("Thiết kế xuất sắc, đúng chuẩn Material 3", listAfter.getSubmissions().get(0).getFeedback());
    }

    @Test
    @DisplayName("openSession, getLiveSession & closeSession: Mở phiên QR, theo dõi trực tiếp và chốt phiên")
    void qrAttendanceSessionLifecycle_Success() {
        when(courseClassRepository.findById(classId)).thenReturn(Optional.of(activeClass));
        when(attendanceSessionRepository.findFirstByCourseClassIdAndStatus(classId, SessionStatus.OPEN))
                .thenReturn(Optional.empty());
        when(attendanceSessionRepository.save(any(AttendanceSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(classMemberRepository.countByCourseClassId(classId)).thenReturn(38L);
        when(attendanceRecordRepository.findBySessionId(any())).thenReturn(List.of());

        OpenQrSessionRequest openReq = OpenQrSessionRequest.builder()
                .wifiBssid("00:11:22:33:44:55")
                .latitude(10.8700)
                .longitude(106.8031)
                .radiusMeters(50.0)
                .build();

        LiveAttendanceSessionResponse opened = attendanceService.openSession(classId, openReq);
        assertNotNull(opened.getSessionId());
        assertEquals("OPEN", opened.getStatus());
        assertEquals(38, opened.getTotalStudents());
        assertEquals(0, opened.getPresentCount());
        assertEquals(38, opened.getAbsentCount());

        UUID sessionId = opened.getSessionId();
        AttendanceSession sessionEntity = AttendanceSession.builder()
                .id(sessionId)
                .courseClass(activeClass)
                .wifiBssid("00:11:22:33:44:55")
                .latitude(10.8700)
                .longitude(106.8031)
                .radiusMeters(50.0)
                .status(SessionStatus.OPEN)
                .createdAt(Instant.now())
                .build();

        AttendanceRecord rec1 = AttendanceRecord.builder()
                .id(UUID.randomUUID())
                .session(sessionEntity)
                .student(student1)
                .deviceId("ANDROID-UIT-01")
                .status(AttendanceStatus.PRESENT)
                .scannedAt(Instant.now())
                .build();

        when(attendanceSessionRepository.findById(sessionId)).thenReturn(Optional.of(sessionEntity));
        when(attendanceRecordRepository.findBySessionId(sessionId)).thenReturn(List.of(rec1));

        LiveAttendanceSessionResponse live = attendanceService.getLiveSession(sessionId);
        assertEquals(1, live.getPresentCount());
        assertEquals(37, live.getAbsentCount());
        assertEquals("Nguyễn Văn An", live.getAttendees().get(0).getStudentName());

        LiveAttendanceSessionResponse closed = attendanceService.closeSession(sessionId);
        assertEquals("CLOSED", closed.getStatus());
        assertNotNull(sessionEntity.getClosedAt());
    }
}

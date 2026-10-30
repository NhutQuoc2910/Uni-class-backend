package vn.edu.uit.edulens.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.uit.edulens.dto.request.AttendanceCheckInRequest;
import vn.edu.uit.edulens.dto.request.SubmitAssignmentRequest;
import vn.edu.uit.edulens.dto.response.AssignmentDetailResponse;
import vn.edu.uit.edulens.dto.response.AttendanceCheckInResponse;
import vn.edu.uit.edulens.dto.response.ClassGradesResponse;
import vn.edu.uit.edulens.entity.*;
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
class AssignmentAndAttendanceServiceTest {

    @Mock
    private AssignmentRepository assignmentRepository;
    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AttendanceSessionRepository attendanceSessionRepository;
    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;
    @Mock
    private ClassMemberRepository classMemberRepository;
    @Mock
    private CourseClassRepository courseClassRepository;
    @Mock
    private MaterialRepository materialRepository;

    @InjectMocks
    private AssignmentService assignmentService;

    @InjectMocks
    private AttendanceService attendanceService;

    @InjectMocks
    private ClassService classService;

    private UUID studentId;
    private UUID otherStudentId;
    private UUID classId;
    private UUID assignmentId;
    private UUID sessionId;
    private User lecturer;
    private User student;
    private User otherStudent;
    private CourseClass sampleClass;
    private Assignment sampleAssignment;
    private AttendanceSession openSession;

    @BeforeEach
    void setUp() {
        studentId = UUID.randomUUID();
        otherStudentId = UUID.randomUUID();
        classId = UUID.randomUUID();
        assignmentId = UUID.randomUUID();
        sessionId = UUID.randomUUID();

        lecturer = User.builder()
                .id(UUID.randomUUID())
                .fullName("ThS. Trần Minh Khoa")
                .role(Role.ROLE_LECTURER)
                .build();

        student = User.builder()
                .id(studentId)
                .fullName("Nguyễn Văn An")
                .role(Role.ROLE_STUDENT)
                .build();

        otherStudent = User.builder()
                .id(otherStudentId)
                .fullName("Trần Thị Bình")
                .role(Role.ROLE_STUDENT)
                .build();

        sampleClass = CourseClass.builder()
                .id(classId)
                .code("NT118.Q11")
                .name("Phát triển ứng dụng trên thiết bị di động")
                .semester("HK1 2026-2027")
                .room("B1.12")
                .lecturer(lecturer)
                .build();

        sampleAssignment = Assignment.builder()
                .id(assignmentId)
                .courseClass(sampleClass)
                .title("Bài tập 2: Thiết kế giao diện & Cấu trúc Dữ liệu")
                .description("Nộp source code zip hoặc file PDF thiết kế màn hình.")
                .deadline(Instant.now().plus(2, ChronoUnit.DAYS))
                .attachmentUrl("https://uit.edu.vn/spec.pdf")
                .build();

        openSession = AttendanceSession.builder()
                .id(sessionId)
                .courseClass(sampleClass)
                .wifiBssid("00:11:22:33:44:55")
                .latitude(10.8700)
                .longitude(106.8031)
                .radiusMeters(50.0)
                .status(SessionStatus.OPEN)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("getAssignmentDetail & submitAssignment: Tạo bài nộp mới và cập nhật khi nộp lại")
    void assignmentDetailAndSubmit_NewAndResubmit_Success() {
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(sampleAssignment));
        when(submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId)).thenReturn(Optional.empty());

        AssignmentDetailResponse initialDetail = assignmentService.getAssignmentDetail(assignmentId, studentId);
        assertFalse(initialDetail.isSubmitted());
        assertEquals("NT118.Q11", initialDetail.getClassCode());
        assertEquals("ThS. Trần Minh Khoa", initialDetail.getLecturerName());

        // 1. New submission
        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        SubmitAssignmentRequest firstReq = new SubmitAssignmentRequest("content://edulens/submissions/report_v1.pdf", "report_v1.pdf");
        AssignmentDetailResponse submittedDetail = assignmentService.submitAssignment(assignmentId, studentId, firstReq);

        assertTrue(submittedDetail.isSubmitted());
        assertEquals("content://edulens/submissions/report_v1.pdf", submittedDetail.getSubmittedFileUrl());
        assertNotNull(submittedDetail.getSubmittedAt());

        // 2. Resubmission update
        Submission existingSub = Submission.builder()
                .id(UUID.randomUUID())
                .assignment(sampleAssignment)
                .student(student)
                .fileUrl("content://edulens/submissions/report_v1.pdf")
                .submittedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();
        when(submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId))
                .thenReturn(Optional.of(existingSub));

        SubmitAssignmentRequest resubmitReq = new SubmitAssignmentRequest("content://edulens/submissions/report_v2_final.zip", "report_v2_final.zip");
        AssignmentDetailResponse resubmittedDetail = assignmentService.submitAssignment(assignmentId, studentId, resubmitReq);

        assertTrue(resubmittedDetail.isSubmitted());
        assertEquals("content://edulens/submissions/report_v2_final.zip", resubmittedDetail.getSubmittedFileUrl());
        verify(submissionRepository, times(2)).save(any(Submission.class));
    }

    @Test
    @DisplayName("checkIn: Điểm danh 3 lớp thành công (QR OPEN + WiFi BSSID khớp + GPS trong bán kính + Device hợp lệ)")
    void checkIn_ThreeLayerSuccess() {
        when(attendanceSessionRepository.findById(sessionId)).thenReturn(Optional.of(openSession));
        when(attendanceRecordRepository.findBySessionIdAndDeviceId(sessionId, "ANDROID-DEV-001")).thenReturn(Optional.empty());
        when(attendanceRecordRepository.findBySessionIdAndStudentId(sessionId, studentId)).thenReturn(Optional.empty());
        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(attendanceRecordRepository.save(any(AttendanceRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        AttendanceCheckInRequest request = AttendanceCheckInRequest.builder()
                .sessionId(sessionId.toString())
                .wifiBssid("00:11:22:33:44:55")
                .latitude(10.8701)
                .longitude(106.8032)
                .deviceId("ANDROID-DEV-001")
                .build();

        AttendanceCheckInResponse response = attendanceService.checkIn(studentId, request);

        assertNotNull(response);
        assertEquals(sessionId, response.getSessionId());
        assertEquals("NT118.Q11", response.getClassCode());
        assertEquals("B1.12", response.getRoom());
        assertEquals("PRESENT", response.getStatus());
        assertTrue(response.isQrVerified());
        assertTrue(response.isWifiVerified());
        assertTrue(response.isGpsVerified());
        assertTrue(response.getDistanceMeters() <= 50.0);
        verify(attendanceRecordRepository, times(1)).save(any(AttendanceRecord.class));
    }

    @Test
    @DisplayName("checkIn: Ném lỗi QR_EXPIRED khi phiên điểm danh đã đóng")
    void checkIn_ClosedSession_ThrowsQrExpired() {
        openSession.setStatus(SessionStatus.CLOSED);
        when(attendanceSessionRepository.findById(sessionId)).thenReturn(Optional.of(openSession));

        AttendanceCheckInRequest request = AttendanceCheckInRequest.builder()
                .sessionId(sessionId.toString())
                .wifiBssid("00:11:22:33:44:55")
                .latitude(10.8700)
                .longitude(106.8031)
                .deviceId("ANDROID-DEV-001")
                .build();

        AppException ex = assertThrows(AppException.class, () -> attendanceService.checkIn(studentId, request));
        assertEquals(ErrorCode.QR_EXPIRED, ex.getErrorCode());
    }

    @Test
    @DisplayName("checkIn: Ném lỗi WIFI_MISMATCH khi BSSID không khớp phòng học")
    void checkIn_WrongWifiBssid_ThrowsWifiMismatch() {
        when(attendanceSessionRepository.findById(sessionId)).thenReturn(Optional.of(openSession));

        AttendanceCheckInRequest request = AttendanceCheckInRequest.builder()
                .sessionId(sessionId.toString())
                .wifiBssid("AA:BB:CC:DD:EE:FF")
                .latitude(10.8700)
                .longitude(106.8031)
                .deviceId("ANDROID-DEV-001")
                .build();

        AppException ex = assertThrows(AppException.class, () -> attendanceService.checkIn(studentId, request));
        assertEquals(ErrorCode.WIFI_MISMATCH, ex.getErrorCode());
    }

    @Test
    @DisplayName("checkIn: Ném lỗi GPS_OUT_OF_RANGE khi khoảng cách vượt quá bán kính 50m")
    void checkIn_GpsTooFar_ThrowsGpsOutOfRange() {
        when(attendanceSessionRepository.findById(sessionId)).thenReturn(Optional.of(openSession));

        AttendanceCheckInRequest request = AttendanceCheckInRequest.builder()
                .sessionId(sessionId.toString())
                .wifiBssid("00:11:22:33:44:55")
                .latitude(10.8800) // ~1.1km away
                .longitude(106.8100)
                .deviceId("ANDROID-DEV-001")
                .build();

        AppException ex = assertThrows(AppException.class, () -> attendanceService.checkIn(studentId, request));
        assertEquals(ErrorCode.GPS_OUT_OF_RANGE, ex.getErrorCode());
    }

    @Test
    @DisplayName("checkIn: Ném lỗi DEVICE_ALREADY_USED khi thiết bị đã điểm danh hộ tài khoản khác")
    void checkIn_DeviceUsedByAnotherStudent_ThrowsDeviceAlreadyUsed() {
        when(attendanceSessionRepository.findById(sessionId)).thenReturn(Optional.of(openSession));

        AttendanceRecord otherStudentRecord = AttendanceRecord.builder()
                .id(UUID.randomUUID())
                .session(openSession)
                .student(otherStudent)
                .deviceId("SHARED-DEVICE-999")
                .build();
        when(attendanceRecordRepository.findBySessionIdAndDeviceId(sessionId, "SHARED-DEVICE-999"))
                .thenReturn(Optional.of(otherStudentRecord));

        AttendanceCheckInRequest request = AttendanceCheckInRequest.builder()
                .sessionId(sessionId.toString())
                .wifiBssid("00:11:22:33:44:55")
                .latitude(10.8700)
                .longitude(106.8031)
                .deviceId("SHARED-DEVICE-999")
                .build();

        AppException ex = assertThrows(AppException.class, () -> attendanceService.checkIn(studentId, request));
        assertEquals(ErrorCode.DEVICE_ALREADY_USED, ex.getErrorCode());
    }

    @Test
    @DisplayName("checkIn: Payload rotating QR hợp lệ UNICLASS:<classCode>:<sessionId>:<timestampMs> điểm danh thành công")
    void checkIn_RotatingQr_ValidTimestamp_Success() {
        when(attendanceSessionRepository.findById(sessionId)).thenReturn(Optional.of(openSession));
        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(attendanceRecordRepository.findBySessionIdAndDeviceId(sessionId, "ANDROID-DEV-001"))
                .thenReturn(Optional.empty());
        when(attendanceRecordRepository.findBySessionIdAndStudentId(sessionId, studentId))
                .thenReturn(Optional.empty());
        when(attendanceRecordRepository.save(any(AttendanceRecord.class))).thenAnswer(i -> i.getArgument(0));

        long currentTimestamp = System.currentTimeMillis() - 5000; // 5 seconds ago
        String rotatingQrPayload = "UNICLASS:NT118.Q11:" + sessionId + ":" + currentTimestamp;

        AttendanceCheckInRequest request = AttendanceCheckInRequest.builder()
                .sessionId(rotatingQrPayload)
                .wifiBssid("00:11:22:33:44:55")
                .latitude(10.8701)
                .longitude(106.8032)
                .deviceId("ANDROID-DEV-001")
                .build();

        AttendanceCheckInResponse response = attendanceService.checkIn(studentId, request);

        assertNotNull(response);
        assertEquals(sessionId, response.getSessionId());
        assertTrue(response.isQrVerified());
    }

    @Test
    @DisplayName("checkIn: Payload rotating QR đã hết hạn (>60s) ném lỗi QR_EXPIRED")
    void checkIn_RotatingQr_ExpiredTimestamp_ThrowsQrExpired() {
        long expiredTimestamp = System.currentTimeMillis() - 65000; // 65 seconds ago (> 60s)
        String expiredPayload = "UNICLASS:NT118.Q11:" + sessionId + ":" + expiredTimestamp;

        AttendanceCheckInRequest request = AttendanceCheckInRequest.builder()
                .sessionId(expiredPayload)
                .wifiBssid("00:11:22:33:44:55")
                .latitude(10.8700)
                .longitude(106.8031)
                .deviceId("ANDROID-DEV-001")
                .build();

        AppException ex = assertThrows(AppException.class, () -> attendanceService.checkIn(studentId, request));
        assertEquals(ErrorCode.QR_EXPIRED, ex.getErrorCode());
    }

    @Test
    @DisplayName("checkIn: Payload rotating QR thời gian tương lai (>10s) ném lỗi QR_EXPIRED")
    void checkIn_RotatingQr_FutureSkew_ThrowsQrExpired() {
        long futureTimestamp = System.currentTimeMillis() + 15000; // 15 seconds in future (> 10s)
        String futurePayload = "UNICLASS:NT118.Q11:" + sessionId + ":" + futureTimestamp;

        AttendanceCheckInRequest request = AttendanceCheckInRequest.builder()
                .sessionId(futurePayload)
                .wifiBssid("00:11:22:33:44:55")
                .latitude(10.8700)
                .longitude(106.8031)
                .deviceId("ANDROID-DEV-001")
                .build();

        AppException ex = assertThrows(AppException.class, () -> attendanceService.checkIn(studentId, request));
        assertEquals(ErrorCode.QR_EXPIRED, ex.getErrorCode());
    }

    @Test
    @DisplayName("checkIn: Payload static QR UNICLASS:<classCode>:<sessionId> điểm danh thành công")
    void checkIn_StaticStructuredQr_Success() {
        when(attendanceSessionRepository.findById(sessionId)).thenReturn(Optional.of(openSession));
        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(attendanceRecordRepository.findBySessionIdAndDeviceId(sessionId, "ANDROID-DEV-001"))
                .thenReturn(Optional.empty());
        when(attendanceRecordRepository.findBySessionIdAndStudentId(sessionId, studentId))
                .thenReturn(Optional.empty());
        when(attendanceRecordRepository.save(any(AttendanceRecord.class))).thenAnswer(i -> i.getArgument(0));

        String staticPayload = "UNICLASS:NT118.Q11:" + sessionId;

        AttendanceCheckInRequest request = AttendanceCheckInRequest.builder()
                .sessionId(staticPayload)
                .wifiBssid("00:11:22:33:44:55")
                .latitude(10.8700)
                .longitude(106.8031)
                .deviceId("ANDROID-DEV-001")
                .build();

        AttendanceCheckInResponse response = attendanceService.checkIn(studentId, request);
        assertNotNull(response);
        assertEquals(sessionId, response.getSessionId());
    }

    @Test
    @DisplayName("checkIn: Demo bypass bị tắt (allowDemoBypass=false) ném SESSION_NOT_FOUND với DEMO_OPEN")
    void checkIn_DemoBypassDisabled_ThrowsSessionNotFound() {
        attendanceService.setAllowDemoBypass(false);

        AttendanceCheckInRequest request = AttendanceCheckInRequest.builder()
                .sessionId("DEMO_OPEN")
                .wifiBssid("00:11:22:33:44:55")
                .latitude(10.8700)
                .longitude(106.8031)
                .deviceId("ANDROID-DEV-001")
                .build();

        AppException ex = assertThrows(AppException.class, () -> attendanceService.checkIn(studentId, request));
        assertEquals(ErrorCode.SESSION_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("checkIn: Demo bypass được bật (allowDemoBypass=true) thành công với DEMO_OPEN")
    void checkIn_DemoBypassEnabled_Success() {
        attendanceService.setAllowDemoBypass(true);
        when(attendanceSessionRepository.findFirstByStatusOrderByCreatedAtDesc(SessionStatus.OPEN))
                .thenReturn(Optional.of(openSession));
        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(attendanceRecordRepository.findBySessionIdAndDeviceId(sessionId, "ANDROID-DEV-001"))
                .thenReturn(Optional.empty());
        when(attendanceRecordRepository.findBySessionIdAndStudentId(sessionId, studentId))
                .thenReturn(Optional.empty());
        when(attendanceRecordRepository.save(any(AttendanceRecord.class))).thenAnswer(i -> i.getArgument(0));

        AttendanceCheckInRequest request = AttendanceCheckInRequest.builder()
                .sessionId("DEMO_OPEN")
                .wifiBssid("00:11:22:33:44:55")
                .latitude(10.8700)
                .longitude(106.8031)
                .deviceId("ANDROID-DEV-001")
                .build();

        AttendanceCheckInResponse response = attendanceService.checkIn(studentId, request);
        assertNotNull(response);
        assertEquals(sessionId, response.getSessionId());
    }

    @Test
    @DisplayName("getClassGrades: Tính điểm trung bình chính xác và ánh xạ nhận xét của giảng viên")
    void getClassGrades_ComputesAverageAndMapsFeedback() {
        Assignment a1 = Assignment.builder()
                .id(UUID.randomUUID())
                .courseClass(sampleClass)
                .title("Bài tập 1: Khởi tạo dự án")
                .deadline(Instant.now().minus(5, ChronoUnit.DAYS))
                .build();

        Assignment a2 = Assignment.builder()
                .id(UUID.randomUUID())
                .courseClass(sampleClass)
                .title("Bài tập 2: Thiết kế UI")
                .deadline(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        Assignment a3 = Assignment.builder()
                .id(UUID.randomUUID())
                .courseClass(sampleClass)
                .title("Bài tập 3: Báo cáo giữa kỳ")
                .deadline(Instant.now().plus(5, ChronoUnit.DAYS))
                .build();

        Submission sub1 = Submission.builder()
                .id(UUID.randomUUID())
                .assignment(a1)
                .student(student)
                .fileUrl("https://uit.edu.vn/sub1.pdf")
                .score(8.5)
                .feedback("Bài làm tốt, cấu trúc rõ ràng.")
                .submittedAt(Instant.now().minus(6, ChronoUnit.DAYS))
                .build();

        Submission sub2 = Submission.builder()
                .id(UUID.randomUUID())
                .assignment(a2)
                .student(student)
                .fileUrl("https://uit.edu.vn/sub2.pdf")
                .score(9.0)
                .feedback("Giao diện chuẩn Material 3.")
                .submittedAt(Instant.now().minus(2, ChronoUnit.DAYS))
                .build();

        when(courseClassRepository.findById(classId)).thenReturn(Optional.of(sampleClass));
        when(assignmentRepository.findByCourseClassIdOrderByDeadlineAsc(classId)).thenReturn(List.of(a1, a2, a3));
        when(submissionRepository.findByAssignmentIdAndStudentId(a1.getId(), studentId)).thenReturn(Optional.of(sub1));
        when(submissionRepository.findByAssignmentIdAndStudentId(a2.getId(), studentId)).thenReturn(Optional.of(sub2));
        when(submissionRepository.findByAssignmentIdAndStudentId(a3.getId(), studentId)).thenReturn(Optional.empty());

        ClassGradesResponse gradesResponse = classService.getClassGrades(classId, studentId);

        assertEquals(classId, gradesResponse.getClassId());
        assertEquals("NT118.Q11", gradesResponse.getClassCode());
        assertEquals("ThS. Trần Minh Khoa", gradesResponse.getLecturerName());
        assertEquals(3, gradesResponse.getTotalAssignments());
        assertEquals(2, gradesResponse.getGradedCount());
        assertEquals(8.8, gradesResponse.getAverageScore(), 0.001);
        assertEquals(3, gradesResponse.getGrades().size());
        assertEquals("Bài làm tốt, cấu trúc rõ ràng.", gradesResponse.getGrades().get(0).getFeedback());
        assertEquals("K", gradesResponse.getGrades().get(0).getLecturerInitial());
        assertFalse(gradesResponse.getGrades().get(2).isSubmitted());
    }
}

package vn.edu.uit.edulens.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.uit.edulens.config.SecurityConfig;
import vn.edu.uit.edulens.dto.request.CreateAssignmentRequest;
import vn.edu.uit.edulens.dto.request.CreateClassRequest;
import vn.edu.uit.edulens.dto.request.CreateMaterialRequest;
import vn.edu.uit.edulens.dto.request.GradeSubmissionRequest;
import vn.edu.uit.edulens.dto.request.OpenQrSessionRequest;
import vn.edu.uit.edulens.dto.request.ResetPasswordRequest;
import vn.edu.uit.edulens.dto.request.VerifyOtpRequest;
import vn.edu.uit.edulens.dto.response.AssignmentSubmissionListResponse;
import vn.edu.uit.edulens.dto.response.ClassDetailResponse;
import vn.edu.uit.edulens.dto.response.ClassMemberItemResponse;
import vn.edu.uit.edulens.dto.response.ClassStatisticsResponse;
import vn.edu.uit.edulens.dto.response.CourseClassItem;
import vn.edu.uit.edulens.dto.response.LiveAttendanceSessionResponse;
import vn.edu.uit.edulens.entity.User;
import vn.edu.uit.edulens.entity.enums.Role;
import vn.edu.uit.edulens.entity.enums.UserStatus;
import vn.edu.uit.edulens.security.CustomUserDetails;
import vn.edu.uit.edulens.security.CustomUserDetailsService;
import vn.edu.uit.edulens.security.JwtAuthenticationFilter;
import vn.edu.uit.edulens.security.JwtTokenProvider;
import vn.edu.uit.edulens.service.AssignmentService;
import vn.edu.uit.edulens.service.AttendanceService;
import vn.edu.uit.edulens.service.ClassService;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ClassController.class, AssignmentController.class, AttendanceController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@ActiveProfiles("test")
public class AuthContractAndRbacSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtTokenProvider tokenProvider;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @MockBean
    private ClassService classService;

    @MockBean
    private AssignmentService assignmentService;

    @MockBean
    private AttendanceService attendanceService;

    private CustomUserDetails studentDetails;
    private CustomUserDetails lecturerDetails;

    private final UUID classId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        User student = User.builder()
                .id(studentId)
                .email("student@uit.edu.vn")
                .passwordHash("hashed")
                .fullName("Nguyen Van Sinh Vien")
                .role(Role.ROLE_STUDENT)
                .status(UserStatus.ACTIVE)
                .build();
        studentDetails = new CustomUserDetails(student);

        User lecturer = User.builder()
                .id(UUID.randomUUID())
                .email("lecturer@uit.edu.vn")
                .passwordHash("hashed")
                .fullName("Tran Van Giang Vien")
                .role(Role.ROLE_LECTURER)
                .status(UserStatus.ACTIVE)
                .build();
        lecturerDetails = new CustomUserDetails(lecturer);
    }

    @Nested
    @DisplayName("1. OTP DTO Contract JSON Alias Deserialization Tests")
    class OtpDtoContractTests {

        @Test
        @DisplayName("VerifyOtpRequest deserializes with 'otp' alias")
        void testVerifyOtpRequestWithOtpAlias() throws Exception {
            String json = "{\"email\": \"student@uit.edu.vn\", \"otp\": \"123456\"}";
            VerifyOtpRequest request = objectMapper.readValue(json, VerifyOtpRequest.class);

            assertThat(request.getEmail()).isEqualTo("student@uit.edu.vn");
            assertThat(request.getOtpCode()).isEqualTo("123456");
        }

        @Test
        @DisplayName("VerifyOtpRequest deserializes with standard 'otpCode'")
        void testVerifyOtpRequestWithOtpCodeField() throws Exception {
            String json = "{\"email\": \"student@uit.edu.vn\", \"otpCode\": \"654321\"}";
            VerifyOtpRequest request = objectMapper.readValue(json, VerifyOtpRequest.class);

            assertThat(request.getEmail()).isEqualTo("student@uit.edu.vn");
            assertThat(request.getOtpCode()).isEqualTo("654321");
        }

        @Test
        @DisplayName("ResetPasswordRequest deserializes with 'otp' alias")
        void testResetPasswordRequestWithOtpAlias() throws Exception {
            String json = "{\"email\": \"student@uit.edu.vn\", \"otp\": \"123456\", \"newPassword\": \"newSecretPassword\"}";
            ResetPasswordRequest request = objectMapper.readValue(json, ResetPasswordRequest.class);

            assertThat(request.getEmail()).isEqualTo("student@uit.edu.vn");
            assertThat(request.getOtpCode()).isEqualTo("123456");
            assertThat(request.getNewPassword()).isEqualTo("newSecretPassword");
        }

        @Test
        @DisplayName("ResetPasswordRequest deserializes with standard 'otpCode'")
        void testResetPasswordRequestWithOtpCodeField() throws Exception {
            String json = "{\"email\": \"student@uit.edu.vn\", \"otpCode\": \"654321\", \"newPassword\": \"newSecretPassword\"}";
            ResetPasswordRequest request = objectMapper.readValue(json, ResetPasswordRequest.class);

            assertThat(request.getEmail()).isEqualTo("student@uit.edu.vn");
            assertThat(request.getOtpCode()).isEqualTo("654321");
            assertThat(request.getNewPassword()).isEqualTo("newSecretPassword");
        }
    }

    @Nested
    @DisplayName("2. Method-Level RBAC Security Tests (ClassController)")
    class ClassControllerRbacTests {

        @Test
        @DisplayName("POST /api/v1/classes - Student 403 Forbidden, Lecturer 200 OK")
        void testCreateClassRbac() throws Exception {
            CreateClassRequest req = CreateClassRequest.builder()
                    .name("Lập trình mạng")
                    .code("NT118")
                    .semester("HK1-2026")
                    .room("A101")
                    .build();

            when(classService.createClass(any(), any()))
                    .thenReturn(new CourseClassItem(classId, "NT118", "Lập trình mạng", "HK1-2026", 0, 0));

            // Student -> 403
            mockMvc.perform(post("/api/v1/classes")
                            .with(user(studentDetails))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(40300));

            // Lecturer -> 200
            mockMvc.perform(post("/api/v1/classes")
                            .with(user(lecturerDetails))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("PUT /api/v1/classes/{classId}/archive - Student 403 Forbidden, Lecturer 200 OK")
        void testArchiveClassRbac() throws Exception {
            when(classService.archiveClass(eq(classId), any()))
                    .thenReturn(new CourseClassItem(classId, "NT118", "Lập trình mạng", "HK1-2026", null, null, null, "ARCHIVED", 0, 0));

            // Student -> 403
            mockMvc.perform(put("/api/v1/classes/{classId}/archive", classId)
                            .with(user(studentDetails)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(40300));

            // Lecturer -> 200
            mockMvc.perform(put("/api/v1/classes/{classId}/archive", classId)
                            .with(user(lecturerDetails)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("GET /api/v1/classes/{classId}/members - Student 403 Forbidden, Lecturer 200 OK")
        void testGetClassMembersRbac() throws Exception {
            when(classService.getClassMembers(eq(classId))).thenReturn(Collections.emptyList());

            // Student -> 403
            mockMvc.perform(get("/api/v1/classes/{classId}/members", classId)
                            .with(user(studentDetails)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(40300));

            // Lecturer -> 200
            mockMvc.perform(get("/api/v1/classes/{classId}/members", classId)
                            .with(user(lecturerDetails)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("DELETE /api/v1/classes/{classId}/members/{studentId} - Student 403 Forbidden, Lecturer 200 OK")
        void testRemoveClassMemberRbac() throws Exception {
            // Student -> 403
            mockMvc.perform(delete("/api/v1/classes/{classId}/members/{studentId}", classId, studentId)
                            .with(user(studentDetails)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(40300));

            // Lecturer -> 200
            mockMvc.perform(delete("/api/v1/classes/{classId}/members/{studentId}", classId, studentId)
                            .with(user(lecturerDetails)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("POST /api/v1/classes/{classId}/materials - Student 403 Forbidden, Lecturer 200 OK")
        void testCreateMaterialRbac() throws Exception {
            CreateMaterialRequest req = CreateMaterialRequest.builder()
                    .title("Slide Bài Giảng Chương 1")
                    .fileUrl("https://storage.uit.edu.vn/materials/slide1.pdf")
                    .fileType("pdf")
                    .build();

            when(classService.createMaterial(eq(classId), any()))
                    .thenReturn(new ClassDetailResponse.MaterialItem(UUID.randomUUID(), req.getTitle(), req.getFileUrl(), req.getFileType(), "2026-10-31"));

            // Student -> 403
            mockMvc.perform(post("/api/v1/classes/{classId}/materials", classId)
                            .with(user(studentDetails))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(40300));

            // Lecturer -> 200
            mockMvc.perform(post("/api/v1/classes/{classId}/materials", classId)
                            .with(user(lecturerDetails))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("POST /api/v1/classes/{classId}/assignments - Student 403 Forbidden, Lecturer 200 OK")
        void testCreateAssignmentRbac() throws Exception {
            CreateAssignmentRequest req = CreateAssignmentRequest.builder()
                    .title("Bài tập tuần 1")
                    .deadline("2026-11-15T23:59:59")
                    .build();

            when(assignmentService.createAssignment(eq(classId), any()))
                    .thenReturn(new ClassDetailResponse.ClassAssignmentItem(assignmentId, req.getTitle(), "desc", req.getDeadline(), null, false, null, null));

            // Student -> 403
            mockMvc.perform(post("/api/v1/classes/{classId}/assignments", classId)
                            .with(user(studentDetails))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(40300));

            // Lecturer -> 200
            mockMvc.perform(post("/api/v1/classes/{classId}/assignments", classId)
                            .with(user(lecturerDetails))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("POST /api/v1/classes/{classId}/attendance-sessions - Student 403 Forbidden, Lecturer 200 OK")
        void testOpenAttendanceSessionRbac() throws Exception {
            OpenQrSessionRequest req = OpenQrSessionRequest.builder()
                    .wifiBssid("00:11:22:33:44:55")
                    .build();

            when(attendanceService.openSession(eq(classId), any()))
                    .thenReturn(LiveAttendanceSessionResponse.builder().sessionId(sessionId).status("OPEN").build());

            // Student -> 403
            mockMvc.perform(post("/api/v1/classes/{classId}/attendance-sessions", classId)
                            .with(user(studentDetails))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(40300));

            // Lecturer -> 200
            mockMvc.perform(post("/api/v1/classes/{classId}/attendance-sessions", classId)
                            .with(user(lecturerDetails))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("GET /api/v1/classes/{classId}/statistics - Student 403 Forbidden, Lecturer 200 OK")
        void testGetClassStatisticsRbac() throws Exception {
            when(classService.getClassStatistics(eq(classId)))
                    .thenReturn(ClassStatisticsResponse.builder().classId(classId).enrolledCount(45).build());

            // Student -> 403
            mockMvc.perform(get("/api/v1/classes/{classId}/statistics", classId)
                            .with(user(studentDetails)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(40300));

            // Lecturer -> 200
            mockMvc.perform(get("/api/v1/classes/{classId}/statistics", classId)
                            .with(user(lecturerDetails)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("3. Method-Level RBAC Security Tests (AssignmentController)")
    class AssignmentControllerRbacTests {

        @Test
        @DisplayName("GET /api/v1/assignments/{assignmentId}/submissions - Student 403 Forbidden, Lecturer 200 OK")
        void testGetAssignmentSubmissionsRbac() throws Exception {
            when(assignmentService.getAssignmentSubmissions(eq(assignmentId)))
                    .thenReturn(AssignmentSubmissionListResponse.builder().assignmentId(assignmentId).submissions(Collections.emptyList()).build());

            // Student -> 403
            mockMvc.perform(get("/api/v1/assignments/{assignmentId}/submissions", assignmentId)
                            .with(user(studentDetails)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(40300));

            // Lecturer -> 200
            mockMvc.perform(get("/api/v1/assignments/{assignmentId}/submissions", assignmentId)
                            .with(user(lecturerDetails)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("PUT /api/v1/assignments/{assignmentId}/submissions/{studentId}/grade - Student 403 Forbidden, Lecturer 200 OK")
        void testGradeSubmissionRbac() throws Exception {
            GradeSubmissionRequest req = GradeSubmissionRequest.builder()
                    .score(9.5)
                    .feedback("Làm bài tốt")
                    .build();

            when(assignmentService.gradeSubmission(eq(assignmentId), eq(studentId), any()))
                    .thenReturn(AssignmentSubmissionListResponse.builder().assignmentId(assignmentId).submissions(Collections.emptyList()).build());

            // Student -> 403
            mockMvc.perform(put("/api/v1/assignments/{assignmentId}/submissions/{studentId}/grade", assignmentId, studentId)
                            .with(user(studentDetails))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(40300));

            // Lecturer -> 200
            mockMvc.perform(put("/api/v1/assignments/{assignmentId}/submissions/{studentId}/grade", assignmentId, studentId)
                            .with(user(lecturerDetails))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("4. Method-Level RBAC Security Tests (AttendanceController)")
    class AttendanceControllerRbacTests {

        @Test
        @DisplayName("GET /api/v1/attendance-sessions/{sessionId}/live - Student 403 Forbidden, Lecturer 200 OK")
        void testGetLiveSessionRbac() throws Exception {
            when(attendanceService.getLiveSession(eq(sessionId)))
                    .thenReturn(LiveAttendanceSessionResponse.builder().sessionId(sessionId).status("OPEN").build());

            // Student -> 403
            mockMvc.perform(get("/api/v1/attendance-sessions/{sessionId}/live", sessionId)
                            .with(user(studentDetails)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(40300));

            // Lecturer -> 200
            mockMvc.perform(get("/api/v1/attendance-sessions/{sessionId}/live", sessionId)
                            .with(user(lecturerDetails)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("PUT /api/v1/attendance-sessions/{sessionId}/close - Student 403 Forbidden, Lecturer 200 OK")
        void testCloseSessionRbac() throws Exception {
            when(attendanceService.closeSession(eq(sessionId)))
                    .thenReturn(LiveAttendanceSessionResponse.builder().sessionId(sessionId).status("CLOSED").build());

            // Student -> 403
            mockMvc.perform(put("/api/v1/attendance-sessions/{sessionId}/close", sessionId)
                            .with(user(studentDetails)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(40300));

            // Lecturer -> 200
            mockMvc.perform(put("/api/v1/attendance-sessions/{sessionId}/close", sessionId)
                            .with(user(lecturerDetails)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("5. Verify Duplicate Routes Removed")
    class DuplicateRoutesRemovedTests {

        @Test
        @DisplayName("Verify duplicate methods were removed from controllers")
        void testDuplicateMethodsRemoved() {
            assertThat(AssignmentController.class.getDeclaredMethods())
                    .noneMatch(m -> m.getName().equals("createAssignmentForClass"));
            assertThat(AttendanceController.class.getDeclaredMethods())
                    .noneMatch(m -> m.getName().equals("openSession"));
        }
    }
}

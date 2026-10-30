package vn.edu.uit.edulens.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;
import vn.edu.uit.edulens.config.WebSocketConfig;
import vn.edu.uit.edulens.dto.request.AttendanceCheckInRequest;
import vn.edu.uit.edulens.dto.request.CreateAssignmentRequest;
import vn.edu.uit.edulens.dto.request.CreateMaterialRequest;
import vn.edu.uit.edulens.dto.request.OpenQrSessionRequest;
import vn.edu.uit.edulens.dto.response.LiveAttendanceSessionResponse;
import vn.edu.uit.edulens.entity.AttendanceRecord;
import vn.edu.uit.edulens.entity.AttendanceSession;
import vn.edu.uit.edulens.entity.CourseClass;
import vn.edu.uit.edulens.entity.Material;
import vn.edu.uit.edulens.entity.User;
import vn.edu.uit.edulens.entity.enums.AttendanceStatus;
import vn.edu.uit.edulens.entity.enums.ClassStatus;
import vn.edu.uit.edulens.entity.enums.Role;
import vn.edu.uit.edulens.entity.enums.SessionStatus;
import vn.edu.uit.edulens.repository.AttendanceRecordRepository;
import vn.edu.uit.edulens.repository.AttendanceSessionRepository;
import vn.edu.uit.edulens.repository.AssignmentRepository;
import vn.edu.uit.edulens.repository.ClassMemberRepository;
import vn.edu.uit.edulens.repository.CourseClassRepository;
import vn.edu.uit.edulens.repository.MaterialRepository;
import vn.edu.uit.edulens.repository.UserRepository;
import vn.edu.uit.edulens.security.CustomUserDetailsService;
import vn.edu.uit.edulens.security.JwtTokenProvider;
import vn.edu.uit.edulens.security.WebSocketAuthChannelInterceptor;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RealtimeAndNotificationIntegrationTest {

    @Nested
    @DisplayName("WebSocket Configuration & Channel Interceptor Tests")
    class WebSocketConfigTests {

        @Mock
        private JwtTokenProvider jwtTokenProvider;
        @Mock
        private CustomUserDetailsService userDetailsService;
        @Mock
        private MessageChannel channel;

        @Test
        @DisplayName("WebSocketConfig registers /ws endpoints and message brokers correctly")
        void testWebSocketConfigRegistration() {
            WebSocketAuthChannelInterceptor interceptor =
                    new WebSocketAuthChannelInterceptor(jwtTokenProvider, userDetailsService);
            WebSocketConfig config = new WebSocketConfig(interceptor);

            StompEndpointRegistry endpointRegistry = mock(StompEndpointRegistry.class);
            StompWebSocketEndpointRegistration registration = mock(StompWebSocketEndpointRegistration.class);
            when(endpointRegistry.addEndpoint("/ws")).thenReturn(registration);
            when(registration.setAllowedOriginPatterns("*")).thenReturn(registration);

            config.registerStompEndpoints(endpointRegistry);
            verify(endpointRegistry, times(2)).addEndpoint("/ws");

            MessageBrokerRegistry brokerRegistry = mock(MessageBrokerRegistry.class);
            config.configureMessageBroker(brokerRegistry);
            verify(brokerRegistry).enableSimpleBroker("/topic", "/queue");
            verify(brokerRegistry).setApplicationDestinationPrefixes("/app");

            ChannelRegistration channelRegistration = mock(ChannelRegistration.class);
            config.configureClientInboundChannel(channelRegistration);
            verify(channelRegistration).interceptors(interceptor);
        }

        @Test
        @DisplayName("WebSocketAuthChannelInterceptor authenticates valid JWT on CONNECT")
        void testInterceptorValidJwt() {
            WebSocketAuthChannelInterceptor authInterceptor =
                    new WebSocketAuthChannelInterceptor(jwtTokenProvider, userDetailsService);

            UUID userId = UUID.randomUUID();
            String token = "valid-token-xyz";

            when(jwtTokenProvider.validateToken(token)).thenReturn(true);
            when(jwtTokenProvider.getUserIdFromToken(token)).thenReturn(userId);

            UserDetails userDetails = mock(UserDetails.class);
            when(userDetails.isEnabled()).thenReturn(true);
            when(userDetails.getUsername()).thenReturn("student1@uit.edu.vn");
            doReturn(List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))).when(userDetails).getAuthorities();
            when(userDetailsService.loadUserById(userId)).thenReturn(userDetails);

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            accessor.setLeaveMutable(true);
            accessor.setNativeHeader("Authorization", "Bearer " + token);
            Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            Message<?> result = authInterceptor.preSend(message, channel);
            assertNotNull(result);

            StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
            Authentication auth = (Authentication) resultAccessor.getUser();
            assertNotNull(auth);
            assertEquals(userDetails, auth.getPrincipal());
        }

        @Test
        @DisplayName("WebSocketAuthChannelInterceptor rejects CONNECT without Authorization header")
        void testInterceptorMissingAuthHeader() {
            WebSocketAuthChannelInterceptor authInterceptor =
                    new WebSocketAuthChannelInterceptor(jwtTokenProvider, userDetailsService);

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            assertThrows(AccessDeniedException.class, () -> authInterceptor.preSend(message, channel));
        }

        @Test
        @DisplayName("WebSocketAuthChannelInterceptor rejects CONNECT with invalid JWT")
        void testInterceptorInvalidJwt() {
            WebSocketAuthChannelInterceptor authInterceptor =
                    new WebSocketAuthChannelInterceptor(jwtTokenProvider, userDetailsService);

            when(jwtTokenProvider.validateToken("bad-token")).thenReturn(false);

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
            accessor.setNativeHeader("Authorization", "Bearer bad-token");
            Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            assertThrows(AccessDeniedException.class, () -> authInterceptor.preSend(message, channel));
        }

        @Test
        @DisplayName("WebSocketAuthChannelInterceptor rejects unauthenticated SUBSCRIBE")
        void testInterceptorRejectsUnauthenticatedSubscribe() {
            WebSocketAuthChannelInterceptor authInterceptor =
                    new WebSocketAuthChannelInterceptor(jwtTokenProvider, userDetailsService);

            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
            accessor.setDestination("/topic/attendance-sessions/123/live");
            Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            assertThrows(AccessDeniedException.class, () -> authInterceptor.preSend(message, channel));
        }

        @Test
        @DisplayName("WebSocketAuthChannelInterceptor allows authenticated SUBSCRIBE")
        void testInterceptorAllowsAuthenticatedSubscribe() {
            WebSocketAuthChannelInterceptor authInterceptor =
                    new WebSocketAuthChannelInterceptor(jwtTokenProvider, userDetailsService);

            Authentication auth = mock(Authentication.class);
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
            accessor.setUser(auth);
            accessor.setDestination("/topic/attendance-sessions/123/live");
            Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

            Message<?> result = authInterceptor.preSend(message, channel);
            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("AttendanceService Realtime STOMP & Push Notification Tests")
    class AttendanceServiceRealtimeTests {

        @Mock
        private AttendanceSessionRepository attendanceSessionRepository;
        @Mock
        private AttendanceRecordRepository attendanceRecordRepository;
        @Mock
        private ClassMemberRepository classMemberRepository;
        @Mock
        private CourseClassRepository courseClassRepository;
        @Mock
        private UserRepository userRepository;
        @Mock
        private SimpMessagingTemplate messagingTemplate;
        @Mock
        private FirebaseService firebaseService;

        @InjectMocks
        private AttendanceService attendanceService;

        private UUID studentId;
        private UUID classId;
        private UUID sessionId;
        private CourseClass courseClass;
        private AttendanceSession session;
        private User student;

        @BeforeEach
        void setUp() {
            studentId = UUID.randomUUID();
            classId = UUID.randomUUID();
            sessionId = UUID.randomUUID();

            student = User.builder()
                    .id(studentId)
                    .fullName("Nguyễn Văn An")
                    .role(Role.ROLE_STUDENT)
                    .fcmToken("fcm-student-token")
                    .build();

            courseClass = CourseClass.builder()
                    .id(classId)
                    .name("Hệ điều hành")
                    .code("IT007.M11")
                    .status(ClassStatus.ACTIVE)
                    .build();

            session = AttendanceSession.builder()
                    .id(sessionId)
                    .courseClass(courseClass)
                    .status(SessionStatus.OPEN)
                    .wifiBssid("00:11:22:33:44:55")
                    .latitude(10.8700)
                    .longitude(106.8031)
                    .radiusMeters(50.0)
                    .createdAt(Instant.now())
                    .build();

            attendanceService.setMessagingTemplate(messagingTemplate);
            attendanceService.setFirebaseService(firebaseService);
        }

        @Test
        @DisplayName("checkIn broadcasts live attendance snapshot via WebSocket")
        void testCheckInBroadcastsLiveSnapshot() {
            long timestampMs = System.currentTimeMillis();
            String qrPayload = "UNICLASS:" + courseClass.getCode() + ":" + sessionId + ":" + timestampMs;

            AttendanceCheckInRequest request = AttendanceCheckInRequest.builder()
                    .sessionId(qrPayload)
                    .wifiBssid("00:11:22:33:44:55")
                    .latitude(10.8700)
                    .longitude(106.8031)
                    .deviceId("DEVICE-UUID-1234")
                    .build();

            when(attendanceSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
            when(attendanceRecordRepository.findBySessionIdAndDeviceId(sessionId, "DEVICE-UUID-1234")).thenReturn(Optional.empty());
            when(attendanceRecordRepository.findBySessionIdAndStudentId(sessionId, studentId)).thenReturn(Optional.empty());
            when(userRepository.findById(studentId)).thenReturn(Optional.of(student));
            when(attendanceRecordRepository.save(any(AttendanceRecord.class))).thenAnswer(i -> i.getArgument(0));
            when(classMemberRepository.countByCourseClassId(classId)).thenReturn(40L);
            when(attendanceRecordRepository.findBySessionId(sessionId)).thenReturn(Collections.emptyList());

            attendanceService.checkIn(studentId, request);

            verify(messagingTemplate).convertAndSend(
                    eq("/topic/attendance-sessions/" + sessionId + "/live"),
                    any(LiveAttendanceSessionResponse.class)
            );
        }

        @Test
        @DisplayName("closeSession broadcasts CLOSED status via WebSocket")
        void testCloseSessionBroadcastsClosedStatus() {
            when(attendanceSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
            when(attendanceSessionRepository.save(any(AttendanceSession.class))).thenAnswer(i -> i.getArgument(0));
            when(classMemberRepository.countByCourseClassId(classId)).thenReturn(30L);
            when(attendanceRecordRepository.findBySessionId(sessionId)).thenReturn(Collections.emptyList());

            LiveAttendanceSessionResponse resp = attendanceService.closeSession(sessionId);

            assertEquals(SessionStatus.CLOSED.name(), resp.getStatus());
            verify(messagingTemplate).convertAndSend(
                    eq("/topic/attendance-sessions/" + sessionId + "/live"),
                    any(LiveAttendanceSessionResponse.class)
            );
        }

        @Test
        @DisplayName("openSession triggers FCM push notification to class students")
        void testOpenSessionNotifiesStudents() {
            OpenQrSessionRequest request = OpenQrSessionRequest.builder()
                    .wifiBssid("00:11:22:33:44:55")
                    .latitude(10.8700)
                    .longitude(106.8031)
                    .radiusMeters(50.0)
                    .build();

            when(courseClassRepository.findById(classId)).thenReturn(Optional.of(courseClass));
            when(attendanceSessionRepository.findFirstByCourseClassIdAndStatus(classId, SessionStatus.OPEN)).thenReturn(Optional.empty());
            when(attendanceSessionRepository.save(any(AttendanceSession.class))).thenAnswer(i -> i.getArgument(0));

            attendanceService.openSession(classId, request);

            ArgumentCaptor<Map<String, String>> dataCaptor = ArgumentCaptor.forClass(Map.class);
            verify(firebaseService).notifyClassStudents(
                    eq(classId),
                    contains("Điểm danh"),
                    contains("Phiên điểm danh đã được mở"),
                    dataCaptor.capture()
            );

            Map<String, String> data = dataCaptor.getValue();
            assertEquals("ATTENDANCE_SESSION_OPEN", data.get("type"));
            assertEquals(classId.toString(), data.get("classId"));
            assertNotNull(data.get("sessionId"));
        }
    }

    @Nested
    @DisplayName("Class & Assignment Service FCM Push Triggers")
    class DomainPushNotificationTests {

        @Mock
        private CourseClassRepository courseClassRepository;
        @Mock
        private MaterialRepository materialRepository;
        @Mock
        private AssignmentRepository assignmentRepository;
        @Mock
        private FirebaseService firebaseService;

        @InjectMocks
        private ClassService classService;

        @InjectMocks
        private AssignmentService assignmentService;

        private UUID classId;
        private CourseClass courseClass;

        @BeforeEach
        void setUp() {
            classId = UUID.randomUUID();
            courseClass = CourseClass.builder()
                    .id(classId)
                    .name("Kiến trúc máy tính")
                    .code("CE119.M11")
                    .status(ClassStatus.ACTIVE)
                    .build();

            classService.setFirebaseService(firebaseService);
            assignmentService.setFirebaseService(firebaseService);
        }

        @Test
        @DisplayName("createMaterial with urgent announcement notifies class students")
        void testCreateMaterialUrgentNotification() {
            CreateMaterialRequest request = CreateMaterialRequest.builder()
                    .title("Thay đổi phòng thi cuối kỳ")
                    .fileUrl("https://example.com/notice.pdf")
                    .urgentAnnouncement(true)
                    .build();

            when(courseClassRepository.findById(classId)).thenReturn(Optional.of(courseClass));
            when(materialRepository.save(any(Material.class))).thenAnswer(i -> i.getArgument(0));

            classService.createMaterial(classId, request);

            verify(firebaseService).notifyClassStudents(
                    eq(classId),
                    contains("Thông báo khẩn"),
                    eq("Thay đổi phòng thi cuối kỳ"),
                    argThat(data -> "URGENT_ANNOUNCEMENT".equals(data.get("type")) && classId.toString().equals(data.get("classId")))
            );
        }

        @Test
        @DisplayName("createAssignment with notifyStudents flag notifies class students")
        void testCreateAssignmentNotification() {
            CreateAssignmentRequest request = CreateAssignmentRequest.builder()
                    .title("Bài tập lớn Kiến trúc máy tính")
                    .deadline("2026-11-20T23:59:59Z")
                    .notifyStudents(true)
                    .build();

            when(courseClassRepository.findById(classId)).thenReturn(Optional.of(courseClass));
            when(assignmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            assignmentService.createAssignment(classId, request);

            verify(firebaseService).notifyClassStudents(
                    eq(classId),
                    contains("Bài tập mới"),
                    contains("2026-11-20"),
                    argThat(data -> "NEW_ASSIGNMENT".equals(data.get("type")) && classId.toString().equals(data.get("classId")))
            );
        }
    }

    @Nested
    @DisplayName("FirebaseService Token Extraction & Partitioning Tests")
    class FirebaseServiceTests {

        @Mock
        private ClassMemberRepository classMemberRepository;

        @InjectMocks
        private FirebaseService firebaseService;

        @Test
        @DisplayName("notifyClassStudents queries repository and deduplicates non-empty FCM tokens")
        void testNotifyClassStudentsTokenExtraction() {
            UUID classId = UUID.randomUUID();

            when(classMemberRepository.findFcmTokensByCourseClassId(classId))
                    .thenReturn(List.of("token-1", "token-2", "  ", "token-1"));

            // Without active FirebaseApp, sendMulticastNotification gracefully logs warning and returns
            assertDoesNotThrow(() -> firebaseService.notifyClassStudents(
                    classId,
                    "Tiêu đề",
                    "Nội dung",
                    Map.of("key", "val")
            ));

            verify(classMemberRepository).findFcmTokensByCourseClassId(classId);
        }

        @Test
        @DisplayName("FirebaseService handles null or empty inputs gracefully")
        void testHandlesNullSafely() {
            assertDoesNotThrow(() -> firebaseService.notifyClassStudents(null, "Title", "Body", Map.of()));
            assertDoesNotThrow(() -> firebaseService.sendMulticastNotification(null, "Title", "Body", Map.of()));
            assertDoesNotThrow(() -> firebaseService.sendMulticastNotification(Collections.emptyList(), "Title", "Body", Map.of()));
            assertDoesNotThrow(() -> firebaseService.sendMulticastNotification(List.of("", "   "), "Title", "Body", Map.of()));
        }
    }
}

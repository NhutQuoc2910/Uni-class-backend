package vn.edu.uit.edulens.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.uit.edulens.dto.request.AttendanceCheckInRequest;
import vn.edu.uit.edulens.dto.request.OpenQrSessionRequest;
import vn.edu.uit.edulens.dto.response.AttendanceCheckInResponse;
import vn.edu.uit.edulens.dto.response.LiveAttendanceSessionResponse;
import vn.edu.uit.edulens.entity.AttendanceRecord;
import vn.edu.uit.edulens.entity.AttendanceSession;
import vn.edu.uit.edulens.entity.ClassMember;
import vn.edu.uit.edulens.entity.CourseClass;
import vn.edu.uit.edulens.entity.User;
import vn.edu.uit.edulens.entity.enums.AttendanceStatus;
import vn.edu.uit.edulens.entity.enums.ClassStatus;
import vn.edu.uit.edulens.entity.enums.SessionStatus;
import vn.edu.uit.edulens.exception.AppException;
import vn.edu.uit.edulens.exception.ErrorCode;
import vn.edu.uit.edulens.repository.AttendanceRecordRepository;
import vn.edu.uit.edulens.repository.AttendanceSessionRepository;
import vn.edu.uit.edulens.repository.ClassMemberRepository;
import vn.edu.uit.edulens.repository.CourseClassRepository;
import vn.edu.uit.edulens.repository.UserRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private static final double EARTH_RADIUS_METERS = 6371000.0;

    private final AttendanceSessionRepository attendanceSessionRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final ClassMemberRepository classMemberRepository;
    private final CourseClassRepository courseClassRepository;
    private final UserRepository userRepository;

    @Value("${app.attendance.allow-demo-bypass:false}")
    private boolean allowDemoBypass = false;

    public void setAllowDemoBypass(boolean allowDemoBypass) {
        this.allowDemoBypass = allowDemoBypass;
    }

    @Transactional
    public LiveAttendanceSessionResponse openSession(UUID classId, OpenQrSessionRequest request) {
        CourseClass cc = courseClassRepository.findById(classId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));

        if (cc.getStatus() == ClassStatus.ARCHIVED) {
            throw new AppException(ErrorCode.CLASS_ARCHIVED);
        }

        String wifiBssid = (request != null && request.getWifiBssid() != null && !request.getWifiBssid().isBlank())
                ? request.getWifiBssid().trim()
                : "00:11:22:33:44:55";
        Double latitude = (request != null && request.getLatitude() != null)
                ? request.getLatitude()
                : 10.8700;
        Double longitude = (request != null && request.getLongitude() != null)
                ? request.getLongitude()
                : 106.8031;
        Double radiusMeters = (request != null && request.getRadiusMeters() != null && request.getRadiusMeters() > 0)
                ? request.getRadiusMeters()
                : 50.0;

        Optional<AttendanceSession> existingOpenOpt =
                attendanceSessionRepository.findFirstByCourseClassIdAndStatus(classId, SessionStatus.OPEN);

        AttendanceSession session;
        if (existingOpenOpt.isPresent()) {
            session = existingOpenOpt.get();
            if (request != null) {
                if (request.getWifiBssid() != null && !request.getWifiBssid().isBlank()) {
                    session.setWifiBssid(wifiBssid);
                }
                if (request.getLatitude() != null) {
                    session.setLatitude(latitude);
                }
                if (request.getLongitude() != null) {
                    session.setLongitude(longitude);
                }
                if (request.getRadiusMeters() != null && request.getRadiusMeters() > 0) {
                    session.setRadiusMeters(radiusMeters);
                }
            }
        } else {
            session = AttendanceSession.builder()
                    .id(UUID.randomUUID())
                    .courseClass(cc)
                    .wifiBssid(wifiBssid)
                    .latitude(latitude)
                    .longitude(longitude)
                    .radiusMeters(radiusMeters)
                    .status(SessionStatus.OPEN)
                    .createdAt(Instant.now())
                    .build();
        }

        AttendanceSession saved = attendanceSessionRepository.save(session);
        AttendanceSession effective = saved != null ? saved : session;
        return buildLiveSessionResponse(effective);
    }

    @Transactional(readOnly = true)
    public LiveAttendanceSessionResponse getLiveSession(UUID sessionId) {
        AttendanceSession session = attendanceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_FOUND));
        return buildLiveSessionResponse(session);
    }

    @Transactional
    public LiveAttendanceSessionResponse closeSession(UUID sessionId) {
        AttendanceSession session = attendanceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_FOUND));

        session.setStatus(SessionStatus.CLOSED);
        session.setClosedAt(Instant.now());

        AttendanceSession saved = attendanceSessionRepository.save(session);
        AttendanceSession effective = saved != null ? saved : session;
        return buildLiveSessionResponse(effective);
    }

    @Transactional
    public AttendanceCheckInResponse checkIn(UUID studentId, AttendanceCheckInRequest request) {
        if (request == null || request.getSessionId() == null || request.getSessionId().isBlank()) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Thiếu mã phiên điểm danh");
        }

        String rawSessionId = request.getSessionId().trim();
        if (rawSessionId.equalsIgnoreCase("DEMO_EXPIRED")) {
            throw new AppException(ErrorCode.QR_EXPIRED);
        }

        AttendanceSession session = resolveSession(studentId, rawSessionId);

        // Layer 1: QR Session status check
        if (session.getStatus() != SessionStatus.OPEN
                || (session.getClosedAt() != null && session.getClosedAt().isBefore(Instant.now()))) {
            throw new AppException(ErrorCode.QR_EXPIRED);
        }

        // Layer 2: WiFi BSSID verification
        if (session.getWifiBssid() != null && !session.getWifiBssid().isBlank()) {
            if (request.getWifiBssid() == null
                    || !session.getWifiBssid().trim().equalsIgnoreCase(request.getWifiBssid().trim())) {
                throw new AppException(ErrorCode.WIFI_MISMATCH);
            }
        }

        // Layer 3a: GPS Haversine distance verification
        double distanceMeters = 0.0;
        if (session.getLatitude() != null && session.getLongitude() != null) {
            if (request.getLatitude() == null || request.getLongitude() == null) {
                throw new AppException(ErrorCode.GPS_OUT_OF_RANGE);
            }
            distanceMeters = calculateHaversineMeters(
                    session.getLatitude(),
                    session.getLongitude(),
                    request.getLatitude(),
                    request.getLongitude()
            );
            double maxRadius = session.getRadiusMeters() != null ? session.getRadiusMeters() : 50.0;
            if (distanceMeters > maxRadius) {
                throw new AppException(ErrorCode.GPS_OUT_OF_RANGE);
            }
            distanceMeters = Math.round(distanceMeters * 10.0) / 10.0;
        }

        // Layer 3b: Device anti-proxy verification
        String deviceId = request.getDeviceId() != null ? request.getDeviceId().trim() : "";
        if (deviceId.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Thiếu mã thiết bị");
        }

        Optional<AttendanceRecord> existingDeviceRecordOpt =
                attendanceRecordRepository.findBySessionIdAndDeviceId(session.getId(), deviceId);
        if (existingDeviceRecordOpt.isPresent()) {
            AttendanceRecord existingDeviceRecord = existingDeviceRecordOpt.get();
            if (existingDeviceRecord.getStudent() != null
                    && !studentId.equals(existingDeviceRecord.getStudent().getId())) {
                throw new AppException(ErrorCode.DEVICE_ALREADY_USED);
            }
        }

        // Check if student already checked in for this session
        Optional<AttendanceRecord> existingStudentRecordOpt =
                attendanceRecordRepository.findBySessionIdAndStudentId(session.getId(), studentId);

        AttendanceRecord record;
        if (existingStudentRecordOpt.isPresent()) {
            record = existingStudentRecordOpt.get();
        } else {
            User student = userRepository.findById(studentId)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

            AttendanceRecord newRecord = AttendanceRecord.builder()
                    .id(UUID.randomUUID())
                    .session(session)
                    .student(student)
                    .deviceId(deviceId)
                    .verifiedBssid(request.getWifiBssid() != null ? request.getWifiBssid().trim() : session.getWifiBssid())
                    .latitude(request.getLatitude())
                    .longitude(request.getLongitude())
                    .status(AttendanceStatus.PRESENT)
                    .scannedAt(Instant.now())
                    .build();

            AttendanceRecord saved = attendanceRecordRepository.save(newRecord);
            record = saved != null ? saved : newRecord;
        }

        CourseClass cc = session.getCourseClass();
        String classCode = cc != null ? cc.getCode() : "";
        String className = cc != null ? cc.getName() : "";
        String room = cc != null ? cc.getRoom() : "";
        String lecturerName = (cc != null && cc.getLecturer() != null) ? cc.getLecturer().getFullName() : "";
        String scannedAtStr = record.getScannedAt() != null
                ? record.getScannedAt().toString()
                : Instant.now().toString();
        String statusStr = record.getStatus() != null
                ? record.getStatus().name()
                : AttendanceStatus.PRESENT.name();

        return AttendanceCheckInResponse.builder()
                .recordId(record.getId())
                .sessionId(session.getId())
                .classCode(classCode)
                .className(className)
                .room(room)
                .lecturerName(lecturerName)
                .scannedAt(scannedAtStr)
                .deviceId(record.getDeviceId())
                .status(statusStr)
                .qrVerified(true)
                .wifiVerified(true)
                .gpsVerified(true)
                .distanceMeters(distanceMeters)
                .build();
    }

    private LiveAttendanceSessionResponse buildLiveSessionResponse(AttendanceSession session) {
        CourseClass cc = session.getCourseClass();
        UUID classId = cc != null ? cc.getId() : null;
        int totalStudents = classId != null ? (int) classMemberRepository.countByCourseClassId(classId) : 0;

        List<AttendanceRecord> records = attendanceRecordRepository.findBySessionId(session.getId());
        List<LiveAttendanceSessionResponse.AttendeeItem> attendees = new ArrayList<>();
        int presentCount = 0;

        if (records != null) {
            for (AttendanceRecord r : records) {
                User student = r.getStudent();
                String studentName = student != null ? student.getFullName() : "";
                AttendanceStatus st = r.getStatus() != null ? r.getStatus() : AttendanceStatus.PRESENT;
                if (st == AttendanceStatus.PRESENT) {
                    presentCount++;
                }
                attendees.add(LiveAttendanceSessionResponse.AttendeeItem.builder()
                        .studentId(student != null ? student.getId() : null)
                        .studentName(studentName)
                        .studentCode(ClassService.extractStudentCode(student))
                        .avatarInitial(ClassService.extractLecturerInitial(studentName))
                        .status(st.name())
                        .scannedAt(r.getScannedAt() != null ? r.getScannedAt().toString() : "")
                        .deviceId(r.getDeviceId())
                        .build());
            }
        }

        if (totalStudents < presentCount) {
            totalStudents = presentCount;
        }
        int absentCount = Math.max(0, totalStudents - presentCount);

        return LiveAttendanceSessionResponse.builder()
                .sessionId(session.getId())
                .classId(classId)
                .classCode(cc != null ? cc.getCode() : "")
                .className(cc != null ? cc.getName() : "")
                .room(cc != null ? cc.getRoom() : "")
                .inviteCode(cc != null ? cc.getInviteCode() : "")
                .status(session.getStatus() != null ? session.getStatus().name() : SessionStatus.OPEN.name())
                .wifiBssid(session.getWifiBssid())
                .latitude(session.getLatitude())
                .longitude(session.getLongitude())
                .radiusMeters(session.getRadiusMeters())
                .createdAt(session.getCreatedAt() != null ? session.getCreatedAt().toString() : Instant.now().toString())
                .totalStudents(totalStudents)
                .presentCount(presentCount)
                .absentCount(absentCount)
                .attendees(attendees)
                .build();
    }

    private AttendanceSession resolveSession(UUID studentId, String rawSessionId) {
        if (rawSessionId.startsWith("UNICLASS:")) {
            String[] parts = rawSessionId.split(":");
            if (parts.length >= 4) {
                String sessionIdStr = parts[2];
                long timestampMs;
                try {
                    timestampMs = Long.parseLong(parts[3]);
                } catch (NumberFormatException e) {
                    throw new AppException(ErrorCode.QR_EXPIRED);
                }
                long now = System.currentTimeMillis();
                if (now - timestampMs > 60000 || timestampMs > now + 10000) {
                    throw new AppException(ErrorCode.QR_EXPIRED);
                }
                rawSessionId = sessionIdStr;
            } else if (parts.length >= 3) {
                rawSessionId = parts[2];
            }
        }

        if (rawSessionId.equalsIgnoreCase("DEMO_OPEN") || rawSessionId.equalsIgnoreCase("ACTIVE_DEMO")) {
            if (!allowDemoBypass) {
                throw new AppException(ErrorCode.SESSION_NOT_FOUND);
            }

            Optional<AttendanceSession> openOpt =
                    attendanceSessionRepository.findFirstByStatusOrderByCreatedAtDesc(SessionStatus.OPEN);
            if (openOpt.isPresent()) {
                return openOpt.get();
            }

            List<AttendanceSession> allSessions = attendanceSessionRepository.findAll();
            for (AttendanceSession s : allSessions) {
                if (s.getStatus() == SessionStatus.OPEN) {
                    return s;
                }
            }

            CourseClass targetClass = null;
            List<ClassMember> memberships = classMemberRepository.findByStudentId(studentId);
            if (memberships != null && !memberships.isEmpty()) {
                targetClass = memberships.get(0).getCourseClass();
            }
            if (targetClass == null) {
                List<CourseClass> classes = courseClassRepository.findAll();
                if (classes != null && !classes.isEmpty()) {
                    targetClass = classes.get(0);
                }
            }
            if (targetClass == null) {
                throw new AppException(ErrorCode.SESSION_NOT_FOUND);
            }

            AttendanceSession created = AttendanceSession.builder()
                    .id(UUID.randomUUID())
                    .courseClass(targetClass)
                    .wifiBssid("00:11:22:33:44:55")
                    .latitude(10.8700)
                    .longitude(106.8031)
                    .radiusMeters(50.0)
                    .status(SessionStatus.OPEN)
                    .createdAt(Instant.now())
                    .build();

            AttendanceSession saved = attendanceSessionRepository.save(created);
            return saved != null ? saved : created;
        }

        UUID sessionUuid;
        try {
            sessionUuid = UUID.fromString(rawSessionId);
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.SESSION_NOT_FOUND);
        }

        return attendanceSessionRepository.findById(sessionUuid)
                .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_FOUND));
    }

    static double calculateHaversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double rLat1 = Math.toRadians(lat1);
        double rLat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                + Math.cos(rLat1) * Math.cos(rLat2)
                * Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0);
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return EARTH_RADIUS_METERS * c;
    }
}

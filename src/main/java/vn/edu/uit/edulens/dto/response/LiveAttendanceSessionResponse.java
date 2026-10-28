package vn.edu.uit.edulens.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiveAttendanceSessionResponse {
    private UUID sessionId;
    private UUID classId;
    private String classCode;
    private String className;
    private String room;
    private String inviteCode;
    private String status;
    private String wifiBssid;
    private Double latitude;
    private Double longitude;
    private Double radiusMeters;
    private String createdAt;
    private int totalStudents;
    private int presentCount;
    private int absentCount;
    private List<AttendeeItem> attendees;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AttendeeItem {
        private UUID studentId;
        private String studentName;
        private String studentCode;
        private String avatarInitial;
        private String status;
        private String scannedAt;
        private String deviceId;
    }
}

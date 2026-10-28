package vn.edu.uit.edulens.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceCheckInResponse {
    private UUID recordId;
    private UUID sessionId;
    private String classCode;
    private String className;
    private String room;
    private String lecturerName;
    private String scannedAt;
    private String deviceId;
    private String status;
    private boolean qrVerified;
    private boolean wifiVerified;
    private boolean gpsVerified;
    private double distanceMeters;
}

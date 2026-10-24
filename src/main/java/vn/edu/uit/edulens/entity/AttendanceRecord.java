package vn.edu.uit.edulens.entity;

import jakarta.persistence.*;
import lombok.*;
import vn.edu.uit.edulens.entity.enums.AttendanceStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "attendance_records",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"session_id", "student_id"}),
        @UniqueConstraint(columnNames = {"session_id", "device_id"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private AttendanceSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "verified_bssid", length = 100)
    private String verifiedBssid;

    private Double latitude;

    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AttendanceStatus status = AttendanceStatus.PRESENT;

    @Column(name = "scanned_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant scannedAt = Instant.now();
}

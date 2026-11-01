package vn.edu.uit.edulens.entity;

import jakarta.persistence.*;
import lombok.*;
import vn.edu.uit.edulens.entity.enums.Role;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "pending_registrations",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_pending_registration_email",
            columnNames = "email"
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    // OTP đã được mã hóa, không lưu OTP gốc
    @Column(name = "otp_hash", nullable = false)
    private String otpHash;

    @Column(name = "otp_expires_at", nullable = false)
    private Instant otpExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    // Số lần nhập OTP sai
    @Column(name = "failed_attempts", nullable = false)
    @Builder.Default
    private int failedAttempts = 0;

    // Thời điểm gửi OTP gần nhất
    @Column(name = "last_sent_at")
    private Instant lastSentAt;
}
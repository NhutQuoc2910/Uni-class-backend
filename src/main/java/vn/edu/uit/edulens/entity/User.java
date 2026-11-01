package vn.edu.uit.edulens.entity;

import jakarta.persistence.*;
import lombok.*;
import vn.edu.uit.edulens.entity.enums.OtpPurpose;
import vn.edu.uit.edulens.entity.enums.Role;
import vn.edu.uit.edulens.entity.enums.UserStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(unique = true, length = 20)
    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "fcm_token")
    private String fcmToken;

    @Column(name = "otp_code", length = 64)
    private String otpCode;

    @Column(name = "otp_expires_at")
    private Instant otpExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "otp_purpose", length = 30)
    private OtpPurpose otpPurpose;

    @Column(name = "otp_failed_attempts", nullable = false)
    @Builder.Default
    private int otpFailedAttempts = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}

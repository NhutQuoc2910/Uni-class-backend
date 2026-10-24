package vn.edu.uit.edulens.dto.response;

import lombok.*;
import vn.edu.uit.edulens.entity.User;
import vn.edu.uit.edulens.entity.enums.Role;
import vn.edu.uit.edulens.entity.enums.UserStatus;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    private UUID id;
    private String email;
    private String phone;
    private String fullName;
    private Role role;
    private UserStatus status;
    private String avatarUrl;
    private String fcmToken;
    private Instant createdAt;

    public static UserResponse fromUser(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fullName(user.getFullName())
                .role(user.getRole())
                .status(user.getStatus())
                .avatarUrl(user.getAvatarUrl())
                .fcmToken(user.getFcmToken())
                .createdAt(user.getCreatedAt())
                .build();
    }
}

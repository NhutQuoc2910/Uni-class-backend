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
    private String studentId;
    private String lecturerId;
    private String faculty;
    private String cohort;
    private String educationProgram;
    private String academicDegree;
    private String academicRank;
    private String position;
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
                .studentId(user.getStudentId())
                .lecturerId(user.getLecturerId())
                .faculty(user.getFaculty())
                .cohort(user.getCohort())
                .educationProgram(user.getEducationProgram())
                .academicDegree(user.getAcademicDegree())
                .academicRank(user.getAcademicRank())
                .position(user.getPosition())
                .createdAt(user.getCreatedAt())
                .build();
    }
}

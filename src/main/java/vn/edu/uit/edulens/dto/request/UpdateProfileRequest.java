package vn.edu.uit.edulens.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileRequest {
    private String fullName;
    private String phone;
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
}

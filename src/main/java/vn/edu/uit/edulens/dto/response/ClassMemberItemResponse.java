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
public class ClassMemberItemResponse {
    private UUID studentId;
    private String fullName;
    private String email;
    private String studentCode;
    private String avatarInitial;
    private int attendanceRate;
    private int absentSessions;
    private int totalSessions;
    private int submittedAssignments;
    private int totalAssignments;
    private Double averageScore;
    private List<MemberAssignmentSummary> recentAssignments;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MemberAssignmentSummary {
        private UUID assignmentId;
        private String title;
        private String deadline;
        private boolean submitted;
        private String submittedAt;
        private Double score;
    }
}

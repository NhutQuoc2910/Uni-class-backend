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
public class AssignmentSubmissionListResponse {
    private UUID assignmentId;
    private String assignmentTitle;
    private String classCode;
    private String deadline;
    private int totalStudents;
    private int submittedCount;
    private int gradedCount;
    private int onTimeCount;
    private int lateCount;
    private int notSubmittedCount;
    private List<SubmissionItem> submissions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SubmissionItem {
        private UUID submissionId;
        private UUID studentId;
        private String studentName;
        private String studentCode;
        private String avatarInitial;
        private boolean submitted;
        private boolean late;
        private String submittedAt;
        private String fileUrl;
        private Double score;
        private String feedback;
    }
}

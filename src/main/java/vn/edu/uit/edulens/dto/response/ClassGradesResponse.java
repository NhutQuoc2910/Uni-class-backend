package vn.edu.uit.edulens.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassGradesResponse {
    private UUID classId;
    private String classCode;
    private String className;
    private String lecturerName;
    private double averageScore;
    private int gradedCount;
    private int totalAssignments;
    private List<GradeItem> grades;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GradeItem {
        private UUID assignmentId;
        private String assignmentTitle;
        private String submittedAt;
        private boolean submitted;
        private Double score;
        private String feedback;
        private String lecturerName;
        private String lecturerInitial;
    }
}

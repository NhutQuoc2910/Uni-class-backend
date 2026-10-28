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
public class ClassStatisticsResponse {
    private UUID classId;
    private String classCode;
    private String className;
    private String semester;
    private String status;
    private int enrolledCount;
    private int attendanceRate;
    private int warningAbsentCount;
    private double averageScore;
    private List<ClassMemberItemResponse> warningStudents;
    private List<ScoreBucket> scoreDistribution;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ScoreBucket {
        private String label;
        private int count;
        private int percentage;
    }
}

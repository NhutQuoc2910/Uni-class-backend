package vn.edu.uit.edulens.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignmentDetailResponse {
    private UUID id;
    private UUID classId;
    private String classCode;
    private String className;
    private String lecturerName;
    private String title;
    private String description;
    private String deadline;
    private String attachmentUrl;
    private boolean submitted;
    private String submittedFileUrl;
    private String submittedAt;
    private Double score;
    private String feedback;
}

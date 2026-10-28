package vn.edu.uit.edulens.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAssignmentRequest {
    @NotBlank(message = "Tiêu đề bài tập không được để trống")
    private String title;

    private String description;

    @NotBlank(message = "Hạn nộp không được để trống")
    private String deadline;

    private String attachmentUrl;

    private Boolean notifyStudents;

    private Boolean allowLate;
}

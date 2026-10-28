package vn.edu.uit.edulens.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GradeSubmissionRequest {
    @NotNull(message = "Điểm số không được để trống")
    private Double score;

    private String feedback;

    private Boolean publishAll;
}

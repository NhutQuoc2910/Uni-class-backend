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
public class CreateMaterialRequest {
    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;

    @NotBlank(message = "Đường dẫn hoặc nội dung tài liệu không được để trống")
    private String fileUrl;

    private String fileType;

    private Boolean urgentAnnouncement;
}

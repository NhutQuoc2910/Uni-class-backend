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
public class CreateClassRequest {
    @NotBlank(message = "Tên môn học không được để trống")
    private String name;

    @NotBlank(message = "Mã lớp không được để trống")
    private String code;

    @NotBlank(message = "Học kỳ không được để trống")
    private String semester;

    private String room;
}

package vn.edu.uit.edulens.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceCheckInRequest {

    @NotBlank(message = "Thiếu mã phiên điểm danh")
    private String sessionId;

    private String wifiBssid;

    private Double latitude;

    private Double longitude;

    @NotBlank(message = "Thiếu mã thiết bị")
    private String deviceId;
}

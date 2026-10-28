package vn.edu.uit.edulens.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OpenQrSessionRequest {
    private String wifiBssid;
    private Double latitude;
    private Double longitude;
    private Double radiusMeters;
}

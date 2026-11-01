package vn.edu.uit.edulens.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import vn.edu.uit.edulens.entity.enums.OtpPurpose;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerifyOtpRequest {

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Định dạng email không hợp lệ")
    private String email;

    @NotBlank(message = "Mã OTP không được để trống")
    @Pattern(
        regexp = "^[0-9]{6}$",
        message = "Mã OTP phải gồm đúng 6 chữ số"
    )
    @JsonAlias({"otp", "otpCode"})
    private String otpCode;

    @NotNull(message = "Mục đích xác thực không được để trống")
    private OtpPurpose purpose;
}
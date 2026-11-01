package vn.edu.uit.edulens.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import vn.edu.uit.edulens.entity.enums.OtpPurpose;
import vn.edu.uit.edulens.entity.enums.Role;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendOtpRequest {

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Định dạng email không hợp lệ")
    private String email;

    @NotNull
    private OtpPurpose purpose;

    // Các trường dưới đây chỉ bắt buộc khi purpose = REGISTER

    private String fullName;

    @Pattern(
        regexp = "^$|^[0-9+]{8,15}$",
        message = "Số điện thoại không hợp lệ"
    )
    private String phone;

    @Size(
        min = 6,
        message = "Mật khẩu phải chứa ít nhất 6 ký tự"
    )
    private String password;

    private Role role;
}
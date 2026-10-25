package vn.edu.uit.edulens.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.uit.edulens.dto.request.ChangePasswordRequest;
import vn.edu.uit.edulens.dto.request.UpdateProfileRequest;
import vn.edu.uit.edulens.dto.response.ApiResponse;
import vn.edu.uit.edulens.dto.response.UserResponse;
import vn.edu.uit.edulens.security.CustomUserDetails;
import vn.edu.uit.edulens.service.UserService;

@Tag(name = "2. User Profile", description = "Các API xem và cập nhật hồ sơ cá nhân")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Lấy thông tin cá nhân của người dùng đang đăng nhập")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        UserResponse response = userService.getProfile(userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Cập nhật họ tên, SĐT, avatar, FCM token")
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateCurrentUser(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody UpdateProfileRequest request
    ) {
        UserResponse response = userService.updateProfile(userDetails.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật thông tin thành công", response));
    }

    @Operation(summary = "Đổi mật khẩu người dùng đang đăng nhập")
    @PutMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        userService.changePassword(userDetails.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Đổi mật khẩu thành công", null));
    }
}

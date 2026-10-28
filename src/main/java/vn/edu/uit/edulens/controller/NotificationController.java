package vn.edu.uit.edulens.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.uit.edulens.dto.response.ApiResponse;
import vn.edu.uit.edulens.dto.response.NotificationItemResponse;
import vn.edu.uit.edulens.security.CustomUserDetails;
import vn.edu.uit.edulens.service.ClassService;

import java.util.Collections;
import java.util.List;

@Tag(name = "5. Notifications", description = "Các API danh sách thông báo của người dùng")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final ClassService classService;

    @Operation(summary = "Lấy danh sách thông báo tổng hợp từ các lớp đang tham gia")
    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationItemResponse>>> getNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.ok(ApiResponse.success(Collections.emptyList()));
        }
        List<NotificationItemResponse> notifications = classService.getNotifications(
                userDetails.getId(),
                userDetails.getUser().getRole()
        );
        return ResponseEntity.ok(ApiResponse.success(notifications));
    }
}

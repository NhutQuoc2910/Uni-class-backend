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
import vn.edu.uit.edulens.dto.response.HomeSummaryResponse;
import vn.edu.uit.edulens.security.CustomUserDetails;
import vn.edu.uit.edulens.service.HomeService;

@Tag(name = "3. Home", description = "Các API dữ liệu trang chủ tổng quan")
@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @Operation(summary = "Lấy dữ liệu tổng hợp cho màn hình Home (Số lớp, số bài tập chờ nộp, tỷ lệ chuyên cần, danh sách lớp và bài tập sắp đến hạn)")
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<HomeSummaryResponse>> getHomeSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.ok(ApiResponse.success(new HomeSummaryResponse()));
        }

        HomeSummaryResponse response = homeService.getSummary(
                userDetails.getId(),
                userDetails.getUser().getRole()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

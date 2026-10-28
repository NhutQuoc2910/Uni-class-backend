package vn.edu.uit.edulens.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.uit.edulens.dto.request.AttendanceCheckInRequest;
import vn.edu.uit.edulens.dto.response.ApiResponse;
import vn.edu.uit.edulens.dto.response.AttendanceCheckInResponse;
import vn.edu.uit.edulens.security.CustomUserDetails;
import vn.edu.uit.edulens.service.AttendanceService;

@Tag(name = "7. Attendance", description = "Các API điểm danh xác minh 3 lớp (QR, WiFi BSSID, GPS & Device ID)")
@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @Operation(summary = "Điểm danh bằng mã QR kết hợp xác minh 3 lớp (QR + WiFi BSSID + GPS + Device ID)")
    @PostMapping("/check-in")
    public ResponseEntity<ApiResponse<AttendanceCheckInResponse>> checkIn(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AttendanceCheckInRequest request
    ) {
        AttendanceCheckInResponse response = attendanceService.checkIn(userDetails.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Điểm danh thành công", response));
    }
}

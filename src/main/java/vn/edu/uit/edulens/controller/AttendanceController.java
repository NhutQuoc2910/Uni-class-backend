package vn.edu.uit.edulens.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.uit.edulens.dto.request.AttendanceCheckInRequest;
import vn.edu.uit.edulens.dto.response.ApiResponse;
import vn.edu.uit.edulens.dto.response.AttendanceCheckInResponse;
import vn.edu.uit.edulens.dto.response.LiveAttendanceSessionResponse;
import vn.edu.uit.edulens.security.CustomUserDetails;
import vn.edu.uit.edulens.service.AttendanceService;

import java.util.UUID;

@Tag(name = "7. Attendance", description = "Các API điểm danh xác minh 3 lớp và quản lý phiên điểm danh QR trực tiếp")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @Operation(summary = "Điểm danh bằng mã QR kết hợp xác minh 3 lớp (QR + WiFi BSSID + GPS + Device ID)")
    @PostMapping("/attendance/check-in")
    public ResponseEntity<ApiResponse<AttendanceCheckInResponse>> checkIn(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AttendanceCheckInRequest request
    ) {
        AttendanceCheckInResponse response = attendanceService.checkIn(userDetails.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Điểm danh thành công", response));
    }

    @PreAuthorize("hasAuthority('ROLE_LECTURER')")
    @Operation(summary = "Giảng viên theo dõi trực tiếp danh sách sinh viên đã điểm danh trong phiên")
    @GetMapping({"/attendance-sessions/{sessionId}/live", "/attendance/sessions/{sessionId}/live"})
    public ResponseEntity<ApiResponse<LiveAttendanceSessionResponse>> getLiveSession(
            @PathVariable UUID sessionId
    ) {
        LiveAttendanceSessionResponse live = attendanceService.getLiveSession(sessionId);
        return ResponseEntity.ok(ApiResponse.success(live));
    }

    @PreAuthorize("hasAuthority('ROLE_LECTURER')")
    @Operation(summary = "Giảng viên chốt và đóng phiên điểm danh")
    @PutMapping({"/attendance-sessions/{sessionId}/close", "/attendance/sessions/{sessionId}/close"})
    public ResponseEntity<ApiResponse<LiveAttendanceSessionResponse>> closeSession(
            @PathVariable UUID sessionId
    ) {
        LiveAttendanceSessionResponse closed = attendanceService.closeSession(sessionId);
        return ResponseEntity.ok(ApiResponse.success("Đã chốt phiên điểm danh", closed));
    }
}

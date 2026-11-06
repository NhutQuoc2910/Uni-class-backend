package vn.edu.uit.edulens.controller;

import java.util.Collections;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import vn.edu.uit.edulens.dto.response.ApiResponse;
import vn.edu.uit.edulens.dto.response.AssignmentItem;
import vn.edu.uit.edulens.dto.response.HomeSummaryResponse;
import vn.edu.uit.edulens.dto.response.SemesterScheduleResponse;
import vn.edu.uit.edulens.dto.response.TodayScheduleItem;
import vn.edu.uit.edulens.security.CustomUserDetails;
import vn.edu.uit.edulens.service.HomeService;
import vn.edu.uit.edulens.service.ScheduleService;

@Tag(name = "3. Home", description = "Các API dữ liệu trang chủ tổng quan")
@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;
    private final ScheduleService scheduleService;

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

    @Operation(summary = "Lấy toàn bộ danh sách bài tập cần chấm điểm của giảng viên")
    @GetMapping("/grading-assignments")
    public ResponseEntity<ApiResponse<List<AssignmentItem>>> getGradingAssignments(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.ok(ApiResponse.success(Collections.emptyList()));
        }

        List<AssignmentItem> items = homeService.getAllGradingAssignments(userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(items));
    }
   
    @Operation(summary = "Lấy lịch học hôm nay của người dùng (sinh viên hoặc giảng viên)")
    @GetMapping("/today-schedule")
    public ResponseEntity<ApiResponse<List<TodayScheduleItem>>>
    getTodaySchedule(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.ok(
                    ApiResponse.success(Collections.emptyList())
            );
        }

        List<TodayScheduleItem> schedules =
                scheduleService.getTodaySchedule(
                        userDetails.getId(),
                        userDetails.getUser().getRole()
                );

        return ResponseEntity.ok(ApiResponse.success(schedules));
    }

    @Operation(summary = "Lấy thời khóa biểu học kỳ và danh sách ngoại lệ lịch học")
    @GetMapping("/semester-schedule")
    public ResponseEntity<ApiResponse<SemesterScheduleResponse>> getSemesterSchedule(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.ok(ApiResponse.success(new SemesterScheduleResponse()));
        }
        SemesterScheduleResponse response = scheduleService.getSemesterSchedule(
                userDetails.getId(),
                userDetails.getUser().getRole()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

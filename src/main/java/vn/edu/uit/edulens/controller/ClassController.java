package vn.edu.uit.edulens.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.uit.edulens.dto.request.*;
import vn.edu.uit.edulens.dto.response.*;
import vn.edu.uit.edulens.security.CustomUserDetails;
import vn.edu.uit.edulens.service.AssignmentService;
import vn.edu.uit.edulens.service.AttendanceService;
import vn.edu.uit.edulens.service.ClassService;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Tag(name = "4. Classes", description = "Các API danh sách lớp học, tạo/lưu trữ lớp, thành viên, tài liệu, thống kê và bảng điểm")
@RestController
@RequestMapping("/api/v1/classes")
@RequiredArgsConstructor
public class ClassController {

    private final ClassService classService;
    private final AssignmentService assignmentService;
    private final AttendanceService attendanceService;

    @Operation(summary = "Lấy danh sách lớp học của người dùng hiện tại")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CourseClassItem>>> getClasses(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.ok(ApiResponse.success(Collections.emptyList()));
        }
        List<CourseClassItem> classes = classService.getClasses(
                userDetails.getId(),
                userDetails.getUser().getRole()
        );
        return ResponseEntity.ok(ApiResponse.success(classes));
    }

    @Operation(summary = "Giảng viên tạo lớp học mới và sinh mã mời 7 ký tự")
    @PostMapping
    public ResponseEntity<ApiResponse<CourseClassItem>> createClass(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateClassRequest request
    ) {
        CourseClassItem created = classService.createClass(userDetails.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Tạo lớp học mới thành công", created));
    }

    @Operation(summary = "Giảng viên lưu trữ lớp học cuối kỳ")
    @PutMapping("/{classId}/archive")
    public ResponseEntity<ApiResponse<CourseClassItem>> archiveClass(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID classId
    ) {
        UUID lecturerId = userDetails != null ? userDetails.getId() : null;
        CourseClassItem archived = classService.archiveClass(classId, lecturerId);
        return ResponseEntity.ok(ApiResponse.success("Đã lưu trữ lớp học thành công", archived));
    }

    @Operation(summary = "Tham gia lớp học bằng mã mời (inviteCode)")
    @PostMapping("/join")
    public ResponseEntity<ApiResponse<CourseClassItem>> joinClass(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody JoinClassRequest request
    ) {
        CourseClassItem joined = classService.joinClass(userDetails.getId(), request.getInviteCode());
        return ResponseEntity.ok(ApiResponse.success("Tham gia lớp học thành công", joined));
    }

    @Operation(summary = "Lấy chi tiết lớp học (Thông báo, Tài liệu, Bài tập, Điểm danh)")
    @GetMapping("/{classId}")
    public ResponseEntity<ApiResponse<ClassDetailResponse>> getClassDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID classId
    ) {
        ClassDetailResponse detail = classService.getClassDetail(
                classId,
                userDetails.getId(),
                userDetails.getUser().getRole()
        );
        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    @Operation(summary = "Lấy danh sách sinh viên trong lớp kèm thống kê chuyên cần và bài tập")
    @GetMapping("/{classId}/members")
    public ResponseEntity<ApiResponse<List<ClassMemberItemResponse>>> getClassMembers(
            @PathVariable UUID classId
    ) {
        List<ClassMemberItemResponse> members = classService.getClassMembers(classId);
        return ResponseEntity.ok(ApiResponse.success(members));
    }

    @Operation(summary = "Giảng viên xóa sinh viên khỏi lớp học")
    @DeleteMapping("/{classId}/members/{studentId}")
    public ResponseEntity<ApiResponse<Void>> removeClassMember(
            @PathVariable UUID classId,
            @PathVariable UUID studentId
    ) {
        classService.removeClassMember(classId, studentId);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa sinh viên khỏi lớp học", null));
    }

    @Operation(summary = "Giảng viên đăng tải tài liệu hoặc thông báo mới cho lớp học")
    @PostMapping("/{classId}/materials")
    public ResponseEntity<ApiResponse<ClassDetailResponse.MaterialItem>> createMaterial(
            @PathVariable UUID classId,
            @Valid @RequestBody CreateMaterialRequest request
    ) {
        ClassDetailResponse.MaterialItem created = classService.createMaterial(classId, request);
        return ResponseEntity.ok(ApiResponse.success("Đăng tải tài liệu thành công", created));
    }

    @Operation(summary = "Giảng viên tạo bài tập mới cho lớp học")
    @PostMapping("/{classId}/assignments")
    public ResponseEntity<ApiResponse<ClassDetailResponse.ClassAssignmentItem>> createAssignment(
            @PathVariable UUID classId,
            @Valid @RequestBody CreateAssignmentRequest request
    ) {
        ClassDetailResponse.ClassAssignmentItem created = assignmentService.createAssignment(classId, request);
        return ResponseEntity.ok(ApiResponse.success("Tạo bài tập mới thành công", created));
    }

    @Operation(summary = "Giảng viên mở phiên điểm danh QR động cho lớp học")
    @PostMapping("/{classId}/attendance-sessions")
    public ResponseEntity<ApiResponse<LiveAttendanceSessionResponse>> openAttendanceSession(
            @PathVariable UUID classId,
            @RequestBody(required = false) OpenQrSessionRequest request
    ) {
        LiveAttendanceSessionResponse session = attendanceService.openSession(classId, request);
        return ResponseEntity.ok(ApiResponse.success("Mở phiên điểm danh thành công", session));
    }

    @Operation(summary = "Lấy thống kê cuối kỳ của lớp học (chuyên cần, cảnh báo vắng >20%, phổ điểm 6 cột)")
    @GetMapping("/{classId}/statistics")
    public ResponseEntity<ApiResponse<ClassStatisticsResponse>> getClassStatistics(
            @PathVariable UUID classId
    ) {
        ClassStatisticsResponse stats = classService.getClassStatistics(classId);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @Operation(summary = "Lấy bảng điểm trung bình và nhận xét giảng viên của lớp học")
    @GetMapping("/{classId}/grades")
    public ResponseEntity<ApiResponse<ClassGradesResponse>> getClassGrades(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID classId
    ) {
        UUID studentId = userDetails != null ? userDetails.getId() : null;
        ClassGradesResponse grades = classService.getClassGrades(classId, studentId);
        return ResponseEntity.ok(ApiResponse.success(grades));
    }
}

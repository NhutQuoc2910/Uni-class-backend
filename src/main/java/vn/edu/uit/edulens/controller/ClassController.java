package vn.edu.uit.edulens.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.uit.edulens.dto.request.JoinClassRequest;
import vn.edu.uit.edulens.dto.response.ApiResponse;
import vn.edu.uit.edulens.dto.response.ClassDetailResponse;
import vn.edu.uit.edulens.dto.response.CourseClassItem;
import vn.edu.uit.edulens.security.CustomUserDetails;
import vn.edu.uit.edulens.service.ClassService;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Tag(name = "4. Classes", description = "Các API danh sách lớp học, tham gia lớp và chi tiết lớp học")
@RestController
@RequestMapping("/api/v1/classes")
@RequiredArgsConstructor
public class ClassController {

    private final ClassService classService;

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
}

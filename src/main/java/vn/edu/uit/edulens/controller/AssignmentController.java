package vn.edu.uit.edulens.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.uit.edulens.dto.request.GradeSubmissionRequest;
import vn.edu.uit.edulens.dto.request.SubmitAssignmentRequest;
import vn.edu.uit.edulens.dto.response.ApiResponse;
import vn.edu.uit.edulens.dto.response.AssignmentDetailResponse;
import vn.edu.uit.edulens.dto.response.AssignmentSubmissionListResponse;
import vn.edu.uit.edulens.security.CustomUserDetails;
import vn.edu.uit.edulens.service.AssignmentService;

import java.util.UUID;

@Tag(name = "6. Assignments", description = "Các API chi tiết bài tập, nộp bài tập, danh sách bài nộp và chấm điểm")
@RestController
@RequestMapping("/api/v1/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;

    @Operation(summary = "Lấy chi tiết bài tập và trạng thái bài nộp của sinh viên hiện tại")
    @GetMapping("/{assignmentId}")
    public ResponseEntity<ApiResponse<AssignmentDetailResponse>> getAssignmentDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID assignmentId
    ) {
        UUID studentId = userDetails != null ? userDetails.getId() : null;
        AssignmentDetailResponse detail = assignmentService.getAssignmentDetail(assignmentId, studentId);
        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    @Operation(summary = "Sinh viên nộp hoặc nộp lại bài tập")
    @PostMapping("/{assignmentId}/submit")
    public ResponseEntity<ApiResponse<AssignmentDetailResponse>> submitAssignment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID assignmentId,
            @Valid @RequestBody SubmitAssignmentRequest request
    ) {
        AssignmentDetailResponse submitted = assignmentService.submitAssignment(
                assignmentId,
                userDetails.getId(),
                request
        );
        return ResponseEntity.ok(ApiResponse.success("Nộp bài tập thành công", submitted));
    }

    @PreAuthorize("hasAuthority('ROLE_LECTURER')")
    @Operation(summary = "Giảng viên lấy danh sách bài nộp của bài tập kèm thống kê tiến độ")
    @GetMapping("/{assignmentId}/submissions")
    public ResponseEntity<ApiResponse<AssignmentSubmissionListResponse>> getAssignmentSubmissions(
            @PathVariable UUID assignmentId
    ) {
        AssignmentSubmissionListResponse submissions = assignmentService.getAssignmentSubmissions(assignmentId);
        return ResponseEntity.ok(ApiResponse.success(submissions));
    }

    @PreAuthorize("hasAuthority('ROLE_LECTURER')")
    @Operation(summary = "Giảng viên chấm điểm và nhận xét bài nộp của sinh viên")
    @PutMapping("/{assignmentId}/submissions/{studentId}/grade")
    public ResponseEntity<ApiResponse<AssignmentSubmissionListResponse>> gradeSubmission(
            @PathVariable UUID assignmentId,
            @PathVariable UUID studentId,
            @Valid @RequestBody GradeSubmissionRequest request
    ) {
        AssignmentSubmissionListResponse updated = assignmentService.gradeSubmission(assignmentId, studentId, request);
        return ResponseEntity.ok(ApiResponse.success("Chấm điểm bài nộp thành công", updated));
    }
}

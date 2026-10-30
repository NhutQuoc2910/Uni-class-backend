package vn.edu.uit.edulens.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.uit.edulens.dto.response.ApiResponse;
import vn.edu.uit.edulens.dto.response.FileUploadResponse;
import vn.edu.uit.edulens.security.CustomUserDetails;
import vn.edu.uit.edulens.service.StorageService;

import java.util.UUID;

@Tag(name = "Storage", description = "Các API lưu trữ, tải lên và truy xuất tệp tài liệu, bài tập")
@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;

    @Operation(summary = "Tải tệp lên hệ thống (Supabase Storage hoặc Local Storage)")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<FileUploadResponse>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", required = false, defaultValue = "materials") String folder,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        UUID uploaderId = userDetails != null ? userDetails.getId() : null;
        FileUploadResponse response = storageService.uploadFile(file, folder, uploaderId);
        return ResponseEntity.ok(ApiResponse.success("Tải tệp lên thành công", response));
    }

    @Operation(summary = "Truy xuất hoặc tải tệp tài liệu từ hệ thống lưu trữ")
    @GetMapping("/files/{folder}/{fileName:.+}")
    public ResponseEntity<Resource> getFile(
            @PathVariable String folder,
            @PathVariable String fileName
    ) {
        Resource resource = storageService.loadFileAsResource(folder, fileName);
        MediaType mediaType = MediaTypeFactory.getMediaType(resource)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }
}

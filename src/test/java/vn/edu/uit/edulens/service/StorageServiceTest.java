package vn.edu.uit.edulens.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import vn.edu.uit.edulens.dto.response.FileUploadResponse;
import vn.edu.uit.edulens.exception.AppException;
import vn.edu.uit.edulens.exception.ErrorCode;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StorageServiceTest {

    private StorageService storageService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        storageService = new StorageService();
        storageService.setLocalDir(tempDir.toString());
        storageService.setSupabaseBucket("uniclass-files");
    }

    @Test
    @DisplayName("uploadFile: Lưu trữ local thành công khi không cấu hình Supabase")
    void uploadFile_LocalFallback_Success() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "lecture-notes.pdf",
                "application/pdf",
                "Sample PDF content".getBytes()
        );

        UUID uploaderId = UUID.randomUUID();
        FileUploadResponse response = storageService.uploadFile(file, "materials", uploaderId);

        assertNotNull(response);
        assertNotNull(response.getFileName());
        assertTrue(response.getFileName().endsWith(".pdf"));
        assertTrue(response.getFileUrl().startsWith("/api/v1/storage/files/materials/"));
        assertEquals("application/pdf", response.getFileType());
        assertEquals("Sample PDF content".length(), response.getFileSize());

        // Test loading back the saved file
        Resource resource = storageService.loadFileAsResource("materials", response.getFileName());
        assertTrue(resource.exists());
        assertTrue(resource.isReadable());
    }

    @Test
    @DisplayName("uploadFile: Upload thành công lên Supabase Storage khi có cấu hình")
    void uploadFile_Supabase_Success() throws Exception {
        storageService.setSupabaseUrl("https://example.supabase.co");
        storageService.setSupabaseKey("secret-service-role-key");

        HttpClient mockHttpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = (HttpResponse<String>) mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"Key\":\"materials/file.png\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        storageService.setHttpClient(mockHttpClient);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "diagram.png",
                "image/png",
                new byte[]{1, 2, 3, 4}
        );

        FileUploadResponse response = storageService.uploadFile(file, "materials", UUID.randomUUID());

        assertNotNull(response);
        assertTrue(response.getFileUrl().startsWith("https://example.supabase.co/storage/v1/object/public/uniclass-files/materials/"));
        assertEquals("image/png", response.getFileType());
        assertEquals(4, response.getFileSize());
    }

    @Test
    @DisplayName("uploadFile: Supabase Storage trả về lỗi HTTP ném FILE_UPLOAD_FAILED")
    void uploadFile_SupabaseFailure_ThrowsFileUploadFailed() throws Exception {
        storageService.setSupabaseUrl("https://example.supabase.co");
        storageService.setSupabaseKey("secret-service-role-key");

        HttpClient mockHttpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResponse = (HttpResponse<String>) mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(500);
        when(mockResponse.body()).thenReturn("Internal Server Error");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        storageService.setHttpClient(mockHttpClient);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "diagram.png",
                "image/png",
                new byte[]{1, 2, 3, 4}
        );

        AppException ex = assertThrows(AppException.class, () ->
                storageService.uploadFile(file, "materials", UUID.randomUUID()));
        assertEquals(ErrorCode.FILE_UPLOAD_FAILED, ex.getErrorCode());
    }

    @Test
    @DisplayName("uploadFile: Tệp trống hoặc null ném INVALID_INPUT")
    void uploadFile_EmptyFile_ThrowsInvalidInput() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.pdf",
                "application/pdf",
                new byte[0]
        );

        AppException ex = assertThrows(AppException.class, () ->
                storageService.uploadFile(emptyFile, "materials", UUID.randomUUID()));
        assertEquals(ErrorCode.INVALID_INPUT, ex.getErrorCode());

        AppException exNull = assertThrows(AppException.class, () ->
                storageService.uploadFile(null, "materials", UUID.randomUUID()));
        assertEquals(ErrorCode.INVALID_INPUT, exNull.getErrorCode());
    }

    @Test
    @DisplayName("uploadFile: Định dạng tệp không được hỗ trợ (.exe, .sh) ném INVALID_FILE_TYPE")
    void uploadFile_InvalidExtension_ThrowsInvalidFileType() {
        MockMultipartFile exeFile = new MockMultipartFile(
                "file",
                "trojan.exe",
                "application/x-msdownload",
                "danger".getBytes()
        );

        AppException ex = assertThrows(AppException.class, () ->
                storageService.uploadFile(exeFile, "materials", UUID.randomUUID()));
        assertEquals(ErrorCode.INVALID_FILE_TYPE, ex.getErrorCode());

        MockMultipartFile shFile = new MockMultipartFile(
                "file",
                "script.sh",
                "application/x-sh",
                "echo hello".getBytes()
        );

        AppException exSh = assertThrows(AppException.class, () ->
                storageService.uploadFile(shFile, "materials", UUID.randomUUID()));
        assertEquals(ErrorCode.INVALID_FILE_TYPE, exSh.getErrorCode());
    }

    @Test
    @DisplayName("uploadFile: Kích thước tệp vượt quá 25MB ném FILE_TOO_LARGE")
    void uploadFile_TooLarge_ThrowsFileTooLarge() {
        MockMultipartFile largeFile = new MockMultipartFile(
                "file",
                "large-doc.pdf",
                "application/pdf",
                new byte[1]
        ) {
            @Override
            public long getSize() {
                return 26L * 1024 * 1024;
            }
        };

        AppException ex = assertThrows(AppException.class, () ->
                storageService.uploadFile(largeFile, "materials", UUID.randomUUID()));
        assertEquals(ErrorCode.FILE_TOO_LARGE, ex.getErrorCode());
    }

    @Test
    @DisplayName("loadFileAsResource: Ngăn chặn tấn công Path Traversal")
    void loadFileAsResource_PathTraversal_ThrowsInvalidInput() {
        AppException ex = assertThrows(AppException.class, () ->
                storageService.loadFileAsResource("materials", "../../../etc/passwd"));
        assertEquals(ErrorCode.INVALID_INPUT, ex.getErrorCode());
    }

    @Test
    @DisplayName("loadFileAsResource: Tệp không tồn tại ném FILE_UPLOAD_FAILED")
    void loadFileAsResource_NotFound_ThrowsFileUploadFailed() {
        AppException ex = assertThrows(AppException.class, () ->
                storageService.loadFileAsResource("materials", "non-existent-file.pdf"));
        assertEquals(ErrorCode.FILE_UPLOAD_FAILED, ex.getErrorCode());
    }
}

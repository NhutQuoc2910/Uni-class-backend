package vn.edu.uit.edulens.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.uit.edulens.config.SecurityConfig;
import vn.edu.uit.edulens.dto.response.FileUploadResponse;
import vn.edu.uit.edulens.entity.User;
import vn.edu.uit.edulens.entity.enums.Role;
import vn.edu.uit.edulens.entity.enums.UserStatus;
import vn.edu.uit.edulens.security.CustomUserDetails;
import vn.edu.uit.edulens.security.CustomUserDetailsService;
import vn.edu.uit.edulens.security.JwtAuthenticationFilter;
import vn.edu.uit.edulens.security.JwtTokenProvider;
import vn.edu.uit.edulens.service.StorageService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = StorageController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@ActiveProfiles("test")
public class StorageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StorageService storageService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("GET /api/v1/storage/files/{folder}/{fileName} là permitAll, cho phép tải không cần xác thực")
    void getFile_PublicAccess_Success() throws Exception {
        byte[] content = "test pdf content".getBytes();
        Resource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "sample.pdf";
            }
        };

        when(storageService.loadFileAsResource(eq("materials"), eq("sample.pdf")))
                .thenReturn(resource);

        mockMvc.perform(get("/api/v1/storage/files/materials/sample.pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "inline; filename=\"sample.pdf\""))
                .andExpect(content().bytes(content));
    }

    @Test
    @DisplayName("POST /api/v1/storage/upload chưa đăng nhập trả về 401 Unauthorized")
    void uploadFile_Unauthenticated_ReturnsUnauthorized() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.pdf",
                "application/pdf",
                "test content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/storage/upload").file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    @DisplayName("POST /api/v1/storage/upload đã đăng nhập trả về 200 kèm FileUploadResponse")
    void uploadFile_Authenticated_Success() throws Exception {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("lecturer@uit.edu.vn")
                .role(Role.ROLE_LECTURER)
                .status(UserStatus.ACTIVE)
                .build();
        CustomUserDetails userDetails = new CustomUserDetails(user);

        FileUploadResponse uploadResponse = FileUploadResponse.builder()
                .fileName("abc-test.pdf")
                .fileUrl("/api/v1/storage/files/materials/abc-test.pdf")
                .fileType("application/pdf")
                .fileSize(12)
                .build();

        when(storageService.uploadFile(any(), eq("materials"), eq(userDetails.getId())))
                .thenReturn(uploadResponse);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.pdf",
                "application/pdf",
                "test content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/storage/upload")
                        .file(file)
                        .param("folder", "materials")
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fileName").value("abc-test.pdf"))
                .andExpect(jsonPath("$.data.fileUrl").value("/api/v1/storage/files/materials/abc-test.pdf"))
                .andExpect(jsonPath("$.data.fileType").value("application/pdf"))
                .andExpect(jsonPath("$.data.fileSize").value(12));
    }
}

package vn.edu.uit.edulens.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.uit.edulens.dto.response.FileUploadResponse;
import vn.edu.uit.edulens.exception.AppException;
import vn.edu.uit.edulens.exception.ErrorCode;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private static final long MAX_FILE_SIZE = 25L * 1024 * 1024; // 25MB

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/zip",
            "application/x-zip-compressed",
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "zip", "jpg", "jpeg", "png", "webp"
    );

    @Value("${storage.supabase.url:}")
    private String supabaseUrl;

    @Value("${storage.supabase.key:}")
    private String supabaseKey;

    @Value("${storage.supabase.bucket:uniclass-files}")
    private String supabaseBucket = "uniclass-files";

    @Value("${storage.local-dir:uploads}")
    private String localDir = "uploads";

    private HttpClient httpClient = HttpClient.newHttpClient();

    public void setSupabaseUrl(String supabaseUrl) {
        this.supabaseUrl = supabaseUrl;
    }

    public void setSupabaseKey(String supabaseKey) {
        this.supabaseKey = supabaseKey;
    }

    public void setSupabaseBucket(String supabaseBucket) {
        this.supabaseBucket = supabaseBucket;
    }

    public void setLocalDir(String localDir) {
        this.localDir = localDir;
    }

    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public FileUploadResponse uploadFile(MultipartFile file, String folder, UUID uploaderId) {
        validateFile(file);

        String cleanFolder = sanitizeFolder(folder);
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        String mimeType = determineMimeType(file, extension);
        String uniqueFileName = generateUniqueFileName(originalFilename);

        log.info("Uploading file '{}' (size: {} bytes, mime: {}) by uploader: {}",
                originalFilename, file.getSize(), mimeType, uploaderId);

        if (isSupabaseConfigured()) {
            return uploadToSupabase(file, cleanFolder, uniqueFileName, mimeType);
        } else {
            return uploadToLocalStorage(file, cleanFolder, uniqueFileName, mimeType);
        }
    }

    public Resource loadFileAsResource(String folder, String fileName) {
        try {
            String cleanFolder = sanitizeFolder(folder);
            if (fileName == null || fileName.isBlank() || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                throw new AppException(ErrorCode.INVALID_INPUT, "Tên tệp không hợp lệ");
            }

            Path basePath = Paths.get(localDir, cleanFolder).toAbsolutePath().normalize();
            Path filePath = basePath.resolve(fileName).normalize();

            if (!filePath.startsWith(basePath)) {
                throw new AppException(ErrorCode.INVALID_INPUT, "Đường dẫn không hợp lệ");
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new AppException(ErrorCode.FILE_UPLOAD_FAILED, "Không tìm thấy tệp hoặc không thể đọc tệp");
            }
        } catch (MalformedURLException e) {
            log.error("Error loading file resource", e);
            throw new AppException(ErrorCode.FILE_UPLOAD_FAILED, "Lỗi đọc tệp");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_INPUT, "Tệp tải lên không được để trống");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }

        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);

        if (extension != null && !extension.isBlank() && !ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new AppException(ErrorCode.INVALID_FILE_TYPE);
        }

        String contentType = file.getContentType();
        boolean isMimeAllowed = false;
        if (contentType != null && !contentType.isBlank() && !contentType.equalsIgnoreCase("application/octet-stream")) {
            String cleanMime = contentType.toLowerCase().trim();
            if (ALLOWED_MIME_TYPES.contains(cleanMime)
                    || cleanMime.startsWith("application/vnd.openxmlformats-officedocument.")) {
                isMimeAllowed = true;
            } else {
                throw new AppException(ErrorCode.INVALID_FILE_TYPE);
            }
        }

        boolean isExtensionAllowed = extension != null && ALLOWED_EXTENSIONS.contains(extension.toLowerCase());

        if (!isMimeAllowed && !isExtensionAllowed) {
            throw new AppException(ErrorCode.INVALID_FILE_TYPE);
        }
    }

    private boolean isSupabaseConfigured() {
        return supabaseUrl != null && !supabaseUrl.isBlank()
                && supabaseKey != null && !supabaseKey.isBlank();
    }

    private FileUploadResponse uploadToSupabase(MultipartFile file, String folder, String uniqueFileName, String mimeType) {
        String base = supabaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String targetUrl = String.format("%s/storage/v1/object/%s/%s/%s", base, supabaseBucket.trim(), folder, uniqueFileName);

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("Authorization", "Bearer " + supabaseKey.trim())
                    .header("Content-Type", mimeType)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(file.getBytes()))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                log.error("Supabase Storage upload failed with status code {}: {}", resp.statusCode(), resp.body());
                throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
            }

            String publicUrl = String.format("%s/storage/v1/object/public/%s/%s/%s", base, supabaseBucket.trim(), folder, uniqueFileName);
            return FileUploadResponse.builder()
                    .fileName(uniqueFileName)
                    .fileUrl(publicUrl)
                    .fileType(mimeType)
                    .fileSize(file.getSize())
                    .build();
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Exception during Supabase storage upload", e);
            throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    private FileUploadResponse uploadToLocalStorage(MultipartFile file, String folder, String uniqueFileName, String mimeType) {
        try {
            Path folderPath = Paths.get(localDir, folder).toAbsolutePath().normalize();
            Files.createDirectories(folderPath);
            Path targetPath = folderPath.resolve(uniqueFileName).normalize();
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            String fileUrl = String.format("/api/v1/storage/files/%s/%s", folder, uniqueFileName);
            return FileUploadResponse.builder()
                    .fileName(uniqueFileName)
                    .fileUrl(fileUrl)
                    .fileType(mimeType)
                    .fileSize(file.getSize())
                    .build();
        } catch (IOException e) {
            log.error("Failed to store file to local directory", e);
            throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    private String sanitizeFolder(String folder) {
        if (folder == null || folder.isBlank()) {
            return "materials";
        }
        String sanitized = folder.replaceAll("[^a-zA-Z0-9_-]", "");
        return sanitized.isEmpty() ? "materials" : sanitized;
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex >= 0 && lastDotIndex < filename.length() - 1) {
            return filename.substring(lastDotIndex + 1);
        }
        return null;
    }

    private String generateUniqueFileName(String originalFilename) {
        String baseName = "file";
        String extension = "";
        if (originalFilename != null && !originalFilename.isBlank()) {
            Path p = Paths.get(originalFilename).getFileName();
            String name = p != null ? p.toString() : originalFilename;
            int lastDot = name.lastIndexOf('.');
            if (lastDot >= 0) {
                baseName = name.substring(0, lastDot);
                extension = name.substring(lastDot);
            } else {
                baseName = name;
            }
        }
        String sanitizedBase = baseName.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (sanitizedBase.length() > 50) {
            sanitizedBase = sanitizedBase.substring(0, 50);
        }
        return UUID.randomUUID() + "-" + sanitizedBase + extension;
    }

    private String determineMimeType(MultipartFile file, String extension) {
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank() && !contentType.equalsIgnoreCase("application/octet-stream")) {
            return contentType.trim().toLowerCase();
        }
        if (extension != null) {
            switch (extension.toLowerCase()) {
                case "pdf":
                    return "application/pdf";
                case "doc":
                    return "application/msword";
                case "docx":
                    return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                case "xls":
                    return "application/vnd.ms-excel";
                case "xlsx":
                    return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                case "ppt":
                    return "application/vnd.ms-powerpoint";
                case "pptx":
                    return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
                case "zip":
                    return "application/zip";
                case "jpg":
                case "jpeg":
                    return "image/jpeg";
                case "png":
                    return "image/png";
                case "webp":
                    return "image/webp";
                default:
                    break;
            }
        }
        return "application/octet-stream";
    }
}

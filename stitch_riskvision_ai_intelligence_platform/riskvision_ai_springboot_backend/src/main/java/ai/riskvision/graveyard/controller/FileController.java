package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.aspect.Auditable;
import ai.riskvision.graveyard.dto.common.ApiResponse;
import ai.riskvision.graveyard.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping("/upload")
    @Auditable(action = "FILE_UPLOAD", module = "FILE", severity = "MEDIUM")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", defaultValue = "general") String category) {
        String fileRef = fileService.storeFile(file, category);
        return ResponseEntity.ok(ApiResponse.success("File uploaded successfully", Map.of("fileRef", fileRef, "fileName", file.getOriginalFilename())));
    }

    @GetMapping("/download/{category}/{fileName:.+}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String category,
            @PathVariable String fileName) {
        Resource resource = fileService.loadFileAsResource(category, fileName);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    @DeleteMapping("/{category}/{fileName:.+}")
    @Auditable(action = "FILE_DELETE", module = "FILE", severity = "HIGH")
    public ResponseEntity<ApiResponse<Void>> deleteFile(
            @PathVariable String category,
            @PathVariable String fileName) {
        boolean deleted = fileService.deleteFile(category, fileName);
        if (deleted) {
            return ResponseEntity.ok(ApiResponse.success("File deleted successfully", null));
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.error("400", "File deletion failed or file not found"));
        }
    }
}

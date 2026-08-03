package ai.riskvision.graveyard.controller;

import ai.riskvision.graveyard.aspect.Auditable;
import ai.riskvision.graveyard.service.ReportGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportGenerationService reportGenerationService;
    private final RestTemplate restTemplate;

    @Value("${ml.service.url:http://localhost:5000}")
    private String mlServiceBaseUrl;

    @GetMapping("/executive/json")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'ANALYST')")
    @Auditable(action = "REPORT_EXPORT_EXECUTIVE", module = "REPORT", severity = "LOW")
    public ResponseEntity<byte[]> exportExecutiveSummaryJson() {
        byte[] data = reportGenerationService.generateExecutiveSummaryJson();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=executive_summary.json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(data);
    }

    @GetMapping("/projects/csv")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'ANALYST')")
    @Auditable(action = "REPORT_EXPORT_PROJECTS", module = "REPORT", severity = "LOW")
    public ResponseEntity<byte[]> exportProjectsCsv() {
        byte[] data = reportGenerationService.generateProjectsCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=projects_report.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(data);
    }

    @GetMapping("/export/zip")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Auditable(action = "REPORT_EXPORT_ZIP", module = "REPORT", severity = "MEDIUM")
    public ResponseEntity<byte[]> exportZipPackage() {
        byte[] data = reportGenerationService.generateZipPackage();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=riskvision_reports_bundle.zip")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(data);
    }

    @GetMapping("/download/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'ANALYST')")
    public ResponseEntity<byte[]> downloadPdf(
            @RequestParam(value = "prediction_id", required = false) String predictionId,
            @RequestParam(value = "project_id", required = false) String projectId) {
        String url = mlServiceBaseUrl + "/api/v1/reports/download/pdf?";
        if (predictionId != null) url += "prediction_id=" + predictionId;
        if (projectId != null) url += (predictionId != null ? "&" : "") + "project_id=" + projectId;

        try {
            ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            String contentDisposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
            if (contentDisposition != null) {
                headers.set(HttpHeaders.CONTENT_DISPOSITION, contentDisposition);
            } else {
                headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=risk_report.pdf");
            }
            return new ResponseEntity<>(response.getBody(), headers, HttpStatus.OK);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "FastAPI ML report service unreachable", e);
        }
    }

    @GetMapping("/download/excel")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'ANALYST')")
    public ResponseEntity<byte[]> downloadExcel(
            @RequestParam(value = "prediction_id", required = false) String predictionId,
            @RequestParam(value = "project_id", required = false) String projectId) {
        String url = mlServiceBaseUrl + "/api/v1/reports/download/excel?";
        if (predictionId != null) url += "prediction_id=" + predictionId;
        if (projectId != null) url += (predictionId != null ? "&" : "") + "project_id=" + projectId;

        try {
            ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            String contentDisposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
            if (contentDisposition != null) {
                headers.set(HttpHeaders.CONTENT_DISPOSITION, contentDisposition);
            } else {
                headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=risk_report.xlsx");
            }
            return new ResponseEntity<>(response.getBody(), headers, HttpStatus.OK);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "FastAPI ML report service unreachable", e);
        }
    }
}

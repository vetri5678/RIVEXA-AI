package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.entity.RepositoryEntity;
import ai.riskvision.graveyard.entity.RepositoryPredictionEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Generates Excel (.xlsx) risk assessment spreadsheets natively in Spring Boot using Apache POI.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelReportService {

    private final ObjectMapper objectMapper;

    public byte[] generatePredictionExcel(RepositoryPredictionEntity prediction, RepositoryEntity repo) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            // Styles
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setFont(headerFont);

            CellStyle boldStyle = workbook.createCellStyle();
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            boldStyle.setFont(boldFont);

            // ── Sheet 1: Executive Summary ───────────────────────────────────
            Sheet summarySheet = workbook.createSheet("Executive Summary");
            int rowNum = 0;

            Row titleRow = summarySheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("RiskVision AI - Project Risk Assessment Report");
            titleCell.setCellStyle(headerStyle);

            summarySheet.createRow(rowNum++); // Blank

            String repoName = repo != null ? safe(repo.getRepositoryName()) : "—";
            String repoUrl = repo != null ? safe(repo.getRepositoryUrl()) : "—";
            String ownerOrg = repo != null ? safe(repo.getOwner(), safe(repo.getOrganization(), "—")) : "—";
            String lang = repo != null ? safe(repo.getLanguage(), "—") : "—";
            String repoIdStr = repo != null && repo.getId() != null ? repo.getId().toString() : "—";

            String predDate = prediction != null && prediction.getCreatedAt() != null
                    ? prediction.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    : LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String riskLevel = prediction != null ? safe(prediction.getRiskLevel(), "UNKNOWN") : "UNKNOWN";
            double fp = prediction != null && prediction.getFailureProbability() != null ? prediction.getFailureProbability() : 0.0;
            int rs = prediction != null && prediction.getRiskScore() != null ? prediction.getRiskScore() : 0;
            double conf = prediction != null && prediction.getConfidence() != null ? prediction.getConfidence() : 0.0;
            double hs = prediction != null && prediction.getHealthScore() != null ? prediction.getHealthScore() : 0.0;
            String modelVer = prediction != null ? safe(prediction.getModelVersion(), "xgboost-v2.x") : "xgboost-v2.x";
            String predIdStr = prediction != null && prediction.getId() != null ? prediction.getId().toString() : "—";

            String[][] summaryData = {
                {"Repository Name", repoName},
                {"Repository URL", repoUrl},
                {"Owner / Organization", ownerOrg},
                {"Primary Language", lang},
                {"Prediction Date", predDate},
                {"Risk Level", riskLevel},
                {"Failure Probability", String.format("%.2f%%", fp * 100)},
                {"Risk Score", String.valueOf(rs)},
                {"AI Confidence", String.format("%.2f%%", conf * 100)},
                {"Health Score", String.format("%.2f", hs)},
                {"Model Version", modelVer},
                {"Prediction ID", predIdStr},
                {"Repository ID", repoIdStr}
            };

            for (String[] pair : summaryData) {
                Row row = summarySheet.createRow(rowNum++);
                Cell c0 = row.createCell(0);
                c0.setCellValue(pair[0]);
                c0.setCellStyle(boldStyle);
                row.createCell(1).setCellValue(pair[1]);
            }

            try {
                summarySheet.autoSizeColumn(0);
                summarySheet.autoSizeColumn(1);
            } catch (Exception e) {
                log.debug("[ExcelReportService] autoSizeColumn skipped: {}", e.getMessage());
            }

            // ── Sheet 2: SHAP Feature Importance ─────────────────────────────
            Sheet shapSheet = workbook.createSheet("SHAP Feature Importance");
            int shapRowNum = 0;

            Row shapHeader = shapSheet.createRow(shapRowNum++);
            String[] shapCols = {"Feature Name", "SHAP Impact Value", "Direction", "Impact Description"};
            for (int c = 0; c < shapCols.length; c++) {
                Cell cell = shapHeader.createCell(c);
                cell.setCellValue(shapCols[c]);
                cell.setCellStyle(headerStyle);
            }

            List<Map<String, Object>> shapFeatures = prediction != null 
                    ? parseShapFeatures(prediction.getFeatureImportanceJson())
                    : Collections.emptyList();
            for (Map<String, Object> f : shapFeatures) {
                Row row = shapSheet.createRow(shapRowNum++);
                String name = safe(f.get("display_name"), f.get("feature_name"), f.get("feature"), "Feature");
                double impact = toDouble(f.get("impact"), f.get("avg_impact"));
                String dir = safe(f.get("direction"), "").toLowerCase();
                boolean increases = dir.contains("increase");

                row.createCell(0).setCellValue(name.replace("_", " "));
                row.createCell(1).setCellValue(impact);
                row.createCell(2).setCellValue(increases ? "INCREASING" : "DECREASING");
                row.createCell(3).setCellValue(increases ? "Increases Project Risk" : "Reduces Project Risk");
            }

            try {
                for (int c = 0; c < shapCols.length; c++) {
                    shapSheet.autoSizeColumn(c);
                }
            } catch (Exception e) {
                log.debug("[ExcelReportService] autoSizeColumn skipped for SHAP sheet: {}", e.getMessage());
            }

            workbook.write(baos);
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("[ExcelReportService] Excel generation failed for repo '{}': {}",
                    repo.getRepositoryName(), e.getMessage(), e);
            throw new RuntimeException("Excel report generation failed: " + e.getMessage(), e);
        }
    }

    private List<Map<String, Object>> parseShapFeatures(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private double toDouble(Object... candidates) {
        for (Object c : candidates) {
            if (c instanceof Number n) return n.doubleValue();
            if (c instanceof String s) {
                try { return Double.parseDouble(s); } catch (Exception ignored) {}
            }
        }
        return 0.0;
    }

    private String safe(Object... candidates) {
        for (Object c : candidates) {
            if (c instanceof String s && !s.isBlank()) return s;
        }
        return "—";
    }
}

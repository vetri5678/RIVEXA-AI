package ai.riskvision.graveyard.service;

import ai.riskvision.graveyard.client.OpenRouterClient;
import ai.riskvision.graveyard.entity.RepositoryEntity;
import ai.riskvision.graveyard.entity.RepositoryPredictionEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * Generates professional PDF risk-assessment reports entirely within Spring Boot
 * using Apache PDFBox — no external FastAPI service required.
 *
 * <p>Report sections:
 * <ol>
 *   <li>Executive Risk Summary</li>
 *   <li>Risk Assessment + gauge visualization</li>
 *   <li>Repository Metrics (actual XGBoost features)</li>
 *   <li>SHAP Feature Importance table</li>
 *   <li>AI-generated Recommendations (via OpenRouter)</li>
 *   <li>Recommended Action Plan</li>
 *   <li>Model Information</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PdfReportService {

    private final OpenRouterClient openRouterClient;
    private final ObjectMapper objectMapper;

    // ── Colours ──────────────────────────────────────────────────────────────
    private static final Color DARK_BG     = new Color(0x05, 0x08, 0x16);
    private static final Color HEADER_BG   = new Color(0x0B, 0x12, 0x20);
    private static final Color ACCENT_CYAN = new Color(0x00, 0xD4, 0xFF);
    private static final Color ACCENT_BLUE = new Color(0x38, 0x82, 0xF6);
    private static final Color TEXT_PRIMARY = new Color(0xF8, 0xFA, 0xFC);
    private static final Color TEXT_MUTED  = new Color(0x94, 0xA3, 0xB8);
    private static final Color CRITICAL_RED = new Color(0xEF, 0x44, 0x44);
    private static final Color HIGH_ORANGE  = new Color(0xF9, 0x73, 0x16);
    private static final Color MEDIUM_AMBER = new Color(0xF5, 0x9E, 0x0B);
    private static final Color LOW_GREEN    = new Color(0x10, 0xB9, 0x81);
    private static final Color ROW_ALT      = new Color(0x11, 0x18, 0x27);
    private static final Color BORDER_COLOR = new Color(0x1E, 0x29, 0x3B);

    // ── Page layout constants ─────────────────────────────────────────────────
    private static final float PAGE_WIDTH  = PDRectangle.A4.getWidth();   // 595
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();  // 842
    private static final float MARGIN_L    = 45f;
    private static final float MARGIN_R    = 45f;
    private static final float CONTENT_W   = PAGE_WIDTH - MARGIN_L - MARGIN_R;

    // ── Fonts (Standard Type-1 — always available in PDFBox) ─────────────────
    private static final PDType1Font FONT_BOLD   = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDType1Font FONT_NORMAL = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font FONT_OBLIQUE = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Generates a complete PDF report for the supplied prediction and repository.
     *
     * @param prediction the XGBoost prediction record
     * @param repo       the associated repository
     * @return raw PDF bytes
     */
    public byte[] generatePredictionPdf(RepositoryPredictionEntity prediction, RepositoryEntity repo) {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            // Parse SHAP features from featureImportanceJson
            List<Map<String, Object>> shapFeatures = parseShapFeatures(prediction.getFeatureImportanceJson());

            // Parse existing rich recommendations or generate fresh ones via AI
            String aiRecommendationsText = resolveAiRecommendations(prediction, repo, shapFeatures);

            // ── Page 1: Executive Summary + Risk Assessment ───────────────────
            PDPage page1 = new PDPage(PDRectangle.A4);
            doc.addPage(page1);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page1)) {
                float y = PAGE_HEIGHT - 30f;
                y = drawPageHeader(cs, repo, prediction, y);
                y = drawExecutiveSummary(cs, prediction, repo, y);
                y = drawRiskGauge(cs, prediction, y);
                y = drawRepositoryMetrics(cs, repo, prediction, y);
            }

            // ── Page 2: SHAP + AI Recommendations ───────────────────────────
            PDPage page2 = new PDPage(PDRectangle.A4);
            doc.addPage(page2);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page2)) {
                float y = PAGE_HEIGHT - 30f;
                y = drawPage2Header(cs, repo, y);
                y = drawShapTable(cs, shapFeatures, y);
                y = drawAiRecommendations(cs, aiRecommendationsText, prediction, y);
            }

            // ── Page 3: Action Plan + Model Info ────────────────────────────
            PDPage page3 = new PDPage(PDRectangle.A4);
            doc.addPage(page3);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page3)) {
                float y = PAGE_HEIGHT - 30f;
                y = drawPage3Header(cs, repo, y);
                y = drawActionPlan(cs, prediction, repo, shapFeatures, y);
                y = drawModelInfo(cs, prediction, repo, y);
                drawPageFooter(cs, 3, 3, repo.getRepositoryName());
            }

            // Add page numbers to pages 1 & 2 (drawn after content so they don't interfere)
            addPageNumber(doc, page1, 1, 3, repo.getRepositoryName());
            addPageNumber(doc, page2, 2, 3, repo.getRepositoryName());

            doc.save(baos);
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("[PdfReportService] PDF generation failed for repo '{}': {}",
                    repo.getRepositoryName(), e.getMessage(), e);
            throw new RuntimeException("PDF report generation failed: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // Section renderers
    // =========================================================================

    /** Dark gradient page header with RiskVision AI branding */
    private float drawPageHeader(PDPageContentStream cs, RepositoryEntity repo,
                                  RepositoryPredictionEntity pred, float y) throws Exception {
        // Header background
        cs.setNonStrokingColor(DARK_BG);
        fillRect(cs, 0, y - 80f, PAGE_WIDTH, 80f);

        // Accent bar at top
        cs.setNonStrokingColor(ACCENT_CYAN);
        fillRect(cs, 0, y - 3f, PAGE_WIDTH, 3f);

        // Brand line
        cs.setNonStrokingColor(TEXT_PRIMARY);
        drawText(cs, FONT_BOLD, 14, "RiskVision AI  ·  Predictive Risk Assessment", MARGIN_L, y - 22f);

        // Subtitle
        cs.setNonStrokingColor(TEXT_MUTED);
        drawText(cs, FONT_NORMAL, 9, "Project Risk Assessment Report  |  Powered by XGBoost + SHAP Explainability", MARGIN_L, y - 35f);

        // Repository name on right
        String repoName = safe(repo.getRepositoryName());
        cs.setNonStrokingColor(ACCENT_CYAN);
        float nameX = PAGE_WIDTH - MARGIN_R - textWidth(FONT_BOLD, 11, repoName);
        drawText(cs, FONT_BOLD, 11, repoName, nameX, y - 22f);

        // Date
        cs.setNonStrokingColor(TEXT_MUTED);
        String dateStr = pred.getCreatedAt() != null
                ? pred.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
                : LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"));
        float dateX = PAGE_WIDTH - MARGIN_R - textWidth(FONT_NORMAL, 8, dateStr);
        drawText(cs, FONT_NORMAL, 8, dateStr, dateX, y - 35f);

        // Bottom border
        cs.setNonStrokingColor(BORDER_COLOR);
        fillRect(cs, 0, y - 82f, PAGE_WIDTH, 2f);

        return y - 96f;
    }

    /** Executive Summary section: all key prediction fields in a clean table */
    private float drawExecutiveSummary(PDPageContentStream cs, RepositoryPredictionEntity pred,
                                        RepositoryEntity repo, float y) throws Exception {
        y -= 10f;
        y = drawSectionTitle(cs, "Executive Risk Summary", y);

        String riskLevel = safe(pred.getRiskLevel(), "UNKNOWN");
        Color riskColor = getRiskColor(riskLevel);

        // Risk level badge
        cs.setNonStrokingColor(riskColor);
        fillRoundRect(cs, MARGIN_L, y - 22f, 90f, 18f);
        cs.setNonStrokingColor(Color.WHITE);
        drawCenteredText(cs, FONT_BOLD, 9, riskLevel + " RISK", MARGIN_L, 90f, y - 14f);

        // Status badge
        cs.setNonStrokingColor(new Color(0x10, 0xB9, 0x81, 180));
        fillRoundRect(cs, MARGIN_L + 98f, y - 22f, 80f, 18f);
        cs.setNonStrokingColor(Color.WHITE);
        drawCenteredText(cs, FONT_BOLD, 8, "COMPLETED", MARGIN_L + 98f, 80f, y - 14f);

        y -= 32f;

        // Summary grid
        double fp = pred.getFailureProbability() != null ? pred.getFailureProbability() : 0.0;
        double conf = pred.getConfidence() != null ? pred.getConfidence() : 0.0;
        double hs = pred.getHealthScore() != null ? pred.getHealthScore() : 0.0;
        int rs = pred.getRiskScore() != null ? pred.getRiskScore() : 0;

        String[][] rows = {
            {"Repository",         safe(repo.getRepositoryName())},
            {"Repository URL",     safe(repo.getRepositoryUrl())},
            {"GitHub Owner",       safe(repo.getOwner(), safe(repo.getOrganization(), "—"))},
            {"Primary Language",   safe(repo.getLanguage(), "—")},
            {"Prediction Date",    pred.getCreatedAt() != null
                    ? pred.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
                    : "—"},
            {"Risk Level",         riskLevel},
            {"Failure Probability", String.format("%.1f%%", fp * 100)},
            {"Risk Score",         rs + " / 100"},
            {"AI Confidence",      String.format("%.1f%%", conf * 100)},
            {"Health Score",       String.format("%.1f", hs)},
            {"Model",              "XGBoost"},
            {"Model Version",      safe(pred.getModelVersion(), "xgboost-v2.x")},
        };

        float colW = CONTENT_W / 2f;
        for (int i = 0; i < rows.length; i++) {
            boolean alt = (i % 2 == 0);
            Color bg = alt ? ROW_ALT : DARK_BG;
            cs.setNonStrokingColor(bg);
            fillRect(cs, MARGIN_L, y - 14f, CONTENT_W, 14f);

            cs.setNonStrokingColor(TEXT_MUTED);
            drawText(cs, FONT_BOLD, 8, rows[i][0], MARGIN_L + 4f, y - 4f);

            String val = rows[i][1];
            Color valColor = TEXT_PRIMARY;
            if (i == 5) valColor = riskColor; // risk level
            if (i == 6 && fp >= 0.75) valColor = CRITICAL_RED;
            if (i == 6 && fp >= 0.5 && fp < 0.75) valColor = HIGH_ORANGE;
            cs.setNonStrokingColor(valColor);
            // Truncate long URLs
            if (val.length() > 55) val = val.substring(0, 52) + "...";
            drawText(cs, i == 5 ? FONT_BOLD : FONT_NORMAL, 8, val, MARGIN_L + colW, y - 4f);
            y -= 14f;
        }

        return y - 6f;
    }

    /** Visual risk gauge bar */
    private float drawRiskGauge(PDPageContentStream cs, RepositoryPredictionEntity pred, float y) throws Exception {
        y -= 8f;
        y = drawSectionTitle(cs, "Failure Probability Gauge", y);
        y -= 4f;

        double fp = pred.getFailureProbability() != null ? pred.getFailureProbability() : 0.0;
        float gaugeH = 14f;
        float gaugeY = y - gaugeH;

        // Background track
        cs.setNonStrokingColor(BORDER_COLOR);
        fillRect(cs, MARGIN_L, gaugeY, CONTENT_W, gaugeH);

        // Fill
        float fillW = (float) Math.min(fp, 1.0) * CONTENT_W;
        Color fillColor = fp >= 0.75 ? CRITICAL_RED : fp >= 0.5 ? HIGH_ORANGE : fp >= 0.25 ? MEDIUM_AMBER : LOW_GREEN;
        cs.setNonStrokingColor(fillColor);
        fillRect(cs, MARGIN_L, gaugeY, fillW, gaugeH);

        // Zone labels below gauge
        y = gaugeY - 14f;
        String[] zoneLabels = {"LOW (0–25%)", "MEDIUM (25–50%)", "HIGH (50–75%)", "CRITICAL (75–100%)"};
        Color[] zoneColors  = {LOW_GREEN, MEDIUM_AMBER, HIGH_ORANGE, CRITICAL_RED};
        float zoneW = CONTENT_W / 4f;
        for (int i = 0; i < 4; i++) {
            boolean active = switch (i) {
                case 0 -> fp < 0.25;
                case 1 -> fp >= 0.25 && fp < 0.50;
                case 2 -> fp >= 0.50 && fp < 0.75;
                default -> fp >= 0.75;
            };
            cs.setNonStrokingColor(active ? zoneColors[i] : TEXT_MUTED);
            String label = zoneLabels[i];
            float lx = MARGIN_L + i * zoneW + 2f;
            drawText(cs, active ? FONT_BOLD : FONT_NORMAL, 7, label, lx, y);
        }

        // Percentage label centered on fill
        cs.setNonStrokingColor(Color.WHITE);
        String pctLabel = String.format("%.1f%%", fp * 100);
        float labelX = MARGIN_L + fillW / 2f - textWidth(FONT_BOLD, 8, pctLabel) / 2f;
        if (labelX < MARGIN_L) labelX = MARGIN_L + 2f;
        drawText(cs, FONT_BOLD, 8, pctLabel, labelX, gaugeY + 3f);

        return y - 6f;
    }

    /** Repository metrics from the entity (actual feature values) */
    private float drawRepositoryMetrics(PDPageContentStream cs, RepositoryEntity repo,
                                         RepositoryPredictionEntity pred, float y) throws Exception {
        y -= 10f;
        y = drawSectionTitle(cs, "Repository Risk Metrics", y);

        List<String[]> metrics = new ArrayList<>();
        if (repo.getOpenIssues() != null) metrics.add(new String[]{"Open Issues", String.valueOf(repo.getOpenIssues())});
        if (repo.getContributors() != null) metrics.add(new String[]{"Contributors (Team Size)", String.valueOf(repo.getContributors())});
        if (repo.getLanguage() != null) metrics.add(new String[]{"Primary Language", repo.getLanguage()});
        if (repo.getBranch() != null) metrics.add(new String[]{"Default Branch", repo.getBranch()});
        if (repo.getVisibility() != null) metrics.add(new String[]{"Visibility", repo.getVisibility()});
        if (repo.getLifecycleStage() != null) metrics.add(new String[]{"Lifecycle Stage", repo.getLifecycleStage()});
        if (repo.getPredictionStatus() != null) metrics.add(new String[]{"Prediction Status", repo.getPredictionStatus()});
        if (repo.getLastCommitDate() != null)
            metrics.add(new String[]{"Last Commit", repo.getLastCommitDate()
                    .format(DateTimeFormatter.ofPattern("dd MMM yyyy"))});
        if (repo.getLastSyncDate() != null)
            metrics.add(new String[]{"Last Sync", repo.getLastSyncDate()
                    .format(DateTimeFormatter.ofPattern("dd MMM yyyy"))});

        if (metrics.isEmpty()) {
            cs.setNonStrokingColor(TEXT_MUTED);
            drawText(cs, FONT_OBLIQUE, 9, "No additional repository metrics available.", MARGIN_L, y - 10f);
            return y - 20f;
        }

        float colW = CONTENT_W / 2f;
        for (int i = 0; i < metrics.size(); i++) {
            boolean alt = (i % 2 == 0);
            cs.setNonStrokingColor(alt ? ROW_ALT : DARK_BG);
            fillRect(cs, MARGIN_L, y - 14f, CONTENT_W, 14f);
            cs.setNonStrokingColor(TEXT_MUTED);
            drawText(cs, FONT_BOLD, 8, metrics.get(i)[0], MARGIN_L + 4f, y - 4f);
            cs.setNonStrokingColor(TEXT_PRIMARY);
            drawText(cs, FONT_NORMAL, 8, metrics.get(i)[1], MARGIN_L + colW, y - 4f);
            y -= 14f;
        }

        return y - 6f;
    }

    private float drawPage2Header(PDPageContentStream cs, RepositoryEntity repo, float y) throws Exception {
        cs.setNonStrokingColor(HEADER_BG);
        fillRect(cs, 0, y - 28f, PAGE_WIDTH, 28f);
        cs.setNonStrokingColor(ACCENT_BLUE);
        fillRect(cs, 0, y - 3f, PAGE_WIDTH, 3f);
        cs.setNonStrokingColor(TEXT_PRIMARY);
        drawText(cs, FONT_BOLD, 11, "RiskVision AI  ·  " + safe(repo.getRepositoryName()) + "  ·  SHAP & AI Recommendations", MARGIN_L, y - 18f);
        cs.setNonStrokingColor(BORDER_COLOR);
        fillRect(cs, 0, y - 30f, PAGE_WIDTH, 1f);
        return y - 42f;
    }

    private float drawPage3Header(PDPageContentStream cs, RepositoryEntity repo, float y) throws Exception {
        cs.setNonStrokingColor(HEADER_BG);
        fillRect(cs, 0, y - 28f, PAGE_WIDTH, 28f);
        cs.setNonStrokingColor(ACCENT_BLUE);
        fillRect(cs, 0, y - 3f, PAGE_WIDTH, 3f);
        cs.setNonStrokingColor(TEXT_PRIMARY);
        drawText(cs, FONT_BOLD, 11, "RiskVision AI  ·  " + safe(repo.getRepositoryName()) + "  ·  Action Plan & Model Info", MARGIN_L, y - 18f);
        cs.setNonStrokingColor(BORDER_COLOR);
        fillRect(cs, 0, y - 30f, PAGE_WIDTH, 1f);
        return y - 42f;
    }

    /** SHAP Feature Importance table */
    private float drawShapTable(PDPageContentStream cs, List<Map<String, Object>> features, float y) throws Exception {
        y -= 8f;
        y = drawSectionTitle(cs, "SHAP Feature Importance — XGBoost Explainability", y);

        if (features.isEmpty()) {
            cs.setNonStrokingColor(TEXT_MUTED);
            drawText(cs, FONT_OBLIQUE, 9, "No SHAP feature data available for this prediction.", MARGIN_L, y - 12f);
            return y - 24f;
        }

        // Table header
        float[] colWidths = {200f, 80f, 80f, CONTENT_W - 200f - 80f - 80f};
        String[] headers = {"Feature", "SHAP Value", "Direction", "Risk Impact"};
        float rowH = 14f;

        cs.setNonStrokingColor(ACCENT_BLUE);
        fillRect(cs, MARGIN_L, y - rowH, CONTENT_W, rowH);
        cs.setNonStrokingColor(Color.WHITE);
        float hx = MARGIN_L + 4f;
        for (int c = 0; c < headers.length; c++) {
            drawText(cs, FONT_BOLD, 8, headers[c], hx, y - (rowH - 4f));
            hx += colWidths[c];
        }
        y -= rowH;

        // Sort features by absolute SHAP value descending
        features.sort((a, b) -> {
            double ia = Math.abs(toDouble(a.get("impact"), a.get("avg_impact")));
            double ib = Math.abs(toDouble(b.get("impact"), b.get("avg_impact")));
            return Double.compare(ib, ia);
        });

        int maxRows = Math.min(features.size(), 12);
        for (int i = 0; i < maxRows; i++) {
            Map<String, Object> f = features.get(i);
            boolean alt = (i % 2 == 0);
            cs.setNonStrokingColor(alt ? ROW_ALT : DARK_BG);
            fillRect(cs, MARGIN_L, y - rowH, CONTENT_W, rowH);

            String featureName = safe(f.get("display_name"), f.get("feature_name"), f.get("feature"), "Feature " + i);
            featureName = featureName.replace("_", " ");
            double impact = toDouble(f.get("impact"), f.get("avg_impact"));
            String direction = safe(f.get("direction"), "").toLowerCase();
            boolean increases = direction.contains("increase");
            String impactLabel = increases ? "▲ Increases Risk" : "▼ Reduces Risk";
            Color impactColor = increases ? CRITICAL_RED : LOW_GREEN;

            float cx = MARGIN_L + 4f;
            cs.setNonStrokingColor(TEXT_PRIMARY);
            drawText(cs, FONT_NORMAL, 8, truncate(featureName, 30), cx, y - (rowH - 4f));
            cx += colWidths[0];

            cs.setNonStrokingColor(increases ? CRITICAL_RED : LOW_GREEN);
            drawText(cs, FONT_BOLD, 8, String.format("%+.3f", impact), cx, y - (rowH - 4f));
            cx += colWidths[1];

            cs.setNonStrokingColor(TEXT_MUTED);
            drawText(cs, FONT_NORMAL, 7, increases ? "INCREASING" : "DECREASING", cx, y - (rowH - 4f));
            cx += colWidths[2];

            cs.setNonStrokingColor(impactColor);
            drawText(cs, FONT_BOLD, 7, impactLabel, cx, y - (rowH - 4f));

            y -= rowH;
        }

        return y - 8f;
    }

    /** AI Recommendations section — wraps multi-line text */
    private float drawAiRecommendations(PDPageContentStream cs, String recsText,
                                         RepositoryPredictionEntity pred, float y) throws Exception {
        y -= 10f;
        y = drawSectionTitle(cs, "AI Recommendations — How to Reduce Project Risk", y);

        cs.setNonStrokingColor(new Color(0x06, 0x4E, 0x3B, 220)); // dark teal background
        fillRoundRect(cs, MARGIN_L, y - 8f, CONTENT_W, 8f);

        // Wrap and render the AI text
        String[] lines = wrapText(recsText, FONT_NORMAL, 8, CONTENT_W - 8f);
        float lineH = 11f;

        for (String line : lines) {
            if (y < 50f) break; // safety — don't overflow page

            // Style priority markers
            boolean isPriority = line.startsWith("Priority") || line.startsWith("PRIORITY")
                    || line.startsWith("P0") || line.startsWith("P1") || line.startsWith("P2") || line.startsWith("P3");
            boolean isBold = isPriority || line.startsWith("Problem:") || line.startsWith("Action:")
                    || line.startsWith("Evidence:") || line.startsWith("Expected");
            boolean isHeader = line.startsWith("###") || line.startsWith("##");

            if (isHeader) {
                line = line.replace("###", "").replace("##", "").trim();
                y -= 4f;
                cs.setNonStrokingColor(ACCENT_CYAN);
                drawText(cs, FONT_BOLD, 9, line, MARGIN_L, y);
                y -= lineH;
                continue;
            }

            // Remove markdown asterisks
            line = line.replace("**", "").replace("*", "").replace("- ", "• ").trim();
            if (line.isEmpty()) { y -= lineH * 0.5f; continue; }

            cs.setNonStrokingColor(isPriority ? ACCENT_CYAN : isBold ? TEXT_PRIMARY : TEXT_MUTED);
            drawText(cs, isBold ? FONT_BOLD : FONT_NORMAL, 8, line, MARGIN_L + 4f, y);
            y -= lineH;
        }

        return y - 8f;
    }

    /** Action Plan: Immediate / Short-Term / Long-Term */
    private float drawActionPlan(PDPageContentStream cs, RepositoryPredictionEntity pred,
                                  RepositoryEntity repo, List<Map<String, Object>> shapFeatures,
                                  float y) throws Exception {
        y -= 8f;
        y = drawSectionTitle(cs, "Recommended Action Plan", y);

        String riskLevel = safe(pred.getRiskLevel(), "LOW");
        double fp = pred.getFailureProbability() != null ? pred.getFailureProbability() : 0.0;

        List<String> immediate = buildImmediateActions(riskLevel, fp, shapFeatures, repo);
        List<String> shortTerm = buildShortTermActions(riskLevel, fp, shapFeatures, repo);
        List<String> longTerm  = buildLongTermActions(riskLevel, fp, shapFeatures, repo);

        String[][] sections = {
            {"IMMEDIATE (0 – 7 days)", String.join(" | ", immediate)},
            {"SHORT-TERM (1 – 4 weeks)", String.join(" | ", shortTerm)},
            {"LONG-TERM (1 – 3 months)", String.join(" | ", longTerm)},
        };
        Color[] secColors = {CRITICAL_RED, MEDIUM_AMBER, LOW_GREEN};

        for (int s = 0; s < sections.length; s++) {
            cs.setNonStrokingColor(secColors[s]);
            drawText(cs, FONT_BOLD, 9, sections[s][0], MARGIN_L, y);
            y -= 12f;
            String[] items = sections[s][1].split(" \\| ");
            for (String item : items) {
                String[] wrapped = wrapText("• " + item, FONT_NORMAL, 8, CONTENT_W - 10f);
                for (String wl : wrapped) {
                    if (y < 60f) break;
                    cs.setNonStrokingColor(TEXT_PRIMARY);
                    drawText(cs, FONT_NORMAL, 8, wl, MARGIN_L + 8f, y);
                    y -= 11f;
                }
            }
            y -= 6f;
        }

        return y;
    }

    /** Model information footer section */
    private float drawModelInfo(PDPageContentStream cs, RepositoryPredictionEntity pred,
                                 RepositoryEntity repo, float y) throws Exception {
        y -= 10f;
        y = drawSectionTitle(cs, "Model Information", y);

        String[][] rows = {
            {"Prediction Engine",    "XGBoost (Extreme Gradient Boosting)"},
            {"Model Version",        safe(pred.getModelVersion(), "xgboost-v2.x")},
            {"Explainability",       "SHAP (SHapley Additive exPlanations)"},
            {"Prediction ID",        pred.getId() != null ? pred.getId().toString() : "—"},
            {"Repository ID",        repo.getId() != null ? repo.getId().toString() : "—"},
            {"Report Generated",     LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss"))},
            {"Prediction Triggered", safe(pred.getTriggeredBy(), "MANUAL")},
        };

        float colW = CONTENT_W / 2f;
        for (int i = 0; i < rows.length; i++) {
            boolean alt = (i % 2 == 0);
            cs.setNonStrokingColor(alt ? ROW_ALT : DARK_BG);
            fillRect(cs, MARGIN_L, y - 14f, CONTENT_W, 14f);
            cs.setNonStrokingColor(TEXT_MUTED);
            drawText(cs, FONT_BOLD, 8, rows[i][0], MARGIN_L + 4f, y - 4f);
            cs.setNonStrokingColor(TEXT_PRIMARY);
            drawText(cs, FONT_NORMAL, 8, truncate(rows[i][1], 60), MARGIN_L + colW, y - 4f);
            y -= 14f;
        }

        // Disclaimer
        y -= 10f;
        cs.setNonStrokingColor(TEXT_MUTED);
        drawText(cs, FONT_OBLIQUE, 7,
                "This report was generated automatically by RiskVision AI. All predictions are based on repository activity patterns analysed by the XGBoost model.",
                MARGIN_L, y);
        y -= 9f;
        drawText(cs, FONT_OBLIQUE, 7,
                "AI recommendations are based on the repository's actual risk factors identified via SHAP feature importance. Qualitative impact estimates are non-binding.",
                MARGIN_L, y);

        return y - 8f;
    }

    // =========================================================================
    // AI Recommendation resolution
    // =========================================================================

    private String resolveAiRecommendations(RepositoryPredictionEntity pred,
                                             RepositoryEntity repo,
                                             List<Map<String, Object>> shapFeatures) {
        // 1. Check if rich AI recommendations already exist in the prediction record
        String existingRecs = pred.getRecommendationsJson();
        if (existingRecs != null && !existingRecs.isBlank()) {
            try {
                Map<String, Object> parsed = objectMapper.readValue(existingRecs,
                        new TypeReference<Map<String, Object>>() {});
                if (parsed.containsKey("recommendations")) {
                    // Convert structured JSON to readable text
                    return formatStructuredRecommendations(parsed);
                }
            } catch (Exception e) {
                log.debug("[PdfReportService] Existing recs not structured JSON, trying as raw text");
            }
            // Could be a plain list of strings
            try {
                List<String> list = objectMapper.readValue(existingRecs,
                        new TypeReference<List<String>>() {});
                if (!list.isEmpty()) {
                    return String.join("\n\n", list);
                }
            } catch (Exception ignored) {}
        }

        // 2. Call OpenRouter for fresh AI recommendations
        try {
            String prompt = buildAiPrompt(pred, repo, shapFeatures);
            String aiResponse = openRouterClient.getCompletion(
                    "You are a senior software engineering risk analyst. Respond only in plain text (no markdown code blocks). Be specific and data-driven.",
                    prompt
            );
            if (aiResponse != null && !aiResponse.isBlank()) {
                log.info("[PdfReportService] AI recommendations obtained from OpenRouter for repo '{}'",
                        repo.getRepositoryName());
                return aiResponse;
            }
        } catch (Exception e) {
            log.warn("[PdfReportService] OpenRouter unavailable for PDF recommendations, using rule-based fallback: {}",
                    e.getMessage());
        }

        // 3. Rule-based fallback from actual metrics
        return buildRuleBasedRecommendations(pred, repo, shapFeatures);
    }

    private String buildAiPrompt(RepositoryPredictionEntity pred, RepositoryEntity repo,
                                  List<Map<String, Object>> shapFeatures) {
        double fp = pred.getFailureProbability() != null ? pred.getFailureProbability() * 100 : 0.0;
        int rs = pred.getRiskScore() != null ? pred.getRiskScore() : 0;
        double conf = pred.getConfidence() != null ? pred.getConfidence() * 100 : 0.0;
        double hs = pred.getHealthScore() != null ? pred.getHealthScore() : 0.0;
        String rl = safe(pred.getRiskLevel(), "UNKNOWN");

        StringBuilder sb = new StringBuilder();
        sb.append("You are analysing a software repository for risk.\n\n");
        sb.append("REPOSITORY INFORMATION:\n");
        sb.append("  Name: ").append(safe(repo.getRepositoryName())).append("\n");
        sb.append("  Language: ").append(safe(repo.getLanguage(), "Unknown")).append("\n");
        sb.append("  Open Issues: ").append(repo.getOpenIssues() != null ? repo.getOpenIssues() : "Unknown").append("\n");
        sb.append("  Contributors: ").append(repo.getContributors() != null ? repo.getContributors() : "Unknown").append("\n");
        sb.append("  Lifecycle Stage: ").append(safe(repo.getLifecycleStage(), "ACTIVE")).append("\n\n");

        sb.append("XGBOOST PREDICTION RESULTS:\n");
        sb.append("  Failure Probability: ").append(String.format("%.1f%%", fp)).append("\n");
        sb.append("  Risk Score: ").append(rs).append(" / 100\n");
        sb.append("  Risk Level: ").append(rl).append("\n");
        sb.append("  AI Confidence: ").append(String.format("%.1f%%", conf)).append("\n");
        sb.append("  Health Score: ").append(String.format("%.1f", hs)).append("\n\n");

        if (!shapFeatures.isEmpty()) {
            sb.append("TOP SHAP FEATURE IMPORTANCE (factors driving this prediction):\n");
            int maxFeatures = Math.min(shapFeatures.size(), 8);
            for (int i = 0; i < maxFeatures; i++) {
                Map<String, Object> f = shapFeatures.get(i);
                String name = safe(f.get("display_name"), f.get("feature_name"), f.get("feature"), "Feature");
                double impact = toDouble(f.get("impact"), f.get("avg_impact"));
                String dir = safe(f.get("direction"), "");
                sb.append("  ").append(i + 1).append(". ").append(name)
                  .append(" (SHAP: ").append(String.format("%+.3f", impact))
                  .append(", ").append(dir).append(")\n");
            }
            sb.append("\n");
        }

        sb.append("TASK:\n");
        sb.append("Based ONLY on the above actual repository data, generate specific, actionable project improvement recommendations.\n\n");
        sb.append("For each of the top 4-5 risk factors identified, provide:\n");
        sb.append("- Priority (P0 CRITICAL / P1 HIGH / P2 MEDIUM / P3 LOW)\n");
        sb.append("- Problem: what is wrong based on the evidence\n");
        sb.append("- Evidence: cite the specific metric or SHAP value\n");
        sb.append("- Recommended Action: concrete steps the team should take\n");
        sb.append("- Expected Impact: qualitative improvement to project health\n\n");
        sb.append("RULES:\n");
        sb.append("- Do NOT invent metrics not provided above\n");
        sb.append("- Do NOT use generic advice like 'improve code quality'\n");
        sb.append("- Recommendations MUST be different from a different project's recommendations\n");
        sb.append("- Do NOT claim exact percentage risk reductions\n");
        sb.append("- Use plain text, no markdown bold (**), no code blocks\n");
        sb.append("- Be specific to this repository: ").append(safe(repo.getRepositoryName())).append("\n");

        return sb.toString();
    }

    /** Format existing structured recommendations JSON to human-readable text */
    @SuppressWarnings("unchecked")
    private String formatStructuredRecommendations(Map<String, Object> parsed) {
        StringBuilder sb = new StringBuilder();
        try {
            List<Map<String, Object>> recs = (List<Map<String, Object>>) parsed.get("recommendations");
            if (recs != null) {
                for (int i = 0; i < recs.size(); i++) {
                    Map<String, Object> r = recs.get(i);
                    String priority = safe(r.get("suggested_priority"), "MEDIUM");
                    sb.append("Priority ").append(i + 1).append(" — ").append(priority.toUpperCase()).append("\n");
                    sb.append("Issue: ").append(safe(r.get("risk_detected"), r.get("title"), "Risk factor identified")).append("\n");
                    if (r.get("current_condition") != null) {
                        sb.append("Evidence: ").append(r.get("current_condition")).append("\n");
                    }
                    sb.append("Recommended Action: ").append(safe(r.get("recommended_action"), "Review and address this factor")).append("\n");
                    sb.append("Why It Matters: ").append(safe(r.get("why_it_matters"), "Improves project health")).append("\n");
                    sb.append("Expected Impact: ").append(safe(r.get("expected_impact"), "Moderate")).append("\n");
                    sb.append("\n");
                }
            }
            // Roadmap
            if (parsed.containsKey("roadmap")) {
                Map<String, Object> roadmap = (Map<String, Object>) parsed.get("roadmap");
                sb.append("### Recommended Action Roadmap\n\n");
                appendRoadmapSection(sb, roadmap, "immediate", "Immediate (0-7 days)");
                appendRoadmapSection(sb, roadmap, "short_term", "Short-Term (1-4 weeks)");
                appendRoadmapSection(sb, roadmap, "medium_term", "Medium-Term (1-3 months)");
            }
        } catch (Exception e) {
            log.warn("[PdfReportService] Could not format structured recs: {}", e.getMessage());
            return parsed.toString();
        }
        return sb.toString();
    }

    private void appendRoadmapSection(StringBuilder sb, Map<String, Object> roadmap,
                                       String key, String label) {
        Object section = roadmap.get(key);
        if (section instanceof List<?> list && !list.isEmpty()) {
            sb.append(label).append(":\n");
            list.forEach(item -> sb.append("  • ").append(item).append("\n"));
            sb.append("\n");
        }
    }

    /** Rule-based fallback — generates recommendations from actual metrics */
    private String buildRuleBasedRecommendations(RepositoryPredictionEntity pred,
                                                   RepositoryEntity repo,
                                                   List<Map<String, Object>> shapFeatures) {
        StringBuilder sb = new StringBuilder();
        sb.append("Note: AI recommendation service is temporarily unavailable.\n");
        sb.append("The following recommendations are generated automatically from the repository's actual XGBoost risk factors.\n\n");

        double fp = pred.getFailureProbability() != null ? pred.getFailureProbability() : 0.0;
        String rl = safe(pred.getRiskLevel(), "LOW");
        int openIssues = repo.getOpenIssues() != null ? repo.getOpenIssues() : 0;
        int contributors = repo.getContributors() != null ? repo.getContributors() : 0;

        int priority = 1;

        // Overall risk summary
        sb.append("Risk Assessment Summary\n");
        sb.append("Repository: ").append(safe(repo.getRepositoryName())).append("\n");
        sb.append("Current Risk Level: ").append(rl).append(" (").append(String.format("%.1f%%", fp * 100)).append(" failure probability)\n\n");

        // Recommendation from SHAP features
        for (int i = 0; i < Math.min(shapFeatures.size(), 5); i++) {
            Map<String, Object> f = shapFeatures.get(i);
            String featureName = safe(f.get("display_name"), f.get("feature_name"), f.get("feature"), "Risk Factor");
            double impact = toDouble(f.get("impact"), f.get("avg_impact"));
            boolean increases = safe(f.get("direction"), "").toLowerCase().contains("increase");
            if (!increases) continue; // Only recommend for risk-increasing factors

            String featureClean = featureName.replace("_", " ").toLowerCase();
            String action = generateActionForFeature(featureClean, impact, repo);
            if (action == null) continue;

            String priorityLabel = fp >= 0.75 ? "P0 CRITICAL" : fp >= 0.5 ? "P1 HIGH" : fp >= 0.25 ? "P2 MEDIUM" : "P3 LOW";
            sb.append("Priority ").append(priority++).append(" — ").append(priorityLabel).append("\n");
            sb.append("Issue: ").append(featureName.replace("_", " ")).append(" is a significant risk driver\n");
            sb.append("Evidence: SHAP value ").append(String.format("%+.3f", impact)).append(" indicates this factor is increasing failure probability\n");
            sb.append("Recommended Action: ").append(action).append("\n");
            sb.append("Expected Impact: Reducing this factor should improve the project's overall health score and lower failure probability\n\n");
        }

        // Metric-based recommendations
        if (openIssues > 20) {
            sb.append("Priority ").append(priority++).append(" — P1 HIGH\n");
            sb.append("Issue: High open issue count (").append(openIssues).append(" open issues)\n");
            sb.append("Evidence: ").append(openIssues).append(" unresolved issues indicate accumulated technical backlog\n");
            sb.append("Recommended Action: Implement an issue triage workflow. Prioritise critical/high-severity issues for immediate resolution. Aim to reduce the backlog by 50% within 4 weeks.\n");
            sb.append("Expected Impact: Reduced issue backlog correlates with improved project stability and lower failure risk\n\n");
        }

        if (contributors <= 1) {
            sb.append("Priority ").append(priority++).append(" — P2 MEDIUM\n");
            sb.append("Issue: Single-contributor bus factor risk\n");
            sb.append("Evidence: Only ").append(contributors).append(" contributor(s) — critical knowledge is concentrated in one person\n");
            sb.append("Recommended Action: Document core architecture and processes. Onboard at least one additional contributor. Implement pair programming and code review workflows.\n");
            sb.append("Expected Impact: Reduces key-person dependency risk and improves long-term project sustainability\n\n");
        }

        if (fp >= 0.75) {
            sb.append("Priority ").append(priority).append(" — P0 CRITICAL\n");
            sb.append("Issue: Critical failure probability (").append(String.format("%.1f%%", fp * 100)).append(")\n");
            sb.append("Evidence: XGBoost model predicts ").append(String.format("%.1f%%", fp * 100)).append(" failure probability — immediate action required\n");
            sb.append("Recommended Action: Conduct an emergency project health review. Address all P0 and P1 items immediately. Consider freezing new feature development until risk is reduced.\n");
            sb.append("Expected Impact: Addressing critical risk factors can significantly reduce failure probability and prevent project abandonment\n\n");
        }

        return sb.toString();
    }

    private String generateActionForFeature(String featureName, double impact, RepositoryEntity repo) {
        if (featureName.contains("technical debt") || featureName.contains("debt")) {
            return "Prioritise refactoring of the highest-complexity modules. Use static analysis tools to identify and track technical debt items. Allocate at least 20% of sprint capacity to debt reduction.";
        } else if (featureName.contains("test") || featureName.contains("coverage")) {
            return "Increase automated test coverage, particularly around frequently-modified and high-risk modules. Add regression tests for areas with historical defect density. Target meaningful coverage improvements incrementally.";
        } else if (featureName.contains("issue") || featureName.contains("bug")) {
            return "Implement a systematic bug triage process. Prioritise critical and high-severity bugs for immediate resolution. Set up automated bug detection in the CI pipeline.";
        } else if (featureName.contains("pull request") || featureName.contains("pr")) {
            return "Analyse recurring causes of failed pull requests. Strengthen CI/CD validation gates. Introduce code review checklists and automated linting to catch issues before merge.";
        } else if (featureName.contains("commit") || featureName.contains("activity") || featureName.contains("inactive")) {
            return "Establish a regular development cadence. Schedule regular dependency updates, maintenance commits, and issue resolution sessions to maintain repository activity health.";
        } else if (featureName.contains("budget") || featureName.contains("cost")) {
            return "Review project resource allocation and ensure development pace aligns with budget constraints. Track cost-to-completion metrics and adjust scope if needed.";
        } else if (featureName.contains("schedule") || featureName.contains("delay")) {
            return "Audit current sprint velocity and milestone progress. Identify and address blockers causing schedule delays. Consider scope reduction to protect delivery timeline.";
        } else if (featureName.contains("contributor") || featureName.contains("team")) {
            return "Expand the contributor base to reduce key-person dependencies. Document institutional knowledge and implement structured knowledge transfer sessions.";
        }
        // Generic but based on actual SHAP factor
        return "Review and address the '" + featureName + "' risk factor. SHAP analysis shows this metric has a significant contribution to failure probability. Consult with the team to develop specific mitigation strategies.";
    }

    // =========================================================================
    // Action Plan builders (from actual metrics)
    // =========================================================================

    private List<String> buildImmediateActions(String riskLevel, double fp,
                                                List<Map<String, Object>> shapFeatures,
                                                RepositoryEntity repo) {
        List<String> actions = new ArrayList<>();
        if (fp >= 0.75) {
            actions.add("Freeze new feature development and conduct emergency health review");
            actions.add("Address all P0 critical findings identified in SHAP analysis");
        }
        if (fp >= 0.5) {
            actions.add("Review and triage all open issues — resolve critical/high severity items first");
        }
        // Top risk-increasing SHAP factor
        for (Map<String, Object> f : shapFeatures) {
            if (safe(f.get("direction"), "").toLowerCase().contains("increase")) {
                String name = safe(f.get("display_name"), f.get("feature_name"), f.get("feature"), "top risk factor");
                actions.add("Investigate and begin mitigation of: " + name.replace("_", " "));
                break;
            }
        }
        if (actions.isEmpty()) actions.add("Run a comprehensive project health audit");
        return actions;
    }

    private List<String> buildShortTermActions(String riskLevel, double fp,
                                                List<Map<String, Object>> shapFeatures,
                                                RepositoryEntity repo) {
        List<String> actions = new ArrayList<>();
        actions.add("Increase automated test coverage for high-change-frequency modules");
        actions.add("Reduce open issue backlog by at least 50%");
        if (repo.getContributors() != null && repo.getContributors() <= 2) {
            actions.add("Onboard additional contributors to reduce bus-factor risk");
        }
        actions.add("Strengthen CI/CD pipeline validation with automated quality gates");
        return actions;
    }

    private List<String> buildLongTermActions(String riskLevel, double fp,
                                               List<Map<String, Object>> shapFeatures,
                                               RepositoryEntity repo) {
        List<String> actions = new ArrayList<>();
        actions.add("Establish technical debt tracking and allocate regular refactoring sprints");
        actions.add("Implement repository health monitoring with automated alerts on risk score increases");
        actions.add("Document core architecture, APIs, and processes to reduce key-person dependencies");
        actions.add("Schedule quarterly risk prediction runs to track long-term health trends");
        return actions;
    }

    // =========================================================================
    // PDF drawing primitives
    // =========================================================================

    private float drawSectionTitle(PDPageContentStream cs, String title, float y) throws Exception {
        y -= 4f;
        // Title bar background
        cs.setNonStrokingColor(new Color(0x1E, 0x40, 0x6E, 200));
        fillRect(cs, MARGIN_L - 4f, y - 16f, CONTENT_W + 8f, 16f);
        // Left accent line
        cs.setNonStrokingColor(ACCENT_CYAN);
        fillRect(cs, MARGIN_L - 4f, y - 16f, 3f, 16f);
        // Title text
        cs.setNonStrokingColor(Color.WHITE);
        drawText(cs, FONT_BOLD, 9, title.toUpperCase(), MARGIN_L + 4f, y - 5f);
        return y - 22f;
    }

    private void drawText(PDPageContentStream cs, PDType1Font font, float size,
                           String text, float x, float y) throws Exception {
        if (text == null || text.isBlank()) return;
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        // Replace any chars that Type1 can't encode
        String safe = text.chars()
                .filter(c -> c >= 32 && c < 127)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        cs.showText(safe);
        cs.endText();
    }

    private void drawCenteredText(PDPageContentStream cs, PDType1Font font, float size,
                                   String text, float x, float width, float y) throws Exception {
        float tw = textWidth(font, size, text);
        float cx = x + (width - tw) / 2f;
        drawText(cs, font, size, text, cx, y);
    }

    private void fillRect(PDPageContentStream cs, float x, float y, float w, float h) throws Exception {
        cs.addRect(x, y, w, h);
        cs.fill();
    }

    private void fillRoundRect(PDPageContentStream cs, float x, float y, float w, float h) throws Exception {
        // Approximation using standard rect (PDFBox 3 path API)
        fillRect(cs, x, y, w, h);
    }

    private void drawPageFooter(PDPageContentStream cs, int pageNum, int totalPages, String repoName) throws Exception {
        cs.setNonStrokingColor(BORDER_COLOR);
        fillRect(cs, 0, 28f, PAGE_WIDTH, 1f);
        cs.setNonStrokingColor(TEXT_MUTED);
        drawText(cs, FONT_NORMAL, 7, "RiskVision AI  •  " + safe(repoName) + "  •  Risk Assessment Report", MARGIN_L, 16f);
        String pageStr = "Page " + pageNum + " of " + totalPages;
        float px = PAGE_WIDTH - MARGIN_R - textWidth(FONT_NORMAL, 7, pageStr);
        drawText(cs, FONT_NORMAL, 7, pageStr, px, 16f);
    }

    private void addPageNumber(PDDocument doc, PDPage page, int pageNum, int totalPages, String repoName)
            throws Exception {
        try (PDPageContentStream cs = new PDPageContentStream(doc, page,
                PDPageContentStream.AppendMode.APPEND, true)) {
            drawPageFooter(cs, pageNum, totalPages, repoName);
        }
    }

    private float textWidth(PDType1Font font, float size, String text) {
        try {
            return font.getStringWidth(text) / 1000f * size;
        } catch (Exception e) {
            return text.length() * size * 0.55f; // rough fallback
        }
    }

    /** Wraps a string to fit within maxWidth, returns array of wrapped lines */
    private String[] wrapText(String text, PDType1Font font, float size, float maxWidth) {
        if (text == null) return new String[0];
        List<String> lines = new ArrayList<>();
        String[] paragraphs = text.split("\n");
        for (String para : paragraphs) {
            if (para.isBlank()) {
                lines.add("");
                continue;
            }
            String[] words = para.split(" ");
            StringBuilder cur = new StringBuilder();
            for (String word : words) {
                String candidate = cur.isEmpty() ? word : cur + " " + word;
                if (textWidth(font, size, candidate) <= maxWidth) {
                    if (!cur.isEmpty()) cur.append(" ");
                    cur.append(word);
                } else {
                    if (!cur.isEmpty()) lines.add(cur.toString());
                    cur = new StringBuilder(word);
                }
            }
            if (!cur.isEmpty()) lines.add(cur.toString());
        }
        return lines.toArray(String[]::new);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Color getRiskColor(String level) {
        if (level == null) return TEXT_MUTED;
        return switch (level.toUpperCase()) {
            case "CRITICAL" -> CRITICAL_RED;
            case "HIGH"     -> HIGH_ORANGE;
            case "MEDIUM"   -> MEDIUM_AMBER;
            default         -> LOW_GREEN;
        };
    }

    private List<Map<String, Object>> parseShapFeatures(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.debug("[PdfReportService] Could not parse featureImportanceJson: {}", e.getMessage());
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

    private String truncate(String s, int max) {
        if (s == null) return "—";
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}

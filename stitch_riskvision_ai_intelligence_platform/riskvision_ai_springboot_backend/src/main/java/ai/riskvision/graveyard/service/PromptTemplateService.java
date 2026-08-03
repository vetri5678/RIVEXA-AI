package ai.riskvision.graveyard.service;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PromptTemplateService {

    public String getSystemPrompt() {
        return "You are an advanced Senior AI Software Architect, SecOps Lead, and AI Risk Specialist for the RiskVision AI platform.\n" +
                "You must perform thorough and accurate risk analysis on the provided telemetry, audit, and repository data.\n" +
                "CRITICAL: You must return your analysis ONLY as a valid JSON object. Do NOT include markdown code blocks (e.g. ```json), introductory text, or explanatory footnotes.\n" +
                "The JSON object must strictly conform to this schema:\n" +
                "{\n" +
                "  \"summary\": \"A concise, high-level natural language summary of the findings.\",\n" +
                "  \"severity\": \"The severity classification: LOW, MEDIUM, HIGH, or CRITICAL.\",\n" +
                "  \"confidence\": \"Confidence level of this analysis: e.g. 95%.\",\n" +
                "  \"rootCause\": \"Detailed technical explanation of the underlying failure drivers or security threats.\",\n" +
                "  \"recommendations\": [\"Array of 3-5 specific, actionable remediation steps (e.g. 'Deploy bugfix', 'Re-engage maintainers').\"],\n" +
                "  \"impact\": \"Optional. Anticipated blast radius or business consequence if left unmitigated.\",\n" +
                "  \"recommendedFix\": \"Optional. Code snippet or architectural remediation recommendation.\"\n" +
                "}";
    }

    public String getUserPrompt(String feature, Map<String, Object> variables) {
        StringBuilder sb = new StringBuilder();
        sb.append("Perform ").append(feature.toUpperCase().replace("_", " ")).append(" using the following context data:\n\n");
        
        variables.forEach((key, value) -> {
            sb.append("- ").append(key).append(": ").append(value != null ? value.toString() : "N/A").append("\n");
        });
        
        sb.append("\nGenerate the JSON analysis report now.");
        return sb.toString();
    }
}

package ai.riskvision.graveyard.dto.prediction;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionRequestDTO {
    @JsonProperty("project_budget")
    private Double projectBudget;

    @JsonProperty("actual_cost")
    private Double actualCost;

    @JsonProperty("schedule_delay")
    private Double scheduleDelay;

    @JsonProperty("team_size")
    private Integer teamSize;

    @JsonProperty("open_issues")
    private Integer openIssues;

    @JsonProperty("critical_bugs")
    private Integer criticalBugs;

    @JsonProperty("completion_pct")
    private Double completionPct;

    @JsonProperty("client_requirement_changes")
    private Integer clientRequirementChanges;

    private String priority;
    private String department;

    @JsonProperty("project_type")
    private String projectType;

    @JsonProperty("estimated_cost")
    private Double estimatedCost;

    @JsonProperty("actual_duration")
    private Double actualDuration;

    @JsonProperty("estimated_duration")
    private Double estimatedDuration;

    @JsonProperty("resource_utilization")
    private Double resourceUtilization;

    @JsonProperty("customer_satisfaction")
    private Double customerSatisfaction;

    @JsonProperty("technical_debt")
    private Double technicalDebt;

    @JsonProperty("security_issues")
    private Integer securityIssues;

    @JsonProperty("compliance_issues")
    private Integer complianceIssues;
}

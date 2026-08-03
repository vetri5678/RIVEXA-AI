"""
RiskVision AI — Stage 12: Risk Report Generator

Assembles all predictions, explanations, and model evaluation metrics into
a comprehensive, production-ready RiskAssessmentReport.  Applies rule-based
logic to generate actionable recommendations and saves the report to disk.
"""

import logging
import uuid
from dataclasses import dataclass, field, asdict
from datetime import datetime, timezone
from pathlib import Path
import json

from src.pipeline.base import PipelineStage, StagePayload
from src.pipeline.exceptions import ReportGenerationError
from src.utils.file_utils import save_json, ensure_directory

logger = logging.getLogger("riskvision.pipeline.ReportGenerator")


# =============================================================================
# Report DTOs
# =============================================================================

@dataclass
class Recommendation:
    """An actionable step to mitigate risk."""
    priority: str                       # "HIGH" | "MEDIUM" | "LOW"
    area: str                           # "Timeline" | "Cost" | "Scope" | "Resources" | "General"
    action: str
    expected_impact: str
    related_risk_factor: str


@dataclass
class RiskAssessmentReport:
    """The master Risk Assessment Report."""
    report_id: str
    report_version: str
    generated_at: str
    project_name: str
    project_id: str
    risk_percentage: float
    risk_level: str
    failure_probability: float
    confidence_score: float
    critical_factors: list[dict]        # list of serialized RiskFactor/FeatureContrib
    positive_factors: list[dict]
    negative_factors: list[dict]
    recommended_actions: list[Recommendation]
    model_name: str
    model_grade: str
    model_accuracy: float
    model_f1_score: float
    human_summary: str
    detailed_explanation: str
    pipeline_version: str
    data_sources: list[str]
    feature_count: int
    training_samples: int


# =============================================================================
# Stage Implementation
# =============================================================================

class RiskReportGeneratorStage(PipelineStage):
    """
    Pipeline Stage 12 — Risk Report Generator.

    Orchestrates the creation of the final risk assessment report,
    generating recommendations based on the detected risk factors.
    """

    def get_stage_name(self) -> str:
        return "ReportGenerator"

    def validate_input(self, payload: StagePayload) -> None:
        if payload.artifacts.get("prediction_result") is None:
            raise ReportGenerationError("Missing prediction result in payload.")
        if payload.artifacts.get("prediction_explanation") is None:
            raise ReportGenerationError("Missing prediction explanation in payload.")

    # ------------------------------------------------------------------
    # Rule-Based Recommendation Generator
    # ------------------------------------------------------------------

    def _generate_recommendations(self, risk_factors: list) -> list[Recommendation]:
        """
        Generate contextual recommendations based on top risk factors.
        """
        recommendations = []

        # Area mapping based on features
        mitigation_rules = {
            "delay_ratio": (
                "Timeline",
                "HIGH",
                "Re-baseline project schedule, introduce agile buffer periods, or adjust scope milestone boundaries.",
                "Reduces timeline pressure and corrects schedule deviations.",
            ),
            "cost_overrun_ratio": (
                "Cost",
                "HIGH",
                "Establish strict budget checkpoints and freeze non-essential feature development.",
                "Halts cost growth and re-allocates reserves.",
            ),
            "requirement_change_rate": (
                "Scope",
                "MEDIUM",
                "Enforce a formal change control board and freeze requirements for the next major milestone.",
                "Stabilises project scope and prevents scope creep.",
            ),
            "budget_utilization": (
                "Cost",
                "MEDIUM",
                "Conduct a financial audit to reconcile high utilization and align spend to milestone outputs.",
                "Optimises budget allocation across work packages.",
            ),
            "team_productivity": (
                "Resources",
                "HIGH",
                "Conduct task blockers review, address developer burnout, or augment the team with senior engineers.",
                "Boosts velocity and removes development bottlenecks.",
            ),
            "schedule_efficiency": (
                "Timeline",
                "MEDIUM",
                "Streamline decision pipelines and eliminate process bottlenecks that slow down code delivery.",
                "Improves throughput and schedule adherence.",
            ),
            "risk_density": (
                "General",
                "HIGH",
                "Perform an immediate dedicated risk review workshop to draft active mitigation strategies for identified items.",
                "Lowers overall project threat level.",
            ),
            "project_complexity_score": (
                "General",
                "MEDIUM",
                "Simplify system architecture or modularise project structure into independent, smaller sub-projects.",
                "Decreases cognitive load and dependency risks.",
            ),
        }

        # Select top risk factors with direction "INCREASING_RISK" (positive SHAP impact)
        target_factors = [
            rf for rf in risk_factors
            if getattr(rf, "direction", "") == "INCREASING_RISK" or getattr(rf, "impact", 0) > 0
        ]

        for rf in target_factors[:4]:  # limit recommendations to top 4 factors
            name = getattr(rf, "feature_name", "")
            display = getattr(rf, "display_name", "")

            # Look up standard mitigation rules
            rule = mitigation_rules.get(name)
            if rule:
                area, priority, action, impact = rule
                recommendations.append(
                    Recommendation(
                        priority=priority,
                        area=area,
                        action=action,
                        expected_impact=impact,
                        related_risk_factor=display,
                    )
                )

        # Default fallback recommendation if no specific rule matched
        if not recommendations:
            recommendations.append(
                Recommendation(
                    priority="MEDIUM",
                    area="General",
                    action="Review general project health metrics and hold a team alignment check-in.",
                    expected_impact="Maintains overall project baseline stability.",
                    related_risk_factor="General Risk Profile",
                )
            )

        return recommendations

    # ------------------------------------------------------------------
    # Content Builders
    # ------------------------------------------------------------------

    def _build_executive_summary(self, prediction, explanation) -> str:
        """Compose a concise 2-3 sentence overview."""
        prob_pct = int(prediction.failure_probability * 100)
        risk_lvl = prediction.risk_category

        summary = (
            f"Executive Summary: Project '{prediction.project_id}' presents a {risk_lvl} failure risk "
            f"({prob_pct}% probability). "
            f"{explanation.human_explanation.strip()}"
        )
        return summary

    def _build_detailed_explanation(self, explanation, evaluation) -> str:
        """Compose a multi-paragraph detailed analysis report."""
        method = explanation.explanation_method
        model_name = getattr(evaluation, "model_name", "model")
        grade = getattr(evaluation, "overall_grade", "N/A")

        details = (
            f"Detailed Analysis:\n\n"
            f"The Risk Assessment Engine evaluated the project using a {model_name} model "
            f"(Evaluated Grade: {grade}). The analysis method was driven by {method}.\n\n"
            f"Primary Drivers:\n"
        )

        for i, rf in enumerate(explanation.top_risk_factors[:5], 1):
            dir_str = "increases" if rf.impact > 0 else "decreases"
            details += f"{i}. '{rf.display_name}' has value {rf.value:.4f}, which {dir_str} the overall risk score (impact weight: {rf.impact:+.4f}).\n"

        return details

    # ------------------------------------------------------------------
    # Serialization and Persistency
    # ------------------------------------------------------------------

    def _serialize_report(self, report: RiskAssessmentReport) -> dict:
        """Serialize the dataclass structure to a JSON-compatible dict."""
        return asdict(report)

    def _save_report(self, serialized: dict, base_dir: Path, project_id: str) -> Path:
        """Save report dict to reports/ subdirectory."""
        reports_dir = ensure_directory(base_dir / "reports")
        filename = f"risk_report_{project_id}_{datetime.now(timezone.utc).strftime('%Y%m%d_%H%M%S')}.json"
        report_path = reports_dir / filename
        save_json(serialized, report_path)
        return report_path

    # ------------------------------------------------------------------
    # Main processing
    # ------------------------------------------------------------------

    def process(self, payload: StagePayload) -> StagePayload:
        """Execute Stage 12: compile report → recommend → serialize → save."""
        prediction = payload.artifacts["prediction_result"]
        explanation = payload.artifacts["prediction_explanation"]
        evaluation = payload.artifacts.get("evaluation_summary")
        loader_meta = payload.metadata.get("loader", {})

        cfg = self.config.report_generator
        base_dir = Path(self.config.base_dir)

        # 1. Generate recommendations
        recs = self._generate_recommendations(explanation.top_risk_factors)

        # 2. Build explanation paragraphs
        exec_summary = self._build_executive_summary(prediction, explanation)
        detailed_exp = self._build_detailed_explanation(explanation, evaluation)

        # 3. Pull pipeline & evaluation fields
        model_name = getattr(evaluation, "model_name", "n/a") if evaluation else "n/a"
        model_grade = getattr(evaluation, "overall_grade", "n/a") if evaluation else "n/a"
        model_acc = getattr(evaluation, "metrics", {}).get("accuracy", 0.0) if evaluation else 0.0
        model_f1 = getattr(evaluation, "metrics", {}).get("f1", 0.0) if evaluation else 0.0

        training_samples = 0
        if evaluation:
            training_samples = getattr(evaluation, "evaluation_dataset_size", 0)

        # Get data sources
        sources = loader_meta.get("source_files", ["unknown"])

        # Convert top features to dict list for serialization
        critical = [asdict(rf) for rf in explanation.top_risk_factors if rf.impact > 0]
        pos = [asdict(fc) for fc in explanation.positive_contributors]
        neg = [asdict(fc) for fc in explanation.negative_contributors]

        # 4. Construct final assessment report
        report = RiskAssessmentReport(
            report_id=str(uuid.uuid4()),
            report_version="1.0.0",
            generated_at=datetime.now(timezone.utc).isoformat(),
            project_name=prediction.raw_features.get("project_name", prediction.project_id) if prediction.raw_features else prediction.project_id,
            project_id=prediction.project_id,
            risk_percentage=float(prediction.risk_score),
            risk_level=prediction.risk_category,
            failure_probability=prediction.failure_probability,
            confidence_score=prediction.confidence_level,
            critical_factors=critical,
            positive_factors=pos,
            negative_factors=neg,
            recommended_actions=recs,
            model_name=model_name,
            model_grade=model_grade,
            model_accuracy=model_acc,
            model_f1_score=model_f1,
            human_summary=exec_summary,
            detailed_explanation=detailed_exp,
            pipeline_version=self.config.version,
            data_sources=sources,
            feature_count=len(prediction.processed_features) if prediction.processed_features else 0,
            training_samples=training_samples,
        )

        # 5. Serialize and save to disk
        serialized = self._serialize_report(report)
        report_path = self._save_report(serialized, base_dir, report.project_id)

        # Update payload
        payload.artifacts["risk_report"] = report
        payload.artifacts["risk_report_path"] = str(report_path.resolve())
        payload.metadata["report_generator"] = {
            "report_id": report.report_id,
            "risk_score": report.risk_percentage,
            "risk_category": report.risk_level,
            "recommendations": len(recs),
            "report_path": str(report_path.resolve()),
        }

        self.logger.info(
            "RiskReportGenerator complete — Saved report to: %s", report_path.name,
        )
        return payload

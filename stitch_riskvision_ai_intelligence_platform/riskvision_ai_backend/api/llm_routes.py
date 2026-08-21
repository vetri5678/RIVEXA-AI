"""
RIVEXA — LLM Telemetry Helper Router

Provides REST endpoints for natural language risk explanations, interactive assistant chat,
and real-time project risk remediation recommendations.
"""

from typing import List, Optional, Dict, Any
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
import logging

logger = logging.getLogger("rivexa.llm")

router = APIRouter(prefix="/api/v1/ai", tags=["AI Copilot & LLM Explanations"])


class ProjectTelemetryInput(BaseModel):
    project_id: Optional[str] = None
    project_name: Optional[str] = "Target Repository"
    failure_probability: float = Field(..., ge=0.0, le=1.0)
    risk_level: str = "HIGH"
    graveyard_index: Optional[float] = 0.65
    top_risk_factors: Optional[List[str]] = Field(default_factory=lambda: ["Schedule Slippage", "High Team Churn", "Low Test Coverage"])
    metrics: Optional[Dict[str, Any]] = Field(default_factory=dict)


class LLMExplanationResponse(BaseModel):
    project_id: Optional[str] = None
    summary: str
    key_drivers: List[str]
    actionable_recommendations: List[str]
    confidence_score: float = 0.92
    source_model: str = "RIVEXA-LLM-v1.4 (Heuristic / Generative)"


class ChatMessage(BaseModel):
    role: str  # "user" | "assistant" | "system"
    content: str


class LLMChatRequest(BaseModel):
    messages: List[ChatMessage]
    project_context: Optional[ProjectTelemetryInput] = None


class LLMChatResponse(BaseModel):
    reply: str
    suggested_actions: List[str] = Field(default_factory=list)


@router.get("/health")
def llm_health_check():
    return {
        "status": "healthy",
        "service": "RIVEXA LLM Telemetry Helper",
        "version": "1.0.0",
        "port": 5001,
        "engine": "active"
    }


@router.post("/explain", response_model=LLMExplanationResponse)
def explain_project_risk(input_data: ProjectTelemetryInput):
    """
    Generates structured natural language risk analysis and actionable recommendations
    based on project metrics and failure probability.
    """
    prob_pct = round(input_data.failure_probability * 100, 1)
    name = input_data.project_name or "Project"
    metrics = input_data.metrics or {}

    open_issues = float(metrics.get("open_issues") or 0.0)
    inactive_days = float(metrics.get("inactive_days") or 0.0)
    code_coverage = float(metrics.get("code_coverage") or 75.0)
    build_success = float(metrics.get("build_success_rate") or 90.0)
    doc_score = float(metrics.get("documentation_score") or 80.0)

    recommendations = []
    drivers = list(input_data.top_risk_factors) if input_data.top_risk_factors else []

    if inactive_days > 14:
        recommendations.append(f"Resume regular commit cadence for '{name}' (inactive for {int(inactive_days)} days).")
        if "Low Repository Activity" not in drivers:
            drivers.append("Low Repository Activity")

    if open_issues > 10:
        recommendations.append(f"Triage and resolve unresolved issue backlog ({int(open_issues)} open issues).")
        if "High Open Issue Backlog" not in drivers:
            drivers.append("High Open Issue Backlog")

    if code_coverage < 60.0:
        recommendations.append(f"Increase automated test coverage (currently at {code_coverage:.1f}%).")
        if "Insufficient Test Coverage" not in drivers:
            drivers.append("Insufficient Test Coverage")

    if build_success < 80.0:
        recommendations.append(f"Stabilize CI/CD pipeline and resolve build failures ({build_success:.1f}% success rate).")
        if "Elevated Build Failure Rate" not in drivers:
            drivers.append("Elevated Build Failure Rate")

    if doc_score < 60.0:
        recommendations.append(f"Expand setup and API documentation (documentation score: {doc_score:.1f}/100).")
        if "Documentation Coverage Gap" not in drivers:
            drivers.append("Documentation Coverage Gap")

    if not recommendations:
        recommendations = [
            f"Maintain current development velocity and automated CI testing standard for '{name}'.",
            "Perform periodic security dependency scans.",
            "Schedule quarterly architectural health checks."
        ]

    if not drivers:
        drivers = ["Nominal Telemetry Metrics", "Active Maintainer Cadence"]

    if input_data.failure_probability >= 0.7:
        summary = (
            f"CRITICAL WARNING: '{name}' exhibits severe failure indicators with a {prob_pct}% probability "
            f"of project demise. Immediate architectural and maintainer intervention is required."
        )
    elif input_data.failure_probability >= 0.4:
        summary = (
            f"MODERATE ELEVATED RISK: '{name}' has a {prob_pct}% risk index. Telemetry trends indicate "
            f"potential maintenance bottlenecks or testing gaps requiring attention."
        )
    else:
        summary = (
            f"STABLE HEALTH: '{name}' maintains strong failure resistance ({prob_pct}% risk probability). "
            f"Telemetry metrics align with healthy engineering baseline standards."
        )

    return LLMExplanationResponse(
        project_id=input_data.project_id,
        summary=summary,
        key_drivers=drivers[:5],
        actionable_recommendations=recommendations,
        confidence_score=0.94,
        source_model="RIVEXA-LLM-v1.4 Engine"
    )


@router.post("/chat", response_model=LLMChatResponse)
def chat_with_copilot(request: LLMChatRequest):
    """
    Handles interactive conversations with the floating AI Assistant in the React Dashboard.
    """
    if not request.messages:
        raise HTTPException(status_code=400, detail="Messages array cannot be empty")
        
    last_user_msg = next((m.content for m in reversed(request.messages) if m.role == "user"), "")
    lower_msg = last_user_msg.lower()
    
    ctx = request.project_context
    ctx_str = f" for '{ctx.project_name}'" if ctx and ctx.project_name else ""
    
    if "risk" in lower_msg or "fail" in lower_msg or "graveyard" in lower_msg:
        reply = (
            f"Analyzing failure telemetry{ctx_str}: Primary risk drivers are team turnover velocity, "
            f"untested commit spikes, and requirement churn. Would you like me to generate a mitigation plan?"
        )
        suggested = ["Generate Mitigation Plan", "View Top Vulnerable Files", "Run Re-training Model"]
    elif "recommend" in lower_msg or "fix" in lower_msg or "help" in lower_msg:
        reply = (
            f"Based on current project health metrics{ctx_str}, I recommend: "
            f"1. Enforce strict PR review policies. 2. Implement automated regression testing. 3. Balance workload distribution."
        )
        suggested = ["Export PDF Risk Report", "Schedule Team Audit"]
    else:
        reply = (
            f"RIVEXA Assistant online. I am monitoring operational telemetry{ctx_str}. "
            f"Ask me about failure probability, risk driver breakdowns, or mitigation strategies."
        )
        suggested = ["Check System Latency", "Run Model Diagnostic", "View Repositories"]
        
    return LLMChatResponse(
        reply=reply,
        suggested_actions=suggested
    )

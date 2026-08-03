"""
RiskVision AI — LLM Telemetry Helper Router

Provides REST endpoints for natural language risk explanations, interactive assistant chat,
and real-time project risk remediation recommendations.
"""

from typing import List, Optional, Dict, Any
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
import logging

logger = logging.getLogger("riskvision.llm")

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
    source_model: str = "RiskVision-LLM-v1.4 (Heuristic / Generative)"


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
        "service": "RiskVision AI LLM Telemetry Helper",
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
    
    if input_data.failure_probability >= 0.7:
        summary = (
            f"CRITICAL WARNING: '{name}' exhibits severe failure indicators with a {prob_pct}% probability "
            f"of project demise. Immediate architectural and management intervention is required."
        )
        recommendations = [
            "Freeze non-essential feature development and execute code stability sprint.",
            "Re-assign senior engineers to decrease single-point dependency risks.",
            "Increase automated unit and integration test coverage above 80% threshold.",
            "Conduct daily risk mitigation standups focusing on blocking technical debt."
        ]
    elif input_data.failure_probability >= 0.4:
        summary = (
            f"MODERATE ELEVATED RISK: '{name}' has a {prob_pct}% risk index. While currently functional, "
            f"telemetry trends indicate potential schedule slippage and growing code debt."
        )
        recommendations = [
            "Perform targeted refactoring on modules with high churn rate.",
            "Automate build pipeline validation to reduce manual release friction.",
            "Review pull request review latency to clear developer bottlenecks."
        ]
    else:
        summary = (
            f"STABLE HEALTH: '{name}' maintains strong failure resistance ({prob_pct}% risk probability). "
            f"Telemetry metrics align with high-performing engineering baseline standards."
        )
        recommendations = [
            "Maintain existing CI/CD test automation standard.",
            "Schedule quarterly dependency security audits.",
            "Document architectural patterns for onboarding new contributors."
        ]
        
    drivers = input_data.top_risk_factors or ["High Commit Friction", "Code Complexity Escalation"]
    
    return LLMExplanationResponse(
        project_id=input_data.project_id,
        summary=summary,
        key_drivers=drivers,
        actionable_recommendations=recommendations,
        confidence_score=0.94,
        source_model="RiskVision-LLM-v1.4 Engine"
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
            f"RiskVision AI Assistant online. I am monitoring operational telemetry{ctx_str}. "
            f"Ask me about failure probability, risk driver breakdowns, or mitigation strategies."
        )
        suggested = ["Check System Latency", "Run Model Diagnostic", "View Repositories"]
        
    return LLMChatResponse(
        reply=reply,
        suggested_actions=suggested
    )

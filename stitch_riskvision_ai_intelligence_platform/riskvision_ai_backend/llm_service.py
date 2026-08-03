"""
RiskVision AI — LLM Telemetry Helper Service

Main entry point to run the standalone LLM service on port 5001.
Usage: python llm_service.py
"""

import sys
import os
from pathlib import Path

_BACKEND_ROOT = Path(__file__).parent.resolve()
sys.path.append(str(_BACKEND_ROOT))

import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from api.llm_routes import router as llm_router

app = FastAPI(
    title="RiskVision AI — LLM Telemetry Helper",
    description="Generative and heuristic AI service providing project failure explanations and copilot chat.",
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(llm_router)


@app.get("/")
def llm_root():
    return {
        "service": "RiskVision AI LLM Telemetry Helper",
        "status": "online",
        "port": 5001,
        "docs_url": "/docs"
    }


if __name__ == "__main__":
    port = int(os.getenv("LLM_PORT", 5001))
    uvicorn.run("llm_service:app", host="0.0.0.0", port=port, reload=False)

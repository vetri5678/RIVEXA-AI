"""
RiskVision AI — Main Entry Point

Integrates the FastAPI app server and the command-line interface (CLI).
Allows running the 12-stage pipeline either via REST endpoints or directly
through terminal commands.
"""

import argparse
import io
import json
import logging
import sys
import os
from pathlib import Path

import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

# Ensure the working directory is the backend root so that relative paths
# (e.g. config/pipeline_config.yaml, data/) resolve correctly even when
# launched from the project root via `npm run backend`.
_BACKEND_ROOT = Path(__file__).parent.resolve()
os.chdir(_BACKEND_ROOT)

# Add the root directory to path to ensure absolute imports work when run as script
sys.path.append(str(_BACKEND_ROOT))

from api.routes import router as api_router, state as api_state
from src.pipeline.base import StagePayload
from src.pipeline.config import load_config
from src.pipeline.exceptions import PipelineError

from contextlib import asynccontextmanager
from core.database import init_db, get_db_context
from services.auth_service import AuthService
from core.config import get_settings

# =============================================================================
# FastAPI Application Setup
# =============================================================================

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Initialize DB tables
    init_db()
    # Bootstrap default super admin user if empty
    with get_db_context() as db:
        AuthService.bootstrap_admin(db)
    yield

app = FastAPI(
    title="RiskVision AI — Software Project Graveyard Analyzer",
    description="Computational intelligence API for predicting and explaining software project failure risks.",
    version="1.0.0",
    lifespan=lifespan,
)

# Enable CORS for frontend/dashboard interaction
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include routes
app.include_router(api_router)


@app.get("/")
def read_root():
    return {
        "app": "RiskVision AI Graveyard Analyzer Backend",
        "status": "online",
        "docs_url": "/docs",
        "api_v1_url": "/api/v1"
    }


# =============================================================================
# Logging Setup
# =============================================================================

def setup_logging(verbose: bool = False) -> None:
    """Configure structured logging for both CLI and API server."""
    level = logging.DEBUG if verbose else logging.INFO
    # Wrap stdout in a UTF-8 TextIOWrapper so Unicode log characters
    # (e.g. ✓ and →) do not raise UnicodeEncodeError on Windows (cp1252).
    utf8_stdout = io.TextIOWrapper(
        sys.stdout.buffer, encoding="utf-8", errors="replace", line_buffering=True
    )
    logging.basicConfig(
        level=level,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        handlers=[
            logging.StreamHandler(utf8_stdout),
            logging.FileHandler("pipeline.log", encoding="utf-8")
        ]
    )


# =============================================================================
# CLI Handlers
# =============================================================================

def handle_cli_train(args) -> None:
    """CLI handler for running training (Stages 1-9)."""
    logger = logging.getLogger("riskvision.cli")
    logger.info("Starting Pipeline Training CLI...")

    # Load configuration
    try:
        config = load_config(args.config)
    except Exception as exc:
        logger.error("Failed to load config: %s", exc)
        sys.exit(1)

    # Instantiate orchestrator
    from api.routes import state as global_state
    
    logger.info("Executing training stages for files: %s", args.files)
    try:
        payload = global_state.orchestrator.run_training_pipeline(args.files)
        training_result = payload.artifacts.get("training_result")
        
        print("\n" + "=" * 50)
        print("TRAINING SUCCESSFUL")
        print("=" * 50)
        print(f"Best Model: {training_result.best_model_name}")
        print(f"Best CV Score (F1): {training_result.best_score:.4f}")
        print(f"Model Saved to: {training_result.model_path}")
        print(f"Warnings during execution: {payload.get_error_count('WARNING')}")
        print("=" * 50 + "\n")
        
    except PipelineError as exc:
        logger.error("Pipeline fatal error during training: %s", exc)
        sys.exit(1)
    except Exception as exc:
        logger.error("Unexpected error: %s", exc)
        sys.exit(1)


def handle_cli_predict(args) -> None:
    """CLI handler for running prediction (Stages 10-12)."""
    logger = logging.getLogger("riskvision.cli")
    logger.info("Starting Prediction CLI...")

    # Parse input project data
    if args.data_file:
        try:
            with open(args.data_file, "r", encoding="utf-8") as f:
                project_data = json.load(f)
        except Exception as exc:
            logger.error("Failed to read JSON data file: %s", exc)
            sys.exit(1)
    else:
        # Prompt or require fields if not passed as file
        if not args.project_id or not args.budget or not args.timeline:
            logger.error("Must specify either --data-file or individual features (--project-id, --budget, --timeline).")
            sys.exit(1)
        project_data = {
            "project_id": args.project_id,
            "project_name": args.project_name or args.project_id,
            "budget": args.budget,
            "actual_cost": args.actual_cost,
            "timeline_months": args.timeline,
            "actual_duration": args.actual_duration,
            "team_size": args.team_size,
            "status": args.status,
            "requirements_changed": args.req_changed,
            "total_requirements": args.req_total,
            "features_delivered": args.features_delivered,
            "identified_risks": args.risks,
            "total_tasks": args.tasks,
        }

    # Verify model is available
    from api.routes import state as global_state
    if not global_state.best_model or not global_state.transformer_artifacts:
        if not global_state.try_load_latest_artifacts():
            logger.error("No trained model artifacts found. Please run training first.")
            sys.exit(1)

    try:
        # Run prediction
        payload = StagePayload(config=global_state.config)
        payload.artifacts["best_model"] = global_state.best_model
        payload.artifacts["transformer_artifacts"] = global_state.transformer_artifacts
        
        # Enrich with engineered features (same as API route)
        from api.routes import _enrich_with_engineered_features
        enriched_data = _enrich_with_engineered_features(project_data)

        res_payload = global_state.orchestrator.run_prediction_pipeline(
            project_data=enriched_data,
            payload=payload
        )
        
        report = res_payload.artifacts["risk_report"]
        
        print("\n" + "=" * 60)
        print(f"RISK ASSESSMENT REPORT — {report.project_id} ({report.project_name})")
        print("=" * 60)
        print(f"Risk Score:    {report.risk_percentage:.1f}%")
        print(f"Risk Level:    {report.risk_level}")
        print(f"Prediction:    {report.prediction_label}")
        print(f"Confidence:    {report.confidence_score * 100:.1f}%")
        print("-" * 60)
        print("EXECUTIVE SUMMARY:")
        print(report.human_summary)
        print("-" * 60)
        print("RECOMMENDED ACTIONS:")
        for idx, rec in enumerate(report.recommended_actions, 1):
            print(f"{idx}. [{rec.priority}][{rec.area}] {rec.action}")
            print(f"   (Impact: {rec.expected_impact} | Driver: {rec.related_risk_factor})")
        print("-" * 60)
        print(f"Report saved to: {res_payload.artifacts['risk_report_path']}")
        print("=" * 60 + "\n")

    except PipelineError as exc:
        logger.error("Pipeline fatal error during prediction: %s", exc)
        sys.exit(1)
    except Exception as exc:
        logger.error("Unexpected error during prediction: %s", exc)
        sys.exit(1)


# =============================================================================
# Main
# =============================================================================

def main():
    parser = argparse.ArgumentParser(
        description="RiskVision AI — Software Project Graveyard Analyzer Engine CLI."
    )
    parser.add_argument(
        "--config", default="config/pipeline_config.yaml", help="Path to pipeline_config.yaml"
    )
    parser.add_argument(
        "--verbose", "-v", action="store_true", help="Enable debug-level logs"
    )

    subparsers = parser.add_subparsers(dest="command", help="Command to run")

    # Server Subcommand
    server_parser = subparsers.add_parser("server", help="Start the FastAPI REST server")
    server_parser.add_argument("--host", default="127.0.0.1", help="Server host IP")
    server_parser.add_argument("--port", type=int, default=get_settings().port, help="Server port number")

    # Train Subcommand
    train_parser = subparsers.add_parser("train", help="Run the pipeline training stages (1-9)")
    train_parser.add_argument(
        "--files", "-f", nargs="+", required=True, help="List of raw data file paths"
    )

    # Predict Subcommand
    predict_parser = subparsers.add_parser("predict", help="Predict risk for a single project (Stages 10-12)")
    predict_parser.add_argument(
        "--data-file", help="Path to a JSON file containing the project metrics"
    )
    predict_parser.add_argument("--project-id", help="Project ID")
    predict_parser.add_argument("--project-name", help="Project name")
    predict_parser.add_argument("--budget", type=float, help="Planned budget")
    predict_parser.add_argument("--actual-cost", type=float, default=0.0, help="Actual cost incurred")
    predict_parser.add_argument("--timeline", type=float, help="Planned duration (months)")
    predict_parser.add_argument("--actual-duration", type=float, default=0.0, help="Actual duration elapsed")
    predict_parser.add_argument("--team-size", type=float, default=1.0, help="Active team size")
    predict_parser.add_argument("--status", default="active", help="Current status")
    
    predict_parser.add_argument("--req-changed", type=float, default=0.0, help="Requirements changed count")
    predict_parser.add_argument("--req-total", type=float, default=1.0, help="Total requirements count")
    predict_parser.add_argument("--features-delivered", type=float, default=0.0, help="Features delivered count")
    predict_parser.add_argument("--risks", type=float, default=0.0, help="Identified risks count")
    predict_parser.add_argument("--tasks", type=float, default=1.0, help="Total tasks count")

    args = parser.parse_args()

    # If no command is specified, default to running the server
    if args.command is None:
        args.command = "server"
        args.host = "127.0.0.1"
        args.port = get_settings().port

    setup_logging(args.verbose)

    if args.command == "server":
        print(f"Starting RiskVision AI API Server on {args.host}:{args.port}...")
        uvicorn.run("main:app", host=args.host, port=args.port, reload=False)
    elif args.command == "train":
        handle_cli_train(args)
    elif args.command == "predict":
        handle_cli_predict(args)


if __name__ == "__main__":
    main()

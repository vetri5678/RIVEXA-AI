"""
RiskVision AI — Pipeline Base Classes

Defines the core abstractions used by every pipeline stage:
  - StageError: structured error record
  - StagePayload: inter-stage data transfer object
  - PipelineStage: abstract base class for all stages
  - PipelineOrchestrator: sequential stage executor
"""

import logging
import time
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any, Optional

import pandas as pd

from src.pipeline.config import PipelineConfig
from src.pipeline.exceptions import PipelineFatalError

logger = logging.getLogger("riskvision.pipeline")


# =============================================================================
# Data Transfer Objects
# =============================================================================

@dataclass
class StageError:
    """A structured record of a non-fatal issue encountered during processing."""
    stage: str
    severity: str          # "WARNING" | "ERROR"
    message: str
    timestamp: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())


@dataclass
class StagePayload:
    """
    Inter-stage data transfer object.

    This is the single object that flows through every pipeline stage.
    Each stage reads from it, processes, and writes back into it.

    Attributes
    ----------
    data : pd.DataFrame | None
        The primary dataset being processed.
    metadata : dict
        Accumulated metadata from all prior stages.
        Keyed by stage name, e.g. metadata["loader"] = {...}.
    artifacts : dict[str, Any]
        Serializable artifacts produced by stages
        (scalers, encoders, models, reports, splits).
    config : PipelineConfig
        Immutable pipeline configuration.
    errors : list[StageError]
        Non-fatal warnings/errors collected during processing.
    """
    data: Optional[pd.DataFrame] = None
    metadata: dict = field(default_factory=dict)
    artifacts: dict = field(default_factory=dict)
    config: PipelineConfig = field(default_factory=PipelineConfig)
    errors: list = field(default_factory=list)

    def add_error(self, stage: str, severity: str, message: str) -> None:
        """Append a non-fatal error to the payload."""
        self.errors.append(StageError(
            stage=stage,
            severity=severity,
            message=message,
        ))

    def get_error_count(self, severity: Optional[str] = None) -> int:
        """Count errors, optionally filtered by severity."""
        if severity:
            return sum(1 for e in self.errors if e.severity == severity)
        return len(self.errors)

    def has_data(self) -> bool:
        """Check if the payload contains a non-empty DataFrame."""
        return self.data is not None and not self.data.empty


# =============================================================================
# Abstract Pipeline Stage
# =============================================================================

class PipelineStage(ABC):
    """
    Abstract base class for all pipeline stages.

    Every stage must implement:
      - validate_input(): check preconditions on the incoming payload
      - process(): core processing logic
      - get_stage_name(): return a human-readable stage name

    The public execute() method handles logging, timing, and error wrapping.
    """

    def __init__(self, config: PipelineConfig):
        self.config = config
        self.stage_name = self.get_stage_name()
        self.logger = logging.getLogger(f"riskvision.pipeline.{self.stage_name}")

    @abstractmethod
    def get_stage_name(self) -> str:
        """Return the human-readable name of this stage."""
        ...

    @abstractmethod
    def validate_input(self, payload: StagePayload) -> None:
        """
        Validate that the incoming payload meets this stage's preconditions.

        Raises PipelineFatalError for unrecoverable issues.
        Appends warnings to payload.errors for recoverable issues.
        """
        ...

    @abstractmethod
    def process(self, payload: StagePayload) -> StagePayload:
        """
        Execute the core processing logic of this stage.

        Parameters
        ----------
        payload : StagePayload
            The validated input payload.

        Returns
        -------
        StagePayload
            The payload with updated data, metadata, and/or artifacts.
        """
        ...

    def execute(self, payload: StagePayload) -> StagePayload:
        """
        Public entry point. Wraps validate_input + process with
        logging, timing, and error handling.
        """
        self.logger.info("=" * 60)
        self.logger.info("STAGE START: %s", self.stage_name)
        self.logger.info("=" * 60)

        start_time = time.time()

        try:
            # --- Validate input ---
            self.logger.info("Validating input...")
            self.validate_input(payload)
            self.logger.info("Input validation passed.")

            # Log input shape
            if payload.has_data():
                self.logger.info(
                    "Input shape: %d rows × %d columns",
                    payload.data.shape[0], payload.data.shape[1]
                )

            # --- Process ---
            self.logger.info("Processing...")
            payload = self.process(payload)

            # Log output shape
            if payload.has_data():
                self.logger.info(
                    "Output shape: %d rows × %d columns",
                    payload.data.shape[0], payload.data.shape[1]
                )

        except PipelineFatalError:
            self.logger.error("FATAL error in stage %s", self.stage_name, exc_info=True)
            raise
        except Exception as e:
            self.logger.error(
                "Unexpected error in stage %s: %s", self.stage_name, str(e),
                exc_info=True
            )
            raise PipelineFatalError(
                f"Unexpected error: {str(e)}", stage=self.stage_name
            ) from e
        finally:
            elapsed = time.time() - start_time
            self.logger.info(
                "STAGE END: %s (%.2fs, %d warnings)",
                self.stage_name, elapsed, payload.get_error_count("WARNING")
            )

        # Record timing metadata
        payload.metadata.setdefault("timings", {})[self.stage_name] = round(elapsed, 3)

        return payload

    def get_stage_metadata(self) -> dict:
        """Return metadata describing this stage's configuration."""
        return {
            "stage_name": self.stage_name,
            "stage_class": self.__class__.__name__,
        }


# =============================================================================
# Pipeline Orchestrator
# =============================================================================

class PipelineOrchestrator:
    """
    Orchestrates the sequential execution of all pipeline stages.

    Provides three execution modes:
      - run_training_pipeline: Stages 1–9 (preprocess + train)
      - run_prediction_pipeline: Stages 10–12 (predict on new data)
      - run_full_pipeline: All 12 stages end-to-end
    """

    def __init__(self, config: PipelineConfig):
        self.config = config
        self.logger = logging.getLogger("riskvision.pipeline.orchestrator")
        self.preprocessing_stages: list[PipelineStage] = []
        self.training_stages: list[PipelineStage] = []
        self.prediction_stages: list[PipelineStage] = []

    def register_preprocessing_stages(self, stages: list[PipelineStage]) -> None:
        """Register stages 1–7 (Data Preprocessing Module)."""
        self.preprocessing_stages = stages
        self.logger.info("Registered %d preprocessing stages.", len(stages))

    def register_training_stages(self, stages: list[PipelineStage]) -> None:
        """Register stages 8–9 (Model Training & Evaluation)."""
        self.training_stages = stages
        self.logger.info("Registered %d training stages.", len(stages))

    def register_prediction_stages(self, stages: list[PipelineStage]) -> None:
        """Register stages 10–12 (Prediction, Explanation, Reporting)."""
        self.prediction_stages = stages
        self.logger.info("Registered %d prediction stages.", len(stages))

    def _run_stages(self, stages: list[PipelineStage], payload: StagePayload) -> StagePayload:
        """Execute a list of stages sequentially, passing payload between them."""
        for stage in stages:
            payload = stage.execute(payload)
        return payload

    def run_training_pipeline(self, file_paths: list[str]) -> StagePayload:
        """
        Execute the full training pipeline: preprocessing (stages 1–7) +
        model training & evaluation (stages 8–9).

        Parameters
        ----------
        file_paths : list[str]
            Paths to raw data files to ingest.

        Returns
        -------
        StagePayload
            Final payload containing trained model, evaluation summary, and
            all accumulated artifacts.
        """
        self.logger.info("=" * 70)
        self.logger.info("TRAINING PIPELINE START — %s v%s", self.config.name, self.config.version)
        self.logger.info("=" * 70)

        start_time = time.time()

        payload = StagePayload(config=self.config)
        payload.metadata["file_paths"] = file_paths
        payload.metadata["pipeline_start"] = datetime.now(timezone.utc).isoformat()

        # Execute preprocessing
        all_stages = self.preprocessing_stages + self.training_stages
        payload = self._run_stages(all_stages, payload)

        elapsed = time.time() - start_time
        payload.metadata["pipeline_duration_seconds"] = round(elapsed, 3)

        self.logger.info(
            "TRAINING PIPELINE COMPLETE (%.2fs, %d total warnings)",
            elapsed, payload.get_error_count("WARNING")
        )
        return payload

    def run_prediction_pipeline(self, project_data: dict, payload: StagePayload = None) -> StagePayload:
        """
        Execute the prediction pipeline (stages 10–12) on new project data.

        Parameters
        ----------
        project_data : dict
            Raw project information to predict on.
        payload : StagePayload, optional
            Pre-existing payload with model and transformer artifacts.
            If None, artifacts are loaded from disk.

        Returns
        -------
        StagePayload
            Final payload containing the risk assessment report.
        """
        self.logger.info("=" * 70)
        self.logger.info("PREDICTION PIPELINE START")
        self.logger.info("=" * 70)

        start_time = time.time()

        if payload is None:
            payload = StagePayload(config=self.config)

        payload.metadata["prediction_input"] = project_data

        payload = self._run_stages(self.prediction_stages, payload)

        elapsed = time.time() - start_time
        payload.metadata["prediction_duration_seconds"] = round(elapsed, 3)

        self.logger.info("PREDICTION PIPELINE COMPLETE (%.2fs)", elapsed)
        return payload

    def run_full_pipeline(
        self, file_paths: list[str], project_data: dict
    ) -> StagePayload:
        """
        Execute all 12 stages: preprocess → train → predict → explain → report.

        Parameters
        ----------
        file_paths : list[str]
            Raw data file paths for training.
        project_data : dict
            New project data for prediction.

        Returns
        -------
        StagePayload
            Final payload with complete risk assessment report.
        """
        self.logger.info("=" * 70)
        self.logger.info("FULL PIPELINE START — %s v%s", self.config.name, self.config.version)
        self.logger.info("=" * 70)

        start_time = time.time()

        # Training pipeline
        payload = self.run_training_pipeline(file_paths)

        # Prediction pipeline
        payload.metadata["prediction_input"] = project_data
        payload = self._run_stages(self.prediction_stages, payload)

        elapsed = time.time() - start_time
        payload.metadata["full_pipeline_duration_seconds"] = round(elapsed, 3)

        self.logger.info("FULL PIPELINE COMPLETE (%.2fs)", elapsed)
        return payload

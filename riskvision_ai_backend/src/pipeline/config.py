"""
RiskVision AI — Pipeline Configuration Loader & Validator

Loads pipeline_config.yaml and exposes typed configuration sections
for each pipeline stage. Validates required fields on load.
"""

import logging
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Optional

import yaml

from src.pipeline.exceptions import ConfigurationError

logger = logging.getLogger("riskvision.pipeline.config")


# =============================================================================
# Configuration Dataclasses
# =============================================================================

@dataclass
class DataLoaderConfig:
    """Configuration for Stage 1: Data Loader."""
    supported_formats: list = field(default_factory=lambda: ["csv", "xlsx", "json"])
    required_columns: list = field(default_factory=lambda: [
        "project_id", "budget", "timeline_months", "team_size", "status"
    ])
    merge_strategy: str = "concat"
    join_key: Optional[str] = "project_id"


@dataclass
class DataInspectorConfig:
    """Configuration for Stage 2: Data Inspector."""
    outlier_method: str = "iqr"
    outlier_threshold: float = 1.5
    missing_threshold_pct: float = 50.0


@dataclass
class DataCleanerConfig:
    """Configuration for Stage 3: Data Cleaner."""
    missing_strategy: str = "median"
    duplicate_subset: Optional[list] = None
    date_format: str = "%Y-%m-%d"
    category_mapping_file: str = "config/category_mappings.json"


@dataclass
class DataTransformerConfig:
    """Configuration for Stage 4: Data Transformer."""
    encoding_strategy: str = "onehot"
    scaling_strategy: str = "standard"
    text_columns: list = field(default_factory=lambda: ["status", "risk_category"])
    # Column to protect from encoding/scaling — the classification target label.
    target_column: str = "project_failed"


@dataclass
class FeatureEngineeringConfig:
    """Configuration for Stage 5: Feature Engineering."""
    enabled_features: list = field(default_factory=lambda: [
        "delay_ratio",
        "cost_overrun_ratio",
        "requirement_change_rate",
        "budget_utilization",
        "team_productivity",
        "schedule_efficiency",
        "risk_density",
        "project_complexity_score",
    ])


@dataclass
class DataValidatorConfig:
    """Configuration for Stage 6: Data Validator."""
    expected_feature_count: Optional[int] = None
    allowed_dtypes: list = field(default_factory=lambda: ["float64", "int64", "uint8"])
    max_null_tolerance: int = 0


@dataclass
class DatasetSplitterConfig:
    """Configuration for Stage 7: Dataset Splitter."""
    train_ratio: float = 0.70
    val_ratio: float = 0.15
    test_ratio: float = 0.15
    stratify_column: str = "project_failed"
    shuffle: bool = True


@dataclass
class AlgorithmConfig:
    """Configuration for a single ML algorithm."""
    name: str = "random_forest"
    params: dict = field(default_factory=dict)


@dataclass
class ModelTrainerConfig:
    """Configuration for Stage 8: Model Trainer."""
    algorithms: list = field(default_factory=lambda: [
        {"name": "random_forest", "params": {"n_estimators": 200, "max_depth": 12}},
        {"name": "xgboost", "params": {"n_estimators": 300, "learning_rate": 0.05, "max_depth": 8}},
        {"name": "gradient_boosting", "params": {"n_estimators": 200, "learning_rate": 0.1}},
        {"name": "logistic_regression", "params": {"max_iter": 1000, "C": 1.0}},
    ])
    selection_metric: str = "f1"
    cross_validation_folds: int = 5


@dataclass
class ModelEvaluatorConfig:
    """Configuration for Stage 9: Model Evaluator."""
    metrics: list = field(default_factory=lambda: [
        "accuracy", "precision", "recall", "f1", "roc_auc"
    ])
    generate_confusion_matrix: bool = True
    cross_validation_folds: int = 5


@dataclass
class ExplainabilityConfig:
    """Configuration for Stage 11: Explainability Engine."""
    shap_method: str = "tree"
    top_features: int = 10
    generate_waterfall: bool = True


@dataclass
class ReportGeneratorConfig:
    """Configuration for Stage 12: Risk Report Generator."""
    output_format: str = "json"
    include_timestamp: bool = True
    include_confidence: bool = True


@dataclass
class PipelineConfig:
    """Master configuration object containing all stage configs."""
    name: str = "riskvision_graveyard_analyzer"
    version: str = "1.0.0"
    random_seed: int = 42

    data_loader: DataLoaderConfig = field(default_factory=DataLoaderConfig)
    data_inspector: DataInspectorConfig = field(default_factory=DataInspectorConfig)
    data_cleaner: DataCleanerConfig = field(default_factory=DataCleanerConfig)
    data_transformer: DataTransformerConfig = field(default_factory=DataTransformerConfig)
    feature_engineering: FeatureEngineeringConfig = field(default_factory=FeatureEngineeringConfig)
    data_validator: DataValidatorConfig = field(default_factory=DataValidatorConfig)
    dataset_splitter: DatasetSplitterConfig = field(default_factory=DatasetSplitterConfig)
    model_trainer: ModelTrainerConfig = field(default_factory=ModelTrainerConfig)
    model_evaluator: ModelEvaluatorConfig = field(default_factory=ModelEvaluatorConfig)
    explainability: ExplainabilityConfig = field(default_factory=ExplainabilityConfig)
    report_generator: ReportGeneratorConfig = field(default_factory=ReportGeneratorConfig)

    base_dir: str = "."


# =============================================================================
# Config Loader
# =============================================================================

def _parse_section(raw: dict, section_key: str, dataclass_type: type) -> Any:
    """Parse a YAML section into a typed dataclass, using defaults for missing keys."""
    section_data = raw.get(section_key, {})
    if section_data is None:
        section_data = {}
    # Only pass keys that the dataclass accepts
    valid_keys = {f.name for f in dataclass_type.__dataclass_fields__.values()}
    filtered = {k: v for k, v in section_data.items() if k in valid_keys}
    return dataclass_type(**filtered)


def load_config(config_path: str | Path) -> PipelineConfig:
    """
    Load and validate the pipeline configuration from a YAML file.

    Parameters
    ----------
    config_path : str or Path
        Path to the pipeline_config.yaml file.

    Returns
    -------
    PipelineConfig
        Fully populated, validated configuration object.

    Raises
    ------
    ConfigurationError
        If the config file cannot be read or contains invalid values.
    """
    config_path = Path(config_path)

    if not config_path.exists():
        raise ConfigurationError(
            f"Configuration file not found: {config_path}"
        )

    try:
        with open(config_path, "r", encoding="utf-8") as f:
            raw = yaml.safe_load(f)
    except yaml.YAMLError as e:
        raise ConfigurationError(f"Invalid YAML in config file: {e}")

    if not raw or not isinstance(raw, dict):
        raise ConfigurationError("Configuration file is empty or not a mapping.")

    # Extract top-level pipeline settings
    pipeline_section = raw.get("pipeline", {})

    config = PipelineConfig(
        name=pipeline_section.get("name", PipelineConfig.name),
        version=pipeline_section.get("version", PipelineConfig.version),
        random_seed=pipeline_section.get("random_seed", PipelineConfig.random_seed),
        data_loader=_parse_section(raw, "data_loader", DataLoaderConfig),
        data_inspector=_parse_section(raw, "data_inspector", DataInspectorConfig),
        data_cleaner=_parse_section(raw, "data_cleaner", DataCleanerConfig),
        data_transformer=_parse_section(raw, "data_transformer", DataTransformerConfig),
        feature_engineering=_parse_section(raw, "feature_engineering", FeatureEngineeringConfig),
        data_validator=_parse_section(raw, "data_validator", DataValidatorConfig),
        dataset_splitter=_parse_section(raw, "dataset_splitter", DatasetSplitterConfig),
        model_trainer=_parse_section(raw, "model_trainer", ModelTrainerConfig),
        model_evaluator=_parse_section(raw, "model_evaluator", ModelEvaluatorConfig),
        explainability=_parse_section(raw, "explainability", ExplainabilityConfig),
        report_generator=_parse_section(raw, "report_generator", ReportGeneratorConfig),
        base_dir=str(config_path.parent.parent),
    )

    _validate_config(config)

    logger.info("Pipeline configuration loaded successfully from: %s", config_path)
    return config


def _validate_config(config: PipelineConfig) -> None:
    """Run validation checks on the loaded configuration."""

    # Validate split ratios sum to 1.0
    splitter = config.dataset_splitter
    total_ratio = splitter.train_ratio + splitter.val_ratio + splitter.test_ratio
    if not (0.99 <= total_ratio <= 1.01):
        raise ConfigurationError(
            f"Dataset split ratios must sum to 1.0, got {total_ratio:.3f} "
            f"(train={splitter.train_ratio}, val={splitter.val_ratio}, test={splitter.test_ratio})"
        )

    # Validate trainer has at least one algorithm
    if not config.model_trainer.algorithms:
        raise ConfigurationError(
            "Model trainer must have at least one algorithm configured."
        )

    # Validate selection metric is valid
    valid_metrics = {"accuracy", "precision", "recall", "f1", "roc_auc"}
    if config.model_trainer.selection_metric not in valid_metrics:
        raise ConfigurationError(
            f"Invalid selection metric: '{config.model_trainer.selection_metric}'. "
            f"Valid options: {valid_metrics}"
        )

    # Validate cross-validation folds
    if config.model_trainer.cross_validation_folds < 2:
        raise ConfigurationError(
            "Cross-validation folds must be >= 2."
        )

    # Validate outlier method
    valid_outlier_methods = {"iqr", "zscore"}
    if config.data_inspector.outlier_method not in valid_outlier_methods:
        raise ConfigurationError(
            f"Invalid outlier method: '{config.data_inspector.outlier_method}'. "
            f"Valid options: {valid_outlier_methods}"
        )

    logger.info("Configuration validation passed.")

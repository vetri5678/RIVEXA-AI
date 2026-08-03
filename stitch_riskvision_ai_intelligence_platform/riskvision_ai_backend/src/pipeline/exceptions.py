"""
RiskVision AI — Custom Exception Hierarchy

All pipeline-related exceptions inherit from PipelineError.
Each stage has domain-specific exceptions for granular error handling.
"""


# =============================================================================
# Base Pipeline Exceptions
# =============================================================================

class PipelineError(Exception):
    """Root exception for all pipeline errors."""

    def __init__(self, message: str, stage: str = "unknown", severity: str = "FATAL"):
        self.stage = stage
        self.severity = severity
        super().__init__(f"[{severity}][{stage}] {message}")


class PipelineFatalError(PipelineError):
    """Non-recoverable pipeline error. Halts execution immediately."""

    def __init__(self, message: str, stage: str = "unknown"):
        super().__init__(message, stage=stage, severity="FATAL")


class PipelineWarning(PipelineError):
    """Recoverable pipeline issue. Logged but execution continues."""

    def __init__(self, message: str, stage: str = "unknown"):
        super().__init__(message, stage=stage, severity="WARNING")


# =============================================================================
# Stage 1: Data Loader Exceptions
# =============================================================================

class UnsupportedFormatError(PipelineFatalError):
    """Raised when an input file has an unsupported format."""

    def __init__(self, file_path: str, detected_format: str = "unknown"):
        self.file_path = file_path
        self.detected_format = detected_format
        super().__init__(
            f"Unsupported file format '{detected_format}' for file: {file_path}",
            stage="DataLoader"
        )


class EmptyDatasetError(PipelineFatalError):
    """Raised when a loaded or processed dataset has zero rows."""

    def __init__(self, source: str = "unknown", stage: str = "DataLoader"):
        self.source = source
        super().__init__(
            f"Dataset is empty (0 rows) from source: {source}",
            stage=stage
        )


class SchemaValidationError(PipelineFatalError):
    """Raised when required columns are missing from the dataset."""

    def __init__(self, missing_columns: list, available_columns: list = None):
        self.missing_columns = missing_columns
        self.available_columns = available_columns or []
        cols_str = ", ".join(missing_columns)
        super().__init__(
            f"Schema validation failed. Missing columns: [{cols_str}]",
            stage="DataLoader"
        )


class DataMergeError(PipelineFatalError):
    """Raised when dataset merging fails."""

    def __init__(self, message: str):
        super().__init__(message, stage="DataLoader")


# =============================================================================
# Stage 3: Data Cleaner Exceptions
# =============================================================================

class CleaningError(PipelineError):
    """Raised when a data cleaning operation fails."""

    def __init__(self, message: str, severity: str = "WARNING"):
        super().__init__(message, stage="DataCleaner", severity=severity)


# =============================================================================
# Stage 4: Data Transformer Exceptions
# =============================================================================

class TransformationError(PipelineError):
    """Raised when data transformation fails."""

    def __init__(self, message: str, column: str = None, severity: str = "WARNING"):
        self.column = column
        col_info = f" (column: {column})" if column else ""
        super().__init__(
            f"Transformation failed{col_info}: {message}",
            stage="DataTransformer",
            severity=severity
        )


# =============================================================================
# Stage 5: Feature Engineering Exceptions
# =============================================================================

class FeatureEngineeringError(PipelineFatalError):
    """Raised when feature engineering fails critically."""

    def __init__(self, message: str):
        super().__init__(message, stage="FeatureEngineer")


class FeatureCreationWarning(PipelineWarning):
    """Raised when a single feature cannot be created but others can."""

    def __init__(self, feature_name: str, reason: str):
        self.feature_name = feature_name
        super().__init__(
            f"Cannot create feature '{feature_name}': {reason}",
            stage="FeatureEngineer"
        )


# =============================================================================
# Stage 6: Data Validator Exceptions
# =============================================================================

class ValidationError(PipelineFatalError):
    """Raised when dataset validation fails with FATAL severity checks."""

    def __init__(self, failed_checks: list):
        self.failed_checks = failed_checks
        checks_str = ", ".join(failed_checks)
        super().__init__(
            f"Validation failed on checks: [{checks_str}]",
            stage="DataValidator"
        )


# =============================================================================
# Stage 7: Dataset Splitter Exceptions
# =============================================================================

class TargetColumnError(PipelineFatalError):
    """Raised when the target column for splitting is not found."""

    def __init__(self, target_column: str, available_columns: list):
        self.target_column = target_column
        self.available_columns = available_columns
        super().__init__(
            f"Target column '{target_column}' not found. Available: {available_columns}",
            stage="DatasetSplitter"
        )


class ConfigurationError(PipelineFatalError):
    """Raised for invalid pipeline configuration."""

    def __init__(self, message: str, stage: str = "Configuration"):
        super().__init__(message, stage=stage)


# =============================================================================
# Stage 8: Model Trainer Exceptions
# =============================================================================

class InsufficientDataError(PipelineFatalError):
    """Raised when training data has too few samples."""

    def __init__(self, sample_count: int, minimum_required: int = 50):
        self.sample_count = sample_count
        self.minimum_required = minimum_required
        super().__init__(
            f"Insufficient training data: {sample_count} samples "
            f"(minimum required: {minimum_required})",
            stage="ModelTrainer"
        )


class ModelTrainingError(PipelineFatalError):
    """Raised when all model training attempts fail."""

    def __init__(self, message: str):
        super().__init__(message, stage="ModelTrainer")


# =============================================================================
# Stage 10: Prediction Engine Exceptions
# =============================================================================

class ModelNotFoundError(PipelineFatalError):
    """Raised when the trained model file cannot be located."""

    def __init__(self, model_path: str):
        self.model_path = model_path
        super().__init__(
            f"Trained model not found at: {model_path}",
            stage="PredictionEngine"
        )


class FeatureMismatchError(PipelineFatalError):
    """Raised when input features don't match model's expected features."""

    def __init__(self, expected: list, received: list):
        self.expected = expected
        self.received = received
        missing = set(expected) - set(received)
        extra = set(received) - set(expected)
        super().__init__(
            f"Feature mismatch. Missing: {missing}, Extra: {extra}",
            stage="PredictionEngine"
        )


class ArtifactCorruptionError(PipelineFatalError):
    """Raised when saved artifacts (encoders, scalers) cannot be loaded."""

    def __init__(self, artifact_path: str, reason: str = ""):
        self.artifact_path = artifact_path
        super().__init__(
            f"Corrupted artifact at '{artifact_path}': {reason}",
            stage="PredictionEngine"
        )


# =============================================================================
# Stage 12: Report Generator Exceptions
# =============================================================================

class ReportGenerationError(PipelineFatalError):
    """Raised when report generation fails due to missing data."""

    def __init__(self, message: str):
        super().__init__(message, stage="ReportGenerator")

"""
RiskVision AI — Stage 1: Data Loader

Reads one or more raw data files, validates their schema against
the required columns list, optionally merges multi-file datasets,
and collects loading metadata.
"""

import logging
from datetime import datetime, timezone
from pathlib import Path
from functools import reduce

import pandas as pd

from src.pipeline.base import PipelineStage, StagePayload
from src.pipeline.exceptions import (
    UnsupportedFormatError,
    EmptyDatasetError,
    SchemaValidationError,
    DataMergeError,
)
from src.utils.file_utils import detect_file_format, read_csv_file, read_excel_file, read_json_file

logger = logging.getLogger("riskvision.pipeline.DataLoader")


class DataLoaderStage(PipelineStage):
    """
    Pipeline Stage 1 — Data Loader.

    Responsibilities:
      1. Read raw data files in supported formats (CSV, XLSX, JSON).
      2. Validate that every required column is present.
      3. Merge multi-source datasets via concatenation or join.
      4. Collect loading metadata for downstream stages.
    """

    # ------------------------------------------------------------------
    # PipelineStage interface
    # ------------------------------------------------------------------

    def get_stage_name(self) -> str:
        return "DataLoader"

    def validate_input(self, payload: StagePayload) -> None:
        """Ensure ``payload.metadata['file_paths']`` is a non-empty list."""
        file_paths = payload.metadata.get("file_paths")
        if not file_paths or not isinstance(file_paths, list):
            raise EmptyDatasetError(
                source="metadata.file_paths",
                stage="DataLoader",
            )

        # Verify every path actually exists
        for fp in file_paths:
            path = Path(fp)
            if not path.exists():
                raise FileNotFoundError(
                    f"Data file not found: {path}"
                )

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------

    def _read_single_file(self, file_path: str) -> pd.DataFrame:
        """Detect the format of *file_path* and delegate to the correct reader."""
        path = Path(file_path)
        try:
            fmt = detect_file_format(path)
        except ValueError:
            raise UnsupportedFormatError(
                file_path=str(path),
                detected_format=path.suffix,
            )

        readers = {
            "csv": read_csv_file,
            "xlsx": read_excel_file,
            "json": read_json_file,
        }

        reader = readers.get(fmt)
        if reader is None:
            raise UnsupportedFormatError(file_path=str(path), detected_format=fmt)

        self.logger.info("Reading %s file: %s", fmt.upper(), path.name)
        df = reader(path)

        if df.empty:
            raise EmptyDatasetError(source=str(path), stage="DataLoader")

        self.logger.info(
            "  → loaded %d rows × %d columns from %s",
            df.shape[0], df.shape[1], path.name,
        )
        return df

    def _validate_schema(
        self, df: pd.DataFrame, required_columns: list[str]
    ) -> None:
        """Raise :class:`SchemaValidationError` if required columns are missing."""
        missing = [col for col in required_columns if col not in df.columns]
        if missing:
            raise SchemaValidationError(
                missing_columns=missing,
                available_columns=list(df.columns),
            )

    def _merge_datasets(
        self,
        dataframes: list[pd.DataFrame],
        strategy: str,
        join_key: str | None,
    ) -> pd.DataFrame:
        """
        Merge multiple DataFrames using the configured strategy.

        Parameters
        ----------
        dataframes : list[pd.DataFrame]
            DataFrames to merge.
        strategy : str
            ``"concat"`` for vertical stacking, ``"join"`` for key-based merge.
        join_key : str | None
            Column to join on when *strategy* is ``"join"``.
        """
        if len(dataframes) == 1:
            return dataframes[0]

        self.logger.info(
            "Merging %d datasets using strategy='%s'", len(dataframes), strategy,
        )

        if strategy == "concat":
            merged = pd.concat(dataframes, ignore_index=True)
        elif strategy == "join":
            if not join_key:
                raise DataMergeError(
                    "Merge strategy is 'join' but no join_key is configured."
                )
            try:
                merged = reduce(
                    lambda left, right: pd.merge(left, right, on=join_key, how="outer"),
                    dataframes,
                )
            except Exception as exc:
                raise DataMergeError(f"Merge failed on key '{join_key}': {exc}")
        else:
            raise DataMergeError(f"Unsupported merge strategy: '{strategy}'")

        self.logger.info(
            "  → merged result: %d rows × %d columns", merged.shape[0], merged.shape[1],
        )
        return merged

    def _collect_metadata(
        self, df: pd.DataFrame, source_files: list[str]
    ) -> dict:
        """Build a metadata dict describing the loaded dataset."""
        return {
            "source_files": source_files,
            "row_count": int(df.shape[0]),
            "column_count": int(df.shape[1]),
            "columns": list(df.columns),
            "dtypes": {col: str(dtype) for col, dtype in df.dtypes.items()},
            "loaded_at": datetime.now(timezone.utc).isoformat(),
        }

    # ------------------------------------------------------------------
    # Main processing
    # ------------------------------------------------------------------

    def process(self, payload: StagePayload) -> StagePayload:
        """Execute Stage 1: load → validate → merge → metadata."""
        file_paths: list[str] = payload.metadata["file_paths"]
        cfg = self.config.data_loader

        # 1. Read all files
        dataframes: list[pd.DataFrame] = []
        for fp in file_paths:
            df = self._read_single_file(fp)
            dataframes.append(df)

        # 2. Merge datasets
        merged = self._merge_datasets(dataframes, cfg.merge_strategy, cfg.join_key)

        # 3. Schema validation
        self._validate_schema(merged, cfg.required_columns)

        # 4. Collect metadata
        loader_meta = self._collect_metadata(merged, file_paths)

        # 5. Update payload
        payload.data = merged
        payload.metadata["loader"] = loader_meta

        self.logger.info(
            "DataLoader complete — %d rows × %d columns from %d source(s).",
            merged.shape[0], merged.shape[1], len(file_paths),
        )
        return payload

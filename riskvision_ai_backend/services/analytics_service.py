"""Dashboard analytics service with optimized aggregation queries."""

from collections import Counter, defaultdict
from datetime import datetime, timedelta, timezone
from typing import List

from sqlalchemy import func
from sqlalchemy.orm import Session

from models.model_version import ModelVersion
from models.prediction import PredictionRecord
from models.project import Project
from models.user import User
from schemas.analytics import (
    AnalyticsDashboardResponse,
    DashboardSummary,
    ModelAccuracySummary,
    MonthlyStatistics,
    RecentPrediction,
    RiskDistribution,
    TopRiskFactor,
    TrendPoint,
)


class AnalyticsService:
    """Aggregates platform metrics for dashboard display."""

    @staticmethod
    def get_dashboard(db: Session) -> AnalyticsDashboardResponse:
        now = datetime.now(timezone.utc)
        thirty_days_ago = now - timedelta(days=30)

        total_projects = db.query(Project).filter(Project.is_deleted == False).count()
        total_predictions = db.query(PredictionRecord).filter(PredictionRecord.is_deleted == False).count()
        active_users = db.query(User).filter(User.is_active == True).count()
        total_models = db.query(ModelVersion).filter(ModelVersion.is_deleted == False).count()

        # Risk level counts from projects
        risk_counts = dict(
            db.query(Project.latest_risk_level, func.count(Project.id))
            .filter(Project.is_deleted == False, Project.latest_risk_level.isnot(None))
            .group_by(Project.latest_risk_level)
            .all()
        )

        summary = DashboardSummary(
            total_projects=total_projects,
            total_predictions=total_predictions,
            high_risk_projects=risk_counts.get("HIGH", 0),
            medium_risk_projects=risk_counts.get("MEDIUM", 0),
            low_risk_projects=risk_counts.get("LOW", 0),
            critical_risk_projects=risk_counts.get("CRITICAL", 0),
            active_users=active_users,
            total_models=total_models,
        )

        # Risk distribution from predictions
        pred_risk = dict(
            db.query(PredictionRecord.risk_level, func.count(PredictionRecord.id))
            .filter(PredictionRecord.is_deleted == False)
            .group_by(PredictionRecord.risk_level)
            .all()
        )
        risk_distribution = RiskDistribution(
            critical=pred_risk.get("CRITICAL", 0),
            high=pred_risk.get("HIGH", 0),
            medium=pred_risk.get("MEDIUM", 0),
            low=pred_risk.get("LOW", 0),
        )

        # Prediction trends (last 7 days)
        trends: List[TrendPoint] = []
        for i in range(6, -1, -1):
            day = (now - timedelta(days=i)).date()
            day_start = datetime.combine(day, datetime.min.time()).replace(tzinfo=timezone.utc)
            day_end = day_start + timedelta(days=1)
            day_preds = db.query(PredictionRecord).filter(
                PredictionRecord.is_deleted == False,
                PredictionRecord.predicted_at >= day_start,
                PredictionRecord.predicted_at < day_end,
            ).all()
            avg_score = sum(p.risk_score for p in day_preds) / len(day_preds) if day_preds else 0.0
            trends.append(TrendPoint(period=day.isoformat(), count=len(day_preds), avg_risk_score=round(avg_score, 1)))

        # Monthly statistics (last 6 months)
        monthly: List[MonthlyStatistics] = []
        for i in range(5, -1, -1):
            month_date = now - timedelta(days=30 * i)
            month_str = month_date.strftime("%Y-%m")
            month_preds = db.query(PredictionRecord).filter(
                PredictionRecord.is_deleted == False,
                func.strftime("%Y-%m", PredictionRecord.predicted_at) == month_str,
            ).all() if hasattr(PredictionRecord.predicted_at.type, "python_type") else []

            # Fallback for SQLite strftime
            all_recent = db.query(PredictionRecord).filter(
                PredictionRecord.is_deleted == False,
                PredictionRecord.predicted_at >= now - timedelta(days=180),
            ).all()
            month_preds = [p for p in all_recent if p.predicted_at.strftime("%Y-%m") == month_str]

            avg_prob = sum(p.failure_probability for p in month_preds) / len(month_preds) if month_preds else 0.0
            high_count = sum(1 for p in month_preds if p.risk_level in ("HIGH", "CRITICAL"))
            monthly.append(MonthlyStatistics(
                month=month_str,
                predictions=len(month_preds),
                avg_failure_probability=round(avg_prob, 3),
                high_risk_count=high_count,
            ))

        # Model accuracy from active model version
        active_model = db.query(ModelVersion).filter(ModelVersion.is_active == True).first()
        model_accuracy = ModelAccuracySummary(
            model_name=active_model.model_name if active_model else None,
            model_grade=active_model.overall_grade if active_model else None,
            accuracy=active_model.accuracy if active_model else None,
            f1_score=active_model.f1_score if active_model else None,
            roc_auc=active_model.roc_auc if active_model else None,
            total_predictions=total_predictions,
        )

        # Recent predictions
        recent = (
            db.query(PredictionRecord)
            .filter(PredictionRecord.is_deleted == False)
            .order_by(PredictionRecord.predicted_at.desc())
            .limit(10)
            .all()
        )
        recent_predictions = [
            RecentPrediction(
                id=p.id,
                external_project_id=p.external_project_id,
                project_name=p.project_name,
                risk_level=p.risk_level,
                risk_score=p.risk_score,
                predicted_at=p.predicted_at.isoformat(),
            )
            for p in recent
        ]

        # Top risk factors aggregated from SHAP data
        factor_impacts: dict[str, list] = defaultdict(list)
        factor_names: dict[str, str] = {}
        recent_for_factors = (
            db.query(PredictionRecord)
            .filter(PredictionRecord.is_deleted == False)
            .order_by(PredictionRecord.predicted_at.desc())
            .limit(100)
            .all()
        )
        for pred in recent_for_factors:
            for factor in pred.top_risk_factors or []:
                if isinstance(factor, dict):
                    fname = factor.get("feature_name", "unknown")
                    factor_impacts[fname].append(abs(factor.get("impact", 0)))
                    factor_names[fname] = factor.get("display_name", fname)

        top_factors = sorted(
            [
                TopRiskFactor(
                    feature_name=fname,
                    display_name=factor_names.get(fname, fname),
                    avg_impact=round(sum(impacts) / len(impacts), 4),
                    occurrence_count=len(impacts),
                )
                for fname, impacts in factor_impacts.items()
            ],
            key=lambda x: x.avg_impact,
            reverse=True,
        )[:10]

        return AnalyticsDashboardResponse(
            summary=summary,
            risk_distribution=risk_distribution,
            prediction_trends=trends,
            monthly_statistics=monthly,
            model_accuracy=model_accuracy,
            recent_predictions=recent_predictions,
            top_risk_factors=top_factors,
        )

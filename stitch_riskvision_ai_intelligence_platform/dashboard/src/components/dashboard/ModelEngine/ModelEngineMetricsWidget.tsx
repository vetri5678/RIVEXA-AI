import React from 'react';
import WidgetWrapper from '../Common/WidgetWrapper';
import { useMLMetrics, useMLVersion, useMLModel } from '../../../hooks/useMLPrediction';
import { Brain, CheckCircle2, Target, TrendingUp, Zap, BarChart3 } from 'lucide-react';

export const ModelEngineMetricsWidget: React.FC = () => {
  const { data: metrics, isLoading: metricsLoading, isError: metricsError, refetch } = useMLMetrics();
  const { data: version, isLoading: versionLoading } = useMLVersion();
  const { data: model } = useMLModel();

  const isLoading = metricsLoading || versionLoading;

  const metricCards = [
    {
      label: 'Accuracy',
      value: metrics?.accuracy !== undefined ? `${(metrics.accuracy * 100).toFixed(1)}%` : '—',
      icon: CheckCircle2,
      color: 'text-emerald-400',
      bg: 'from-emerald-500/20 to-emerald-500/5',
      border: 'border-emerald-500/30',
    },
    {
      label: 'Precision',
      value: metrics?.precision !== undefined ? `${(metrics.precision * 100).toFixed(1)}%` : '—',
      icon: Target,
      color: 'text-blue-400',
      bg: 'from-blue-500/20 to-blue-500/5',
      border: 'border-blue-500/30',
    },
    {
      label: 'Recall',
      value: metrics?.recall !== undefined ? `${(metrics.recall * 100).toFixed(1)}%` : '—',
      icon: TrendingUp,
      color: 'text-cyan-400',
      bg: 'from-cyan-500/20 to-cyan-500/5',
      border: 'border-cyan-500/30',
    },
    {
      label: 'F1 Score',
      value: metrics?.f1_score !== undefined ? `${(metrics.f1_score * 100).toFixed(1)}%` : '—',
      icon: BarChart3,
      color: 'text-purple-400',
      bg: 'from-purple-500/20 to-purple-500/5',
      border: 'border-purple-500/30',
    },
    {
      label: 'ROC-AUC',
      value: metrics?.roc_auc !== undefined ? `${(metrics.roc_auc * 100).toFixed(1)}%` : '—',
      icon: Zap,
      color: 'text-amber-400',
      bg: 'from-amber-500/20 to-amber-500/5',
      border: 'border-amber-500/30',
    },
    {
      label: 'CV Mean',
      value: metrics?.cross_val_mean !== undefined ? `${(metrics.cross_val_mean * 100).toFixed(1)}%` : '—',
      icon: Brain,
      color: 'text-rose-400',
      bg: 'from-rose-500/20 to-rose-500/5',
      border: 'border-rose-500/30',
    },
  ];

  return (
    <WidgetWrapper
      title="ML MODEL PERFORMANCE METRICS"
      subtitle={`${version?.modelName ?? 'RandomForest'} ${version?.modelVersion ?? ''} — Trained on ${model?.dataset_records?.toLocaleString() ?? '—'} records`}
      isLoading={isLoading}
      isError={metricsError}
      onRetry={refetch}
      headerActions={
        <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[9px] font-mono font-bold bg-emerald-500/15 text-emerald-400 border border-emerald-500/30">
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
          {version?.status ?? 'Development Model'}
        </span>
      }
    >
      <div className="grid grid-cols-2 sm:grid-cols-3 gap-2.5 py-1 font-mono">
        {metricCards.map((card) => (
          <div
            key={card.label}
            className={`p-3 rounded-xl bg-gradient-to-br ${card.bg} border ${card.border} hover:scale-[1.02] transition-transform duration-200`}
          >
            <div className="flex items-center gap-1.5 mb-1.5">
              <card.icon size={11} className={card.color} />
              <span className="text-[9px] uppercase tracking-wider text-slate-400 font-bold">{card.label}</span>
            </div>
            <span className={`text-lg font-extrabold ${card.color} block`}>
              {isLoading ? '…' : card.value}
            </span>
          </div>
        ))}
      </div>
      {model && (
        <div className="mt-3 pt-2.5 border-t border-white/[0.05] flex items-center justify-between text-[10px] font-mono text-slate-500">
          <span>Trained: {model.trained_at ? new Date(model.trained_at).toLocaleDateString() : '—'}</span>
          <span>v{model.model_version ?? '—'}</span>
        </div>
      )}
    </WidgetWrapper>
  );
};

export default ModelEngineMetricsWidget;

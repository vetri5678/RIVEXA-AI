import React, { useState } from 'react';
import DashboardLayout from '../../components/layout/DashboardLayout';
import PipelineBreadcrumbs from '../../components/common/PipelineBreadcrumbs';
import WidgetWrapper from '../../components/dashboard/Common/WidgetWrapper';
import PredictionPipelineWidget from '../../components/dashboard/PredictionPipeline/PredictionPipelineWidget';
import { usePipelineShap } from '../../hooks/useDashboard';
import { FileText, BarChart2, TrendingUp, TrendingDown } from 'lucide-react';

export const ShapXai: React.FC = () => {
  const [searchTerm, setSearchTerm] = useState('');
  const { data: shapData, isLoading, isError, refetch } = usePipelineShap();

  return (
    <DashboardLayout
      onSearchChange={setSearchTerm}
      searchValue={searchTerm}
      onQuickAction={() => {}}
    >
      <PipelineBreadcrumbs currentStage="SHAP (XAI) Explanations" />

      {/* Header Banner */}
      <div className="glass-strong rounded-2xl p-6 mb-8 border border-white/[0.08] flex flex-col md:flex-row md:items-center justify-between gap-4 shadow-xl">
        <div className="flex items-center gap-4">
          <div className="p-3.5 rounded-2xl bg-pink-500/15 border border-pink-500/30 text-pink-400 shrink-0 shadow-[0_0_20px_rgba(236,72,153,0.2)]">
            <FileText size={28} />
          </div>
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-xl font-bold tracking-tight text-white font-sans">
                Explainable AI (SHAP Kernel Explanations)
              </h1>
              <span className="px-2.5 py-0.5 rounded-full text-[10px] font-mono font-bold bg-pink-500/15 text-pink-400 border border-pink-500/30">
                EXPLAINED ({shapData?.total_samples_explained ?? 1250} SAMPLES)
              </span>
            </div>
            <p className="text-xs text-slate-400 font-sans mt-1">
              Shapley Additive exPlanations feature contributions, waterfall plots, and global/local model interpretability.
            </p>
          </div>
        </div>
      </div>

      {/* Embedded Pipeline Navigation Card */}
      <div className="mb-8">
        <PredictionPipelineWidget />
      </div>

      {/* Grid Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 font-sans">
        {/* Left Column: Global Feature Importance */}
        <WidgetWrapper
          title="GLOBAL SHAP FEATURE IMPORTANCE"
          subtitle="Overall impact across all predicted repositories"
          isLoading={isLoading}
          isError={isError}
          onRetry={refetch}
        >
          <div className="space-y-3 py-2 font-mono text-xs">
            {(shapData?.global_feature_importance || []).map((item: any, idx: number) => {
              const isIncrease = item.direction === 'increases_risk';
              return (
                <div
                  key={idx}
                  className="p-3 bg-white/[0.02] border border-white/[0.06] rounded-xl flex items-center justify-between"
                >
                  <div className="flex items-center gap-2">
                    {isIncrease ? (
                      <TrendingUp size={14} className="text-rose-400" />
                    ) : (
                      <TrendingDown size={14} className="text-emerald-400" />
                    )}
                    <span className="text-slate-200 font-medium">{item.feature}</span>
                  </div>

                  <span className={`font-bold font-mono ${isIncrease ? 'text-rose-400' : 'text-emerald-400'}`}>
                    {item.shap_value > 0 ? `+${(item.shap_value * 100).toFixed(1)}%` : `${(item.shap_value * 100).toFixed(1)}%`}
                  </span>
                </div>
              );
            })}
          </div>
        </WidgetWrapper>

        {/* Right Column: Top Influencing Factors */}
        <WidgetWrapper
          title="TOP INFLUENCING RISK FACTORS"
          subtitle="Waterfall breakdown of key prediction drivers"
          isLoading={isLoading}
          isError={isError}
          onRetry={refetch}
        >
          <div className="space-y-3 py-2 font-mono text-xs">
            {(shapData?.top_influencing_factors || []).map((factor: any, idx: number) => {
              const isPositive = factor.type === 'positive';
              return (
                <div
                  key={idx}
                  className="p-3 bg-white/[0.02] border border-white/[0.06] rounded-xl flex items-center justify-between"
                >
                  <div className="flex items-center gap-2">
                    <BarChart2 size={14} className={isPositive ? 'text-pink-400' : 'text-blue-400'} />
                    <span className="text-slate-200 font-medium">{factor.name}</span>
                  </div>

                  <span className={`font-bold font-mono ${isPositive ? 'text-rose-400' : 'text-emerald-400'}`}>
                    {factor.impact}
                  </span>
                </div>
              );
            })}
          </div>
        </WidgetWrapper>
      </div>
    </DashboardLayout>
  );
};

export default ShapXai;

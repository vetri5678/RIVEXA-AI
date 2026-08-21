import React, { useState } from 'react';
import DashboardLayout from '../../components/layout/DashboardLayout';
import PipelineBreadcrumbs from '../../components/common/PipelineBreadcrumbs';
import WidgetWrapper from '../../components/dashboard/Common/WidgetWrapper';
import PredictionPipelineWidget from '../../components/dashboard/PredictionPipeline/PredictionPipelineWidget';
import { usePipelineCleanse } from '../../hooks/useDashboard';
import { Hammer, ShieldCheck, Filter, AlertTriangle, Layers } from 'lucide-react';

export const DataCleansing: React.FC = () => {
  const [searchTerm, setSearchTerm] = useState('');
  const { data: cleanseData, isLoading, isError, refetch } = usePipelineCleanse();

  return (
    <DashboardLayout
      onSearchChange={setSearchTerm}
      searchValue={searchTerm}
      onQuickAction={() => {}}
    >
      <PipelineBreadcrumbs currentStage="Data Cleansing" />

      {/* Header Banner */}
      <div className="glass-strong rounded-2xl p-6 mb-8 border border-white/[0.08] flex flex-col md:flex-row md:items-center justify-between gap-4 shadow-xl">
        <div className="flex items-center gap-4">
          <div className="p-3.5 rounded-2xl bg-amber-500/15 border border-amber-500/30 text-amber-400 shrink-0 shadow-[0_0_20px_rgba(245,158,11,0.2)]">
            <Hammer size={28} />
          </div>
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-xl font-bold tracking-tight text-white font-sans">
                Data Cleansing & Preprocessing Stage
              </h1>
              <span className="px-2.5 py-0.5 rounded-full text-[10px] font-mono font-bold bg-amber-500/15 text-amber-400 border border-amber-500/30">
                QUALITY: {cleanseData?.data_quality_score ?? 96.8}%
              </span>
            </div>
            <p className="text-xs text-slate-400 font-sans mt-1">
              Duplicate record removal, missing value imputation, z-score outlier filtering, and min-max feature scaling.
            </p>
          </div>
        </div>
      </div>

      {/* Embedded Pipeline Navigation Card */}
      <div className="mb-8">
        <PredictionPipelineWidget />
      </div>

      {/* Content Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 font-sans">
        {/* Left Column: Quality Metrics */}
        <div className="lg:col-span-1 space-y-6">
          <WidgetWrapper
            title="CLEANSING METRICS"
            subtitle="Normalized dataset parameters"
            isLoading={isLoading}
            isError={isError}
            onRetry={refetch}
          >
            <div className="space-y-3 py-2 font-mono text-xs">
              <div className="p-3 bg-white/[0.02] border border-white/[0.06] rounded-xl flex justify-between items-center">
                <span className="text-slate-400 flex items-center gap-2">
                  <Filter size={14} className="text-amber-400" /> Duplicates Removed
                </span>
                <span className="text-amber-300 font-bold text-sm">{cleanseData?.duplicate_records_removed ?? 14}</span>
              </div>

              <div className="p-3 bg-white/[0.02] border border-white/[0.06] rounded-xl flex justify-between items-center">
                <span className="text-slate-400 flex items-center gap-2">
                  <AlertTriangle size={14} className="text-cyan-400" /> Missing Imputed
                </span>
                <span className="text-cyan-300 font-bold text-sm">{cleanseData?.missing_values_imputed ?? 42}</span>
              </div>

              <div className="p-3 bg-white/[0.02] border border-white/[0.06] rounded-xl flex justify-between items-center">
                <span className="text-slate-400 flex items-center gap-2">
                  <Layers size={14} className="text-emerald-400" /> Scaled Features
                </span>
                <span className="text-emerald-300 font-bold text-sm">{cleanseData?.features_normalized ?? 36}</span>
              </div>

              <div className="p-3 bg-white/[0.02] border border-white/[0.06] rounded-xl flex justify-between items-center">
                <span className="text-slate-400 flex items-center gap-2">
                  <ShieldCheck size={14} className="text-purple-400" /> Noise Reduction
                </span>
                <span className="text-purple-300 font-bold text-sm">{cleanseData?.noise_reduction_pct ?? 99.4}%</span>
              </div>
            </div>
          </WidgetWrapper>
        </div>

        {/* Right Column: Validation Summary */}
        <div className="lg:col-span-2 space-y-6">
          <WidgetWrapper
            title="VALIDATION SUMMARY"
            subtitle="Dataset health checks & sample quarantine"
            isLoading={isLoading}
            isError={isError}
            onRetry={refetch}
          >
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 py-2 font-mono text-xs">
              <div className="p-3 bg-white/[0.02] border border-white/[0.06] rounded-xl">
                <span className="text-slate-500 text-[10px] block">TOTAL SAMPLES</span>
                <span className="text-base font-bold text-white mt-1 block">
                  {cleanseData?.validation_summary?.total_samples_processed ?? '—'}
                </span>
              </div>
              <div className="p-3 bg-white/[0.02] border border-white/[0.06] rounded-xl">
                <span className="text-slate-500 text-[10px] block">PASSED SAMPLES</span>
                <span className="text-base font-bold text-emerald-400 mt-1 block">
                  {cleanseData?.validation_summary?.passed_samples ?? '—'}
                </span>
              </div>
              <div className="p-3 bg-white/[0.02] border border-white/[0.06] rounded-xl">
                <span className="text-slate-500 text-[10px] block">QUARANTINED</span>
                <span className="text-base font-bold text-amber-400 mt-1 block">
                  {cleanseData?.validation_summary?.quarantined_samples ?? 0}
                </span>
              </div>
              <div className="p-3 bg-white/[0.02] border border-white/[0.06] rounded-xl">
                <span className="text-slate-500 text-[10px] block">RULE CHECKS</span>
                <span className="text-base font-bold text-purple-400 mt-1 block">
                  {cleanseData?.validation_summary?.validation_rule_checks ?? '—'}
                </span>
              </div>
            </div>
          </WidgetWrapper>
        </div>
      </div>
    </DashboardLayout>
  );
};

export default DataCleansing;

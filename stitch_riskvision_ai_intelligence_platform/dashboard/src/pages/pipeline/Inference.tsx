import React, { useState } from 'react';
import DashboardLayout from '../../components/layout/DashboardLayout';
import PipelineBreadcrumbs from '../../components/common/PipelineBreadcrumbs';
import WidgetWrapper from '../../components/dashboard/Common/WidgetWrapper';
import PredictionPipelineWidget from '../../components/dashboard/PredictionPipeline/PredictionPipelineWidget';
import { usePipelineInference } from '../../hooks/useDashboard';
import { CheckCircle2, Zap, AlertTriangle, ShieldCheck, Layers } from 'lucide-react';

export const Inference: React.FC = () => {
  const [searchTerm, setSearchTerm] = useState('');
  const { data: inferenceData, isLoading, isError, refetch } = usePipelineInference();

  return (
    <DashboardLayout
      onSearchChange={setSearchTerm}
      searchValue={searchTerm}
      onQuickAction={() => {}}
    >
      <PipelineBreadcrumbs currentStage="Inference Engine" />

      {/* Header Banner */}
      <div className="glass-strong rounded-2xl p-6 mb-8 border border-white/[0.08] flex flex-col md:flex-row md:items-center justify-between gap-4 shadow-xl">
        <div className="flex items-center gap-4">
          <div className="p-3.5 rounded-2xl bg-emerald-500/15 border border-emerald-500/30 text-emerald-400 shrink-0 shadow-[0_0_20px_rgba(16,185,129,0.2)]">
            <CheckCircle2 size={28} />
          </div>
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-xl font-bold tracking-tight text-white font-sans">
                Real-Time Risk Inference & Predictions
              </h1>
              <span className="px-2.5 py-0.5 rounded-full text-[10px] font-mono font-bold bg-emerald-500/15 text-emerald-400 border border-emerald-500/30">
                ACTIVE QUEUE (IDLE)
              </span>
            </div>
            <p className="text-xs text-slate-400 font-sans mt-1">
              Live probability distribution scoring, confidence index computation, batch inference stream, and category breakdown.
            </p>
          </div>
        </div>
      </div>

      {/* Embedded Pipeline Navigation Card */}
      <div className="mb-8">
        <PredictionPipelineWidget />
      </div>

      {/* Grid Layout */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 font-sans">
        {/* Left Column: Inference Parameters */}
        <div className="lg:col-span-1 space-y-6">
          <WidgetWrapper
            title="INFERENCE BENCHMARKS"
            subtitle="Latency & throughput statistics"
            isLoading={isLoading}
            isError={isError}
            onRetry={refetch}
          >
            <div className="space-y-3 py-2 font-mono text-xs">
              <div className="p-3 bg-white/[0.02] border border-white/[0.06] rounded-xl flex justify-between items-center">
                <span className="text-slate-400 flex items-center gap-2">
                  <Zap size={14} className="text-emerald-400" /> Avg Latency
                </span>
                <span className="text-emerald-300 font-bold text-sm">{inferenceData?.average_prediction_time_ms ?? 18.4}ms</span>
              </div>

              <div className="p-3 bg-white/[0.02] border border-white/[0.06] rounded-xl flex justify-between items-center">
                <span className="text-slate-400 flex items-center gap-2">
                  <ShieldCheck size={14} className="text-cyan-400" /> Avg Confidence
                </span>
                <span className="text-cyan-300 font-bold text-sm">
                  {((inferenceData?.average_confidence_score ?? 0.94) * 100).toFixed(1)}%
                </span>
              </div>

              <div className="p-3 bg-white/[0.02] border border-white/[0.06] rounded-xl flex justify-between items-center">
                <span className="text-slate-400 flex items-center gap-2">
                  <AlertTriangle size={14} className="text-rose-400" /> At-Risk Count
                </span>
                <span className="text-rose-300 font-bold text-sm">{inferenceData?.high_risk_category_count ?? 3}</span>
              </div>

              <div className="p-3 bg-white/[0.02] border border-white/[0.06] rounded-xl flex justify-between items-center">
                <span className="text-slate-400 flex items-center gap-2">
                  <Layers size={14} className="text-purple-400" /> Batch Size
                </span>
                <span className="text-purple-300 font-bold text-sm">{inferenceData?.batch_size ?? 64}</span>
              </div>
            </div>
          </WidgetWrapper>
        </div>

        {/* Right Column: Live Predictions Table */}
        <div className="lg:col-span-2">
          <WidgetWrapper
            title="LATEST REPOSITORY INFERENCE STREAM"
            subtitle="Scored failure probabilities & risk classifications"
            isLoading={isLoading}
            isError={isError}
            onRetry={refetch}
          >
            <div className="space-y-2 py-2 font-mono text-xs">
              {(inferenceData?.recent_predictions || []).map((pred: any, idx: number) => {
                const isHigh = pred.risk_category === 'HIGH' || pred.risk_category === 'CRITICAL';
                return (
                  <div
                    key={idx}
                    className="p-3 bg-white/[0.02] border border-white/[0.06] rounded-xl flex items-center justify-between gap-3"
                  >
                    <div>
                      <span className="text-white font-bold block">{pred.repository_name}</span>
                      <span className="text-[10px] text-slate-500">Confidence: {((pred.confidence || 0.94) * 100).toFixed(1)}%</span>
                    </div>

                    <div className="flex items-center gap-4">
                      <div className="text-right">
                        <span className={`font-bold block ${isHigh ? 'text-rose-400' : 'text-emerald-400'}`}>
                          Risk Score: {pred.risk_score}%
                        </span>
                        <span className="text-[9px] text-slate-500">Prob: {(pred.failure_probability || 0).toFixed(2)}</span>
                      </div>

                      <span className={`px-2 py-1 rounded text-[9px] font-bold ${
                        isHigh
                          ? 'bg-rose-500/15 text-rose-300 border border-rose-500/30'
                          : 'bg-emerald-500/15 text-emerald-300 border border-emerald-500/30'
                      }`}>
                        {pred.risk_category}
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>
          </WidgetWrapper>
        </div>
      </div>
    </DashboardLayout>
  );
};

export default Inference;

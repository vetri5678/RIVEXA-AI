import React from 'react';
import WidgetWrapper from '../Common/WidgetWrapper';
import { motion } from 'framer-motion';
import { Database, Cpu, Hammer, FileText, CheckCircle, Clock, PlayCircle } from 'lucide-react';
import { useNavigate, useLocation } from 'react-router-dom';
import { usePipelineLifecycle } from '../../../hooks/useDashboard';
import type { PipelineStage } from '../../../types/dashboard';

const getIconForStage = (name: string) => {
  const lower = name.toLowerCase();
  if (lower.includes('sync')) return Database;
  if (lower.includes('extract')) return Cpu;
  if (lower.includes('cleanse')) return Hammer;
  if (lower.includes('engine') || lower.includes('model')) return Cpu;
  if (lower.includes('inference')) return CheckCircle;
  if (lower.includes('shap') || lower.includes('xai')) return FileText;
  return PlayCircle;
};

const getRouteForStage = (name: string) => {
  const lower = name.toLowerCase();
  if (lower.includes('sync')) return '/pipeline/repository-sync';
  if (lower.includes('extract')) return '/pipeline/extract';
  if (lower.includes('cleanse')) return '/pipeline/cleanse';
  if (lower.includes('engine') || lower.includes('model')) return '/pipeline/model-engine';
  if (lower.includes('inference')) return '/pipeline/inference';
  if (lower.includes('shap') || lower.includes('xai')) return '/pipeline/shap';
  return '/dashboard';
};

const getStatusColor = (status: string, isCurrent: boolean, isActiveRoute: boolean) => {
  if (isActiveRoute) {
    return 'border-cyan-400 bg-cyan-950/40 text-cyan-300 ring-2 ring-cyan-400/60 shadow-[0_0_25px_rgba(6,182,212,0.3)]';
  }
  if (isCurrent || status === 'RUNNING') {
    return 'border-cyan-500/60 bg-cyan-950/30 text-cyan-400 shadow-[0_0_15px_rgba(6,182,212,0.15)]';
  }
  switch (status) {
    case 'COMPLETED':
      return 'border-emerald-500/40 bg-emerald-950/20 text-emerald-400';
    case 'FAILED':
      return 'border-rose-500/40 bg-rose-950/20 text-rose-400';
    default:
      return 'border-slate-800 bg-cyber-950/40 text-slate-500';
  }
};

const formatTime = (timeStr?: string) => {
  if (!timeStr) return '--:--:--';
  try {
    const d = new Date(timeStr);
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  } catch {
    return timeStr;
  }
};

export const PredictionPipelineWidget: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { data: pipelineData, isLoading, isError, refetch } = usePipelineLifecycle();

  const stages: PipelineStage[] = pipelineData?.stages || [
    { name: 'Repo Sync', status: 'COMPLETED', progressPct: 100, durationSeconds: 8, startTime: new Date().toISOString(), currentStage: false },
    { name: 'Extract', status: 'COMPLETED', progressPct: 100, durationSeconds: 10, startTime: new Date().toISOString(), currentStage: false },
    { name: 'Cleanse', status: 'COMPLETED', progressPct: 100, durationSeconds: 12, startTime: new Date().toISOString(), currentStage: false },
    { name: 'Model Engine', status: 'COMPLETED', progressPct: 100, durationSeconds: 14, startTime: new Date().toISOString(), currentStage: false },
    { name: 'Inference', status: 'COMPLETED', progressPct: 100, durationSeconds: 16, startTime: new Date().toISOString(), currentStage: false },
    { name: 'SHAP (XAI)', status: 'COMPLETED', progressPct: 100, durationSeconds: 18, startTime: new Date().toISOString(), currentStage: false },
  ];

  return (
    <WidgetWrapper
      title="NEURAL PIPELINE LIFECYCLE"
      subtitle={`Click any stage card to open workflow view • Active: ${pipelineData?.active_stage || 'Inference Ready'}`}
      isLoading={isLoading}
      isError={isError}
      onRetry={refetch}
    >
      <div className="space-y-4 py-2 font-mono text-[9px] w-full">
        {/* Header metadata summary */}
        <div className="flex items-center justify-between px-2.5 py-1 bg-white/[0.02] border border-white/[0.05] rounded text-[10px] text-slate-400">
          <span className="flex items-center gap-1.5">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-ping" />
            Model Version: <strong className="text-white">{pipelineData?.model_version || 'v2.4-neural'}</strong>
          </span>
          <span className="flex items-center gap-1">
            <Clock size={11} className="text-cyan-400" />
            Last Evaluated: {formatTime(pipelineData?.timestamp)}
          </span>
        </div>

        {/* Pipeline Stage Cards */}
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-2.5 w-full">
          {stages.map((stage, idx) => {
            const Icon = getIconForStage(stage.name);
            const route = getRouteForStage(stage.name);
            const isActiveRoute = location.pathname === route;
            const colorClass = getStatusColor(stage.status, stage.currentStage, isActiveRoute);

            return (
              <motion.div
                key={stage.name}
                initial={{ opacity: 0, y: 5 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: idx * 0.05 }}
                onClick={() => navigate(route)}
                className={`flex flex-col p-2.5 border rounded-lg transition-all duration-300 relative overflow-hidden cursor-pointer hover:scale-[1.02] hover:border-cyan-400/70 select-none ${colorClass}`}
                title={`Click to open ${stage.name} workflow details`}
              >
                <div className="flex items-center justify-between mb-1.5">
                  <Icon size={14} className={stage.currentStage ? 'animate-spin text-cyan-400' : ''} />
                  <span className={`px-1.5 py-0.5 rounded text-[8px] font-bold uppercase ${
                    stage.status === 'COMPLETED' ? 'bg-emerald-500/20 text-emerald-300' :
                    stage.status === 'RUNNING' ? 'bg-cyan-500/20 text-cyan-300' :
                    'bg-slate-800 text-slate-400'
                  }`}>
                    {stage.status}
                  </span>
                </div>

                <span className="font-bold uppercase tracking-wider text-slate-100 block text-[10px] truncate mb-1">
                  {stage.name}
                </span>

                {/* Dynamic Progress Bar */}
                <div className="w-full bg-cyber-950 border border-white/10 rounded-full h-1.5 mb-2 overflow-hidden">
                  <motion.div
                    className={`h-full ${
                      stage.status === 'COMPLETED' ? 'bg-emerald-400' :
                      stage.status === 'RUNNING' ? 'bg-cyan-400' :
                      'bg-slate-700'
                    }`}
                    initial={{ width: 0 }}
                    animate={{ width: `${Math.min(100, Math.max(0, stage.progressPct))}%` }}
                    transition={{ duration: 0.5 }}
                  />
                </div>

                <div className="space-y-0.5 text-[8px] text-slate-400 font-mono">
                  <div className="flex justify-between">
                    <span>Progress:</span>
                    <strong className="text-white">{stage.progressPct}%</strong>
                  </div>
                  <div className="flex justify-between">
                    <span>Duration:</span>
                    <strong className="text-slate-300">{stage.durationSeconds}s</strong>
                  </div>
                  <div className="flex justify-between text-[7.5px] text-slate-500">
                    <span>Start:</span>
                    <span>{formatTime(stage.startTime)}</span>
                  </div>
                </div>

                {stage.currentStage && (
                  <div className="absolute top-0 right-0 w-2 h-2 rounded-full bg-cyan-400 animate-ping m-1" />
                )}
              </motion.div>
            );
          })}
        </div>
      </div>
    </WidgetWrapper>
  );
};

export default PredictionPipelineWidget;

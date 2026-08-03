import React, { useState } from 'react';
import DashboardLayout from '../../components/layout/DashboardLayout';
import PipelineBreadcrumbs from '../../components/common/PipelineBreadcrumbs';
import WidgetWrapper from '../../components/dashboard/Common/WidgetWrapper';
import PredictionPipelineWidget from '../../components/dashboard/PredictionPipeline/PredictionPipelineWidget';
import { usePipelineExtract } from '../../hooks/useDashboard';
import { Cpu, FileCode, Users, GitPullRequest, ShieldAlert, CheckCircle2 } from 'lucide-react';

export const FeatureExtraction: React.FC = () => {
  const [searchTerm, setSearchTerm] = useState('');
  const { data: extractData, isLoading, isError, refetch } = usePipelineExtract();

  return (
    <DashboardLayout
      onSearchChange={setSearchTerm}
      searchValue={searchTerm}
      onQuickAction={() => {}}
    >
      <PipelineBreadcrumbs currentStage="Feature Extraction" />

      {/* Header Banner */}
      <div className="glass-strong rounded-2xl p-6 mb-8 border border-white/[0.08] flex flex-col md:flex-row md:items-center justify-between gap-4 shadow-xl">
        <div className="flex items-center gap-4">
          <div className="p-3.5 rounded-2xl bg-purple-500/15 border border-purple-500/30 text-purple-400 shrink-0 shadow-[0_0_20px_rgba(168,85,247,0.2)]">
            <Cpu size={28} />
          </div>
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-xl font-bold tracking-tight text-white font-sans">
                Feature Extraction Engine
              </h1>
              <span className="px-2.5 py-0.5 rounded-full text-[10px] font-mono font-bold bg-purple-500/15 text-purple-400 border border-purple-500/30">
                PROCESSING (98.5%)
              </span>
            </div>
            <p className="text-xs text-slate-400 font-sans mt-1">
              AST code parsing, developer churn calculation, PR latency extraction, and security vulnerability scanning.
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
        {/* Left Column: Extraction Parameters */}
        <div className="lg:col-span-1 space-y-6">
          <WidgetWrapper
            title="EXTRACTION COUNTS"
            subtitle="Scanned source artifacts & metrics"
            isLoading={isLoading}
            isError={isError}
            onRetry={refetch}
          >
            <div className="space-y-3 py-2 font-mono text-xs">
              <div className="p-3 bg-white/[0.02] border border-white/[0.06] rounded-xl flex justify-between items-center">
                <span className="text-slate-400 flex items-center gap-2">
                  <FileCode size={14} className="text-purple-400" /> Scanned Files
                </span>
                <span className="text-white font-bold text-sm">{extractData?.scanned_source_files ?? 17380}</span>
              </div>

              <div className="p-3 bg-white/[0.02] border border-white/[0.06] rounded-xl flex justify-between items-center">
                <span className="text-slate-400 flex items-center gap-2">
                  <GitPullRequest size={14} className="text-cyan-400" /> Pull Requests
                </span>
                <span className="text-cyan-300 font-bold text-sm">{extractData?.pull_requests_extracted ?? 519}</span>
              </div>

              <div className="p-3 bg-white/[0.02] border border-white/[0.06] rounded-xl flex justify-between items-center">
                <span className="text-slate-400 flex items-center gap-2">
                  <Users size={14} className="text-emerald-400" /> Contributors
                </span>
                <span className="text-emerald-300 font-bold text-sm">{extractData?.contributors_extracted ?? 102}</span>
              </div>

              <div className="p-3 bg-white/[0.02] border border-white/[0.06] rounded-xl flex justify-between items-center">
                <span className="text-slate-400 flex items-center gap-2">
                  <ShieldAlert size={14} className="text-amber-400" /> Vulnerabilities Scanned
                </span>
                <span className="text-amber-300 font-bold text-sm">{extractData?.security_vulnerabilities_scanned ?? 38}</span>
              </div>
            </div>
          </WidgetWrapper>

          <WidgetWrapper
            title="DETECTED LANGUAGES"
            subtitle="Extracted codebase stacks"
            isLoading={false}
            isError={false}
          >
            <div className="flex flex-wrap gap-2 py-2 font-mono text-xs">
              {(extractData?.languages_detected || ['Java', 'TypeScript', 'Python', 'Go', 'Dockerfile', 'SQL']).map((lang: string) => (
                <span key={lang} className="px-3 py-1 bg-purple-500/10 border border-purple-500/30 text-purple-300 rounded-lg text-xs font-bold">
                  {lang}
                </span>
              ))}
            </div>
          </WidgetWrapper>
        </div>

        {/* Right Column: Processing Logs */}
        <div className="lg:col-span-2">
          <WidgetWrapper
            title="FEATURE PROCESSING LOGS"
            subtitle="AST parser & metric aggregator events"
            isLoading={isLoading}
            isError={isError}
            onRetry={refetch}
          >
            <div className="space-y-3 py-2 font-mono text-xs">
              {(extractData?.extraction_logs || []).map((log: any, idx: number) => (
                <div
                  key={idx}
                  className="p-3 bg-white/[0.02] border border-white/[0.06] rounded-xl flex items-center justify-between gap-3 text-xs"
                >
                  <div className="flex items-center gap-3">
                    <CheckCircle2 size={16} className="text-purple-400 shrink-0" />
                    <div>
                      <span className="text-slate-200 font-medium block">{log.event}</span>
                      <span className="text-[10px] text-slate-500">{log.timestamp}</span>
                    </div>
                  </div>
                  <span className="px-2 py-0.5 rounded text-[9px] font-bold bg-purple-500/15 text-purple-300 border border-purple-500/30">
                    {log.status}
                  </span>
                </div>
              ))}
            </div>
          </WidgetWrapper>
        </div>
      </div>
    </DashboardLayout>
  );
};

export default FeatureExtraction;

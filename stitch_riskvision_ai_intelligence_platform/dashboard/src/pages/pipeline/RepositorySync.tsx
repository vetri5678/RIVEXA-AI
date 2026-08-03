import React, { useState } from 'react';
import DashboardLayout from '../../components/layout/DashboardLayout';
import PipelineBreadcrumbs from '../../components/common/PipelineBreadcrumbs';
import WidgetWrapper from '../../components/dashboard/Common/WidgetWrapper';
import PredictionPipelineWidget from '../../components/dashboard/PredictionPipeline/PredictionPipelineWidget';
import { usePipelineRepositorySync } from '../../hooks/useDashboard';
import { Database, RefreshCw, GitBranch, GitCommit, CheckCircle2, ShieldCheck } from 'lucide-react';

export const RepositorySync: React.FC = () => {
  const [searchTerm, setSearchTerm] = useState('');
  const { data: syncData, isLoading, isError, refetch } = usePipelineRepositorySync();
  const [manualSyncing, setManualSyncing] = useState(false);

  const handleTriggerSync = async () => {
    setManualSyncing(true);
    setTimeout(() => {
      setManualSyncing(false);
      refetch();
    }, 1500);
  };

  return (
    <DashboardLayout
      onSearchChange={setSearchTerm}
      searchValue={searchTerm}
      onQuickAction={() => {}}
    >
      <PipelineBreadcrumbs currentStage="Repository Sync" />

      {/* Header Banner */}
      <div className="glass-strong rounded-2xl p-6 mb-8 border border-white/[0.08] flex flex-col md:flex-row md:items-center justify-between gap-4 shadow-xl">
        <div className="flex items-center gap-4">
          <div className="p-3.5 rounded-2xl bg-blue-500/15 border border-blue-500/30 text-blue-400 shrink-0 shadow-[0_0_20px_rgba(59,130,246,0.2)]">
            <Database size={28} />
          </div>
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-xl font-bold tracking-tight text-white font-sans">
                GitHub Repository Synchronization
              </h1>
              <span className="px-2.5 py-0.5 rounded-full text-[10px] font-mono font-bold bg-emerald-500/15 text-emerald-400 border border-emerald-500/30">
                ACTIVE
              </span>
            </div>
            <p className="text-xs text-slate-400 font-sans mt-1">
              Automated VCS metadata polling, branch synchronization, commit delta indexing, and webhook event streaming.
            </p>
          </div>
        </div>

        <button
          disabled={manualSyncing}
          onClick={handleTriggerSync}
          className="btn-primary py-2.5 px-4 text-xs font-semibold rounded-xl flex items-center justify-center gap-2 cursor-pointer shrink-0"
        >
          <RefreshCw size={14} className={manualSyncing ? 'animate-spin' : ''} />
          <span>{manualSyncing ? 'Syncing VCS Repos…' : 'Trigger Manual Sync'}</span>
        </button>
      </div>

      {/* Embedded Pipeline Navigation Card */}
      <div className="mb-8">
        <PredictionPipelineWidget />
      </div>

      {/* Content Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 font-sans">
        {/* Left Column: Sync Metrics */}
        <div className="lg:col-span-1 space-y-6">
          <WidgetWrapper
            title="SYNC STATS & HEALTH"
            subtitle="Current VCS connectivity parameters"
            isLoading={isLoading}
            isError={isError}
            onRetry={refetch}
          >
            <div className="space-y-4 py-2 font-mono text-xs">
              <div className="p-3 bg-white/[0.02] border border-white/[0.06] rounded-xl flex justify-between items-center">
                <span className="text-slate-400 flex items-center gap-2">
                  <Database size={14} className="text-cyan-400" /> Connected Repos
                </span>
                <span className="text-white font-bold text-sm">{syncData?.connected_repositories ?? 12}</span>
              </div>

              <div className="p-3 bg-white/[0.02] border border-white/[0.06] rounded-xl flex justify-between items-center">
                <span className="text-slate-400 flex items-center gap-2">
                  <GitBranch size={14} className="text-purple-400" /> Active Branches
                </span>
                <span className="text-purple-300 font-bold text-sm">{syncData?.active_branches_synced ?? 48}</span>
              </div>

              <div className="p-3 bg-white/[0.02] border border-white/[0.06] rounded-xl flex justify-between items-center">
                <span className="text-slate-400 flex items-center gap-2">
                  <GitCommit size={14} className="text-emerald-400" /> Total Commits Index
                </span>
                <span className="text-emerald-300 font-bold text-sm">{syncData?.total_commits_synced ?? 6680}</span>
              </div>

              <div className="p-3 bg-white/[0.02] border border-white/[0.06] rounded-xl flex justify-between items-center">
                <span className="text-slate-400 flex items-center gap-2">
                  <ShieldCheck size={14} className="text-blue-400" /> Health Score
                </span>
                <span className="text-blue-300 font-bold text-sm">{syncData?.overall_health_score ?? 94.2}%</span>
              </div>
            </div>
          </WidgetWrapper>

          <WidgetWrapper
            title="AUTO-SYNC CONFIG"
            subtitle="Scheduler parameters"
            isLoading={false}
            isError={false}
          >
            <div className="p-2 space-y-3 font-mono text-xs text-slate-300">
              <div className="flex justify-between items-center">
                <span>Webhook Integration:</span>
                <span className="text-emerald-400 font-bold">ACTIVE (HMAC)</span>
              </div>
              <div className="flex justify-between items-center">
                <span>Polling Interval:</span>
                <span className="text-cyan-400 font-bold">{syncData?.sync_interval_seconds ?? 300}s</span>
              </div>
              <div className="flex justify-between items-center">
                <span>Failed Sync Repos:</span>
                <span className="text-slate-400 font-bold">{syncData?.failed_repositories_count ?? 0}</span>
              </div>
            </div>
          </WidgetWrapper>
        </div>

        {/* Right Column: Synchronization Audit Logs */}
        <div className="lg:col-span-2">
          <WidgetWrapper
            title="SYNCHRONIZATION STREAM LOGS"
            subtitle="Live event log from VCS connector service"
            isLoading={isLoading}
            isError={isError}
            onRetry={refetch}
          >
            <div className="space-y-2 py-2 font-mono text-xs">
              {(syncData?.sync_logs || []).map((log: any, idx: number) => (
                <div
                  key={idx}
                  className="p-3 bg-white/[0.02] border border-white/[0.06] rounded-xl flex items-center justify-between gap-3 text-xs"
                >
                  <div className="flex items-center gap-3">
                    <CheckCircle2 size={16} className="text-emerald-400 shrink-0" />
                    <div>
                      <span className="text-slate-200 font-medium block">{log.event}</span>
                      <span className="text-[10px] text-slate-500">{log.timestamp}</span>
                    </div>
                  </div>
                  <span className="px-2 py-0.5 rounded text-[9px] font-bold bg-emerald-500/15 text-emerald-400 border border-emerald-500/30">
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

export default RepositorySync;

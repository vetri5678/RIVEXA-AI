import React, { useState } from 'react';
import DashboardLayout from '../components/layout/DashboardLayout';
import ActivityMonitorWidget from '../components/dashboard/ActivityMonitor/ActivityMonitorWidget';
import ActivityFeedWidget from '../components/dashboard/ActivityFeed/ActivityFeedWidget';
import AlertsWidget from '../components/dashboard/Alerts/AlertsWidget';
import AITelemetryAnalysisWidget from '../components/dashboard/SystemHealth/AITelemetryAnalysisWidget';
import { useTelemetryStatus } from '../hooks/useDashboard';
import { useWebSocket } from '../hooks/useWebSocket';
import { Radio, RefreshCw, Activity, ShieldCheck, Zap } from 'lucide-react';

export const Telemetry: React.FC = () => {
  const [searchTerm, setSearchTerm] = useState('');
  const { data: telemetryStatus, refetch: refetchStatus } = useTelemetryStatus();
  const { status: wsStatus, latency, eventsPerSec, reconnect } = useWebSocket();

  const handleGlobalRetry = () => {
    reconnect();
    refetchStatus();
  };

  // Connection badge helper
  const getConnectionBadge = () => {
    const s = wsStatus || 'Connected';
    switch (s) {
      case 'Connected':
        return (
          <span className="px-3 py-1 rounded-full text-[10px] font-mono font-bold bg-emerald-500/15 text-emerald-400 border border-emerald-500/30 flex items-center gap-1.5 shadow-[0_0_12px_rgba(16,185,129,0.2)]">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
            CONNECTED
          </span>
        );
      case 'Reconnecting':
        return (
          <span className="px-3 py-1 rounded-full text-[10px] font-mono font-bold bg-amber-500/15 text-amber-400 border border-amber-500/30 flex items-center gap-1.5 shadow-[0_0_12px_rgba(245,158,11,0.2)]">
            <span className="w-1.5 h-1.5 rounded-full bg-amber-400 animate-ping" />
            RECONNECTING...
          </span>
        );
      default:
        return (
          <span className="px-3 py-1 rounded-full text-[10px] font-mono font-bold bg-red-500/15 text-red-400 border border-red-500/30 flex items-center gap-1.5 shadow-[0_0_12px_rgba(239,68,68,0.2)]">
            <span className="w-1.5 h-1.5 rounded-full bg-red-400" />
            DISCONNECTED
          </span>
        );
    }
  };

  return (
    <DashboardLayout
      onSearchChange={setSearchTerm}
      searchValue={searchTerm}
      onQuickAction={() => {}}
    >
      {/* Live Header Banner */}
      <div className="glass-strong rounded-2xl p-6 mb-8 border border-white/[0.08] flex flex-col lg:flex-row lg:items-center justify-between gap-4 shadow-2xl relative overflow-hidden">
        {/* Ambient Glow */}
        <div className="absolute -top-12 -right-12 w-48 h-48 bg-cyan-500/10 rounded-full blur-3xl pointer-events-none" />

        <div className="flex items-center gap-4 z-10">
          <div className="p-3.5 rounded-2xl bg-cyan-500/15 border border-cyan-500/30 text-cyan-400 shrink-0 shadow-[0_0_20px_rgba(56,189,248,0.2)]">
            <Radio size={28} className="animate-pulse" />
          </div>
          <div>
            <div className="flex items-center gap-3 flex-wrap">
              <h1 className="text-xl font-bold tracking-tight text-white font-sans">
                Telemetry & Activity Audit Stream
              </h1>
              {getConnectionBadge()}
            </div>
            <p className="text-xs text-slate-400 font-sans mt-1">
              Real-time microservice security events, Spring Boot JVM system telemetry, active alerts, and OpenRouter AI audit analysis.
            </p>
          </div>
        </div>

        {/* Live System Metrics Badges */}
        <div className="flex items-center gap-3 shrink-0 flex-wrap z-10 font-mono">
          <div className="px-3.5 py-1.5 rounded-xl bg-white/[0.03] border border-white/[0.08] text-right text-xs">
            <span className="text-slate-500 block text-[9px] uppercase tracking-wider">Stream Latency</span>
            <span className="text-cyan-400 font-bold flex items-center justify-end gap-1 mt-0.5">
              <Zap size={11} className="text-cyan-400" />
              {latency}ms
            </span>
          </div>

          <div className="px-3.5 py-1.5 rounded-xl bg-white/[0.03] border border-white/[0.08] text-right text-xs">
            <span className="text-slate-500 block text-[9px] uppercase tracking-wider">Event Velocity</span>
            <span className="text-emerald-400 font-bold flex items-center justify-end gap-1 mt-0.5">
              <Activity size={11} className="text-emerald-400" />
              {eventsPerSec} events/sec
            </span>
          </div>

          <div className="px-3.5 py-1.5 rounded-xl bg-white/[0.03] border border-white/[0.08] text-right text-xs">
            <span className="text-slate-500 block text-[9px] uppercase tracking-wider">Server Health</span>
            <span className="text-purple-400 font-bold flex items-center justify-end gap-1 mt-0.5">
              <ShieldCheck size={11} className="text-purple-400" />
              {telemetryStatus?.server_health || 'HEALTHY'}
            </span>
          </div>

          {/* Global Manual Reconnect Button */}
          <button
            onClick={handleGlobalRetry}
            className="btn-secondary py-2 px-3.5 text-xs font-mono font-bold rounded-xl text-cyan-400 border-cyan-500/30 hover:border-cyan-500/60 hover:bg-cyan-500/10 flex items-center gap-1.5 cursor-pointer transition-all shadow-[0_0_15px_rgba(6,182,212,0.15)]"
            title="Force reconnect WebSocket stream and refresh REST endpoints"
          >
            <RefreshCw size={14} className="hover:rotate-180 transition-transform duration-300" />
            <span>RETRY SYNC</span>
          </button>
        </div>
      </div>

      {/* Grid Layout for Telemetry Widgets */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 font-sans">
        <div className="lg:col-span-1 space-y-6">
          <AITelemetryAnalysisWidget />
          <ActivityMonitorWidget />
          <AlertsWidget />
        </div>
        <div className="lg:col-span-2">
          <ActivityFeedWidget />
        </div>
      </div>
    </DashboardLayout>
  );
};

export default Telemetry;

import React, { useState } from 'react';
import DashboardLayout from '../components/layout/DashboardLayout';
import ActivityMonitorWidget from '../components/dashboard/ActivityMonitor/ActivityMonitorWidget';
import ActivityFeedWidget from '../components/dashboard/ActivityFeed/ActivityFeedWidget';
import AlertsWidget from '../components/dashboard/Alerts/AlertsWidget';
import AITelemetryAnalysisWidget from '../components/dashboard/SystemHealth/AITelemetryAnalysisWidget';
import { useTelemetryStatus, useTelemetryCurrent } from '../hooks/useDashboard';
import { useWebSocket } from '../hooks/useWebSocket';
import {
  Radio,
  RefreshCw,
  Activity,
  ShieldCheck,
  Zap,
  AlertTriangle,
  Server,
  Database,
  Cpu,
  Globe,
  Bot,
  Clock,
  Layers,
  CheckCircle2,
} from 'lucide-react';

export const Telemetry: React.FC = () => {
  const [searchTerm, setSearchTerm] = useState('');
  const [timeRange, setTimeRange] = useState('1h');
  const [autoRefresh, setAutoRefresh] = useState(true);

  const { refetch: refetchStatus } = useTelemetryStatus();
  const { data: currentMetrics, refetch: refetchMetrics } = useTelemetryCurrent();
  const { status: wsStatus, latency, eventsPerSec, reconnect } = useWebSocket();

  const handleGlobalRetry = () => {
    reconnect();
    refetchStatus();
    refetchMetrics();
  };

  // Connection badge helper
  const getConnectionBadge = () => {
    const s = wsStatus || 'Connected';
    switch (s) {
      case 'Connected':
        return (
          <span className="px-3 py-1 rounded-full text-[10px] font-mono font-bold bg-emerald-500/15 text-emerald-400 border border-emerald-500/30 flex items-center gap-1.5 shadow-[0_0_12px_rgba(16,185,129,0.2)]">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
            LIVE OPERATIONAL
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

  // Microservice Health Items
  const microservices = [
    { name: 'Spring Boot Backend', status: 'ONLINE', latency: '18ms', icon: Server, color: 'text-emerald-400' },
    { name: 'FastAPI Prediction Engine', status: 'ONLINE', latency: '34ms', icon: Cpu, color: 'text-cyan-400' },
    { name: 'PostgreSQL Database', status: 'ONLINE', latency: '4ms', icon: Database, color: 'text-blue-400' },
    { name: 'GitHub Connector', status: 'ONLINE', latency: '110ms', icon: Globe, color: 'text-purple-400' },
    { name: 'n8n Automation Engine', status: 'ONLINE', latency: '45ms', icon: Layers, color: 'text-teal-400' },
    { name: 'OpenRouter LLM Service', status: 'ONLINE', latency: '210ms', icon: Bot, color: 'text-amber-400' },
  ];

  return (
    <DashboardLayout
      onSearchChange={setSearchTerm}
      searchValue={searchTerm}
      onQuickAction={() => {}}
    >
      <div className="w-full max-w-[1920px] mx-auto space-y-6 font-sans text-slate-100">
        {/* Live Header Banner */}
        <div className="glass-strong rounded-2xl p-5 lg:p-6 border border-white/[0.08] flex flex-col lg:flex-row lg:items-center justify-between gap-4 shadow-2xl relative overflow-hidden">
          {/* Ambient Glow */}
          <div className="absolute -top-12 -right-12 w-48 h-48 bg-cyan-500/10 rounded-full blur-3xl pointer-events-none" />

          <div className="flex items-center gap-4 z-10">
            <div className="p-3.5 rounded-2xl bg-cyan-500/15 border border-cyan-500/30 text-cyan-400 shrink-0 shadow-[0_0_20px_rgba(56,189,248,0.2)]">
              <Radio size={28} className="animate-pulse" />
            </div>
            <div>
              <div className="flex items-center gap-3 flex-wrap">
                <h1 className="text-xl font-bold tracking-tight text-white font-sans">
                  Telemetry & Security Audit Console
                </h1>
                {getConnectionBadge()}
              </div>
              <p className="text-xs text-slate-400 font-sans mt-1">
                Real-time AI observability, microservice telemetry, active alerts, and SecurityContext audit intelligence.
              </p>
            </div>
          </div>

          {/* Controls Bar */}
          <div className="flex items-center gap-3 shrink-0 flex-wrap z-10 font-mono">
            {/* Time Range Selector */}
            <div className="flex items-center gap-1.5 bg-white/[0.03] border border-white/[0.08] rounded-xl px-2.5 py-1 text-xs">
              <Clock size={12} className="text-slate-400" />
              <span className="text-slate-400 text-[10px]">Range:</span>
              <select
                value={timeRange}
                onChange={(e) => setTimeRange(e.target.value)}
                className="bg-transparent text-cyan-300 font-bold text-xs outline-none cursor-pointer"
              >
                <option value="15m" className="bg-[#090d20]">15m</option>
                <option value="1h" className="bg-[#090d20]">1h</option>
                <option value="24h" className="bg-[#090d20]">24h</option>
                <option value="7d" className="bg-[#090d20]">7d</option>
              </select>
            </div>

            {/* Auto Refresh Toggle */}
            <button
              onClick={() => setAutoRefresh(!autoRefresh)}
              className={`px-3 py-1.5 rounded-xl border text-xs font-bold flex items-center gap-1.5 transition-all cursor-pointer ${
                autoRefresh
                  ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400 shadow-[0_0_10px_rgba(16,185,129,0.15)]'
                  : 'bg-white/[0.03] border-white/[0.08] text-slate-400'
              }`}
            >
              <span className={`w-2 h-2 rounded-full ${autoRefresh ? 'bg-emerald-400 animate-pulse' : 'bg-slate-500'}`} />
              <span>{autoRefresh ? 'AUTO-REFRESH ON' : 'PAUSED'}</span>
            </button>

            {/* Manual Reconnect / Retry Button */}
            <button
              onClick={handleGlobalRetry}
              className="btn-secondary py-1.5 px-3 text-xs font-mono font-bold rounded-xl text-cyan-400 border-cyan-500/30 hover:border-cyan-500/60 hover:bg-cyan-500/10 flex items-center gap-1.5 cursor-pointer transition-all shadow-[0_0_15px_rgba(6,182,212,0.15)]"
              title="Force reconnect WebSocket stream and refresh REST endpoints"
            >
              <RefreshCw size={13} className="hover:rotate-180 transition-transform duration-300" />
              <span>REFRESH</span>
            </button>
          </div>
        </div>

        {/* Full-Width KPI Summary Cards Bar */}
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3.5">
          {/* Total Events */}
          <div className="p-3.5 rounded-2xl glass-card border border-white/[0.08] relative overflow-hidden">
            <div className="flex items-center justify-between text-slate-400 mb-1.5">
              <span className="text-[10px] font-mono font-semibold uppercase tracking-wider">Total Events</span>
              <Activity size={15} className="text-cyan-400" />
            </div>
            <div className="text-xl font-bold font-mono text-white">1,248</div>
            <div className="text-[10px] text-emerald-400 font-mono mt-1 flex items-center gap-1">
              <span>↑ 12.4%</span>
              <span className="text-slate-500">vs 1h ago</span>
            </div>
          </div>

          {/* Errors */}
          <div className="p-3.5 rounded-2xl glass-card border border-white/[0.08] relative overflow-hidden">
            <div className="flex items-center justify-between text-slate-400 mb-1.5">
              <span className="text-[10px] font-mono font-semibold uppercase tracking-wider">Errors</span>
              <AlertTriangle size={15} className="text-rose-400" />
            </div>
            <div className="text-xl font-bold font-mono text-white">
              {currentMetrics?.error_rate !== undefined ? `${(Number(currentMetrics.error_rate) * 10).toFixed(0)}` : '0'}
            </div>
            <div className="text-[10px] text-emerald-400 font-mono mt-1">0.00% error rate</div>
          </div>

          {/* Warnings */}
          <div className="p-3.5 rounded-2xl glass-card border border-white/[0.08] relative overflow-hidden">
            <div className="flex items-center justify-between text-slate-400 mb-1.5">
              <span className="text-[10px] font-mono font-semibold uppercase tracking-wider">Warnings</span>
              <AlertTriangle size={15} className="text-amber-400" />
            </div>
            <div className="text-xl font-bold font-mono text-white">3</div>
            <div className="text-[10px] text-amber-400 font-mono mt-1">Low threshold alert</div>
          </div>

          {/* System Uptime */}
          <div className="p-3.5 rounded-2xl glass-card border border-white/[0.08] relative overflow-hidden">
            <div className="flex items-center justify-between text-slate-400 mb-1.5">
              <span className="text-[10px] font-mono font-semibold uppercase tracking-wider">System Uptime</span>
              <ShieldCheck size={15} className="text-purple-400" />
            </div>
            <div className="text-xl font-bold font-mono text-white">99.98%</div>
            <div className="text-[10px] text-purple-400 font-mono mt-1 flex items-center gap-1">
              <CheckCircle2 size={10} />
              <span>OPERATIONAL</span>
            </div>
          </div>

          {/* Stream Latency */}
          <div className="p-3.5 rounded-2xl glass-card border border-white/[0.08] relative overflow-hidden">
            <div className="flex items-center justify-between text-slate-400 mb-1.5">
              <span className="text-[10px] font-mono font-semibold uppercase tracking-wider">Stream Latency</span>
              <Zap size={15} className="text-cyan-400" />
            </div>
            <div className="text-xl font-bold font-mono text-cyan-300">{latency}ms</div>
            <div className="text-[10px] text-slate-400 font-mono mt-1">Optimal WebSocket</div>
          </div>

          {/* Event Velocity */}
          <div className="p-3.5 rounded-2xl glass-card border border-white/[0.08] relative overflow-hidden">
            <div className="flex items-center justify-between text-slate-400 mb-1.5">
              <span className="text-[10px] font-mono font-semibold uppercase tracking-wider">Event Velocity</span>
              <Activity size={15} className="text-emerald-400" />
            </div>
            <div className="text-xl font-bold font-mono text-emerald-300">{eventsPerSec} evt/s</div>
            <div className="text-[10px] text-slate-400 font-mono mt-1">Real-time throughput</div>
          </div>
        </div>

        {/* 2-Column Balanced Dashboard Grid (2/3 Main Feed, 1/3 Microservices & Alerts) */}
        <div className="grid grid-cols-1 xl:grid-cols-3 gap-6 items-start">
          {/* Main Column (2/3 Width) */}
          <div className="xl:col-span-2 space-y-6 min-w-0">
            {/* Live Audit Log & Activity Stream Console */}
            <ActivityFeedWidget />

            {/* Real-Time Telemetry Monitor Grid */}
            <ActivityMonitorWidget />
          </div>

          {/* Right Observability Side Panel (1/3 Width) */}
          <div className="xl:col-span-1 space-y-6 min-w-0">
            {/* Microservice System Health Panel */}
            <div className="glass-strong rounded-2xl p-5 border border-white/[0.08] shadow-xl">
              <div className="flex items-center justify-between mb-4">
                <div className="flex items-center gap-2">
                  <Server size={16} className="text-cyan-400" />
                  <h3 className="text-sm font-bold font-mono text-white uppercase tracking-wider">
                    MICROSERVICE HEALTH
                  </h3>
                </div>
                <span className="px-2 py-0.5 rounded-full text-[9px] font-mono font-bold bg-emerald-500/15 text-emerald-400 border border-emerald-500/30">
                  ALL ONLINE
                </span>
              </div>

              <div className="space-y-2.5 font-mono text-xs">
                {microservices.map((svc) => (
                  <div
                    key={svc.name}
                    className="p-2.5 rounded-xl bg-cyber-950/40 border border-white/[0.06] flex items-center justify-between hover:border-cyan-500/30 transition-all"
                  >
                    <div className="flex items-center gap-2.5 min-w-0">
                      <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse shrink-0" />
                      <svc.icon size={14} className={`${svc.color} shrink-0`} />
                      <span className="text-slate-200 text-xs truncate font-sans">{svc.name}</span>
                    </div>

                    <div className="flex items-center gap-2 shrink-0">
                      <span className="text-[10px] text-slate-400">{svc.latency}</span>
                      <span className="px-1.5 py-0.5 rounded text-[9px] font-bold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                        {svc.status}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Cognitive Audit Summary Report */}
            <AITelemetryAnalysisWidget />

            {/* Active Alerts Widget */}
            <AlertsWidget />
          </div>
        </div>
      </div>
    </DashboardLayout>
  );
};

export default Telemetry;


import React from 'react';
import WidgetWrapper from '../Common/WidgetWrapper';
import { useTelemetryCurrent } from '../../../hooks/useDashboard';
import { Cpu, HardDrive, Activity, Zap, Database, Server, Wifi, RefreshCw, AlertTriangle } from 'lucide-react';

export const ActivityMonitorWidget: React.FC = () => {
  const { data: telemetry, isLoading, isError, refetch } = useTelemetryCurrent();

  const cpu = telemetry?.cpu_usage !== undefined ? `${Number(telemetry.cpu_usage).toFixed(1)}%` : (isLoading ? '...' : '—');
  const ram = telemetry?.memory_usage !== undefined ? `${Number(telemetry.memory_usage).toFixed(1)}%` : (isLoading ? '...' : '—');
  const heap = telemetry?.heap_usage !== undefined ? `${Number(telemetry.heap_usage).toFixed(1)}%` : (isLoading ? '...' : '—');
  const threads = telemetry?.thread_count !== undefined ? `${telemetry.thread_count}` : (isLoading ? '...' : '—');
  const latency = telemetry?.api_latency !== undefined ? `${telemetry.api_latency}ms` : (isLoading ? '...' : '—');
  const disk = telemetry?.disk_usage !== undefined ? `${Number(telemetry.disk_usage).toFixed(1)}%` : (isLoading ? '...' : '—');
  const dbConns = telemetry?.active_sessions !== undefined ? `${telemetry.active_sessions}` : (isLoading ? '...' : '—');
  const network = telemetry?.network_usage !== undefined ? `${Number(telemetry.network_usage).toFixed(1)} MB/s` : (isLoading ? '...' : '—');
  const requestRate = telemetry?.request_rate !== undefined ? `${telemetry.request_rate} req/s` : (isLoading ? '...' : '—');
  const errorRate = telemetry?.error_rate !== undefined ? `${Number(telemetry.error_rate).toFixed(2)}%` : (isLoading ? '...' : '—');


  const metrics = [
    { label: 'CPU Usage', value: cpu, icon: Cpu, color: 'text-cyan-400' },
    { label: 'RAM Usage', value: ram, icon: HardDrive, color: 'text-emerald-400' },
    { label: 'Heap Memory', value: heap, icon: Server, color: 'text-blue-400' },
    { label: 'JVM Threads', value: threads, icon: Activity, color: 'text-purple-400' },
    { label: 'API Latency', value: latency, icon: Zap, color: 'text-amber-400' },
    { label: 'Disk Usage', value: disk, icon: HardDrive, color: 'text-orange-400' },
    { label: 'DB Connections', value: dbConns, icon: Database, color: 'text-teal-400' },
    { label: 'Network Usage', value: network, icon: Wifi, color: 'text-sky-400' },
    { label: 'Request Rate', value: requestRate, icon: RefreshCw, color: 'text-indigo-400' },
    { label: 'Error Rate', value: errorRate, icon: AlertTriangle, color: 'text-rose-400' },
  ];

  return (
    <WidgetWrapper
      title="REAL-TIME TELEMETRY MONITOR"
      subtitle="Live Spring Boot Actuator & System Metrics (Refreshed every 5s)"
      isLoading={isLoading}
      isError={isError}
      onRetry={refetch}
    >
      <div className="grid grid-cols-2 sm:grid-cols-5 gap-2.5 py-1 font-sans">
        {metrics.map((metric) => (
          <div
            key={metric.label}
            className="p-2.5 border border-white/[0.06] bg-cyber-950/40 rounded-xl hover:border-cyan-500/40 transition-all duration-200"
          >
            <div className="flex items-center gap-1 mb-1 font-mono text-[8.5px] text-slate-400 uppercase tracking-wider truncate">
              <metric.icon size={11} className={metric.color} />
              <span className="truncate">{metric.label}</span>
            </div>
            <span className="text-sm font-extrabold font-mono text-white block mt-0.5">
              {metric.value}
            </span>
          </div>
        ))}
      </div>
    </WidgetWrapper>
  );
};

export default ActivityMonitorWidget;

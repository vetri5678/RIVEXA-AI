import React from 'react';
import { useSystemStatus } from '../../../hooks/useDashboard';
import WidgetWrapper from '../Common/WidgetWrapper';
import { Activity, ShieldCheck, AlertTriangle } from 'lucide-react';

export const SystemHealthWidget: React.FC = () => {
  const { data: status, isLoading, isError, refetch } = useSystemStatus();

  return (
    <WidgetWrapper
      title="SYSTEM STATUS TELEMETRY"
      subtitle="FastAPI / DB / Core ML microservices health check"
      isLoading={isLoading}
      isError={isError}
      onRetry={refetch}
    >
      <div className="space-y-2 py-1">
        {status?.services.map((svc) => (
          <div
            key={svc.name}
            className="flex items-center justify-between p-2 border border-slate-800/60 bg-cyber-950/20 hover:border-slate-800 rounded font-mono transition-all duration-200"
          >
            <div className="flex items-center gap-2">
              <span className={`w-2 h-2 rounded-full ${
                svc.status === 'online' ? 'bg-neon-green animate-pulse shadow-[0_0_8px_#00ff88]' :
                svc.status === 'degraded' ? 'bg-neon-yellow shadow-[0_0_8px_#f59e0b]' :
                'bg-neon-pink shadow-[0_0_8px_#ff2d55]'
              }`} />
              <span className="text-[11px] font-bold text-slate-300">{svc.name}</span>
            </div>
            <div className="text-right flex items-center gap-2">
              {svc.latency_ms !== undefined && (
                <span className="text-[9px] text-slate-500 font-bold">{svc.latency_ms}ms</span>
              )}
              <span className={`text-[10px] uppercase font-bold ${
                svc.status === 'online' ? 'text-neon-green' :
                svc.status === 'degraded' ? 'text-neon-yellow' : 'text-neon-pink'
              }`}>
                {svc.status}
              </span>
            </div>
          </div>
        ))}
      </div>
    </WidgetWrapper>
  );
};
export default SystemHealthWidget;

import React from 'react';
import { useAlerts } from '../../../hooks/useDashboard';
import WidgetWrapper from '../Common/WidgetWrapper';
import Badge from '../../common/Badge';
import { AlertCircle, AlertTriangle } from 'lucide-react';

export const AlertsWidget: React.FC = () => {
  const { data: alerts, isLoading, isError, refetch } = useAlerts();

  return (
    <WidgetWrapper
      title="CRITICAL INCIDENT LOG"
      subtitle="Live feed monitoring repository anomaly thresholds"
      isLoading={isLoading}
      isError={isError}
      onRetry={refetch}
    >
      <div className="space-y-2 overflow-y-auto max-h-[180px] pr-1 py-1 font-mono text-[10px] flex-1 flex flex-col justify-start">
        {alerts?.items.slice(0, 4).map((alert) => (
          <div
            key={alert.id}
            className={`p-2 border rounded flex items-start gap-2 ${
              alert.severity === 'critical'
                ? 'border-neon-pink/15 bg-neon-pink/5 hover:border-neon-pink/30'
                : 'border-slate-800 bg-cyber-950/20 hover:border-neon-yellow/20'
            } transition-all duration-200`}
          >
            <div className="mt-0.5 shrink-0">
              {alert.severity === 'critical' ? (
                <AlertCircle className="text-neon-pink" size={12} />
              ) : (
                <AlertTriangle className="text-neon-yellow" size={12} />
              )}
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center justify-between gap-2 mb-0.5">
                <span className="font-bold text-slate-300 truncate">{alert.title}</span>
                <Badge label={alert.severity} variant={alert.severity === 'critical' ? 'critical' : 'warning'} />
              </div>
              <p className="text-[9px] text-slate-400 leading-normal">{alert.message}</p>
            </div>
          </div>
        ))}
        {(!alerts || alerts.items.length === 0) && (
          <div className="text-center py-6 text-slate-500 uppercase tracking-widest font-mono text-[9px]">
            No live incidents detected
          </div>
        )}
      </div>
    </WidgetWrapper>
  );
};
export default AlertsWidget;

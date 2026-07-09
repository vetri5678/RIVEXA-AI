import React from 'react';
import { X, AlertTriangle, Info, BellRing } from 'lucide-react';
import { useAlerts } from '../../hooks/useDashboard';
import Badge from '../common/Badge';

interface NotificationDrawerProps {
  isOpen: boolean;
  onClose: () => void;
}

export const NotificationDrawer: React.FC<NotificationDrawerProps> = ({ isOpen, onClose }) => {
  const { data: alerts, isLoading } = useAlerts();

  if (!isOpen) return null;

  return (
    <div className="fixed inset-y-0 right-0 w-96 bg-cyber-950/95 border-l border-glass-border shadow-2xl z-50 flex flex-col backdrop-blur-lg animate-count-up">
      {/* Header */}
      <div className="p-6 border-b border-glass-border flex items-center justify-between">
        <div className="flex items-center gap-2 text-neon-blue">
          <BellRing size={20} />
          <h2 className="text-md font-mono font-bold uppercase tracking-wider text-slate-100">
            SYSTEM ALERTS
          </h2>
        </div>
        <button
          onClick={onClose}
          className="text-slate-400 hover:text-slate-100 hover:bg-cyber-800/40 p-1.5 rounded-lg border border-transparent hover:border-cyber-700 transition-all duration-200"
        >
          <X size={18} />
        </button>
      </div>

      {/* Alerts Stream */}
      <div className="flex-1 overflow-y-auto p-4 space-y-3">
        {isLoading ? (
          <div className="text-center py-8 font-mono text-xs text-slate-500">
            Retrieving alert registries...
          </div>
        ) : !alerts || alerts.items.length === 0 ? (
          <div className="text-center py-12 text-slate-500 font-mono text-xs">
            System status nominal. Zero warnings.
          </div>
        ) : (
          alerts.items.map((alert) => (
            <div
              key={alert.id}
              className={`p-4 border rounded-xl bg-cyber-900/60 backdrop-blur-md transition-all duration-300 ${
                alert.severity === 'critical'
                  ? 'border-neon-pink/20 hover:border-neon-pink/40 shadow-[0_0_15px_rgba(255,45,85,0.05)]'
                  : 'border-glass-border hover:border-neon-yellow/30'
              }`}
            >
              <div className="flex gap-3">
                <div
                  className={`mt-0.5 ${
                    alert.severity === 'critical' ? 'text-neon-pink' : 'text-neon-yellow'
                  }`}
                >
                  <AlertTriangle size={16} />
                </div>
                <div className="flex-1">
                  <div className="flex items-center justify-between gap-2 mb-1">
                    <h4 className="text-xs font-mono font-bold text-slate-200">
                      {alert.title}
                    </h4>
                    <Badge
                      label={alert.severity}
                      variant={alert.severity === 'critical' ? 'critical' : 'warning'}
                    />
                  </div>
                  <p className="text-xs text-slate-400 leading-relaxed mb-2">
                    {alert.message}
                  </p>
                  <div className="flex items-center justify-between text-[9px] font-mono text-slate-500">
                    <span>{alert.project_name || 'System Level'}</span>
                    <span>{new Date(alert.created_at).toLocaleTimeString()}</span>
                  </div>
                </div>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
};
export default NotificationDrawer;

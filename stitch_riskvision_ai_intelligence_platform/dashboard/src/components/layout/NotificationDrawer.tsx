import React from 'react';
import { X, AlertTriangle, BellRing, CheckCircle2 } from 'lucide-react';
import { useAlerts } from '../../hooks/useDashboard';

interface NotificationDrawerProps {
  isOpen: boolean;
  onClose: () => void;
}

export const NotificationDrawer: React.FC<NotificationDrawerProps> = ({ isOpen, onClose }) => {
  const { data: alerts, isLoading } = useAlerts();

  if (!isOpen) return null;

  return (
    <div className="fixed inset-y-0 right-0 w-full sm:w-96 max-w-full bg-[#0B1220]/95 border-l border-white/[0.08] shadow-2xl z-50 flex flex-col backdrop-blur-2xl animate-slide-in-right">
      {/* Header */}
      <div className="p-5 border-b border-white/[0.06] flex items-center justify-between">
        <div className="flex items-center gap-2.5">
          <div className="p-1.5 rounded-lg bg-blue-500/15 border border-blue-500/30 text-cyan-400">
            <BellRing size={18} />
          </div>
          <div>
            <h2 className="text-sm font-bold tracking-tight text-white font-sans">
              System Notifications
            </h2>
            <p className="text-[10px] text-slate-400 font-mono">Real-time AI telemetry alerts</p>
          </div>
        </div>
        <button
          onClick={onClose}
          className="text-slate-400 hover:text-white hover:bg-white/[0.08] p-1.5 rounded-lg transition-all cursor-pointer"
        >
          <X size={18} />
        </button>
      </div>

      {/* Alerts Stream */}
      <div className="flex-1 overflow-y-auto p-4 space-y-3 no-scrollbar">
        {isLoading ? (
          <div className="space-y-3 py-4">
            <div className="h-16 skeleton rounded-xl" />
            <div className="h-16 skeleton rounded-xl" />
            <div className="h-16 skeleton rounded-xl" />
          </div>
        ) : !alerts || alerts.items.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-center text-slate-400 font-sans">
            <CheckCircle2 size={32} className="text-emerald-400 mb-2" />
            <p className="text-xs font-semibold text-slate-200">All Systems Nominal</p>
            <p className="text-[11px] text-slate-500 mt-0.5">Zero unread alerts detected.</p>
          </div>
        ) : (
          alerts.items.map((alert) => (
            <div
              key={alert.id}
              className={`p-3.5 border rounded-xl bg-white/[0.03] backdrop-blur-md transition-all duration-200 ${
                alert.severity === 'critical'
                  ? 'border-red-500/30 bg-red-500/[0.03] hover:border-red-500/50'
                  : 'border-white/[0.06] hover:border-white/[0.15]'
              }`}
            >
              <div className="flex gap-3">
                <div
                  className={`mt-0.5 ${
                    alert.severity === 'critical' ? 'text-red-400' : 'text-amber-400'
                  }`}
                >
                  <AlertTriangle size={16} />
                </div>
                <div className="flex-1">
                  <div className="flex items-center justify-between gap-2 mb-1">
                    <h4 className="text-xs font-bold text-slate-200">
                      {alert.title}
                    </h4>
                    <span
                      className={`px-2 py-0.5 rounded-full text-[9px] font-mono font-bold uppercase ${
                        alert.severity === 'critical'
                          ? 'bg-red-500/20 text-red-400 border border-red-500/30'
                          : 'bg-amber-500/20 text-amber-400 border border-amber-500/30'
                      }`}
                    >
                      {alert.severity}
                    </span>
                  </div>
                  <p className="text-xs text-slate-400 leading-relaxed mb-2">
                    {alert.message}
                  </p>
                  <div className="flex items-center justify-between text-[10px] font-mono text-slate-500">
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

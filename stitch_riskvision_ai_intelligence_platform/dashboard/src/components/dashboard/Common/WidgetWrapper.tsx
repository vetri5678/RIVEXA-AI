import React, { useState } from 'react';
import { AlertTriangle, RefreshCw, Info, Wifi } from 'lucide-react';

interface WidgetWrapperProps {
  title: string;
  subtitle?: string;
  isLoading: boolean;
  isError: boolean;
  isEmpty?: boolean;
  onRetry?: () => void;
  children: React.ReactNode;
  className?: string;
  headerActions?: React.ReactNode;
}

export const WidgetWrapper: React.FC<WidgetWrapperProps> = ({
  title,
  subtitle,
  isLoading,
  isError,
  isEmpty = false,
  onRetry,
  children,
  className = '',
  headerActions,
}) => {
  const [retrying, setRetrying] = useState(false);
  const [retryStatus, setRetryStatus] = useState<string | null>(null);

  const handleRetry = async () => {
    setRetrying(true);
    setRetryStatus('Reconnecting WebSocket & REST endpoint...');

    try {
      if (onRetry) {
        await onRetry();
      }
      setRetryStatus('Connection re-established!');
      setTimeout(() => {
        setRetryStatus(null);
      }, 2000);
    } catch {
      setRetryStatus('Retry failed. Server unreachable.');
      setTimeout(() => {
        setRetryStatus(null);
      }, 3000);
    } finally {
      setRetrying(false);
    }
  };

  return (
    <div
      className={`glass rounded-2xl p-5 flex flex-col justify-between h-full text-slate-100 relative overflow-hidden shadow-card hover:shadow-card-hover transition-all duration-300 border border-white/[0.08] ${className}`}
    >
      {/* Top Gradient Shimmer Line */}
      <div className="absolute top-0 left-0 right-0 h-[2px] bg-gradient-to-r from-transparent via-cyan-500/50 to-transparent" />

      {/* Widget Header */}
      <div className="flex items-start justify-between border-b border-white/[0.06] pb-3.5 mb-4 shrink-0">
        <div>
          <h3 className="text-xs font-bold uppercase tracking-wider text-slate-200 flex items-center gap-2 font-mono">
            <span className="w-1.5 h-1.5 rounded-full bg-cyan-400 inline-block animate-pulse" />
            {title}
          </h3>
          {subtitle && (
            <p className="text-[11px] text-slate-400 font-sans mt-0.5 tracking-normal font-normal">
              {subtitle}
            </p>
          )}
        </div>
        {headerActions && <div className="flex items-center gap-2">{headerActions}</div>}
      </div>

      {/* Widget Body Content */}
      <div className="flex-1 min-h-0 flex flex-col justify-center">
        {isLoading ? (
          <div className="space-y-3 py-4">
            <div className="h-4 skeleton w-1/3 rounded" />
            <div className="h-3 skeleton w-5/6 rounded" />
            <div className="h-3 skeleton w-4/6 rounded" />
            <div className="h-3 skeleton w-2/3 rounded" />
          </div>
        ) : isError ? (
          <div className="flex flex-col items-center justify-center py-6 text-center">
            <AlertTriangle className="text-amber-400 mb-2 animate-bounce" size={24} />
            <span className="text-xs font-bold text-slate-200 block font-mono">
              TELEMETRY LINK INTERRUPTED
            </span>
            <span className="text-[11px] text-slate-400 block mb-4 max-w-[240px] mt-1">
              Unable to sync data stream from server endpoint.
            </span>

            {retryStatus && (
              <div className="mb-3 text-[10px] font-mono text-cyan-400 flex items-center gap-1.5 bg-blue-500/10 px-3 py-1 rounded-full border border-blue-500/20">
                <Wifi size={12} className={retrying ? 'animate-pulse' : ''} />
                <span>{retryStatus}</span>
              </div>
            )}

            {onRetry && (
              <button
                disabled={retrying}
                onClick={handleRetry}
                className="btn-secondary text-[11px] py-1.5 px-4 flex items-center gap-2 rounded-xl text-cyan-400 border-cyan-500/30 hover:border-cyan-500/60 hover:bg-cyan-500/10 transition-all cursor-pointer font-mono font-bold"
              >
                <RefreshCw size={12} className={retrying ? 'animate-spin' : ''} />
                <span>{retrying ? 'RECONNECTING STREAM...' : 'RETRY SYNC'}</span>
              </button>
            )}
          </div>
        ) : isEmpty ? (
          <div className="flex flex-col items-center justify-center py-8 text-center text-slate-400">
            <Info size={22} className="mb-2 text-slate-500" />
            <span className="text-xs font-semibold text-slate-300">No Telemetry Registers</span>
            <span className="text-[11px] text-slate-500 block mt-0.5">
              No logged data detected for this metric.
            </span>
          </div>
        ) : (
          children
        )}
      </div>
    </div>
  );
};

export default WidgetWrapper;

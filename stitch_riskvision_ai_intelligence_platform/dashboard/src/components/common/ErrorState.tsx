import React from 'react';
import GlassCard from './GlassCard';
import { AlertOctagon, RefreshCw } from 'lucide-react';

interface ErrorStateProps {
  title?: string;
  message?: string;
  onRetry?: () => void;
  className?: string;
}

export const ErrorState: React.FC<ErrorStateProps> = ({
  title = 'Data Aggregation Interrupted',
  message = 'Failed to compile active telemetry stream from the risk prediction service.',
  onRetry,
  className,
}) => {
  return (
    <GlassCard isCritical className={className}>
      <div className="flex flex-col items-center justify-center p-8 text-center">
        <div className="p-3.5 bg-red-500/15 border border-red-500/30 rounded-2xl text-red-400 mb-4 animate-pulse">
          <AlertOctagon size={32} />
        </div>
        <h3 className="text-sm font-bold text-slate-100 mb-1">{title}</h3>
        <p className="text-xs text-slate-400 max-w-sm mb-5 leading-relaxed">{message}</p>
        {onRetry && (
          <button onClick={onRetry} className="btn-danger text-xs py-2 px-4 rounded-xl">
            <RefreshCw size={14} />
            <span>Retry Re-synchronization</span>
          </button>
        )}
      </div>
    </GlassCard>
  );
};
export default ErrorState;

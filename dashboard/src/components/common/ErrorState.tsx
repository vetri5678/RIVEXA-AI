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
  title = 'Aggregation Failure',
  message = 'Failed to compile telemetry stream from the prediction cluster.',
  onRetry,
  className,
}) => {
  return (
    <GlassCard isCritical className={className}>
      <div className="flex flex-col items-center justify-center p-6 text-center">
        <div className="p-3 bg-neon-pink/10 border border-neon-pink/30 rounded-full text-neon-pink mb-4 animate-pulse">
          <AlertOctagon size={32} />
        </div>
        <h3 className="text-lg font-mono font-bold text-slate-200 mb-2">{title}</h3>
        <p className="text-sm text-slate-400 max-w-sm mb-6">{message}</p>
        {onRetry && (
          <button onClick={onRetry} className="btn-cyber-danger">
            <RefreshCw size={16} className="animate-spin-slow" />
            Retry Re-aggregation
          </button>
        )}
      </div>
    </GlassCard>
  );
};
export default ErrorState;

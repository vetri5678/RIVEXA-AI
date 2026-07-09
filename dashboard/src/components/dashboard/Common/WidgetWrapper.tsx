import React from 'react';
import { AlertTriangle, RefreshCw, Info } from 'lucide-react';

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
  return (
    <div className={`bg-cyber-900/80 border border-slate-800 rounded-lg p-4 flex flex-col justify-between h-full font-mono text-slate-100 hover:border-neon-blue/30 transition-all duration-300 relative overflow-hidden shadow-2xl ${className}`}>
      {/* Datadog Tech Grid header lines decoration */}
      <div className="absolute top-0 left-0 right-0 h-[2px] bg-gradient-to-r from-transparent via-slate-800 to-transparent" />
      <div className="absolute bottom-0 left-0 right-0 h-[1px] bg-cyber-800/50" />
      
      {/* Widget Header */}
      <div className="flex items-start justify-between border-b border-slate-800 pb-3 mb-4 shrink-0">
        <div>
          <h3 className="text-xs font-bold uppercase tracking-wider text-slate-300 flex items-center gap-1.5 font-mono">
            <span className="w-1.5 h-1.5 bg-neon-blue inline-block animate-pulse" />
            {title}
          </h3>
          {subtitle && (
            <p className="text-[10px] text-slate-500 font-mono mt-0.5 uppercase tracking-wide">
              {subtitle}
            </p>
          )}
        </div>
        {headerActions && <div className="flex items-center gap-2">{headerActions}</div>}
      </div>

      {/* Widget Body Content */}
      <div className="flex-1 min-h-0 flex flex-col justify-center">
        {isLoading ? (
          <div className="space-y-3 py-4 animate-pulse">
            <div className="h-4 bg-cyber-850 rounded w-1/3"></div>
            <div className="h-3 bg-cyber-850 rounded w-5/6"></div>
            <div className="h-3 bg-cyber-850 rounded w-4/6"></div>
            <div className="h-3 bg-cyber-850 rounded w-2/3"></div>
          </div>
        ) : isError ? (
          <div className="flex flex-col items-center justify-center py-6 text-center">
            <AlertTriangle className="text-neon-pink mb-2 animate-bounce" size={24} />
            <span className="text-[11px] font-bold text-slate-200 block">TELEMETRY ERROR</span>
            <span className="text-[10px] text-slate-500 block mb-4 max-w-[200px]">
              Datalink packet collection interrupted.
            </span>
            {onRetry && (
              <button
                onClick={onRetry}
                className="flex items-center gap-1.5 px-3 py-1.5 bg-neon-pink/10 hover:bg-neon-pink/20 text-neon-pink border border-neon-pink/30 hover:border-neon-pink rounded text-[10px] font-bold transition-all duration-350"
              >
                <RefreshCw size={10} className="animate-spin-slow" />
                RETRY HANDSHAKE
              </button>
            )}
          </div>
        ) : isEmpty ? (
          <div className="flex flex-col items-center justify-center py-8 text-center text-slate-500">
            <Info size={20} className="mb-2 text-slate-600" />
            <span className="text-[10px] font-bold uppercase">Telemetry Empty</span>
            <span className="text-[9px] block mt-0.5">No logged registers detected.</span>
          </div>
        ) : (
          children
        )}
      </div>
    </div>
  );
};
export default WidgetWrapper;

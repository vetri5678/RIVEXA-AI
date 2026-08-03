import React from 'react';
import GlassCard from './GlassCard';
import { Database } from 'lucide-react';

interface EmptyStateProps {
  title?: string;
  message?: string;
  className?: string;
}

export const EmptyState: React.FC<EmptyStateProps> = ({
  title = 'No Telemetry Records Found',
  message = 'The requested telemetry data registry is currently empty. Connect or sync repositories to begin real-time risk scoring.',
  className,
}) => {
  return (
    <GlassCard className={className}>
      <div className="flex flex-col items-center justify-center p-10 text-center">
        <div className="p-4 rounded-2xl bg-white/[0.04] border border-white/[0.08] text-slate-400 mb-4 shadow-inner">
          <Database size={32} className="text-cyan-400" />
        </div>
        <h3 className="text-sm font-bold text-slate-200 mb-1">{title}</h3>
        <p className="text-xs text-slate-400 max-w-sm leading-relaxed">{message}</p>
      </div>
    </GlassCard>
  );
};
export default EmptyState;

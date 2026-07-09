import React from 'react';
import GlassCard from './GlassCard';
import { Database } from 'lucide-react';

interface EmptyStateProps {
  title?: string;
  message?: string;
  className?: string;
}

export const EmptyState: React.FC<EmptyStateProps> = ({
  title = 'No Records Found',
  message = 'Telemetry data registry is empty. Sync repositories to generate predictions.',
  className,
}) => {
  return (
    <GlassCard className={className}>
      <div className="flex flex-col items-center justify-center p-8 text-center">
        <div className="p-3 bg-cyber-800 border border-cyber-700 rounded-full text-slate-400 mb-4">
          <Database size={28} />
        </div>
        <h3 className="text-md font-mono font-semibold text-slate-300 mb-1">{title}</h3>
        <p className="text-xs text-slate-500 max-w-xs">{message}</p>
      </div>
    </GlassCard>
  );
};
export default EmptyState;

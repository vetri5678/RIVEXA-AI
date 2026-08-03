import React from 'react';
import { useExecutiveSummary } from '../../../hooks/useDashboard';
import WidgetWrapper from '../Common/WidgetWrapper';
import { Sparkles } from 'lucide-react';

export const ExecutiveSummaryWidget: React.FC = () => {
  const { data: summary, isLoading, isError, refetch } = useExecutiveSummary();

  return (
    <WidgetWrapper
      title="EXECUTIVE AI SUMMARY"
      subtitle="Generative situational report compiled from current observations"
      isLoading={isLoading}
      isError={isError}
      onRetry={refetch}
    >
      <div className="flex gap-3 items-start p-3 bg-cyber-950/60 border border-slate-800 rounded-lg py-4">
        <div className="p-2 bg-neon-blue/10 border border-neon-blue/20 rounded-lg text-neon-blue shrink-0 animate-pulse-slow">
          <Sparkles size={16} />
        </div>
        <div className="font-mono text-xs text-slate-300 leading-relaxed">
          {summary ? (
            <p>{summary.summary_text}</p>
          ) : (
            <p>Initializing analysis matrices...</p>
          )}
        </div>
      </div>
    </WidgetWrapper>
  );
};
export default ExecutiveSummaryWidget;

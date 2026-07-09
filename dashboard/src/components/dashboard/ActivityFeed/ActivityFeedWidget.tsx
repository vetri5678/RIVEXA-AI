import React from 'react';
import { useActivity } from '../../../hooks/useDashboard';
import WidgetWrapper from '../Common/WidgetWrapper';
import { Terminal, Clock } from 'lucide-react';

export const ActivityFeedWidget: React.FC = () => {
  const { data: logFeed, isLoading, isError, refetch } = useActivity(5);

  return (
    <WidgetWrapper
      title="SYSTEM AUDIT & EVENT LOG"
      subtitle="Structured registers compiled from secure audit records"
      isLoading={isLoading}
      isError={isError}
      onRetry={refetch}
    >
      <div className="space-y-2 py-1 overflow-y-auto max-h-[180px] flex-1 flex flex-col justify-start">
        {logFeed?.items.slice(0, 5).map((log) => (
          <div
            key={log.id}
            className="flex items-start gap-2 p-2 border border-slate-900 bg-cyber-950/40 rounded hover:border-slate-800 transition-all duration-200"
          >
            <div className="mt-0.5 text-slate-500 shrink-0">
              <Terminal size={12} />
            </div>
            <div className="flex-1 min-w-0 font-mono text-[9px]">
              <div className="flex justify-between items-center text-slate-500 mb-0.5 font-bold">
                <span className="uppercase text-neon-blue font-black">{log.action}</span>
                <div className="flex items-center gap-0.5 font-normal">
                  <Clock size={8} />
                  <span>{new Date(log.created_at).toLocaleTimeString()}</span>
                </div>
              </div>
              <p className="text-slate-300 leading-relaxed truncate">{log.description}</p>
            </div>
          </div>
        ))}
        {(!logFeed || logFeed.items.length === 0) && (
          <div className="text-center py-6 text-slate-500 uppercase tracking-widest font-mono text-[9px]">
            No recent system events
          </div>
        )}
      </div>
    </WidgetWrapper>
  );
};
export default ActivityFeedWidget;

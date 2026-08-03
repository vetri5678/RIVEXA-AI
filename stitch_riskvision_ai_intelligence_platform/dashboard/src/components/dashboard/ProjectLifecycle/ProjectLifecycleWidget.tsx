import React from 'react';
import WidgetWrapper from '../Common/WidgetWrapper';
import { useProjectLifecycleCounts } from '../../../hooks/useDashboard';

export const ProjectLifecycleWidget: React.FC = () => {
  const { data: lifecycleData, isLoading, isError, refetch } = useProjectLifecycleCounts();

  const steps = lifecycleData?.steps || [
    { label: 'Idea', count: 0, color: 'bg-slate-800' },
    { label: 'Dev', count: 0, color: 'bg-neon-blue' },
    { label: 'Testing', count: 0, color: 'bg-neon-purple' },
    { label: 'Deploy', count: 0, color: 'bg-neon-green' },
    { label: 'Ops', count: 0, color: 'bg-neon-yellow' },
    { label: 'Inactive', count: 0, color: 'bg-neon-orange' },
    { label: 'Archived', count: 0, color: 'bg-slate-700' },
    { label: 'Dead', count: 0, color: 'bg-neon-pink' },
  ];

  const total = lifecycleData?.total || steps.reduce((sum, s) => sum + s.count, 0);

  return (
    <WidgetWrapper
      title="PROJECT LIFECYCLE TRACKER"
      subtitle={`Live database repository distribution (${total} Total)`}
      isLoading={isLoading}
      isError={isError}
      onRetry={refetch}
    >
      <div className="space-y-4 py-2 font-mono text-[9px] w-full">
        {/* Horizontal bar compilation */}
        <div className="flex h-3 w-full bg-cyber-950 border border-slate-800 rounded-full overflow-hidden">
          {steps.map((step) => {
            const pct = total > 0 ? (step.count / total) * 100 : 0;
            return (
              <div
                key={step.label}
                className={`${step.color} transition-all duration-500`}
                style={{ width: `${pct}%` }}
                title={`${step.label}: ${step.count} (${pct.toFixed(1)}%)`}
              />
            );
          })}
        </div>

        {/* Text descriptions grid (8 phases) */}
        <div className="grid grid-cols-2 sm:grid-cols-4 md:grid-cols-8 gap-2">
          {steps.map((step) => {
            const pct = total > 0 ? ((step.count / total) * 100).toFixed(1) : '0';
            return (
              <div key={step.label} className="p-1.5 border border-slate-900 bg-cyber-950/20 text-center rounded hover:border-slate-700 transition-colors">
                <span className="text-slate-500 uppercase block tracking-wider mb-0.5 text-[8px] truncate">{step.label}</span>
                <span className="text-slate-200 font-bold text-[11px] block">{step.count}</span>
                <span className="text-slate-600 text-[7.5px] block">{pct}%</span>
              </div>
            );
          })}
        </div>
      </div>
    </WidgetWrapper>
  );
};

export default ProjectLifecycleWidget;

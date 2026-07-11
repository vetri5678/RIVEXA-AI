import React from 'react';
import WidgetWrapper from '../Common/WidgetWrapper';
import { useOverview } from '../../../hooks/useDashboard';
import { GitCommit, GitPullRequest, ShieldAlert, ShieldCheck } from 'lucide-react';

export const ActivityMonitorWidget: React.FC = () => {
  const { isLoading, isError, refetch } = useOverview();

  const metrics = [
    { label: 'Commits Today', count: 184, icon: GitCommit, color: 'text-neon-blue' },
    { label: 'Merged PRs', count: 24, icon: GitPullRequest, color: 'text-neon-purple' },
    { label: 'Failed Builds', count: 3, icon: ShieldAlert, color: 'text-neon-pink' },
    { label: 'Successful Builds', count: 42, icon: ShieldCheck, color: 'text-neon-green' },
  ];

  return (
    <WidgetWrapper
      title="REPOSITORY ACTIVITY MONITOR"
      subtitle="Operational telemetry from VCS and CI systems"
      isLoading={isLoading}
      isError={isError}
      onRetry={refetch}
    >
      <div className="grid grid-cols-2 gap-3 py-1">
        {metrics.map((metric) => (
          <div
            key={metric.label}
            className="p-3 border border-slate-800 bg-cyber-950/40 rounded-lg hover:border-slate-700 transition-all duration-200"
          >
            <div className="flex items-center gap-1.5 mb-1.5 font-mono text-[9px] text-slate-500 uppercase tracking-wider">
              <metric.icon size={12} className={metric.color} />
              <span>{metric.label}</span>
            </div>
            <span className="text-xl font-bold font-mono text-slate-200">{metric.count}</span>
          </div>
        ))}
      </div>
    </WidgetWrapper>
  );
};
export default ActivityMonitorWidget;

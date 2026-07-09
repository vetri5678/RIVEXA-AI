import React from 'react';
import { useRiskDistribution } from '../../../hooks/useDashboard';
import WidgetWrapper from '../Common/WidgetWrapper';
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from 'recharts';

export const RiskDistributionWidget: React.FC = () => {
  const { data: dist, isLoading, isError, refetch } = useRiskDistribution();

  const data = dist?.slices.map((slice) => ({
    name: slice.level,
    value: slice.count,
    color: slice.color,
  })) || [];

  return (
    <WidgetWrapper
      title="RISK DISTRIBUTION MATRIX"
      subtitle="Categorized project counts by ML risk thresholds"
      isLoading={isLoading}
      isError={isError}
      onRetry={refetch}
    >
      <div className="flex items-center justify-between gap-4 h-full pt-1">
        <div className="w-1/2 h-32 relative">
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie
                data={data}
                cx="50%"
                cy="50%"
                innerRadius="65%"
                outerRadius="90%"
                paddingAngle={3}
                dataKey="value"
              >
                {data.map((entry, index) => (
                  <Cell key={`cell-${index}`} fill={entry.color} stroke="#020817" strokeWidth={1} />
                ))}
              </Pie>
              <Tooltip
                contentStyle={{
                  background: '#050b1a',
                  borderColor: 'rgba(0, 212, 255, 0.15)',
                  fontSize: '10px',
                  fontFamily: 'monospace',
                }}
              />
            </PieChart>
          </ResponsiveContainer>
          <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
            <span className="text-xl font-black text-slate-100 font-mono">
              {dist?.total || 0}
            </span>
            <span className="text-[8px] text-slate-500 uppercase tracking-widest font-mono font-bold">
              Total
            </span>
          </div>
        </div>

        <div className="w-1/2 space-y-2 font-mono text-[10px]">
          {dist?.slices.map((slice) => (
            <div key={slice.level} className="flex items-center justify-between border-b border-slate-900 pb-1">
              <div className="flex items-center gap-1.5">
                <span className="h-1.5 w-1.5 rounded-full" style={{ backgroundColor: slice.color }} />
                <span className="text-slate-400 capitalize">{slice.level.toLowerCase()}</span>
              </div>
              <div className="text-right">
                <span className="text-slate-200 font-bold block">{slice.count}</span>
                <span className="text-[8px] text-slate-500 block">{slice.percentage}%</span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </WidgetWrapper>
  );
};
export default RiskDistributionWidget;

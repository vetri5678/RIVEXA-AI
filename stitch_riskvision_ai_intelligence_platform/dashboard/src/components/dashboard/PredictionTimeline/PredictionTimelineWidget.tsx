import React, { useState } from 'react';
import { usePredictionTimeline } from '../../../hooks/useDashboard';
import WidgetWrapper from '../Common/WidgetWrapper';
import { AreaChart, Area, XAxis, YAxis, ResponsiveContainer, Tooltip } from 'recharts';

export const PredictionTimelineWidget: React.FC = () => {
  const [granularity, setGranularity] = useState<'hourly' | 'daily' | 'weekly' | 'monthly'>('daily');
  const { data: timeline, isLoading, isError, refetch } = usePredictionTimeline(granularity);

  return (
    <WidgetWrapper
      title="RISK INTELLIGENCE TIMELINE"
      subtitle="AI failure probability forecasts across temporal limits"
      isLoading={isLoading}
      isError={isError}
      onRetry={refetch}
      headerActions={
        <div className="flex bg-cyber-950 p-0.5 rounded border border-slate-800">
          {(['hourly', 'daily', 'weekly', 'monthly'] as const).map((g) => (
            <button
              key={g}
              onClick={() => setGranularity(g)}
              className={`px-1.5 py-0.5 rounded text-[8px] uppercase font-bold font-mono tracking-wider transition-all duration-200 ${
                granularity === g ? 'bg-neon-blue text-cyber-950 shadow-[0_0_8px_rgba(0,212,255,0.2)]' : 'text-slate-500 hover:text-slate-300'
              }`}
            >
              {g}
            </button>
          ))}
        </div>
      }
    >
      <div className="h-44 w-full pt-1">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={timeline?.points || []} margin={{ top: 5, right: 5, left: -25, bottom: 0 }}>
            <defs>
              <linearGradient id="colorRiskTimeline" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#00d4ff" stopOpacity={0.15} />
                <stop offset="95%" stopColor="#00d4ff" stopOpacity={0} />
              </linearGradient>
            </defs>
            <XAxis
              dataKey="period"
              tickLine={false}
              axisLine={false}
              tick={{ fill: '#475569', fontSize: 8, fontFamily: 'monospace' }}
            />
            <YAxis
              tickLine={false}
              axisLine={false}
              tick={{ fill: '#475569', fontSize: 8, fontFamily: 'monospace' }}
            />
            <Tooltip
              contentStyle={{
                background: '#050b1a',
                borderColor: 'rgba(0, 212, 255, 0.15)',
                fontSize: '9px',
                fontFamily: 'monospace',
                color: '#f8fafc',
              }}
            />
            <Area
              type="monotone"
              dataKey="avg_risk_score"
              stroke="#00d4ff"
              fillOpacity={1}
              fill="url(#colorRiskTimeline)"
              strokeWidth={1.5}
              name="Index"
            />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </WidgetWrapper>
  );
};
export default PredictionTimelineWidget;

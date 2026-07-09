import React from 'react';
import { useFeatureImportance } from '../../../hooks/useDashboard';
import WidgetWrapper from '../Common/WidgetWrapper';
import { BarChart, Bar, XAxis, YAxis, ResponsiveContainer, Tooltip } from 'recharts';

export const ExplainableAIWidget: React.FC = () => {
  const { data: shap, isLoading, isError, refetch } = useFeatureImportance();

  const data = shap?.features.slice(0, 6).map((feat) => ({
    name: feat.display_name,
    impact: feat.avg_impact,
    pct: feat.contribution_pct,
    direction: feat.direction,
  })) || [];

  return (
    <WidgetWrapper
      title="EXPLAINABLE AI (XAI) INDEX"
      subtitle="Feature impact coefficients aggregated from Shapley SHAP parameters"
      isLoading={isLoading}
      isError={isError}
      onRetry={refetch}
    >
      <div className="h-44 w-full pt-1">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} layout="vertical" margin={{ left: 5, right: 5, top: 0, bottom: 0 }}>
            <XAxis type="number" hide />
            <YAxis
              dataKey="name"
              type="category"
              axisLine={false}
              tickLine={false}
              tick={{ fill: '#64748b', fontSize: 9, fontFamily: 'monospace' }}
              width={90}
            />
            <Tooltip
              content={({ active, payload }) => {
                if (active && payload && payload.length) {
                  const dp = payload[0].payload;
                  return (
                    <div className="bg-cyber-950 border border-slate-800 p-2 rounded font-mono text-[9px] shadow-2xl">
                      <p className="font-bold text-slate-200">{dp.name}</p>
                      <p className="text-neon-blue mt-0.5">Telemetry Impact: {dp.impact.toFixed(4)}</p>
                      <p className="text-slate-500">Weight contribution: {dp.pct}%</p>
                      <p className={dp.direction === 'increases_risk' ? 'text-neon-pink' : 'text-neon-green'}>
                        {dp.direction === 'increases_risk' ? '↑ Risk Accelerator' : '↓ Protective Barrier'}
                      </p>
                    </div>
                  );
                }
                return null;
              }}
            />
            <Bar dataKey="impact" radius={2}>
              {data.map((entry, index) => (
                <rect
                  key={`rect-${index}`}
                  fill={entry.direction === 'increases_risk' ? '#ff2d55' : '#00ff88'}
                  opacity={0.8}
                />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
    </WidgetWrapper>
  );
};
export default ExplainableAIWidget;

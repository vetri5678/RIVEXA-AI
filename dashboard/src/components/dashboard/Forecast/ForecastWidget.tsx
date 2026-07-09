import React, { useState } from 'react';
import { useForecast } from '../../../hooks/useDashboard';
import WidgetWrapper from '../Common/WidgetWrapper';
import { LineChart, Line, XAxis, YAxis, ResponsiveContainer, Tooltip, Area } from 'recharts';

export const ForecastWidget: React.FC = () => {
  const [horizon, setHorizon] = useState<'7' | '30' | '90'>('30');
  const { data: forecast, isLoading, isError, refetch } = useForecast();

  const getChartData = () => {
    if (!forecast) return [];
    if (horizon === '7') return forecast.seven_day;
    if (horizon === '90') return forecast.ninety_day;
    return forecast.thirty_day;
  };

  return (
    <WidgetWrapper
      title="PREDICTIVE RISK FORECAST"
      subtitle="Statistical moving averages projecting future abandonment scores"
      isLoading={isLoading}
      isError={isError}
      onRetry={refetch}
      headerActions={
        <div className="flex bg-cyber-950 p-0.5 rounded border border-slate-800">
          {(['7', '30', '90'] as const).map((h) => (
            <button
              key={h}
              onClick={() => setHorizon(h)}
              className={`px-2 py-0.5 rounded text-[8px] uppercase font-bold font-mono tracking-wider transition-all duration-200 ${
                horizon === h ? 'bg-neon-blue text-cyber-950 shadow-[0_0_8px_rgba(0,212,255,0.2)]' : 'text-slate-500 hover:text-slate-300'
              }`}
            >
              {h}D
            </button>
          ))}
        </div>
      }
    >
      <div className="h-44 w-full pt-1">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={getChartData()} margin={{ top: 5, right: 5, left: -25, bottom: 0 }}>
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
              domain={[0, 100]}
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
            <Line
              type="monotone"
              dataKey="projected_risk_score"
              stroke="#a855f7"
              strokeWidth={1.5}
              dot={false}
              name="Projected Index"
            />
            <Line
              type="monotone"
              dataKey="confidence_interval_high"
              stroke="#ff2d55"
              strokeWidth={1}
              strokeDasharray="3 3"
              dot={false}
              name="High Interval"
            />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </WidgetWrapper>
  );
};
export default ForecastWidget;

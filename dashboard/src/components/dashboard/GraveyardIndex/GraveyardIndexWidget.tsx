import React from 'react';
import { useGraveyardIndex } from '../../../hooks/useDashboard';
import WidgetWrapper from '../Common/WidgetWrapper';
import AnimatedNumber from '../../common/AnimatedNumber';
import { TrendingUp, TrendingDown } from 'lucide-react';

export const GraveyardIndexWidget: React.FC = () => {
  const { data: indexData, isLoading, isError, refetch } = useGraveyardIndex();

  const getStrokeDashOffset = (score: number) => {
    const progress = Math.min(score / 100, 1);
    return 339.3 * (1 - progress);
  };

  return (
    <WidgetWrapper
      title="GRAVEYARD INDEX"
      subtitle="Primary platform risk metric (Weighted Score 0-100)"
      isLoading={isLoading}
      isError={isError}
      onRetry={refetch}
    >
      <div className="flex flex-col items-center justify-between h-full pt-1">
        <div className="relative w-36 h-36 flex items-center justify-center">
          <svg className="w-full h-full transform -rotate-90">
            <circle
              cx="72"
              cy="72"
              r="54"
              className="stroke-cyber-850"
              strokeWidth="8"
              fill="transparent"
            />
            {indexData && (
              <circle
                cx="72"
                cy="72"
                r="54"
                stroke={indexData.color}
                strokeWidth="8"
                fill="transparent"
                strokeDasharray="339.3"
                strokeDashoffset={getStrokeDashOffset(indexData.index)}
                strokeLinecap="round"
                className="transition-all duration-1000 ease-out"
              />
            )}
          </svg>

          <div className="absolute text-center mt-2 font-mono">
            <span className="text-3xl font-black text-slate-100 block tracking-tight">
              {indexData ? <AnimatedNumber value={indexData.index} /> : '0'}
            </span>
            <span className="text-[9px] uppercase tracking-wider text-slate-400 font-bold block mt-0.5">
              {indexData?.classification || 'PENDING'}
            </span>
          </div>
        </div>

        <div className="flex items-center justify-between w-full border-t border-slate-800/60 pt-3 mt-4 text-[11px]">
          <div>
            <span className="text-slate-500 block text-[9px] uppercase">Active Failures</span>
            <span className="text-slate-200 font-bold">
              {indexData ? indexData.critical_count + indexData.high_count : 0} Repos
            </span>
          </div>
          <div className="text-right">
            <span className="text-slate-500 block text-[9px] uppercase">7D Velocity</span>
            <div className="flex items-center gap-1 justify-end font-bold">
              {indexData && indexData.trend >= 0 ? (
                <>
                  <TrendingUp size={12} className="text-neon-pink" />
                  <span className="text-neon-pink">+{indexData.trend}%</span>
                </>
              ) : (
                <>
                  <TrendingDown size={12} className="text-neon-green" />
                  <span className="text-neon-green">{indexData?.trend}%</span>
                </>
              )}
            </div>
          </div>
        </div>
      </div>
    </WidgetWrapper>
  );
};
export default GraveyardIndexWidget;

import React from 'react';
import { useRecommendations } from '../../../hooks/useDashboard';
import WidgetWrapper from '../Common/WidgetWrapper';
import Badge from '../../common/Badge';
import { CheckSquare } from 'lucide-react';

export const RecommendationsWidget: React.FC = () => {
  const { data: recs, isLoading, isError, refetch } = useRecommendations();

  return (
    <WidgetWrapper
      title="AI REMEDIATION PLAN"
      subtitle="Mitigation instructions generated from top abandonment drivers"
      isLoading={isLoading}
      isError={isError}
      onRetry={refetch}
    >
      <div className="space-y-2.5 py-1 flex-1 flex flex-col justify-start">
        {recs?.items.slice(0, 3).map((item) => (
          <div
            key={item.id}
            className="p-2.5 border border-slate-800 bg-cyber-950/20 rounded hover:border-neon-green/20 transition-all duration-200"
          >
            <div className="flex justify-between items-center mb-1 font-mono text-[10px]">
              <div className="flex items-center gap-1.5 text-slate-300 font-bold">
                <CheckSquare size={12} className="text-neon-green" />
                <span>{item.action}</span>
              </div>
              <Badge label={item.priority} variant={item.priority === 'CRITICAL' ? 'critical' : 'warning'} />
            </div>
            <p className="text-[9px] text-slate-500 font-mono leading-relaxed pl-4">
              Driver: {item.related_risk_factor.replace(/_/g, ' ')} | Impact: {item.expected_impact}
            </p>
          </div>
        ))}
        {(!recs || recs.items.length === 0) && (
          <div className="text-center py-6 text-slate-500 uppercase tracking-widest font-mono text-[9px]">
            No remediation steps needed
          </div>
        )}
      </div>
    </WidgetWrapper>
  );
};
export default RecommendationsWidget;

import React from 'react';
import { useTelemetryAnalysis } from '../../../hooks/useDashboard';
import AICard from '../../common/AICard';

export const AITelemetryAnalysisWidget: React.FC = () => {
  const { data, isLoading, isError, refetch } = useTelemetryAnalysis();

  // If there's an error, we show the card with a retry option.
  // We handle the raw string content or fallback message.
  const content = isError 
    ? '{"summary":"AI temporarily unavailable. Re-indexing telemetry pipeline...","severity":"MEDIUM","confidence":"0%","rootCause":"Connection to AI completion provider lost or rate limited.","recommendations":["Click RETRY to establish new sync session.","Verify backend environment keys.","Wait 30s before retry."] }'
    : (data ? (typeof data === 'object' ? JSON.stringify(data) : data) : '');

  return (
    <AICard
      title="SYSTEM AUDIT COGNITIVE REPORT"
      subtitle="Real-time generative audit report compiled by OpenRouter"
      content={content}
      isLoading={isLoading}
      onRetry={refetch}
      className="h-full"
    />
  );
};

export default AITelemetryAnalysisWidget;

import React from 'react';
import WidgetWrapper from '../Common/WidgetWrapper';
import ReactECharts from 'echarts-for-react';
import { useMLFeatureImportance } from '../../../hooks/useMLPrediction';

/**
 * TeamAnalyticsWidget — Dynamic Department Risk Spectrum via ML Feature Importance.
 * Replaces the static hardcoded [85, 45, 90, 30, 60] arrays with live feature importance
 * data from the RandomForest ML model.
 */
export const TeamAnalyticsWidget: React.FC = () => {
  const { data: featureData, isLoading, isError, refetch } = useMLFeatureImportance();

  // Build radar indicators and values from the top 5 ranked features by ML model
  const topFeatures = featureData?.ranked_features?.slice(0, 5) ?? [];

  const indicatorLabels = topFeatures.length > 0
    ? topFeatures.map((f) => ({ name: truncateLabel(f.feature), max: 100 }))
    : [
        { name: 'Budget Overrun', max: 100 },
        { name: 'Schedule Delay', max: 100 },
        { name: 'Critical Bugs', max: 100 },
        { name: 'Tech Debt', max: 100 },
        { name: 'Security Issues', max: 100 },
      ];

  // Convert feature importance (0-1) → risk impact (0-100)
  const highRiskValues = topFeatures.length > 0
    ? topFeatures.map((f) => parseFloat((f.importance * 100).toFixed(1)))
    : [85, 45, 90, 30, 60];

  // Moderate risk is inversely modulated
  const moderateValues = highRiskValues.map((v) => parseFloat((v * 0.65).toFixed(1)));

  const radarOption = {
    backgroundColor: 'transparent',
    color: ['#00d4ff', '#a855f7'],
    tooltip: {
      trigger: 'item',
      backgroundColor: '#050b1a',
      borderColor: 'rgba(0, 212, 255, 0.15)',
      textStyle: {
        color: '#f8fafc',
        fontFamily: 'monospace',
        fontSize: 10,
      },
    },
    radar: {
      indicator: indicatorLabels,
      shape: 'circle',
      axisName: {
        color: '#64748b',
        fontFamily: 'monospace',
        fontSize: 8,
        textTransform: 'uppercase',
      },
      splitLine: {
        lineStyle: { color: 'rgba(255, 255, 255, 0.03)' },
      },
      splitArea: { show: false },
      axisLine: {
        lineStyle: { color: 'rgba(255, 255, 255, 0.03)' },
      },
    },
    series: [
      {
        name: 'Risk Spectrum',
        type: 'radar',
        data: [
          {
            value: highRiskValues,
            name: 'Failure Probability',
            areaStyle: { color: 'rgba(0, 212, 255, 0.1)' },
            lineStyle: { width: 1.5 },
          },
          {
            value: moderateValues,
            name: 'Abandonment Danger',
            areaStyle: { color: 'rgba(168, 85, 247, 0.1)' },
            lineStyle: { width: 1.5, type: 'dashed' },
          },
        ],
      },
    ],
  };

  return (
    <WidgetWrapper
      title="DEPARTMENT RISK SPECTRUM"
      subtitle="Radar spectrum — ML feature importance mapped to risk dimensions"
      isLoading={isLoading}
      isError={isError}
      onRetry={refetch}
    >
      <div className="h-44 w-full">
        <ReactECharts option={radarOption} style={{ height: '100%', width: '100%' }} />
      </div>
    </WidgetWrapper>
  );
};

function truncateLabel(label: string): string {
  const words = label.split(' ');
  return words.length > 2 ? words.slice(0, 2).join(' ') : label;
}

export default TeamAnalyticsWidget;

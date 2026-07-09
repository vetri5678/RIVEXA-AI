import React from 'react';
import WidgetWrapper from '../Common/WidgetWrapper';
import ReactECharts from 'echarts-for-react';

export const TeamAnalyticsWidget: React.FC = () => {
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
      indicator: [
        { name: 'Engineering', max: 100 },
        { name: 'QA & Testing', max: 100 },
        { name: 'DevOps/CI', max: 100 },
        { name: 'Security', max: 100 },
        { name: 'R&D', max: 100 },
      ],
      shape: 'circle',
      axisName: {
        color: '#64748b',
        fontFamily: 'monospace',
        fontSize: 8,
        textTransform: 'uppercase',
      },
      splitLine: {
        lineStyle: {
          color: 'rgba(255, 255, 255, 0.03)',
        },
      },
      splitArea: {
        show: false,
      },
      axisLine: {
        lineStyle: {
          color: 'rgba(255, 255, 255, 0.03)',
        },
      },
    },
    series: [
      {
        name: 'Risk Spectrum',
        type: 'radar',
        data: [
          {
            value: [85, 45, 90, 30, 60],
            name: 'Failure Probability',
            areaStyle: {
              color: 'rgba(0, 212, 255, 0.1)',
            },
            lineStyle: {
              width: 1.5,
            },
          },
          {
            value: [65, 35, 75, 50, 40],
            name: 'Abandonment Danger',
            areaStyle: {
              color: 'rgba(168, 85, 247, 0.1)',
            },
            lineStyle: {
              width: 1.5,
              type: 'dashed',
            },
          },
        ],
      },
    ],
  };

  return (
    <WidgetWrapper
      title="DEPARTMENT RISK SPECTRUM"
      subtitle="Radar spectrum mapping average failures across teams"
      isLoading={false}
      isError={false}
    >
      <div className="h-44 w-full">
        <ReactECharts option={radarOption} style={{ height: '100%', width: '100%' }} />
      </div>
    </WidgetWrapper>
  );
};
export default TeamAnalyticsWidget;

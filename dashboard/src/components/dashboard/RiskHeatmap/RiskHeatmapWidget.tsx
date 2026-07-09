import React from 'react';
import WidgetWrapper from '../Common/WidgetWrapper';
import ReactECharts from 'echarts-for-react';

export const RiskHeatmapWidget: React.FC = () => {
  // Columns: Commits, Issues, PR Delay, Coverage, Complexity
  const xData = ['Commits', 'Issues', 'PR Delay', 'Coverage', 'Complexity'];
  
  // Rows: inventory-service, auth-gateway, billing-core, data-pipeline, notification-node
  const yData = ['inventory-svc', 'auth-gateway', 'billing-core', 'data-pipeline', 'notify-node'];

  // Map values [x, y, value] where value 0-100 represents risk
  const data = [
    [0, 0, 90], [1, 0, 85], [2, 0, 95], [3, 0, 10], [4, 0, 80], // inventory-svc (high risk)
    [0, 1, 10], [1, 1, 20], [2, 1, 15], [3, 1, 85], [4, 1, 12], // auth-gateway (low risk)
    [0, 2, 45], [1, 2, 60], [2, 2, 50], [3, 2, 60], [4, 2, 35], // billing-core (medium risk)
    [0, 3, 15], [1, 3, 10], [2, 3, 20], [3, 3, 90], [4, 3, 15], // data-pipeline (healthy)
    [0, 4, 80], [1, 4, 75], [2, 4, 85], [3, 4, 20], [4, 4, 70], // notify-node (critical)
  ];

  const option = {
    backgroundColor: 'transparent',
    tooltip: {
      position: 'top',
      backgroundColor: '#050b1a',
      borderColor: 'rgba(0, 212, 255, 0.15)',
      textStyle: {
        color: '#f8fafc',
        fontFamily: 'monospace',
        fontSize: 9,
      },
    },
    grid: {
      top: '10%',
      bottom: '15%',
      left: '20%',
      right: '5%',
    },
    xAxis: {
      type: 'category',
      data: xData,
      splitArea: {
        show: true,
      },
      axisLabel: {
        color: '#64748b',
        fontFamily: 'monospace',
        fontSize: 8,
      },
      axisLine: {
        show: false,
      },
    },
    yAxis: {
      type: 'category',
      data: yData,
      splitArea: {
        show: true,
      },
      axisLabel: {
        color: '#64748b',
        fontFamily: 'monospace',
        fontSize: 8,
      },
      axisLine: {
        show: false,
      },
    },
    visualMap: {
      min: 0,
      max: 100,
      calculable: true,
      orient: 'horizontal',
      left: 'center',
      bottom: '0%',
      show: false,
      inRange: {
        color: ['#00ff88', '#f59e0b', '#ff6b35', '#ff2d55'], // Green, Yellow, Orange, Red
      },
    },
    series: [
      {
        name: 'Risk Weight',
        type: 'heatmap',
        data: data,
        label: {
          show: true,
          color: '#000',
          fontSize: 8,
          fontFamily: 'monospace',
          formatter: (params: any) => `${params.value[2]}%`,
        },
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowColor: 'rgba(0, 0, 0, 0.5)',
          },
        },
      },
    ],
  };

  return (
    <WidgetWrapper
      title="RISK HEATMAP"
      subtitle="Grid matrix correlating repository sectors with metrics anomalies"
      isLoading={false}
      isError={false}
    >
      <div className="h-44 w-full">
        <ReactECharts option={option} style={{ height: '100%', width: '100%' }} />
      </div>
    </WidgetWrapper>
  );
};
export default RiskHeatmapWidget;

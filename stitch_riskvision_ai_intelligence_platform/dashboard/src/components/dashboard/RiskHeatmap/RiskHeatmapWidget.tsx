import React, { useState } from 'react';
import WidgetWrapper from '../Common/WidgetWrapper';
import ReactECharts from 'echarts-for-react';
import { Search, Filter, ArrowUpDown, Download, ChevronLeft, ChevronRight } from 'lucide-react';
import { useRiskHeatmap } from '../../../hooks/useDashboard';

export const RiskHeatmapWidget: React.FC = () => {
  const [search, setSearch] = useState('');
  const [riskLevel, setRiskLevel] = useState('');
  const [sortBy, setSortBy] = useState('name');
  const [sortDesc, setSortDesc] = useState(false);
  const [page, setPage] = useState(1);
  const pageSize = 10;

  const { data: heatmapResponse, isLoading, isError, refetch } = useRiskHeatmap({
    search: search.trim() || undefined,
    risk_level: riskLevel || undefined,
    sort_by: sortBy,
    sort_desc: sortDesc,
    page,
    page_size: pageSize,
  });

  const xData = heatmapResponse?.xData || [
    'Commits', 'Issues', 'Pull Requests', 'Security', 'Coverage', 'Complexity', 'Technical Debt', 'Risk Score'
  ];
  const yData = heatmapResponse?.yData || [];
  const heatmapData = heatmapResponse?.heatmapData || [];
  const totalPages = heatmapResponse?.total_pages || 1;
  const totalItems = heatmapResponse?.total || 0;

  // Handle Export to CSV
  const handleExportCSV = () => {
    if (!heatmapResponse?.rows || heatmapResponse.rows.length === 0) return;

    const headers = ['Repository Name', 'Risk Level', 'Health Score', 'Failure Probability', ...xData];
    const csvRows = [headers.join(',')];

    heatmapResponse.rows.forEach((row) => {
      const line = [
        `"${row.name}"`,
        `"${row.risk_level}"`,
        row.health_score,
        row.failure_probability,
        ...xData.map((col) => row.metrics[col] ?? 0)
      ];
      csvRows.push(line.join(','));
    });

    const blob = new Blob([csvRows.join('\n')], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `RiskVision_Heatmap_Export_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const option = {
    backgroundColor: 'transparent',
    tooltip: {
      position: 'top',
      backgroundColor: '#050b1a',
      borderColor: 'rgba(0, 212, 255, 0.3)',
      borderWidth: 1,
      extraCssText: 'box-shadow: 0 0 20px rgba(0, 212, 255, 0.2); border-radius: 8px;',
      formatter: (params: any) => {
        const colName = xData[params.value[0]] || '';
        const repoName = yData[params.value[1]] || '';
        const val = params.value[2];
        let colorStr = '#00ff88';
        if (val > 75) colorStr = '#ff2d55';
        else if (val > 50) colorStr = '#ff9f43';
        else if (val > 25) colorStr = '#f59e0b';

        return `
          <div style="font-family: monospace; font-size: 11px; padding: 4px 8px;">
            <div style="color: #94a3b8; font-weight: bold; margin-bottom: 2px;">${repoName}</div>
            <div style="color: #f8fafc;">Metric: <span style="color: #38bdf8;">${colName}</span></div>
            <div style="color: ${colorStr}; font-weight: bold; margin-top: 2px;">Value: ${val}%</div>
          </div>
        `;
      },
    },
    grid: {
      top: '5%',
      bottom: '18%',
      left: '18%',
      right: '4%',
    },
    xAxis: {
      type: 'category',
      data: xData,
      splitArea: { show: true, areaStyle: { color: ['rgba(255,255,255,0.01)', 'rgba(0,0,0,0.2)'] } },
      axisLabel: { color: '#94a3b8', fontFamily: 'monospace', fontSize: 9, interval: 0, rotate: 15 },
      axisLine: { show: false },
    },
    yAxis: {
      type: 'category',
      data: yData,
      splitArea: { show: true, areaStyle: { color: ['rgba(255,255,255,0.01)', 'rgba(0,0,0,0.2)'] } },
      axisLabel: { color: '#cbd5e1', fontFamily: 'monospace', fontSize: 9, fontWeight: 'bold' },
      axisLine: { show: false },
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
        color: ['#052e16', '#15803d', '#eab308', '#f97316', '#ef4444'],
      },
    },
    series: [
      {
        name: 'Risk Score',
        type: 'heatmap',
        data: heatmapData,
        label: {
          show: true,
          color: '#ffffff',
          fontSize: 9,
          fontFamily: 'monospace',
          fontWeight: 'bold',
          formatter: (params: any) => `${params.value[2]}%`,
        },
        emphasis: {
          itemStyle: {
            shadowBlur: 12,
            shadowColor: 'rgba(56, 189, 248, 0.8)',
          },
        },
      },
    ],
  };

  return (
    <WidgetWrapper
      title="RISK HEATMAP"
      subtitle={`Dynamic multi-metric repository matrix (${totalItems} Repositories)`}
      isLoading={isLoading}
      isError={isError}
      onRetry={refetch}
    >
      <div className="space-y-4 py-2 font-mono text-xs w-full">
        {/* Controls Toolbar: Search, Filter, Sort, Export */}
        <div className="flex flex-wrap items-center justify-between gap-2 bg-white/[0.02] border border-white/[0.06] p-2.5 rounded-xl">
          <div className="flex items-center gap-2 flex-wrap flex-1">
            {/* Search */}
            <div className="relative flex-1 min-w-[160px]">
              <Search size={13} className="absolute left-2.5 top-2.5 text-slate-500" />
              <input
                type="text"
                placeholder="Search repository..."
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value);
                  setPage(1);
                }}
                className="w-full bg-cyber-950/80 border border-slate-800 rounded-lg pl-8 pr-3 py-1.5 text-[10px] text-slate-200 placeholder-slate-500 focus:outline-none focus:border-cyan-500/50"
              />
            </div>

            {/* Risk Filter */}
            <div className="flex items-center gap-1">
              <Filter size={13} className="text-slate-500" />
              <select
                value={riskLevel}
                onChange={(e) => {
                  setRiskLevel(e.target.value);
                  setPage(1);
                }}
                className="bg-cyber-950/80 border border-slate-800 rounded-lg px-2 py-1.5 text-[10px] text-slate-200 focus:outline-none focus:border-cyan-500/50"
              >
                <option value="">All Risk Levels</option>
                <option value="CRITICAL">Critical</option>
                <option value="HIGH">High Risk</option>
                <option value="MEDIUM">Medium</option>
                <option value="LOW">Low</option>
              </select>
            </div>

            {/* Sort By */}
            <div className="flex items-center gap-1">
              <ArrowUpDown size={13} className="text-slate-500" />
              <select
                value={sortBy}
                onChange={(e) => setSortBy(e.target.value)}
                className="bg-cyber-950/80 border border-slate-800 rounded-lg px-2 py-1.5 text-[10px] text-slate-200 focus:outline-none focus:border-cyan-500/50"
              >
                <option value="name">Sort by Name</option>
                <option value="riskScore">Sort by Risk Score</option>
                <option value="health_score">Sort by Health Score</option>
              </select>
              <button
                onClick={() => setSortDesc(!sortDesc)}
                className={`p-1.5 border rounded-lg text-[10px] ${sortDesc ? 'bg-cyan-500/20 text-cyan-400 border-cyan-500/40' : 'bg-slate-800 text-slate-400 border-slate-700'}`}
                title="Toggle sort direction"
              >
                {sortDesc ? 'DESC' : 'ASC'}
              </button>
            </div>
          </div>

          {/* Export CSV Button */}
          <button
            onClick={handleExportCSV}
            className="flex items-center gap-1.5 px-3 py-1.5 bg-cyan-500/15 border border-cyan-500/30 rounded-lg text-cyan-400 hover:bg-cyan-500/25 hover:border-cyan-500/60 text-[10px] font-bold cursor-pointer transition-all shadow-[0_0_10px_rgba(6,182,212,0.1)]"
            title="Export heatmap metric data to CSV file"
          >
            <Download size={13} />
            <span>EXPORT CSV</span>
          </button>
        </div>

        {/* Heatmap EChart container */}
        {yData.length === 0 && !isLoading ? (
          <div className="h-48 flex items-center justify-center border border-dashed border-slate-800 rounded-xl text-slate-500 text-[11px]">
            No repository risk data found for the current search/filter.
          </div>
        ) : (
          <div className="w-full" style={{ height: `${Math.max(220, yData.length * 36 + 60)}px` }}>
            <ReactECharts option={option} style={{ height: '100%', width: '100%' }} />
          </div>
        )}

        {/* Pagination Bar */}
        <div className="flex items-center justify-between px-2 pt-1 text-[10px] text-slate-400 border-t border-slate-900">
          <span>Showing page {page} of {totalPages} ({totalItems} repositories)</span>
          <div className="flex items-center gap-1">
            <button
              disabled={page <= 1}
              onClick={() => setPage((p) => Math.max(1, p - 1))}
              className="p-1 rounded bg-slate-900 border border-slate-800 disabled:opacity-30 disabled:cursor-not-allowed hover:bg-slate-800"
            >
              <ChevronLeft size={14} />
            </button>
            <span className="px-2 font-bold text-white">{page}</span>
            <button
              disabled={page >= totalPages}
              onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
              className="p-1 rounded bg-slate-900 border border-slate-800 disabled:opacity-30 disabled:cursor-not-allowed hover:bg-slate-800"
            >
              <ChevronRight size={14} />
            </button>
          </div>
        </div>
      </div>
    </WidgetWrapper>
  );
};

export default RiskHeatmapWidget;

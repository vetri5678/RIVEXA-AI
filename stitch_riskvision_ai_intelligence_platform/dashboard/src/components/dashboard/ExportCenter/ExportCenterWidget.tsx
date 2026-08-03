import React, { useState } from 'react';
import WidgetWrapper from '../Common/WidgetWrapper';
import dashboardApi from '../../../api/dashboard';
import { apiClient } from '../../../api/client';
import { FileDown, FileSpreadsheet, FileJson } from 'lucide-react';

export const ExportCenterWidget: React.FC = () => {
  const [exporting, setExporting] = useState(false);

  const handleExport = async (format: 'pdf' | 'excel' | 'csv' | 'json') => {
    setExporting(true);
    try {
      if (format === 'pdf' || format === 'excel') {
        const response = await apiClient.get(`/reports/download/${format}`, {
          responseType: 'blob',
        });
        const url = window.URL.createObjectURL(response.data);
        const link = document.createElement('a');
        link.href = url;
        link.setAttribute('download', `riskvision_portfolio_report.${format === 'pdf' ? 'pdf' : 'xlsx'}`);
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(url);
      } else {
        const res = await dashboardApi.exportReport({
          format,
          report_type: 'full',
        });
        alert(`Report created: ${res.file_name} (${(res.size_bytes / 1024).toFixed(1)} KB)`);
      }
    } catch (err: any) {
      console.error('[ExportCenterWidget] Export failed:', err);
      const errMsg = err.response?.data?.message || err.message || 'An unexpected error occurred';
      alert(`Export failed: ${errMsg}`);
    } finally {
      setExporting(false);
    }
  };

  return (
    <WidgetWrapper
      title="REPORT & TELEMETRY EXPORT"
      subtitle="Generate secure reports of failure predictions"
      isLoading={false}
      isError={false}
    >
      <div className="grid grid-cols-2 gap-3 py-1">
        <button
          disabled={exporting}
          onClick={() => handleExport('pdf')}
          className="flex items-center gap-2 justify-center p-3 border border-slate-800 hover:border-neon-pink/40 bg-cyber-950/40 hover:bg-neon-pink/5 rounded-lg text-slate-300 hover:text-neon-pink transition-all duration-300 font-mono text-[10px] font-bold"
        >
          <FileDown size={14} />
          EXPORT PDF
        </button>

        <button
          disabled={exporting}
          onClick={() => handleExport('excel')}
          className="flex items-center gap-2 justify-center p-3 border border-slate-800 hover:border-neon-green/40 bg-cyber-950/40 hover:bg-neon-green/5 rounded-lg text-slate-300 hover:text-neon-green transition-all duration-300 font-mono text-[10px] font-bold"
        >
          <FileSpreadsheet size={14} />
          EXPORT EXCEL
        </button>

        <button
          disabled={exporting}
          onClick={() => handleExport('csv')}
          className="flex items-center gap-2 justify-center p-3 border border-slate-800 hover:border-neon-yellow/40 bg-cyber-950/40 hover:bg-neon-yellow/5 rounded-lg text-slate-300 hover:text-neon-yellow transition-all duration-300 font-mono text-[10px] font-bold"
        >
          <FileDown size={14} />
          EXPORT CSV
        </button>

        <button
          disabled={exporting}
          onClick={() => handleExport('json')}
          className="flex items-center gap-2 justify-center p-3 border border-slate-800 hover:border-neon-blue/40 bg-cyber-950/40 hover:bg-neon-blue/5 rounded-lg text-slate-300 hover:text-neon-blue transition-all duration-300 font-mono text-[10px] font-bold"
        >
          <FileJson size={14} />
          EXPORT JSON
        </button>
      </div>
    </WidgetWrapper>
  );
};
export default ExportCenterWidget;

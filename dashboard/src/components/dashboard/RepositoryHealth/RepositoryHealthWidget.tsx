import React, { useState } from 'react';
import { useRepositoryRanking } from '../../../hooks/useDashboard';
import WidgetWrapper from '../Common/WidgetWrapper';
import Badge from '../../common/Badge';
import { ChevronLeft, ChevronRight, ArrowUpDown } from 'lucide-react';

interface RepositoryHealthWidgetProps {
  searchTerm: string;
  onSelectProject: (projectId: string) => void;
}

export const RepositoryHealthWidget: React.FC<RepositoryHealthWidgetProps> = ({
  searchTerm,
  onSelectProject,
}) => {
  const [page, setPage] = useState(1);
  const [sortBy, setSortBy] = useState('failure_probability');
  const [sortDesc, setSortDesc] = useState(true);

  const { data: ranking, isLoading, isError, refetch } = useRepositoryRanking({
    page,
    page_size: 5,
    search: searchTerm,
    sort_by: sortBy,
    sort_desc: sortDesc,
  });

  const handleSort = (field: string) => {
    if (sortBy === field) {
      setSortDesc(!sortDesc);
    } else {
      setSortBy(field);
      setSortDesc(true);
    }
    setPage(1);
  };

  return (
    <WidgetWrapper
      title="REPOSITORY RISK OBSERVATORY"
      subtitle="Fail probability / Model confidence / Trend tracking registry"
      isLoading={isLoading}
      isError={isError}
      onRetry={refetch}
    >
      <div className="overflow-x-auto min-h-[220px] pt-1">
        <table className="w-full text-left border-collapse font-mono text-[10px]">
          <thead>
            <tr className="border-b border-slate-800 text-slate-500 uppercase">
              <th
                onClick={() => handleSort('name')}
                className="pb-2 cursor-pointer hover:text-slate-300 transition-colors"
              >
                <div className="flex items-center gap-1">
                  Repository <ArrowUpDown size={8} />
                </div>
              </th>
              <th
                onClick={() => handleSort('failure_probability')}
                className="pb-2 cursor-pointer hover:text-slate-300 transition-colors"
              >
                <div className="flex items-center gap-1">
                  Fail Prob <ArrowUpDown size={8} />
                </div>
              </th>
              <th className="pb-2">Health</th>
              <th className="pb-2">Trend</th>
              <th className="pb-2">Risk State</th>
              <th className="pb-2 text-right">Telemetry</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800/40">
            {ranking?.items.map((item) => (
              <tr
                key={item.id}
                className="hover:bg-cyber-850/20 transition-all duration-200"
              >
                <td className="py-2.5 font-bold text-slate-300">{item.name}</td>
                <td className="py-2.5 font-bold text-neon-pink">
                  {(item.failure_probability * 100).toFixed(1)}%
                </td>
                <td className="py-2.5">
                  <span
                    className={
                      item.health_score >= 70
                        ? 'text-neon-green font-bold'
                        : item.health_score >= 40
                        ? 'text-neon-yellow font-bold'
                        : 'text-neon-pink font-bold'
                    }
                  >
                    {item.health_score}%
                  </span>
                </td>
                <td className="py-2.5">
                  <span
                    className={
                      item.trend === 'improving'
                        ? 'text-neon-green'
                        : item.trend === 'worsening'
                        ? 'text-neon-pink'
                        : 'text-slate-500'
                    }
                  >
                    {item.trend === 'improving' ? '↑' : item.trend === 'worsening' ? '↓' : '→'}
                  </span>
                </td>
                <td className="py-2.5">
                  <Badge label={item.risk_level} />
                </td>
                <td className="py-2.5 text-right">
                  <button
                    onClick={() => onSelectProject(item.external_id)}
                    className="px-2 py-0.5 border border-neon-blue/20 hover:border-neon-blue bg-neon-blue/5 hover:bg-neon-blue/10 text-neon-blue rounded text-[9px] font-bold transition-all duration-200"
                  >
                    EXPLAIN
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {ranking && ranking.total > 0 && (
        <div className="flex items-center justify-between border-t border-slate-800/60 pt-3 mt-4 text-[9px] text-slate-500 font-mono">
          <span>
            PAGE {page} OF {Math.ceil(ranking.total / 5)} ({ranking.total} REGISTERS)
          </span>

          <div className="flex items-center gap-1.5">
            <button
              disabled={page === 1}
              onClick={() => setPage(page - 1)}
              className="p-1 border border-slate-800 rounded text-slate-400 hover:text-slate-200 disabled:opacity-30 disabled:pointer-events-none transition-colors"
            >
              <ChevronLeft size={12} />
            </button>
            <button
              disabled={page >= Math.ceil(ranking.total / 5)}
              onClick={() => setPage(page + 1)}
              className="p-1 border border-slate-800 rounded text-slate-400 hover:text-slate-200 disabled:opacity-30 disabled:pointer-events-none transition-colors"
            >
              <ChevronRight size={12} />
            </button>
          </div>
        </div>
      )}
    </WidgetWrapper>
  );
};
export default RepositoryHealthWidget;

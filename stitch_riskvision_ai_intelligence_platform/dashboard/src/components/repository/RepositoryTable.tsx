import React from 'react';
import {
  ChevronUp, ChevronDown, ChevronsUpDown, ExternalLink,
  GitBranch, Loader2, ChevronLeft, ChevronRight,
} from 'lucide-react';
import type { RepositorySummary, RepositoryFilters } from '../../types/repository';
import RepositoryActionsMenu from './RepositoryActionsMenu';
import Badge from '../common/Badge';

interface Props {
  data: RepositorySummary[];
  totalElements: number;
  totalPages: number;
  isLoading: boolean;
  filters: RepositoryFilters;
  selectedIds: string[];
  onFiltersChange: (f: Partial<RepositoryFilters>) => void;
  onRowClick: (id: string) => void;
  onSelectionChange: (ids: string[]) => void;
  onAction: (action: string, id: string) => void;
}

type SortDir = 'asc' | 'desc';

const riskColor: Record<string, string> = {
  LOW: 'text-neon-green',
  MEDIUM: 'text-neon-yellow',
  HIGH: 'text-neon-orange',
  CRITICAL: 'text-neon-pink',
};

const healthBar = (score: number) => {
  const pct = Math.min(100, Math.max(0, score));
  const color = pct >= 70 ? 'bg-neon-green' : pct >= 40 ? 'bg-neon-yellow' : 'bg-neon-pink';
  return (
    <div className="flex items-center gap-2">
      <div className="flex-1 h-1 bg-cyber-800 rounded-full overflow-hidden">
        <div className={`h-full rounded-full transition-all ${color}`} style={{ width: `${pct}%` }} />
      </div>
      <span className={`text-xs font-mono font-bold ${pct >= 70 ? 'text-neon-green' : pct >= 40 ? 'text-neon-yellow' : 'text-neon-pink'}`}>
        {pct.toFixed(0)}
      </span>
    </div>
  );
};

const gitProviderIcon: Record<string, string> = {
  GITHUB: '⬡',
  GITLAB: '◈',
  BITBUCKET: '◆',
  AZURE_DEVOPS: '▲',
  OTHER: '◉',
};

const getCleanRepoName = (repo: any): string => {
  if (!repo) return 'Repository';
  let name = repo.repositoryName || repo.name || repo.fullName || repo.full_name;
  if (name && name !== '(Unnamed)' && name !== 'Unnamed Repository' && name.trim() !== '') {
    return name;
  }
  const url = repo.repositoryUrl || repo.html_url || repo.url || '';
  if (url) {
    const cleanUrl = url.trim().replace(/\/+$/, '').replace(/\.git$/, '');
    const parts = cleanUrl.split('/');
    if (parts.length >= 2) {
      const owner = parts[parts.length - 2];
      const r = parts[parts.length - 1];
      if (owner && r && !owner.includes(':') && !owner.includes('.')) {
        return `${owner}/${r}`;
      }
      if (r) return r;
    } else if (parts.length === 1 && parts[0]) {
      return parts[0];
    }
  }
  return repo.id ? `Repository-${String(repo.id).substring(0, 8)}` : 'Repository';
};

const SortIcon: React.FC<{ col: string; active: string; dir: SortDir }> = ({ col, active, dir }) => {
  if (active !== col) return <ChevronsUpDown size={12} className="text-slate-600" />;
  return dir === 'asc' ? <ChevronUp size={12} className="text-neon-blue" /> : <ChevronDown size={12} className="text-neon-blue" />;
};

const SkeletonRow: React.FC = () => (
  <tr className="border-b border-cyber-800/50">
    {Array.from({ length: 12 }).map((_, i) => (
      <td key={i} className="px-3 py-3">
        <div className="h-3 bg-cyber-800 rounded animate-pulse" style={{ width: `${40 + Math.random() * 40}%` }} />
      </td>
    ))}
  </tr>
);

export const RepositoryTable: React.FC<Props> = ({
  data,
  totalElements,
  totalPages,
  isLoading,
  filters,
  selectedIds,
  onFiltersChange,
  onRowClick,
  onSelectionChange,
  onAction,
}) => {
  const allSelected = data.length > 0 && data.every(r => selectedIds.includes(r.id));
  const someSelected = data.some(r => selectedIds.includes(r.id));

  const toggleAll = () => {
    if (allSelected) onSelectionChange([]);
    else onSelectionChange(data.map(r => r.id));
  };

  const toggleRow = (id: string) => {
    if (selectedIds.includes(id)) onSelectionChange(selectedIds.filter(x => x !== id));
    else onSelectionChange([...selectedIds, id]);
  };

  const handleSort = (col: string) => {
    if (filters.sortBy === col) {
      onFiltersChange({ sortDir: filters.sortDir === 'asc' ? 'desc' : 'asc', page: 0 });
    } else {
      onFiltersChange({ sortBy: col, sortDir: 'desc', page: 0 });
    }
  };

  const th = (label: string, col?: string, minW?: string) => (
    <th
      className={`px-3 py-3 text-left text-[10px] font-mono font-bold uppercase tracking-widest text-slate-500 whitespace-nowrap select-none ${col ? 'cursor-pointer hover:text-neon-blue transition-colors' : ''} ${minW ?? ''}`}
      onClick={col ? () => handleSort(col) : undefined}
    >
      <div className="flex items-center gap-1">
        {label}
        {col && <SortIcon col={col} active={filters.sortBy} dir={filters.sortDir as SortDir} />}
      </div>
    </th>
  );

  const formatDate = (d: string | null) => {
    if (!d) return <span className="text-slate-600">—</span>;
    const dt = new Date(d);
    const now = new Date();
    const diffDays = Math.floor((now.getTime() - dt.getTime()) / 86400000);
    if (diffDays === 0) return <span className="text-neon-green text-xs">Today</span>;
    if (diffDays === 1) return <span className="text-neon-yellow text-xs">Yesterday</span>;
    if (diffDays < 7) return <span className="text-slate-300 text-xs">{diffDays}d ago</span>;
    if (diffDays < 30) return <span className="text-slate-400 text-xs">{Math.floor(diffDays / 7)}w ago</span>;
    return <span className="text-slate-500 text-xs">{Math.floor(diffDays / 30)}mo ago</span>;
  };

  const predictionBadge = (status: string) => {
    const map: Record<string, 'success' | 'info' | 'warning' | 'error' | 'critical'> = {
      COMPLETED: 'success', RUNNING: 'info', PENDING: 'warning', FAILED: 'error', DEAD: 'critical',
    };
    return <Badge label={status} variant={map[status] ?? 'info'} />;
  };

  return (
    <div className="glass-panel overflow-hidden">
      {/* Table header row count */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-glass-border">
        <div className="flex items-center gap-3">
          <GitBranch size={16} className="text-neon-blue" />
          <span className="text-sm font-mono font-bold text-slate-200">
            {isLoading ? 'Loading…' : `${totalElements.toLocaleString()} Repositories`}
          </span>
          {selectedIds.length > 0 && (
            <span className="px-2 py-0.5 bg-neon-blue/10 border border-neon-blue/20 rounded text-xs font-mono text-neon-blue">
              {selectedIds.length} selected
            </span>
          )}
        </div>
        {isLoading && <Loader2 size={14} className="animate-spin text-neon-blue" />}
      </div>

      <div className="overflow-x-auto">
        <table className="w-full text-sm font-mono min-w-[1400px]">
          <thead className="bg-cyber-950/60 sticky top-0 z-10">
            <tr className="border-b border-glass-border">
              <th className="px-3 py-3 w-10">
                <input
                  type="checkbox"
                  checked={allSelected}
                  ref={el => { if (el) el.indeterminate = someSelected && !allSelected; }}
                  onChange={toggleAll}
                  className="w-3.5 h-3.5 accent-neon-blue cursor-pointer"
                />
              </th>
              {th('Repository', 'repositoryName')}
              {th('Organization', 'organization')}
              {th('Tech Stack')}
              {th('Provider')}
              {th('Branch')}
              {th('Status', 'status')}
              {th('Health', 'healthScore', 'min-w-[120px]')}
              {th('Failure %', 'failureProbability')}
              {th('Prediction', 'predictionStatus')}
              {th('Issues', 'openIssues')}
              {th('Last Commit', 'lastCommitDate')}
              {th('Created', 'createdAt')}
              <th className="px-3 py-3 text-[10px] font-mono font-bold uppercase tracking-widest text-slate-500 text-right">
                Actions
              </th>
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              Array.from({ length: 8 }).map((_, i) => <SkeletonRow key={i} />)
            ) : data.length === 0 ? (
              <tr>
                <td colSpan={14} className="py-20 text-center">
                  <div className="flex flex-col items-center gap-3">
                    <GitBranch size={40} className="text-slate-700" />
                    <p className="text-slate-500 font-mono text-sm">No repositories found</p>
                    <p className="text-slate-600 text-xs">Try adjusting your search or filters</p>
                  </div>
                </td>
              </tr>
            ) : (
              data.map((repo) => (
                <tr
                  key={repo.id}
                  className={`border-b border-cyber-800/30 transition-all duration-150 cursor-pointer
                    ${selectedIds.includes(repo.id) ? 'bg-neon-blue/5' : 'hover:bg-cyber-800/20'}`}
                >
                  <td className="px-3 py-3" onClick={e => e.stopPropagation()}>
                    <input
                      type="checkbox"
                      checked={selectedIds.includes(repo.id)}
                      onChange={() => toggleRow(repo.id)}
                      className="w-3.5 h-3.5 accent-neon-blue cursor-pointer"
                    />
                  </td>

                  <td className="px-3 py-3" onClick={() => onRowClick(repo.id)}>
                    <div className="flex flex-col gap-0.5">
                      <span className="text-slate-100 font-bold text-xs truncate max-w-[180px]">
                        {getCleanRepoName(repo)}
                      </span>
                      {repo.language && (
                        <span className="text-[10px] text-slate-500">{repo.language}</span>
                      )}
                    </div>
                  </td>

                  <td className="px-3 py-3" onClick={() => onRowClick(repo.id)}>
                    <span className="text-slate-300 text-xs truncate max-w-[120px] block">
                      {repo.organization ?? '—'}
                    </span>
                  </td>

                  <td className="px-3 py-3" onClick={() => onRowClick(repo.id)}>
                    <span className="text-slate-400 text-xs truncate max-w-[120px] block">
                      {repo.technology ?? '—'}
                    </span>
                  </td>

                  <td className="px-3 py-3" onClick={() => onRowClick(repo.id)}>
                    <div className="flex items-center gap-1.5">
                      <span className="text-neon-blue text-sm">{gitProviderIcon[repo.gitProvider] ?? '◉'}</span>
                      <span className="text-slate-400 text-[10px]">{repo.gitProvider}</span>
                    </div>
                  </td>

                  <td className="px-3 py-3" onClick={() => onRowClick(repo.id)}>
                    <code className="text-[10px] text-neon-purple bg-neon-purple/10 px-1.5 py-0.5 rounded">
                      {repo.branch}
                    </code>
                  </td>

                  <td className="px-3 py-3" onClick={() => onRowClick(repo.id)}>
                    <Badge
                      label={repo.status}
                      variant={
                        repo.status === 'ACTIVE' ? 'success' :
                        repo.status === 'ARCHIVED' ? 'info' :
                        repo.status === 'INACTIVE' ? 'warning' : 'error'
                      }
                    />
                  </td>

                  <td className="px-3 py-3" onClick={() => onRowClick(repo.id)}>
                    {healthBar(repo.healthScore)}
                  </td>

                  <td className="px-3 py-3" onClick={() => onRowClick(repo.id)}>
                    <span className={`font-bold text-xs ${riskColor[repo.riskLevel] ?? 'text-slate-400'}`}>
                      {(repo.failureProbability * 100).toFixed(1)}%
                    </span>
                  </td>

                  <td className="px-3 py-3" onClick={() => onRowClick(repo.id)}>
                    {predictionBadge(repo.predictionStatus)}
                  </td>

                  <td className="px-3 py-3 text-center" onClick={() => onRowClick(repo.id)}>
                    <span className={`text-xs font-mono ${repo.openIssues > 20 ? 'text-neon-pink' : repo.openIssues > 10 ? 'text-neon-yellow' : 'text-slate-400'}`}>
                      {repo.openIssues}
                    </span>
                  </td>

                  <td className="px-3 py-3" onClick={() => onRowClick(repo.id)}>
                    {formatDate(repo.lastCommitDate)}
                  </td>

                  <td className="px-3 py-3" onClick={() => onRowClick(repo.id)}>
                    {formatDate(repo.createdAt)}
                  </td>

                  <td className="px-3 py-3 text-right" onClick={e => e.stopPropagation()}>
                    <div className="flex items-center justify-end gap-1">
                      <a
                        href={repo.repositoryUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="p-1.5 rounded text-slate-500 hover:text-neon-blue transition-colors"
                        title="Open repository"
                      >
                        <ExternalLink size={13} />
                      </a>
                      <RepositoryActionsMenu
                        repo={repo}
                        onAction={(action) => onAction(action, repo.id)}
                      />
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      {!isLoading && totalPages > 1 && (
        <div className="flex flex-col sm:flex-row items-center justify-between gap-3 px-4 py-3 border-t border-glass-border">
          <span className="text-xs text-slate-500 font-mono">
            Page {filters.page + 1} of {totalPages} · {totalElements.toLocaleString()} total
          </span>
          <div className="flex items-center gap-1">
            <button
              onClick={() => onFiltersChange({ page: 0 })}
              disabled={filters.page === 0}
              className="px-2 py-1 text-xs font-mono text-slate-400 hover:text-neon-blue disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
            >
              «
            </button>
            <button
              onClick={() => onFiltersChange({ page: filters.page - 1 })}
              disabled={filters.page === 0}
              className="p-1.5 rounded hover:bg-cyber-800 text-slate-400 hover:text-neon-blue disabled:opacity-30 disabled:cursor-not-allowed transition-all"
            >
              <ChevronLeft size={14} />
            </button>
            {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
              const pg = Math.max(0, Math.min(totalPages - 5, filters.page - 2)) + i;
              return (
                <button
                  key={pg}
                  onClick={() => onFiltersChange({ page: pg })}
                  className={`w-7 h-7 rounded text-xs font-mono transition-all ${
                    pg === filters.page
                      ? 'bg-neon-blue/10 text-neon-blue border border-neon-blue/30'
                      : 'text-slate-400 hover:text-slate-200 hover:bg-cyber-800'
                  }`}
                >
                  {pg + 1}
                </button>
              );
            })}
            <button
              onClick={() => onFiltersChange({ page: filters.page + 1 })}
              disabled={filters.page >= totalPages - 1}
              className="p-1.5 rounded hover:bg-cyber-800 text-slate-400 hover:text-neon-blue disabled:opacity-30 disabled:cursor-not-allowed transition-all"
            >
              <ChevronRight size={14} />
            </button>
            <button
              onClick={() => onFiltersChange({ page: totalPages - 1 })}
              disabled={filters.page >= totalPages - 1}
              className="px-2 py-1 text-xs font-mono text-slate-400 hover:text-neon-blue disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
            >
              »
            </button>
          </div>
          <select
            value={filters.size}
            onChange={e => onFiltersChange({ size: Number(e.target.value), page: 0 })}
            className="glass-input text-xs py-1 px-2 w-20"
          >
            {[10, 20, 50, 100].map(s => <option key={s} value={s}>{s} / pg</option>)}
          </select>
        </div>
      )}
    </div>
  );
};

export default RepositoryTable;

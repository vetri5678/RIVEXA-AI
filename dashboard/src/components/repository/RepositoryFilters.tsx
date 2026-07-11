import React from 'react';
import { Filter, X, ChevronDown } from 'lucide-react';
import type { RepositoryFilters } from '../../types/repository';

interface Props {
  filters: RepositoryFilters;
  onChange: (f: Partial<RepositoryFilters>) => void;
  onReset: () => void;
}

const SelectFilter: React.FC<{
  label: string;
  value: string;
  options: { value: string; label: string }[];
  onChange: (v: string) => void;
}> = ({ label, value, options, onChange }) => (
  <div className="relative">
    <select
      value={value}
      onChange={e => onChange(e.target.value)}
      className="glass-input appearance-none pr-7 pl-3 py-1.5 text-xs w-full"
    >
      <option value="">{label}</option>
      {options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
    </select>
    <ChevronDown size={12} className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-500 pointer-events-none" />
  </div>
);

const activeFilterCount = (f: RepositoryFilters) =>
  [f.status, f.riskLevel, f.predictionStatus, f.gitProvider, f.language, f.organization]
    .filter(Boolean).length;

export const RepositoryFiltersComponent: React.FC<Props> = ({ filters, onChange, onReset }) => {
  const count = activeFilterCount(filters);

  return (
    <div className="glass-panel px-4 py-3 mb-4">
      <div className="flex flex-wrap items-center gap-3">
        {/* Header */}
        <div className="flex items-center gap-2 mr-1">
          <Filter size={14} className="text-neon-blue" />
          <span className="text-xs font-mono font-bold text-slate-300">Filters</span>
          {count > 0 && (
            <span className="px-1.5 py-0.5 bg-neon-blue/10 border border-neon-blue/20 rounded text-[10px] font-mono text-neon-blue">
              {count}
            </span>
          )}
        </div>

        {/* Search */}
        <input
          type="text"
          placeholder="Search name, org, description…"
          value={filters.search}
          onChange={e => onChange({ search: e.target.value, page: 0 })}
          className="glass-input text-xs py-1.5 px-3 min-w-[220px] flex-1 max-w-xs"
        />

        {/* Status filter */}
        <SelectFilter
          label="All Statuses"
          value={filters.status}
          options={[
            { value: 'ACTIVE', label: 'Active' },
            { value: 'ARCHIVED', label: 'Archived' },
            { value: 'INACTIVE', label: 'Inactive' },
            { value: 'DEPRECATED', label: 'Deprecated' },
          ]}
          onChange={v => onChange({ status: v, page: 0 })}
        />

        {/* Risk Level */}
        <SelectFilter
          label="All Risk Levels"
          value={filters.riskLevel}
          options={[
            { value: 'LOW', label: '🟢 Low Risk' },
            { value: 'MEDIUM', label: '🟡 Medium Risk' },
            { value: 'HIGH', label: '🟠 High Risk' },
            { value: 'CRITICAL', label: '🔴 Critical' },
          ]}
          onChange={v => onChange({ riskLevel: v, page: 0 })}
        />

        {/* Prediction Status */}
        <SelectFilter
          label="Prediction Status"
          value={filters.predictionStatus}
          options={[
            { value: 'PENDING', label: 'Pending' },
            { value: 'RUNNING', label: 'Running' },
            { value: 'COMPLETED', label: 'Completed' },
            { value: 'FAILED', label: 'Failed' },
            { value: 'DEAD', label: 'Predicted Dead' },
          ]}
          onChange={v => onChange({ predictionStatus: v, page: 0 })}
        />

        {/* Git Provider */}
        <SelectFilter
          label="Git Provider"
          value={filters.gitProvider}
          options={[
            { value: 'GITHUB', label: '⬡ GitHub' },
            { value: 'GITLAB', label: '◈ GitLab' },
            { value: 'BITBUCKET', label: '◆ Bitbucket' },
            { value: 'AZURE_DEVOPS', label: '▲ Azure DevOps' },
            { value: 'OTHER', label: '◉ Other' },
          ]}
          onChange={v => onChange({ gitProvider: v, page: 0 })}
        />

        {/* Language */}
        <SelectFilter
          label="Language"
          value={filters.language}
          options={[
            { value: 'Java', label: 'Java' },
            { value: 'TypeScript', label: 'TypeScript' },
            { value: 'Python', label: 'Python' },
            { value: 'Go', label: 'Go' },
            { value: 'Rust', label: 'Rust' },
            { value: 'C#', label: 'C#' },
            { value: 'JavaScript', label: 'JavaScript' },
            { value: 'Kotlin', label: 'Kotlin' },
            { value: 'Swift', label: 'Swift' },
          ]}
          onChange={v => onChange({ language: v, page: 0 })}
        />

        {/* Reset button */}
        {count > 0 && (
          <button
            onClick={onReset}
            className="flex items-center gap-1.5 text-xs font-mono text-slate-400 hover:text-neon-pink transition-colors ml-auto"
          >
            <X size={12} />
            Clear All
          </button>
        )}
      </div>
    </div>
  );
};

export default RepositoryFiltersComponent;

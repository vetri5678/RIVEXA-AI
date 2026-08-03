import React, { useRef } from 'react';
import { MoreVertical, Eye, Edit, Trash2, Archive, RotateCcw, Copy, RefreshCw, Brain, BarChart2, Clock, FileText } from 'lucide-react';
import type { RepositorySummary } from '../../types/repository';

interface Props {
  repo: RepositorySummary;
  onAction: (action: string) => void;
}

interface ActionItem {
  label: string;
  icon: React.ElementType;
  action: string;
  color?: string;
  divider?: boolean;
  hidden?: boolean;
}

export const RepositoryActionsMenu: React.FC<Props> = ({ repo, onAction }) => {
  const [open, setOpen] = React.useState(false);
  const ref = useRef<HTMLDivElement>(null);

  // Close on outside click
  React.useEffect(() => {
    if (!open) return;
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [open]);

  const actions: ActionItem[] = [
    { label: 'View Details', icon: Eye, action: 'view' },
    { label: 'Edit', icon: Edit, action: 'edit' },
    { label: 'Duplicate', icon: Copy, action: 'duplicate', divider: true },
    { label: 'Sync Repository', icon: RefreshCw, action: 'sync' },
    { label: 'Run AI Prediction', icon: Brain, action: 'predict' },
    { label: 'View Metrics', icon: BarChart2, action: 'metrics', divider: true },
    { label: 'Prediction History', icon: Clock, action: 'history' },
    { label: 'Generate Report', icon: FileText, action: 'report', divider: true },
    {
      label: repo.status === 'ARCHIVED' ? 'Restore' : 'Archive',
      icon: repo.status === 'ARCHIVED' ? RotateCcw : Archive,
      action: repo.status === 'ARCHIVED' ? 'restore' : 'archive',
      color: 'text-neon-yellow',
    },
    {
      label: 'Delete',
      icon: Trash2,
      action: 'delete',
      color: 'text-neon-pink',
    },
  ];

  const handleAction = (action: string) => {
    setOpen(false);
    onAction(action);
  };

  return (
    <div ref={ref} className="relative">
      <button
        onClick={(e) => { e.stopPropagation(); setOpen(!open); }}
        className="p-1.5 rounded text-slate-500 hover:text-slate-200 hover:bg-cyber-800 transition-all"
        title="Actions"
      >
        <MoreVertical size={14} />
      </button>

      {open && (
        <div className="absolute right-0 top-7 z-50 min-w-[200px] bg-cyber-900 border border-glass-border rounded-xl shadow-2xl shadow-black/50 overflow-hidden animate-fade-in">
          {/* Repo name header */}
          <div className="px-4 py-2.5 border-b border-glass-border">
            <p className="text-[11px] font-mono font-bold text-slate-300 truncate">{repo.repositoryName}</p>
            <p className="text-[10px] text-slate-600">{repo.gitProvider}</p>
          </div>

          <div className="py-1.5">
            {actions.map((item) => {
              if (item.hidden) return null;
              const Icon = item.icon;
              return (
                <React.Fragment key={item.action}>
                  {item.divider && <div className="my-1 border-t border-cyber-800/50" />}
                  <button
                    onClick={() => handleAction(item.action)}
                    className={`w-full flex items-center gap-3 px-4 py-2 text-xs font-mono transition-all hover:bg-cyber-800/60 ${item.color ?? 'text-slate-300 hover:text-slate-100'}`}
                  >
                    <Icon size={13} />
                    {item.label}
                  </button>
                </React.Fragment>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
};

export default RepositoryActionsMenu;

import React, { useState } from 'react';
import { Plus, Download, RefreshCw, Trash2, Archive, Brain, Database } from 'lucide-react';
import DashboardLayout from '../components/layout/DashboardLayout';
import RepositorySummaryCards from '../components/repository/RepositorySummaryCards';
import RepositoryFiltersComponent from '../components/repository/RepositoryFilters';
import RepositoryTable from '../components/repository/RepositoryTable';
import RepositoryDetailsDrawer from '../components/repository/RepositoryDetailsDrawer';
import AddRepositoryWizard from '../components/repository/AddRepositoryWizard';
import RepositoryAnalyticsCharts from '../components/repository/RepositoryAnalyticsCharts';

import {
  useRepositories,
  useRepositoryStatistics,
  useSyncRepository,
  usePredictRepository,
  useArchiveRepository,
  useRestoreRepository,
  useDeleteRepository,
  useDuplicateRepository,
  useExportRepositories,
} from '../hooks/useRepository';

import type { RepositoryFilters } from '../types/repository';

export const Repositories: React.FC = () => {
  // Page search & filter states
  const [filters, setFilters] = useState<RepositoryFilters>({
    search: '',
    status: 'ACTIVE',
    riskLevel: '',
    predictionStatus: '',
    gitProvider: '',
    language: '',
    organization: '',
    page: 0,
    size: 20,
    sortBy: 'createdAt',
    sortDir: 'desc',
  });

  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [activeDetailsId, setActiveDetailsId] = useState<string | null>(null);
  const [isWizardOpen, setIsWizardOpen] = useState(false);

  // Queries
  const { data: listData, isLoading: listLoading, refetch: refetchList } = useRepositories(filters);
  const { data: statsData, isLoading: statsLoading, refetch: refetchStats } = useRepositoryStatistics();

  // Mutations
  const syncMutation = useSyncRepository();
  const predictMutation = usePredictRepository();
  const archiveMutation = useArchiveRepository();
  const restoreMutation = useRestoreRepository();
  const deleteMutation = useDeleteRepository();
  const duplicateMutation = useDuplicateRepository();
  const exportMutation = useExportRepositories();

  const handleFiltersChange = (newFilters: Partial<RepositoryFilters>) => {
    setFilters(prev => ({ ...prev, ...newFilters }));
  };

  const handleResetFilters = () => {
    setFilters({
      search: '',
      status: '',
      riskLevel: '',
      predictionStatus: '',
      gitProvider: '',
      language: '',
      organization: '',
      page: 0,
      size: 20,
      sortBy: 'createdAt',
      sortDir: 'desc',
    });
  };

  const handleAction = async (action: string, id: string) => {
    try {
      if (action === 'view' || action === 'metrics' || action === 'history') {
        setActiveDetailsId(id);
      } else if (action === 'sync') {
        await syncMutation.mutateAsync(id);
        alert('Repository synchronization run scheduled successfully.');
        refetchList();
        refetchStats();
      } else if (action === 'predict') {
        const res = await predictMutation.mutateAsync(id);
        alert(`AI prediction completed. Failure probability: ${(res.failureProbability * 100).toFixed(1)}%, Risk: ${res.riskLevel}`);
        refetchList();
        refetchStats();
      } else if (action === 'archive') {
        if (confirm('Are you sure you want to archive this repository? It will no longer receive automated daily predictions.')) {
          await archiveMutation.mutateAsync(id);
          refetchList();
          refetchStats();
        }
      } else if (action === 'restore') {
        await restoreMutation.mutateAsync(id);
        refetchList();
        refetchStats();
      } else if (action === 'duplicate') {
        await duplicateMutation.mutateAsync(id);
        refetchList();
        refetchStats();
      } else if (action === 'delete') {
        if (confirm('CRITICAL WARNING: This will permanently delete the repository and all of its prediction history and telemetry logs. This cannot be undone. Proceed?')) {
          await deleteMutation.mutateAsync(id);
          setSelectedIds(prev => prev.filter(x => x !== id));
          if (activeDetailsId === id) setActiveDetailsId(null);
          refetchList();
          refetchStats();
        }
      } else if (action === 'report') {
        alert('Compiling comprehensive project lifecycle and prediction telemetry into PDF...');
      }
    } catch (e: any) {
      alert(e.response?.data?.error || `Action ${action} failed.`);
    }
  };

  // Bulk Operations
  const handleBulkAction = async (action: string) => {
    if (selectedIds.length === 0) return;
    if (!confirm(`Are you sure you want to run bulk ${action} on ${selectedIds.length} repositories?`)) return;

    try {
      for (const id of selectedIds) {
        if (action === 'sync') await syncMutation.mutateAsync(id);
        else if (action === 'predict') await predictMutation.mutateAsync(id);
        else if (action === 'archive') await archiveMutation.mutateAsync(id);
        else if (action === 'delete') await deleteMutation.mutateAsync(id);
      }
      alert(`Bulk ${action} completed successfully.`);
      setSelectedIds([]);
      refetchList();
      refetchStats();
    } catch (e: any) {
      alert(`Bulk ${action} encountered an error: ` + (e.response?.data?.error || e.message));
    }
  };

  const handleExport = async () => {
    try {
      const res = await exportMutation.mutateAsync({ status: filters.status, riskLevel: filters.riskLevel });
      const csvContent = "data:text/csv;charset=utf-8," 
        + ["Repository Name,Organization,Git Provider,Branch,Status,Health Score,Failure Probability,Risk Level,Created Date"]
        .concat(res.content.map(r => 
          `"${r.repositoryName}","${r.organization || ''}","${r.gitProvider}","${r.branch}","${r.status}",${r.healthScore},${r.failureProbability},"${r.riskLevel}","${r.createdAt}"`
        )).join("\n");
      const encodedUri = encodeURI(csvContent);
      const link = document.createElement("a");
      link.setAttribute("href", encodedUri);
      link.setAttribute("download", `riskvision_repositories_export_${new Date().toISOString().slice(0,10)}.csv`);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    } catch (e) {
      alert('Export failed.');
    }
  };

  return (
    <DashboardLayout
      onSearchChange={(val) => handleFiltersChange({ search: val, page: 0 })}
      searchValue={filters.search}
      onQuickAction={(action) => {
        if (action === 'add-repo') setIsWizardOpen(true);
      }}
    >
      {/* Page Title & Actions Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-6 font-mono border-b border-glass-border pb-5">
        <div>
          <h1 className="text-lg font-black tracking-wider text-slate-100 uppercase glow-text-blue flex items-center gap-2.5">
            <Database size={20} className="text-neon-blue animate-pulse-slow" />
            Repository Nodes Center
          </h1>
          <p className="text-xs text-slate-500 font-bold uppercase tracking-widest mt-1">
            "Configure, synchronize, and perform ML failure assessments on Git repositories."
          </p>
        </div>

        <div className="flex flex-wrap gap-2.5">
          <button
            onClick={() => handleExport()}
            className="btn-cyber-secondary text-xs"
          >
            <Download size={13} /> Export List
          </button>
          <button
            onClick={() => setIsWizardOpen(true)}
            className="btn-cyber-primary text-xs"
          >
            <Plus size={13} /> Register Repository
          </button>
        </div>
      </div>

      {/* Summary Cards */}
      <RepositorySummaryCards stats={statsData} isLoading={statsLoading} />

      {/* Analytics Charts */}
      <div className="mb-6">
        <RepositoryAnalyticsCharts />
      </div>

      {/* Filter Bar */}
      <RepositoryFiltersComponent
        filters={filters}
        onChange={handleFiltersChange}
        onReset={handleResetFilters}
      />

      {/* Bulk Operations Toolbar */}
      {selectedIds.length > 0 && (
        <div className="flex items-center gap-3 px-4 py-3 bg-neon-blue/5 border border-neon-blue/30 rounded-xl mb-4 font-mono text-xs">
          <span className="text-slate-300 font-bold">Bulk Operations on {selectedIds.length} Nodes:</span>
          <button
            onClick={() => handleBulkAction('sync')}
            className="flex items-center gap-1.5 px-2.5 py-1 rounded bg-cyber-800 text-slate-300 hover:text-neon-blue border border-cyber-700 transition-all"
          >
            <RefreshCw size={11} /> Sync
          </button>
          <button
            onClick={() => handleBulkAction('predict')}
            className="flex items-center gap-1.5 px-2.5 py-1 rounded bg-cyber-800 text-slate-300 hover:text-neon-blue border border-cyber-700 transition-all"
          >
            <Brain size={11} /> Assess Risk
          </button>
          <button
            onClick={() => handleBulkAction('archive')}
            className="flex items-center gap-1.5 px-2.5 py-1 rounded bg-cyber-800 text-slate-300 hover:text-neon-yellow border border-cyber-700 transition-all"
          >
            <Archive size={11} /> Archive
          </button>
          <button
            onClick={() => handleBulkAction('delete')}
            className="flex items-center gap-1.5 px-2.5 py-1 rounded bg-neon-pink/10 text-neon-pink hover:bg-neon-pink/20 border border-neon-pink/30 transition-all"
          >
            <Trash2 size={11} /> Delete
          </button>
          <button
            onClick={() => setSelectedIds([])}
            className="text-slate-500 hover:text-slate-300 transition-colors ml-auto"
          >
            Clear Selection
          </button>
        </div>
      )}

      {/* Repository Main Table */}
      <RepositoryTable
        data={listData?.content ?? []}
        totalElements={listData?.totalElements ?? 0}
        totalPages={listData?.totalPages ?? 0}
        isLoading={listLoading}
        filters={filters}
        selectedIds={selectedIds}
        onFiltersChange={handleFiltersChange}
        onRowClick={(id) => setActiveDetailsId(id)}
        onSelectionChange={setSelectedIds}
        onAction={handleAction}
      />

      {/* Details Side panel Drawer */}
      <RepositoryDetailsDrawer
        repositoryId={activeDetailsId}
        onClose={() => setActiveDetailsId(null)}
        onAction={handleAction}
      />

      {/* Create Repository Wizard */}
      <AddRepositoryWizard
        isOpen={isWizardOpen}
        onClose={() => {
          setIsWizardOpen(false);
          refetchList();
          refetchStats();
        }}
      />
    </DashboardLayout>
  );
};

export default Repositories;

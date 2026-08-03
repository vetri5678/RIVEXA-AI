import React, { useState } from 'react';
import { Plus, Download, RefreshCw, Trash2, Archive, Brain, GitBranch } from 'lucide-react';
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
  useDownloadPdfReport,
  useDownloadExcelReport,
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
  const downloadPdfMutation = useDownloadPdfReport();
  const downloadExcelMutation = useDownloadExcelReport();

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
      } else if (action === 'report' || action === 'download-pdf') {
        const blob = await downloadPdfMutation.mutateAsync(id);
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.setAttribute('download', `risk_report_${id}.pdf`);
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(url);
      } else if (action === 'download-excel') {
        const blob = await downloadExcelMutation.mutateAsync(id);
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.setAttribute('download', `risk_report_${id}.xlsx`);
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(url);
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
    } catch {
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
      {/* Page Header Banner */}
      <div className="glass-strong rounded-2xl p-6 mb-8 border border-white/[0.08] flex flex-col md:flex-row md:items-center justify-between gap-4 shadow-xl">
        <div className="flex items-center gap-4">
          <div className="p-3.5 rounded-2xl bg-gradient-to-br from-blue-500/20 to-cyan-500/10 border border-blue-500/30 text-cyan-400 shrink-0 shadow-[0_0_20px_rgba(56,189,248,0.2)]">
            <GitBranch size={28} />
          </div>
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-xl font-bold tracking-tight text-white font-sans">
                Repository Intelligence Center
              </h1>
              <span className="px-2.5 py-0.5 rounded-full text-[10px] font-mono font-bold bg-blue-500/15 text-blue-400 border border-blue-500/30">
                GITHUB ENTERPRISE
              </span>
            </div>
            <p className="text-xs text-slate-400 font-sans mt-1">
              Configure, synchronize, and execute ML risk predictions across all connected Git repositories and microservices.
            </p>
          </div>
        </div>

        <div className="flex flex-wrap gap-2.5 shrink-0">
          <button
            onClick={() => handleExport()}
            className="btn-secondary text-xs flex items-center gap-1.5 rounded-xl cursor-pointer"
          >
            <Download size={14} /> Export CSV
          </button>
          <button
            onClick={() => setIsWizardOpen(true)}
            className="btn-primary text-xs flex items-center gap-1.5 rounded-xl cursor-pointer"
          >
            <Plus size={14} /> Register Repository
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
        <div className="flex items-center gap-3 px-4 py-3 bg-blue-500/10 border border-blue-500/30 rounded-xl mb-4 font-mono text-xs">
          <span className="text-slate-200 font-bold">Bulk Operations on {selectedIds.length} Nodes:</span>
          <button
            onClick={() => handleBulkAction('sync')}
            className="flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-white/[0.08] text-slate-300 hover:text-cyan-400 border border-white/[0.1] transition-all cursor-pointer"
          >
            <RefreshCw size={11} /> Sync
          </button>
          <button
            onClick={() => handleBulkAction('predict')}
            className="flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-white/[0.08] text-slate-300 hover:text-cyan-400 border border-white/[0.1] transition-all cursor-pointer"
          >
            <Brain size={11} /> Assess Risk
          </button>
          <button
            onClick={() => handleBulkAction('archive')}
            className="flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-white/[0.08] text-slate-300 hover:text-amber-400 border border-white/[0.1] transition-all cursor-pointer"
          >
            <Archive size={11} /> Archive
          </button>
          <button
            onClick={() => handleBulkAction('delete')}
            className="flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-red-500/15 text-red-400 hover:bg-red-500/25 border border-red-500/30 transition-all cursor-pointer"
          >
            <Trash2 size={11} /> Delete
          </button>
          <button
            onClick={() => setSelectedIds([])}
            className="text-slate-400 hover:text-slate-200 transition-colors ml-auto cursor-pointer"
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

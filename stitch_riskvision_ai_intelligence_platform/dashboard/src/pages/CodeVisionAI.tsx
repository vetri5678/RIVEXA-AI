import React, { useState, useEffect, useMemo } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import DashboardLayout from '../components/layout/DashboardLayout';
import PipelineBreadcrumbs from '../components/common/PipelineBreadcrumbs';
import PredictionPipelineWidget from '../components/dashboard/PredictionPipeline/PredictionPipelineWidget';
import {
  Eye,
  Search,
  Zap,
  FileCode,
  ChevronRight,
  Loader2,
  RotateCcw,
  ShieldAlert,
} from 'lucide-react';
import { useRepositories, useGithubUserRepositories } from '../hooks/useRepository';
import { useGitHubUrlPredictionMutation } from '../hooks/useDashboard';
import {
  useCodeVisionSummary,
  useCodeVisionFiles,
  useStartCodeVisionAnalysis,
  useStartBatchCodeVisionAnalysis,
  useForceCodeVisionRescan,
} from '../hooks/useCodeVision';
import { CodeVisionFileDetailDrawer } from '../components/codevision/CodeVisionFileDetailDrawer';

interface RepositoryOption {
  id: string;
  name: string;
  fullName: string;
  url: string;
  gitProvider: string;
  isGithubOnly?: boolean;
}

const resolveRepoName = (item: any): { name: string; fullName: string } => {
  if (!item) return { name: 'Repository', fullName: 'Repository' };

  let rawName = item.repositoryName || item.name;
  let rawFullName = item.fullName || item.full_name || rawName;

  const isInvalid = (val?: string) => !val || val === '(Unnamed)' || val === 'Unnamed Repository' || val.trim() === '';

  if (isInvalid(rawName) || isInvalid(rawFullName)) {
    const url = item.repositoryUrl || item.html_url || item.url || '';
    if (url) {
      const cleanUrl = url.trim().replace(/\/+$/, '').replace(/\.git$/, '');
      const parts = cleanUrl.split('/');
      if (parts.length >= 2) {
        const owner = parts[parts.length - 2];
        const repo = parts[parts.length - 1];
        if (owner && repo && !owner.includes(':') && !owner.includes('.')) {
          rawFullName = `${owner}/${repo}`;
          rawName = repo;
        } else if (repo) {
          rawName = repo;
          rawFullName = repo;
        }
      } else if (parts.length === 1 && parts[0]) {
        rawName = parts[0];
        rawFullName = parts[0];
      }
    }
  }

  if (isInvalid(rawName)) {
    rawName = item.id ? `Repo-${String(item.id).substring(0, 8)}` : 'Repository';
  }
  if (isInvalid(rawFullName)) {
    rawFullName = rawName;
  }

  return { name: rawName, fullName: rawFullName };
};

const parseRepositories = (dbReposResponse: any, ghReposResponse: any): RepositoryOption[] => {
  const options: RepositoryOption[] = [];
  const addedIds = new Set<string>();

  // 1. Parse registered database repositories
  const dbItems = Array.isArray(dbReposResponse)
    ? dbReposResponse
    : dbReposResponse?.content || dbReposResponse?.data || dbReposResponse?.repositories || [];

  for (const item of dbItems) {
    if (item && item.id) {
      const idStr = String(item.id);
      const { name, fullName } = resolveRepoName(item);
      options.push({
        id: idStr,
        name,
        fullName,
        url: item.repositoryUrl || item.html_url || item.url || '',
        gitProvider: item.gitProvider || 'GitHub',
        isGithubOnly: false,
      });
      addedIds.add(idStr);
    }
  }

  // 2. Parse live GitHub repositories if connected
  const ghItems = Array.isArray(ghReposResponse)
    ? ghReposResponse
    : ghReposResponse?.repositories || ghReposResponse?.data || [];

  for (const item of ghItems) {
    if (item) {
      const idStr = String(item.id || item.full_name || item.name);
      const { name, fullName } = resolveRepoName(item);
      const url = item.html_url || item.repositoryUrl || item.url || '';

      const alreadyPresent = options.some(
        (o) => o.id === idStr || (o.url && url && o.url.toLowerCase() === url.toLowerCase())
      );

      if (!alreadyPresent) {
        options.push({
          id: idStr,
          name,
          fullName,
          url,
          gitProvider: 'GitHub',
          isGithubOnly: true,
        });
      }
    }
  }

  return options;
};

export const CodeVisionAI: React.FC = () => {
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedRepoId, setSelectedRepoId] = useState<string>('');
  const [selectedRepoIds, setSelectedRepoIds] = useState<string[]>([]);
  const [isMultiMode, setIsMultiMode] = useState<boolean>(false);
  const [isStartingAnalysis, setIsStartingAnalysis] = useState<boolean>(false);

  // Table Filters & Pagination
  const [fileSearch, setFileSearch] = useState('');
  const [selectedSeverity, setSelectedSeverity] = useState<string>('');
  const [selectedLanguage, setSelectedLanguage] = useState<string>('');
  const [sortBy, setSortBy] = useState<string>('riskScore');
  const [sortDir] = useState<string>('desc');
  const [page, setPage] = useState(0);

  // File Drawer State
  const [selectedFileId, setSelectedFileId] = useState<string | null>(null);

  // Fetch user's registered repositories and live GitHub user repos
  const { data: reposData, isLoading: reposLoading } = useRepositories({ size: 100 });
  const { data: githubUserRepos } = useGithubUserRepositories();

  const repositories = useMemo(() => {
    return parseRepositories(reposData, githubUserRepos);
  }, [reposData, githubUserRepos]);

  // Development debug logging
  useEffect(() => {
    console.log('[CodeVisionAI] Repositories:', repositories);
    console.log('[CodeVisionAI] Selected Focus Repo ID:', selectedRepoId);
    console.log('[CodeVisionAI] Selected Repo IDs:', selectedRepoIds);
  }, [repositories, selectedRepoId, selectedRepoIds]);

  // Auto-select first repository when list loads or changes
  useEffect(() => {
    if (repositories.length > 0) {
      const isValid = repositories.some((r) => r.id === selectedRepoId);
      if (!selectedRepoId || !isValid) {
        const firstId = repositories[0].id;
        setSelectedRepoId(firstId);
        if (selectedRepoIds.length === 0) {
          setSelectedRepoIds([firstId]);
        }
      }
    }
  }, [repositories, selectedRepoId, selectedRepoIds.length]);

  const queryClient = useQueryClient();

  // Fetch Summary & Job Status for selected repository
  const { data: summary } = useCodeVisionSummary(selectedRepoId);
  const latestRun = summary?.latestRun;
  const isRunning = latestRun?.status === 'RUNNING' || latestRun?.status === 'QUEUED';

  // Fetch Files Table Data
  const { data: pagedFiles, isLoading: filesLoading, isError } = useCodeVisionFiles(selectedRepoId, {
    severity: selectedSeverity || undefined,
    language: selectedLanguage || undefined,
    search: fileSearch.trim() || undefined,
    page,
    size: 15,
    sortBy,
    sortDir,
  });

  const startAnalysisMutation = useStartCodeVisionAnalysis();
  const startBatchAnalysisMutation = useStartBatchCodeVisionAnalysis();
  const forceRescanMutation = useForceCodeVisionRescan();
  const githubUrlMutation = useGitHubUrlPredictionMutation();

  const isUuid = (str: string): boolean => {
    return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(str);
  };

  // Reset file detail drawer & table filters when repository selection changes
  const handleRepoSelect = (repoId: string) => {
    console.log('[CodeVisionAI] Repository selected:', repoId);
    if (selectedRepoId && selectedRepoId !== repoId) {
      queryClient.cancelQueries({ queryKey: ['code-vision-summary', selectedRepoId] });
      queryClient.removeQueries({ queryKey: ['code-vision-summary', selectedRepoId] });
      queryClient.cancelQueries({ queryKey: ['code-vision-files', selectedRepoId] });
      queryClient.removeQueries({ queryKey: ['code-vision-files', selectedRepoId] });
    }
    setSelectedRepoId(repoId);
    setSelectedRepoIds([repoId]);
    setSelectedFileId(null);
    setFileSearch('');
    setSelectedSeverity('');
    setSelectedLanguage('');
    setPage(0);
    queryClient.invalidateQueries({ queryKey: ['code-vision-summary', repoId] });
    queryClient.invalidateQueries({ queryKey: ['code-vision-files', repoId] });
  };

  const handleToggleMultiRepo = (repoId: string) => {
    setSelectedRepoIds((prev) => {
      let updated: string[];
      if (prev.includes(repoId)) {
        updated = prev.filter((id) => id !== repoId);
      } else {
        updated = [...prev, repoId];
      }
      if (updated.length > 0) {
        setSelectedRepoId(updated[0]);
      }
      return updated;
    });
  };

  const handleSelectAllRepos = () => {
    if (selectedRepoIds.length === repositories.length) {
      setSelectedRepoIds([]);
    } else {
      const allIds = repositories.map((r) => r.id);
      setSelectedRepoIds(allIds);
      if (allIds.length > 0) setSelectedRepoId(allIds[0]);
    }
  };

  // Auto-resolve non-UUID repository IDs (such as raw GitHub repository options) to database UUIDs
  useEffect(() => {
    let isSubscribed = true;
    if (selectedRepoId && !isUuid(selectedRepoId)) {
      const repoOption = repositories.find((r) => r.id === selectedRepoId);
      if (repoOption?.url) {
        console.log('[CodeVisionAI] Auto-resolving non-UUID selectedRepoId to DB entity:', selectedRepoId, repoOption.url);
        githubUrlMutation
          .mutateAsync(repoOption.url)
          .then((res) => {
            if (isSubscribed && res && res.repositoryId) {
              console.log('[CodeVisionAI] Successfully auto-resolved repo ID to DB UUID:', res.repositoryId);
              setSelectedRepoId(res.repositoryId);
              setSelectedRepoIds([res.repositoryId]);
            }
          })
          .catch((err) => {
            console.warn('[CodeVisionAI] Failed to auto-resolve repo ID:', err);
          });
      }
    }
    return () => {
      isSubscribed = false;
    };
  }, [selectedRepoId, repositories]);

  // Auto-start analysis if selected repository has no analysis run yet
  useEffect(() => {
    if (selectedRepoId && isUuid(selectedRepoId)) {
      queryClient.invalidateQueries({ queryKey: ['code-vision-files', selectedRepoId] });
      queryClient.invalidateQueries({ queryKey: ['pipeline-lifecycle'] });

      if (summary && !summary.latestRun && !isRunning && !isStartingAnalysis) {
        console.log('[CodeVisionAI] Auto-initializing code-level analysis for selected repo:', selectedRepoId);
        handleStartAnalysis(false);
      }
    }
  }, [selectedRepoId, summary?.latestRun, queryClient]);

  // Refetch source files table query as soon as analysis job status reaches COMPLETED
  useEffect(() => {
    if (selectedRepoId && isUuid(selectedRepoId) && summary?.latestRun?.status === 'COMPLETED') {
      console.log('[CodeVisionAI] Code Vision analysis COMPLETED for repo:', selectedRepoId, '— Refetching file analysis registry...');
      queryClient.invalidateQueries({ queryKey: ['code-vision-files', selectedRepoId] });
      queryClient.invalidateQueries({ queryKey: ['pipeline-lifecycle'] });
    }
  }, [selectedRepoId, summary?.latestRun?.status, queryClient]);

  const resolveRepoIdToDb = async (repoId: string): Promise<string> => {
    const repoOption = repositories.find((r) => r.id === repoId);
    if ((repoOption?.isGithubOnly || !isUuid(repoId)) && repoOption?.url) {
      console.log('[CodeVisionAI] Resolving GitHub URL to database repository entity:', repoOption.url);
      const res = await githubUrlMutation.mutateAsync(repoOption.url);
      if (res && res.repositoryId) {
        return res.repositoryId;
      }
    }
    return repoId;
  };

  const handleStartAnalysis = async (force = false) => {
    const targetIds = isMultiMode ? selectedRepoIds : [selectedRepoId].filter(Boolean);

    if (targetIds.length === 0) {
      alert('Please select at least one repository before starting analysis.');
      return;
    }

    try {
      setIsStartingAnalysis(true);
      console.log('[CodeVisionAI] Starting analysis for target IDs:', targetIds);

      const resolvedDbRepoIds: string[] = [];
      for (const id of targetIds) {
        const resolvedId = await resolveRepoIdToDb(id);
        resolvedDbRepoIds.push(resolvedId);
      }

      console.log('[CodeVisionAI] Resolved database repository IDs:', resolvedDbRepoIds);

      if (resolvedDbRepoIds.length === 1) {
        const targetId = resolvedDbRepoIds[0];
        setSelectedRepoId(targetId);
        if (force) {
          await forceRescanMutation.mutateAsync(targetId);
        } else {
          await startAnalysisMutation.mutateAsync({ repositoryId: targetId, force: false });
        }
        queryClient.invalidateQueries({ queryKey: ['code-vision-summary', targetId] });
        queryClient.invalidateQueries({ queryKey: ['code-vision-files', targetId] });
      } else {
        await startBatchAnalysisMutation.mutateAsync({ repositoryIds: resolvedDbRepoIds, force });
        resolvedDbRepoIds.forEach((id) => {
          queryClient.invalidateQueries({ queryKey: ['code-vision-summary', id] });
          queryClient.invalidateQueries({ queryKey: ['code-vision-files', id] });
        });
      }

      queryClient.invalidateQueries({ queryKey: ['pipeline-lifecycle'] });
    } catch (err: any) {
      console.error('[CodeVisionAI] Failed to start analysis:', err);
      alert(err?.response?.data?.message || err?.message || 'Failed to start repository analysis.');
    } finally {
      setIsStartingAnalysis(false);
    }
  };

  const getSeverityBadgeClass = (sev: string) => {
    switch (sev) {
      case 'CRITICAL':
        return 'bg-rose-500/20 text-rose-300 border-rose-500/30 shadow-[0_0_10px_rgba(244,63,94,0.2)]';
      case 'HIGH':
        return 'bg-orange-500/20 text-orange-300 border-orange-500/30';
      case 'MEDIUM':
        return 'bg-amber-500/20 text-amber-300 border-amber-500/30';
      default:
        return 'bg-emerald-500/20 text-emerald-300 border-emerald-500/30';
    }
  };

  const isPendingState = isStartingAnalysis || startAnalysisMutation.isPending || startBatchAnalysisMutation.isPending || forceRescanMutation.isPending;

  return (
    <DashboardLayout
      onSearchChange={setSearchTerm}
      searchValue={searchTerm}
      onQuickAction={() => {}}
    >
      <PipelineBreadcrumbs currentStage="Code Vision AI" />

      {/* Header Banner */}
      <div className="glass-strong rounded-2xl p-6 mb-8 border border-white/[0.08] flex flex-col md:flex-row md:items-center justify-between gap-4 shadow-xl">
        <div className="flex items-center gap-4">
          <div className="p-3.5 rounded-2xl bg-cyan-500/15 border border-cyan-500/30 text-cyan-400 shrink-0 shadow-[0_0_20px_rgba(6,182,212,0.2)]">
            <Eye size={28} />
          </div>
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-xl font-bold tracking-tight text-white font-sans">
                RIVEXA Code Vision AI
              </h1>
              <span className="px-2.5 py-0.5 rounded-full text-[10px] font-mono font-bold bg-cyan-500/15 text-cyan-400 border border-cyan-500/30">
                ANALYZE → DETECT → LOCATE → EXPLAIN
              </span>
            </div>
            <p className="text-xs text-slate-400 font-sans mt-1">
              Source-code-level risk detection, AST pattern analysis, line localization, and developer remediation.
            </p>
          </div>
        </div>

        {/* Repository Selector & Controls */}
        <div className="flex flex-wrap items-center gap-3">
          {/* Mode Switcher */}
          <div className="flex items-center bg-slate-900/80 p-1 rounded-xl border border-white/[0.08] font-mono text-[10px]">
            <button
              onClick={() => setIsMultiMode(false)}
              className={`px-2.5 py-1 rounded-lg transition-colors cursor-pointer ${
                !isMultiMode ? 'bg-cyan-500/20 text-cyan-300 font-bold border border-cyan-500/30' : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              Single
            </button>
            <button
              onClick={() => setIsMultiMode(true)}
              className={`px-2.5 py-1 rounded-lg transition-colors cursor-pointer ${
                isMultiMode ? 'bg-cyan-500/20 text-cyan-300 font-bold border border-cyan-500/30' : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              Multi ({selectedRepoIds.length})
            </button>
          </div>

          {!isMultiMode ? (
            <div className="flex items-center gap-2">
              <label htmlFor="repository-selector" className="text-xs font-mono text-slate-400">
                Repository:
              </label>
              <select
                id="repository-selector"
                value={selectedRepoId}
                onChange={(e) => handleRepoSelect(e.target.value)}
                disabled={reposLoading || isPendingState}
                className="bg-cyber-900 border border-glass-border text-slate-200 text-xs font-mono rounded-xl px-3 py-2 focus:outline-none focus:border-cyan-500 min-w-[240px] cursor-pointer disabled:opacity-50 relative z-10"
              >
                {reposLoading ? (
                  <option value="">Loading repositories...</option>
                ) : repositories.length === 0 ? (
                  <option value="">No Repositories Available</option>
                ) : (
                  repositories.map((r) => (
                    <option key={r.id} value={r.id}>
                      {r.fullName || r.name} ({r.gitProvider})
                    </option>
                  ))
                )}
              </select>
            </div>
          ) : (
            <div className="flex items-center gap-2">
              <button
                onClick={handleSelectAllRepos}
                className="text-[10px] font-mono px-2.5 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 border border-slate-700 transition-colors"
              >
                {selectedRepoIds.length === repositories.length ? 'Deselect All' : 'Select All'}
              </button>
              <span className="text-xs font-mono text-cyan-400 font-bold">
                {selectedRepoIds.length} Repositories Selected
              </span>
            </div>
          )}

          <button
            onClick={() => handleStartAnalysis(false)}
            disabled={
              (isMultiMode ? selectedRepoIds.length === 0 : !selectedRepoId) ||
              isRunning ||
              isPendingState ||
              reposLoading
            }
            className="flex items-center gap-2 px-4 py-2 rounded-xl bg-gradient-to-r from-cyan-600 to-blue-600 hover:from-cyan-500 hover:to-blue-500 text-white font-mono text-xs font-bold transition-all disabled:opacity-50 shadow-lg shadow-cyan-500/20 cursor-pointer disabled:cursor-not-allowed"
          >
            {isPendingState || isRunning ? (
              <Loader2 size={14} className="animate-spin" />
            ) : (
              <Zap size={14} />
            )}
            {isPendingState ? 'Starting Analysis...' : isMultiMode ? `Start Batch Analysis (${selectedRepoIds.length})` : 'Start Analysis'}
          </button>

          <button
            onClick={() => handleStartAnalysis(true)}
            disabled={
              (isMultiMode ? selectedRepoIds.length === 0 : !selectedRepoId) ||
              isRunning ||
              isPendingState ||
              reposLoading
            }
            className="flex items-center gap-2 px-3 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 border border-slate-700 text-slate-300 font-mono text-xs transition-all disabled:opacity-50 cursor-pointer disabled:cursor-not-allowed"
            title="Force Full Scan (bypass incremental hash cache)"
          >
            <RotateCcw size={13} />
            Force Full Scan
          </button>
        </div>
      </div>

      {/* Multi-Repo Selection Grid Panel when Multi Mode is active */}
      {isMultiMode && (
        <div className="glass-strong rounded-2xl p-4 mb-8 border border-cyan-500/30 bg-cyber-900/40 shadow-xl">
          <div className="flex items-center justify-between mb-3 border-b border-white/[0.06] pb-2">
            <h3 className="text-xs font-mono font-bold text-cyan-300 uppercase tracking-wider flex items-center gap-2">
              <Zap size={14} className="text-cyan-400" /> Multi-Repository Batch Selection Panel
            </h3>
            <span className="text-[10px] font-mono text-slate-400">
              Check repositories to run simultaneous AI risk scans
            </span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3 max-h-56 overflow-y-auto no-scrollbar pr-1">
            {repositories.map((repo) => {
              const isChecked = selectedRepoIds.includes(repo.id);
              const isFocus = selectedRepoId === repo.id;
              return (
                <div
                  key={repo.id}
                  onClick={() => handleToggleMultiRepo(repo.id)}
                  className={`p-3 rounded-xl border transition-all cursor-pointer flex items-center gap-2.5 ${
                    isChecked
                      ? 'bg-cyan-500/10 border-cyan-500/40 text-cyan-200 shadow-[0_0_12px_rgba(6,182,212,0.15)]'
                      : 'bg-slate-900/40 border-white/[0.06] text-slate-400 hover:bg-white/[0.03]'
                  }`}
                >
                  <input
                    type="checkbox"
                    checked={isChecked}
                    onChange={() => {}} // handled by parent div onClick
                    className="rounded border-slate-700 bg-slate-950 text-cyan-500 focus:ring-cyan-500 cursor-pointer"
                  />
                  <div className="min-w-0 flex-1">
                    <p className={`text-xs font-mono font-bold truncate ${isChecked ? 'text-white' : 'text-slate-300'}`}>
                      {repo.name}
                    </p>
                    <p className="text-[10px] font-mono text-slate-500 truncate">
                      {repo.fullName}
                    </p>
                  </div>
                  {isFocus && (
                    <span className="px-1.5 py-0.5 rounded text-[8px] font-mono font-bold bg-cyan-400/20 text-cyan-300 border border-cyan-400/30 shrink-0">
                      FOCUS
                    </span>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* No Repositories Warning Banner */}
      {repositories.length === 0 && !reposLoading && (
        <div className="glass-strong rounded-2xl p-4 mb-8 border border-amber-500/30 bg-amber-950/20 text-amber-300 flex items-center gap-3 shadow-xl">
          <ShieldAlert size={20} className="text-amber-400 shrink-0" />
          <div>
            <h3 className="text-xs font-mono font-bold uppercase">No Repositories Available</h3>
            <p className="text-xs text-amber-200/80 font-sans mt-0.5">
              Connect your GitHub account or register a repository in the Repository Intelligence Center to run Code Vision AI.
            </p>
          </div>
        </div>
      )}

      {/* Embedded Pipeline Navigation Card */}
      <div className="mb-8">
        <PredictionPipelineWidget />
      </div>

      {/* Real-time Job Progress Banner */}
      {isRunning && (
        <div className="glass-strong rounded-2xl p-5 mb-8 border border-cyan-500/30 bg-cyan-950/20 shadow-xl relative overflow-hidden">
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center gap-3">
              <Loader2 size={18} className="animate-spin text-cyan-400" />
              <div>
                <h3 className="text-xs font-mono font-bold text-cyan-300 uppercase tracking-wider">
                  Code Vision AI Analysis In Progress... ({latestRun?.status})
                </h3>
                <p className="text-[11px] font-mono text-slate-400 mt-0.5 truncate max-w-xl">
                  Currently Analyzing: <span className="text-cyan-400">{latestRun?.currentlyAnalyzingFile || 'Discovering files...'}</span>
                </p>
              </div>
            </div>

            <div className="text-right font-mono text-xs text-slate-400">
              <span className="text-cyan-400 font-bold">{latestRun?.filesAnalyzed ?? 0}</span> / {latestRun?.filesDiscovered ?? 0} Files
            </div>
          </div>

          {/* Progress Bar */}
          <div className="w-full bg-slate-900 border border-white/10 rounded-full h-2 overflow-hidden">
            <div
              className="h-full bg-gradient-to-r from-cyan-500 to-blue-500 transition-all duration-300"
              style={{
                width: `${
                  (latestRun?.filesDiscovered ?? 0) > 0
                    ? Math.min(100, Math.round(((latestRun?.filesAnalyzed ?? 0) / (latestRun?.filesDiscovered ?? 1)) * 100))
                    : 10
                }%`,
              }}
            />
          </div>
        </div>
      )}

      {/* Failed Run Banner */}
      {latestRun?.status === 'FAILED' && (
        <div className="glass-strong rounded-2xl p-4 mb-8 border border-rose-500/30 bg-rose-950/20 text-rose-300 flex items-start gap-3 shadow-xl">
          <ShieldAlert size={20} className="text-rose-400 shrink-0 mt-0.5" />
          <div>
            <h3 className="text-xs font-mono font-bold text-rose-300 uppercase">Analysis Execution Issue</h3>
            <p className="text-xs text-rose-200 mt-0.5 font-mono">
              {latestRun.errorMessage || 'GitHub repository file tree or file content could not be retrieved.'}
            </p>
          </div>
        </div>
      )}

      {/* Summary Cards */}
      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4 mb-8">
        <div className="glass-strong p-4 rounded-2xl border border-white/[0.08]">
          <span className="text-[10px] font-mono font-bold uppercase text-slate-500 block mb-1">
            FILES ANALYZED
          </span>
          <span className="text-2xl font-bold font-mono text-white">
            {summary?.totalFilesAnalyzed ?? 0}
          </span>
        </div>

        <div className="glass-strong p-4 rounded-2xl border border-white/[0.08]">
          <span className="text-[10px] font-mono font-bold uppercase text-slate-500 block mb-1">
            FILES WITH FINDINGS
          </span>
          <span className="text-2xl font-bold font-mono text-pink-400">
            {summary?.filesWithFindings ?? 0}
          </span>
        </div>

        <div className="glass-strong p-4 rounded-2xl border border-rose-500/20 bg-rose-950/10">
          <span className="text-[10px] font-mono font-bold uppercase text-rose-400 block mb-1">
            CRITICAL
          </span>
          <span className="text-2xl font-bold font-mono text-rose-400">
            {summary?.criticalCount ?? 0}
          </span>
        </div>

        <div className="glass-strong p-4 rounded-2xl border border-orange-500/20 bg-orange-950/10">
          <span className="text-[10px] font-mono font-bold uppercase text-orange-400 block mb-1">
            HIGH
          </span>
          <span className="text-2xl font-bold font-mono text-orange-400">
            {summary?.highCount ?? 0}
          </span>
        </div>

        <div className="glass-strong p-4 rounded-2xl border border-amber-500/20 bg-amber-950/10">
          <span className="text-[10px] font-mono font-bold uppercase text-amber-400 block mb-1">
            MEDIUM
          </span>
          <span className="text-2xl font-bold font-mono text-amber-400">
            {summary?.mediumCount ?? 0}
          </span>
        </div>

        <div className="glass-strong p-4 rounded-2xl border border-emerald-500/20 bg-emerald-950/10">
          <span className="text-[10px] font-mono font-bold uppercase text-emerald-400 block mb-1">
            LOW
          </span>
          <span className="text-2xl font-bold font-mono text-emerald-400">
            {summary?.lowCount ?? 0}
          </span>
        </div>
      </div>

      {/* Problematic Source Files Section */}
      <div className="glass-strong rounded-2xl border border-white/[0.08] p-6 shadow-xl font-sans">
        {/* Table Header & Controls */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-6">
          <div>
            <h2 className="text-sm font-mono font-bold text-white uppercase tracking-wider flex items-center gap-2">
              <FileCode size={16} className="text-cyan-400" /> Source File Risk Registry
            </h2>
            <p className="text-xs text-slate-400 mt-0.5">
              Click any file row to open the line-by-line inspector and code finding remediation drawer.
            </p>
          </div>

          {/* Filters */}
          <div className="flex flex-wrap items-center gap-3 font-mono text-xs">
            {/* Search Input */}
            <div className="relative">
              <Search size={13} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
              <input
                type="text"
                placeholder="Search file path..."
                value={fileSearch}
                onChange={(e) => {
                  setFileSearch(e.target.value);
                  setPage(0);
                }}
                className="bg-cyber-900 border border-glass-border text-slate-200 text-xs rounded-xl pl-8 pr-3 py-1.5 focus:outline-none focus:border-cyan-500 w-44"
              />
            </div>

            {/* Severity Filter */}
            <select
              value={selectedSeverity}
              onChange={(e) => {
                setSelectedSeverity(e.target.value);
                setPage(0);
              }}
              className="bg-cyber-900 border border-glass-border text-slate-200 text-xs rounded-xl px-3 py-1.5 focus:outline-none focus:border-cyan-500"
            >
              <option value="">All Severities</option>
              <option value="CRITICAL">Critical</option>
              <option value="HIGH">High</option>
              <option value="MEDIUM">Medium</option>
              <option value="LOW">Low</option>
            </select>

            {/* Language Filter */}
            <select
              value={selectedLanguage}
              onChange={(e) => {
                setSelectedLanguage(e.target.value);
                setPage(0);
              }}
              className="bg-cyber-900 border border-glass-border text-slate-200 text-xs rounded-xl px-3 py-1.5 focus:outline-none focus:border-cyan-500"
            >
              <option value="">All Languages</option>
              <option value="Java">Java</option>
              <option value="Python">Python</option>
              <option value="JavaScript">JavaScript</option>
              <option value="TypeScript">TypeScript</option>
              <option value="React JSX">React JSX</option>
              <option value="React TSX">React TSX</option>
            </select>

            {/* Sort */}
            <select
              value={sortBy}
              onChange={(e) => setSortBy(e.target.value)}
              className="bg-cyber-900 border border-glass-border text-slate-200 text-xs rounded-xl px-3 py-1.5 focus:outline-none focus:border-cyan-500"
            >
              <option value="riskScore">Sort by Risk Score</option>
              <option value="linesOfCode">Sort by LOC</option>
              <option value="analyzedAt">Sort by Date</option>
            </select>
          </div>
        </div>

        {/* Files Table */}
        {filesLoading ? (
          <div className="p-12 text-center text-slate-400 font-mono text-xs flex flex-col items-center justify-center gap-3">
            <Loader2 size={24} className="animate-spin text-cyan-400" />
            Loading file analysis registry...
          </div>
        ) : isError || !pagedFiles ? (
          <div className="p-12 text-center text-slate-500 font-mono text-xs bg-white/[0.01] border border-white/[0.04] rounded-2xl">
            <div className="space-y-3">
              <ShieldAlert size={32} className="mx-auto text-amber-500/80" />
              <p className="text-slate-300 font-bold text-sm">
                {!selectedRepoId ? 'No Repository Selected' : 'No Source File Analysis Found'}
              </p>
              <p className="text-slate-500 text-xs">
                {!selectedRepoId
                  ? 'Please select a repository from the dropdown above to view source file risk analysis.'
                  : 'Select a repository above and click Start Analysis to perform code-level scanning and build the file directory.'}
              </p>
            </div>
          </div>
        ) : pagedFiles.content.length === 0 ? (
          <div className="p-12 text-center text-slate-500 font-mono text-xs bg-white/[0.01] border border-white/[0.04] rounded-2xl">
            {latestRun ? (
              <p>No source files match the selected filter criteria.</p>
            ) : (
              <div className="space-y-3">
                <ShieldAlert size={32} className="mx-auto text-slate-600" />
                <p className="text-slate-300 font-bold text-sm">No Code Vision Analysis Run Recorded Yet</p>
                <p className="text-slate-500 text-xs">
                  Select a repository above and click <strong className="text-cyan-400">Start Analysis</strong> to analyze repository source code.
                </p>
              </div>
            )}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse font-mono text-xs">
              <thead>
                <tr className="border-b border-white/[0.08] text-slate-500 text-[10px] uppercase tracking-wider">
                  <th className="py-3 px-4">File Path</th>
                  <th className="py-3 px-4">Language</th>
                  <th className="py-3 px-4 text-right">LOC</th>
                  <th className="py-3 px-4 text-center">Risk Score</th>
                  <th className="py-3 px-4 text-center">Severity</th>
                  <th className="py-3 px-4 text-center">Findings</th>
                  <th className="py-3 px-4 text-right">Last Analyzed</th>
                  <th className="py-3 px-4"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/[0.04]">
                {pagedFiles?.content.map((file) => (
                  <tr
                    key={file.id}
                    onClick={() => setSelectedFileId(file.id)}
                    className="hover:bg-white/[0.03] transition-colors cursor-pointer group"
                  >
                    <td className="py-3.5 px-4 font-bold text-slate-200 flex items-center gap-2 max-w-md truncate">
                      <FileCode size={14} className="text-slate-500 group-hover:text-cyan-400 shrink-0" />
                      <span className="truncate" title={file.filePath}>
                        {file.filePath}
                      </span>
                    </td>

                    <td className="py-3.5 px-4 text-slate-400">{file.language}</td>

                    <td className="py-3.5 px-4 text-right text-slate-300 font-bold">{file.linesOfCode}</td>

                    <td className="py-3.5 px-4 text-center">
                      <span
                        className={`font-bold ${
                          file.riskScore >= 75
                            ? 'text-rose-400'
                            : file.riskScore >= 50
                            ? 'text-orange-400'
                            : file.riskScore >= 25
                            ? 'text-amber-400'
                            : 'text-emerald-400'
                        }`}
                      >
                        {file.riskScore}
                      </span>
                      <span className="text-[10px] text-slate-600"> / 100</span>
                    </td>

                    <td className="py-3.5 px-4 text-center">
                      <span
                        className={`px-2.5 py-0.5 rounded-full text-[10px] font-bold border uppercase ${getSeverityBadgeClass(
                          file.severity
                        )}`}
                      >
                        {file.severity}
                      </span>
                    </td>

                    <td className="py-3.5 px-4 text-center">
                      <span
                        className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                          file.findingCount > 0 ? 'bg-pink-500/20 text-pink-300' : 'bg-slate-800 text-slate-500'
                        }`}
                      >
                        {file.findingCount}
                      </span>
                    </td>

                    <td className="py-3.5 px-4 text-right text-slate-500 text-[11px]">
                      {new Date(file.analyzedAt).toLocaleTimeString([], {
                        hour: '2-digit',
                        minute: '2-digit',
                      })}
                    </td>

                    <td className="py-3.5 px-4 text-right">
                      <ChevronRight size={14} className="text-slate-600 group-hover:text-cyan-400 transition-colors" />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination Footer */}
        {pagedFiles && pagedFiles.totalPages > 1 && (
          <div className="flex items-center justify-between pt-4 mt-4 border-t border-white/[0.06] font-mono text-xs text-slate-400">
            <div>
              Page <span className="text-white font-bold">{page + 1}</span> of {pagedFiles.totalPages}
            </div>

            <div className="flex gap-2">
              <button
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
                className="px-3 py-1.5 rounded-lg bg-slate-800 text-slate-300 hover:bg-slate-700 disabled:opacity-30 disabled:cursor-not-allowed"
              >
                Previous
              </button>
              <button
                onClick={() => setPage((p) => Math.min(pagedFiles.totalPages - 1, p + 1))}
                disabled={page >= pagedFiles.totalPages - 1}
                className="px-3 py-1.5 rounded-lg bg-slate-800 text-slate-300 hover:bg-slate-700 disabled:opacity-30 disabled:cursor-not-allowed"
              >
                Next
              </button>
            </div>
          </div>
        )}
      </div>

      {/* File Analysis Detail Drawer */}
      <CodeVisionFileDetailDrawer
        repositoryId={selectedRepoId}
        fileId={selectedFileId || undefined}
        onClose={() => setSelectedFileId(null)}
      />
    </DashboardLayout>
  );
};

export default CodeVisionAI;

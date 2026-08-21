import React, { useState, useEffect } from 'react';
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
import { useRepositories } from '../hooks/useRepository';
import {
  useCodeVisionSummary,
  useCodeVisionFiles,
  useStartCodeVisionAnalysis,
  useForceCodeVisionRescan,
} from '../hooks/useCodeVision';
import { CodeVisionFileDetailDrawer } from '../components/codevision/CodeVisionFileDetailDrawer';

export const CodeVisionAI: React.FC = () => {
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedRepoId, setSelectedRepoId] = useState<string>('');

  // Table Filters & Pagination
  const [fileSearch, setFileSearch] = useState('');
  const [selectedSeverity, setSelectedSeverity] = useState<string>('');
  const [selectedLanguage, setSelectedLanguage] = useState<string>('');
  const [sortBy, setSortBy] = useState<string>('riskScore');
  const [sortDir] = useState<string>('desc');
  const [page, setPage] = useState(0);

  // File Drawer State
  const [selectedFileId, setSelectedFileId] = useState<string | null>(null);

  // Fetch user's registered repositories
  const { data: reposData } = useRepositories({ size: 100 });
  const repos = reposData?.content || [];

  // Auto-select first repository if available
  useEffect(() => {
    if (repos.length > 0 && !selectedRepoId) {
      setSelectedRepoId(repos[0].id);
    }
  }, [repos, selectedRepoId]);

  // Fetch Summary & Job Status
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
  const forceRescanMutation = useForceCodeVisionRescan();

  const handleStartAnalysis = (force = false) => {
    if (!selectedRepoId) return;
    if (force) {
      forceRescanMutation.mutate(selectedRepoId);
    } else {
      startAnalysisMutation.mutate({ repositoryId: selectedRepoId, force: false });
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
          <div className="flex items-center gap-2">
            <label className="text-xs font-mono text-slate-400">Repository:</label>
            <select
              value={selectedRepoId}
              onChange={(e) => {
                setSelectedRepoId(e.target.value);
                setPage(0);
              }}
              className="bg-cyber-900 border border-glass-border text-slate-200 text-xs font-mono rounded-xl px-3 py-2 focus:outline-none focus:border-cyan-500"
            >
              {repos.length === 0 && <option value="">No Repositories Found</option>}
              {repos.map((r) => (
                <option key={r.id} value={r.id}>
                  {r.repositoryName} ({r.gitProvider})
                </option>
              ))}
            </select>
          </div>

          <button
            onClick={() => handleStartAnalysis(false)}
            disabled={!selectedRepoId || isRunning || startAnalysisMutation.isPending}
            className="flex items-center gap-2 px-4 py-2 rounded-xl bg-gradient-to-r from-cyan-600 to-blue-600 hover:from-cyan-500 hover:to-blue-500 text-white font-mono text-xs font-bold transition-all disabled:opacity-50 shadow-lg shadow-cyan-500/20"
          >
            {startAnalysisMutation.isPending || isRunning ? (
              <Loader2 size={14} className="animate-spin" />
            ) : (
              <Zap size={14} />
            )}
            Start Analysis
          </button>

          <button
            onClick={() => handleStartAnalysis(true)}
            disabled={!selectedRepoId || isRunning || forceRescanMutation.isPending}
            className="flex items-center gap-2 px-3 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 border border-slate-700 text-slate-300 font-mono text-xs transition-all disabled:opacity-50"
            title="Force Full Scan (bypass incremental hash cache)"
          >
            <RotateCcw size={13} />
            Force Full Scan
          </button>
        </div>
      </div>

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

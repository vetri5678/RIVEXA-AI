import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import DashboardLayout from '../components/layout/DashboardLayout';
import {
  useGithubUserRepositories,
  useGithubConnectionStatus,
  useDisconnectGithub,
} from '../hooks/useRepository';
import GitHubDisconnectModal from '../components/common/GitHubDisconnectModal';
import { repositoryApi } from '../api/repository';
import type { GithubRepository } from '../types/repository';
import {
  Zap,
  Search,
  CheckCircle2,
  Loader2,
  AlertTriangle,
  ExternalLink,
  Calendar,
  ShieldAlert,
  ChevronRight,
  RefreshCw,
  LayoutDashboard,
  Lock,
  Globe,
  Code2,
  Unplug,
} from 'lucide-react';
import { FaGithub } from 'react-icons/fa';
import { getConnectGitHubUrl } from '../utils/auth';

// ── Types ──────────────────────────────────────────────────────────────────────

interface PipelineStage {
  id: string;
  label: string;
  description: string;
  durationMs: number;
}

// ── Pipeline Stage Definitions ─────────────────────────────────────────────────

const PIPELINE_STAGES: PipelineStage[] = [
  { id: 'repo_loaded',      label: 'Repository Loaded',          description: 'GitHub repository metadata validated',            durationMs: 800  },
  { id: 'repo_cloned',      label: 'Repository Cloned',          description: 'Source code fetched from GitHub API',            durationMs: 2200 },
  { id: 'feature_extract',  label: 'Feature Extraction',         description: 'Commit history and issue metrics extracted',      durationMs: 2800 },
  { id: 'data_preprocess',  label: 'Data Preprocessing',         description: 'Normalizing and scaling feature vectors',         durationMs: 1800 },
  { id: 'model_loading',    label: 'Model Loading',              description: 'XGBoost model initialized',                      durationMs: 1200 },
  { id: 'xgb_prediction',   label: 'XGBoost Prediction',         description: 'XGBoost model inference for risk score & probability', durationMs: 2500 },
  { id: 'shap',             label: 'SHAP Explainability',        description: 'Generating SHAP values for feature attribution', durationMs: 2000 },
  { id: 'saving',           label: 'Saving Results',             description: 'Persisting prediction to database',               durationMs: 900  },
  { id: 'report',           label: 'Report Generation',          description: 'Assembling prediction report and insights',       durationMs: 800  },
];

// ── Helpers ────────────────────────────────────────────────────────────────────

const formatDate = (dateStr: string | null) => {
  if (!dateStr) return 'Recently updated';
  try {
    return new Date(dateStr).toLocaleDateString(undefined, {
      month: 'short', day: 'numeric', year: 'numeric',
    });
  } catch { return 'Recently updated'; }
};

const getErrorMessage = (error: unknown): string => {
  if (!error) return 'Unable to fetch GitHub repositories.';
  if (typeof error === 'object' && error !== null && 'response' in error) {
    const res = (error as any).response;
    const data = res?.data;
    if (data?.error?.message) return data.error.message;
    if (data?.message && data.message !== 'Internal Server Error' && data.message !== 'An unexpected error occurred') return data.message;
    if (typeof data?.error === 'string' && data.error !== 'Internal Server Error' && data.error !== 'An unexpected error occurred') return data.error;
    if (data?.detail && data.detail !== 'Internal Server Error') return data.detail;
    if (res?.status === 401) return 'GitHub connection required or expired. Please connect GitHub below.';
    if (res?.status === 403) return 'GitHub repository access denied. Please reconnect GitHub with repository access.';
    if (res?.status === 429) return 'GitHub API rate limit reached. Please try again later.';
    if (res?.status === 500) return 'GitHub repository service encountered an error. Please click Reconnect GitHub to re-authorize.';
  }
  if (error instanceof Error && error.message !== 'Internal Server Error') return error.message;
  return 'GitHub connection required or expired. Please connect GitHub below.';
};

// ── Component ──────────────────────────────────────────────────────────────────

type RunPhase = 'select' | 'running' | 'error';

export const RunPrediction: React.FC = () => {
  const navigate = useNavigate();
  const [searchTerm, setSearchTerm] = useState('');
  const [repoSearch, setRepoSearch]   = useState('');
  const [selectedRepo, setSelectedRepo] = useState<GithubRepository | null>(null);
  const [phase, setPhase]               = useState<RunPhase>('select');
  const [currentStageIdx, setCurrentStageIdx] = useState(-1);
  const [completedStages, setCompletedStages] = useState<Set<number>>(new Set());
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const stageTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Fetch connection status (Sole Source of Truth)
  const { data: connectionStatus, isLoading: statusLoading } = useGithubConnectionStatus();
  const isConnected = connectionStatus?.connected ?? false;
  const githubUsername = connectionStatus?.githubUsername;

  const disconnectMutation = useDisconnectGithub();

  // Fetch live GitHub user repositories (enabled only when connected)
  const {
    data: githubData,
    isLoading: reposLoading,
    isError: reposError,
    error: githubError,
    refetch: refetchRepos,
  } = useGithubUserRepositories();

  const allRepos: GithubRepository[] = isConnected ? (githubData?.repositories ?? []) : [];
  const repoCount = isConnected ? allRepos.length : 0;

  // Filtering against dynamic repository list
  const search = repoSearch.toLowerCase().trim();
  const filteredRepos = allRepos.filter((r) => {
    const name = r.name ?? '';
    const fullName = r.full_name ?? '';
    const owner = r.owner ?? '';
    const desc = r.description ?? '';
    const lang = r.language ?? '';
    const url = r.html_url ?? '';
    return (
      name.toLowerCase().includes(search) ||
      fullName.toLowerCase().includes(search) ||
      owner.toLowerCase().includes(search) ||
      desc.toLowerCase().includes(search) ||
      lang.toLowerCase().includes(search) ||
      url.toLowerCase().includes(search)
    );
  });

  // Auto-select if exactly one repository exists
  useEffect(() => {
    if (isConnected && allRepos.length === 1 && !selectedRepo) {
      setSelectedRepo(allRepos[0]);
    }
    if (!isConnected && selectedRepo) {
      setSelectedRepo(null);
    }
  }, [isConnected, allRepos, selectedRepo]);

  // Cleanup timers on unmount
  useEffect(() => {
    return () => { if (stageTimerRef.current) clearTimeout(stageTimerRef.current); };
  }, []);

  const [isDisconnectModalOpen, setIsDisconnectModalOpen] = useState(false);

  const handleConfirmDisconnect = async () => {
    try {
      await disconnectMutation.mutateAsync();
      setSelectedRepo(null);
      setIsDisconnectModalOpen(false);
    } catch (err) {
      console.error('[RunPrediction] Disconnect failed:', err);
    }
  };

  const connectGitHubUrl = getConnectGitHubUrl();

  // ── Animated progress stages ───────────────────────────────────────────────

  const animateStages = (totalDuration: number) => {
    const totalMs = PIPELINE_STAGES.reduce((sum, s) => sum + s.durationMs, 0);
    let elapsed = 0;
    let idx = 0;

    const advance = () => {
      if (idx >= PIPELINE_STAGES.length) return;
      setCurrentStageIdx(idx);
      const stageMs = Math.max(
        300,
        Math.round((PIPELINE_STAGES[idx].durationMs / totalMs) * totalDuration * 0.85)
      );
      stageTimerRef.current = setTimeout(() => {
        setCompletedStages((prev) => new Set([...prev, idx]));
        elapsed += stageMs;
        idx++;
        advance();
      }, stageMs);
    };

    advance();
  };

  // ── Run prediction ─────────────────────────────────────────────────────────

  const handleRunPrediction = async () => {
    if (!isConnected) {
      setErrorMessage('GitHub account is not connected. Please connect GitHub before running predictions.');
      setPhase('error');
      return;
    }
    if (!selectedRepo) {
      setErrorMessage('Please select a repository before running a prediction.');
      setPhase('error');
      return;
    }
    if (!selectedRepo.html_url) {
      setErrorMessage('Selected repository URL is missing. Please select the repository again.');
      setPhase('error');
      return;
    }

    setPhase('running');
    setCurrentStageIdx(0);
    setCompletedStages(new Set());
    setErrorMessage(null);

    // Start stage animation (~14 sec budget)
    animateStages(14000);

    try {
      // Execute prediction against real selected GitHub repository
      const result = await repositoryApi.predictByGithubUrl(selectedRepo.html_url);
      
      if (stageTimerRef.current) clearTimeout(stageTimerRef.current);
      setCompletedStages(new Set(PIPELINE_STAGES.map((_, i) => i)));
      setCurrentStageIdx(PIPELINE_STAGES.length - 1);

      setTimeout(() => {
        const predictionId = result?.predictionId ?? (result as any)?.id ?? null;
        if (predictionId) {
          navigate(`/prediction/${predictionId}`);
        } else {
          setErrorMessage('Prediction completed but no prediction ID was returned. Check backend logs.');
          setPhase('error');
        }
      }, 800);
    } catch (err: any) {
      if (stageTimerRef.current) clearTimeout(stageTimerRef.current);
      console.error('[RunPrediction] Prediction request failed:', {
        error: err,
        status: err?.response?.status,
        data: err?.response?.data,
        message: err?.message
      });
      const msg =
        err?.response?.data?.message ||
        err?.response?.data?.error ||
        err?.message ||
        'Prediction failed. Please check that the backend services are running.';
      setErrorMessage(msg);
      setPhase('error');
    }
  };

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <DashboardLayout
      onSearchChange={setSearchTerm}
      searchValue={searchTerm}
      onQuickAction={() => {}}
    >
      {/* Page Header */}
      <div className="glass-strong rounded-2xl p-6 mb-8 border border-white/[0.08] relative overflow-hidden shadow-2xl">
        <div className="absolute -top-20 -right-20 w-80 h-80 bg-cyan-500/10 rounded-full blur-3xl pointer-events-none" />
        <div className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-cyan-400/40 to-transparent" />
        <div className="flex items-center gap-4 relative z-10">
          <div className="p-3.5 rounded-2xl bg-gradient-to-br from-cyan-500/20 to-blue-500/10 border border-cyan-500/30 text-cyan-400 shrink-0 shadow-[0_0_20px_rgba(34,211,238,0.2)]">
            <Zap size={28} />
          </div>
          <div>
            <div className="flex items-center gap-3 flex-wrap">
              <h1 className="text-2xl font-extrabold tracking-tight text-white font-sans">
                Run <span className="text-transparent bg-clip-text bg-gradient-to-r from-cyan-400 to-blue-400">AI Prediction</span>
              </h1>
              <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-mono font-bold bg-blue-500/15 text-blue-400 border border-blue-500/30">
                <span className="w-1.5 h-1.5 rounded-full bg-blue-400 animate-pulse" />
                Live GitHub Pipeline
              </span>
            </div>
            <p className="text-xs text-slate-400 mt-1.5 max-w-2xl leading-relaxed">
              Select an authenticated GitHub repository to execute feature extraction, preprocessing, XGBoost prediction, and SHAP explainability.
            </p>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
        {/* ── Left: Repository Selector ────────────────────────────────────── */}
        <div className="lg:col-span-3">
          <div className="glass-strong rounded-2xl border border-white/[0.08] overflow-hidden">
            {/* Section header */}
            <div className="px-5 py-4 border-b border-white/[0.06] flex items-center justify-between flex-wrap gap-2">
              <div className="flex items-center gap-2">
                <FaGithub size={15} className="text-cyan-400" />
                <span className="text-xs font-bold text-slate-200 uppercase tracking-wider">
                  GitHub Repositories
                </span>
                <span className="text-[10px] bg-cyan-950/60 text-cyan-400 border border-cyan-500/30 px-2 py-0.5 rounded-full font-mono font-bold">
                  {repoCount} available
                </span>
              </div>
              <div className="flex items-center gap-2">
                {isConnected ? (
                  <>
                    {githubUsername && (
                      <span className="text-[11px] text-slate-400 font-mono">
                        Connected as <strong className="text-cyan-400">@{githubUsername}</strong>
                      </span>
                    )}
                    <button
                      onClick={() => refetchRepos()}
                      disabled={reposLoading || phase === 'running'}
                      title="Refresh GitHub Repositories"
                      className="flex items-center gap-1 text-[10px] px-2.5 py-1 bg-slate-900 border border-slate-800 text-slate-400 hover:text-cyan-400 hover:border-cyan-500/30 rounded-lg transition-colors disabled:opacity-50 cursor-pointer"
                    >
                      <RefreshCw size={11} className={reposLoading ? 'animate-spin' : ''} />
                      <span>Refresh</span>
                    </button>
                    <a
                      href={connectGitHubUrl}
                      title="Connect a Different GitHub Account"
                      className="flex items-center gap-1 text-[10px] px-2.5 py-1 bg-cyan-950/40 border border-cyan-500/30 text-cyan-300 hover:bg-cyan-900/50 rounded-lg transition-colors cursor-pointer"
                    >
                      <FaGithub size={11} />
                      <span>Switch GitHub Account</span>
                    </a>
                    <button
                      onClick={() => setIsDisconnectModalOpen(true)}
                      disabled={disconnectMutation.isPending || phase === 'running'}
                      title="Disconnect GitHub Account"
                      className="flex items-center gap-1 text-[10px] px-2.5 py-1 bg-rose-950/40 border border-rose-500/30 text-rose-400 hover:bg-rose-900/50 rounded-lg transition-colors disabled:opacity-50 cursor-pointer"
                    >
                      <Unplug size={11} />
                      <span>Disconnect</span>
                    </button>
                  </>
                ) : (
                  <a
                    href={connectGitHubUrl}
                    className="flex items-center gap-1.5 text-[10px] px-3 py-1 bg-gradient-to-r from-cyan-500 to-blue-600 text-white font-bold rounded-lg shadow transition-transform hover:scale-[1.02]"
                  >
                    <FaGithub size={11} />
                    <span>Connect GitHub</span>
                  </a>
                )}
              </div>
            </div>

            {/* Search */}
            <div className="px-5 py-3 border-b border-white/[0.04] bg-slate-900/30">
              <div className="relative">
                <Search size={13} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
                <input
                  type="text"
                  value={repoSearch}
                  onChange={(e) => setRepoSearch(e.target.value)}
                  placeholder={isConnected ? "Filter by name, owner, language, or URL…" : "GitHub account not connected"}
                  disabled={!isConnected || phase === 'running'}
                  className="w-full pl-8 pr-4 py-2 bg-slate-950 border border-slate-800 rounded-xl text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-cyan-500/60 focus:ring-1 focus:ring-cyan-500/20 disabled:opacity-50 transition-all duration-200"
                />
              </div>
            </div>

            {/* Repository list */}
            <div className="overflow-y-auto max-h-[420px] p-3 space-y-2 no-scrollbar">
              {statusLoading || (isConnected && reposLoading) ? (
                <div className="flex flex-col items-center justify-center py-14 gap-3">
                  <Loader2 size={24} className="text-cyan-400 animate-spin" />
                  <span className="text-xs text-slate-400 font-mono">Loading repositories for the connected GitHub account…</span>
                </div>
              ) : !isConnected ? (
                <div className="flex flex-col items-center justify-center py-14 gap-3 text-center px-4">
                  <FaGithub size={36} className="text-slate-600 mb-1" />
                  <span className="text-xs font-bold text-slate-200 uppercase tracking-wider">GitHub Account Not Connected</span>
                  <p className="text-[11px] text-slate-400 max-w-xs leading-relaxed">
                    You must connect a GitHub account to view repositories and run AI risk predictions.
                  </p>
                  <a
                    href={connectGitHubUrl}
                    className="mt-2 inline-flex items-center gap-2 px-4 py-2 bg-gradient-to-r from-cyan-500 to-blue-600 text-white font-bold text-xs rounded-xl shadow-lg hover:from-cyan-400 hover:to-blue-500 transition-all"
                  >
                    <FaGithub size={13} />
                    <span>Connect GitHub</span>
                  </a>
                </div>
              ) : reposError ? (
                <div className="flex flex-col items-center justify-center py-14 gap-3 text-center px-4">
                  <ShieldAlert size={28} className="text-rose-400" />
                  <span className="text-xs font-bold text-rose-300">Unable to fetch GitHub repositories</span>
                  <p className="text-[11px] text-slate-400 max-w-xs leading-relaxed">
                    {getErrorMessage(githubError)}
                  </p>
                  <div className="flex items-center gap-2 mt-1">
                    <button
                      onClick={() => refetchRepos()}
                      className="flex items-center gap-1.5 text-[10px] px-3 py-1.5 bg-rose-950/30 border border-rose-500/20 text-rose-400 rounded-lg hover:bg-rose-950/50 transition-colors cursor-pointer"
                    >
                      <RefreshCw size={11} /> Retry Loading
                    </button>
                    <a
                      href={connectGitHubUrl}
                      className="flex items-center gap-1.5 text-[10px] px-3 py-1.5 bg-cyan-950/30 border border-cyan-500/20 text-cyan-400 rounded-lg hover:bg-cyan-950/50 transition-colors"
                    >
                      <FaGithub size={11} /> Reconnect GitHub
                    </a>
                  </div>
                </div>
              ) : allRepos.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-14 gap-2 text-center px-4">
                  <FaGithub size={32} className="text-slate-600 mb-1" />
                  <span className="text-xs font-bold text-slate-300">No GitHub Repositories Found</span>
                  <p className="text-[11px] text-slate-500 max-w-xs leading-relaxed">
                    No repositories were returned for your active GitHub account.
                  </p>
                </div>
              ) : filteredRepos.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-10 text-center">
                  <span className="text-xs font-bold text-slate-300">No matches found</span>
                  <span className="text-[10px] text-slate-500 mt-1">No repositories match "{repoSearch}"</span>
                </div>
              ) : (
                filteredRepos.map((repo) => {
                  const isSelected = selectedRepo?.id === repo.id || selectedRepo?.full_name === repo.full_name;
                  return (
                    <button
                      key={repo.id || repo.full_name}
                      disabled={phase === 'running'}
                      onClick={() => setSelectedRepo(repo)}
                      className={`w-full text-left p-3.5 rounded-xl border transition-all duration-200 flex items-center gap-3 group disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer
                        ${isSelected
                          ? 'bg-cyan-500/10 border-cyan-500/40 shadow-[0_0_16px_rgba(34,211,238,0.12)]'
                          : 'bg-white/[0.02] border-white/[0.06] hover:bg-white/[0.04] hover:border-white/[0.10]'
                        }`}
                    >
                      {/* Selection indicator */}
                      <div className={`w-4 h-4 rounded-full border-2 flex items-center justify-center shrink-0 transition-colors duration-200
                        ${isSelected ? 'border-cyan-400 bg-cyan-400/20' : 'border-slate-600'}`}>
                        {isSelected && <div className="w-2 h-2 rounded-full bg-cyan-400" />}
                      </div>

                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-1 flex-wrap">
                          <span className={`text-xs font-bold truncate transition-colors duration-200 ${isSelected ? 'text-cyan-300' : 'text-slate-200 group-hover:text-white'}`}>
                            {repo.name}
                          </span>
                          <span className="text-[9px] text-slate-400 font-mono">
                            {repo.full_name}
                          </span>
                          {repo.private ? (
                            <span className="inline-flex items-center gap-1 text-[8px] bg-rose-950/40 text-rose-400 border border-rose-500/20 px-1.5 py-0.5 rounded font-bold uppercase shrink-0">
                              <Lock size={8} /> Private
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1 text-[8px] bg-emerald-950/40 text-emerald-400 border border-emerald-500/20 px-1.5 py-0.5 rounded font-bold uppercase shrink-0">
                              <Globe size={8} /> Public
                            </span>
                          )}
                        </div>

                        {repo.description && (
                          <p className="text-[10px] text-slate-400 line-clamp-1 mb-1 font-sans">
                            {repo.description}
                          </p>
                        )}

                        <div className="flex items-center gap-3 text-[10px] text-slate-500 flex-wrap">
                          {repo.language && (
                            <div className="flex items-center gap-1 text-cyan-400">
                              <Code2 size={9} />
                              <span>{repo.language}</span>
                            </div>
                          )}
                          <div className="flex items-center gap-1">
                            <Calendar size={9} />
                            <span>Updated {formatDate(repo.updated_at)}</span>
                          </div>
                        </div>
                      </div>

                      <div className="shrink-0 text-right">
                        <a
                          href={repo.html_url}
                          target="_blank"
                          rel="noopener noreferrer"
                          onClick={(e) => e.stopPropagation()}
                          className="inline-flex items-center gap-1 text-[10px] text-slate-500 hover:text-cyan-400 p-1 transition-colors"
                          title="View on GitHub"
                        >
                          <ExternalLink size={12} />
                        </a>
                      </div>
                    </button>
                  );
                })
              )}
            </div>

            {/* Run button */}
            <div className="px-4 sm:px-5 py-4 border-t border-white/[0.06] flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3 bg-slate-900/20">
              <div className="text-[10px] text-slate-400 font-mono truncate max-w-full sm:max-w-[260px]">
                {!isConnected
                  ? 'GitHub account not connected'
                  : selectedRepo
                  ? `Selected: ${selectedRepo.full_name}`
                  : 'No repository selected'}
              </div>
              <button
                id="run-prediction-btn"
                onClick={handleRunPrediction}
                disabled={!isConnected || !selectedRepo || phase === 'running'}
                className="w-full sm:w-auto flex items-center justify-center gap-2 px-5 py-2.5 rounded-xl bg-gradient-to-r from-cyan-500 to-blue-600 text-white text-xs font-bold uppercase tracking-wider
                  hover:from-cyan-400 hover:to-blue-500 transition-all duration-200 shadow-lg shadow-cyan-500/25
                  disabled:opacity-40 disabled:cursor-not-allowed disabled:shadow-none cursor-pointer"
              >
                {phase === 'running' ? (
                  <><Loader2 size={13} className="animate-spin" /> Running…</>
                ) : (
                  <><Zap size={13} /> Run Prediction</>
                )}
              </button>
            </div>
          </div>
        </div>

        {/* ── Right: Pipeline Stages ───────────────────────────────────────── */}
        <div className="lg:col-span-2">
          <div className="glass-strong rounded-2xl border border-white/[0.08] h-full overflow-hidden">
            <div className="px-5 py-4 border-b border-white/[0.06] flex items-center gap-2">
              <ChevronRight size={14} className="text-blue-400" />
              <span className="text-xs font-bold text-slate-200 uppercase tracking-wider">ML Pipeline Stages</span>
            </div>

            <div className="p-4 space-y-2">
              {PIPELINE_STAGES.map((stage, idx) => {
                const isCompleted = completedStages.has(idx);
                const isActive    = currentStageIdx === idx && phase === 'running' && !isCompleted;
                const isPending   = currentStageIdx < idx && phase === 'running';
                const isIdle      = phase === 'select';

                return (
                  <div
                    key={stage.id}
                    className={`flex items-center gap-3 p-3 rounded-xl border transition-all duration-300
                      ${isCompleted ? 'bg-emerald-500/8 border-emerald-500/25' :
                        isActive    ? 'bg-cyan-500/10 border-cyan-500/30 shadow-[0_0_12px_rgba(34,211,238,0.1)]' :
                        isPending   ? 'bg-slate-900/20 border-white/[0.04] opacity-60' :
                        isIdle      ? 'bg-slate-900/20 border-white/[0.04] opacity-50' :
                                      'bg-slate-900/20 border-white/[0.04] opacity-40'}`}
                  >
                    {/* Stage indicator */}
                    <div className="shrink-0 w-6 h-6 flex items-center justify-center">
                      {isCompleted ? (
                        <CheckCircle2 size={16} className="text-emerald-400" />
                      ) : isActive ? (
                        <Loader2 size={16} className="text-cyan-400 animate-spin" />
                      ) : (
                        <div className={`w-5 h-5 rounded-full border-2 flex items-center justify-center text-[9px] font-bold font-mono
                          ${isPending ? 'border-slate-600 text-slate-500' : 'border-slate-700 text-slate-600'}`}>
                          {idx + 1}
                        </div>
                      )}
                    </div>

                    {/* Stage info */}
                    <div className="flex-1 min-w-0">
                      <p className={`text-[11px] font-bold transition-colors duration-200
                        ${isCompleted ? 'text-emerald-300' :
                          isActive    ? 'text-cyan-300' :
                          isPending   ? 'text-slate-400' : 'text-slate-500'}`}>
                        {stage.label}
                      </p>
                      {(isActive || isCompleted) && (
                        <p className="text-[9px] text-slate-500 mt-0.5 leading-tight">{stage.description}</p>
                      )}
                    </div>

                    {/* Duration hint */}
                    {isCompleted && (
                      <span className="text-[9px] text-emerald-500 font-mono shrink-0">✓</span>
                    )}
                    {isActive && (
                      <span className="text-[9px] text-cyan-400 font-mono shrink-0 animate-pulse">…</span>
                    )}
                  </div>
                );
              })}
            </div>

            {/* Error state */}
            {phase === 'error' && errorMessage && (
              <div className="mx-4 mb-4 p-4 bg-rose-950/30 border border-rose-500/30 rounded-xl">
                <div className="flex items-start gap-2 mb-3">
                  <AlertTriangle size={14} className="text-rose-400 shrink-0 mt-0.5" />
                  <div>
                    <p className="text-xs font-bold text-rose-300 mb-1">Prediction Failed</p>
                    <p className="text-[11px] text-slate-400 leading-relaxed">{errorMessage}</p>
                  </div>
                </div>
                <div className="flex gap-2 mt-3">
                  <button
                    onClick={() => { setPhase('select'); setCurrentStageIdx(-1); setCompletedStages(new Set()); setErrorMessage(null); }}
                    className="flex items-center gap-1.5 text-[10px] px-3 py-1.5 bg-slate-800 border border-slate-700 text-slate-300 rounded-lg hover:bg-slate-700 transition-colors"
                  >
                    <RefreshCw size={10} /> Try Again
                  </button>
                  <button
                    onClick={() => navigate('/dashboard')}
                    className="flex items-center gap-1.5 text-[10px] px-3 py-1.5 bg-slate-800 border border-slate-700 text-slate-300 rounded-lg hover:bg-slate-700 transition-colors"
                  >
                    <LayoutDashboard size={10} /> Dashboard
                  </button>
                </div>
              </div>
            )}

            {/* Idle hint */}
            {phase === 'select' && (
              <div className="px-5 pb-5">
                <div className="p-3 bg-slate-900/40 border border-white/[0.04] rounded-xl">
                  <p className="text-[10px] text-slate-500 leading-relaxed">
                    Select a GitHub repository on the left and click <strong className="text-slate-400">Run Prediction</strong> to start the full ML pipeline. Results will be stored and displayed on the Prediction Result page.
                  </p>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Disconnect Confirmation Modal */}
      <GitHubDisconnectModal
        isOpen={isDisconnectModalOpen}
        onClose={() => setIsDisconnectModalOpen(false)}
        onConfirm={handleConfirmDisconnect}
        isPending={disconnectMutation.isPending}
      />
    </DashboardLayout>
  );
};

export default RunPrediction;

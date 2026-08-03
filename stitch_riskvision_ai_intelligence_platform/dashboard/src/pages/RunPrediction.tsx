import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import DashboardLayout from '../components/layout/DashboardLayout';
import { useRepositories } from '../hooks/useRepository';
import { useRunPredictionMutation } from '../hooks/useDashboard';
import type { RepositorySummary } from '../types/repository';
import {
  Zap,
  Search,
  Database,
  CheckCircle2,
  Loader2,
  AlertTriangle,
  ExternalLink,
  Calendar,
  ShieldAlert,
  ChevronRight,
  GitBranch,
  RefreshCw,
  LayoutDashboard,
} from 'lucide-react';

// ── Types ──────────────────────────────────────────────────────────────────────

interface PipelineStage {
  id: string;
  label: string;
  description: string;
  durationMs: number;
}

// ── Pipeline Stage Definitions ─────────────────────────────────────────────────

const PIPELINE_STAGES: PipelineStage[] = [
  { id: 'repo_loaded',      label: 'Repository Loaded',          description: 'Repository metadata validated from database',     durationMs: 800  },
  { id: 'repo_cloned',      label: 'Repository Cloned',          description: 'Source code fetched from Git provider',           durationMs: 2200 },
  { id: 'feature_extract',  label: 'Feature Extraction',         description: 'Commit history and issue metrics extracted',      durationMs: 2800 },
  { id: 'data_preprocess',  label: 'Data Preprocessing',         description: 'Normalizing and scaling feature vectors',         durationMs: 1800 },
  { id: 'model_loading',    label: 'Model Loading',              description: 'RandomForest and XGBoost models initialized',     durationMs: 1200 },
  { id: 'rf_prediction',    label: 'RF/XGBoost Prediction',      description: 'Ensemble inference across trained models',        durationMs: 2500 },
  { id: 'shap',             label: 'SHAP Explainability',        description: 'Generating SHAP values for feature attribution', durationMs: 2000 },
  { id: 'saving',           label: 'Saving Results',             description: 'Persisting prediction to database',               durationMs: 900  },
  { id: 'report',           label: 'Report Generation',          description: 'Assembling prediction report and insights',       durationMs: 800  },
];

// ── Helpers ────────────────────────────────────────────────────────────────────

const getRiskBadgeClass = (level: string) => {
  switch (level) {
    case 'CRITICAL': return 'bg-rose-500/20 text-rose-400 border border-rose-500/30';
    case 'HIGH':     return 'bg-orange-500/20 text-orange-400 border border-orange-500/30';
    case 'MEDIUM':   return 'bg-amber-500/20 text-amber-400 border border-amber-500/30';
    default:         return 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30';
  }
};

const formatDate = (dateStr: string | null) => {
  if (!dateStr) return 'Never scanned';
  try {
    return new Date(dateStr).toLocaleDateString(undefined, {
      month: 'short', day: 'numeric', year: 'numeric',
    });
  } catch { return 'Unknown'; }
};

// ── Component ──────────────────────────────────────────────────────────────────

type RunPhase = 'select' | 'running' | 'error';

export const RunPrediction: React.FC = () => {
  const navigate = useNavigate();
  const [searchTerm, setSearchTerm] = useState('');
  const [repoSearch, setRepoSearch]   = useState('');
  const [selectedRepo, setSelectedRepo] = useState<RepositorySummary | null>(null);
  const [phase, setPhase]               = useState<RunPhase>('select');
  const [currentStageIdx, setCurrentStageIdx] = useState(-1);
  const [completedStages, setCompletedStages] = useState<Set<number>>(new Set());
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const stageTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Fetch repositories
  const { data: repoData, isLoading: reposLoading, isError: reposError, refetch: refetchRepos } =
    useRepositories({ size: 100 });
  const runMutation = useRunPredictionMutation();

  const allRepos: RepositorySummary[] = repoData?.content ?? [];
  const filteredRepos = allRepos.filter(
    (r) =>
      r.repositoryName.toLowerCase().includes(repoSearch.toLowerCase()) ||
      (r.repositoryUrl && r.repositoryUrl.toLowerCase().includes(repoSearch.toLowerCase()))
  );

  // Auto-select if exactly one repository
  useEffect(() => {
    if (allRepos.length === 1 && !selectedRepo) {
      setSelectedRepo(allRepos[0]);
    }
  }, [allRepos]);

  // Cleanup timers on unmount
  useEffect(() => {
    return () => { if (stageTimerRef.current) clearTimeout(stageTimerRef.current); };
  }, []);

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
    if (!selectedRepo) return;

    setPhase('running');
    setCurrentStageIdx(0);
    setCompletedStages(new Set());
    setErrorMessage(null);

    // Start stage animation while waiting for the API (~15 sec total budget)
    animateStages(14000);

    try {
      const result = await runMutation.mutateAsync(selectedRepo.id);
      // Clear timers
      if (stageTimerRef.current) clearTimeout(stageTimerRef.current);
      // Mark all stages complete
      setCompletedStages(new Set(PIPELINE_STAGES.map((_, i) => i)));
      setCurrentStageIdx(PIPELINE_STAGES.length - 1);

      // Brief pause so user sees all stages green before navigating
      setTimeout(() => {
        const predictionId = result?.predictionId ?? result?.id ?? null;
        if (predictionId) {
          navigate(`/prediction/${predictionId}`);
        } else {
          // Fallback: navigate to result without ID (server returned no ID)
          setErrorMessage('Prediction completed but no prediction ID was returned. Check backend logs.');
          setPhase('error');
        }
      }, 800);
    } catch (err: any) {
      if (stageTimerRef.current) clearTimeout(stageTimerRef.current);
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
                Full ML Pipeline
              </span>
            </div>
            <p className="text-xs text-slate-400 mt-1.5 max-w-2xl leading-relaxed">
              Select a connected repository to run the full RandomForest / XGBoost prediction pipeline with SHAP explainability.
            </p>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
        {/* ── Left: Repository Selector ────────────────────────────────────── */}
        <div className="lg:col-span-3">
          <div className="glass-strong rounded-2xl border border-white/[0.08] overflow-hidden">
            {/* Section header */}
            <div className="px-5 py-4 border-b border-white/[0.06] flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Database size={14} className="text-cyan-400" />
                <span className="text-xs font-bold text-slate-200 uppercase tracking-wider">
                  Select Repository
                </span>
                {allRepos.length > 0 && (
                  <span className="text-[10px] bg-slate-800 text-slate-400 px-1.5 py-0.5 rounded font-mono">
                    {allRepos.length} connected
                  </span>
                )}
              </div>
              {phase === 'select' && selectedRepo && (
                <span className="text-[10px] text-cyan-400 flex items-center gap-1">
                  <CheckCircle2 size={11} /> Selected
                </span>
              )}
            </div>

            {/* Search */}
            <div className="px-5 py-3 border-b border-white/[0.04] bg-slate-900/30">
              <div className="relative">
                <Search size={13} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
                <input
                  type="text"
                  value={repoSearch}
                  onChange={(e) => setRepoSearch(e.target.value)}
                  placeholder="Filter by name or URL…"
                  disabled={phase === 'running'}
                  className="w-full pl-8 pr-4 py-2 bg-slate-950 border border-slate-800 rounded-xl text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-cyan-500/60 focus:ring-1 focus:ring-cyan-500/20 disabled:opacity-50 transition-all duration-200"
                />
              </div>
            </div>

            {/* Repository list */}
            <div className="overflow-y-auto max-h-[420px] p-3 space-y-2 no-scrollbar">
              {reposLoading ? (
                <div className="flex flex-col items-center justify-center py-14 gap-3">
                  <Loader2 size={24} className="text-cyan-400 animate-spin" />
                  <span className="text-xs text-slate-400">Fetching your repositories…</span>
                </div>
              ) : reposError ? (
                <div className="flex flex-col items-center justify-center py-14 gap-3 text-center">
                  <ShieldAlert size={28} className="text-rose-400" />
                  <span className="text-xs font-bold text-rose-300">Failed to load repositories</span>
                  <span className="text-[11px] text-slate-500 max-w-xs">
                    Could not reach the backend. Ensure Spring Boot is running.
                  </span>
                  <button
                    onClick={() => refetchRepos()}
                    className="mt-1 flex items-center gap-1.5 text-[10px] px-3 py-1.5 bg-rose-950/30 border border-rose-500/20 text-rose-400 rounded-lg hover:bg-rose-950/50 transition-colors"
                  >
                    <RefreshCw size={11} /> Retry
                  </button>
                </div>
              ) : allRepos.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-14 gap-2 text-center">
                  <Database size={32} className="text-slate-600 mb-1" />
                  <span className="text-xs font-bold text-slate-300">No Repositories Connected</span>
                  <p className="text-[11px] text-slate-500 max-w-xs leading-relaxed">
                    Connect a GitHub repository from the Repositories page, then return here to run a prediction.
                  </p>
                  <button
                    onClick={() => navigate('/repositories')}
                    className="mt-2 flex items-center gap-1.5 text-[10px] px-3 py-1.5 bg-cyan-950/30 border border-cyan-500/20 text-cyan-400 rounded-lg hover:bg-cyan-950/50 transition-colors"
                  >
                    <GitBranch size={11} /> Go to Repositories
                  </button>
                </div>
              ) : filteredRepos.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-10 text-center">
                  <span className="text-xs font-bold text-slate-300">No matches for "{repoSearch}"</span>
                </div>
              ) : (
                filteredRepos.map((repo) => {
                  const isSelected = selectedRepo?.id === repo.id;
                  return (
                    <button
                      key={repo.id}
                      disabled={phase === 'running'}
                      onClick={() => setSelectedRepo(repo)}
                      className={`w-full text-left p-3.5 rounded-xl border transition-all duration-200 flex items-center gap-3 group disabled:opacity-50 disabled:cursor-not-allowed
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
                        <div className="flex items-center gap-2 mb-1">
                          <span className={`text-xs font-bold truncate transition-colors duration-200 ${isSelected ? 'text-cyan-300' : 'text-slate-200 group-hover:text-white'}`}>
                            {repo.repositoryName}
                          </span>
                          {repo.organization && (
                            <span className="text-[8px] bg-slate-800 text-slate-400 px-1.5 py-0.5 rounded font-bold uppercase shrink-0">
                              {repo.organization}
                            </span>
                          )}
                        </div>
                        <div className="flex items-center gap-1 text-[10px] text-slate-500 truncate">
                          <ExternalLink size={9} />
                          <span className="truncate">{repo.repositoryUrl}</span>
                        </div>
                        <div className="flex items-center gap-1 text-[10px] text-slate-500 mt-0.5">
                          <Calendar size={9} />
                          <span>Last scan: {formatDate(repo.lastSyncDate)}</span>
                        </div>
                      </div>

                      <div className="shrink-0 text-right">
                        <span className={`inline-block px-2 py-0.5 rounded-full text-[8px] font-bold uppercase font-mono ${getRiskBadgeClass(repo.riskLevel)}`}>
                          {repo.riskLevel}
                        </span>
                        <div className="text-[9px] text-slate-500 mt-1 font-mono">
                          {(repo.failureProbability * 100).toFixed(1)}% FP
                        </div>
                      </div>
                    </button>
                  );
                })
              )}
            </div>

            {/* Run button */}
            <div className="px-5 py-4 border-t border-white/[0.06] flex items-center justify-between gap-4 bg-slate-900/20">
              <div className="text-[10px] text-slate-500 font-mono">
                {selectedRepo
                  ? `Selected: ${selectedRepo.repositoryName}`
                  : 'No repository selected'}
              </div>
              <button
                id="run-prediction-btn"
                onClick={handleRunPrediction}
                disabled={!selectedRepo || phase === 'running'}
                className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-gradient-to-r from-cyan-500 to-blue-600 text-white text-xs font-bold uppercase tracking-wider
                  hover:from-cyan-400 hover:to-blue-500 transition-all duration-200 shadow-lg shadow-cyan-500/25
                  disabled:opacity-40 disabled:cursor-not-allowed disabled:shadow-none"
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
                    Select a repository on the left and click <strong className="text-slate-400">Run Prediction</strong> to start the full ML pipeline. Results will be stored and displayed on the Prediction Result page.
                  </p>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </DashboardLayout>
  );
};

export default RunPrediction;

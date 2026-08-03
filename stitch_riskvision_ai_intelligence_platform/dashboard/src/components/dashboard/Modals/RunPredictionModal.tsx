import React, { useState, useRef, useEffect } from 'react';
import { X, Search, Database, ExternalLink, Calendar, ShieldAlert, Zap, AlertCircle, CheckCircle2 } from 'lucide-react';
import { FaGithub } from 'react-icons/fa';
import { useRepositories } from '../../../hooks/useRepository';
import type { RepositorySummary } from '../../../types/repository';

type Tab = 'select' | 'url';

interface RunPredictionModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSelect: (repoId: string) => void;
  onGithubUrl: (githubUrl: string) => void;
  isSubmitting: boolean;
}

// ── Helpers ────────────────────────────────────────────────────────────────────

const GITHUB_URL_REGEX = /^https?:\/\/github\.com\/[a-zA-Z0-9_.-]+\/[a-zA-Z0-9_.-]+(?:\.git)?(?:\/.*)?$/;

function isValidGitHubUrl(url: string): boolean {
  return GITHUB_URL_REGEX.test(url.trim());
}

const getRiskBadgeColor = (level: string) => {
  switch (level) {
    case 'CRITICAL': return 'bg-rose-500/20 text-rose-400 border border-rose-500/30';
    case 'HIGH':     return 'bg-orange-500/20 text-orange-400 border border-orange-500/30';
    case 'MEDIUM':   return 'bg-amber-500/20 text-amber-400 border border-amber-500/30';
    default:         return 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30';
  }
};

const formatLastScan = (dateStr: string | null) => {
  if (!dateStr) return 'Never scanned';
  try {
    return new Date(dateStr).toLocaleDateString(undefined, {
      month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
    });
  } catch {
    return 'Never scanned';
  }
};

// ── Component ──────────────────────────────────────────────────────────────────

export const RunPredictionModal: React.FC<RunPredictionModalProps> = ({
  isOpen,
  onClose,
  onSelect,
  onGithubUrl,
  isSubmitting,
}) => {
  const [activeTab, setActiveTab] = useState<Tab>('select');
  const [searchTerm, setSearchTerm] = useState('');
  const [githubUrl, setGithubUrl] = useState('');
  const [urlTouched, setUrlTouched] = useState(false);
  const urlInputRef = useRef<HTMLInputElement>(null);

  // Fetch all repositories (up to 100)
  const { data: repoData, isLoading, isError, refetch } = useRepositories({ size: 100 });

  // Auto-focus URL input when switching to URL tab
  useEffect(() => {
    if (activeTab === 'url') {
      setTimeout(() => urlInputRef.current?.focus(), 60);
    }
  }, [activeTab]);

  if (!isOpen) return null;

  const repositories: RepositorySummary[] = repoData?.content || [];
  const filteredRepos = repositories.filter(
    (repo) =>
      repo.repositoryName.toLowerCase().includes(searchTerm.toLowerCase()) ||
      (repo.repositoryUrl && repo.repositoryUrl.toLowerCase().includes(searchTerm.toLowerCase())),
  );

  const urlIsValid = githubUrl.trim().length > 0 && isValidGitHubUrl(githubUrl);
  const urlHasError = urlTouched && githubUrl.trim().length > 0 && !urlIsValid;

  const handleUrlSubmit = () => {
    if (!urlIsValid || isSubmitting) return;
    onGithubUrl(githubUrl.trim());
  };

  const handleUrlKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') handleUrlSubmit();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-md p-4 font-mono animate-fade-in">
      <div className="w-full max-w-xl bg-slate-950 border border-slate-800 rounded-2xl shadow-2xl flex flex-col overflow-hidden relative max-h-[90vh]">
        {/* Top accent bar */}
        <div className="absolute top-0 left-0 right-0 h-[2px] bg-gradient-to-r from-cyan-400 via-blue-500 to-indigo-500" />

        {/* ── Header ──────────────────────────────────────────────────────── */}
        <div className="p-5 border-b border-slate-800/80 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="p-1.5 rounded-lg bg-cyan-500/10 border border-cyan-500/20 text-cyan-400">
              <Zap size={16} />
            </div>
            <div>
              <h2 className="text-xs font-black uppercase tracking-wider text-slate-100">
                Run AI Prediction
              </h2>
              <p className="text-[10px] text-slate-400 mt-0.5 tracking-normal">
                Analyze failure probability using the ML pipeline
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-slate-100 p-1.5 bg-slate-900 border border-slate-800 rounded-lg transition-all duration-200"
          >
            <X size={14} />
          </button>
        </div>

        {/* ── Tab Switcher ─────────────────────────────────────────────────── */}
        <div className="flex border-b border-slate-800 bg-slate-900/40">
          <button
            onClick={() => setActiveTab('select')}
            className={`flex-1 flex items-center justify-center gap-2 py-3 text-[11px] font-bold uppercase tracking-wider transition-all duration-200
              ${activeTab === 'select'
                ? 'text-cyan-400 border-b-2 border-cyan-400 bg-cyan-500/5'
                : 'text-slate-500 hover:text-slate-300 border-b-2 border-transparent'
              }`}
          >
            <Database size={12} />
            Select Repository
          </button>
          <button
            onClick={() => setActiveTab('url')}
            className={`flex-1 flex items-center justify-center gap-2 py-3 text-[11px] font-bold uppercase tracking-wider transition-all duration-200
              ${activeTab === 'url'
                ? 'text-cyan-400 border-b-2 border-cyan-400 bg-cyan-500/5'
                : 'text-slate-500 hover:text-slate-300 border-b-2 border-transparent'
              }`}
          >
            <FaGithub size={12} />
            GitHub URL
          </button>
        </div>

        {/* ── Tab: Select Repository ───────────────────────────────────────── */}
        {activeTab === 'select' && (
          <>
            {/* Search */}
            <div className="p-4 border-b border-slate-950 bg-slate-900/40">
              <div className="relative">
                <span className="absolute inset-y-0 left-0 flex items-center pl-3 pointer-events-none text-slate-400">
                  <Search size={14} />
                </span>
                <input
                  type="text"
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  placeholder="Filter by repository name or GitHub URL..."
                  className="w-full pl-9 pr-4 py-2 bg-slate-950 border border-slate-800 rounded-xl text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-cyan-500/60 focus:ring-1 focus:ring-cyan-500/20 transition-all duration-200"
                />
              </div>
            </div>

            {/* Repository list */}
            <div className="flex-1 overflow-y-auto p-4 space-y-2 max-h-[45vh] min-h-[200px] no-scrollbar">
              {isLoading ? (
                <div className="flex flex-col items-center justify-center py-12 text-center text-slate-400 space-y-2">
                  <span className="w-6 h-6 border-2 border-cyan-500/30 border-t-cyan-400 rounded-full animate-spin" />
                  <span className="text-[10px]">Retrieving repository list...</span>
                </div>
              ) : isError ? (
                <div className="flex flex-col items-center justify-center py-12 text-center text-rose-400 space-y-3">
                  <ShieldAlert size={28} className="text-rose-500 animate-bounce" />
                  <span className="text-[11px] font-bold">Failed to load repositories</span>
                  <button
                    onClick={() => refetch()}
                    className="text-[10px] px-3 py-1.5 bg-rose-950/30 border border-rose-500/20 text-rose-400 rounded-lg hover:bg-rose-950/50"
                  >
                    Retry Loading
                  </button>
                </div>
              ) : repositories.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-16 text-center text-slate-400 space-y-2">
                  <Database size={32} className="text-slate-500 mb-1" />
                  <span className="text-xs font-bold text-slate-300">No Repositories Found</span>
                  <p className="text-[10px] text-slate-500 max-w-[280px]">
                    No repositories found. Please connect GitHub or create a repository first.
                  </p>
                  <button
                    onClick={() => setActiveTab('url')}
                    className="mt-2 text-[10px] px-3 py-1.5 bg-cyan-950/30 border border-cyan-500/20 text-cyan-400 rounded-lg hover:bg-cyan-950/50 flex items-center gap-1.5"
                  >
                    <FaGithub size={10} />
                    Paste a GitHub URL instead
                  </button>
                </div>
              ) : filteredRepos.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-12 text-center text-slate-400">
                  <span className="text-xs font-bold text-slate-300">No matches found</span>
                  <span className="text-[10px] text-slate-500 mt-1">
                    No repositories match "{searchTerm}"
                  </span>
                </div>
              ) : (
                filteredRepos.map((repo) => (
                  <button
                    key={repo.id}
                    disabled={isSubmitting}
                    onClick={() => onSelect(repo.id)}
                    className="w-full text-left p-3.5 border border-slate-900 bg-slate-950/60 hover:bg-slate-900/50 hover:border-slate-800 rounded-xl transition-all duration-300 flex items-center justify-between group disabled:opacity-50"
                  >
                    <div className="space-y-1.5 pr-4 flex-1">
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-bold text-slate-200 group-hover:text-cyan-400 transition-colors duration-200">
                          {repo.repositoryName}
                        </span>
                        {repo.organization && (
                          <span className="text-[8px] bg-slate-800 text-slate-400 px-1.5 py-0.5 rounded font-bold uppercase">
                            {repo.organization}
                          </span>
                        )}
                      </div>
                      <div className="flex items-center gap-1.5 text-[9px] text-slate-500">
                        <ExternalLink size={10} />
                        <span className="truncate max-w-[280px]">{repo.repositoryUrl}</span>
                      </div>
                      <div className="flex items-center gap-1.5 text-[9px] text-slate-500">
                        <Calendar size={10} />
                        <span>Last Scan: {formatLastScan(repo.lastSyncDate)}</span>
                      </div>
                    </div>
                    <div className="text-right shrink-0 flex flex-col items-end gap-1.5">
                      <span className={`px-2 py-0.5 rounded-full text-[8px] font-bold font-mono uppercase ${getRiskBadgeColor(repo.riskLevel)}`}>
                        {repo.riskLevel}
                      </span>
                      <span className="text-[9px] font-bold text-slate-400">
                        {(repo.failureProbability * 100).toFixed(1)}% FP
                      </span>
                    </div>
                  </button>
                ))
              )}
            </div>

            {/* Footer */}
            <div className="p-4 border-t border-slate-900 bg-slate-950/50 text-[9px] text-slate-500 text-center flex items-center justify-between">
              <span>Connected Repos: {repositories.length}</span>
              <span>UUID auto-selected on click</span>
            </div>
          </>
        )}

        {/* ── Tab: GitHub URL ───────────────────────────────────────────────── */}
        {activeTab === 'url' && (
          <div className="flex flex-col flex-1">
            <div className="p-6 flex-1 space-y-5">

              {/* Instruction */}
              <div className="bg-cyan-950/20 border border-cyan-500/20 rounded-xl p-4 space-y-1.5">
                <div className="flex items-center gap-2 text-cyan-400">
                  <FaGithub size={14} />
                  <span className="text-[11px] font-bold uppercase tracking-wider">GitHub-Native Analysis</span>
                </div>
                <p className="text-[10px] text-slate-400 leading-relaxed">
                  Paste any public GitHub repository URL. RiskVision will fetch live metadata,
                  register the repository, and run the full ML prediction pipeline automatically.
                </p>
              </div>

              {/* URL Input */}
              <div className="space-y-2">
                <label className="text-[10px] font-bold uppercase tracking-wider text-slate-400">
                  GitHub Repository URL
                </label>
                <div className="relative">
                  <span className="absolute inset-y-0 left-0 flex items-center pl-3.5 pointer-events-none text-slate-500">
                    <FaGithub size={14} />
                  </span>
                  <input
                    ref={urlInputRef}
                    id="github-url-input"
                    type="url"
                    value={githubUrl}
                    onChange={(e) => { setGithubUrl(e.target.value); setUrlTouched(true); }}
                    onBlur={() => setUrlTouched(true)}
                    onKeyDown={handleUrlKeyDown}
                    placeholder="https://github.com/owner/repository"
                    disabled={isSubmitting}
                    className={`w-full pl-10 pr-10 py-3 bg-slate-900 border rounded-xl text-xs text-slate-100 placeholder-slate-600
                      focus:outline-none focus:ring-1 transition-all duration-200 disabled:opacity-50
                      ${urlHasError
                        ? 'border-rose-500/50 focus:border-rose-500/70 focus:ring-rose-500/20'
                        : urlIsValid
                          ? 'border-emerald-500/40 focus:border-emerald-500/60 focus:ring-emerald-500/20'
                          : 'border-slate-700 focus:border-cyan-500/60 focus:ring-cyan-500/20'
                      }`}
                  />
                  {/* Validation icon */}
                  {githubUrl.trim().length > 0 && (
                    <span className="absolute inset-y-0 right-0 flex items-center pr-3.5 pointer-events-none">
                      {urlIsValid
                        ? <CheckCircle2 size={14} className="text-emerald-400" />
                        : <AlertCircle size={14} className="text-rose-400" />
                      }
                    </span>
                  )}
                </div>

                {/* Validation message */}
                {urlHasError && (
                  <p className="text-[10px] text-rose-400 flex items-center gap-1.5">
                    <AlertCircle size={10} />
                    Must be a valid GitHub URL, e.g. https://github.com/owner/repo
                  </p>
                )}
                {urlIsValid && (
                  <p className="text-[10px] text-emerald-400 flex items-center gap-1.5">
                    <CheckCircle2 size={10} />
                    Valid GitHub repository URL detected
                  </p>
                )}
              </div>

              {/* Examples */}
              <div className="space-y-1.5">
                <span className="text-[9px] font-bold uppercase tracking-wider text-slate-500">Examples</span>
                <div className="grid gap-1.5">
                  {[
                    'https://github.com/vetri5678/riskprediction-ai-',
                    'https://github.com/microsoft/vscode',
                    'https://github.com/facebook/react',
                  ].map((ex) => (
                    <button
                      key={ex}
                      onClick={() => { setGithubUrl(ex); setUrlTouched(true); }}
                      className="text-left text-[9px] text-slate-500 hover:text-cyan-400 font-mono truncate transition-colors duration-200 px-2 py-1 rounded-lg hover:bg-slate-900"
                    >
                      {ex}
                    </button>
                  ))}
                </div>
              </div>
            </div>

            {/* Action footer */}
            <div className="p-4 border-t border-slate-900 bg-slate-950/50 flex items-center justify-between gap-3">
              <p className="text-[9px] text-slate-500">
                Live GitHub metadata fetched on first analysis
              </p>
              <button
                id="run-github-url-prediction-btn"
                onClick={handleUrlSubmit}
                disabled={!urlIsValid || isSubmitting}
                className="flex items-center gap-2 px-4 py-2 rounded-xl bg-gradient-to-r from-cyan-500 to-blue-500 text-white text-[11px] font-bold uppercase tracking-wider
                  hover:from-cyan-400 hover:to-blue-400 transition-all duration-200 shadow-lg shadow-cyan-500/25
                  disabled:opacity-40 disabled:cursor-not-allowed disabled:shadow-none"
              >
                {isSubmitting ? (
                  <>
                    <span className="w-3 h-3 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                    Analyzing...
                  </>
                ) : (
                  <>
                    <Zap size={12} />
                    Analyze Repository
                  </>
                )}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default RunPredictionModal;

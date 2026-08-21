import React, { useState, useRef, useEffect } from 'react';
import { X, Search, ExternalLink, Calendar, ShieldAlert, Zap, AlertCircle, CheckCircle2, Lock, Globe, Code2 } from 'lucide-react';
import { FaGithub } from 'react-icons/fa';
import { useGithubUserRepositories } from '../../../hooks/useRepository';
import type { GithubRepository } from '../../../types/repository';

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

const formatDate = (dateStr: string | null) => {
  if (!dateStr) return 'Recently updated';
  try {
    return new Date(dateStr).toLocaleDateString(undefined, {
      month: 'short', day: 'numeric', year: 'numeric',
    });
  } catch {
    return 'Recently updated';
  }
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
    if (res?.status === 401) return 'GitHub account is connected, but repository authorization is missing or expired. Please click Reconnect GitHub below.';
    if (res?.status === 403) return 'GitHub repository access denied. Please reconnect GitHub with repository access.';
    if (res?.status === 429) return 'GitHub API rate limit reached. Please try again later.';
    if (res?.status === 500) return 'GitHub repository service encountered an error. Please click Reconnect GitHub to re-authorize.';
  }
  if (error instanceof Error && error.message !== 'Internal Server Error') return error.message;
  return 'GitHub repository authorization is missing or expired. Please click Reconnect GitHub below.';
};

// ── Component ──────────────────────────────────────────────────────────────────

export const RunPredictionModal: React.FC<RunPredictionModalProps> = ({
  isOpen,
  onClose,
  onSelect: _onSelect,
  onGithubUrl,
  isSubmitting,
}) => {
  const [activeTab, setActiveTab] = useState<Tab>('select');
  const [searchTerm, setSearchTerm] = useState('');
  const [githubUrl, setGithubUrl] = useState('');
  const [urlTouched, setUrlTouched] = useState(false);
  const urlInputRef = useRef<HTMLInputElement>(null);

  // Fetch live GitHub user repositories
  const { data: githubData, isLoading, isError, error, refetch } = useGithubUserRepositories();

  // Auto-focus URL input when switching to URL tab
  useEffect(() => {
    if (activeTab === 'url') {
      setTimeout(() => urlInputRef.current?.focus(), 60);
    }
  }, [activeTab]);

  if (!isOpen) return null;

  const repositories: GithubRepository[] = githubData?.repositories || [];
  const modalSearch = searchTerm.toLowerCase().trim();
  const filteredRepos = repositories.filter((repo) => {
    const name = repo.name ?? '';
    const fullName = repo.full_name ?? '';
    const owner = repo.owner ?? '';
    const desc = repo.description ?? '';
    const lang = repo.language ?? '';
    const url = repo.html_url ?? '';
    return (
      name.toLowerCase().includes(modalSearch) ||
      fullName.toLowerCase().includes(modalSearch) ||
      owner.toLowerCase().includes(modalSearch) ||
      desc.toLowerCase().includes(modalSearch) ||
      lang.toLowerCase().includes(modalSearch) ||
      url.toLowerCase().includes(modalSearch)
    );
  });

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
                Analyze failure probability using the live GitHub ML pipeline
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
            <FaGithub size={12} />
            GitHub Repositories ({repositories.length})
          </button>
          <button
            onClick={() => setActiveTab('url')}
            className={`flex-1 flex items-center justify-center gap-2 py-3 text-[11px] font-bold uppercase tracking-wider transition-all duration-200
              ${activeTab === 'url'
                ? 'text-cyan-400 border-b-2 border-cyan-400 bg-cyan-500/5'
                : 'text-slate-500 hover:text-slate-300 border-b-2 border-transparent'
              }`}
          >
            <ExternalLink size={12} />
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
                  placeholder="Filter by repository name, owner, language, or URL..."
                  className="w-full pl-9 pr-4 py-2 bg-slate-950 border border-slate-800 rounded-xl text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-cyan-500/60 focus:ring-1 focus:ring-cyan-500/20 transition-all duration-200"
                />
              </div>
            </div>

            {/* Repository list */}
            <div className="flex-1 overflow-y-auto p-4 space-y-2 max-h-[45vh] min-h-[200px] no-scrollbar">
              {isLoading ? (
                <div className="flex flex-col items-center justify-center py-12 text-center text-slate-400 space-y-2">
                  <span className="w-6 h-6 border-2 border-cyan-500/30 border-t-cyan-400 rounded-full animate-spin" />
                  <span className="text-[10px]">Retrieving repository list from GitHub...</span>
                </div>
              ) : isError ? (
                <div className="flex flex-col items-center justify-center py-12 text-center text-rose-400 space-y-3">
                  <ShieldAlert size={28} className="text-rose-500 animate-bounce" />
                  <span className="text-[11px] font-bold">Failed to load GitHub repositories</span>
                  <p className="text-[10px] text-slate-400 max-w-[280px]">
                    {getErrorMessage(error)}
                  </p>
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => refetch()}
                      className="text-[10px] px-3 py-1.5 bg-rose-950/30 border border-rose-500/20 text-rose-400 rounded-lg hover:bg-rose-950/50 transition-colors"
                    >
                      Retry Loading
                    </button>
                    <a
                      href="/oauth2/authorization/github"
                      className="text-[10px] px-3 py-1.5 bg-cyan-950/30 border border-cyan-500/20 text-cyan-400 rounded-lg hover:bg-cyan-950/50 transition-colors inline-flex items-center gap-1"
                    >
                      <FaGithub size={10} /> Reconnect GitHub
                    </a>
                  </div>
                </div>
              ) : repositories.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-16 text-center text-slate-400 space-y-2">
                  <FaGithub size={32} className="text-slate-500 mb-1" />
                  <span className="text-xs font-bold text-slate-300">No Repositories Found</span>
                  <p className="text-[10px] text-slate-500 max-w-[280px]">
                    No repositories found for your connected GitHub account. Please connect GitHub or paste a URL below.
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
                    key={repo.id || repo.full_name}
                    disabled={isSubmitting}
                    onClick={() => onGithubUrl(repo.html_url)}
                    className="w-full text-left p-3.5 border border-slate-900 bg-slate-950/60 hover:bg-slate-900/50 hover:border-slate-800 rounded-xl transition-all duration-300 flex items-center justify-between group disabled:opacity-50"
                  >
                    <div className="space-y-1.5 pr-4 flex-1">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="text-xs font-bold text-slate-200 group-hover:text-cyan-400 transition-colors duration-200">
                          {repo.name}
                        </span>
                        <span className="text-[9px] text-slate-500 font-mono">
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
                        <p className="text-[10px] text-slate-400 line-clamp-1">
                          {repo.description}
                        </p>
                      )}
                      <div className="flex items-center gap-3 text-[9px] text-slate-500 flex-wrap">
                        {repo.language && (
                          <span className="flex items-center gap-1 text-cyan-400">
                            <Code2 size={9} /> {repo.language}
                          </span>
                        )}
                        <span className="flex items-center gap-1">
                          <Calendar size={9} /> Updated {formatDate(repo.updated_at)}
                        </span>
                      </div>
                    </div>
                    <div className="text-right shrink-0">
                      <span className="px-2 py-1 rounded-lg text-[10px] font-bold bg-cyan-950/50 text-cyan-400 border border-cyan-500/30 group-hover:bg-cyan-500 group-hover:text-black transition-all">
                        Select
                      </span>
                    </div>
                  </button>
                ))
              )}
            </div>

            {/* Footer */}
            <div className="p-4 border-t border-slate-900 bg-slate-950/50 text-[9px] text-slate-500 text-center flex items-center justify-between">
              <span>Connected Repositories: {repositories.length}</span>
              <span>Click any repository to run prediction</span>
            </div>
          </>
        )}

        {/* ── Tab: GitHub URL ────────────────────────────────────────────── */}
        {activeTab === 'url' && (
          <div className="flex flex-col flex-1">
            <div className="p-6 flex-1 space-y-5">
              <div className="bg-cyan-950/20 border border-cyan-500/20 rounded-xl p-4 space-y-1.5">
                <div className="flex items-center gap-2 text-cyan-400">
                  <FaGithub size={14} />
                  <span className="text-[11px] font-bold uppercase tracking-wider">
                    GitHub-Native Analysis
                  </span>
                </div>
                <p className="text-[10px] text-slate-400 leading-relaxed">
                  Paste any public GitHub repository URL. RIVEXA will fetch live metadata, register the repository, and run the full ML prediction pipeline automatically.
                </p>
              </div>

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
                    onChange={(e) => {
                      setGithubUrl(e.target.value);
                      setUrlTouched(true);
                    }}
                    onBlur={() => setUrlTouched(true)}
                    onKeyDown={handleUrlKeyDown}
                    placeholder="https://github.com/owner/repository"
                    disabled={isSubmitting}
                    className={`w-full pl-10 pr-10 py-3 bg-slate-900 border rounded-xl text-xs text-slate-100 placeholder-slate-600 focus:outline-none transition-all duration-200 disabled:opacity-50 ${
                      urlHasError
                        ? 'border-rose-500/70 focus:border-rose-500 focus:ring-1 focus:ring-rose-500/20'
                        : urlIsValid
                        ? 'border-emerald-500/60 focus:border-emerald-500 focus:ring-1 focus:ring-emerald-500/20'
                        : 'border-slate-800 focus:border-cyan-500/60 focus:ring-1 focus:ring-cyan-500/20'
                    }`}
                  />
                  {urlIsValid && (
                    <span className="absolute inset-y-0 right-0 flex items-center pr-3.5 text-emerald-400 pointer-events-none">
                      <CheckCircle2 size={15} />
                    </span>
                  )}
                  {urlHasError && (
                    <span className="absolute inset-y-0 right-0 flex items-center pr-3.5 text-rose-400 pointer-events-none">
                      <AlertCircle size={15} />
                    </span>
                  )}
                </div>

                {urlHasError && (
                  <p className="text-[10px] text-rose-400 flex items-center gap-1 font-mono">
                    <AlertCircle size={11} />
                    Enter a valid URL (e.g. https://github.com/owner/repo)
                  </p>
                )}
              </div>
            </div>

            {/* Footer */}
            <div className="p-4 border-t border-slate-900 bg-slate-950/50 flex items-center justify-between gap-3">
              <button
                type="button"
                onClick={onClose}
                disabled={isSubmitting}
                className="px-4 py-2 bg-slate-900 border border-slate-800 hover:bg-slate-800 text-slate-300 rounded-xl text-xs font-bold transition-all disabled:opacity-50"
              >
                Cancel
              </button>
              <button
                type="button"
                id="run-prediction-url-btn"
                onClick={handleUrlSubmit}
                disabled={!urlIsValid || isSubmitting}
                className="flex items-center gap-2 px-5 py-2 bg-gradient-to-r from-cyan-500 to-blue-600 hover:from-cyan-400 hover:to-blue-500 text-white rounded-xl text-xs font-bold uppercase tracking-wider transition-all shadow-lg shadow-cyan-500/20 disabled:opacity-40 disabled:cursor-not-allowed disabled:shadow-none"
              >
                {isSubmitting ? (
                  <>
                    <span className="w-3.5 h-3.5 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                    Initializing...
                  </>
                ) : (
                  <>
                    <Zap size={13} />
                    Run Prediction
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

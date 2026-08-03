import React, { useState, useEffect } from 'react';
import {
  X, GitBranch, ExternalLink, RefreshCw, Brain, Activity,
  BarChart2, Clock, Settings,
  Users, Bug, Code, Zap, Loader2,
  FileDown, FileSpreadsheet, Shield, GitCommit, Eye, Star, GitFork, Lock
} from 'lucide-react';
import { useRepositoryById } from '../../hooks/useRepository';
import { useRepositoryRiskAnalysis } from '../../hooks/useDashboard';
import AICard from '../common/AICard';
import Badge from '../common/Badge';
import { githubApi, type GitHubHealthStatus } from '../../api/githubApi';
import type { RepositoryPrediction } from '../../types/repository';

interface Props {
  repositoryId: string | null;
  onClose: () => void;
  onAction: (action: string, id: string) => void;
}

type Tab = 'info' | 'github' | 'metrics' | 'prediction' | 'history' | 'activities';

const TabBtn: React.FC<{ label: string; tab: Tab; icon: React.ElementType; active: Tab; onClick: (t: Tab) => void }> = ({ label, tab, icon: Icon, active, onClick }) => (
  <button
    onClick={() => onClick(tab)}
    className={`flex items-center gap-1.5 px-3 py-2 text-xs font-mono font-semibold rounded-lg transition-all ${
      active === tab
        ? 'bg-neon-blue/10 text-neon-blue border border-neon-blue/20'
        : 'text-slate-400 hover:text-slate-200 hover:bg-cyber-800/40'
    }`}
  >
    <Icon size={12} />
    {label}
  </button>
);

const MetricRow: React.FC<{ label: string; value: string | number; color?: string; bar?: number }> = ({ label, value, color = 'text-slate-200', bar }) => (
  <div className="flex items-center justify-between py-2 border-b border-cyber-800/30 last:border-0">
    <span className="text-[11px] text-slate-500 font-mono uppercase tracking-wider">{label}</span>
    <div className="flex items-center gap-3">
      {bar !== undefined && (
        <div className="w-20 h-1 bg-cyber-800 rounded-full overflow-hidden">
          <div
            className={`h-full rounded-full ${bar >= 70 ? 'bg-neon-green' : bar >= 40 ? 'bg-neon-yellow' : 'bg-neon-pink'}`}
            style={{ width: `${Math.min(100, bar)}%` }}
          />
        </div>
      )}
      <span className={`text-sm font-mono font-bold ${color}`}>{value}</span>
    </div>
  </div>
);

const GitHubLiveTelemetrySection: React.FC<{ repoUrl?: string; owner?: string; repo?: string }> = ({ repoUrl, owner: propOwner, repo: propRepo }) => {
  const [loading, setLoading] = useState(true);
  const [meta, setMeta] = useState<any>(null);
  const [commits, setCommits] = useState<any[]>([]);
  const [branches, setBranches] = useState<any[]>([]);
  const [contributors, setContributors] = useState<any[]>([]);
  const [health, setHealth] = useState<GitHubHealthStatus | null>(null);
  const [error, setError] = useState<string | null>(null);

  let owner = propOwner;
  let repoName = propRepo;
  if ((!owner || !repoName) && repoUrl) {
    try {
      const parts = repoUrl.replace(/\/+$/, '').replace(/\.git$/, '').split('/');
      if (parts.length >= 2) {
        repoName = parts[parts.length - 1];
        owner = parts[parts.length - 2];
      }
    } catch {}
  }
  if (!owner) owner = 'vetri5678';
  if (!repoName) repoName = 'riskprediction-ai-';

  useEffect(() => {
    let isMounted = true;
    async function loadLiveData() {
      setLoading(true);
      setError(null);
      try {
        const [hData, mData, cData, bData, contribData] = await Promise.allSettled([
          githubApi.getHealth(),
          githubApi.getRepoMetadata(owner!, repoName!),
          githubApi.getRepoCommits(owner!, repoName!, undefined, 1, 5),
          githubApi.getRepoBranches(owner!, repoName!),
          githubApi.getRepoContributors(owner!, repoName!),
        ]);

        if (isMounted) {
          if (hData.status === 'fulfilled') setHealth(hData.value);
          if (mData.status === 'fulfilled') setMeta(mData.value);
          if (cData.status === 'fulfilled' && Array.isArray(cData.value)) setCommits(cData.value);
          if (bData.status === 'fulfilled' && Array.isArray(bData.value)) setBranches(bData.value);
          if (contribData.status === 'fulfilled' && Array.isArray(contribData.value)) setContributors(contribData.value);
        }
      } catch (err: any) {
        if (isMounted) setError(err.message || 'Failed to load GitHub telemetry');
      } finally {
        if (isMounted) setLoading(false);
      }
    }
    loadLiveData();
    return () => { isMounted = false; };
  }, [owner, repoName]);

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center py-12 gap-3">
        <Loader2 size={24} className="animate-spin text-neon-blue" />
        <span className="text-xs font-mono text-slate-400">Fetching live telemetry via GitHub PAT proxy…</span>
      </div>
    );
  }

  return (
    <div className="space-y-5">
      {/* PAT Status Header */}
      <div className="flex items-center justify-between p-3.5 bg-cyber-900/60 border border-cyber-800 rounded-xl">
        <div className="flex items-center gap-2.5">
          <div className={`w-2.5 h-2.5 rounded-full ${health?.status === 'UP' ? 'bg-neon-green animate-pulse' : 'bg-neon-pink'}`} />
          <div>
            <div className="text-xs font-mono font-bold text-slate-200">
              GitHub PAT Status: <span className={health?.status === 'UP' ? 'text-neon-green' : 'text-neon-pink'}>{health?.status ?? 'CONNECTED'}</span>
            </div>
            <div className="text-[10px] text-slate-500 font-mono">
              Authenticated User: {health?.authenticated_user ?? 'PAT Proxy'} · Rate Remaining: {health?.rate_limit?.remaining ?? '5,000'}
            </div>
          </div>
        </div>
        <div className="px-2.5 py-1 rounded bg-neon-blue/10 border border-neon-blue/20 text-[10px] font-mono text-neon-blue font-bold">
          Zero-Exposure Proxy
        </div>
      </div>

      {error && (
        <div className="p-3 bg-neon-pink/10 border border-neon-pink/30 rounded-lg text-xs font-mono text-neon-pink">
          {error}
        </div>
      )}

      {/* Live GitHub Stats Grid */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <div className="p-3 bg-cyber-900/40 border border-cyber-800 rounded-lg text-center">
          <div className="flex items-center justify-center gap-1 text-neon-yellow mb-1"><Star size={14} /> <span className="text-[10px] font-mono text-slate-500 uppercase">Stars</span></div>
          <div className="text-lg font-mono font-bold text-slate-100">{meta?.stargazers_count ?? 0}</div>
        </div>
        <div className="p-3 bg-cyber-900/40 border border-cyber-800 rounded-lg text-center">
          <div className="flex items-center justify-center gap-1 text-neon-blue mb-1"><GitFork size={14} /> <span className="text-[10px] font-mono text-slate-500 uppercase">Forks</span></div>
          <div className="text-lg font-mono font-bold text-slate-100">{meta?.forks_count ?? 0}</div>
        </div>
        <div className="p-3 bg-cyber-900/40 border border-cyber-800 rounded-lg text-center">
          <div className="flex items-center justify-center gap-1 text-neon-purple mb-1"><Eye size={14} /> <span className="text-[10px] font-mono text-slate-500 uppercase">Watchers</span></div>
          <div className="text-lg font-mono font-bold text-slate-100">{meta?.subscribers_count ?? meta?.watchers_count ?? 0}</div>
        </div>
        <div className="p-3 bg-cyber-900/40 border border-cyber-800 rounded-lg text-center">
          <div className="flex items-center justify-center gap-1 text-neon-pink mb-1"><Bug size={14} /> <span className="text-[10px] font-mono text-slate-500 uppercase">Open Issues</span></div>
          <div className="text-lg font-mono font-bold text-slate-100">{meta?.open_issues_count ?? 0}</div>
        </div>
      </div>

      {/* Metadata Detail Rows */}
      <div className="bg-cyber-900/30 border border-cyber-800 rounded-xl p-4 space-y-2">
        <h4 className="text-[11px] font-mono font-bold uppercase tracking-widest text-slate-400 mb-2 flex items-center gap-1.5">
          <Code size={12} className="text-neon-blue" /> Live Repository Attributes
        </h4>
        <MetricRow label="Default Branch" value={meta?.default_branch ?? 'main'} color="text-neon-blue" />
        <MetricRow label="Size (KB)" value={meta?.size ? `${meta.size} KB` : '—'} />
        <MetricRow label="License" value={meta?.license?.name ?? 'Not Specified'} />
        <MetricRow label="Visibility" value={meta?.private ? 'PRIVATE' : 'PUBLIC'} color={meta?.private ? 'text-neon-yellow' : 'text-neon-green'} />
        <MetricRow label="Pushed At" value={meta?.pushed_at ? new Date(meta.pushed_at).toLocaleString() : '—'} />
      </div>

      {/* Recent Live Commits */}
      <div>
        <h4 className="text-[11px] font-mono font-bold uppercase tracking-widest text-slate-400 mb-3 flex items-center gap-1.5">
          <GitCommit size={12} className="text-neon-purple" /> Recent Live Commits
        </h4>
        {commits.length === 0 ? (
          <div className="text-xs font-mono text-slate-500 p-3 bg-cyber-900/20 rounded-lg">No commit data retrieved.</div>
        ) : (
          <div className="space-y-2">
            {commits.map((c: any, i: number) => (
              <div key={c.sha || i} className="p-3 bg-cyber-900/40 border border-cyber-800 rounded-lg flex items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  <div className="text-xs font-mono text-slate-200 truncate">{c.commit?.message?.split('\n')[0]}</div>
                  <div className="text-[10px] text-slate-500 font-mono mt-1">
                    {c.commit?.author?.name} · {c.commit?.author?.date ? new Date(c.commit.author.date).toLocaleDateString() : ''}
                  </div>
                </div>
                <span className="px-2 py-0.5 bg-cyber-800 text-[10px] font-mono text-neon-blue rounded shrink-0">
                  {c.sha ? c.sha.substring(0, 7) : ''}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Active Branches & Contributors */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div>
          <h4 className="text-[11px] font-mono font-bold uppercase tracking-widest text-slate-400 mb-2 flex items-center gap-1.5">
            <GitBranch size={12} className="text-neon-blue" /> Active Branches ({branches.length})
          </h4>
          <div className="space-y-1.5 max-h-40 overflow-y-auto pr-1">
            {branches.slice(0, 6).map((b: any, i: number) => (
              <div key={i} className="px-2.5 py-1.5 bg-cyber-900/30 border border-cyber-800/60 rounded text-xs font-mono text-slate-300 flex items-center justify-between">
                <span className="truncate">{b.name}</span>
                {b.protected && <Lock size={10} className="text-neon-yellow shrink-0" />}
              </div>
            ))}
          </div>
        </div>

        <div>
          <h4 className="text-[11px] font-mono font-bold uppercase tracking-widest text-slate-400 mb-2 flex items-center gap-1.5">
            <Users size={12} className="text-neon-green" /> Top Contributors ({contributors.length})
          </h4>
          <div className="space-y-1.5 max-h-40 overflow-y-auto pr-1">
            {contributors.slice(0, 6).map((ct: any, i: number) => (
              <div key={i} className="px-2.5 py-1.5 bg-cyber-900/30 border border-cyber-800/60 rounded text-xs font-mono text-slate-300 flex items-center justify-between">
                <span className="truncate">{ct.login}</span>
                <span className="text-[10px] text-neon-green font-bold">{ct.contributions} commits</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};

const PredictionCard: React.FC<{ pred: RepositoryPrediction; repoId: string; onAction: (action: string, id: string) => void }> = ({ pred, repoId, onAction }) => {
  const riskColor = {
    LOW: 'text-neon-green border-neon-green/20 bg-neon-green/5',
    MEDIUM: 'text-neon-yellow border-yellow-500/20 bg-yellow-500/5',
    HIGH: 'text-neon-orange border-orange-500/20 bg-orange-500/5',
    CRITICAL: 'text-neon-pink border-neon-pink/20 bg-neon-pink/5',
  }[pred.riskLevel] ?? 'text-slate-400 border-slate-700 bg-slate-800/20';

  let recommendations: string[] = [];
  try { recommendations = pred.recommendationsJson ? JSON.parse(pred.recommendationsJson) : []; } catch {}

  let features: any[] = [];
  try { features = pred.featureImportanceJson ? JSON.parse(pred.featureImportanceJson) : []; } catch {}

  return (
    <div className="space-y-4">
      {/* Risk gauge */}
      <div className={`border rounded-xl p-4 ${riskColor}`}>
        <div className="flex items-center justify-between mb-3">
          <span className="text-xs font-mono font-bold uppercase tracking-widest">Risk Level</span>
          <Badge label={pred.riskLevel} />
        </div>
        <div className="flex items-end gap-4">
          <div>
            <div className="text-4xl font-black font-mono">
              {(pred.failureProbability * 100).toFixed(1)}%
            </div>
            <div className="text-xs opacity-70 mt-0.5">Failure Probability</div>
          </div>
          <div className="text-right">
            <div className="text-xl font-black font-mono">{(pred.confidence * 100).toFixed(0)}%</div>
            <div className="text-xs opacity-70 mt-0.5">AI Confidence</div>
          </div>
        </div>
        <div className="mt-3 h-2 bg-black/20 rounded-full overflow-hidden">
          <div
            className="h-full rounded-full transition-all duration-1000"
            style={{ width: `${pred.failureProbability * 100}%`, background: 'currentColor' }}
          />
        </div>
      </div>

      {/* Feature importance */}
      {features.length > 0 && (
        <div>
          <h4 className="text-[11px] font-mono font-bold uppercase tracking-widest text-slate-500 mb-3">Feature Importance</h4>
          <div className="space-y-2">
            {features.map((f: any, i: number) => (
              <div key={i} className="flex items-center gap-3">
                <span className="text-[10px] text-slate-400 w-32 truncate">{f.feature?.replace(/_/g, ' ')}</span>
                <div className="flex-1 h-1.5 bg-cyber-800 rounded-full overflow-hidden">
                  <div
                    className={f.direction === 'increases_risk' ? 'h-full bg-neon-pink rounded-full' : 'h-full bg-neon-green rounded-full'}
                    style={{ width: `${Math.min(100, f.impact * 200)}%` }}
                  />
                </div>
                <span className="text-[10px] text-slate-500 w-10 text-right">{(f.impact * 100).toFixed(0)}%</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Recommendations */}
      {recommendations.length > 0 && (
        <div>
          <h4 className="text-[11px] font-mono font-bold uppercase tracking-widest text-slate-500 mb-3">Recommendations</h4>
          <ul className="space-y-2">
            {recommendations.map((r, i) => (
              <li key={i} className="flex items-start gap-2">
                <span className="text-neon-blue mt-0.5 shrink-0">›</span>
                <span className="text-xs text-slate-300">{r}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="text-[10px] text-slate-600 font-mono">
        Model: {pred.modelVersion} · {new Date(pred.createdAt).toLocaleString()} · by {pred.triggeredBy}
      </div>

      <div className="flex gap-2.5 pt-3 border-t border-cyber-800/30 mt-3">
        <button
          onClick={() => onAction('download-pdf', repoId)}
          className="btn-cyber-secondary py-1.5 px-3 text-[10px] flex items-center gap-1.5 shrink-0"
        >
          <FileDown size={12} className="text-neon-blue" />
          DOWNLOAD PDF REPORT
        </button>
        <button
          onClick={() => onAction('download-excel', repoId)}
          className="btn-cyber-secondary py-1.5 px-3 text-[10px] flex items-center gap-1.5 shrink-0"
        >
          <FileSpreadsheet size={12} className="text-neon-green" />
          DOWNLOAD EXCEL REPORT
        </button>
      </div>
    </div>
  );
};

const RepositoryAIRiskAnalysisSection: React.FC<{ repoId: string }> = ({ repoId }) => {
  const { data, isLoading, isError, refetch } = useRepositoryRiskAnalysis(repoId);

  const content = isError 
    ? '{"summary":"AI repository analysis temporarily offline.","severity":"LOW","confidence":"0%","rootCause":"Connection issue","recommendations":["Retry the request."] }'
    : (data ? (typeof data === 'object' ? JSON.stringify(data) : data) : '');

  return (
    <div className="mt-4 pt-4 border-t border-cyber-800/30">
      <AICard
        title="AI Repository Cognitive Risk Analysis"
        subtitle="Generative risk intelligence compiled from commit activity and codebase features"
        content={content}
        isLoading={isLoading}
        onRetry={refetch}
      />
    </div>
  );
};

export const RepositoryDetailsDrawer: React.FC<Props> = ({ repositoryId, onClose, onAction }) => {
  const [activeTab, setActiveTab] = useState<Tab>('info');
  const { data, isLoading } = useRepositoryById(repositoryId);

  const isOpen = !!repositoryId;

  return (
    <>
      {/* Backdrop */}
      <div
        className={`fixed inset-0 bg-black/60 backdrop-blur-sm z-40 transition-opacity duration-300 ${isOpen ? 'opacity-100' : 'opacity-0 pointer-events-none'}`}
        onClick={onClose}
      />

      {/* Drawer */}
      <div className={`fixed right-0 top-0 h-screen w-[640px] max-w-full bg-cyber-950 border-l border-glass-border z-50 flex flex-col shadow-2xl transition-transform duration-300 ease-out ${isOpen ? 'translate-x-0' : 'translate-x-full'}`}>

        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-glass-border bg-cyber-900/40">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-neon-blue/10 border border-neon-blue/20 rounded-lg">
              <GitBranch size={16} className="text-neon-blue" />
            </div>
            <div>
              <h2 className="text-sm font-mono font-bold text-slate-100 truncate max-w-[300px]">
                {isLoading ? 'Loading…' : data?.repositoryName ?? 'Repository Details'}
              </h2>
              {data?.organization && (
                <p className="text-[11px] text-slate-500 font-mono">{data.organization}</p>
              )}
            </div>
          </div>
          <div className="flex items-center gap-2">
            {data && (
              <a
                href={data.repositoryUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="p-1.5 rounded text-slate-400 hover:text-neon-blue transition-colors"
              >
                <ExternalLink size={14} />
              </a>
            )}
            <button onClick={onClose} className="p-1.5 rounded text-slate-400 hover:text-slate-200 hover:bg-cyber-800 transition-all">
              <X size={16} />
            </button>
          </div>
        </div>

        {/* Quick actions bar */}
        {data && (
          <div className="flex items-center gap-2 px-6 py-3 border-b border-glass-border bg-cyber-900/20 overflow-x-auto">
            <button onClick={() => onAction('sync', data.id)} className="btn-cyber-secondary text-xs py-1.5 px-3 shrink-0">
              <RefreshCw size={12} /> Sync
            </button>
            <button onClick={() => onAction('predict', data.id)} className="btn-cyber-primary text-xs py-1.5 px-3 shrink-0">
              <Brain size={12} /> Run AI
            </button>
            <button onClick={() => onAction('edit', data.id)} className="btn-cyber-secondary text-xs py-1.5 px-3 shrink-0">
              <Settings size={12} /> Edit
            </button>
            {data.status === 'ARCHIVED' ? (
              <button onClick={() => onAction('restore', data.id)} className="btn-cyber-secondary text-xs py-1.5 px-3 shrink-0">
                <Zap size={12} /> Restore
              </button>
            ) : (
              <button onClick={() => onAction('archive', data.id)} className="btn-cyber-secondary text-xs py-1.5 px-3 shrink-0">
                <Activity size={12} /> Archive
              </button>
            )}
          </div>
        )}

        {/* Tabs */}
        <div className="flex items-center gap-1 px-6 py-3 border-b border-glass-border overflow-x-auto">
          <TabBtn label="Info" tab="info" icon={GitBranch} active={activeTab} onClick={setActiveTab} />
          <TabBtn label="GitHub PAT Telemetry" tab="github" icon={Shield} active={activeTab} onClick={setActiveTab} />
          <TabBtn label="Metrics" tab="metrics" icon={BarChart2} active={activeTab} onClick={setActiveTab} />
          <TabBtn label="Prediction" tab="prediction" icon={Brain} active={activeTab} onClick={setActiveTab} />
          <TabBtn label="History" tab="history" icon={Clock} active={activeTab} onClick={setActiveTab} />
          <TabBtn label="Activities" tab="activities" icon={Activity} active={activeTab} onClick={setActiveTab} />
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto px-6 py-5">
          {isLoading ? (
            <div className="flex flex-col items-center justify-center h-full gap-4">
              <Loader2 size={32} className="animate-spin text-neon-blue" />
              <span className="text-slate-500 font-mono text-sm">Loading repository data…</span>
            </div>
          ) : !data ? null : (
            <>
              {/* ── INFO TAB ── */}
              {activeTab === 'info' && (
                <div className="space-y-5">
                  {/* Status badges row */}
                  <div className="flex flex-wrap gap-2">
                    <Badge label={data.status} variant={data.status === 'ACTIVE' ? 'success' : 'info'} />
                    <Badge label={data.riskLevel} variant={
                      data.riskLevel === 'CRITICAL' ? 'critical' :
                      data.riskLevel === 'HIGH' ? 'high' :
                      data.riskLevel === 'MEDIUM' ? 'medium' : 'low'
                    } />
                    <Badge label={data.gitProvider} variant="info" />
                    <Badge label={data.lifecycleStage} variant="warning" />
                  </div>

                  {/* Description */}
                  {data.description && (
                    <p className="text-sm text-slate-300 leading-relaxed border-l-2 border-neon-blue/30 pl-4">
                      {data.description}
                    </p>
                  )}

                  {/* Info grid */}
                  <div className="grid grid-cols-2 gap-3">
                    {[
                      { label: 'Owner', value: data.owner ?? '—' },
                      { label: 'Organization', value: data.organization ?? '—' },
                      { label: 'Language', value: data.language ?? '—' },
                      { label: 'Technology', value: data.technology ?? '—' },
                      { label: 'Project Type', value: data.projectType ?? '—' },
                      { label: 'Visibility', value: data.visibility },
                      { label: 'License', value: data.license ?? '—' },
                      { label: 'Branch', value: data.branch },
                      { label: 'Contributors', value: data.contributors },
                      { label: 'Open Issues', value: data.openIssues },
                    ].map(({ label, value }) => (
                      <div key={label} className="bg-cyber-900/40 rounded-lg px-3 py-2.5">
                        <div className="text-[10px] font-mono text-slate-500 uppercase tracking-widest mb-1">{label}</div>
                        <div className="text-sm font-mono text-slate-200 truncate">{value}</div>
                      </div>
                    ))}
                  </div>

                  {/* Config toggles */}
                  <div>
                    <h4 className="text-[11px] font-mono font-bold uppercase tracking-widest text-slate-500 mb-3">Configuration</h4>
                    <div className="space-y-2">
                      {[
                        { label: 'Auto Prediction', value: data.autoPredictionEnabled },
                        { label: 'Notifications', value: data.notificationsEnabled },
                        { label: 'Background Sync', value: data.backgroundSyncEnabled },
                        { label: 'Report Generation', value: data.reportGenerationEnabled },
                      ].map(({ label, value }) => (
                        <div key={label} className="flex items-center justify-between py-1.5">
                          <span className="text-xs text-slate-400 font-mono">{label}</span>
                          <span className={`text-xs font-mono font-bold ${value ? 'text-neon-green' : 'text-slate-600'}`}>
                            {value ? 'ENABLED' : 'DISABLED'}
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              )}

              {/* ── GITHUB PAT TELEMETRY TAB ── */}
              {activeTab === 'github' && (
                <GitHubLiveTelemetrySection repoUrl={data.repositoryUrl} owner={data.owner ?? undefined} repo={data.repositoryName} />
              )}

              {/* ── METRICS TAB ── */}
              {activeTab === 'metrics' && (
                <div className="space-y-3">
                  {data.metrics ? (
                    <>
                      <div className="grid grid-cols-2 gap-3 mb-4">
                        {[
                          { label: 'Commits', value: data.metrics.commitCount, icon: GitCommit },
                          { label: 'Pull Requests', value: data.metrics.pullRequests, icon: GitBranch },
                          { label: 'Contributors', value: data.metrics.contributors, icon: Users },
                          { label: 'Open Issues', value: data.metrics.openIssues, icon: Bug },
                        ].map(({ label, value, icon: Icon }) => (
                          <div key={label} className="bg-cyber-900/40 rounded-xl p-4 flex items-center gap-3">
                            <Icon size={18} className="text-neon-blue shrink-0" />
                            <div>
                              <div className="text-xl font-black font-mono text-slate-100">{value}</div>
                              <div className="text-[10px] text-slate-500 uppercase">{label}</div>
                            </div>
                          </div>
                        ))}
                      </div>
                      <MetricRow label="Code Coverage" value={`${data.metrics.codeCoverage?.toFixed(1)}%`} bar={data.metrics.codeCoverage} color="text-neon-green" />
                      <MetricRow label="Build Success Rate" value={`${data.metrics.buildSuccessRate?.toFixed(1)}%`} bar={data.metrics.buildSuccessRate} color="text-neon-blue" />
                      <MetricRow label="Documentation Score" value={`${data.metrics.documentationScore?.toFixed(0)}`} bar={data.metrics.documentationScore} />
                      <MetricRow label="Commit Frequency" value={`${data.metrics.commitFrequency?.toFixed(1)}/wk`} bar={data.metrics.commitFrequency * 10} />
                      <MetricRow label="Active Contributors" value={data.metrics.activeContributors ?? 0} />
                      <MetricRow label="Merged PRs" value={data.metrics.mergedPullRequests ?? 0} color="text-neon-green" />
                      <MetricRow label="Failed PRs" value={data.metrics.failedPullRequests ?? 0} color="text-neon-pink" />
                      <MetricRow label="Inactive Days" value={data.metrics.inactiveDays ?? 0} color={data.metrics.inactiveDays > 30 ? 'text-neon-pink' : 'text-slate-400'} />
                      <MetricRow label="Bus Factor" value={data.metrics.busFactor ?? 1} color={data.metrics.busFactor <= 1 ? 'text-neon-pink' : 'text-neon-green'} />
                      <MetricRow label="Technical Debt" value={`${data.metrics.technicalDebt?.toFixed(0)}h`} color={data.metrics.technicalDebt > 100 ? 'text-neon-pink' : 'text-neon-yellow'} />
                      <MetricRow label="Cyclomatic Complexity" value={data.metrics.cyclomaticComplexity?.toFixed(1) ?? '—'} />
                      <MetricRow label="Velocity" value={`${data.metrics.velocity?.toFixed(1)} pts/wk`} color="text-neon-purple" />
                    </>
                  ) : (
                    <div className="text-center py-12 text-slate-500 font-mono text-sm">
                      No metrics available yet. Run a sync to collect data.
                    </div>
                  )}
                </div>
              )}

              {/* ── PREDICTION TAB ── */}
              {activeTab === 'prediction' && (
                <div>
                  {data.latestPrediction ? (
                    <PredictionCard pred={data.latestPrediction} repoId={data.id} onAction={onAction} />
                  ) : (
                    <div className="text-center py-8">
                      <Brain size={32} className="mx-auto text-slate-600 mb-3" />
                      <p className="text-sm font-mono text-slate-400 mb-4">No prediction recorded yet</p>
                      <button onClick={() => onAction('predict', data.id)} className="btn-cyber-primary text-xs">
                        Run AI Analysis Now
                      </button>
                    </div>
                  )}
                  <RepositoryAIRiskAnalysisSection repoId={data.id} />
                </div>
              )}

              {/* ── HISTORY TAB ── */}
              {activeTab === 'history' && (
                <div className="space-y-3">
                  <div className="text-xs font-mono text-slate-400 border-b border-cyber-800/40 pb-2 mb-3">
                    Repository Sync & Prediction Audit History
                  </div>
                  {data.lastSyncDate ? (
                    <div className="p-3 bg-cyber-900/40 rounded-lg border border-cyber-800/50">
                      <div className="flex items-center justify-between text-xs font-mono text-slate-300">
                        <span>Repository Sync Completed</span>
                        <span className="text-slate-500">{new Date(data.lastSyncDate).toLocaleString()}</span>
                      </div>
                      <div className="text-[11px] text-slate-500 font-mono mt-1">
                        Updated git metadata, contributors, and branch telemetry via GitHub PAT.
                      </div>
                    </div>
                  ) : (
                    <div className="text-xs text-slate-500 font-mono">No historical sync events found.</div>
                  )}
                </div>
              )}

              {/* ── ACTIVITIES TAB ── */}
              {activeTab === 'activities' && (
                <div className="space-y-3">
                  <div className="p-3 bg-cyber-900/40 rounded-lg border border-cyber-800/50">
                    <div className="flex items-center justify-between text-xs font-mono">
                      <span className="text-neon-blue font-bold">REPOSITORY_CREATED</span>
                      <span className="text-slate-500">{new Date(data.createdAt).toLocaleDateString()}</span>
                    </div>
                    <p className="text-[11px] text-slate-400 mt-1">Repository registered on RiskVision platform.</p>
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </>
  );
};

export default RepositoryDetailsDrawer;

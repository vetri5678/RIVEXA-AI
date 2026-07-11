import React, { useState } from 'react';
import {
  X, GitBranch, ExternalLink, RefreshCw, Brain, Activity,
  BarChart2, Clock, Settings,
  Users, GitPullRequest, Bug, Code, Zap, Loader2,
} from 'lucide-react';
import { useRepositoryById } from '../../hooks/useRepository';
import Badge from '../common/Badge';
import type { RepositoryPrediction } from '../../types/repository';

interface Props {
  repositoryId: string | null;
  onClose: () => void;
  onAction: (action: string, id: string) => void;
}

type Tab = 'info' | 'metrics' | 'prediction' | 'history' | 'activities';

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

const PredictionCard: React.FC<{ pred: RepositoryPrediction }> = ({ pred }) => {
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
                      <div className="flex items-center justify-between py-1.5">
                        <span className="text-xs text-slate-400 font-mono">Prediction Frequency</span>
                        <code className="text-xs text-neon-purple bg-neon-purple/10 px-2 py-0.5 rounded">
                          {data.predictionFrequency}
                        </code>
                      </div>
                    </div>
                  </div>
                </div>
              )}

              {/* ── METRICS TAB ── */}
              {activeTab === 'metrics' && (
                <div className="space-y-4">
                  {!data.metrics ? (
                    <div className="text-center py-12 text-slate-500 font-mono text-sm">
                      No metrics available yet. Run a sync to collect data.
                    </div>
                  ) : (
                    <>
                      <div className="grid grid-cols-2 gap-3 mb-4">
                        {[
                          { label: 'Commits', value: data.metrics.commitCount, icon: Code },
                          { label: 'Pull Requests', value: data.metrics.pullRequests, icon: GitPullRequest },
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
                      <MetricRow label="Active Contributors" value={data.metrics.activeContributors ?? 0} />
                      <MetricRow label="Merged PRs" value={data.metrics.mergedPullRequests ?? 0} color="text-neon-green" />
                      <MetricRow label="Failed PRs" value={data.metrics.failedPullRequests ?? 0} color="text-neon-pink" />
                      <MetricRow label="Inactive Days" value={data.metrics.inactiveDays ?? 0} color={data.metrics.inactiveDays > 30 ? 'text-neon-pink' : 'text-slate-400'} />
                      <MetricRow label="Bus Factor" value={data.metrics.busFactor ?? 1} color={data.metrics.busFactor <= 1 ? 'text-neon-pink' : 'text-neon-green'} />
                      <MetricRow label="Technical Debt" value={`${data.metrics.technicalDebt?.toFixed(0)}h`} color={data.metrics.technicalDebt > 100 ? 'text-neon-pink' : 'text-neon-yellow'} />
                      <MetricRow label="Cyclomatic Complexity" value={data.metrics.cyclomaticComplexity?.toFixed(1) ?? '—'} />
                      <MetricRow label="Velocity" value={`${data.metrics.velocity?.toFixed(1)} pts/wk`} color="text-neon-purple" />
                    </>
                  )}
                </div>
              )}

              {/* ── PREDICTION TAB ── */}
              {activeTab === 'prediction' && (
                <div>
                  {!data.latestPrediction ? (
                    <div className="text-center py-12">
                      <Brain size={40} className="mx-auto text-slate-700 mb-4" />
                      <p className="text-slate-500 font-mono text-sm">No prediction run yet.</p>
                      <button
                        onClick={() => onAction('predict', data.id)}
                        className="btn-cyber-primary mt-4 text-xs"
                      >
                        <Brain size={12} /> Run First Prediction
                      </button>
                    </div>
                  ) : (
                    <PredictionCard pred={data.latestPrediction} />
                  )}
                </div>
              )}

              {/* ── HISTORY TAB ── */}
              {activeTab === 'history' && (
                <div className="space-y-3">
                  {(!data.predictionHistory || data.predictionHistory.length === 0) ? (
                    <div className="text-center py-12 text-slate-500 font-mono text-sm">
                      No prediction history available.
                    </div>
                  ) : data.predictionHistory.map((pred, i) => (
                    <div key={pred.id} className={`border rounded-lg px-4 py-3 ${
                      pred.riskLevel === 'CRITICAL' ? 'border-neon-pink/20 bg-neon-pink/5' :
                      pred.riskLevel === 'HIGH' ? 'border-orange-500/20 bg-orange-500/5' :
                      pred.riskLevel === 'MEDIUM' ? 'border-yellow-500/20 bg-yellow-500/5' :
                      'border-neon-green/20 bg-neon-green/5'
                    }`}>
                      <div className="flex items-center justify-between mb-2">
                        <div className="flex items-center gap-2">
                          {i === 0 && <span className="text-[10px] bg-neon-blue/10 text-neon-blue border border-neon-blue/20 px-1.5 py-0.5 rounded font-mono">LATEST</span>}
                          <Badge label={pred.riskLevel} />
                        </div>
                        <span className="text-[10px] text-slate-500 font-mono">
                          {new Date(pred.createdAt).toLocaleString()}
                        </span>
                      </div>
                      <div className="flex items-center gap-4 text-xs font-mono">
                        <span className="text-slate-400">Failure: <span className="font-bold text-slate-200">{(pred.failureProbability * 100).toFixed(1)}%</span></span>
                        <span className="text-slate-400">Confidence: <span className="font-bold text-slate-200">{(pred.confidence * 100).toFixed(0)}%</span></span>
                        <span className="text-slate-400">Health: <span className="font-bold text-slate-200">{pred.healthScore?.toFixed(0)}</span></span>
                      </div>
                    </div>
                  ))}
                </div>
              )}

              {/* ── ACTIVITIES TAB ── */}
              {activeTab === 'activities' && (
                <div className="space-y-2">
                  {(!data.recentActivities || data.recentActivities.length === 0) ? (
                    <div className="text-center py-12 text-slate-500 font-mono text-sm">
                      No activity recorded yet.
                    </div>
                  ) : data.recentActivities.map(activity => (
                    <div key={activity.id} className="flex items-start gap-3 py-3 border-b border-cyber-800/30 last:border-0">
                      <div className={`w-1.5 h-1.5 rounded-full mt-1.5 shrink-0 ${
                        activity.severity === 'ERROR' ? 'bg-neon-pink' :
                        activity.severity === 'WARNING' ? 'bg-neon-yellow' : 'bg-neon-green'
                      }`} />
                      <div className="flex-1 min-w-0">
                        <p className="text-xs text-slate-200 font-mono">{activity.description}</p>
                        <div className="flex items-center gap-3 mt-1">
                          <span className="text-[10px] text-slate-500">{activity.actor}</span>
                          <span className="text-[10px] text-neon-blue/70 font-mono">{activity.action}</span>
                        </div>
                      </div>
                      <span className="text-[10px] text-slate-600 shrink-0">
                        {new Date(activity.createdAt).toLocaleString()}
                      </span>
                    </div>
                  ))}
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

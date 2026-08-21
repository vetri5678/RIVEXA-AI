import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import DashboardLayout from '../components/layout/DashboardLayout';
import SystemHealthWidget from '../components/dashboard/SystemHealth/SystemHealthWidget';
import GraveyardIndexWidget from '../components/dashboard/GraveyardIndex/GraveyardIndexWidget';
import RiskDistributionWidget from '../components/dashboard/RiskDistribution/RiskDistributionWidget';
import PredictionTimelineWidget from '../components/dashboard/PredictionTimeline/PredictionTimelineWidget';
import RepositoryHealthWidget from '../components/dashboard/RepositoryHealth/RepositoryHealthWidget';
import ExplainableAIWidget from '../components/dashboard/ExplainableAI/ExplainableAIWidget';
import AIInsightsWidget from '../components/dashboard/AIInsights/AIInsightsWidget';
import ExecutiveSummaryWidget from '../components/dashboard/ExecutiveSummary/ExecutiveSummaryWidget';
import ForecastWidget from '../components/dashboard/Forecast/ForecastWidget';
import AlertsWidget from '../components/dashboard/Alerts/AlertsWidget';
import RecommendationsWidget from '../components/dashboard/Recommendations/RecommendationsWidget';
import TeamAnalyticsWidget from '../components/dashboard/TeamAnalytics/TeamAnalyticsWidget';
import ActivityFeedWidget from '../components/dashboard/ActivityFeed/ActivityFeedWidget';
import ExportCenterWidget from '../components/dashboard/ExportCenter/ExportCenterWidget';
import FloatingAIAssistantWidget from '../components/dashboard/FloatingAIAssistant/FloatingAIAssistantWidget';
import PredictionPipelineWidget from '../components/dashboard/PredictionPipeline/PredictionPipelineWidget';
import ProjectLifecycleWidget from '../components/dashboard/ProjectLifecycle/ProjectLifecycleWidget';
import RiskHeatmapWidget from '../components/dashboard/RiskHeatmap/RiskHeatmapWidget';
import ActivityMonitorWidget from '../components/dashboard/ActivityMonitor/ActivityMonitorWidget';
import ExplainPredictionModal from '../components/dashboard/ExplainableAI/ExplainPredictionModal';
import ModelEngineMetricsWidget from '../components/dashboard/ModelEngine/ModelEngineMetricsWidget';
import RunPredictionModal from '../components/dashboard/Modals/RunPredictionModal';
import { getStoredUser, isAdminUser, getConnectGitHubUrl } from '../utils/auth';

import {
  useOverview,
  useRepositoryAssessmentMutation,
  useSystemStatus,
  useGitHubUrlPredictionMutation,
} from '../hooks/useDashboard';
import { useGithubConnectionStatus } from '../hooks/useRepository';
import { useMLVersion, useMLHealth } from '../hooks/useMLPrediction';

import { ShieldCheck, Activity, Brain, AlertOctagon, TrendingUp, Layers, CheckCircle2, ArrowRight } from 'lucide-react';

const GithubIcon: React.FC<{ size?: number; className?: string }> = ({ size = 24, className }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}>
    <path d="M15 22v-4a4.8 4.8 0 0 0-1-3.5c3 0 6-2 6-5.5.08-1.25-.27-2.48-1-3.5.28-1.15.28-2.35 0-3.5 0 0-1 0-3 1.5-2.64-.5-5.36-.5-8 0C6 2 5 2 5 2c-.3 1.15-.3 2.35 0 3.5A5.403 5.403 0 0 0 4 9c0 3.5 3 5.5 6 5.5-.39.49-.68 1.05-.85 1.65-.17.6-.22 1.23-.15 1.85v4" />
    <path d="M9 18c-4.51 2-5-2-7-2" />
  </svg>
);

export const Dashboard: React.FC = () => {
  const navigate = useNavigate();
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedProject, setSelectedProject] = useState<string | null>(null);
  const [isPredictionModalOpen, setIsPredictionModalOpen] = useState(false);

  const currentUser = getStoredUser();
  const isAdmin = isAdminUser(currentUser);

  const { data: overview, isLoading: overviewLoading } = useOverview();
  const { data: systemStatus } = useSystemStatus();
  const { data: mlVersion } = useMLVersion();
  const { data: mlHealth } = useMLHealth();
  const assessmentMutation = useRepositoryAssessmentMutation();
  const githubUrlMutation = useGitHubUrlPredictionMutation();

  // ─── GitHub connection gate ────────────────────────────────────────────────
  // Check if GitHub is connected for the current logged-in user
  const { data: githubConnection, isLoading: githubLoading } = useGithubConnectionStatus();
  const totalRepos = overview?.total_projects ?? githubConnection?.repositoryCount ?? 0;
  const isGitHubConnected: boolean = githubConnection?.connected === true || totalRepos > 0;
  const showGithubWarning: boolean = !githubLoading && !overviewLoading && !isGitHubConnected && overview?.github_required !== false;

  // Dynamic header badge values from API
  const activeModelTag = mlVersion?.modelVersion
    ? `${mlVersion.modelName ?? 'XGBoost'} v${mlVersion.modelVersion}`
    : 'Model Loading...';
  const backendLatencyMs = systemStatus?.services?.find((s: any) => s.name === 'Backend API')?.latency_ms;
  const latencyLabel = backendLatencyMs !== undefined ? `${backendLatencyMs}ms Nominal` : (mlHealth?.status === 'healthy' ? 'ML Online' : 'ML Offline');

  /**
   * Resolve the specific user-facing message from an axios error response.
   * Provides contextual feedback instead of the generic fallback.
   */
  const resolveAssessmentError = (error: any): string => {
    // Client-side validation errors thrown before the request
    if (error?.message && !error?.response) {
      return error.message;
    }

    const status: number = error?.response?.status;
    const serverMsg: string =
      error?.response?.data?.message ||
      error?.response?.data?.error ||
      error?.response?.data?.detail ||
      '';

    if (status === 400) {
      if (serverMsg) return `Validation error: ${serverMsg}`;
      return 'Invalid repository parameters — check the UUID format.';
    }
    if (status === 401 || status === 403) {
      return 'GitHub authentication failed — your access token may be expired or missing.';
    }
    if (status === 404) {
      return `Repository not found. Verify the UUID exists in the system.`;
    }
    if (status === 503 || error?.response?.data?.type === 'BACKEND_UNAVAILABLE') {
      return 'FastAPI ML service is unavailable — ensure the Python backend is running on port 5000.';
    }
    if (status === 408 || error?.response?.data?.type === 'TIMEOUT') {
      return 'Assessment request timed out — the ML pipeline may be overloaded.';
    }
    if (status >= 500) {
      return `Server error (${status}): ${serverMsg || 'Internal prediction engine failure — check backend logs.'}`;
    }
    if (serverMsg) return serverMsg;
    return error?.message || 'Assessment failed — verify the backend services are running.';
  };

  const handleQuickAction = async (action: string) => {
    if (action === 'predict') {
      // Navigate to the dedicated Run Prediction page instead of showing a modal
      navigate('/prediction/run');
    }
  };

  const handleRunPredictionSelect = async (repositoryId: string) => {
    // Client-side UUID format validation before hitting the network
    const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    if (!uuidRegex.test(repositoryId)) {
      alert(
        `Invalid UUID format: "${repositoryId}"\n\nExpected format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`
      );
      return;
    }

    try {
      const result = await assessmentMutation.mutateAsync(repositoryId);
      const riskLevel: string = result?.riskLevel || result?.risk_level || 'UNKNOWN';
      const prob: number = result?.failureProbability ?? result?.failure_probability ?? 0;
      const probPct = (prob * 100).toFixed(1);
      setIsPredictionModalOpen(false);
      alert(
        `✅ Assessment completed for repository:\n${repositoryId}\n\n` +
        `Risk Level: ${riskLevel}\n` +
        `Failure Probability: ${probPct}%\n\n` +
        `Dashboard telemetry has been refreshed.`
      );
    } catch (error: any) {
      const friendlyMessage = resolveAssessmentError(error);
      console.error('[Dashboard] Assessment failed:', {
        repositoryId,
        status: error?.response?.status,
        data: error?.response?.data,
        error: error
      });
      alert(`❌ Assessment Failed\n\n${friendlyMessage}`);
    }
  };

  /**
   * GitHub-native path: user pastes a URL, backend resolves/creates the repo and runs prediction.
   */
  const handleRunPredictionByUrl = async (githubUrl: string) => {
    try {
      const result = await githubUrlMutation.mutateAsync(githubUrl);
      const riskLevel: string = result?.riskLevel || 'UNKNOWN';
      const prob: number = result?.failureProbability ?? 0;
      const probPct = (prob * 100).toFixed(1);
      setIsPredictionModalOpen(false);
      alert(
        `✅ Assessment completed for:\n${result?.repositoryName || githubUrl}\n${result?.repositoryUrl || githubUrl}\n\n` +
        `Risk Level: ${riskLevel}\n` +
        `Failure Probability: ${probPct}%\n` +
        `Health Score: ${result?.healthScore?.toFixed(1) ?? 'N/A'}\n\n` +
        `Dashboard telemetry has been refreshed.`
      );
    } catch (error: any) {
      const friendlyMessage = resolveAssessmentError(error);
      console.error('[Dashboard] GitHub URL prediction failed:', {
        githubUrl,
        status: error?.response?.status,
        data: error?.response?.data,
        error: error
      });
      alert(`❌ Prediction Failed\n\n${friendlyMessage}`);
    }
  };

  return (
    <DashboardLayout
      onSearchChange={setSearchTerm}
      searchValue={searchTerm}
      onQuickAction={handleQuickAction}
    >
      {/* Enterprise AI Operating System Hero Banner */}
      <div className="glass-strong rounded-2xl p-6 mb-8 border border-white/[0.08] relative overflow-hidden shadow-2xl">
        {/* Background Ambient Glow */}
        <div className="absolute -top-24 -right-24 w-96 h-96 rounded-full bg-blue-500/10 blur-3xl pointer-events-none" />
        <div className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-cyan-400/40 to-transparent" />

        <div className="flex flex-col lg:flex-row items-start lg:items-center justify-between gap-6 relative z-10">
          <div className="flex items-start gap-4">
            <div className="p-3.5 rounded-2xl bg-gradient-to-br from-blue-500/20 to-cyan-500/10 border border-blue-500/30 text-cyan-400 shrink-0 shadow-[0_0_20px_rgba(56,189,248,0.2)]">
              <ShieldCheck size={32} />
            </div>
            <div>
              <div className="flex items-center gap-3 flex-wrap">
                <h1 className="text-2xl font-extrabold tracking-tight text-white font-sans">
                  RIVEXA <span className="text-transparent bg-clip-text bg-gradient-to-r from-blue-400 to-cyan-400">Command Center</span>
                </h1>
                <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-mono font-bold bg-emerald-500/15 text-emerald-400 border border-emerald-500/30">
                  <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
                  Live AI Telemetry Active
                </span>
              </div>
              <p className="text-xs text-slate-400 font-sans mt-1.5 max-w-2xl leading-relaxed">
                Enterprise predictive risk intelligence, SHAP explainable failure forecasting, and real-time repository health analytics across all software projects.
              </p>
            </div>
          </div>

          <div className="flex items-center gap-3 shrink-0 self-end lg:self-center">
            <div className="px-4 py-2 rounded-xl bg-white/[0.04] border border-white/[0.08] text-right">
              <span className="text-[10px] font-mono text-slate-400 uppercase block">Active Model</span>
              <span className="text-xs font-bold text-cyan-400 font-mono">{activeModelTag}</span>
            </div>
            <div className="px-4 py-2 rounded-xl bg-white/[0.04] border border-white/[0.08] text-right">
              <span className="text-[10px] font-mono text-slate-400 uppercase block">System Latency</span>
              <span className="text-xs font-bold text-emerald-400 font-mono">{latencyLabel}</span>
            </div>
          </div>
        </div>
      </div>

      {/* ─── GitHub Not Connected Banner ──────────────────────────────────────── */}
      {showGithubWarning && (
        <div className="mb-6 rounded-2xl border border-amber-500/30 bg-amber-500/5 p-5 flex flex-col sm:flex-row items-start sm:items-center gap-4 shadow-lg relative overflow-hidden">
          <div className="absolute inset-0 bg-gradient-to-r from-amber-500/5 via-transparent to-transparent pointer-events-none" />
          <div className="p-3 rounded-xl bg-amber-500/10 border border-amber-500/20 text-amber-400 shrink-0">
            <GithubIcon size={24} />
          </div>
          <div className="flex-1 relative z-10">
            <h3 className="text-sm font-bold text-amber-300 font-sans">GitHub Integration Required</h3>
            <p className="text-xs text-slate-400 mt-0.5 leading-relaxed">
              Dashboard analytics require a connected GitHub account. Connect your GitHub to see your
              repository risk scores, failure index, org health, and AI-driven insights.
            </p>
          </div>
          <button
            id="connect-github-btn"
            onClick={() => {
              window.location.href = getConnectGitHubUrl();
            }}
            className="flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold font-mono bg-amber-500/15 border border-amber-500/30 text-amber-300 hover:bg-amber-500/25 transition-all whitespace-nowrap shrink-0 relative z-10 cursor-pointer"
          >
            Connect GitHub <ArrowRight size={14} />
          </button>
        </div>
      )}

      {/* Futuristic Metric KPI Grid (Responsive 1-7 Columns) */}
      <div className={`grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 lg:grid-cols-7 gap-3 sm:gap-3.5 mb-8 ${showGithubWarning ? 'opacity-40 pointer-events-none select-none' : ''}`}>
        {/* KPI 1: Risk Index */}
        <div className="metric-card">
          <div className="flex items-center justify-between text-slate-400 mb-2">
            <span className="text-[10px] font-mono font-bold uppercase tracking-wider">Failure Index</span>
            <AlertOctagon size={14} className="text-red-400" />
          </div>
          <span className="text-2xl font-extrabold text-red-400 font-mono block">
            {overviewLoading ? '...' : (showGithubWarning ? '—' : overview?.graveyard_index)}
          </span>
          <span className="text-[10px] text-slate-500 font-sans mt-1 block">Org Failure Hazard</span>
        </div>

        {/* KPI 2: Org Health */}
        <div className="metric-card">
          <div className="flex items-center justify-between text-slate-400 mb-2">
            <span className="text-[10px] font-mono font-bold uppercase tracking-wider">Org Health</span>
            <CheckCircle2 size={14} className="text-emerald-400" />
          </div>
          <span className="text-2xl font-extrabold text-emerald-400 font-mono block">
            {overviewLoading ? '...' : (showGithubWarning ? '—' : `${overview?.health_score}%`)}
          </span>
          <span className="text-[10px] text-emerald-500/80 font-sans mt-1 block">{showGithubWarning ? 'No data' : '↑ +2.4% vs last week'}</span>
        </div>

        {/* KPI 3: Confidence */}
        <div className="metric-card">
          <div className="flex items-center justify-between text-slate-400 mb-2">
            <span className="text-[10px] font-mono font-bold uppercase tracking-wider">AI Confidence</span>
            <Brain size={14} className="text-blue-400" />
          </div>
          <span className="text-2xl font-extrabold text-blue-400 font-mono block">
            {overviewLoading ? '...' : (showGithubWarning ? '—' : (() => {
              const raw = overview?.avg_confidence;
              if (raw == null) return '—';
              // Normalize: if value > 1 it's already a percentage (e.g. 93.0), else decimal (0.93)
              const pct = raw > 1 ? raw : raw * 100;
              return `${Math.min(100, Math.max(0, pct)).toFixed(0)}%`;
            })())}
          </span>
          <span className="text-[10px] text-slate-500 font-sans mt-1 block">SHAP Verified</span>
        </div>

        {/* KPI 4: Monitored Repos */}
        <div className="metric-card">
          <div className="flex items-center justify-between text-slate-400 mb-2">
            <span className="text-[10px] font-mono font-bold uppercase tracking-wider">Active Repos</span>
            <Layers size={14} className="text-slate-400" />
          </div>
          <span className="text-2xl font-extrabold text-white font-mono block">
            {overviewLoading ? '...' : (showGithubWarning ? '—' : overview?.total_projects)}
          </span>
          <span className="text-[10px] text-slate-500 font-sans mt-1 block">Connected Systems</span>
        </div>

        {/* KPI 5: Critical Repos */}
        <div className="metric-card">
          <div className="flex items-center justify-between text-slate-400 mb-2">
            <span className="text-[10px] font-mono font-bold uppercase tracking-wider">High Risk</span>
            <AlertOctagon size={14} className="text-orange-400" />
          </div>
          <span className="text-2xl font-extrabold text-orange-400 font-mono block">
            {overviewLoading ? '...' : (showGithubWarning ? '—' : overview?.critical_projects)}
          </span>
          <span className="text-[10px] text-orange-400/80 font-sans mt-1 block">Action Required</span>
        </div>

        {/* KPI 6: Predictions Today */}
        <div className="metric-card">
          <div className="flex items-center justify-between text-slate-400 mb-2">
            <span className="text-[10px] font-mono font-bold uppercase tracking-wider">Runs Today</span>
            <TrendingUp size={14} className="text-cyan-400" />
          </div>
          <span className="text-2xl font-extrabold text-cyan-400 font-mono block">
            {overviewLoading ? '...' : (showGithubWarning ? '—' : overview?.predictions_today)}
          </span>
          <span className="text-[10px] text-cyan-400/80 font-sans mt-1 block">Inference Operations</span>
        </div>

        {/* KPI 7: System State */}
        <div className="metric-card flex flex-col justify-between">
          <div className="flex items-center justify-between text-slate-400">
            <span className="text-[10px] font-mono font-bold uppercase tracking-wider">System State</span>
            <Activity size={14} className="text-emerald-400 animate-pulse" />
          </div>
          <div className="mt-2">
            <span className="text-xs font-bold text-emerald-400 uppercase font-mono block">Nominal</span>
            <span className="text-[10px] text-slate-500 font-sans block mt-0.5">100% Uptime</span>
          </div>
        </div>
      </div>

      {/* Main Grid: modular widgets layout */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mb-6">
        <SystemHealthWidget />
        <GraveyardIndexWidget />
        <RiskDistributionWidget />
      </div>

      {/* ML Model Performance Metrics — Live from RandomForest evaluation */}
      <div className="mb-6">
        <ModelEngineMetricsWidget />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-6">
        <div className="lg:col-span-2">
          <PredictionTimelineWidget />
        </div>
        <ExplainableAIWidget />
      </div>

      {/* Pipeline & Timeline modules */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
        <PredictionPipelineWidget />
        <ProjectLifecycleWidget />
      </div>

      {/* Heatmap & Activity Monitor */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
        <RiskHeatmapWidget />
        <ActivityMonitorWidget />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-6">
        <div className="lg:col-span-2">
          <RepositoryHealthWidget
            searchTerm={searchTerm}
            onSelectProject={(id) => setSelectedProject(id)}
          />
        </div>
        <AIInsightsWidget />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-6">
        <div className="lg:col-span-2">
          <ExecutiveSummaryWidget />
        </div>
        <ForecastWidget />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-6">
        <AlertsWidget />
        <RecommendationsWidget />
        <TeamAnalyticsWidget />
      </div>

      {isAdmin ? (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-6">
          <div className="lg:col-span-2">
            <ActivityFeedWidget />
          </div>
          <ExportCenterWidget />
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-6 mb-6">
          <ExportCenterWidget />
        </div>
      )}

      <ExplainPredictionModal
        projectId={selectedProject}
        onClose={() => setSelectedProject(null)}
      />

      <RunPredictionModal
        isOpen={isPredictionModalOpen}
        onClose={() => setIsPredictionModalOpen(false)}
        onSelect={handleRunPredictionSelect}
        onGithubUrl={handleRunPredictionByUrl}
        isSubmitting={assessmentMutation.isPending || githubUrlMutation.isPending}
      />

      <FloatingAIAssistantWidget />
    </DashboardLayout>
  );
};

export default Dashboard;

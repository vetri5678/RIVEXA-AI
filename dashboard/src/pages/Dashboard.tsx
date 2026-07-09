import React, { useState } from 'react';
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

import {
  useOverview,
  usePredictMutation,
} from '../hooks/useDashboard';

import { BrainCircuit } from 'lucide-react';

export const Dashboard: React.FC = () => {
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedProject, setSelectedProject] = useState<string | null>(null);

  const { data: overview, isLoading: overviewLoading } = useOverview();
  const predictMutation = usePredictMutation();

  const handleQuickAction = async (action: string) => {
    if (action === 'predict') {
      const repoId = prompt('Enter Repository external ID to assess:');
      if (!repoId) return;

      try {
        await predictMutation.mutateAsync({
          project_id: repoId,
          budget: 500000.0,
          timeline_months: 12.0,
          team_size: 5.0,
          status: 'active',
          total_requirements: 80.0,
          total_tasks: 240.0,
        });
        alert(`Assessment completed for ${repoId}. Telemetry logs re-indexed.`);
      } catch (e) {
        alert('Assessment command failed. Verify repository parameters.');
      }
    }
  };

  return (
    <DashboardLayout
      onSearchChange={setSearchTerm}
      searchValue={searchTerm}
      onQuickAction={handleQuickAction}
    >
      {/* Hero Cyber Header Banner */}
      <div className="relative overflow-hidden border border-slate-800 bg-cyber-900/60 p-6 rounded-lg mb-6 flex flex-col md:flex-row items-start md:items-center justify-between gap-4 font-mono shadow-2xl">
        <div className="absolute top-0 left-0 w-24 h-24 bg-neon-blue/5 rounded-full blur-3xl pointer-events-none" />
        <div className="absolute bottom-0 right-0 w-24 h-24 bg-neon-pink/5 rounded-full blur-3xl pointer-events-none" />
        
        <div className="flex items-center gap-4">
          <div className="p-3 bg-neon-blue/10 border border-neon-blue/20 rounded-lg text-neon-blue shrink-0 animate-pulse-slow">
            <BrainCircuit size={28} />
          </div>
          <div>
            <h1 className="text-lg font-black tracking-wider text-slate-100 uppercase glow-text-blue">
              GRAVEYARD ANALYZER
            </h1>
            <p className="text-xs text-slate-400 font-bold uppercase tracking-widest mt-0.5">
              AI Software Project Intelligence Center — "Predict software project failure before it happens using Explainable Artificial Intelligence."
            </p>
          </div>
        </div>
      </div>

      {/* Cyber Ops Headline KPI Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-7 gap-4 mb-6 font-mono text-[10px]">
        {/* KPI 1: Graveyard Index */}
        <div className="bg-cyber-900/80 border border-slate-800 rounded p-3 text-left">
          <span className="text-slate-500 uppercase block tracking-wider">Graveyard Index</span>
          <span className="text-xl font-bold text-neon-pink block mt-1">
            {overviewLoading ? '...' : overview?.graveyard_index}
          </span>
        </div>

        {/* KPI 2: Organization Health */}
        <div className="bg-cyber-900/80 border border-slate-800 rounded p-3 text-left">
          <span className="text-slate-500 uppercase block tracking-wider">Org Health</span>
          <span className="text-xl font-bold text-neon-green block mt-1">
            {overviewLoading ? '...' : `${overview?.health_score}%`}
          </span>
        </div>

        {/* KPI 3: Prediction Confidence */}
        <div className="bg-cyber-900/80 border border-slate-800 rounded p-3 text-left">
          <span className="text-slate-500 uppercase block tracking-wider">Prediction Conf</span>
          <span className="text-xl font-bold text-neon-blue block mt-1">
            {overviewLoading ? '...' : `${(overview?.avg_confidence || 0 * 100).toFixed(0)}%`}
          </span>
        </div>

        {/* KPI 4: Observed Repos */}
        <div className="bg-cyber-900/80 border border-slate-800 rounded p-3 text-left">
          <span className="text-slate-500 uppercase block tracking-wider">Observed Repos</span>
          <span className="text-xl font-bold text-slate-200 block mt-1">
            {overviewLoading ? '...' : overview?.total_projects}
          </span>
        </div>

        {/* KPI 5: Critical projects count */}
        <div className="bg-cyber-900/80 border border-slate-800 rounded p-3 text-left">
          <span className="text-slate-500 uppercase block tracking-wider">Critical Repos</span>
          <span className="text-xl font-bold text-neon-pink block mt-1">
            {overviewLoading ? '...' : overview?.critical_projects}
          </span>
        </div>

        {/* KPI 6: Predictions Today */}
        <div className="bg-cyber-900/80 border border-slate-800 rounded p-3 text-left">
          <span className="text-slate-500 uppercase block tracking-wider">Predictions Today</span>
          <span className="text-xl font-bold text-neon-purple block mt-1">
            {overviewLoading ? '...' : overview?.predictions_today}
          </span>
        </div>

        {/* KPI 7: Telemetry status */}
        <div className="bg-cyber-900/80 border border-slate-800 rounded p-3 text-left flex items-center justify-between">
          <div>
            <span className="text-slate-500 uppercase block tracking-wider">System State</span>
            <span className="text-neon-green font-bold block mt-1 uppercase">NOMINAL</span>
          </div>
          <span className="w-1.5 h-1.5 rounded-full bg-neon-green animate-pulse shadow-[0_0_8px_#00ff88]" />
        </div>
      </div>

      {/* Main Grid: modular widgets layout */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mb-6">
        <SystemHealthWidget />
        <GraveyardIndexWidget />
        <RiskDistributionWidget />
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
        <ExecutiveSummaryWidget />
        <ForecastWidget />
        <AlertsWidget />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-6">
        <RecommendationsWidget />
        <TeamAnalyticsWidget />
        <ActivityFeedWidget />
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-6">
        <ExportCenterWidget />
      </div>

      {/* Floating Copilot module */}
      <FloatingAIAssistantWidget />

      {/* Explainer detailed modal */}
      <ExplainPredictionModal
        projectId={selectedProject}
        onClose={() => setSelectedProject(null)}
      />
    </DashboardLayout>
  );
};
export default Dashboard;

import { mlApiClient, apiClient } from './client';
import type {
  SystemStatusResponse,
  GraveyardIndexResponse,
  OrgHealthResponse,
  DashboardOverviewResponse,
  RiskDistributionResponse,
  PredictionSummaryResponse,
  RepositoryRankingResponse,
  HighRiskProjectsResponse,
  FeatureImportanceResponse,
  PredictionTimelineResponse,
  RecommendationsResponse,
  AlertsResponse,
  ModelInfoResponse,
  ActivityResponse,
  ForecastResponse,
  ExecutiveSummaryResponse,
  AIInsightsResponse,
  ExportResponse,
  PipelineLifecycleResponse,
  ProjectLifecycleCountsResponse,
  RiskHeatmapResponse,
} from '../types/dashboard';

export const dashboardApi = {
  getSystemStatus: async (): Promise<SystemStatusResponse> => {
    const { data } = await mlApiClient.get('/dashboard/system-status');
    return data;
  },

  getOverview: async (): Promise<DashboardOverviewResponse> => {
    const { data } = await mlApiClient.get('/dashboard/overview');
    return data;
  },

  getGraveyardIndex: async (): Promise<GraveyardIndexResponse> => {
    const { data } = await mlApiClient.get('/dashboard/graveyard-index');
    return data;
  },

  getOrgHealth: async (): Promise<OrgHealthResponse> => {
    const { data } = await mlApiClient.get('/dashboard/org-health');
    return data;
  },

  getRiskDistribution: async (): Promise<RiskDistributionResponse> => {
    const { data } = await mlApiClient.get('/dashboard/risk-distribution');
    return data;
  },

  getPredictionSummary: async (): Promise<PredictionSummaryResponse> => {
    const { data } = await mlApiClient.get('/dashboard/prediction-summary');
    return data;
  },

  getRepositoryRanking: async (params: {
    page?: number;
    page_size?: number;
    search?: string;
    risk_level?: string;
    sort_by?: string;
    sort_desc?: boolean;
  }): Promise<RepositoryRankingResponse> => {
    const { data } = await mlApiClient.get('/dashboard/repository-ranking', { params });
    return data;
  },

  getHighRiskProjects: async (limit = 10): Promise<HighRiskProjectsResponse> => {
    const { data } = await mlApiClient.get('/dashboard/high-risk-projects', { params: { limit } });
    return data;
  },

  getFeatureImportance: async (): Promise<FeatureImportanceResponse> => {
    const { data } = await mlApiClient.get('/dashboard/feature-importance');
    return data;
  },

  getPredictionTimeline: async (granularity = 'daily'): Promise<PredictionTimelineResponse> => {
    const { data } = await mlApiClient.get('/dashboard/prediction-timeline', { params: { granularity } });
    return data;
  },

  getRecommendations: async (): Promise<RecommendationsResponse> => {
    const { data } = await mlApiClient.get('/dashboard/recommendations');
    return data;
  },

  getAlerts: async (): Promise<AlertsResponse> => {
    const { data } = await mlApiClient.get('/dashboard/alerts');
    return data;
  },

  getModelInfo: async (): Promise<ModelInfoResponse> => {
    const { data } = await mlApiClient.get('/dashboard/model-info');
    return data;
  },

  getActivity: async (limit = 50): Promise<ActivityResponse> => {
    const { data } = await mlApiClient.get('/dashboard/activity', { params: { limit } });
    return data;
  },

  getForecast: async (): Promise<ForecastResponse> => {
    const { data } = await mlApiClient.get('/dashboard/forecast');
    return data;
  },

  getExecutiveSummary: async (): Promise<ExecutiveSummaryResponse> => {
    const { data } = await mlApiClient.get('/dashboard/executive-summary');
    return data;
  },

  getAIInsights: async (limit = 10): Promise<AIInsightsResponse> => {
    const { data } = await mlApiClient.get('/dashboard/ai-insights', { params: { limit } });
    return data;
  },

  exportReport: async (payload: {
    format: 'pdf' | 'excel' | 'csv' | 'json';
    report_type: 'executive' | 'predictions' | 'full';
    date_from?: string;
    date_to?: string;
  }): Promise<ExportResponse> => {
    const { data } = await apiClient.post('/dashboard/export', payload);
    return data;
  },

  // Predict repository via FastAPI ML pipeline (accepts full project payload)
  // Endpoint: POST /api/v1/pipeline/predict — defined in api/routes.py
  predictProject: async (payload: any) => {
    if (!payload || !payload.project_id) {
      throw new Error('Repository project_id is required for assessment');
    }
    const { data } = await mlApiClient.post('/pipeline/predict', payload);
    return data;
  },

  // Run assessment via Spring Boot repository prediction (UUID-based, calls FastAPI internally)
  // Endpoint: POST /api/v1/repositories/{id}/predict
  runRepositoryAssessment: async (repositoryId: string) => {
    if (!repositoryId || !repositoryId.trim()) {
      throw new Error('Repository ID is required to run assessment');
    }
    // Validate UUID format before sending to avoid confusing 400 errors
    const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    if (!uuidRegex.test(repositoryId.trim())) {
      throw new Error(`Invalid repository UUID format: "${repositoryId}". Expected format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`);
    }
    const { data } = await apiClient.post(`/repositories/${repositoryId.trim()}/predict`);
    return data;
  },

  // ── Dedicated Prediction Workflow Endpoints ────────────────────────────────

  /**
   * Run a full AI prediction for the given repository.
   * Endpoint: POST /api/v1/predictions/run
   * Body: { repositoryId: string (UUID) }
   * Returns a PredictionResultResponse with full risk metrics, SHAP data, and repo info.
   */
  runPrediction: async (repositoryId: string): Promise<any> => {
    if (!repositoryId || !repositoryId.trim()) {
      throw new Error('Repository ID is required to run prediction');
    }
    const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
    if (!uuidRegex.test(repositoryId.trim())) {
      throw new Error(`Invalid repository UUID format: "${repositoryId}". Expected: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`);
    }
    const { data } = await apiClient.post('/predictions/run', { repositoryId: repositoryId.trim() });
    return data;
  },

  /**
   * Fetch a stored prediction result by its prediction UUID.
   * Endpoint: GET /api/v1/predictions/{id}
   * Returns a PredictionResultResponse including repository metadata.
   */
  getPrediction: async (predictionId: string): Promise<any> => {
    if (!predictionId || !predictionId.trim()) {
      throw new Error('Prediction ID is required');
    }
    const { data } = await apiClient.get(`/predictions/${predictionId.trim()}`);
    return data;
  },

  // Retrain model quick action
  retrainModel: async (payload: any) => {
    const { data } = await mlApiClient.post('/retraining/train', payload);
    return data;
  },

  // OpenRouter AI Integration APIs
  getTelemetryAnalysis: async (): Promise<any> => {
    const { data } = await mlApiClient.get('/ai/telemetry-analysis');
    return data;
  },

  explainEvent: async (payload: any): Promise<any> => {
    const { data } = await mlApiClient.post('/ai/explain-event', payload);
    return data;
  },

  getRepositoryRiskAnalysis: async (repoId: string): Promise<any> => {
    const { data } = await mlApiClient.get(`/ai/repository/${repoId}/risk-analysis`);
    return data;
  },

  clearAICache: async (): Promise<any> => {
    const { data } = await mlApiClient.post('/ai/cache/clear');
    return data;
  },

  // Audit & Event Log APIs
  getAuditLogs: async (page = 0, size = 20): Promise<any> => {
    const { data } = await mlApiClient.get('/audit/logs', { params: { page, size } });
    return data;
  },

  getAuditStatistics: async (): Promise<any> => {
    const { data } = await mlApiClient.get('/audit/statistics');
    return data;
  },

  filterAuditLogs: async (filters: any): Promise<any> => {
    const { data } = await mlApiClient.post('/audit/filter', filters);
    return data;
  },

  // Telemetry APIs
  getTelemetryCurrent: async (): Promise<any> => {
    const { data } = await mlApiClient.get('/telemetry/current');
    return data;
  },

  getTelemetryHistory: async (limit = 50): Promise<any> => {
    const { data } = await mlApiClient.get('/telemetry/history', { params: { limit } });
    return data;
  },

  getTelemetryStatus: async (): Promise<any> => {
    const { data } = await mlApiClient.get('/telemetry/status');
    return data;
  },

  getPipelineLifecycle: async (): Promise<PipelineLifecycleResponse> => {
    const { data } = await mlApiClient.get('/pipeline/lifecycle');
    return data;
  },

  getProjectLifecycleCounts: async (): Promise<ProjectLifecycleCountsResponse> => {
    const { data } = await mlApiClient.get('/dashboard/project-lifecycle');
    return data;
  },

  getRiskHeatmapData: async (params: {
    page?: number;
    page_size?: number;
    search?: string;
    risk_level?: string;
    sort_by?: string;
    sort_desc?: boolean;
  } = {}): Promise<RiskHeatmapResponse> => {
    const { data } = await mlApiClient.get('/dashboard/risk-heatmap', { params });
    return data;
  },

  getPipelineRepositorySync: async (): Promise<any> => {
    const { data } = await mlApiClient.get('/pipeline/repository-sync');
    return data;
  },

  getPipelineExtract: async (): Promise<any> => {
    const { data } = await mlApiClient.get('/pipeline/extract');
    return data;
  },

  getPipelineCleanse: async (): Promise<any> => {
    const { data } = await mlApiClient.get('/pipeline/cleanse');
    return data;
  },

  getPipelineModel: async (): Promise<any> => {
    const { data } = await mlApiClient.get('/pipeline/model');
    return data;
  },

  getPipelineInference: async (): Promise<any> => {
    const { data } = await mlApiClient.get('/pipeline/inference');
    return data;
  },

  getPipelineShap: async (): Promise<any> => {
    const { data } = await mlApiClient.get('/pipeline/shap');
    return data;
  },

  getLoginHistory: async (page = 0, size = 20): Promise<any> => {
    const { data } = await apiClient.get('/auth/login-history', { params: { page, size } });
    return data;
  },
};
export default dashboardApi;

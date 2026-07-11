import { mlApiClient } from './client';
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
    const { data } = await mlApiClient.post('/dashboard/export', payload);
    return data;
  },

  // Predict repository quick action
  predictProject: async (payload: any) => {
    const { data } = await mlApiClient.post('/predictions/predict', payload);
    return data;
  },

  // Retrain model quick action
  retrainModel: async (payload: any) => {
    const { data } = await mlApiClient.post('/retraining/train', payload);
    return data;
  },
};
export default dashboardApi;

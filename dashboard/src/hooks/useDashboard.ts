import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import dashboardApi from '../api/dashboard';

export const useSystemStatus = () => {
  return useQuery({
    queryKey: ['system-status'],
    queryFn: dashboardApi.getSystemStatus,
    refetchInterval: 10000, // Sync status every 10s
  });
};

export const useOverview = () => {
  return useQuery({
    queryKey: ['overview'],
    queryFn: dashboardApi.getOverview,
  });
};

export const useGraveyardIndex = () => {
  return useQuery({
    queryKey: ['graveyard-index'],
    queryFn: dashboardApi.getGraveyardIndex,
  });
};

export const useOrgHealth = () => {
  return useQuery({
    queryKey: ['org-health'],
    queryFn: dashboardApi.getOrgHealth,
  });
};

export const useRiskDistribution = () => {
  return useQuery({
    queryKey: ['risk-distribution'],
    queryFn: dashboardApi.getRiskDistribution,
  });
};

export const usePredictionSummary = () => {
  return useQuery({
    queryKey: ['prediction-summary'],
    queryFn: dashboardApi.getPredictionSummary,
  });
};

export const useRepositoryRanking = (params: {
  page?: number;
  page_size?: number;
  search?: string;
  risk_level?: string;
  sort_by?: string;
  sort_desc?: boolean;
}) => {
  return useQuery({
    queryKey: ['repository-ranking', params],
    queryFn: () => dashboardApi.getRepositoryRanking(params),
  });
};

export const useHighRiskProjects = (limit = 10) => {
  return useQuery({
    queryKey: ['high-risk-projects', limit],
    queryFn: () => dashboardApi.getHighRiskProjects(limit),
  });
};

export const useFeatureImportance = () => {
  return useQuery({
    queryKey: ['feature-importance'],
    queryFn: dashboardApi.getFeatureImportance,
  });
};

export const usePredictionTimeline = (granularity = 'daily') => {
  return useQuery({
    queryKey: ['prediction-timeline', granularity],
    queryFn: () => dashboardApi.getPredictionTimeline(granularity),
  });
};

export const useRecommendations = () => {
  return useQuery({
    queryKey: ['recommendations'],
    queryFn: dashboardApi.getRecommendations,
  });
};

export const useAlerts = () => {
  return useQuery({
    queryKey: ['alerts'],
    queryFn: dashboardApi.getAlerts,
    refetchInterval: 15000, // Poll alerts every 15s
  });
};

export const useModelInfo = () => {
  return useQuery({
    queryKey: ['model-info'],
    queryFn: dashboardApi.getModelInfo,
  });
};

export const useActivity = (limit = 50) => {
  return useQuery({
    queryKey: ['activity', limit],
    queryFn: () => dashboardApi.getActivity(limit),
  });
};

export const useForecast = () => {
  return useQuery({
    queryKey: ['forecast'],
    queryFn: dashboardApi.getForecast,
  });
};

export const useExecutiveSummary = () => {
  return useQuery({
    queryKey: ['executive-summary'],
    queryFn: dashboardApi.getExecutiveSummary,
  });
};

export const useAIInsights = (limit = 10) => {
  return useQuery({
    queryKey: ['ai-insights', limit],
    queryFn: () => dashboardApi.getAIInsights(limit),
  });
};

export const usePredictMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: dashboardApi.predictProject,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['overview'] });
      queryClient.invalidateQueries({ queryKey: ['graveyard-index'] });
      queryClient.invalidateQueries({ queryKey: ['org-health'] });
      queryClient.invalidateQueries({ queryKey: ['repository-ranking'] });
      queryClient.invalidateQueries({ queryKey: ['high-risk-projects'] });
      queryClient.invalidateQueries({ queryKey: ['prediction-summary'] });
    },
  });
};

export const useRetrainMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: dashboardApi.retrainModel,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['model-info'] });
      queryClient.invalidateQueries({ queryKey: ['system-status'] });
    },
  });
};
export default {
  useSystemStatus,
  useOverview,
  useGraveyardIndex,
  useOrgHealth,
  useRiskDistribution,
  usePredictionSummary,
  useRepositoryRanking,
  useHighRiskProjects,
  useFeatureImportance,
  usePredictionTimeline,
  useRecommendations,
  useAlerts,
  useModelInfo,
  useActivity,
  useForecast,
  useExecutiveSummary,
  useAIInsights,
  usePredictMutation,
  useRetrainMutation,
};

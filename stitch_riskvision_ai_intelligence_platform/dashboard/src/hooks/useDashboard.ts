import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import dashboardApi from '../api/dashboard';
import repositoryApi from '../api/repository';

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

/**
 * useRepositoryAssessmentMutation — calls Spring Boot POST /api/v1/repositories/{id}/predict
 * which internally invokes the FastAPI ML pipeline and stores results in Supabase.
 * Accepts a repository UUID string.
 */
export const useRepositoryAssessmentMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (repositoryId: string) => dashboardApi.runRepositoryAssessment(repositoryId),
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

/**
 * useGitHubUrlPredictionMutation — calls Spring Boot POST /api/v1/repositories/predict-by-url
 * Accepts a raw GitHub URL, resolves/creates the repository, and runs prediction.
 */
export const useGitHubUrlPredictionMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (githubUrl: string) => repositoryApi.predictByGithubUrl(githubUrl),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['overview'] });
      queryClient.invalidateQueries({ queryKey: ['graveyard-index'] });
      queryClient.invalidateQueries({ queryKey: ['org-health'] });
      queryClient.invalidateQueries({ queryKey: ['repository-ranking'] });
      queryClient.invalidateQueries({ queryKey: ['repositories'] });
      queryClient.invalidateQueries({ queryKey: ['high-risk-projects'] });
      queryClient.invalidateQueries({ queryKey: ['prediction-summary'] });
    },
  });
};

/**
 * useRunPredictionMutation — calls the dedicated POST /api/v1/predictions/run endpoint.
 * Accepts a repository UUID string and returns a full PredictionResultResponse.
 * Invalidates all dashboard queries on success so the UI refreshes automatically.
 */
export const useRunPredictionMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (repositoryId: string) => dashboardApi.runPrediction(repositoryId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['overview'] });
      queryClient.invalidateQueries({ queryKey: ['graveyard-index'] });
      queryClient.invalidateQueries({ queryKey: ['org-health'] });
      queryClient.invalidateQueries({ queryKey: ['repository-ranking'] });
      queryClient.invalidateQueries({ queryKey: ['repositories'] });
      queryClient.invalidateQueries({ queryKey: ['high-risk-projects'] });
      queryClient.invalidateQueries({ queryKey: ['prediction-summary'] });
    },
  });
};

/**
 * usePredictionResult — fetches a stored prediction by its UUID.
 * Calls GET /api/v1/predictions/{id}
 * Enabled only when a predictionId is provided.
 */
export const usePredictionResult = (predictionId: string | null) => {
  return useQuery({
    queryKey: ['prediction-result', predictionId],
    queryFn: () => dashboardApi.getPrediction(predictionId!),
    enabled: !!predictionId,
    staleTime: 5 * 60 * 1000, // 5 minutes — prediction results don't change
    retry: 2,
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

export const useTelemetryAnalysis = () => {
  return useQuery({
    queryKey: ['telemetry-analysis'],
    queryFn: dashboardApi.getTelemetryAnalysis,
    refetchInterval: 60000, // Refresh analysis every minute
  });
};

export const useExplainEventMutation = () => {
  return useMutation({
    mutationFn: (payload: { action: string; description: string }) =>
      dashboardApi.explainEvent(payload),
  });
};

export const useRepositoryRiskAnalysis = (repoId: string | null) => {
  return useQuery({
    queryKey: ['repository-risk-analysis', repoId],
    queryFn: () => (repoId ? dashboardApi.getRepositoryRiskAnalysis(repoId) : null),
    enabled: !!repoId,
  });
};

export const useAuditLogs = (page = 0, size = 20) => {
  return useQuery({
    queryKey: ['audit-logs', page, size],
    queryFn: () => dashboardApi.getAuditLogs(page, size),
    refetchInterval: 5000,
  });
};

export const useAuditStatistics = () => {
  return useQuery({
    queryKey: ['audit-statistics'],
    queryFn: dashboardApi.getAuditStatistics,
    refetchInterval: 15000,
  });
};

export const useTelemetryCurrent = () => {
  return useQuery({
    queryKey: ['telemetry-current'],
    queryFn: dashboardApi.getTelemetryCurrent,
    refetchInterval: 5000,
  });
};

export const useTelemetryHistory = (limit = 50) => {
  return useQuery({
    queryKey: ['telemetry-history', limit],
    queryFn: () => dashboardApi.getTelemetryHistory(limit),
    refetchInterval: 15000,
  });
};

export const useTelemetryStatus = () => {
  return useQuery({
    queryKey: ['telemetry-status'],
    queryFn: dashboardApi.getTelemetryStatus,
    refetchInterval: 10000,
  });
};

export const usePipelineLifecycle = () => {
  return useQuery({
    queryKey: ['pipeline-lifecycle'],
    queryFn: dashboardApi.getPipelineLifecycle,
    refetchInterval: 3000, // Poll every 3 seconds for continuous stage progress animation
  });
};

export const useProjectLifecycleCounts = () => {
  return useQuery({
    queryKey: ['project-lifecycle-counts'],
    queryFn: dashboardApi.getProjectLifecycleCounts,
    refetchInterval: 10000,
  });
};

export const useRiskHeatmap = (params: {
  page?: number;
  page_size?: number;
  search?: string;
  risk_level?: string;
  sort_by?: string;
  sort_desc?: boolean;
} = {}) => {
  return useQuery({
    queryKey: ['risk-heatmap', params],
    queryFn: () => dashboardApi.getRiskHeatmapData(params),
    refetchInterval: 15000,
  });
};

export const usePipelineRepositorySync = () => {
  return useQuery({
    queryKey: ['pipeline-repository-sync'],
    queryFn: dashboardApi.getPipelineRepositorySync,
    refetchInterval: 10000,
  });
};

export const usePipelineExtract = () => {
  return useQuery({
    queryKey: ['pipeline-extract'],
    queryFn: dashboardApi.getPipelineExtract,
    refetchInterval: 10000,
  });
};

export const usePipelineCleanse = () => {
  return useQuery({
    queryKey: ['pipeline-cleanse'],
    queryFn: dashboardApi.getPipelineCleanse,
    refetchInterval: 10000,
  });
};

export const usePipelineModel = () => {
  return useQuery({
    queryKey: ['pipeline-model'],
    queryFn: dashboardApi.getPipelineModel,
    refetchInterval: 10000,
  });
};

export const usePipelineInference = () => {
  return useQuery({
    queryKey: ['pipeline-inference'],
    queryFn: dashboardApi.getPipelineInference,
    refetchInterval: 5000,
  });
};

export const usePipelineShap = () => {
  return useQuery({
    queryKey: ['pipeline-shap'],
    queryFn: dashboardApi.getPipelineShap,
    refetchInterval: 15000,
  });
};

// All hooks above are exported individually as named exports.
// useRunPredictionMutation and usePredictionResult are also exported above.

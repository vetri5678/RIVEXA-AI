/**
 * React Query hooks for ML Prediction Module
 * Wraps mlApi client calls in typed React Query hooks for the dashboard.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import mlApi from '../api/mlApi';
import type { PredictionRequest } from '../api/mlApi';

// ── Model Info & Health ────────────────────────────────────────────────────────

export const useMLHealth = () =>
  useQuery({
    queryKey: ['ml-health'],
    queryFn: mlApi.getHealth,
    refetchInterval: 30000,
  });

export const useMLVersion = () =>
  useQuery({
    queryKey: ['ml-version'],
    queryFn: mlApi.getVersion,
    staleTime: 60000,
  });

export const useMLModel = () =>
  useQuery({
    queryKey: ['ml-model'],
    queryFn: mlApi.getModel,
    staleTime: 60000,
  });

// ── Metrics & Feature Importance ──────────────────────────────────────────────

export const useMLMetrics = () =>
  useQuery({
    queryKey: ['ml-metrics'],
    queryFn: mlApi.getMetrics,
    staleTime: 60000,
  });

export const useMLFeatureImportance = () =>
  useQuery({
    queryKey: ['ml-feature-importance'],
    queryFn: mlApi.getFeatureImportance,
    staleTime: 120000,
  });

// ── Prediction History ─────────────────────────────────────────────────────────

export const useMLPredictionHistory = (limit = 20) =>
  useQuery({
    queryKey: ['ml-prediction-history', limit],
    queryFn: () => mlApi.getPredictionHistory(limit),
    refetchInterval: 30000,
  });

// ── Analytics Summary ──────────────────────────────────────────────────────────

export const useMLAnalytics = () =>
  useQuery({
    queryKey: ['ml-analytics'],
    queryFn: mlApi.getAnalytics,
    refetchInterval: 60000,
  });

export const useMLRiskDistribution = () =>
  useQuery({
    queryKey: ['ml-risk-distribution'],
    queryFn: mlApi.getRiskDistribution,
    refetchInterval: 60000,
  });

// ── Mutations ─────────────────────────────────────────────────────────────────

export const useMLPredictMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: PredictionRequest) => mlApi.predict(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ml-prediction-history'] });
      queryClient.invalidateQueries({ queryKey: ['ml-analytics'] });
      queryClient.invalidateQueries({ queryKey: ['ml-risk-distribution'] });
    },
  });
};

export const useMLRetrainMutation = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: mlApi.triggerRetrain,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ml-metrics'] });
      queryClient.invalidateQueries({ queryKey: ['ml-model'] });
      queryClient.invalidateQueries({ queryKey: ['ml-version'] });
      queryClient.invalidateQueries({ queryKey: ['ml-feature-importance'] });
    },
  });
};

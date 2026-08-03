/**
 * ML Prediction Module API Client
 * Handles all requests to FastAPI ML endpoints via Spring Boot proxy or direct.
 */
import { mlApiClient } from './client';

export interface PredictionRequest {
  project_budget: number;
  actual_cost: number;
  schedule_delay: number;
  team_size: number;
  open_issues: number;
  critical_bugs: number;
  completion_pct: number;
  client_requirement_changes: number;
  priority: string;
  department: string;
  project_type: string;
  estimated_cost?: number;
  actual_duration?: number;
  estimated_duration?: number;
  resource_utilization?: number;
  customer_satisfaction?: number;
  technical_debt?: number;
  security_issues?: number;
  compliance_issues?: number;
}

export interface PredictionResponse {
  id: string;
  riskLevel: string;
  riskScore: number;
  confidence: number;
  probability: number;
  topFactors: string[];
  shapExplainability?: {
    positive: Array<{ feature: string; value: number }>;
    negative: Array<{ feature: string; value: number }>;
    waterfall: Array<{ feature: string; impact: number }>;
  };
  modelVersion: string;
  predictionTime: string;
}

export interface MLMetrics {
  accuracy: number;
  precision: number;
  recall: number;
  f1_score: number;
  roc_auc: number;
  cross_val_mean: number;
  confusion_matrix: number[][];
}

export interface FeatureImportanceItem {
  feature: string;
  importance: number;
}

const mlApi = {
  // POST /api/v1/ml/predict
  predict: async (request: PredictionRequest): Promise<PredictionResponse> => {
    const { data } = await mlApiClient.post('/ml/predict', request);
    return data;
  },

  // POST /api/v1/ml/batch-predict
  batchPredict: async (projects: PredictionRequest[]): Promise<{ total: number; predictions: PredictionResponse[] }> => {
    const { data } = await mlApiClient.post('/ml/batch-predict', { projects });
    return data;
  },

  // GET /api/v1/ml/metrics
  getMetrics: async (): Promise<MLMetrics> => {
    const { data } = await mlApiClient.get('/ml/metrics');
    return data;
  },

  // GET /api/v1/ml/model
  getModel: async (): Promise<Record<string, any>> => {
    const { data } = await mlApiClient.get('/ml/model');
    return data;
  },

  // GET /api/v1/ml/health
  getHealth: async (): Promise<Record<string, any>> => {
    const { data } = await mlApiClient.get('/ml/health');
    return data;
  },

  // GET /api/v1/ml/version
  getVersion: async (): Promise<Record<string, any>> => {
    const { data } = await mlApiClient.get('/ml/version');
    return data;
  },

  // GET /api/v1/ml/feature-importance
  getFeatureImportance: async (): Promise<{ feature_importance: Record<string, number>; ranked_features: FeatureImportanceItem[] }> => {
    const { data } = await mlApiClient.get('/ml/feature-importance');
    return data;
  },

  // GET /api/v1/ml/prediction-history
  getPredictionHistory: async (limit = 20): Promise<{ total: number; items: PredictionResponse[] }> => {
    const { data } = await mlApiClient.get('/ml/prediction-history', { params: { limit } });
    return data;
  },

  // GET /api/v1/ml/analytics (Spring Boot analytics summary)
  getAnalytics: async (): Promise<Record<string, any>> => {
    const { data } = await mlApiClient.get('/ml/analytics');
    return data;
  },

  // GET /api/v1/ml/risk-distribution (Spring Boot DB analytics)
  getRiskDistribution: async (): Promise<Record<string, any>> => {
    const { data } = await mlApiClient.get('/ml/risk-distribution');
    return data;
  },

  // POST /api/v1/ml/train — trigger retraining
  triggerRetrain: async (): Promise<Record<string, any>> => {
    const { data } = await mlApiClient.post('/ml/train', {});
    return data;
  },
};

export default mlApi;

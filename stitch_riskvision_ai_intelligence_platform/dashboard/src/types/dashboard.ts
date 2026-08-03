export interface ServiceStatus {
  name: string;
  status: 'online' | 'degraded' | 'offline' | 'unknown';
  latency_ms?: number;
  message?: string;
}

export interface SystemStatusResponse {
  overall: 'healthy' | 'degraded' | 'critical';
  services: ServiceStatus[];
  checked_at: string;
}

export interface GraveyardIndexResponse {
  index: number;
  classification: 'Healthy' | 'Moderate' | 'High Risk' | 'Critical';
  color: string;
  critical_count: number;
  high_count: number;
  medium_count: number;
  low_count: number;
  total_projects: number;
  trend: number;
  computed_at: string;
}

export interface OrgHealthResponse {
  health_score: number;
  classification: 'Healthy' | 'Warning' | 'Critical';
  avg_failure_probability: number;
  healthy_projects: number;
  at_risk_projects: number;
  critical_projects: number;
  total_analyzed: number;
  trend: number;
  computed_at: string;
}

export interface DashboardOverviewResponse {
  total_projects: number;
  total_predictions: number;
  predictions_today: number;
  active_users: number;
  model_accuracy: number | null;
  critical_projects: number;
  high_risk_projects: number;
  avg_confidence: number;
  graveyard_index: number;
  health_score: number;
}

export interface RiskSlice {
  level: string;
  count: number;
  percentage: number;
  color: string;
}

export interface RiskDistributionResponse {
  slices: RiskSlice[];
  total: number;
}

export interface PredictionSummaryResponse {
  analyzed_today: number;
  alive: number;
  at_risk: number;
  dead: number;
  pending: number;
  avg_confidence_today: number;
  high_confidence_predictions: number;
}

export interface RepositoryRankItem {
  id: string;
  external_id: string;
  name: string;
  health_score: number;
  failure_probability: number;
  risk_level: string;
  last_predicted_at: string | null;
  prediction_count: number;
  trend: 'improving' | 'worsening' | 'stable';
  status: string;
}

export interface RepositoryRankingResponse {
  items: RepositoryRankItem[];
  total: number;
  page: number;
  page_size: number;
}

export interface CriticalFactor {
  name: string;
  impact: number;
  direction: 'increases_risk' | 'decreases_risk';
}

export interface HighRiskProject {
  rank: number;
  project_id: string;
  project_name: string;
  failure_probability: number;
  confidence_level: number;
  risk_score: number;
  critical_factors: CriticalFactor[];
  last_updated: string;
  recommendation?: string;
}

export interface HighRiskProjectsResponse {
  projects: HighRiskProject[];
  total_critical: number;
}

export interface FeatureImportanceItem {
  feature_name: string;
  display_name: string;
  avg_impact: number;
  contribution_pct: number;
  occurrence_count: number;
  direction: 'increases_risk' | 'decreases_risk';
}

export interface FeatureImportanceResponse {
  features: FeatureImportanceItem[];
  total_predictions_analyzed: number;
  computed_at: string;
}

export interface TimelinePoint {
  period: string;
  count: number;
  avg_risk_score: number;
  critical_count: number;
  avg_confidence: number;
}

export interface PredictionTimelineResponse {
  granularity: 'hourly' | 'daily' | 'weekly' | 'monthly';
  points: TimelinePoint[];
}

export interface RecommendationItem {
  id: string;
  priority: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
  area: string;
  action: string;
  affected_projects: number;
  expected_impact: string;
  related_risk_factor: string;
}

export interface RecommendationsResponse {
  items: RecommendationItem[];
  critical_count: number;
  total: number;
}

export interface AlertItem {
  id: string;
  severity: 'critical' | 'warning' | 'info';
  title: string;
  message: string;
  project_id?: string;
  project_name?: string;
  created_at: string;
  is_read: boolean;
}

export interface AlertsResponse {
  items: AlertItem[];
  unread_count: number;
  critical_count: number;
}

export interface ModelInfoResponse {
  model_id?: string;
  model_name?: string;
  version_tag?: string;
  algorithm?: string;
  training_date?: string;
  accuracy?: number;
  precision?: number;
  recall?: number;
  f1_score?: number;
  roc_auc?: number;
  cv_score?: number;
  overall_grade?: string;
  dataset_version?: string;
  total_predictions: number;
  is_loaded: boolean;
  training_duration_seconds?: number;
}

export interface ActivityItem {
  id: string;
  action: string;
  event_type?: string;
  module?: string;
  severity?: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | string;
  status?: string;
  description: string;
  actor?: string;
  username?: string;
  ip_address?: string;
  endpoint?: string;
  http_method?: string;
  response_code?: number;
  duration_ms?: number;
  resource_type?: string;
  created_at: string;
  icon?: string;
}

export interface ActivityResponse {
  items: ActivityItem[];
  total: number;
}

export interface AuditLogsResponse {
  items: ActivityItem[];
  total: number;
  page: number;
  page_size: number;
  total_pages: number;
}

export interface AuditStatistics {
  total_events: number;
  events_last_24h: number;
  events_last_hour: number;
  by_event_type: Array<{ event_type: string; count: number }>;
  by_severity: Record<string, number>;
  by_module: Record<string, number>;
}

export interface TelemetrySnapshot {
  cpu_usage: number;
  memory_usage: number;
  heap_usage: number;
  disk_usage: number;
  network_usage?: number;
  thread_count: number;
  active_sessions: number;
  api_latency: number;
  prediction_latency: number;
  timestamp: string;
}

export interface TelemetryHistoryResponse {
  items: TelemetrySnapshot[];
  total: number;
}

export interface TelemetryStatusResponse {
  status: 'CONNECTED' | 'DISCONNECTED' | 'RECONNECTING';
  server_health: 'HEALTHY' | 'DEGRADED' | 'DOWN';
  uptime_ms: number;
  database_connected: boolean;
  websocket_available: boolean;
  timestamp: string;
}

export interface ForecastPoint {
  period: string;
  projected_risk_score: number;
  confidence_interval_low: number;
  confidence_interval_high: number;
  predicted_critical_count: number;
}

export interface ForecastResponse {
  seven_day: ForecastPoint[];
  thirty_day: ForecastPoint[];
  ninety_day: ForecastPoint[];
  trend_direction: 'improving' | 'worsening' | 'stable';
  computed_at: string;
}

export interface ExecutiveSummaryResponse {
  summary_text: string;
  analyzed_today: number;
  requiring_attention: number;
  health_trend_pct: number;
  avg_confidence_pct: number;
  top_risk_project?: string;
  generated_at: string;
}

export interface AIInsightItem {
  project_id: string;
  project_name: string;
  insight: string;
  risk_level: string;
  failure_probability: number;
  generated_at: string;
}

export interface AIInsightsResponse {
  insights: AIInsightItem[];
  total: number;
}

export interface ExportResponse {
  download_url?: string;
  file_name: string;
  format: string;
  size_bytes: number;
  generated_at: string;
}

export interface PipelineStage {
  name: string;
  status: 'COMPLETED' | 'RUNNING' | 'PENDING' | 'FAILED' | string;
  progressPct: number;
  durationSeconds: number;
  startTime: string;
  endTime?: string;
  currentStage: boolean;
}

export interface PipelineLifecycleResponse {
  status: string;
  active_stage: string;
  model_version: string;
  timestamp: string;
  stages: PipelineStage[];
}

export interface ProjectLifecycleStep {
  label: string;
  count: number;
  color: string;
}

export interface ProjectLifecycleCountsResponse {
  counts: {
    idea: number;
    dev: number;
    testing: number;
    deploy: number;
    ops: number;
    inactive: number;
    archived: number;
    dead: number;
    total: number;
  };
  steps: ProjectLifecycleStep[];
  total: number;
}

export interface RiskHeatmapRow {
  id: string;
  name: string;
  risk_level: string;
  health_score: number;
  failure_probability: number;
  metrics: Record<string, number>;
}

export interface RiskHeatmapResponse {
  xData: string[];
  yData: string[];
  heatmapData: Array<[number, number, number]>;
  rows: RiskHeatmapRow[];
  total: number;
  page: number;
  page_size: number;
  total_pages: number;
}

// ─── Enums ──────────────────────────────────────────────────────────────────

export type GitProvider = 'GITHUB' | 'GITLAB' | 'BITBUCKET' | 'AZURE_DEVOPS' | 'OTHER';

export type RepositoryStatus = 'ACTIVE' | 'ARCHIVED' | 'INACTIVE' | 'DEPRECATED';

export type LifecycleStage = 'ACTIVE' | 'MAINTENANCE' | 'DEPRECATED' | 'ARCHIVED' | 'SUNSET';

export type PredictionStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'DEAD';

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export type PredictionFrequency = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'MANUAL';

export type Visibility = 'PUBLIC' | 'PRIVATE' | 'INTERNAL';

// ─── Core Domain Types ───────────────────────────────────────────────────────

export interface Repository {
  id: string;
  repositoryName: string;
  description: string | null;
  organization: string | null;
  owner: string | null;
  repositoryUrl: string;
  gitProvider: GitProvider;
  branch: string;
  technology: string | null;
  language: string | null;
  projectType: string | null;
  visibility: Visibility;
  license: string | null;
  healthScore: number;
  failureProbability: number;
  predictionStatus: PredictionStatus;
  lifecycleStage: LifecycleStage;
  status: RepositoryStatus;
  riskLevel: RiskLevel;
  aiConfidence: number;
  contributors: number;
  openIssues: number;
  lastCommitDate: string | null;
  lastSyncDate: string | null;
  predictionFrequency: PredictionFrequency;
  autoPredictionEnabled: boolean;
  notificationsEnabled: boolean;
  backgroundSyncEnabled: boolean;
  reportGenerationEnabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface DownloadReportRequest {
  id: string;
  format: 'PDF' | 'EXCEL';
}

// ─── Live GitHub Repository Types ──────────────────────────────────────────────

export interface GithubRepository {
  id: number;
  node_id: string;
  name: string;
  full_name: string;
  owner: string;
  owner_avatar_url: string | null;
  html_url: string;
  clone_url: string;
  ssh_url: string | null;
  default_branch: string;
  private: boolean;
  visibility: string;
  description: string | null;
  language: string | null;
  updated_at: string | null;
  stargazers_count: number | null;
  forks_count: number | null;
}

export interface GithubUserReposResponse {
  success: boolean;
  repositories: GithubRepository[];
  total: number;
  pagination?: {
    page: number;
    per_page: number;
    has_next: boolean;
  };
  error?: string;
}

export interface RepositoryMetrics {
  id: string;
  repositoryId: string;
  commitCount: number;
  commitFrequency: number;
  pullRequests: number;
  mergedPullRequests: number;
  failedPullRequests: number;
  contributors: number;
  activeContributors: number;
  inactiveDays: number;
  openIssues: number;
  closedIssues: number;
  codeCoverage: number;
  documentationScore: number;
  buildSuccessRate: number;
  cyclomaticComplexity: number;
  technicalDebt: number;
  busFactor: number;
  velocity: number;
  updatedAt: string;
}

export interface RepositoryPrediction {
  id: string;
  failureProbability: number;
  riskScore: number;
  riskLevel: RiskLevel;
  confidence: number;
  healthScore: number;
  modelVersion: string;
  predictionStatus: string;
  featureImportanceJson: string | null;
  recommendationsJson: string | null;
  triggeredBy: string;
  createdAt: string;
}

export interface RepositoryActivity {
  id: string;
  action: string;
  description: string;
  actor: string;
  resourceType: string;
  severity: string;
  createdAt: string;
}

export interface RepositoryDetail extends Repository {
  metrics: RepositoryMetrics | null;
  latestPrediction: RepositoryPrediction | null;
  predictionHistory: RepositoryPrediction[];
  recentActivities: RepositoryActivity[];
}

// ─── Summary for Table List ──────────────────────────────────────────────────

export interface RepositorySummary {
  id: string;
  repositoryName: string | null;   // nullable — backend DTO has no @NotNull guarantee
  organization: string | null;
  description: string | null;
  technology: string | null;
  language: string | null;
  repositoryUrl: string;
  gitProvider: GitProvider;
  branch: string;
  status: RepositoryStatus;
  healthScore: number;
  failureProbability: number;
  predictionStatus: PredictionStatus;
  contributors: number;
  openIssues: number;
  commitCount?: number;
  pullRequests?: number;
  buildSuccessRate?: number;
  lastCommitDate: string | null;
  lastSyncDate: string | null;
  lifecycleStage: LifecycleStage;
  aiConfidence: number;
  riskLevel: RiskLevel;
  createdAt: string;
}

// ─── Statistics ──────────────────────────────────────────────────────────────

export interface RepositoryStatistics {
  total: number;
  healthy: number;
  underObservation: number;
  highRisk: number;
  predictedDead: number;
  archived: number;
  active: number;
  pendingPrediction: number;
  aiCoveragePercent: number;
  avgHealthScore: number;
  avgFailureProbability: number;
  totalPredictionsRun: number;
  lastSyncTime: string;
}

// ─── Pagination ──────────────────────────────────────────────────────────────

export interface PagedRepositoryResponse {
  content: RepositorySummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  sortBy: string;
  sortDirection: string;
}

// ─── Request Types ───────────────────────────────────────────────────────────

export interface RepositoryCreateRequest {
  repositoryName: string;
  description?: string;
  organization?: string;
  owner?: string;
  repositoryUrl: string;
  gitProvider: GitProvider;
  branch?: string;
  technology?: string;
  language?: string;
  projectType?: string;
  visibility?: Visibility;
  license?: string;
  predictionFrequency?: PredictionFrequency;
  autoPredictionEnabled?: boolean;
  notificationsEnabled?: boolean;
  backgroundSyncEnabled?: boolean;
  reportGenerationEnabled?: boolean;
  authTokenHint?: string;
  webhookSecret?: string;
}

export interface RepositoryUpdateRequest extends Partial<RepositoryCreateRequest> {}

// ─── Filter State ────────────────────────────────────────────────────────────

export interface RepositoryFilters {
  search: string;
  status: string;
  riskLevel: string;
  predictionStatus: string;
  gitProvider: string;
  language: string;
  organization: string;
  page: number;
  size: number;
  sortBy: string;
  sortDir: string;
}

// ─── Predict Response ────────────────────────────────────────────────────────

export interface PredictResponse {
  success: boolean;
  message: string;
  predictionId: string;
  failureProbability: number;
  riskLevel: RiskLevel;
  confidence: number;
  healthScore: number;
}

/** Extended prediction response returned by POST /repositories/predict-by-url */
export interface GitHubPredictResponse extends PredictResponse {
  repositoryId: string;
  repositoryName: string;
  repositoryUrl: string;
}

export interface SyncResponse {
  success: boolean;
  message: string;
  repositoryId: string;
}

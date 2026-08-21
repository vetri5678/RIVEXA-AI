import { apiClient } from './client';

export interface CodeAnalysisRun {
  id: string;
  userId: string;
  repositoryId: string;
  status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'PARTIAL_SUCCESS' | 'FAILED' | 'CANCELLED';
  startedAt?: string;
  completedAt?: string;
  filesDiscovered: number;
  filesAnalyzed: number;
  filesWithFindings: number;
  currentlyAnalyzingFile?: string;
  errorMessage?: string;
  createdAt: string;
}

export interface CodeFinding {
  id: string;
  fileAnalysisId: string;
  analysisRunId: string;
  findingType: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  confidence: number;
  symbolName?: string;
  startLine?: number;
  endLine?: number;
  title: string;
  description: string;
  evidence: string;
  recommendation: string;
  analysisSource: 'STATIC' | 'HYBRID' | 'ML';
  createdAt: string;
}

export interface CodeFileAnalysis {
  id: string;
  analysisRunId: string;
  repositoryId: string;
  filePath: string;
  fileHash?: string;
  language: string;
  linesOfCode: number;
  riskScore: number;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  confidence: number;
  analysisType: 'STATIC' | 'HYBRID' | 'ML';
  metrics?: Record<string, any>;
  status: string;
  analyzedAt: string;
  findingCount: number;
  findings?: CodeFinding[];
}

export interface CodeVisionSummary {
  repositoryId: string;
  latestRun?: CodeAnalysisRun;
  totalFilesDiscovered: number;
  totalFilesAnalyzed: number;
  filesWithFindings: number;
  criticalCount: number;
  highCount: number;
  mediumCount: number;
  lowCount: number;
  failureProbability?: number;
  riskScore?: number;
  riskLevel?: string;
  healthScore?: number;
  aiConfidence?: number;
  modelVersion?: string;
  featureImportance?: any;
  languageBreakdown: Record<string, number>;
  findingTypeBreakdown: Record<string, number>;
}

export interface PagedCodeFileResponse {
  content: CodeFileAnalysis[];
  totalPages: number;
  totalElements: number;
  number: number;
  size: number;
}

export const codeVisionApi = {
  startAnalysis: async (repositoryId: string, force = false): Promise<CodeAnalysisRun> => {
    const { data } = await apiClient.post<CodeAnalysisRun>(
      `/repositories/${repositoryId}/code-analysis`,
      null,
      { params: { force } }
    );
    return data;
  },

  getLatestSummary: async (repositoryId: string): Promise<CodeVisionSummary> => {
    const { data } = await apiClient.get<CodeVisionSummary>(
      `/repositories/${repositoryId}/code-analysis/latest`
    );
    return data;
  },

  getStatus: async (repositoryId: string): Promise<CodeVisionSummary> => {
    const { data } = await apiClient.get<CodeVisionSummary>(
      `/repositories/${repositoryId}/code-analysis/status`
    );
    return data;
  },

  getFileAnalyses: async (
    repositoryId: string,
    params?: {
      severity?: string;
      language?: string;
      search?: string;
      page?: number;
      size?: number;
      sortBy?: string;
      sortDir?: string;
    }
  ): Promise<PagedCodeFileResponse> => {
    const { data } = await apiClient.get<PagedCodeFileResponse>(
      `/repositories/${repositoryId}/code-analysis/files`,
      { params }
    );
    return data;
  },

  getFileDetail: async (repositoryId: string, fileId: string): Promise<CodeFileAnalysis> => {
    const { data } = await apiClient.get<CodeFileAnalysis>(
      `/repositories/${repositoryId}/code-analysis/files/${fileId}`
    );
    return data;
  },

  forceRescan: async (repositoryId: string): Promise<CodeAnalysisRun> => {
    const { data } = await apiClient.post<CodeAnalysisRun>(
      `/repositories/${repositoryId}/code-analysis/force-rescan`
    );
    return data;
  },
};

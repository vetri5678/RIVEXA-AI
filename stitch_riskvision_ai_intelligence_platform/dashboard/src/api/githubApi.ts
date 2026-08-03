import { apiClient } from './client';

export interface GitHubHealthStatus {
  pat_configured: boolean;
  token_masked: string;
  api_base_url: string;
  authenticated_user?: string;
  user_type?: string;
  pat_valid: boolean;
  status: 'UP' | 'DOWN' | 'DEGRADED';
  rate_limit?: any;
  error?: string;
  timestamp: string;
}

export const githubApi = {
  getHealth: async (): Promise<GitHubHealthStatus> => {
    const { data } = await apiClient.get('/github/health');
    return data;
  },

  getRateLimit: async (): Promise<any> => {
    const { data } = await apiClient.get('/github/rate-limit');
    return data;
  },

  getRepoMetadata: async (owner: string, repo: string): Promise<any> => {
    const { data } = await apiClient.get(`/github/repos/${owner}/${repo}`);
    return data;
  },

  getRepoLanguages: async (owner: string, repo: string): Promise<any> => {
    const { data } = await apiClient.get(`/github/repos/${owner}/${repo}/languages`);
    return data;
  },

  getRepoBranches: async (owner: string, repo: string): Promise<any[]> => {
    const { data } = await apiClient.get(`/github/repos/${owner}/${repo}/branches`);
    return data;
  },

  getRepoCommits: async (
    owner: string,
    repo: string,
    branch?: string,
    page: number = 1,
    perPage: number = 30
  ): Promise<any[]> => {
    const { data } = await apiClient.get(`/github/repos/${owner}/${repo}/commits`, {
      params: { branch, page, perPage },
    });
    return data;
  },

  getRepoContributors: async (owner: string, repo: string): Promise<any[]> => {
    const { data } = await apiClient.get(`/github/repos/${owner}/${repo}/contributors`);
    return data;
  },

  getPullRequests: async (owner: string, repo: string, state: string = 'open'): Promise<any[]> => {
    const { data } = await apiClient.get(`/github/repos/${owner}/${repo}/pulls`, {
      params: { state },
    });
    return data;
  },

  getIssues: async (owner: string, repo: string, state: string = 'open'): Promise<any[]> => {
    const { data } = await apiClient.get(`/github/repos/${owner}/${repo}/issues`, {
      params: { state },
    });
    return data;
  },

  getWorkflows: async (owner: string, repo: string): Promise<any> => {
    const { data } = await apiClient.get(`/github/repos/${owner}/${repo}/workflows`);
    return data;
  },

  getDependabotAlerts: async (owner: string, repo: string): Promise<any[]> => {
    const { data } = await apiClient.get(`/github/repos/${owner}/${repo}/alerts/dependabot`);
    return data;
  },

  getCodeScanningAlerts: async (owner: string, repo: string): Promise<any[]> => {
    const { data } = await apiClient.get(`/github/repos/${owner}/${repo}/alerts/code-scanning`);
    return data;
  },

  getSecretScanningAlerts: async (owner: string, repo: string): Promise<any[]> => {
    const { data } = await apiClient.get(`/github/repos/${owner}/${repo}/alerts/secret-scanning`);
    return data;
  },

  getReadme: async (owner: string, repo: string): Promise<any> => {
    const { data } = await apiClient.get(`/github/repos/${owner}/${repo}/readme`);
    return data;
  },

  searchRepositories: async (query: string): Promise<any> => {
    const { data } = await apiClient.get('/github/search/repositories', {
      params: { q: query },
    });
    return data;
  },

  getUserProfile: async (): Promise<any> => {
    const { data } = await apiClient.get('/github/user/profile');
    return data;
  },
};

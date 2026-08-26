import { apiClient } from './client';

export interface SearchResultItem {
  id: string;
  type: 'REPOSITORY' | 'SOURCE_FILE' | 'FINDING' | 'PREDICTION' | 'PAGE';
  title: string;
  subtitle: string;
  riskLevel: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO';
  url: string;
  metadata?: Record<string, any>;
}

export interface GlobalSearchResponse {
  query: string;
  totalCount: number;
  results: SearchResultItem[];
}

export const searchApi = {
  globalSearch: async (query: string, limit = 10, signal?: AbortSignal): Promise<GlobalSearchResponse> => {
    const response = await apiClient.get<GlobalSearchResponse>('/search', {
      params: { q: query, limit },
      signal,
    });
    return response.data;
  },
};

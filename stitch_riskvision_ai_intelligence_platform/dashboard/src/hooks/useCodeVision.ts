import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { codeVisionApi } from '../api/codeVision';

export const useCodeVisionSummary = (repositoryId?: string) => {
  return useQuery({
    queryKey: ['code-vision-summary', repositoryId],
    queryFn: () => codeVisionApi.getLatestSummary(repositoryId!),
    enabled: !!repositoryId,
    refetchInterval: (query) => {
      const status = query.state.data?.latestRun?.status;
      return status === 'RUNNING' || status === 'QUEUED' ? 3000 : false;
    },
  });
};

export const useCodeVisionFiles = (
  repositoryId?: string,
  params?: {
    severity?: string;
    language?: string;
    search?: string;
    page?: number;
    size?: number;
    sortBy?: string;
    sortDir?: string;
  }
) => {
  return useQuery({
    queryKey: ['code-vision-files', repositoryId, params],
    queryFn: () => codeVisionApi.getFileAnalyses(repositoryId!, params),
    enabled: !!repositoryId,
  });
};

export const useCodeVisionFileDetail = (repositoryId?: string, fileId?: string) => {
  return useQuery({
    queryKey: ['code-vision-file-detail', repositoryId, fileId],
    queryFn: () => codeVisionApi.getFileDetail(repositoryId!, fileId!),
    enabled: !!repositoryId && !!fileId,
  });
};

export const useStartCodeVisionAnalysis = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ repositoryId, force = false }: { repositoryId: string; force?: boolean }) =>
      codeVisionApi.startAnalysis(repositoryId, force),
    onSuccess: (_, { repositoryId }) => {
      queryClient.invalidateQueries({ queryKey: ['code-vision-summary', repositoryId] });
      queryClient.invalidateQueries({ queryKey: ['code-vision-files', repositoryId] });
    },
  });
};

export const useForceCodeVisionRescan = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (repositoryId: string) => codeVisionApi.forceRescan(repositoryId),
    onSuccess: (_, repositoryId) => {
      queryClient.invalidateQueries({ queryKey: ['code-vision-summary', repositoryId] });
      queryClient.invalidateQueries({ queryKey: ['code-vision-files', repositoryId] });
    },
  });
};

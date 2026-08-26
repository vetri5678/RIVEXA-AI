import { useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { searchApi } from '../api/search';
import { getStoredUser } from '../utils/auth';

export const useGlobalSearch = (query: string, delay = 300) => {
  const [debouncedQuery, setDebouncedQuery] = useState(query);
  const user = getStoredUser();
  const userId = user?.id || user?.email || 'anonymous';

  useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedQuery(query.trim());
    }, delay);

    return () => {
      clearTimeout(handler);
    };
  }, [query, delay]);

  const searchQuery = useQuery({
    queryKey: ['global-search', userId, debouncedQuery],
    queryFn: ({ signal }) => searchApi.globalSearch(debouncedQuery, 15, signal),
    enabled: true, // Always enabled so empty queries can load default page navigation items
    staleTime: 5000,
    retry: 1,
  });

  return {
    ...searchQuery,
    debouncedQuery,
  };
};

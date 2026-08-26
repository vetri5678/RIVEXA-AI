import axios from 'axios';
import type { AxiosInstance } from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_URL || '/api/v1';
const ML_BASE_URL = import.meta.env.VITE_ML_SERVICE_URL || '/api/v1';
const LLM_BASE_URL = import.meta.env.VITE_LLM_SERVICE_URL || '/api/v1';
const WS_BASE_URL = import.meta.env.VITE_WS_URL || `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}`;

export { WS_BASE_URL };

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
});

export const mlApiClient = axios.create({
  baseURL: ML_BASE_URL,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
});

export const llmApiClient = axios.create({
  baseURL: LLM_BASE_URL,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
});

function formatError(error: any) {
  if (!error) return error;

  const requestUrl = error.config?.url || '';
  const timestamp = new Date().toISOString();
  let status = error.response?.status || 500;
  let type = 'UNKNOWN';
  let message = error.message || 'An unexpected error occurred';

  if (error.code === 'ECONNABORTED' || message.toLowerCase().includes('timeout')) {
    status = 408;
    type = 'TIMEOUT';
    message = 'Network request timed out';
  } else if (!error.response && error.request) {
    status = 503;
    type = 'BACKEND_UNAVAILABLE';
    message = 'Spring Boot server is not running';
  } else if (status === 401 || status === 403) {
    const errorData = error.response?.data;
    if (requestUrl.includes('/auth/login')) {
      type = 'INVALID_CREDENTIALS';
      message = 'Invalid email or password.';
    } else if (requestUrl.includes('/github/') || errorData?.error?.code?.startsWith('GITHUB_')) {
      type = errorData?.error?.code || 'GITHUB_ERROR';
      message = errorData?.error?.message || errorData?.message || 'GitHub service authentication issue.';
    } else {
      type = 'JWT_FAILURE';
      message = errorData?.message || errorData?.error?.message || 'Session expired or invalid authentication token.';
    }
  } else if (status >= 500) {
    const errorData = error.response?.data;
    const bodyStr = typeof errorData === 'string' ? errorData : JSON.stringify(errorData || '');
    if (
      bodyStr.toLowerCase().includes('connection') ||
      bodyStr.toLowerCase().includes('datasource') ||
      bodyStr.toLowerCase().includes('sql') ||
      bodyStr.toLowerCase().includes('hibernate')
    ) {
      status = 503;
      type = 'DATABASE_UNAVAILABLE';
      message = 'Database connection failed';
    } else if (message.toLowerCase().includes('cors')) {
      type = 'CORS_ERROR';
      message = 'CORS policy block - origin not allowed';
    }
  }

  const structuredData = {
    success: false,
    status,
    message,
    detail: message,
    error: message,
    type,
    path: requestUrl,
    timestamp,
  };

  if (!error.response) {
    error.response = {
      data: structuredData,
      status,
      statusText: 'Service Unavailable',
      headers: {},
      config: error.config,
    };
  } else {
    error.response.data = {
      ...structuredData,
      ...error.response.data,
      success: false,
      type,
    };
  }

  return error;
}

// Configure interceptors on a client instance
function applyCommonInterceptors(client: AxiosInstance) {
  // Request interceptor to add Authorization token
  client.interceptors.request.use(
    (config) => {
      const token = localStorage.getItem('rv_access_token');
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
      return config;
    },
    (error) => Promise.reject(formatError(error))
  );

  // Response interceptor to handle token refresh
  let isRefreshing = false;
  let failedQueue: any[] = [];

  const processQueue = (error: any, token: string | null = null) => {
    failedQueue.forEach((prom) => {
      if (error) {
        prom.reject(error);
      } else {
        prom.resolve(token);
      }
    });
    failedQueue = [];
  };

  client.interceptors.response.use(
    (response) => response,
    async (error) => {
      const originalRequest = error.config;
      const isGithubEndpoint = originalRequest?.url?.includes('/github/') || error.response?.data?.error?.code?.startsWith('GITHUB_');
      if (error.response?.status === 401 && !originalRequest?._retry && !isGithubEndpoint) {
        if (isRefreshing) {
          return new Promise((resolve, reject) => {
            failedQueue.push({ resolve, reject });
          })
            .then((token) => {
              originalRequest.headers.Authorization = `Bearer ${token}`;
              return client(originalRequest);
            })
            .catch((err) => Promise.reject(formatError(err)));
        }

        originalRequest._retry = true;
        isRefreshing = true;

        const refreshToken = localStorage.getItem('rv_refresh_token');
        if (!refreshToken) {
          isRefreshing = false;
          handleLogout();
          return Promise.reject(formatError(error));
        }

        try {
          // Tokens are managed by the Spring Boot Backend
          const { data } = await axios.post(`${API_BASE_URL}/auth/refresh`, {}, {
            headers: { Authorization: `Bearer ${refreshToken}` },
          });

          localStorage.setItem('rv_access_token', data.access_token);
          if (data.refresh_token) {
            localStorage.setItem('rv_refresh_token', data.refresh_token);
          }

          processQueue(null, data.access_token);
          isRefreshing = false;

          originalRequest.headers.Authorization = `Bearer ${data.access_token}`;
          return client(originalRequest);
        } catch (refreshError) {
          processQueue(refreshError, null);
          isRefreshing = false;
          handleLogout();
          return Promise.reject(formatError(refreshError));
        }
      }

      // Retry transient connection errors during initial startup (e.g., backend initializing)
      const isConnError = !error.response || error.response?.status === 503;
      const retryCount = originalRequest?._connRetryCount || 0;
      if (isConnError && retryCount < 3 && originalRequest) {
        originalRequest._connRetryCount = retryCount + 1;
        await new Promise((resolve) => setTimeout(resolve, 800 * (retryCount + 1)));
        return client(originalRequest);
      }

      return Promise.reject(formatError(error));
    }
  );
}

// Apply interceptors to all active client instances
applyCommonInterceptors(apiClient);
applyCommonInterceptors(mlApiClient);
applyCommonInterceptors(llmApiClient);

function handleLogout() {
  // Clear auth tokens and all user-identifying data from localStorage
  localStorage.removeItem('rv_access_token');
  localStorage.removeItem('rv_refresh_token');
  localStorage.removeItem('rv_user');
  localStorage.removeItem('rivexa_user');
  localStorage.removeItem('rivexa_token');
  localStorage.removeItem('access_token');
  localStorage.removeItem('user');

  // Clear ALL TanStack Query cache so no dashboard statistics survive
  // a user switch. This prevents cross-user data leakage when a different
  // user logs in after the current session ends.
  try {
    import('../App').then((m) => {
      if (m?.queryClient?.clear) {
        m.queryClient.clear();
      }
    }).catch(() => {
      // Ignore — the hash navigation below forces a remount anyway
    });
  } catch {
    // ignore
  }

  if (window.location.hash !== '#/login') {
    window.location.hash = '#/login';
  }
}

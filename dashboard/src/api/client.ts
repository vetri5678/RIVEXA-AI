import axios from 'axios';

const API_BASE_URL = '/api/v1';

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor to add Authorization token
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('rv_access_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
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

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then((token) => {
            originalRequest.headers.Authorization = `Bearer ${token}`;
            return apiClient(originalRequest);
          })
          .catch((err) => Promise.reject(err));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      const refreshToken = localStorage.getItem('rv_refresh_token');
      if (!refreshToken) {
        isRefreshing = false;
        handleLogout();
        return Promise.reject(error);
      }

      try {
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
        return apiClient(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError, null);
        isRefreshing = false;
        handleLogout();
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  }
);

function handleLogout() {
  localStorage.removeItem('rv_access_token');
  localStorage.removeItem('rv_refresh_token');
  localStorage.removeItem('rv_user');
  if (window.location.hash !== '#/login') {
    window.location.hash = '#/login';
  }
}

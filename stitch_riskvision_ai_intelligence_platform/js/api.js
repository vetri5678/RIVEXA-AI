/**
 * RIVEXA — Backend API Client
 *
 * Provides typed functions for communicating with the FastAPI backend
 * at http://localhost:8000/api/v1. Handles errors gracefully and returns
 * structured response objects for use in the UI. Supports JWT and automatically
 * handles authentication tokens.
 */

const API_BASE = '/api/v1';

// ─── JWT Authentication Interceptor Helpers ──────────────────────────────────

function getAuthHeaders() {
  const headers = {};
  const token = localStorage.getItem('rv_access_token');
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  return headers;
}

async function attemptTokenRefresh() {
  const refreshToken = localStorage.getItem('rv_refresh_token');
  if (!refreshToken) return false;

  try {
    const res = await fetch(`${API_BASE}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refresh_token: refreshToken }),
    });
    if (res.ok) {
      const data = await res.json();
      localStorage.setItem('rv_access_token', data.access_token);
      if (data.refresh_token) {
        localStorage.setItem('rv_refresh_token', data.refresh_token);
      }
      return true;
    }
  } catch (err) {
    console.error('[API] Token refresh failed:', err);
  }
  return false;
}

function handleUnauthorized() {
  localStorage.removeItem('rv_access_token');
  localStorage.removeItem('rv_refresh_token');
  window.location.hash = '#/login';
}

// ─── Generic Fetch Helper ────────────────────────────────────────────────────

async function apiFetch(endpoint, options = {}) {
  const url = `${API_BASE}${endpoint}`;
  const headers = {
    'Content-Type': 'application/json',
    ...getAuthHeaders(),
    ...(options.headers || {}),
  };

  try {
    let res = await fetch(url, { ...options, headers });

    // Handle token expired (401)
    if (res.status === 401 && endpoint !== '/auth/login' && endpoint !== '/auth/refresh') {
      const refreshed = await attemptTokenRefresh();
      if (refreshed) {
        const retryHeaders = {
          'Content-Type': 'application/json',
          ...getAuthHeaders(),
          ...(options.headers || {}),
        };
        res = await fetch(url, { ...options, headers: retryHeaders });
      } else {
        handleUnauthorized();
        throw new Error('Session expired. Please log in again.');
      }
    }

    if (!res.ok) {
      let detail = `HTTP ${res.status}`;
      try {
        const errJson = await res.json();
        detail = errJson.detail || errJson.message || detail;
      } catch {}
      throw new Error(detail);
    }
    return await res.json();
  } catch (err) {
    throw new Error(`[API] ${endpoint}: ${err.message}`);
  }
}

// ─── Pipeline & Model Core APIs (Existing) ───────────────────────────────────

export async function fetchPipelineStatus() {
  return apiFetch('/pipeline/status');
}

export async function fetchPipelineMetrics() {
  return apiFetch('/pipeline/metrics');
}

export async function triggerTraining(filePaths) {
  return apiFetch('/pipeline/train', {
    method: 'POST',
    body: JSON.stringify({ file_paths: filePaths }),
  });
}

export async function predictProject(projectData) {
  return apiFetch('/pipeline/predict', {
    method: 'POST',
    body: JSON.stringify(projectData),
  });
}

export async function batchPredictProjects(projects) {
  return apiFetch('/pipeline/predict/batch', {
    method: 'POST',
    body: JSON.stringify({ projects }),
  });
}

export async function fetchEvaluationMetrics() {
  return apiFetch('/pipeline/evaluation');
}

export async function fetchReportsList() {
  return apiFetch('/pipeline/reports');
}

export async function fetchReportById(reportId) {
  return apiFetch(`/pipeline/reports/${encodeURIComponent(reportId)}`);
}

export async function uploadAndTrain(file) {
  const formData = new FormData();
  formData.append('file', file);
  
  const headers = getAuthHeaders(); // Don't set Content-Type, browser handles multipart bound
  const url = `${API_BASE}/pipeline/train/upload`;
  const res = await fetch(url, { method: 'POST', body: formData, headers });
  
  if (res.status === 401) {
    const refreshed = await attemptTokenRefresh();
    if (refreshed) {
      const retryHeaders = getAuthHeaders();
      const retryRes = await fetch(url, { method: 'POST', body: formData, headers: retryHeaders });
      if (retryRes.ok) return retryRes.json();
    }
    handleUnauthorized();
    throw new Error('Session expired. Please log in again.');
  }

  if (!res.ok) {
    let detail = `HTTP ${res.status}`;
    try { detail = (await res.json()).detail || detail; } catch {}
    throw new Error(`[API] upload: ${detail}`);
  }
  return res.json();
}

// ─── Authentication APIs ─────────────────────────────────────────────────────

export async function rvLogin(email, password) {
  const res = await apiFetch('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });
  localStorage.setItem('rv_access_token', res.access_token);
  localStorage.setItem('rv_refresh_token', res.refresh_token);
  return res;
}

export async function rvRegister(email, username, password, fullName) {
  return apiFetch('/auth/register', {
    method: 'POST',
    body: JSON.stringify({ email, username, password, full_name: fullName }),
  });
}

export async function rvLogout() {
  const token = localStorage.getItem('rv_refresh_token');
  if (token) {
    try {
      await apiFetch('/auth/logout', {
        method: 'POST',
        body: JSON.stringify({ refresh_token: token }),
      });
    } catch (err) {
      console.warn('[API] Logout endpoint error:', err);
    }
  }
  handleUnauthorized();
}

export async function rvGetMe() {
  return apiFetch('/auth/me');
}

export async function rvChangePassword(currentPassword, newPassword) {
  return apiFetch('/auth/change-password', {
    method: 'POST',
    body: JSON.stringify({ current_password: currentPassword, new_password: newPassword }),
  });
}

export async function rvRequestPasswordReset(email) {
  return apiFetch('/auth/password-reset', {
    method: 'POST',
    body: JSON.stringify({ email }),
  });
}

export async function rvConfirmPasswordReset(token, newPassword) {
  return apiFetch('/auth/password-reset/confirm', {
    method: 'POST',
    body: JSON.stringify({ token, new_password: newPassword }),
  });
}

export async function rvListUsers() {
  return apiFetch('/auth/users');
}

export async function rvUpdateUser(userId, payload) {
  return apiFetch(`/auth/users/${userId}`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  });
}

// ─── Project APIs ────────────────────────────────────────────────────────────

export async function rvListProjects(params = {}) {
  const q = new URLSearchParams();
  if (params.page) q.append('page', params.page);
  if (params.page_size) q.append('page_size', params.page_size);
  if (params.search) q.append('search', params.search);
  if (params.status) q.append('status', params.status);
  if (params.risk_level) q.append('risk_level', params.risk_level);
  if (params.archived !== undefined) q.append('archived', params.archived);
  if (params.sort_by) q.append('sort_by', params.sort_by);
  if (params.sort_order) q.append('sort_order', params.sort_order);

  return apiFetch(`/projects?${q.toString()}`);
}

export async function rvCreateProject(projectData) {
  return apiFetch('/projects', {
    method: 'POST',
    body: JSON.stringify(projectData),
  });
}

export async function rvGetProject(projectId) {
  return apiFetch(`/projects/${projectId}`);
}

export async function rvUpdateProject(projectId, projectData) {
  return apiFetch(`/projects/${projectId}`, {
    method: 'PATCH',
    body: JSON.stringify(projectData),
  });
}

export async function rvDeleteProject(projectId) {
  return apiFetch(`/projects/${projectId}`, {
    method: 'DELETE',
  });
}

export async function rvArchiveProject(projectId) {
  return apiFetch(`/projects/${projectId}/archive`, {
    method: 'POST',
  });
}

export async function rvRestoreProject(projectId) {
  return apiFetch(`/projects/${projectId}/restore`, {
    method: 'POST',
  });
}

// ─── Prediction History APIs ─────────────────────────────────────────────────

export async function rvListPredictions(params = {}) {
  const q = new URLSearchParams();
  if (params.page) q.append('page', params.page);
  if (params.page_size) q.append('page_size', params.page_size);
  if (params.project_id) q.append('project_id', params.project_id);
  if (params.risk_level) q.append('risk_level', params.risk_level);
  if (params.date_from) q.append('date_from', params.date_from);
  if (params.date_to) q.append('date_to', params.date_to);

  return apiFetch(`/predictions?${q.toString()}`);
}

export async function rvGetPrediction(predictionId) {
  return apiFetch(`/predictions/${predictionId}`);
}

export async function rvDeletePrediction(predictionId) {
  return apiFetch(`/predictions/${predictionId}`, {
    method: 'DELETE',
  });
}

export async function rvRestorePrediction(predictionId) {
  return apiFetch(`/predictions/${predictionId}/restore`, {
    method: 'POST',
  });
}

// ─── Analytics APIs ──────────────────────────────────────────────────────────

export async function rvGetDashboard() {
  return apiFetch('/analytics/dashboard');
}

// ─── Reports Download APIs (Blob files trigger) ──────────────────────────────

export async function downloadPDFReport(predictionId, projectId) {
  const q = new URLSearchParams();
  if (predictionId) q.append('prediction_id', predictionId);
  if (projectId) q.append('project_id', projectId);

  const token = localStorage.getItem('rv_access_token');
  const headers = {};
  if (token) headers['Authorization'] = `Bearer ${token}`;

  const res = await fetch(`${API_BASE}/reports/download/pdf?${q.toString()}`, { headers });
  if (!res.ok) {
    throw new Error(`Failed to download PDF report: HTTP ${res.status}`);
  }

  const blob = await res.blob();
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `risk_report_${predictionId || projectId || 'export'}.pdf`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.URL.revokeObjectURL(url);
}

export async function downloadExcelReport(predictionId, projectId) {
  const q = new URLSearchParams();
  if (predictionId) q.append('prediction_id', predictionId);
  if (projectId) q.append('project_id', projectId);

  const token = localStorage.getItem('rv_access_token');
  const headers = {};
  if (token) headers['Authorization'] = `Bearer ${token}`;

  const res = await fetch(`${API_BASE}/reports/download/excel?${q.toString()}`, { headers });
  if (!res.ok) {
    throw new Error(`Failed to download Excel report: HTTP ${res.status}`);
  }

  const blob = await res.blob();
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `risk_report_${predictionId || projectId || 'export'}.xlsx`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.URL.revokeObjectURL(url);
}

// ─── Notification APIs ───────────────────────────────────────────────────────

export async function rvListNotifications(isRead = null, page = 1, pageSize = 20) {
  const q = new URLSearchParams();
  if (isRead !== null) q.append('is_read', isRead);
  q.append('page', page);
  q.append('page_size', pageSize);
  return apiFetch(`/notifications?${q.toString()}`);
}

export async function rvCreateNotification(title, message, type = 'info', userId = null) {
  return apiFetch('/notifications', {
    method: 'POST',
    body: JSON.stringify({ title, message, type, user_id: userId }),
  });
}

export async function rvMarkNotificationRead(notificationId) {
  return apiFetch(`/notifications/${notificationId}/read`, {
    method: 'POST',
  });
}

export async function rvMarkAllNotificationsRead() {
  return apiFetch('/notifications/read-all', {
    method: 'POST',
  });
}

// ─── Model Management Registry APIs ──────────────────────────────────────────

export async function rvListModelVersions(page = 1, pageSize = 20) {
  const q = new URLSearchParams();
  q.append('page', page);
  q.append('page_size', pageSize);
  return apiFetch(`/models/versions?${q.toString()}`);
}

export async function rvGetActiveModel() {
  return apiFetch('/models/active');
}

export async function rvRollbackModel(versionId) {
  return apiFetch(`/models/rollback/${versionId}`, {
    method: 'POST',
  });
}

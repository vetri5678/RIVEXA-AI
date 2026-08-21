import React from 'react';
import { HashRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import Dashboard from './pages/Dashboard';
import Repositories from './pages/Repositories';
import Login from './pages/Login';
import Register from './pages/Register';
import VerifyEmail from './pages/VerifyEmail';
import ForgotPassword from './pages/ForgotPassword';
import ResetPasswordVerify from './pages/ResetPasswordVerify';
import ResetPasswordSuccess from './pages/ResetPasswordSuccess';
import Telemetry from './pages/Telemetry';
import System from './pages/System';
import Profile from './pages/Profile';
import LoginActivity from './pages/LoginActivity';
import OAuthCallback from './pages/OAuthCallback';
import OAuthEmailRequired from './pages/OAuthEmailRequired';
import RepositorySync from './pages/pipeline/RepositorySync';
import FeatureExtraction from './pages/pipeline/FeatureExtraction';
import DataCleansing from './pages/pipeline/DataCleansing';
import ModelEngine from './pages/pipeline/ModelEngine';
import Inference from './pages/pipeline/Inference';
import ShapXai from './pages/pipeline/ShapXai';
import CodeVisionAI from './pages/CodeVisionAI';
import RunPrediction from './pages/RunPrediction';
import PredictionResult from './pages/PredictionResult';
import { ErrorBoundary } from './components/common/ErrorBoundary';

import authApi from './api/auth';
import { getStoredUser, isAdminUser } from './utils/auth';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
    },
  },
});

// Guard route to check login credentials
const ProtectedRoute: React.FC<{ children: React.ReactNode; requireAdmin?: boolean }> = ({ children, requireAdmin = false }) => {
  const token = localStorage.getItem('rv_access_token');
  if (!token) {
    const currentHash = window.location.hash.replace(/^#/, '');
    if (
      currentHash &&
      currentHash !== '/' &&
      currentHash !== '/login' &&
      currentHash !== '/register' &&
      !currentHash.startsWith('/oauth2') &&
      !currentHash.startsWith('/auth') &&
      !currentHash.includes('repository-sync')
    ) {
      sessionStorage.setItem('rv_redirect_after_login', currentHash);
    }
    return <Navigate to="/login" replace />;
  }

  if (requireAdmin) {
    const user = getStoredUser();
    if (!isAdminUser(user)) {
      sessionStorage.setItem('rv_toast_msg', 'Administrator access required');
      sessionStorage.setItem('rv_toast_type', 'error');
      return <Navigate to="/dashboard" replace />;
    }
  }

  return <>{children}</>;
};

export const App: React.FC = () => {
  React.useEffect(() => {
    const token = localStorage.getItem('rv_access_token');
    if (token) {
      authApi.getMe()
        .then((userData) => {
          if (userData && userData.role) {
            localStorage.setItem('rv_user', JSON.stringify(userData));
          }
        })
        .catch((err) => {
          console.warn('[App] Session validation failed on startup. Purging stale auth state:', err);
          localStorage.removeItem('rv_access_token');
          localStorage.removeItem('rv_refresh_token');
          localStorage.removeItem('rv_user');
          localStorage.removeItem('rivexa_user');
          localStorage.removeItem('rivexa_token');
          localStorage.removeItem('access_token');
          localStorage.removeItem('user');
          queryClient.clear();
          if (
            window.location.hash &&
            window.location.hash !== '#/login' &&
            !window.location.hash.startsWith('#/oauth2') &&
            !window.location.hash.startsWith('#/auth')
          ) {
            window.location.hash = '#/login';
          }
        });
    }
  }, []);

  return (
    <QueryClientProvider client={queryClient}>
      <HashRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/verify-email" element={<VerifyEmail />} />

          {/* Dedicated Password Reset Workflow Routes */}
          <Route path="/forgot-password" element={<ForgotPassword />} />
          <Route path="/password-reset" element={<ForgotPassword />} />
          <Route path="/reset-password" element={<ForgotPassword />} />

          <Route path="/reset-password/verify" element={<ResetPasswordVerify />} />
          <Route path="/password-reset/verify" element={<ResetPasswordVerify />} />

          <Route path="/reset-password/success" element={<ResetPasswordSuccess />} />
          <Route path="/password-reset/success" element={<ResetPasswordSuccess />} />
          <Route
            path="/"
            element={
              <ProtectedRoute>
                <Dashboard />
              </ProtectedRoute>
            }
          />
          <Route
            path="/dashboard"
            element={
              <ProtectedRoute>
                <Dashboard />
              </ProtectedRoute>
            }
          />
          <Route
            path="/repositories"
            element={
              <ProtectedRoute>
                <Repositories />
              </ProtectedRoute>
            }
          />
          <Route
            path="/telemetry"
            element={
              <ProtectedRoute>
                <Telemetry />
              </ProtectedRoute>
            }
          />
          <Route
            path="/system"
            element={
              <ProtectedRoute>
                <System />
              </ProtectedRoute>
            }
          />
          <Route
            path="/admin/login-activity"
            element={
              <ProtectedRoute requireAdmin={true}>
                <LoginActivity />
              </ProtectedRoute>
            }
          />
          <Route
            path="/profile"
            element={
              <ProtectedRoute>
                <Profile />
              </ProtectedRoute>
            }
          />
          <Route
            path="/pipeline/repository-sync"
            element={
              <ProtectedRoute>
                <RepositorySync />
              </ProtectedRoute>
            }
          />
          <Route
            path="/pipeline/extract"
            element={
              <ProtectedRoute>
                <FeatureExtraction />
              </ProtectedRoute>
            }
          />
          <Route
            path="/pipeline/cleanse"
            element={
              <ProtectedRoute>
                <DataCleansing />
              </ProtectedRoute>
            }
          />
          <Route
            path="/pipeline/model-engine"
            element={
              <ProtectedRoute>
                <ModelEngine />
              </ProtectedRoute>
            }
          />
          <Route
            path="/pipeline/inference"
            element={
              <ProtectedRoute>
                <Inference />
              </ProtectedRoute>
            }
          />
          <Route
            path="/pipeline/shap"
            element={
              <ProtectedRoute>
                <ShapXai />
              </ProtectedRoute>
            }
          />
          <Route
            path="/code-vision"
            element={
              <ProtectedRoute>
                <CodeVisionAI />
              </ProtectedRoute>
            }
          />
          {/* ── Prediction Workflow Routes ─────────────────────────────── */}
          <Route
            path="/prediction/run"
            element={
              <ProtectedRoute>
                <ErrorBoundary fallbackTitle="Run Prediction Error">
                  <RunPrediction />
                </ErrorBoundary>
              </ProtectedRoute>
            }
          />
          <Route
            path="/prediction/:predictionId"
            element={
              <ProtectedRoute>
                <ErrorBoundary fallbackTitle="Prediction Result Error">
                  <PredictionResult />
                </ErrorBoundary>
              </ProtectedRoute>
            }
          />
          <Route path="/settings/integrations" element={<Navigate to="/profile" replace />} />
          <Route path="/settings" element={<Navigate to="/profile" replace />} />
          <Route path="/auth/callback" element={<OAuthCallback />} />
          <Route path="/oauth2/callback" element={<OAuthCallback />} />
          <Route path="/auth/oauth-success" element={<OAuthCallback />} />
          <Route path="/oauth2/email-required" element={<OAuthEmailRequired />} />
          {/* Catch-all: authenticated → dashboard, unauthenticated → login */}
          <Route
            path="*"
            element={
              localStorage.getItem('rv_access_token')
                ? <Navigate to="/dashboard" replace />
                : <Navigate to="/login" replace />
            }
          />
        </Routes>
      </HashRouter>
    </QueryClientProvider>
  );
};
export default App;

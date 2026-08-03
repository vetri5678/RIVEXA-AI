import React from 'react';
import { HashRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import Dashboard from './pages/Dashboard';
import Repositories from './pages/Repositories';
import Login from './pages/Login';
import Register from './pages/Register';
import VerifyEmail from './pages/VerifyEmail';
import ResetPassword from './pages/ResetPassword';
import Telemetry from './pages/Telemetry';
import System from './pages/System';
import Profile from './pages/Profile';
import OAuthCallback from './pages/OAuthCallback';
import OAuthEmailRequired from './pages/OAuthEmailRequired';
import RepositorySync from './pages/pipeline/RepositorySync';
import FeatureExtraction from './pages/pipeline/FeatureExtraction';
import DataCleansing from './pages/pipeline/DataCleansing';
import ModelEngine from './pages/pipeline/ModelEngine';
import Inference from './pages/pipeline/Inference';
import ShapXai from './pages/pipeline/ShapXai';
import RunPrediction from './pages/RunPrediction';
import PredictionResult from './pages/PredictionResult';
import { ErrorBoundary } from './components/common/ErrorBoundary';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
    },
  },
});

// Guard route to check login credentials
const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const token = localStorage.getItem('rv_access_token');
  if (!token) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
};

export const App: React.FC = () => {
  return (
    <QueryClientProvider client={queryClient}>
      <HashRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/verify-email" element={<VerifyEmail />} />
          <Route path="/password-reset" element={<ResetPassword />} />
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
          <Route path="/oauth2/callback" element={<OAuthCallback />} />
          <Route path="/auth/oauth-success" element={<OAuthCallback />} />
          <Route path="/oauth2/email-required" element={<OAuthEmailRequired />} />
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </HashRouter>
    </QueryClientProvider>
  );
};
export default App;

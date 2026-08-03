import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import authApi from '../api/auth';
import { AlertCircle, X, RefreshCw } from 'lucide-react';

interface ToastItem {
  id: number;
  message: string;
  type: 'error' | 'info' | 'success';
}

const Toast: React.FC<ToastItem & { onDismiss: (id: number) => void }> = ({
  id,
  message,
  type,
  onDismiss,
}) => {
  useEffect(() => {
    const t = setTimeout(() => onDismiss(id), 5000);
    return () => clearTimeout(t);
  }, [id, onDismiss]);

  const styles = {
    error: 'bg-red-500/20 border-red-500/40 text-red-300',
    info: 'bg-cyan-500/20 border-cyan-500/40 text-cyan-300',
    success: 'bg-emerald-500/20 border-emerald-500/40 text-emerald-300',
  };

  return (
    <motion.div
      layout
      initial={{ opacity: 0, x: 20, scale: 0.95 }}
      animate={{ opacity: 1, x: 0, scale: 1 }}
      exit={{ opacity: 0, x: 20, scale: 0.95 }}
      transition={{ duration: 0.2 }}
      className={`flex items-start gap-3 px-4 py-3 rounded-lg border shadow-xl max-w-sm w-full text-xs font-mono backdrop-blur-md ${styles[type]}`}
      role="alert"
    >
      <AlertCircle size={16} className="mt-0.5 shrink-0" />
      <span className="flex-1 leading-relaxed">{message}</span>
      <button
        onClick={() => onDismiss(id)}
        className="shrink-0 opacity-60 hover:opacity-100 transition-opacity"
        aria-label="Close notification"
      >
        <X size={14} />
      </button>
    </motion.div>
  );
};

export const Login: React.FC = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [rememberMe, setRememberMe] = useState(false);
  const [loading, setLoading] = useState(false);
  const [oauthLoading, setOAuthLoading] = useState<'google' | 'github' | null>(null);
  const [error, setError] = useState('');
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const pushToast = (message: string, type: ToastItem['type'] = 'error') =>
    setToasts((prev) => [...prev, { id: Date.now(), message, type }]);

  const dismissToast = (id: number) =>
    setToasts((prev) => prev.filter((t) => t.id !== id));

  useEffect(() => {
    // If already authenticated, redirect to dashboard automatically
    const existingToken = localStorage.getItem('rv_access_token');
    if (existingToken) {
      console.log('[Login] Existing token found. Redirecting to Dashboard...');
      window.location.hash = '#/dashboard';
      return;
    }

    const hashPart = window.location.hash.includes('?')
      ? window.location.hash.split('?')[1]
      : '';
    const searchPart = window.location.search
      ? window.location.search.substring(1)
      : '';
    const params = new URLSearchParams(hashPart || searchPart);
    const errorParam = params.get('error');

    if (errorParam) {
      const decoded = decodeURIComponent(errorParam);
      setError(decoded);
      pushToast(decoded, 'error');
      if (window.history.replaceState) {
        window.history.replaceState(
          null,
          '',
          window.location.pathname + window.location.hash.split('?')[0]
        );
      }
    }

    const saved = localStorage.getItem('rv_remembered_email');
    if (saved) {
      setEmail(saved);
      setRememberMe(true);
    }
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      const emailNorm = email.trim().toLowerCase();
      const res = await authApi.login({ email: emailNorm, password });
      localStorage.setItem('rv_access_token', res.access_token);
      localStorage.setItem('rv_refresh_token', res.refresh_token);

      if (rememberMe) localStorage.setItem('rv_remembered_email', emailNorm);
      else localStorage.removeItem('rv_remembered_email');

      const user = await authApi.getMe();
      localStorage.setItem('rv_user', JSON.stringify(user));
      window.location.hash = '#/dashboard';
    } catch (err: any) {
      const detail =
        err.response?.data?.detail || err.response?.data?.message || err.message;
      let msg = typeof detail === 'string' ? detail : 'Invalid credentials.';
      setError(msg);
      pushToast(msg, 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleOAuth = (provider: 'google' | 'github') => {
    if (oauthLoading) return;
    setOAuthLoading(provider);
    pushToast(
      `Connecting to ${provider === 'google' ? 'Google' : 'GitHub'} OAuth…`,
      'info'
    );
    setTimeout(() => {
      const backendUrl = (import.meta as any).env?.VITE_SPRINGBOOT_URL || '';
      window.location.href = `${backendUrl}/oauth2/authorization/${provider}`;
    }, 400);
  };

  return (
    <>
      {/* Toast Notification Stack */}
      <div className="fixed top-20 right-5 z-50 flex flex-col gap-2 items-end pointer-events-none">
        <AnimatePresence mode="sync">
          {toasts.map((t) => (
            <div key={t.id} className="pointer-events-auto">
              <Toast {...t} onDismiss={dismissToast} />
            </div>
          ))}
        </AnimatePresence>
      </div>

      <div className="bg-[#030d25] text-[#d9e2ff] font-sans min-h-screen flex flex-col selection:bg-[#00e5ff]/20 selection:text-[#00daf3] overflow-x-hidden relative">
        {/* Top App Bar Header */}
        <header className="w-full h-16 flex items-center justify-center px-6 bg-[#030d25] border-b border-[#1f2942]/40">
          <div className="flex items-center gap-2.5">
            <span
              className="material-symbols-outlined text-[#00daf3]"
              style={{ fontVariationSettings: "'FILL' 1", fontSize: "24px" }}
            >
              shield
            </span>
            <h1 className="text-xl font-bold text-white tracking-tight font-sans">
              RiskVision AI
            </h1>
          </div>
        </header>

        {/* Main Content Canvas */}
        <main className="flex-grow flex flex-col items-center justify-center pt-10 pb-12 px-4 relative z-10">
          {/* Hero Identity */}
          <div className="w-full max-w-[420px] mb-8 text-center">
            <p className="text-[13px] font-mono font-medium text-[#00daf3] uppercase tracking-[0.2em] mb-2">
              INTELLIGENCE PLATFORM
            </p>
            <h2 className="text-3xl font-bold text-white mb-2 tracking-tight">
              Sign in to your account
            </h2>
            <p className="text-sm text-[#bac9cc] font-sans">
              Access real-time AI failure risk analytics
            </p>
          </div>

          {/* Sign-in Form Glass Card */}
          <motion.div
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.25 }}
            className="w-full max-w-[420px] bg-[#07122a]/90 rounded-2xl p-7 relative border border-[#1f2942] shadow-2xl backdrop-blur-xl"
          >
            {/* Error Banner */}
            <AnimatePresence>
              {error && (
                <motion.div
                  initial={{ opacity: 0, height: 0 }}
                  animate={{ opacity: 1, height: 'auto' }}
                  exit={{ opacity: 0, height: 0 }}
                  className="mb-5 p-3 bg-red-500/10 border border-red-500/30 rounded-lg text-xs text-red-400 flex flex-col gap-1.5 font-mono"
                  role="alert"
                >
                  <div className="flex items-start gap-2">
                    <AlertCircle size={15} className="mt-0.5 shrink-0" />
                    <span>{error}</span>
                  </div>
                </motion.div>
              )}
            </AnimatePresence>

            <form onSubmit={handleSubmit} noValidate className="space-y-4">
              {/* Boxed Email Input */}
              <div className="relative">
                <input
                  id="email"
                  type="email"
                  required
                  autoComplete="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="Work Email Address"
                  className="w-full px-4 py-3.5 bg-[#0a152d] border border-[#1f2942] rounded-xl text-[#d9e2ff] placeholder-[#bac9cc]/70 focus:border-[#00daf3] focus:ring-1 focus:ring-[#00daf3]/30 outline-none text-sm transition-all font-sans"
                />
              </div>

              {/* Boxed Password Input */}
              <div className="relative">
                <input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  required
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Password"
                  className="w-full pl-4 pr-11 py-3.5 bg-[#0a152d] border border-[#1f2942] rounded-xl text-[#d9e2ff] placeholder-[#bac9cc]/70 focus:border-[#00daf3] focus:ring-1 focus:ring-[#00daf3]/30 outline-none text-sm transition-all font-sans"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((prev) => !prev)}
                  className="absolute right-4 top-1/2 -translate-y-1/2 text-[#bac9cc] hover:text-[#00daf3] transition-colors"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  <span className="material-symbols-outlined text-xl">
                    {showPassword ? 'visibility_off' : 'visibility'}
                  </span>
                </button>
              </div>

              {/* Utilities Row */}
              <div className="flex items-center justify-between pt-1">
                <label className="flex items-center gap-2.5 cursor-pointer group">
                  <input
                    type="checkbox"
                    checked={rememberMe}
                    onChange={(e) => setRememberMe(e.target.checked)}
                    className="w-4 h-4 rounded border-[#1f2942] bg-[#0a152d] text-[#00daf3] focus:ring-[#00daf3] cursor-pointer"
                  />
                  <span className="text-xs font-mono text-[#bac9cc] group-hover:text-[#d9e2ff] transition-colors">
                    Remember me
                  </span>
                </label>
                <a
                  href="#/password-reset"
                  className="text-xs font-mono font-medium text-[#00daf3] hover:underline"
                >
                  Forgot password?
                </a>
              </div>

              {/* Primary CTA Sign In */}
              <button
                disabled={loading}
                type="submit"
                className="w-full flex items-center justify-center gap-2 py-3.5 mt-2 bg-[#00e5ff] text-[#030d25] font-bold text-sm rounded-xl hover:brightness-110 active:scale-[0.98] transition-all shadow-lg shadow-[#00e5ff]/20 disabled:opacity-60 cursor-pointer"
              >
                {loading ? (
                  <RefreshCw size={18} className="animate-spin text-[#030d25]" />
                ) : (
                  <>
                    <span>Sign In</span>
                    <span className="material-symbols-outlined text-xl">
                      arrow_forward
                    </span>
                  </>
                )}
              </button>
            </form>

            {/* Divider */}
            <div className="my-7 flex items-center gap-3">
              <div className="h-[1px] flex-grow bg-[#1f2942]" />
              <span className="text-[11px] font-mono text-[#bac9cc] uppercase tracking-widest px-1">
                OR CONTINUE WITH
              </span>
              <div className="h-[1px] flex-grow bg-[#1f2942]" />
            </div>

            {/* Social Logins Grid */}
            <div className="grid grid-cols-2 gap-3.5">
              {/* Google Button */}
              <button
                type="button"
                disabled={oauthLoading !== null || loading}
                onClick={() => handleOAuth('google')}
                className="flex items-center justify-center gap-2.5 py-3 rounded-xl border border-[#1f2942] bg-[#0a152d] hover:bg-[#101b33] transition-colors group cursor-pointer disabled:opacity-60"
              >
                {oauthLoading === 'google' ? (
                  <RefreshCw size={16} className="animate-spin text-[#00daf3]" />
                ) : (
                  <svg className="w-4 h-4 shrink-0" viewBox="0 0 24 24">
                    <path
                      fill="#4285F4"
                      d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
                    />
                    <path
                      fill="#34A853"
                      d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
                    />
                    <path
                      fill="#FBBC05"
                      d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"
                    />
                    <path
                      fill="#EA4335"
                      d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"
                    />
                  </svg>
                )}
                <span className="text-xs font-mono text-white group-hover:text-[#00daf3] transition-colors font-medium">
                  {oauthLoading === 'google' ? 'Redirecting…' : 'Google'}
                </span>
              </button>

              {/* GitHub Button */}
              <button
                type="button"
                disabled={oauthLoading !== null || loading}
                onClick={() => handleOAuth('github')}
                className="flex items-center justify-center gap-2.5 py-3 rounded-xl border border-[#1f2942] bg-[#0a152d] hover:bg-[#101b33] transition-colors group cursor-pointer disabled:opacity-60"
              >
                {oauthLoading === 'github' ? (
                  <RefreshCw size={16} className="animate-spin text-[#00daf3]" />
                ) : (
                  <span
                    className="material-symbols-outlined text-white group-hover:text-[#00daf3] transition-colors shrink-0"
                    style={{ fontSize: '18px' }}
                  >
                    terminal
                  </span>
                )}
                <span className="text-xs font-mono text-white group-hover:text-[#00daf3] transition-colors font-medium">
                  {oauthLoading === 'github' ? 'Redirecting…' : 'GitHub'}
                </span>
              </button>
            </div>
          </motion.div>

          {/* Secondary CTA */}
          <p className="mt-8 text-sm text-[#bac9cc] font-sans">
            Don't have an account?{' '}
            <a
              href="#/register"
              className="text-[#00e5ff] font-bold hover:underline"
            >
              Create Account
            </a>
          </p>
        </main>

        {/* Shared Footer */}
        <footer className="w-full px-6 py-6 flex flex-col items-center gap-4 text-center mt-auto border-t border-[#1f2942]/30 font-mono">
          <div className="flex items-center gap-8">
            <a
              href="#"
              className="text-xs text-[#bac9cc] hover:text-[#00daf3] transition-colors duration-200"
            >
              Privacy Policy
            </a>
            <a
              href="#"
              className="text-xs text-[#bac9cc] hover:text-[#00daf3] transition-colors duration-200"
            >
              Terms of Service
            </a>
            <a
              href="#"
              className="text-xs text-[#bac9cc] hover:text-[#00daf3] transition-colors duration-200"
            >
              Security Center
            </a>
          </div>
          <div className="flex flex-col gap-1">
            <p className="text-xs font-bold text-white">RiskVision AI</p>
            <p className="text-[11px] text-[#bac9cc]">
              © 2026 RiskVision AI. Protected by enterprise single sign-on.
            </p>
          </div>
        </footer>
      </div>
    </>
  );
};

export default Login;

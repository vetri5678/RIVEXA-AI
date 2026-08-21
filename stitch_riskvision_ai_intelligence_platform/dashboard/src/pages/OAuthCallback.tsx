import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import authApi from '../api/auth';
import { queryClient } from '../App';
import { CheckCircle2, RefreshCw } from 'lucide-react';
import { RivexaLogo } from '../components/common/RivexaLogo';

type Phase = 'verifying' | 'fetching' | 'success' | 'error';

interface StatusStep {
  label: string;
  done: boolean;
}

export const OAuthCallback: React.FC = () => {
  const navigate = useNavigate();
  const [phase, setPhase] = useState<Phase>('verifying');
  const [steps, setSteps] = useState<StatusStep[]>([
    { label: 'Verifying OAuth session tokens', done: false },
    { label: 'Establishing secure API link', done: false },
    { label: 'Loading user profile', done: false },
  ]);
  const [username, setUsername] = useState('');

  const markStep = (index: number) =>
    setSteps((prev) => prev.map((s, i) => (i === index ? { ...s, done: true } : s)));

  useEffect(() => {
    const run = async () => {
      try {
        console.log('[AUTH] OAuth authentication successful');

        const hashPart = window.location.hash.includes('?') ? window.location.hash.split('?')[1] : '';
        const searchPart = window.location.search ? window.location.search.substring(1) : '';
        const params = new URLSearchParams(hashPart || searchPart);

        const token = params.get('token');
        const refreshToken = params.get('refreshToken');
        const uname = params.get('username') || '';
        const errorMsg = params.get('error');

        console.log(`[AUTH] Token received: ${token ? 'YES' : 'NO'}`);

        if (errorMsg) {
          console.warn('[AUTH] OAuth error received:', errorMsg);
          navigate(`/login?error=${encodeURIComponent(errorMsg)}`, { replace: true });
          window.location.hash = `#/login?error=${encodeURIComponent(errorMsg)}`;
          return;
        }
        if (!token) {
          console.error('[AUTH] Missing access token in OAuth callback URL');
          navigate('/login?error=Authentication%20failed%3A%20Missing%20access%20token.', { replace: true });
          window.location.hash = '#/login?error=Authentication%20failed%3A%20Missing%20access%20token.';
          return;
        }

        // ── Clear stale TanStack Query cache before setting new user tokens ──
        if (queryClient && queryClient.clear) {
          queryClient.clear();
        }

        // Store active session tokens
        localStorage.setItem('rv_access_token', token);
        if (refreshToken) localStorage.setItem('rv_refresh_token', refreshToken);
        console.log('[AUTH] Token stored: YES');

        await new Promise((r) => setTimeout(r, 200));
        markStep(0);

        setPhase('fetching');
        await new Promise((r) => setTimeout(r, 200));
        markStep(1);

        // Fetch current user profile to verify token and initialize state
        const user = await authApi.getMe();
        if (user) {
          localStorage.setItem('rv_user', JSON.stringify(user));
          localStorage.setItem('rivexa_user', JSON.stringify(user));
          localStorage.setItem('user', JSON.stringify(user));
        }
        console.log('[AUTH] Current user loaded: YES');
        console.log('[AUTH] Authenticated state: true');

        // Invalidate and reset all query caches so fresh data loads immediately after GitHub connect
        if (queryClient) {
          queryClient.clear();
          queryClient.invalidateQueries();
        }

        setUsername(user.full_name || uname || user.username || 'User');
        markStep(2);

        setPhase('success');

        // Resolve preserved destination target or default to /dashboard
        let redirectTarget = sessionStorage.getItem('rv_redirect_after_login') || '/dashboard';
        sessionStorage.removeItem('rv_redirect_after_login');

        // Avoid landing back on login/register or stuck sync pages
        if (
          redirectTarget.includes('login') ||
          redirectTarget.includes('register') ||
          redirectTarget.includes('repository-sync')
        ) {
          redirectTarget = '/dashboard';
        }

        console.log(`[AUTH] Redirect target: ${redirectTarget}`);
        console.log('[AUTH] Protected route access: ALLOWED');

        setTimeout(() => {
          console.log(`[OAuth Callback] Navigating to ${redirectTarget}...`);
          const target = redirectTarget.startsWith('/') ? redirectTarget : '/' + redirectTarget;
          navigate(target, { replace: true });
          window.location.hash = `#${target}`;
        }, 800);
      } catch (err: any) {
        console.error('[AUTH] Protected route access: DENIED');
        console.error('[OAuth Callback Error]', err);
        setPhase('error');
        setTimeout(() => {
          navigate('/login?error=Failed%20to%20load%20user%20profile.', { replace: true });
          window.location.hash = '#/login?error=Failed%20to%20load%20user%20profile.';
        }, 1500);
      }
    };

    run();
  }, [navigate]);

  return (
    <div className="min-h-screen bg-[#0F172A] flex flex-col items-center justify-center p-4 relative overflow-hidden font-sans">
      <motion.div initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} className="w-full max-w-md">
        <div className="flex flex-col items-center mb-8">            <RivexaLogo variant="compact" size={34} alt="RIVEXA" />
          <p className="text-xs text-slate-400 font-medium tracking-wide mt-1">
            OAuth Authentication
          </p>
        </div>

        <div className="bg-[#1E293B]/90 border border-slate-700/60 rounded-xl p-8 shadow-card backdrop-blur-sm text-center">
          {phase !== 'success' ? (
            <div className="py-4 space-y-6">
              <RefreshCw size={36} className="text-blue-400 animate-spin mx-auto" />

              <div className="space-y-2 text-left">
                {steps.map((st, i) => (
                  <div key={i} className="flex items-center gap-3 text-xs">
                    <span
                      className={`flex h-4 w-4 shrink-0 items-center justify-center rounded-full text-[10px] ${
                        st.done ? 'bg-emerald-500/20 text-emerald-400' : 'bg-slate-800 text-slate-500'
                      }`}
                    >
                      {st.done ? '✓' : i + 1}
                    </span>
                    <span className={st.done ? 'text-slate-200 font-medium' : 'text-slate-400'}>
                      {st.label}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          ) : (
            <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} className="py-4 space-y-3">
              <CheckCircle2 size={48} className="text-emerald-400 mx-auto" />
              <h2 className="text-base font-semibold text-slate-100">
                Welcome back, {username}!
              </h2>
              <p className="text-xs text-slate-400">
                Authentication successful. Redirecting to platform dashboard…
              </p>
            </motion.div>
          )}
        </div>
      </motion.div>
    </div>
  );
};

export default OAuthCallback;

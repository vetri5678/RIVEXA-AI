import React, { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import authApi from '../api/auth';
import { CheckCircle2, ShieldCheck, RefreshCw } from 'lucide-react';

type Phase = 'verifying' | 'fetching' | 'success' | 'error';

interface StatusStep {
  label: string;
  done: boolean;
}

export const OAuthCallback: React.FC = () => {
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
        const hashPart = window.location.hash.includes('?') ? window.location.hash.split('?')[1] : '';
        const searchPart = window.location.search ? window.location.search.substring(1) : '';
        const params = new URLSearchParams(hashPart || searchPart);

        const token = params.get('token');
        const refreshToken = params.get('refreshToken');
        const uname = params.get('username') || '';
        const errorMsg = params.get('error');

        if (errorMsg) {
          window.location.hash = `#/login?error=${encodeURIComponent(errorMsg)}`;
          return;
        }
        if (!token) {
          window.location.hash = '#/login?error=Authentication%20failed%3A%20Missing%20access%20token.';
          return;
        }

        localStorage.setItem('rv_access_token', token);
        if (refreshToken) localStorage.setItem('rv_refresh_token', refreshToken);

        await new Promise((r) => setTimeout(r, 200));
        markStep(0);

        setPhase('fetching');
        await new Promise((r) => setTimeout(r, 200));
        markStep(1);

        const user = await authApi.getMe();
        localStorage.setItem('rv_user', JSON.stringify(user));
        setUsername(user.full_name || uname || user.username || 'User');
        markStep(2);

        setPhase('success');

        setTimeout(() => {
          console.log('[OAuth Callback] Redirecting user to Dashboard...');
          window.location.hash = '#/dashboard';
        }, 1000);
      } catch (err: any) {
        console.error('[OAuth Callback Error]', err);
        setPhase('error');
        setTimeout(() => {
          window.location.hash = '#/login?error=Failed%20to%20load%20user%20profile.';
        }, 1500);
      }
    };

    run();
  }, []);

  return (
    <div className="min-h-screen bg-[#0F172A] flex flex-col items-center justify-center p-4 relative overflow-hidden font-sans">
      <motion.div initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} className="w-full max-w-md">
        <div className="flex flex-col items-center mb-8">
          <div className="p-3 bg-blue-600/10 border border-blue-500/20 rounded-xl text-blue-400 mb-3 shadow-sm">
            <ShieldCheck size={32} />
          </div>
          <h1 className="text-xl font-bold text-slate-100 tracking-tight text-center">
            RiskVision AI
          </h1>
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

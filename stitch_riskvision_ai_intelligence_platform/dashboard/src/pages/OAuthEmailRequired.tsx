import React, { useState, useEffect } from 'react';
import authApi from '../api/auth';
import { Mail, RefreshCw, AlertCircle, ShieldCheck, ArrowRight } from 'lucide-react';

export const OAuthEmailRequired: React.FC = () => {
  const [email, setEmail] = useState('');
  const [provider, setProvider] = useState('');
  const [providerUserId, setProviderUserId] = useState('');
  const [avatarUrl, setAvatarUrl] = useState('');
  const [username, setUsername] = useState('');
  const [fullName, setFullName] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const hashPart = window.location.hash.includes('?') ? window.location.hash.split('?')[1] : '';
    const searchPart = window.location.search ? window.location.search.substring(1) : '';
    const params = new URLSearchParams(hashPart || searchPart);

    setProvider(params.get('provider') || 'github');
    setProviderUserId(params.get('providerUserId') || '');
    setAvatarUrl(params.get('avatarUrl') || '');
    setUsername(params.get('username') || '');
    setFullName(params.get('fullName') || '');
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    const emailNormalized = email.trim().toLowerCase();

    try {
      const res = await authApi.completeOAuthEmail({
        email: emailNormalized,
        provider,
        providerUserId,
        username,
        fullName,
        avatarUrl,
      });

      localStorage.setItem('rv_access_token', res.access_token);
      localStorage.setItem('rv_refresh_token', res.refresh_token);

      const user = await authApi.getMe();
      localStorage.setItem('rv_user', JSON.stringify(user));

      console.log('[OAuth Email Required] Account complete. Navigating to Dashboard...');
      window.location.hash = '#/dashboard';
    } catch (err: any) {
      const detail = err.response?.data?.detail || err.response?.data?.message || err.message;
      setError(detail || 'Failed to complete registration.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#0F172A] flex flex-col items-center justify-center p-4 relative overflow-hidden font-sans">
      <div className="w-full max-w-md z-10">
        <div className="flex flex-col items-center mb-8">
          <div className="p-3 bg-blue-600/10 border border-blue-500/20 rounded-xl text-blue-400 mb-3 shadow-sm">
            <ShieldCheck size={32} />
          </div>
          <h1 className="text-xl font-bold text-slate-100 tracking-tight text-center">
            RiskVision AI
          </h1>
          <p className="text-xs text-slate-400 font-medium tracking-wide mt-1">
            Email Verification Required
          </p>
        </div>

        <div className="bg-[#1E293B]/90 border border-slate-700/60 rounded-xl p-8 shadow-card backdrop-blur-sm">
          <p className="text-xs text-slate-300 mb-6 leading-relaxed">
            Your <span className="font-semibold uppercase text-blue-400">{provider}</span> account did not supply a public email address. Please enter your email to complete registration.
          </p>

          <form onSubmit={handleSubmit} className="space-y-4">
            {error && (
              <div className="p-3 bg-red-500/10 border border-red-500/30 rounded-lg text-xs text-red-400 flex items-start gap-2">
                <AlertCircle size={15} className="mt-0.5 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            <div className="space-y-1.5">
              <label className="block text-xs font-medium text-slate-300">
                Work Email Address
              </label>
              <div className="relative">
                <span className="absolute inset-y-0 left-0 flex items-center pl-3 text-slate-400 pointer-events-none">
                  <Mail size={15} />
                </span>
                <input
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="name@company.com"
                  className="w-full pl-9 pr-3 py-2 bg-slate-900/90 border border-slate-700/80 rounded-lg text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/30 transition-all"
                />
              </div>
            </div>

            <button
              disabled={loading}
              type="submit"
              className="w-full btn-primary py-2.5 flex items-center justify-center gap-2 text-xs font-semibold mt-2"
            >
              {loading ? (
                <RefreshCw size={15} className="animate-spin" />
              ) : (
                <span>Complete Registration</span>
              )}
              {!loading && <ArrowRight size={14} />}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
};

export default OAuthEmailRequired;

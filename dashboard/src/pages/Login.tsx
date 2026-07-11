import React, { useState } from 'react';
import authApi from '../api/auth';
import GlassCard from '../components/common/GlassCard';
import { BrainCircuit, Key, Mail, Sparkles, RefreshCw } from 'lucide-react';

export const Login: React.FC = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      const res = await authApi.login({ email, password });
      localStorage.setItem('rv_access_token', res.access_token);
      localStorage.setItem('rv_refresh_token', res.refresh_token);

      // Retrieve user info
      const user = await authApi.getMe();
      localStorage.setItem('rv_user', JSON.stringify(user));

      window.location.hash = '#/';
    } catch (err: any) {
      const detail = err.response?.data?.detail;
      if (Array.isArray(detail)) {
        setError(detail.map((d: any) => `${d.loc.join('.')}: ${d.msg}`).join(', '));
      } else if (typeof detail === 'string') {
        setError(detail);
      } else {
        setError('Invalid administrative credentials.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-cyber-950 flex flex-col items-center justify-center p-4 relative overflow-hidden">
      {/* Visual cyber decorations */}
      <div className="absolute top-1/4 left-1/4 h-72 w-72 rounded-full bg-neon-blue/5 blur-3xl animate-pulse-slow"></div>
      <div className="absolute bottom-1/4 right-1/4 h-72 w-72 rounded-full bg-neon-pink/5 blur-3xl animate-pulse-slow"></div>

      <div className="w-full max-w-md z-10">
        {/* Brand Header */}
        <div className="flex flex-col items-center mb-8">
          <div className="p-3 bg-neon-blue/10 border border-neon-blue/30 rounded-2xl text-neon-blue mb-3 shadow-[0_0_20px_rgba(0,212,255,0.2)]">
            <BrainCircuit size={40} className="animate-pulse-slow" />
          </div>
          <h1 className="text-2xl font-mono font-black text-slate-100 tracking-wider text-center glow-text-blue">
            GRAVEYARD ANALYZER
          </h1>
          <p className="text-xs font-mono text-neon-blue tracking-widest uppercase">
            AI Operational Command Center
          </p>
        </div>

        <GlassCard className="p-8 border-glass-border/40 shadow-glass">
          <form onSubmit={handleSubmit} className="space-y-6">
            {error && (
              <div className="p-3 bg-neon-pink/10 border border-neon-pink/30 rounded-lg text-xs font-mono text-neon-pink glow-text-red">
                {error}
              </div>
            )}

            {/* Email Field */}
            <div className="space-y-2">
              <label className="block text-xs font-mono text-slate-400 uppercase tracking-wider">
                System Email Access
              </label>
              <div className="relative">
                <span className="absolute inset-y-0 left-0 flex items-center pl-3 text-slate-500">
                  <Mail size={16} />
                </span>
                <input
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="admin@riskvision.ai"
                  className="w-full pl-10 glass-input"
                />
              </div>
            </div>

            {/* Password Field */}
            <div className="space-y-2">
              <label className="block text-xs font-mono text-slate-400 uppercase tracking-wider">
                Encryption Key Phrase
              </label>
              <div className="relative">
                <span className="absolute inset-y-0 left-0 flex items-center pl-3 text-slate-500">
                  <Key size={16} />
                </span>
                <input
                  type="password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••••••"
                  className="w-full pl-10 glass-input"
                />
              </div>
            </div>

            {/* Submit button */}
            <button
              disabled={loading}
              type="submit"
              className="w-full btn-cyber-primary py-3 flex items-center justify-center gap-2 font-mono text-sm font-bold uppercase tracking-wider"
            >
              {loading ? (
                <RefreshCw size={16} className="animate-spin" />
              ) : (
                <Sparkles size={16} />
              )}
              ESTABLISH Datalink
            </button>
          </form>
        </GlassCard>

        {/* System footer message */}
        <p className="text-center text-[10px] font-mono text-slate-500 mt-6 uppercase tracking-widest">
          Secure Administrative Node v1.0.0
        </p>
      </div>
    </div>
  );
};
export default Login;

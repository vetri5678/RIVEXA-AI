import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { FcGoogle } from 'react-icons/fc';
import { FaGithub } from 'react-icons/fa';
import authApi from '../api/auth';
import {
  Key,
  Mail,
  User,
  ClipboardList,
  ArrowRight,
  RefreshCw,
  AlertCircle,
  X,
  CheckCircle2 } from 'lucide-react';
import { RivexaLogo } from '../components/common/RivexaLogo';

// ─── Toast ────────────────────────────────────────────────────────────────────

interface ToastItem {
  id: number;
  message: string;
  type: 'error' | 'info' | 'success';
}

const Toast: React.FC<ToastItem & { onDismiss: (id: number) => void }> = ({
  id,
  message,
  type,
  onDismiss }) => {
  useEffect(() => {
    const t = setTimeout(() => onDismiss(id), 5000);
    return () => clearTimeout(t);
  }, [id, onDismiss]);

  const styles = {
    error: 'bg-red-500/10 border-red-500/30 text-red-400',
    info: 'bg-blue-500/10 border-blue-500/30 text-blue-400',
    success: 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400' };

  return (
    <motion.div
      layout
      initial={{ opacity: 0, x: 20, scale: 0.95 }}
      animate={{ opacity: 1, x: 0, scale: 1 }}
      exit={{ opacity: 0, x: 20, scale: 0.95 }}
      transition={{ duration: 0.2 }}
      className={`flex items-start gap-3 px-4 py-3 rounded-lg border shadow-lg max-w-sm w-full text-xs font-sans ${styles[type]}`}
      role="alert"
    >
      <AlertCircle size={15} className="mt-0.5 shrink-0" aria-hidden="true" />
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

// ─── OAuth Button ─────────────────────────────────────────────────────────────

const OAuthButton: React.FC<{
  provider: 'google' | 'github';
  isLoading: boolean;
  disabled: boolean;
  onClick: () => void;
}> = ({ provider, isLoading, disabled, onClick }) => {
  const isGoogle = provider === 'google';

  return (
    <motion.button
      type="button"
      onClick={onClick}
      disabled={disabled}
      whileHover={disabled ? {} : { y: -1 }}
      whileTap={disabled ? {} : { scale: 0.98 }}
      className={`
        relative w-full flex items-center justify-center gap-3 py-2.5 px-4 rounded-lg
        font-sans font-medium text-xs tracking-wide transition-all duration-150
        border focus:outline-none focus:ring-2 focus:ring-offset-1 focus:ring-offset-[#0F172A]
        ${
          isGoogle
            ? 'bg-white text-slate-800 border-slate-200 hover:bg-slate-50 focus:ring-blue-500 shadow-sm'
            : 'bg-[#181818] text-white border-slate-700 hover:bg-[#222222] focus:ring-slate-400 shadow-sm'
        }
        ${disabled ? 'opacity-60 cursor-not-allowed' : 'cursor-pointer'}
      `}
    >
      <span className="flex items-center justify-center w-4 h-4 shrink-0">
        {isLoading ? (
          <RefreshCw size={14} className="animate-spin text-slate-400" />
        ) : isGoogle ? (
          <FcGoogle size={18} />
        ) : (
          <FaGithub size={18} />
        )}
      </span>
      <span>
        {isLoading ? 'Redirecting…' : `Continue with ${isGoogle ? 'Google' : 'GitHub'}`}
      </span>
    </motion.button>
  );
};

// ─── Register Page ────────────────────────────────────────────────────────────

export const Register: React.FC = () => {
  const [email, setEmail] = useState('');
  const [username, setUsername] = useState('');
  const [fullName, setFullName] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [oauthLoading, setOAuthLoading] = useState<'google' | 'github' | null>(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const pushToast = (message: string, type: ToastItem['type'] = 'error') =>
    setToasts((prev) => [...prev, { id: Date.now(), message, type }]);

  const dismissToast = (id: number) =>
    setToasts((prev) => prev.filter((t) => t.id !== id));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    setSuccess(false);

    const emailNormalized = email.trim().toLowerCase();

    try {
      await authApi.register({
        email: emailNormalized,
        username: username.trim(),
        password,
        full_name: fullName.trim() });
      setSuccess(true);
      pushToast('Account created! A verification link has been sent to your email.', 'success');
    } catch (err: any) {
      const detail =
        err.response?.data?.error || err.response?.data?.detail || 'Registration failed.';
      let msg: string;
      if (Array.isArray(detail)) {
        msg = detail.map((d: any) => `${d.loc.join('.')}: ${d.msg}`).join(', ');
      } else if (typeof detail === 'string') {
        msg = detail;
      } else {
        msg = JSON.stringify(detail);
      }
      setError(msg);
      pushToast(msg, 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleOAuthSignUp = (provider: 'google' | 'github') => {
    if (oauthLoading) return;
    setOAuthLoading(provider);
    pushToast(
      `Connecting to ${provider === 'google' ? 'Google' : 'GitHub'}…`,
      'info'
    );
    setTimeout(() => {
      window.location.href = `/oauth2/authorization/${provider}`;
    }, 400);
  };

  const isOAuthBusy = oauthLoading !== null;

  return (
    <>
      {/* Toast notification stack */}
      <div className="fixed top-5 right-5 z-50 flex flex-col gap-2 items-end pointer-events-none">
        <AnimatePresence mode="sync">
          {toasts.map((t) => (
            <div key={t.id} className="pointer-events-auto">
              <Toast {...t} onDismiss={dismissToast} />
            </div>
          ))}
        </AnimatePresence>
      </div>

      <div className="min-h-screen bg-[#0F172A] flex flex-col items-center justify-start py-12 px-4 relative overflow-y-auto overflow-x-hidden font-sans">
        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3 }}
          className="w-full max-w-md z-10"
        >
          {/* Brand Header */}
          <div className="flex flex-col items-center mb-8">
            <RivexaLogo variant="compact" size={34} alt="RIVEXA" />
            <p className="text-xs text-slate-400 font-medium tracking-wide mt-3">
              Create Enterprise Credentials
            </p>
          </div>

          <div className="bg-[#1E293B]/90 border border-slate-700/60 rounded-xl p-5 sm:p-8 px-4 sm:px-8 shadow-card backdrop-blur-sm">
            {success ? (
              <motion.div
                initial={{ opacity: 0, scale: 0.95 }}
                animate={{ opacity: 1, scale: 1 }}
                className="space-y-4 text-center py-4"
              >
                <div className="mx-auto w-12 h-12 rounded-full bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 flex items-center justify-center">
                  <CheckCircle2 size={26} />
                </div>
                <h2 className="text-base font-semibold text-slate-100">
                  Verify Your Email Address
                </h2>
                <p className="text-xs text-slate-300 leading-relaxed">
                  We have sent a verification email to{' '}
                  <span className="text-blue-400 font-semibold">{email}</span>.
                </p>
                <p className="text-[11px] text-slate-400">
                  Please check your inbox and click the activation link to complete setup before signing in.
                </p>
                <div className="pt-4">
                  <a
                    href="#/login"
                    className="w-full btn-primary py-2.5 flex items-center justify-center gap-2 text-xs font-semibold"
                  >
                    <span>Proceed to Sign In</span>
                    <ArrowRight size={14} />
                  </a>
                </div>
              </motion.div>
            ) : (
              <form onSubmit={handleSubmit} className="space-y-4" noValidate>
                <div className="mb-4 text-center">
                  <h2 className="text-lg font-semibold text-slate-100">
                    Create account
                  </h2>
                  <p className="text-xs text-slate-400 mt-1">
                    Start monitoring software project failure hazards
                  </p>
                </div>

                <AnimatePresence>
                  {error && (
                    <motion.div
                      initial={{ opacity: 0, height: 0 }}
                      animate={{ opacity: 1, height: 'auto' }}
                      exit={{ opacity: 0, height: 0 }}
                      className="p-3 bg-red-500/10 border border-red-500/30 rounded-lg text-xs text-red-400 flex items-start gap-2"
                      role="alert"
                    >
                      <AlertCircle size={15} className="mt-0.5 shrink-0" />
                      <span>{error}</span>
                    </motion.div>
                  )}
                </AnimatePresence>

                {/* Full Name */}
                <div className="space-y-1.5">
                  <label className="block text-xs font-medium text-slate-300">
                    Full Name
                  </label>
                  <div className="relative">
                    <span className="absolute inset-y-0 left-0 flex items-center pl-3 text-slate-400 pointer-events-none">
                      <User size={15} />
                    </span>
                    <input
                      type="text"
                      required
                      value={fullName}
                      onChange={(e) => setFullName(e.target.value)}
                      placeholder="John Doe"
                      className="w-full pl-9 pr-3 py-2 bg-slate-900/90 border border-slate-700/80 rounded-lg text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/30 transition-all"
                    />
                  </div>
                </div>

                {/* Username */}
                <div className="space-y-1.5">
                  <label className="block text-xs font-medium text-slate-300">
                    Username
                  </label>
                  <div className="relative">
                    <span className="absolute inset-y-0 left-0 flex items-center pl-3 text-slate-400 pointer-events-none">
                      <ClipboardList size={15} />
                    </span>
                    <input
                      type="text"
                      required
                      minLength={3}
                      maxLength={50}
                      value={username}
                      onChange={(e) => setUsername(e.target.value)}
                      placeholder="johndoe"
                      className="w-full pl-9 pr-3 py-2 bg-slate-900/90 border border-slate-700/80 rounded-lg text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/30 transition-all"
                    />
                  </div>
                </div>

                {/* Email */}
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

                {/* Password */}
                <div className="space-y-1.5">
                  <label className="block text-xs font-medium text-slate-300">
                    Password
                  </label>
                  <div className="relative">
                    <span className="absolute inset-y-0 left-0 flex items-center pl-3 text-slate-400 pointer-events-none">
                      <Key size={15} />
                    </span>
                    <input
                      type="password"
                      required
                      minLength={8}
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      placeholder="••••••••••••"
                      className="w-full pl-9 pr-3 py-2 bg-slate-900/90 border border-slate-700/80 rounded-lg text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/30 transition-all"
                    />
                  </div>
                  <p className="text-[10px] text-slate-500">Minimum 8 characters</p>
                </div>

                {/* Submit */}
                <button
                  disabled={loading}
                  type="submit"
                  className="w-full btn-primary py-2.5 text-xs font-semibold mt-2"
                >
                  {loading ? (
                    <RefreshCw size={15} className="animate-spin" />
                  ) : (
                    <span>Create Account</span>
                  )}
                  {!loading && <ArrowRight size={14} />}
                </button>

                {/* Divider */}
                <div className="relative my-4" role="separator">
                  <div className="absolute inset-0 flex items-center">
                    <div className="w-full border-t border-slate-700/60" />
                  </div>
                  <div className="relative flex justify-center text-[11px]">
                    <span className="px-3 bg-[#1E293B] text-slate-400 font-medium uppercase tracking-wider">
                      Or continue with
                    </span>
                  </div>
                </div>

                {/* OAuth Buttons */}
                <div className="space-y-2.5">
                  <OAuthButton
                    provider="google"
                    isLoading={oauthLoading === 'google'}
                    disabled={isOAuthBusy || loading}
                    onClick={() => handleOAuthSignUp('google')}
                  />
                  <OAuthButton
                    provider="github"
                    isLoading={oauthLoading === 'github'}
                    disabled={isOAuthBusy || loading}
                    onClick={() => handleOAuthSignUp('github')}
                  />
                </div>

                {/* Footer link */}
                <div className="text-center pt-3 text-xs text-slate-400">
                  Already have an account?{' '}
                  <a
                    href="#/login"
                    className="text-blue-400 hover:text-blue-300 font-semibold"
                  >
                    Sign In
                  </a>
                </div>
              </form>
            )}
          </div>

          <p className="text-center text-[11px] text-slate-500 mt-6">
            © 2026 RIVEXA. Protected by enterprise single sign-on.
          </p>
        </motion.div>
      </div>
    </>
  );
};

export default Register;

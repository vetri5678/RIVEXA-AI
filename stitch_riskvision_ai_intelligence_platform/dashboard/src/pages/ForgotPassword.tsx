import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import authApi from '../api/auth';
import { Mail, RefreshCw, AlertCircle, CheckCircle2, ArrowRight } from 'lucide-react';
import { RivexaLogo } from '../components/common/RivexaLogo';

export const ForgotPassword: React.FC = () => {
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [infoMessage, setInfoMessage] = useState('');

  const handleRequestOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    const targetEmail = email.trim().toLowerCase();

    if (!targetEmail || !targetEmail.includes('@')) {
      setError('Please enter a valid work email address.');
      return;
    }

    setLoading(true);
    setError('');
    setInfoMessage('');

    try {
      await authApi.requestPasswordReset(targetEmail);
      sessionStorage.setItem('rv_reset_email', targetEmail);
      setInfoMessage(`A 6-digit OTP code has been dispatched to ${targetEmail}. Redirecting to verification page…`);
      
      setTimeout(() => {
        window.location.hash = '#/reset-password/verify';
      }, 1200);
    } catch (err: any) {
      const msg = err.response?.data?.detail || err.response?.data?.message || err.message || 'Failed to send OTP code.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#0F172A] flex flex-col items-center justify-center p-4 relative overflow-y-auto font-sans">
      <motion.div
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3 }}
        className="w-full max-w-md z-10"
      >
        {/* Header */}
        <div className="flex flex-col items-center mb-8">            <RivexaLogo variant="compact" size={34} alt="RIVEXA" />
          <p className="text-xs text-slate-400 font-medium tracking-wide mt-1">
            Secure OTP Password Reset
          </p>
        </div>

        {/* Form Container */}
        <div className="bg-[#1E293B]/90 border border-slate-700/60 rounded-xl p-8 shadow-card backdrop-blur-sm">
          <div className="mb-6 text-center">
            <h2 className="text-base font-semibold text-slate-100">
              Forgot Password?
            </h2>
            <p className="text-xs text-slate-400 mt-1">
              Enter your registered work email to receive a 6-digit OTP code
            </p>
          </div>

          {/* Error Banner */}
          <AnimatePresence>
            {error && (
              <motion.div
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: 'auto' }}
                exit={{ opacity: 0, height: 0 }}
                className="mb-4 p-3 bg-red-500/10 border border-red-500/30 rounded-lg text-xs text-red-400 flex items-start gap-2 font-mono"
              >
                <AlertCircle size={15} className="mt-0.5 shrink-0" />
                <span>{error}</span>
              </motion.div>
            )}
          </AnimatePresence>

          {/* Info Banner */}
          {infoMessage && (
            <div className="mb-4 p-3 bg-blue-500/10 border border-blue-500/30 rounded-lg text-xs text-blue-300 flex items-start gap-2 font-mono">
              <CheckCircle2 size={15} className="mt-0.5 shrink-0" />
              <span>{infoMessage}</span>
            </div>
          )}

          <form onSubmit={handleRequestOtp} className="space-y-5" noValidate>
            <div className="space-y-1.5">
              <label className="block text-xs font-medium text-slate-300">
                Registered Work Email
              </label>
              <div className="relative">
                <span className="absolute inset-y-0 left-0 flex items-center pl-3 text-slate-400">
                  <Mail size={15} />
                </span>
                <input
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="name@company.com"
                  className="w-full pl-9 pr-3 py-2.5 bg-slate-900/90 border border-slate-700/80 rounded-lg text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/30 transition-all font-sans"
                />
              </div>
            </div>

            <button
              disabled={loading}
              type="submit"
              className="w-full btn-primary py-2.5 flex items-center justify-center gap-2 text-xs font-semibold mt-2 cursor-pointer"
            >
              {loading ? (
                <RefreshCw size={15} className="animate-spin" />
              ) : (
                <span>Send 6-Digit OTP Code</span>
              )}
              {!loading && <ArrowRight size={14} />}
            </button>
          </form>

          <div className="text-center pt-4 text-xs border-t border-slate-700/60 mt-6">
            <span className="text-slate-400">Remember your password? </span>
            <a href="#/login" className="text-blue-400 hover:text-blue-300 font-semibold">
              Sign In
            </a>
          </div>
        </div>
      </motion.div>
    </div>
  );
};

export default ForgotPassword;

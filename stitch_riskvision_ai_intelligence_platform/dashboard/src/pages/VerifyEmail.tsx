import React, { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import authApi from '../api/auth';
import { CheckCircle2, AlertCircle, RefreshCw, Mail, ArrowRight } from 'lucide-react';
import { RivexaLogo } from '../components/common/RivexaLogo';

export const VerifyEmail: React.FC = () => {
  const [status, setStatus] = useState<'verifying' | 'success' | 'error'>('verifying');
  const [message, setMessage] = useState('Verifying your email token…');
  const [resendEmail, setResendEmail] = useState('');
  const [resending, setResending] = useState(false);
  const [resendMsg, setResendMsg] = useState('');

  useEffect(() => {
    const hashPart = window.location.hash.includes('?') ? window.location.hash.split('?')[1] : '';
    const searchPart = window.location.search ? window.location.search.substring(1) : '';
    const params = new URLSearchParams(hashPart || searchPart);
    const tokenParam = params.get('token');

    if (!tokenParam) {
      setStatus('error');
      setMessage('Missing verification token in link.');
      return;
    }

    verifyToken(tokenParam);
  }, []);

  const verifyToken = async (tStr: string) => {
    setStatus('verifying');
    try {
      const res = await authApi.verifyEmail(tStr);
      setStatus('success');
      setMessage(res.message || 'Email verified successfully! You may now sign in.');
    } catch (err: any) {
      setStatus('error');
      const detail = err.response?.data?.detail || err.response?.data?.message || err.message;
      setMessage(detail || 'Verification failed. Token may be invalid or expired.');
    }
  };

  const handleResend = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!resendEmail.trim()) return;
    setResending(true);
    setResendMsg('');
    try {
      const res = await authApi.resendVerification(resendEmail.trim());
      setResendMsg(res.message || 'Verification email sent! Check your inbox.');
    } catch (err: any) {
      const detail = err.response?.data?.detail || err.response?.data?.message || err.message;
      setResendMsg('Failed: ' + detail);
    } finally {
      setResending(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#0F172A] flex flex-col items-center justify-center p-4 relative overflow-hidden font-sans">
      <div className="w-full max-w-md z-10">
        <div className="flex flex-col items-center mb-8">            <RivexaLogo variant="compact" size={34} alt="RIVEXA" />
          <p className="text-xs text-slate-400 font-medium tracking-wide mt-1">
            Email Verification
          </p>
        </div>

        <div className="bg-[#1E293B]/90 border border-slate-700/60 rounded-xl p-8 shadow-card backdrop-blur-sm">
          {status === 'verifying' && (
            <div className="flex flex-col items-center py-8 space-y-4 text-center">
              <RefreshCw size={36} className="text-blue-400 animate-spin" />
              <p className="text-xs text-slate-300">{message}</p>
            </div>
          )}

          {status === 'success' && (
            <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} className="flex flex-col items-center py-6 space-y-4 text-center">
              <CheckCircle2 size={48} className="text-emerald-400" />
              <h2 className="text-base font-semibold text-slate-100">Verification Complete</h2>
              <p className="text-xs text-slate-300">{message}</p>
              <a
                href="#/login"
                className="w-full btn-primary py-2.5 flex items-center justify-center gap-2 text-xs font-semibold mt-4"
              >
                <span>Proceed to Sign In</span>
                <ArrowRight size={14} />
              </a>
            </motion.div>
          )}

          {status === 'error' && (
            <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} className="space-y-6">
              <div className="flex flex-col items-center py-2 text-center space-y-3">
                <AlertCircle size={40} className="text-red-400" />
                <h2 className="text-base font-semibold text-red-400">Verification Failed</h2>
                <p className="text-xs text-slate-300 leading-relaxed">{message}</p>
              </div>

              <div className="border-t border-slate-700/60 pt-4">
                <p className="text-xs font-medium text-slate-300 mb-3">Request new verification link</p>
                <form onSubmit={handleResend} className="space-y-3">
                  <div className="relative">
                    <span className="absolute inset-y-0 left-0 flex items-center pl-3 text-slate-400 pointer-events-none">
                      <Mail size={15} />
                    </span>
                    <input
                      type="email"
                      required
                      value={resendEmail}
                      onChange={e => setResendEmail(e.target.value)}
                      placeholder="name@company.com"
                      className="w-full pl-9 pr-3 py-2 bg-slate-900/90 border border-slate-700/80 rounded-lg text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/30 transition-all"
                    />
                  </div>
                  <button
                    disabled={resending}
                    type="submit"
                    className="w-full btn-primary py-2.5 flex items-center justify-center gap-2 text-xs font-semibold"
                  >
                    {resending ? <RefreshCw size={14} className="animate-spin" /> : <Mail size={14} />}
                    <span>{resending ? 'Sending…' : 'Resend Verification Link'}</span>
                  </button>
                </form>
                {resendMsg && (
                  <p className="text-xs text-blue-400 mt-2 text-center font-medium">{resendMsg}</p>
                )}
              </div>

              <div className="text-center pt-2">
                <a href="#/login" className="text-xs text-slate-400 hover:text-slate-200">
                  Return to Sign In
                </a>
              </div>
            </motion.div>
          )}
        </div>
      </div>
    </div>
  );
};

export default VerifyEmail;

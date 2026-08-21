import React, { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import authApi from '../api/auth';
import {
  Key,
  Mail,
  RefreshCw,
  AlertCircle,
  CheckCircle2,
  ArrowRight,
  Eye,
  EyeOff,
  Clock,
  RotateCcw } from 'lucide-react';
import { RivexaLogo } from '../components/common/RivexaLogo';

export const ResetPassword: React.FC = () => {
  const [step, setStep] = useState<'request' | 'verify'>('request');
  const [email, setEmail] = useState('');
  const [otpDigits, setOtpDigits] = useState<string[]>(['', '', '', '', '', '']);
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [resendLoading, setResendLoading] = useState(false);
  const [error, setError] = useState('');
  const [infoMessage, setInfoMessage] = useState('');
  const [success, setSuccess] = useState(false);

  // Timers
  const [resendCooldown, setResendCooldown] = useState(0);
  const [otpExpirySeconds, setOtpExpirySeconds] = useState(900); // 15 minutes

  const otpInputRefs = useRef<(HTMLInputElement | null)[]>([]);

  useEffect(() => {
    // Check URL parameters if redirected with otp or token
    const hashPart = window.location.hash.includes('?')
      ? window.location.hash.split('?')[1]
      : '';
    const searchPart = window.location.search
      ? window.location.search.substring(1)
      : '';
    const params = new URLSearchParams(hashPart || searchPart);
    const otpParam = params.get('otp') || params.get('token');

    if (otpParam && otpParam.length === 6 && /^\d+$/.test(otpParam)) {
      setOtpDigits(otpParam.split(''));
      setStep('verify');
    }
  }, []);

  // Resend cooldown timer
  useEffect(() => {
    if (resendCooldown <= 0) return;
    const timer = setInterval(() => {
      setResendCooldown((prev) => prev - 1);
    }, 1000);
    return () => clearInterval(timer);
  }, [resendCooldown]);

  // 15-min OTP expiration countdown
  useEffect(() => {
    if (step !== 'verify') return;
    const timer = setInterval(() => {
      setOtpExpirySeconds((prev) => {
        if (prev <= 1) {
          setError('OTP code has expired. Please request a new code.');
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(timer);
  }, [step]);

  // Handle OTP digit box change
  const handleOtpChange = (index: number, value: string) => {
    if (!/^\d*$/.test(value)) return;

    const newDigits = [...otpDigits];
    newDigits[index] = value.slice(-1); // Take single digit
    setOtpDigits(newDigits);

    // Auto focus next box
    if (value && index < 5) {
      otpInputRefs.current[index + 1]?.focus();
    }
  };

  const handleOtpKeyDown = (
    index: number,
    e: React.KeyboardEvent<HTMLInputElement>
  ) => {
    if (e.key === 'Backspace' && !otpDigits[index] && index > 0) {
      otpInputRefs.current[index - 1]?.focus();
    }
  };

  const handleOtpPaste = (e: React.ClipboardEvent<HTMLInputElement>) => {
    e.preventDefault();
    const pasted = e.clipboardData.getData('text').trim();
    if (/^\d{6}$/.test(pasted)) {
      const digits = pasted.split('');
      setOtpDigits(digits);
      otpInputRefs.current[5]?.focus();
    }
  };

  // Step 1: Request OTP
  const handleRequestOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email || !email.includes('@')) {
      setError('Please enter a valid work email address.');
      return;
    }

    setLoading(true);
    setError('');
    setInfoMessage('');

    try {
      await authApi.requestPasswordReset(email.trim().toLowerCase());
      setStep('verify');
      setResendCooldown(60);
      setOtpExpirySeconds(900);
      setInfoMessage(`A 6-digit OTP code has been dispatched to ${email}. Check your inbox.`);
    } catch (err: any) {
      const msg = err.response?.data?.detail || err.message || 'Failed to send OTP code.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  // Resend OTP handler
  const handleResendOtp = async () => {
    if (resendCooldown > 0 || !email) return;
    setResendLoading(true);
    setError('');
    setInfoMessage('');

    try {
      await authApi.requestPasswordReset(email.trim().toLowerCase());
      setResendCooldown(60);
      setOtpExpirySeconds(900);
      setOtpDigits(['', '', '', '', '', '']);
      setInfoMessage('New 6-digit OTP code dispatched! Check your email.');
    } catch {
      setError('Failed to resend OTP code.');
    } finally {
      setResendLoading(false);
    }
  };

  // Step 2: Confirm OTP & Reset Password
  const handleConfirmReset = async (e: React.FormEvent) => {
    e.preventDefault();
    const otpCode = otpDigits.join('');

    if (otpCode.length !== 6) {
      setError('Please enter the full 6-digit verification code.');
      return;
    }
    if (newPassword.length < 8) {
      setError('Password must be at least 8 characters long.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setError('New passwords do not match.');
      return;
    }

    setLoading(true);
    setError('');

    try {
      await authApi.confirmPasswordReset(otpCode, newPassword);
      setSuccess(true);
      setTimeout(() => {
        window.location.hash = '#/login';
      }, 3000);
    } catch (err: any) {
      const msg =
        err.response?.data?.detail || err.response?.data?.message || err.message;
      setError(msg || 'Failed to update password. Code may be invalid or expired.');
    } finally {
      setLoading(false);
    }
  };

  const formatTimer = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs < 10 ? '0' : ''}${secs}`;
  };

  return (
    <div className="min-h-screen bg-[#0F172A] flex flex-col items-center justify-center p-4 relative overflow-y-auto font-sans">
      <motion.div
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3 }}
        className="w-full max-w-md z-10"
      >
        <div className="flex flex-col items-center mb-8">            <RivexaLogo variant="compact" size={34} alt="RIVEXA" />
          <p className="text-xs text-slate-400 font-medium tracking-wide mt-1">
            Secure OTP Password Reset
          </p>
        </div>

        <div className="bg-[#1E293B]/90 border border-slate-700/60 rounded-xl p-8 shadow-card backdrop-blur-sm">
          {success ? (
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              className="text-center py-6 space-y-4"
            >
              <div className="w-14 h-14 bg-emerald-500/10 border border-emerald-500/30 rounded-full flex items-center justify-center mx-auto text-emerald-400">
                <CheckCircle2 size={36} />
              </div>
              <h2 className="text-lg font-bold text-slate-100">
                Password Successfully Reset
              </h2>
              <p className="text-xs text-slate-300 leading-relaxed max-w-xs mx-auto">
                Your credentials have been updated. All active sessions have been invalidated across devices for your security.
              </p>
              <p className="text-[11px] text-slate-400">
                Redirecting to sign-in page...
              </p>
            </motion.div>
          ) : (
            <div>
              {/* Header */}
              <div className="mb-6 text-center">
                <h2 className="text-base font-semibold text-slate-100">
                  {step === 'request' ? 'Forgot Password?' : 'Enter Verification OTP'}
                </h2>
                <p className="text-xs text-slate-400 mt-1">
                  {step === 'request'
                    ? 'Enter your email to receive a 6-digit OTP code'
                    : 'We sent a 6-digit verification code to your email'}
                </p>
              </div>

              {/* Error Banner */}
              <AnimatePresence>
                {error && (
                  <motion.div
                    initial={{ opacity: 0, height: 0 }}
                    animate={{ opacity: 1, height: 'auto' }}
                    exit={{ opacity: 0, height: 0 }}
                    className="mb-4 p-3 bg-red-500/10 border border-red-500/30 rounded-lg text-xs text-red-400 flex items-start gap-2"
                  >
                    <AlertCircle size={15} className="mt-0.5 shrink-0" />
                    <span>{error}</span>
                  </motion.div>
                )}
              </AnimatePresence>

              {/* Info Banner */}
              {infoMessage && (
                <div className="mb-4 p-3 bg-blue-500/10 border border-blue-500/30 rounded-lg text-xs text-blue-300 flex items-start gap-2">
                  <CheckCircle2 size={15} className="mt-0.5 shrink-0" />
                  <span>{infoMessage}</span>
                </div>
              )}

              {step === 'request' ? (
                /* STEP 1: Request OTP Form */
                <form onSubmit={handleRequestOtp} className="space-y-4" noValidate>
                  <div className="space-y-1.5">
                    <label className="block text-xs font-medium text-slate-300">
                      Work Email Address
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
                      <span>Send 6-Digit OTP Code</span>
                    )}
                    {!loading && <ArrowRight size={14} />}
                  </button>
                </form>
              ) : (
                /* STEP 2: Enter OTP & New Password Form */
                <form onSubmit={handleConfirmReset} className="space-y-5" noValidate>
                  {/* OTP Digit Input Boxes */}
                  <div className="space-y-2">
                    <div className="flex justify-between items-center text-xs">
                      <label className="block font-medium text-slate-300">
                        6-Digit Verification Code (OTP)
                      </label>
                      <span className="text-[11px] text-slate-400 flex items-center gap-1 font-mono">
                        <Clock size={12} />
                        Expires in: {formatTimer(otpExpirySeconds)}
                      </span>
                    </div>

                    <div className="flex gap-2 justify-between">
                      {otpDigits.map((digit, idx) => (
                        <input
                          key={idx}
                          ref={(el) => {
                            otpInputRefs.current[idx] = el;
                          }}
                          type="text"
                          maxLength={1}
                          value={digit}
                          onChange={(e) => handleOtpChange(idx, e.target.value)}
                          onKeyDown={(e) => handleOtpKeyDown(idx, e)}
                          onPaste={handleOtpPaste}
                          className="w-11 h-12 text-center text-lg font-bold font-mono bg-slate-900 border border-slate-700 rounded-lg text-blue-400 focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 transition-all"
                        />
                      ))}
                    </div>

                    {/* Resend OTP */}
                    <div className="flex justify-between items-center pt-1 text-[11px]">
                      <span className="text-slate-400">Didn't receive the code?</span>
                      <button
                        type="button"
                        disabled={resendCooldown > 0 || resendLoading}
                        onClick={handleResendOtp}
                        className="text-blue-400 hover:text-blue-300 font-semibold disabled:opacity-50 flex items-center gap-1"
                      >
                        <RotateCcw size={12} />
                        {resendCooldown > 0
                          ? `Resend in ${resendCooldown}s`
                          : resendLoading
                          ? 'Sending...'
                          : 'Resend OTP'}
                      </button>
                    </div>
                  </div>

                  {/* New Password */}
                  <div className="space-y-1.5">
                    <label className="block text-xs font-medium text-slate-300">
                      New Password
                    </label>
                    <div className="relative">
                      <span className="absolute inset-y-0 left-0 flex items-center pl-3 text-slate-400">
                        <Key size={15} />
                      </span>
                      <input
                        type={showPassword ? 'text' : 'password'}
                        required
                        minLength={8}
                        value={newPassword}
                        onChange={(e) => setNewPassword(e.target.value)}
                        placeholder="••••••••••••"
                        className="w-full pl-9 pr-10 py-2 bg-slate-900/90 border border-slate-700/80 rounded-lg text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/30 transition-all"
                      />
                      <button
                        type="button"
                        onClick={() => setShowPassword((prev) => !prev)}
                        className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-200"
                      >
                        {showPassword ? <EyeOff size={15} /> : <Eye size={15} />}
                      </button>
                    </div>
                  </div>

                  {/* Confirm New Password */}
                  <div className="space-y-1.5">
                    <label className="block text-xs font-medium text-slate-300">
                      Confirm New Password
                    </label>
                    <div className="relative">
                      <span className="absolute inset-y-0 left-0 flex items-center pl-3 text-slate-400">
                        <Key size={15} />
                      </span>
                      <input
                        type={showPassword ? 'text' : 'password'}
                        required
                        minLength={8}
                        value={confirmPassword}
                        onChange={(e) => setConfirmPassword(e.target.value)}
                        placeholder="••••••••••••"
                        className="w-full pl-9 pr-10 py-2 bg-slate-900/90 border border-slate-700/80 rounded-lg text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500/30 transition-all"
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
                      <span>Update Password & Logout Sessions</span>
                    )}
                    {!loading && <ArrowRight size={14} />}
                  </button>
                </form>
              )}

              <div className="text-center pt-4 text-xs border-t border-slate-700/60 mt-4">
                <a href="#/login" className="text-slate-400 hover:text-slate-200 font-medium">
                  ← Return to Sign In
                </a>
              </div>
            </div>
          )}
        </div>
      </motion.div>
    </div>
  );
};

export default ResetPassword;

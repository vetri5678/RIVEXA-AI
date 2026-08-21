import React from 'react';
import { motion } from 'framer-motion';
import { CheckCircle2, ArrowRight } from 'lucide-react';
import { RivexaLogo } from '../components/common/RivexaLogo';

export const ResetPasswordSuccess: React.FC = () => {
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

        {/* Card */}
        <div className="bg-[#1E293B]/90 border border-slate-700/60 rounded-xl p-8 shadow-card backdrop-blur-sm">
          <motion.div
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            className="text-center py-4 space-y-4"
          >
            <div className="w-14 h-14 bg-emerald-500/10 border border-emerald-500/30 rounded-full flex items-center justify-center mx-auto text-emerald-400 shadow-lg shadow-emerald-500/10">
              <CheckCircle2 size={36} />
            </div>

            <h2 className="text-lg font-bold text-slate-100">
              Password Reset Successful
            </h2>

            <p className="text-xs text-slate-300 leading-relaxed max-w-xs mx-auto">
              Your password has been updated successfully. All active sessions have been invalidated across your devices for your security.
            </p>

            <div className="pt-4">
              <a
                href="#/login"
                className="w-full btn-primary py-3 flex items-center justify-center gap-2 text-xs font-bold rounded-xl shadow-lg shadow-blue-500/20"
              >
                <span>Return to Sign In</span>
                <ArrowRight size={14} />
              </a>
            </div>
          </motion.div>
        </div>
      </motion.div>
    </div>
  );
};

export default ResetPasswordSuccess;

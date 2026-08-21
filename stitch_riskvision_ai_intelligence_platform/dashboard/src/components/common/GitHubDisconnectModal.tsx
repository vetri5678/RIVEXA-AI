import React from 'react';
import { AlertTriangle, X } from 'lucide-react';

const GithubIcon: React.FC<{ size?: number }> = ({ size = 24 }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M15 22v-4a4.8 4.8 0 0 0-1-3.5c3 0 6-2 6-5.5.08-1.25-.27-2.48-1-3.5.28-1.15.28-2.35 0-3.5 0 0-1 0-3 1.5-2.64-.5-5.36-.5-8 0C6 2 5 2 5 2c-.3 1.15-.3 2.35 0 3.5A5.403 5.403 0 0 0 4 9c0 3.5 3 5.5 6 5.5-.39.49-.68 1.05-.85 1.65-.17.6-.22 1.23-.15 1.85v4" />
    <path d="M9 18c-4.51 2-5-2-7-2" />
  </svg>
);

interface GitHubDisconnectModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => Promise<void> | void;
  isPending?: boolean;
}

export const GitHubDisconnectModal: React.FC<GitHubDisconnectModalProps> = ({
  isOpen,
  onClose,
  onConfirm,
  isPending = false,
}) => {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm animate-fadeIn">
      <div
        className="w-full max-w-md bg-[#0b1021] border border-red-500/30 rounded-2xl p-6 shadow-2xl relative overflow-hidden text-slate-100"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Glow accent */}
        <div className="absolute -top-16 -right-16 w-36 h-36 bg-red-500/10 rounded-full blur-2xl pointer-events-none" />

        {/* Close Button */}
        <button
          onClick={onClose}
          disabled={isPending}
          className="absolute top-4 right-4 p-1 rounded-lg text-slate-400 hover:text-white hover:bg-white/10 transition-colors disabled:opacity-50"
          aria-label="Close"
        >
          <X size={18} />
        </button>

        {/* Header Icon */}
        <div className="flex items-center gap-3 mb-4">
          <div className="p-3 rounded-xl bg-red-500/10 border border-red-500/30 text-red-400 shrink-0">
            <GithubIcon size={24} />
          </div>
          <div>
            <h3 className="text-lg font-bold text-white font-sans flex items-center gap-2">
              Disconnect GitHub?
            </h3>
            <span className="text-xs text-red-400/90 font-mono flex items-center gap-1 mt-0.5">
              <AlertTriangle size={12} /> Irreversible for current session
            </span>
          </div>
        </div>

        {/* Modal Description */}
        <div className="text-xs text-slate-300 space-y-2 mb-6 leading-relaxed font-sans bg-white/[0.02] p-3.5 rounded-xl border border-white/[0.06]">
          <p>
            Are you sure you want to disconnect your GitHub account?
          </p>
          <p className="text-slate-400">
            Repository synchronization, failure hazard forecasting, and GitHub-powered AI risk analytics will no longer be available for your account until you connect GitHub again.
          </p>
        </div>

        {/* Actions */}
        <div className="flex items-center justify-end gap-3 pt-2">
          <button
            type="button"
            onClick={onClose}
            disabled={isPending}
            className="px-4 py-2 rounded-xl text-xs font-semibold text-slate-300 bg-white/[0.06] hover:bg-white/10 border border-white/10 transition-all disabled:opacity-50 cursor-pointer"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={isPending}
            className="px-4 py-2 rounded-xl text-xs font-bold font-mono text-white bg-red-600 hover:bg-red-500 border border-red-400/40 shadow-lg shadow-red-600/20 transition-all disabled:opacity-50 flex items-center gap-2 cursor-pointer"
          >
            {isPending ? (
              <>
                <span className="w-3 h-3 rounded-full border-2 border-white border-t-transparent animate-spin" />
                Disconnecting...
              </>
            ) : (
              'Disconnect GitHub'
            )}
          </button>
        </div>
      </div>
    </div>
  );
};

export default GitHubDisconnectModal;

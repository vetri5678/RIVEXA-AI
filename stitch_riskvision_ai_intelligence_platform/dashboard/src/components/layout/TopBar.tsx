import React, { useState } from 'react';
import { Search, Bell, Play, RefreshCw, User as UserIcon, Command, Menu } from 'lucide-react';
import { useAlerts, useRetrainMutation } from '../../hooks/useDashboard';
import { useMLVersion } from '../../hooks/useMLPrediction';

import { getStoredUser, getUserRoleDisplay } from '../../utils/auth';

interface TopBarProps {
  onSearchChange: (val: string) => void;
  searchValue: string;
  onOpenNotifications: () => void;
  onQuickAction: (action: string) => void;
  collapsed?: boolean;
  onToggleMobileMenu?: () => void;
}

export const TopBar: React.FC<TopBarProps> = ({
  onSearchChange,
  searchValue,
  onOpenNotifications,
  onQuickAction,
  collapsed = false,
  onToggleMobileMenu,
}) => {
  const { data: alerts } = useAlerts();
  const { data: mlVersion } = useMLVersion();
  const retrainMutation = useRetrainMutation();
  const [retraining, setRetraining] = useState(false);

  const modelChipText = `${mlVersion?.modelName || 'XGBoost'} ${mlVersion?.modelVersion || 'v2.4'} · Operational`;

  const handleRetrain = async () => {
    setRetraining(true);
    try {
      await retrainMutation.mutateAsync({});
      alert('Model retrain command issued successfully.');
    } catch (e) {
      console.error(e);
    } finally {
      setRetraining(false);
    }
  };

  const user = getStoredUser();
  const userRoleDisplay = getUserRoleDisplay(user);

  return (
    <header
      className={`h-16 bg-[#050816]/85 backdrop-blur-2xl border-b border-white/[0.08] fixed top-0 right-0 z-20 flex items-center justify-between px-3 sm:px-6 transition-all duration-300 shadow-lg ${
        collapsed ? 'left-0 lg:left-16' : 'left-0 lg:left-64'
      }`}
    >
      {/* Search Bar & Command Palette Hint */}
      <div className="flex items-center gap-2 sm:gap-3 flex-1 max-w-[200px] sm:max-w-xs md:max-w-md">
        {onToggleMobileMenu && (
          <button
            onClick={onToggleMobileMenu}
            className="lg:hidden p-2 rounded-xl bg-white/[0.04] border border-white/[0.08] text-slate-300 hover:text-white transition-all cursor-pointer shrink-0"
            aria-label="Open Mobile Menu"
          >
            <Menu size={18} />
          </button>
        )}

        <div className="relative flex-1">
          <span className="absolute inset-y-0 left-0 flex items-center pl-3 pointer-events-none text-slate-400">
            <Search size={14} />
          </span>
          <input
            type="text"
            value={searchValue}
            onChange={(e) => onSearchChange(e.target.value)}
            placeholder="Search..."
            className="w-full pl-8 sm:pl-9 pr-8 sm:pr-12 py-1.5 sm:py-2 bg-white/[0.04] border border-white/[0.08] rounded-xl text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-blue-500/60 focus:ring-2 focus:ring-blue-500/20 focus:bg-white/[0.06] transition-all duration-200"
          />
          <div className="absolute inset-y-0 right-0 hidden sm:flex items-center pr-3 pointer-events-none">
            <kbd className="inline-flex items-center gap-0.5 px-1.5 py-0.5 text-[10px] font-mono font-medium text-slate-400 bg-white/[0.08] border border-white/[0.1] rounded">
              <Command size={10} /> K
            </kbd>
          </div>
        </div>
      </div>

      {/* Right Controls Panel */}
      <div className="flex items-center gap-3">
        {/* Live AI Status Chip */}
        <div className="hidden lg:flex items-center gap-2 px-3 py-1.5 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-xs text-emerald-400 font-mono">
          <span className="relative flex h-2 w-2">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75" />
            <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500" />
          </span>
          <span className="font-semibold text-[11px]">{modelChipText}</span>
        </div>

        {/* Quick Action Buttons */}
        <div className="flex items-center gap-1.5 sm:gap-2">
          <button
            onClick={() => onQuickAction('predict')}
            className="btn-primary text-xs py-1.5 px-2.5 sm:px-3 flex items-center gap-1.5 rounded-xl cursor-pointer"
          >
            <Play size={13} className="fill-white" />
            <span className="hidden xs:inline sm:inline">Predict</span>
          </button>
          
          <button
            disabled={retraining}
            onClick={handleRetrain}
            className="btn-secondary text-xs py-1.5 px-2.5 sm:px-3 flex items-center gap-1.5 rounded-xl cursor-pointer"
          >
            <RefreshCw size={13} className={retraining ? 'animate-spin text-cyan-400' : ''} />
            <span className="hidden md:inline">Retrain</span>
          </button>
        </div>

        {/* Notifications Drawer Button */}
        <button
          onClick={onOpenNotifications}
          className="relative p-2 rounded-xl bg-white/[0.04] border border-white/[0.08] hover:border-white/[0.18] text-slate-300 hover:text-white transition-all cursor-pointer"
          aria-label="View notifications"
        >
          <Bell size={16} />
          {alerts && alerts.unread_count > 0 && (
            <span className="absolute -top-1 -right-1 flex h-4 w-4 items-center justify-center rounded-full bg-gradient-to-r from-red-500 to-pink-500 text-[9px] font-bold text-white shadow-lg shadow-red-500/40">
              {alerts.unread_count}
            </span>
          )}
        </button>

        {/* User Profile Pill */}
        <div className="flex items-center gap-3 pl-3 border-l border-white/[0.08]">
          <div className="text-right hidden md:block">
            <h4 className="text-xs font-semibold text-slate-200 leading-tight">{user.full_name || 'User'}</h4>
            <p className="text-[10px] text-cyan-400 font-mono font-medium uppercase tracking-wider">
              {userRoleDisplay}
            </p>
          </div>
          
          <div className="relative w-8 h-8 rounded-xl bg-gradient-to-tr from-blue-600 to-cyan-400 p-[1px] shadow-[0_0_12px_rgba(56,189,248,0.3)] cursor-pointer">
            <div className="w-full h-full rounded-[11px] bg-[#0B1220] flex items-center justify-center text-cyan-400 font-bold text-xs overflow-hidden">
              {user.avatar_url ? (
                <img src={user.avatar_url} alt="Avatar" className="w-full h-full object-cover" />
              ) : (
                <span>{user.full_name ? user.full_name.charAt(0).toUpperCase() : <UserIcon size={14} />}</span>
              )}
            </div>
          </div>
        </div>
      </div>
    </header>
  );
};

export default TopBar;

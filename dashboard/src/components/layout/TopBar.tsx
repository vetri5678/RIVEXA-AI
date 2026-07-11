import React, { useState } from 'react';
import { Search, Bell, Shield, RefreshCw } from 'lucide-react';
import { useAlerts, useRetrainMutation } from '../../hooks/useDashboard';

interface TopBarProps {
  onSearchChange: (val: string) => void;
  searchValue: string;
  onOpenNotifications: () => void;
  onQuickAction: (action: string) => void;
}

export const TopBar: React.FC<TopBarProps> = ({
  onSearchChange,
  searchValue,
  onOpenNotifications,
  onQuickAction,
}) => {
  const { data: alerts } = useAlerts();
  const retrainMutation = useRetrainMutation();
  const [retraining, setRetraining] = useState(false);

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

  const userRaw = localStorage.getItem('rv_user');
  const user = userRaw ? JSON.parse(userRaw) : { full_name: 'Administrator', role: 'SUPER_ADMIN' };

  return (
    <header className="h-20 bg-cyber-950/40 border-b border-glass-border fixed top-0 right-0 left-64 z-20 flex items-center justify-between px-8 backdrop-blur-md">
      {/* Search Input */}
      <div className="relative w-96">
        <span className="absolute inset-y-0 left-0 flex items-center pl-3 pointer-events-none text-slate-500">
          <Search size={16} />
        </span>
        <input
          type="text"
          value={searchValue}
          onChange={(e) => onSearchChange(e.target.value)}
          placeholder="Filter Projects, Repositories, Developers..."
          className="w-full pl-10 glass-input focus:shadow-[0_0_15px_rgba(0,212,255,0.1)]"
        />
      </div>

      {/* Right Controls Panel */}
      <div className="flex items-center gap-6">
        {/* Quick actions bar */}
        <div className="flex items-center gap-2">
          <button
            onClick={() => onQuickAction('predict')}
            className="btn-cyber-primary text-xs py-1.5 px-3"
          >
            <Shield size={14} />
            RUN PREDICTION
          </button>
          <button
            disabled={retraining}
            onClick={handleRetrain}
            className="btn-cyber-secondary text-xs py-1.5 px-3"
          >
            <RefreshCw size={14} className={retraining ? 'animate-spin' : ''} />
            RETRAIN CORE
          </button>
        </div>

        {/* Notifications Toggle */}
        <button
          onClick={onOpenNotifications}
          className="relative p-2 bg-cyber-900/60 border border-glass-border hover:border-neon-blue/30 rounded-lg text-slate-300 hover:text-neon-blue transition-all duration-300"
        >
          <Bell size={18} />
          {alerts && alerts.unread_count > 0 && (
            <span className="absolute -top-1.5 -right-1.5 flex h-5 w-5 items-center justify-center rounded-full bg-neon-pink text-[10px] font-bold font-mono text-white animate-pulse">
              {alerts.unread_count}
            </span>
          )}
        </button>

        {/* User Card */}
        <div className="flex items-center gap-3 pl-4 border-l border-glass-border">
          <div className="text-right">
            <h4 className="text-xs font-mono font-bold text-slate-200">{user.full_name}</h4>
            <p className="text-[9px] font-mono text-neon-blue tracking-wider uppercase">
              {user.role}
            </p>
          </div>
          <div className="h-9 w-9 rounded-lg bg-cyber-800 border border-glass-border flex items-center justify-center text-neon-blue font-mono font-bold shadow-[0_0_10px_rgba(0,212,255,0.1)]">
            AD
          </div>
        </div>
      </div>
    </header>
  );
};
export default TopBar;

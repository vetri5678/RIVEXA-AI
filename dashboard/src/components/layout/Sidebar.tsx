import React from 'react';
import { LayoutDashboard, ShieldAlert, Cpu, BrainCircuit, Activity, FileDown, LogOut } from 'lucide-react';
import { useLocation } from 'react-router-dom';

interface SidebarProps {
  onLogout: () => void;
}

export const Sidebar: React.FC<SidebarProps> = ({ onLogout }) => {
  const location = useLocation();

  const menuItems = [
    { name: 'AI Command Center', icon: LayoutDashboard, path: '/' },
    { name: 'Telemetry Stream', icon: Activity, path: '/telemetry' },
    { name: 'System Core', icon: Cpu, path: '/system' },
  ];

  return (
    <aside className="w-64 bg-cyber-900/40 border-r border-glass-border flex flex-col h-screen fixed left-0 top-0 z-30 backdrop-blur-md">
      {/* Brand Header */}
      <div className="p-6 border-b border-glass-border flex items-center gap-3">
        <BrainCircuit size={28} className="text-neon-blue animate-pulse-slow" />
        <div>
          <h1 className="text-md font-mono font-bold tracking-wider text-slate-100 glow-text-blue">
            GRAVEYARD
          </h1>
          <p className="text-[10px] font-mono tracking-widest text-neon-blue uppercase">
            AI Predictor
          </p>
        </div>
      </div>

      {/* Nav Link List */}
      <nav className="flex-1 px-4 py-6 space-y-2">
        {menuItems.map((item) => {
          const isActive = location.pathname === item.path;
          return (
            <a
              key={item.name}
              href={item.path === '/' ? '#/' : `#${item.path}`}
              className={`flex items-center gap-3 px-4 py-3 rounded-lg font-mono text-sm font-semibold transition-all duration-300 ${
                isActive
                  ? 'bg-neon-blue/10 text-neon-blue border border-neon-blue/30 shadow-[0_0_15px_rgba(0,212,255,0.1)]'
                  : 'text-slate-400 hover:text-slate-100 hover:bg-cyber-800/40 border border-transparent'
              }`}
            >
              <item.icon size={18} />
              {item.name}
            </a>
          );
        })}
      </nav>

      {/* Sidebar Footer Controls */}
      <div className="p-4 border-t border-glass-border">
        <button
          onClick={onLogout}
          className="w-full flex items-center gap-3 px-4 py-3 text-slate-400 hover:text-neon-pink hover:bg-neon-pink/10 border border-transparent hover:border-neon-pink/20 rounded-lg font-mono text-sm font-semibold transition-all duration-300"
        >
          <LogOut size={18} />
          SYSTEM EXIT
        </button>
      </div>
    </aside>
  );
};
export default Sidebar;

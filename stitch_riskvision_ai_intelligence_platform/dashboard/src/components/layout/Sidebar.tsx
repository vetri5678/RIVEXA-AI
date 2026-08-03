import React from 'react';
import {
  LayoutDashboard,
  GitBranch,
  Activity,
  Cpu,
  User,
  LogOut,
  ShieldCheck,
  ChevronLeft,
  ChevronRight,
  Layers,
  Sparkles,
  Database,
  Hammer,
  CheckCircle,
  FileText,
} from 'lucide-react';
import { useLocation } from 'react-router-dom';

interface SidebarProps {
  onLogout: () => void;
  collapsed?: boolean;
  onToggleCollapse?: () => void;
}

interface NavItem {
  name: string;
  icon: React.ComponentType<{ size?: number; className?: string }>;
  path: string;
  badge?: string;
  live?: boolean;
}

interface MenuSection {
  group: string;
  items: NavItem[];
}

export const Sidebar: React.FC<SidebarProps> = ({
  onLogout,
  collapsed = false,
  onToggleCollapse,
}) => {
  const location = useLocation();

  const menuSections: MenuSection[] = [
    {
      group: 'INTELLIGENCE OS',
      items: [
        { name: 'Dashboard', icon: LayoutDashboard, path: '/' },
        { name: 'Repositories', icon: GitBranch, path: '/repositories', badge: '12' },
      ],
    },
    {
      group: 'NEURAL PIPELINE',
      items: [
        { name: 'Repo Sync', icon: Database, path: '/pipeline/repository-sync' },
        { name: 'Feature Extract', icon: Cpu, path: '/pipeline/extract' },
        { name: 'Data Cleanse', icon: Hammer, path: '/pipeline/cleanse' },
        { name: 'Model Engine', icon: Cpu, path: '/pipeline/model-engine' },
        { name: 'Risk Inference', icon: CheckCircle, path: '/pipeline/inference' },
        { name: 'SHAP (XAI)', icon: FileText, path: '/pipeline/shap' },
      ],
    },
    {
      group: 'TELEMETRY & CORE',
      items: [
        { name: 'Telemetry Stream', icon: Activity, path: '/telemetry', live: true },
        { name: 'System Core', icon: Cpu, path: '/system' },
      ],
    },
    {
      group: 'ACCOUNT',
      items: [
        { name: 'User Profile', icon: User, path: '/profile' },
      ],
    },
  ];

  const userRaw = localStorage.getItem('rv_user');
  const user = userRaw ? JSON.parse(userRaw) : { full_name: 'Enterprise Admin', role: 'SUPER_ADMIN', email: '' };

  return (
    <aside
      className={`fixed left-0 top-0 z-30 h-screen bg-[#0B1220]/90 backdrop-blur-2xl border-r border-white/[0.08] flex flex-col transition-all duration-300 shadow-2xl select-none ${
        collapsed ? 'w-16' : 'w-64'
      }`}
    >
      {/* Brand & Workspace Header */}
      <div className="h-16 px-4 border-b border-white/[0.06] flex items-center justify-between shrink-0">
        <div className="flex items-center gap-3 overflow-hidden">
          <div className="relative flex items-center justify-center p-2 rounded-xl bg-gradient-to-br from-blue-500/20 to-cyan-500/10 border border-blue-500/30 text-blue-400 shrink-0 shadow-[0_0_15px_rgba(59,130,246,0.3)]">
            <ShieldCheck size={20} className="text-[#38BDF8]" />
            <span className="absolute -top-0.5 -right-0.5 w-2 h-2 rounded-full bg-cyan-400 animate-pulse" />
          </div>
          {!collapsed && (
            <div className="whitespace-nowrap">
              <div className="flex items-center gap-1.5">
                <h1 className="text-sm font-extrabold text-white tracking-tight font-sans">
                  RiskVision <span className="text-transparent bg-clip-text bg-gradient-to-r from-blue-400 to-cyan-400">AI</span>
                </h1>
                <span className="px-1.5 py-0.2 rounded text-[9px] font-mono font-bold bg-blue-500/20 text-blue-300 border border-blue-500/30">v2.4</span>
              </div>
              <p className="text-[10px] text-slate-400 font-mono font-medium tracking-wide uppercase">
                Predictive Risk OS
              </p>
            </div>
          )}
        </div>

        {onToggleCollapse && (
          <button
            onClick={onToggleCollapse}
            className="p-1.5 text-slate-400 hover:text-white hover:bg-white/[0.08] rounded-lg transition-all duration-200 cursor-pointer"
            aria-label={collapsed ? 'Expand Sidebar' : 'Collapse Sidebar'}
          >
            {collapsed ? <ChevronRight size={16} /> : <ChevronLeft size={16} />}
          </button>
        )}
      </div>

      {/* Workspace Selector (Expanded view) */}
      {!collapsed && (
        <div className="px-3 pt-3 pb-1">
          <div className="p-2.5 rounded-xl bg-white/[0.03] border border-white/[0.06] hover:border-blue-500/30 transition-all cursor-pointer flex items-center justify-between group">
            <div className="flex items-center gap-2.5 overflow-hidden">
              <div className="w-6 h-6 rounded-lg bg-gradient-to-tr from-purple-600 to-blue-500 flex items-center justify-center text-[10px] font-bold text-white shrink-0">
                HQ
              </div>
              <div className="truncate">
                <p className="text-xs font-semibold text-slate-200 group-hover:text-white transition-colors truncate">Enterprise Workspace</p>
                <p className="text-[10px] text-slate-400 truncate font-mono">12 Repositories Active</p>
              </div>
            </div>
            <Layers size={13} className="text-slate-500 group-hover:text-blue-400 transition-colors shrink-0" />
          </div>
        </div>
      )}

      {/* Navigation Links Grouping */}
      <nav className="flex-1 px-3 py-3 space-y-4 overflow-y-auto no-scrollbar">
        {menuSections.map((section, idx) => (
          <div key={idx} className="space-y-1">
            {!collapsed && (
              <p className="px-3 text-[10px] font-mono font-semibold uppercase tracking-wider text-slate-500 mb-1">
                {section.group}
              </p>
            )}
            {section.items.map((item) => {
              const isActive = location.pathname === item.path;
              return (
                <a
                  key={item.name}
                  href={item.path === '/' ? '#/' : `#${item.path}`}
                  title={collapsed ? item.name : undefined}
                  className={`group relative flex items-center gap-3 px-3 py-2.5 rounded-xl text-xs font-medium transition-all duration-200 ${
                    isActive
                      ? 'bg-gradient-to-r from-blue-500/15 to-cyan-500/5 text-blue-300 font-semibold border border-blue-500/30 shadow-[0_0_15px_rgba(59,130,246,0.15)]'
                      : 'text-slate-400 hover:text-slate-100 hover:bg-white/[0.05] border border-transparent'
                  }`}
                >
                  {/* Left Active Indicator Bar */}
                  {isActive && (
                    <span className="absolute left-0 top-2 bottom-2 w-1 rounded-r-full bg-gradient-to-b from-blue-400 to-cyan-400 shadow-[0_0_8px_rgba(56,189,248,0.8)]" />
                  )}

                  <item.icon
                    size={18}
                    className={`shrink-0 transition-transform duration-200 group-hover:scale-110 ${
                      isActive ? 'text-cyan-400' : 'text-slate-400 group-hover:text-slate-200'
                    }`}
                  />

                  {!collapsed && (
                    <div className="flex-1 flex items-center justify-between overflow-hidden">
                      <span className="truncate">{item.name}</span>
                      {item.live && (
                        <span className="flex items-center gap-1 px-1.5 py-0.5 rounded-full text-[9px] font-mono font-bold bg-emerald-500/15 text-emerald-400 border border-emerald-500/30">
                          <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
                          LIVE
                        </span>
                      )}
                      {item.badge && (
                        <span className="px-1.5 py-0.5 rounded-md text-[10px] font-mono font-bold bg-slate-800 text-slate-400 border border-slate-700">
                          {item.badge}
                        </span>
                      )}
                    </div>
                  )}
                </a>
              );
            })}
          </div>
        ))}
      </nav>

      {/* User Profile Card & Sign Out Footer */}
      <div className="p-3 border-t border-white/[0.06] space-y-2 shrink-0 bg-[#050816]/40">
        {!collapsed && (
          <div className="p-2.5 rounded-xl bg-white/[0.03] border border-white/[0.06] flex items-center justify-between">
            <div className="flex items-center gap-2.5 overflow-hidden">
              <div className="w-7 h-7 rounded-lg bg-gradient-to-tr from-blue-600 to-cyan-400 text-white flex items-center justify-center font-bold text-xs shrink-0 shadow-[0_0_10px_rgba(56,189,248,0.3)]">
                {user.full_name ? user.full_name.charAt(0).toUpperCase() : 'U'}
              </div>
              <div className="truncate">
                <p className="text-xs font-semibold text-slate-200 truncate">{user.full_name || 'User'}</p>
                <p className="text-[10px] text-cyan-400 font-mono font-medium uppercase truncate">{user.role || 'Viewer'}</p>
              </div>
            </div>
            <Sparkles size={14} className="text-cyan-400 shrink-0 animate-pulse" />
          </div>
        )}

        <button
          onClick={onLogout}
          title={collapsed ? 'Sign Out' : undefined}
          className="w-full flex items-center justify-center gap-2.5 px-3 py-2 text-xs font-medium text-slate-400 hover:text-red-400 hover:bg-red-500/10 rounded-xl transition-all duration-200 border border-transparent hover:border-red-500/20 cursor-pointer"
        >
          <LogOut size={16} className="shrink-0" />
          {!collapsed && <span>Sign Out Session</span>}
        </button>
      </div>
    </aside>
  );
};

export default Sidebar;

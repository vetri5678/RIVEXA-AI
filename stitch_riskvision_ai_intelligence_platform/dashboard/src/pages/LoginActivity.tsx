import React, { useState } from 'react';
import { Navigate } from 'react-router-dom';
import DashboardLayout from '../components/layout/DashboardLayout';
import WidgetWrapper from '../components/dashboard/Common/WidgetWrapper';
import { useLoginHistory } from '../hooks/useDashboard';
import { getStoredUser, isAdminUser } from '../utils/auth';
import { 
  ShieldAlert, 
  Search, 
  RefreshCw, 
  ChevronLeft, 
  ChevronRight, 
  CheckCircle2, 
  XCircle, 
  Globe, 
  Laptop, 
  Key,
  Copy,
  Check
} from 'lucide-react';

interface LoginHistoryItem {
  id: string;
  user_id: string | null;
  username: string | null;
  full_name: string | null;
  email: string;
  ip_address: string;
  user_agent: string;
  provider: string;
  browser: string;
  operating_system: string;
  session_id: string | null;
  success: boolean;
  failure_reason: string | null;
  created_at: string | null;
}

export const LoginActivity: React.FC = () => {
  const user = getStoredUser();
  const isAdmin = isAdminUser(user);

  const [page, setPage] = useState(0);
  const [pageSize] = useState(15);
  const [searchTerm, setSearchTerm] = useState('');
  const [copySuccess, setCopySuccess] = useState<string | null>(null);

  const { data, isLoading, isError, refetch } = useLoginHistory(page, pageSize, { enabled: isAdmin });

  if (!isAdmin) {
    sessionStorage.setItem('rv_toast_msg', 'Administrator access required');
    sessionStorage.setItem('rv_toast_type', 'error');
    return <Navigate to="/dashboard" replace />;
  }

  const handleCopy = (text: string, id: string) => {
    navigator.clipboard.writeText(text);
    setCopySuccess(id);
    setTimeout(() => setCopySuccess(null), 2000);
  };

  const formatDateTime = (dateStr: string | null) => {
    if (!dateStr) return 'N/A';
    try {
      const d = new Date(dateStr);
      return d.toLocaleString(undefined, {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
      });
    } catch {
      return dateStr;
    }
  };

  const getProviderBadge = (provider: string) => {
    const p = provider ? provider.toLowerCase() : 'email';
    if (p === 'google' || p.includes('google')) {
      return (
        <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-indigo-500/15 text-indigo-400 border border-indigo-500/30 shadow-[0_0_10px_rgba(99,102,241,0.15)]">
          Google OAuth
        </span>
      );
    }
    if (p === 'github' || p.includes('github')) {
      return (
        <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-cyan-500/15 text-cyan-400 border border-cyan-500/30 shadow-[0_0_10px_rgba(6,182,212,0.15)]">
          GitHub OAuth
        </span>
      );
    }
    return (
      <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-blue-500/15 text-blue-400 border border-blue-500/30 flex items-center gap-1 w-max shadow-[0_0_10px_rgba(59,130,246,0.15)]">
        <Key size={10} /> Credentials
      </span>
    );
  };

  const getStatusBadge = (item: LoginHistoryItem) => {
    if (item.success) {
      return (
        <span className="px-2.5 py-0.5 rounded-full text-[10px] font-mono font-bold bg-emerald-500/15 text-emerald-400 border border-emerald-500/30 flex items-center gap-1 w-max shadow-[0_0_10px_rgba(16,185,129,0.2)]">
          <CheckCircle2 size={12} className="text-emerald-400" /> SUCCESS
        </span>
      );
    }
    return (
      <span 
        className="px-2.5 py-0.5 rounded-full text-[10px] font-mono font-bold bg-red-500/15 text-red-400 border border-red-500/30 flex items-center gap-1 w-max cursor-help shadow-[0_0_10px_rgba(239,68,68,0.2)]"
        title={item.failure_reason || 'Unknown failure category'}
      >
        <XCircle size={12} className="text-red-400" /> FAILED ({item.failure_reason || 'AUTH_ERR'})
      </span>
    );
  };

  const logs: LoginHistoryItem[] = data?.items || [];
  const total = data?.total || 0;
  const totalPages = data?.total_pages || 1;

  // Filter logs locally based on search term
  const filteredLogs = logs.filter(item => {
    const term = searchTerm.toLowerCase();
    return (
      item.email.toLowerCase().includes(term) ||
      (item.full_name && item.full_name.toLowerCase().includes(term)) ||
      (item.username && item.username.toLowerCase().includes(term)) ||
      item.ip_address.includes(term) ||
      (item.failure_reason && item.failure_reason.toLowerCase().includes(term)) ||
      (item.browser && item.browser.toLowerCase().includes(term)) ||
      (item.operating_system && item.operating_system.toLowerCase().includes(term))
    );
  });

  return (
    <DashboardLayout
      onSearchChange={setSearchTerm}
      searchValue={searchTerm}
      onQuickAction={() => refetch()}
    >
      {/* Page Header */}
      <div className="glass-strong rounded-2xl p-6 mb-8 border border-white/[0.08] flex flex-col md:flex-row md:items-center justify-between gap-4 shadow-xl relative overflow-hidden">
        {/* Glow */}
        <div className="absolute -top-12 -right-12 w-48 h-48 bg-purple-500/10 rounded-full blur-3xl pointer-events-none" />
        
        <div className="flex items-center gap-4 z-10">
          <div className="p-3.5 rounded-2xl bg-purple-500/15 border border-purple-500/30 text-purple-400 shrink-0 shadow-[0_0_20px_rgba(168,85,247,0.2)]">
            <ShieldAlert size={28} />
          </div>
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-xl font-bold tracking-tight text-white font-sans">
                Admin Audit Operations: Security Events Center
              </h1>
              <span className="px-2.5 py-0.5 rounded-full text-[10px] font-mono font-bold bg-purple-500/15 text-purple-400 border border-purple-500/30 shadow-[0_0_10px_rgba(168,85,247,0.15)] animate-pulse">
                MONITOR ACTIVE
              </span>
            </div>
            <p className="text-xs text-slate-400 font-sans mt-1">
              Authoritative real-time transaction activity feed. Tracks session handshakes, browser signatures, OAuth handshakes, and access lock metrics.
            </p>
          </div>
        </div>

        {/* Action Button */}
        <button
          onClick={() => refetch()}
          className="btn-secondary py-2 px-4 text-xs font-mono font-bold rounded-xl text-purple-400 border-purple-500/30 hover:border-purple-500/60 hover:bg-purple-500/10 flex items-center gap-1.5 cursor-pointer transition-all shadow-[0_0_15px_rgba(168,85,247,0.15)] z-10"
        >
          <RefreshCw size={14} className={isLoading ? 'animate-spin' : ''} />
          <span>FORCE REFRESH</span>
        </button>
      </div>

      {/* Main Logs Table Card */}
      <div className="font-sans">
        <WidgetWrapper
          title="Administrative Login & Session Audit Logs"
          subtitle="Direct ledger entries retrieved dynamically from core database"
          isLoading={isLoading}
          isError={isError}
        >
          {/* Table Toolbar */}
          <div className="flex flex-col sm:flex-row items-center justify-between gap-4 p-4 border-b border-white/[0.06] bg-white/[0.01]">
            <div className="relative w-full sm:w-80">
              <span className="absolute inset-y-0 left-0 flex items-center pl-3 text-slate-500 pointer-events-none">
                <Search size={14} />
              </span>
              <input
                type="text"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                placeholder="Search by email, username, IP..."
                className="w-full pl-9 pr-4 py-2 text-xs rounded-xl bg-white/[0.03] border border-white/[0.08] text-slate-200 placeholder-slate-500 focus:outline-none focus:border-purple-500/50 transition-colors"
              />
            </div>
            <div className="text-slate-400 text-xs font-mono">
              Total Recorded Logs: <span className="text-purple-400 font-bold">{total}</span>
            </div>
          </div>

          {/* Table Container */}
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="border-b border-white/[0.06] bg-white/[0.02] text-slate-400 text-[10px] font-semibold tracking-wider uppercase font-mono">
                  <th className="py-4 px-6">User / Profile</th>
                  <th className="py-4 px-4">Login Method</th>
                  <th className="py-4 px-4">Timestamp</th>
                  <th className="py-4 px-4">IP Address</th>
                  <th className="py-4 px-4">Browser & OS</th>
                  <th className="py-4 px-6 text-center">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/[0.04] text-xs">
                {filteredLogs.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="py-12 text-center text-slate-500 font-mono">
                      No security records found matching filters in active buffer.
                    </td>
                  </tr>
                ) : (
                  filteredLogs.map((item) => (
                    <tr 
                      key={item.id} 
                      className="hover:bg-white/[0.02] transition-colors border-b border-white/[0.02]"
                    >
                      {/* User details */}
                      <td className="py-4 px-6 flex items-center gap-3">
                        <div className="w-8 h-8 rounded-lg bg-gradient-to-tr from-purple-600 to-indigo-500 flex items-center justify-center font-bold text-white shrink-0 shadow-[0_0_10px_rgba(168,85,247,0.3)]">
                          {item.full_name ? item.full_name.charAt(0).toUpperCase() : 'U'}
                        </div>
                        <div className="truncate max-w-[180px]">
                          <p className="font-semibold text-slate-200 truncate">
                            {item.full_name || item.username || 'Non-Existent User'}
                          </p>
                          <p className="text-[10px] text-slate-400 truncate font-mono">
                            {item.email}
                          </p>
                        </div>
                      </td>

                      {/* Login Method */}
                      <td className="py-4 px-4">
                        {getProviderBadge(item.provider)}
                      </td>

                      {/* Timestamp */}
                      <td className="py-4 px-4 font-mono text-slate-300">
                        {formatDateTime(item.created_at)}
                      </td>

                      {/* IP Address */}
                      <td className="py-4 px-4">
                        <div className="flex items-center gap-2 font-mono text-slate-300">
                          <Globe size={12} className="text-slate-500 shrink-0" />
                          <span>{item.ip_address}</span>
                          <button
                            onClick={() => handleCopy(item.ip_address, item.id)}
                            className="p-1 text-slate-500 hover:text-slate-200 transition-colors rounded hover:bg-white/5 cursor-pointer"
                            title="Copy IP Address"
                          >
                            {copySuccess === item.id ? <Check size={10} className="text-emerald-400" /> : <Copy size={10} />}
                          </button>
                        </div>
                      </td>

                      {/* Device & Agent info */}
                      <td className="py-4 px-4 text-slate-300">
                        <div className="flex items-center gap-2">
                          <Laptop size={12} className="text-slate-500 shrink-0" />
                          <span className="truncate max-w-[160px]" title={item.user_agent}>
                            {item.browser} on {item.operating_system}
                          </span>
                        </div>
                      </td>

                      {/* Status */}
                      <td className="py-4 px-6 text-center">
                        <div className="flex justify-center">
                          {getStatusBadge(item)}
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          {/* Table Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-between px-6 py-4 border-t border-white/[0.06] bg-white/[0.01]">
              <div className="text-slate-400 text-xs font-mono">
                Showing page <span className="text-purple-400 font-bold">{page + 1}</span> of <span className="text-purple-400 font-bold">{totalPages}</span>
              </div>
              <div className="flex items-center gap-2">
                <button
                  disabled={page === 0}
                  onClick={() => setPage(p => Math.max(0, p - 1))}
                  className="p-1.5 rounded-lg border border-white/[0.08] bg-white/[0.02] text-slate-400 hover:text-white hover:bg-white/[0.08] disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer transition-colors"
                >
                  <ChevronLeft size={16} />
                </button>
                <button
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                  className="p-1.5 rounded-lg border border-white/[0.08] bg-white/[0.02] text-slate-400 hover:text-white hover:bg-white/[0.08] disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer transition-colors"
                >
                  <ChevronRight size={16} />
                </button>
              </div>
            </div>
          )}
        </WidgetWrapper>
      </div>
    </DashboardLayout>
  );
};

export default LoginActivity;

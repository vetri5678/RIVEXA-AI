import React, { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import Sidebar from './Sidebar';
import TopBar from './TopBar';
import NotificationDrawer from './NotificationDrawer';
import AuroraBackground from './AuroraBackground';
import authApi from '../../api/auth';
import { AlertCircle, X, CheckCircle, Info } from 'lucide-react';

interface DashboardLayoutProps {
  children: React.ReactNode;
  onSearchChange: (val: string) => void;
  searchValue: string;
  onQuickAction: (action: string) => void;
}

export const DashboardLayout: React.FC<DashboardLayoutProps> = ({
  children,
  onSearchChange,
  searchValue,
  onQuickAction,
}) => {
  const [isNotificationsOpen, setIsNotificationsOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [isMobileDrawerOpen, setIsMobileDrawerOpen] = useState(false);
  const queryClient = useQueryClient();

  const [toasts, setToasts] = useState<{ id: number; message: string; type: 'success' | 'error' | 'info' }[]>([]);

  React.useEffect(() => {
    const msg = sessionStorage.getItem('rv_toast_msg');
    const type = sessionStorage.getItem('rv_toast_type') as 'success' | 'error' | 'info' || 'info';
    if (msg) {
      setToasts([{ id: Date.now(), message: msg, type }]);
      sessionStorage.removeItem('rv_toast_msg');
      sessionStorage.removeItem('rv_toast_type');
    }
  }, []);

  const dismissToast = (id: number) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  };

  const handleLogout = async () => {
    try {
      await authApi.logout();
    } catch (err) {
      console.warn('Logout request failed', err);
    } finally {
      queryClient.clear();
      // Clear ALL auth-related state from storage
      localStorage.removeItem('rv_access_token');
      localStorage.removeItem('rv_refresh_token');
      localStorage.removeItem('rv_user');
      localStorage.removeItem('rivexa_user');
      localStorage.removeItem('rivexa_token');
      localStorage.removeItem('access_token');
      localStorage.removeItem('user');
      sessionStorage.removeItem('rv_toast_msg');
      window.location.href = '/#/';
    }
  };

  return (
    <div className="min-h-screen bg-[#050816] text-slate-100 flex font-sans antialiased relative overflow-x-hidden">
      {/* GPU Animated Ambient Canvas Background */}
      <AuroraBackground />

      {/* Mobile Off-Canvas Drawer Backdrop */}
      {isMobileDrawerOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/70 backdrop-blur-sm lg:hidden transition-opacity"
          onClick={() => setIsMobileDrawerOpen(false)}
        />
      )}

      {/* Futuristic Sidebar Navigation */}
      <Sidebar
        onLogout={handleLogout}
        collapsed={sidebarCollapsed}
        onToggleCollapse={() => setSidebarCollapsed((v) => !v)}
        mobileOpen={isMobileDrawerOpen}
        onCloseMobile={() => setIsMobileDrawerOpen(false)}
      />

      {/* Main Workspace Frame */}
      <div
        className={`flex-1 flex flex-col min-h-screen transition-all duration-300 relative z-10 ${
          sidebarCollapsed ? 'pl-0 lg:pl-16' : 'pl-0 lg:pl-64'
        }`}
      >
        <TopBar
          onSearchChange={onSearchChange}
          searchValue={searchValue}
          onOpenNotifications={() => setIsNotificationsOpen(true)}
          onQuickAction={onQuickAction}
          collapsed={sidebarCollapsed}
          onToggleMobileMenu={() => setIsMobileDrawerOpen((v) => !v)}
        />

        {/* Dynamic Content Viewport */}
        <main className="flex-1 pt-20 px-3 sm:px-6 pb-12 overflow-y-auto max-w-[1920px] w-full mx-auto animate-fade-in-up">
          {children}
        </main>
      </div>

      {/* Notification Drawer Component */}
      <NotificationDrawer
        isOpen={isNotificationsOpen}
        onClose={() => setIsNotificationsOpen(false)}
      />

      {/* Dynamic Toast Notifications */}
      <div className="fixed bottom-6 right-6 z-50 flex flex-col gap-3 max-w-sm w-[90%]">
        {toasts.map((t) => {
          const isError = t.type === 'error';
          const isSuccess = t.type === 'success';
          const borderClass = isError 
            ? 'border-red-500/30 bg-red-950/40 text-red-200 shadow-[0_0_20px_rgba(239,68,68,0.15)]' 
            : isSuccess 
              ? 'border-emerald-500/30 bg-emerald-950/40 text-emerald-200 shadow-[0_0_20px_rgba(16,185,129,0.15)]' 
              : 'border-blue-500/30 bg-blue-950/40 text-blue-200 shadow-[0_0_20px_rgba(59,130,246,0.15)]';
          const Icon = isError ? AlertCircle : isSuccess ? CheckCircle : Info;

          return (
            <div
              key={t.id}
              className={`p-4 rounded-xl border backdrop-blur-xl flex items-start gap-3 transition-all duration-300 font-sans ${borderClass}`}
            >
              <Icon size={18} className="shrink-0 mt-0.5" />
              <div className="flex-1 text-xs font-semibold leading-relaxed">
                {t.message}
              </div>
              <button
                onClick={() => dismissToast(t.id)}
                className="text-slate-400 hover:text-white transition-colors cursor-pointer"
              >
                <X size={14} />
              </button>
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default DashboardLayout;

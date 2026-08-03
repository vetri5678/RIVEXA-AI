import React, { useState } from 'react';
import Sidebar from './Sidebar';
import TopBar from './TopBar';
import NotificationDrawer from './NotificationDrawer';
import AuroraBackground from './AuroraBackground';

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

  const handleLogout = () => {
    localStorage.removeItem('rv_access_token');
    localStorage.removeItem('rv_refresh_token');
    localStorage.removeItem('rv_user');
    window.location.hash = '#/login';
  };

  return (
    <div className="min-h-screen bg-[#050816] text-slate-100 flex font-sans antialiased relative overflow-x-hidden">
      {/* GPU Animated Ambient Canvas Background */}
      <AuroraBackground />

      {/* Futuristic Sidebar Navigation */}
      <Sidebar
        onLogout={handleLogout}
        collapsed={sidebarCollapsed}
        onToggleCollapse={() => setSidebarCollapsed((v) => !v)}
      />

      {/* Main Workspace Frame */}
      <div
        className={`flex-1 flex flex-col min-h-screen transition-all duration-300 relative z-10 ${
          sidebarCollapsed ? 'pl-16' : 'pl-64'
        }`}
      >
        <TopBar
          onSearchChange={onSearchChange}
          searchValue={searchValue}
          onOpenNotifications={() => setIsNotificationsOpen(true)}
          onQuickAction={onQuickAction}
          collapsed={sidebarCollapsed}
        />

        {/* Dynamic Content Viewport */}
        <main className="flex-1 pt-20 px-6 pb-12 overflow-y-auto animate-fade-in-up">
          {children}
        </main>
      </div>

      {/* Notification Drawer Component */}
      <NotificationDrawer
        isOpen={isNotificationsOpen}
        onClose={() => setIsNotificationsOpen(false)}
      />
    </div>
  );
};

export default DashboardLayout;

import React, { useState } from 'react';
import Sidebar from './Sidebar';
import TopBar from './TopBar';
import NotificationDrawer from './NotificationDrawer';

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

  const handleLogout = () => {
    localStorage.removeItem('rv_access_token');
    localStorage.removeItem('rv_refresh_token');
    localStorage.removeItem('rv_user');
    window.location.hash = '#/login';
  };

  return (
    <div className="min-h-screen bg-[#020817] text-slate-100 flex font-mono select-none antialiased">
      {/* Laser tech mesh backdrop pattern */}
      <div className="fixed inset-0 bg-cyber-grid pointer-events-none opacity-[0.2]" />

      {/* Cyber Operations Sidebar */}
      <Sidebar onLogout={handleLogout} />

      {/* Main telemetry display node */}
      <div className="flex-1 pl-64 flex flex-col min-h-screen">
        <TopBar
          onSearchChange={onSearchChange}
          searchValue={searchValue}
          onOpenNotifications={() => setIsNotificationsOpen(true)}
          onQuickAction={onQuickAction}
        />

        {/* Content stream area */}
        <main className="flex-1 pt-24 px-8 pb-12 overflow-y-auto">
          {children}
        </main>
      </div>

      <NotificationDrawer
        isOpen={isNotificationsOpen}
        onClose={() => setIsNotificationsOpen(false)}
      />
    </div>
  );
};
export default DashboardLayout;

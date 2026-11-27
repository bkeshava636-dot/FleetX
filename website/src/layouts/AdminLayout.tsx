import React, { useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { AdminSidebar } from '../components/admin/AdminSidebar';
import { AdminHeader } from '../components/admin/AdminHeader';

export const AdminLayout: React.FC = () => {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [theme, setTheme] = useState<'dark' | 'light-bw'>(() => {
    try {
      return localStorage.getItem('fleetx_landing_theme') === 'light-bw' ? 'light-bw' : 'dark';
    } catch {
      return 'dark';
    }
  });
  const location = useLocation();

  const toggleTheme = () => {
    setTheme((prev) => {
      const next = prev === 'dark' ? 'light-bw' : 'dark';
      try {
        localStorage.setItem('fleetx_landing_theme', next);
      } catch {
        // ignore storage errors
      }
      return next;
    });
  };

  // Determine header title based on active path
  const getPageTitle = (pathname: string): string => {
    if (pathname.includes('/admin/submissions')) return 'Stop Coordinate Submissions';
    if (pathname.includes('/admin/stop-requests')) return 'Driver Stop Requests';
    if (pathname.includes('/admin/routes')) return 'Routes & Bus Stops';
    if (pathname.includes('/admin/fleet')) return 'Fleet Management';
    if (pathname.includes('/admin/drivers')) return 'Driver Management';
    if (pathname.includes('/admin/live-tracking')) return 'Live Fleet Tracking';
    if (pathname.includes('/admin/bus-swaps')) return 'Bus Swaps';
    if (pathname.includes('/admin/temporary-assignments')) return 'Temporary Assignments';
    if (pathname.includes('/admin/calendar')) return 'College Calendar';
    if (pathname.includes('/admin/announcements')) return 'Announcements';
    if (pathname.includes('/admin/students')) return 'Student Management';
    if (pathname.includes('/admin/analytics')) return 'Analytics & Reports';
    if (pathname.includes('/admin/audit')) return 'Audit Logs';
    if (pathname.includes('/admin/settings')) return 'Admin Settings';
    if (pathname === '/admin' || pathname === '/admin/') return 'Transport Dashboard Overview';
    return 'FleetX Management';
  };

  return (
    <div
      className={`min-h-screen bg-fleet-navy text-fleet-offwhite flex admin-theme-fleetx transition-colors duration-200 ${
        theme === 'light-bw' ? 'landing-theme-bw' : ''
      }`}
    >
      {/* Sidebar */}
      <AdminSidebar isOpen={sidebarOpen} onClose={() => setSidebarOpen(false)} />

      {/* Main Content Area */}
      <div className="flex-1 flex flex-col min-w-0 lg:pl-64">
        <AdminHeader
          onMenuClick={() => setSidebarOpen(true)}
          title={getPageTitle(location.pathname)}
          theme={theme}
          onToggleTheme={toggleTheme}
        />

        <main className="flex-1 p-4 sm:p-6 lg:p-8 max-w-7xl w-full mx-auto">
          <Outlet />
        </main>
      </div>
    </div>
  );
};

export default AdminLayout;

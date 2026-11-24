import React from 'react';
import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard,
  MapPin,
  Route as RouteIcon,
  Bus,
  Users,
  Radio,
  Repeat,
  UserCheck,
  Calendar,
  Bell,
  GraduationCap,
  BarChart3,
  FileText,
  Settings,
  LogOut,
  X,
  ExternalLink,
} from 'lucide-react';
import logoUrl from '../../assets/logo/fleetx-logo.png';
import { useAdminAuth } from '../../context/AdminAuthContext';

interface AdminSidebarProps {
  isOpen: boolean;
  onClose: () => void;
}

interface NavItem {
  name: string;
  href: string;
  icon: React.ComponentType<{ className?: string }>;
  isImplemented: boolean;
  badge?: string;
}

export const AdminSidebar: React.FC<AdminSidebarProps> = ({ isOpen, onClose }) => {
  const { profile, logout } = useAdminAuth();

  const navItems: NavItem[] = [
    { name: 'Dashboard', href: '/admin', icon: LayoutDashboard, isImplemented: true },
    { name: 'Stop Submissions', href: '/admin/submissions', icon: MapPin, isImplemented: true, badge: 'New' },
    { name: 'Routes & Stops', href: '/admin/routes', icon: RouteIcon, isImplemented: true },
    { name: 'Fleet Management', href: '/admin/fleet', icon: Bus, isImplemented: false },
    { name: 'Driver Management', href: '/admin/drivers', icon: Users, isImplemented: false },
    { name: 'Live Fleet Tracking', href: '/admin/live-tracking', icon: Radio, isImplemented: false },
    { name: 'Bus Swaps', href: '/admin/bus-swaps', icon: Repeat, isImplemented: false },
    { name: 'Temporary Assignments', href: '/admin/temporary-assignments', icon: UserCheck, isImplemented: false },
    { name: 'College Calendar', href: '/admin/calendar', icon: Calendar, isImplemented: false },
    { name: 'Announcements', href: '/admin/announcements', icon: Bell, isImplemented: false },
    { name: 'Student Management', href: '/admin/students', icon: GraduationCap, isImplemented: false },
    { name: 'Analytics & Reports', href: '/admin/analytics', icon: BarChart3, isImplemented: false },
    { name: 'Audit Logs', href: '/admin/audit', icon: FileText, isImplemented: false },
    { name: 'Settings', href: '/admin/settings', icon: Settings, isImplemented: false },
  ];

  return (
    <>
      {/* Mobile Backdrop */}
      {isOpen && (
        <div
          className="fixed inset-0 bg-slate-950/70 backdrop-blur-sm z-40 lg:hidden"
          onClick={onClose}
        />
      )}

      {/* Sidebar Panel */}
      <aside
        className={`fixed top-0 bottom-0 left-0 z-50 w-64 bg-slate-900 border-r border-slate-800 flex flex-col transition-transform duration-300 ease-in-out lg:translate-x-0 ${
          isOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        {/* Header */}
        <div className="h-16 flex items-center justify-between px-5 border-b border-slate-800 bg-slate-900/90">
          <div className="flex items-center gap-3">
            <img src={logoUrl} alt="FleetX Logo" className="w-8 h-8 rounded-lg bg-white p-0.5 object-cover" />
            <div className="flex flex-col">
              <span className="text-sm font-bold tracking-tight text-white leading-tight">
                Fleet<span className="text-blue-400">X</span> Admin
              </span>
              <span className="text-[10px] text-slate-400 font-medium">Transit Console</span>
            </div>
          </div>
          <button
            onClick={onClose}
            className="lg:hidden p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* User Card */}
        <div className="p-4 border-b border-slate-800/80 bg-slate-850">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-full bg-blue-600/20 border border-blue-500/30 flex items-center justify-center text-blue-400 font-semibold text-xs">
              {profile?.name?.charAt(0).toUpperCase() || 'A'}
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-xs font-semibold text-white truncate">{profile?.name || 'Administrator'}</p>
              <p className="text-[11px] text-slate-400 truncate">{profile?.email || 'admin@fleetx'}</p>
            </div>
          </div>
        </div>

        {/* Navigation Items */}
        <nav className="flex-1 overflow-y-auto px-3 py-4 space-y-1 custom-scrollbar">
          <div className="px-2 pb-2 text-[10px] font-bold uppercase tracking-wider text-slate-500">
            Operations & Navigation
          </div>
          {navItems.map((item) => {
            const Icon = item.icon;
            if (item.isImplemented) {
              return (
                <NavLink
                  key={item.name}
                  to={item.href}
                  end={item.href === '/admin'}
                  onClick={() => onClose()}
                  className={({ isActive }) =>
                    `flex items-center justify-between px-3 py-2 text-xs font-medium rounded-xl transition-all ${
                      isActive
                        ? 'bg-blue-600 text-white shadow-sm shadow-blue-500/20'
                        : 'text-slate-300 hover:text-white hover:bg-slate-800/80'
                    }`
                  }
                >
                  <div className="flex items-center gap-3">
                    <Icon className="w-4 h-4 flex-shrink-0" />
                    <span>{item.name}</span>
                  </div>
                  {item.badge && (
                    <span className="text-[9px] font-bold uppercase tracking-wide bg-blue-500/20 text-blue-300 border border-blue-400/30 px-1.5 py-0.5 rounded-full">
                      {item.badge}
                    </span>
                  )}
                </NavLink>
              );
            }

            // Non-implemented future modules
            return (
              <div
                key={item.name}
                className="flex items-center justify-between px-3 py-2 text-xs font-medium text-slate-500 rounded-xl cursor-not-allowed opacity-60"
                title="Planned module (Phase 5–7)"
              >
                <div className="flex items-center gap-3">
                  <Icon className="w-4 h-4 flex-shrink-0 text-slate-600" />
                  <span>{item.name}</span>
                </div>
                <span className="text-[9px] text-slate-600 font-medium bg-slate-800/80 px-1.5 py-0.5 rounded">
                  Soon
                </span>
              </div>
            );
          })}
        </nav>

        {/* Footer Actions */}
        <div className="p-3 border-t border-slate-800 bg-slate-900/90 space-y-1">
          <a
            href="/"
            target="_blank"
            rel="noopener noreferrer"
            className="w-full flex items-center justify-between px-3 py-2 text-xs font-medium text-slate-400 hover:text-slate-200 hover:bg-slate-800 rounded-lg transition-colors"
          >
            <span className="flex items-center gap-2">
              <ExternalLink className="w-3.5 h-3.5" />
              <span>Public Website</span>
            </span>
            <span className="text-[10px] text-slate-500">Live</span>
          </a>

          <button
            onClick={() => logout()}
            className="w-full flex items-center gap-2 px-3 py-2 text-xs font-medium text-red-400 hover:text-red-300 hover:bg-red-500/10 rounded-lg transition-colors"
          >
            <LogOut className="w-3.5 h-3.5" />
            <span>Sign Out</span>
          </button>
        </div>
      </aside>
    </>
  );
};

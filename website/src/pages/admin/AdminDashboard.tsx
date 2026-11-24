import React from 'react';
import { Link } from 'react-router-dom';
import {
  MapPin,
  Route as RouteIcon,
  ArrowRight,
} from 'lucide-react';
import { useAdminAuth } from '../../context/AdminAuthContext';

export const AdminDashboard: React.FC = () => {
  const { profile } = useAdminAuth();

  return (
    <div className="space-y-6">
      {/* Welcome Banner */}
      <div className="bg-gradient-to-r from-blue-700 to-indigo-800 rounded-2xl p-6 sm:p-8 text-white shadow-lg shadow-blue-900/10 relative overflow-hidden">
        <div className="relative z-10 max-w-2xl">
          <div className="inline-flex items-center gap-2 bg-blue-500/30 text-blue-100 text-xs font-semibold px-3 py-1 rounded-full border border-blue-400/30 mb-3">
            <span>FleetX Transit Operations</span>
          </div>
          <h2 className="text-2xl sm:text-3xl font-extrabold tracking-tight">
            Welcome back, {profile?.name || 'Administrator'}
          </h2>
          <p className="mt-2 text-sm text-blue-100 leading-relaxed">
            Monitor real-time campus bus operations, review student-submitted stop coordinates, and manage transit route data across all 25 fleet buses.
          </p>
        </div>
      </div>

      {/* Quick Action Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <Link
          to="/admin/submissions"
          className="group p-5 bg-white rounded-2xl border border-slate-200/80 shadow-sm hover:shadow-md hover:border-blue-300 transition-all flex items-start justify-between"
        >
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <span className="p-2 rounded-xl bg-blue-50 text-blue-600 group-hover:scale-105 transition-transform">
                <MapPin className="w-5 h-5" />
              </span>
              <h3 className="text-base font-bold text-slate-900 group-hover:text-blue-600 transition-colors">
                Stop Coordinate Submissions
              </h3>
            </div>
            <p className="text-xs text-slate-500 leading-relaxed pt-1">
              Review and approve student-submitted GPS pickup points to update canonical route stop coordinates.
            </p>
          </div>
          <ArrowRight className="w-5 h-5 text-slate-400 group-hover:text-blue-600 group-hover:translate-x-0.5 transition-all flex-shrink-0 mt-2" />
        </Link>

        <Link
          to="/admin/routes"
          className="group p-5 bg-white rounded-2xl border border-slate-200/80 shadow-sm hover:shadow-md hover:border-blue-300 transition-all flex items-start justify-between"
        >
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <span className="p-2 rounded-xl bg-indigo-50 text-indigo-600 group-hover:scale-105 transition-transform">
                <RouteIcon className="w-5 h-5" />
              </span>
              <h3 className="text-base font-bold text-slate-900 group-hover:text-indigo-600 transition-colors">
                Routes & Bus Stops
              </h3>
            </div>
            <p className="text-xs text-slate-500 leading-relaxed pt-1">
              Inspect current route stop sequences, coordinate coverage, and scheduled timings.
            </p>
          </div>
          <ArrowRight className="w-5 h-5 text-slate-400 group-hover:text-indigo-600 group-hover:translate-x-0.5 transition-all flex-shrink-0 mt-2" />
        </Link>
      </div>
    </div>
  );
};

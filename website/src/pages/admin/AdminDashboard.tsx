import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Bus,
  MapPin,
  Route as RouteIcon,
  Radio,
  Clock,
  ArrowRight,
  AlertTriangle,
  CheckCircle2,
  Users,
  ShieldCheck,
  Repeat,
  UserCheck,
} from 'lucide-react';
import { useAdminAuth } from '../../context/AdminAuthContext';
import { subscribeToDashboardMetrics } from '../../services/dashboardService';
import {
  subscribeToBusSwaps,
  subscribeToTemporaryAssignments,
} from '../../services/operationsService';
import { DashboardStats } from '../../types/admin';
import { BusSwapRecord, TemporaryAssignmentRecord } from '../../types/operations';
import { StatCard } from '../../components/admin/StatCard';
import { ErrorState } from '../../components/common/ErrorState';

export const AdminDashboard: React.FC = () => {
  const { profile } = useAdminAuth();

  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [activeSwaps, setActiveSwaps] = useState<BusSwapRecord[]>([]);
  const [activeReplacements, setActiveReplacements] = useState<TemporaryAssignmentRecord[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [lastSyncTime, setLastSyncTime] = useState<string>('Connecting...');

  useEffect(() => {
    setIsLoading(true);
    setErrorMessage(null);

    const unsubscribe = subscribeToDashboardMetrics({
      onData: (data) => {
        setStats(data);
        setIsLoading(false);
        setLastSyncTime(new Date(data.lastUpdatedMs).toLocaleTimeString());
      },
      onError: (err) => {
        console.error('[AdminDashboard] Stream error:', err);
        setErrorMessage(err.message || 'Failed to sync with Firebase operational streams.');
        setIsLoading(false);
      },
    });

    const unsubSwaps = subscribeToBusSwaps(
      (items) => setActiveSwaps(items.filter((s) => s.status === 'ACTIVE')),
      () => {}
    );

    const unsubReplacements = subscribeToTemporaryAssignments(
      (items) => setActiveReplacements(items.filter((a) => a.status === 'ACTIVE')),
      () => {}
    );

    return () => {
      unsubscribe();
      unsubSwaps();
      unsubReplacements();
    };
  }, []);

  const coveragePercent = stats && stats.totalStops > 0
    ? Math.round((stats.stopsWithCoordinates / stats.totalStops) * 100)
    : 0;

  return (
    <div className="space-y-6">
      {/* Welcome Banner */}
      <div className="bg-gradient-to-r from-blue-700 via-blue-800 to-indigo-900 rounded-2xl p-6 sm:p-8 text-white shadow-xl shadow-blue-900/10 relative overflow-hidden">
        {/* Subtle decorative circles */}
        <div className="absolute -right-10 -bottom-10 w-64 h-64 bg-white/5 rounded-full pointer-events-none" />
        <div className="absolute right-20 -top-10 w-40 h-40 bg-white/5 rounded-full pointer-events-none" />

        <div className="relative z-10 max-w-3xl">
          <div className="inline-flex items-center gap-2 bg-blue-500/20 text-blue-100 text-xs font-semibold px-3 py-1 rounded-full border border-blue-400/30 mb-3 backdrop-blur-sm">
            <ShieldCheck className="w-3.5 h-3.5 text-blue-300" />
            <span>Authorized Administration Portal</span>
          </div>

          <h2 className="text-2xl sm:text-3xl font-extrabold tracking-tight">
            Welcome, {profile?.name || 'Administrator'}
          </h2>

          <p className="mt-2 text-xs sm:text-sm text-blue-100/90 leading-relaxed max-w-2xl">
            Real-time operations center for FleetX college bus transit. Review live fleet metrics, coordinate crowdsourcing submissions, and verified route stop data.
          </p>

          <div className="mt-5 flex flex-wrap items-center gap-3 text-xs">
            <div className="inline-flex items-center gap-1.5 bg-black/20 px-3 py-1.5 rounded-xl border border-white/10 text-blue-100">
              <Clock className="w-3.5 h-3.5 text-blue-300" />
              <span>Last Firebase Sync: {lastSyncTime}</span>
            </div>

            {stats && stats.pendingStopSubmissions > 0 && (
              <Link
                to="/admin/submissions"
                className="inline-flex items-center gap-1.5 bg-amber-400 text-amber-950 font-bold px-3 py-1.5 rounded-xl hover:bg-amber-300 shadow-sm transition-colors"
              >
                <AlertTriangle className="w-3.5 h-3.5" />
                <span>{stats.pendingStopSubmissions} Stop Submissions Awaiting Approval</span>
              </Link>
            )}
          </div>
        </div>
      </div>

      {/* Error State Banner */}
      {errorMessage && (
        <ErrorState
          title="Firebase Stream Connection Warning"
          message={errorMessage}
          onRetry={() => window.location.reload()}
        />
      )}

      {/* Active Operational Attention Banners */}
      {(activeSwaps.length > 0 || activeReplacements.length > 0) && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {activeSwaps.length > 0 && (
            <Link
              to="/admin/bus-swaps"
              className="group flex items-center justify-between gap-4 p-4 rounded-2xl bg-amber-50 border border-amber-200 hover:border-amber-400 transition-colors"
            >
              <div className="flex items-center gap-3">
                <div className="p-2.5 rounded-xl bg-amber-100 text-amber-700">
                  <Repeat className="w-5 h-5" />
                </div>
                <div>
                  <p className="text-sm font-bold text-amber-950">
                    {activeSwaps.length} Active Bus {activeSwaps.length === 1 ? 'Swap' : 'Swaps'}
                  </p>
                  <p className="text-xs text-amber-800 mt-0.5">
                    {activeSwaps
                      .slice(0, 3)
                      .map((s) => `${s.originalBusId} → ${s.replacementBusId}`)
                      .join(', ')}
                    {activeSwaps.length > 3 ? ` +${activeSwaps.length - 3} more` : ''}
                  </p>
                </div>
              </div>
              <span className="inline-flex items-center gap-1 text-xs font-bold text-amber-900 group-hover:translate-x-0.5 transition-transform">
                <span>Manage Swaps</span>
                <ArrowRight className="w-4 h-4" />
              </span>
            </Link>
          )}

          {activeReplacements.length > 0 && (
            <Link
              to="/admin/temporary-assignments"
              className="group flex items-center justify-between gap-4 p-4 rounded-2xl bg-blue-50 border border-blue-200 hover:border-blue-400 transition-colors"
            >
              <div className="flex items-center gap-3">
                <div className="p-2.5 rounded-xl bg-blue-100 text-blue-700">
                  <UserCheck className="w-5 h-5" />
                </div>
                <div>
                  <p className="text-sm font-bold text-blue-950">
                    {activeReplacements.length} Active Temporary{' '}
                    {activeReplacements.length === 1 ? 'Replacement' : 'Replacements'}
                  </p>
                  <p className="text-xs text-blue-800 mt-0.5">
                    {activeReplacements
                      .slice(0, 3)
                      .map((a) => `${a.busId} (${a.replacementDriverName})`)
                      .join(', ')}
                    {activeReplacements.length > 3
                      ? ` +${activeReplacements.length - 3} more`
                      : ''}
                  </p>
                </div>
              </div>
              <span className="inline-flex items-center gap-1 text-xs font-bold text-blue-900 group-hover:translate-x-0.5 transition-transform">
                <span>View Replacements</span>
                <ArrowRight className="w-4 h-4" />
              </span>
            </Link>
          )}
        </div>
      )}

      {/* Key Metrics Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Total & Active Buses */}
        <StatCard
          title="Fleet Buses"
          value={isLoading ? '...' : `${stats?.activeBuses ?? 0} / ${stats?.totalBuses ?? 0}`}
          subtitle={isLoading ? 'Connecting...' : `${stats?.activeBuses ?? 0} active in service`}
          icon={Bus}
          color="blue"
          linkTo="/admin/fleet"
          linkLabel="Manage Fleet"
          isLoading={isLoading}
        />

        {/* Live GPS Telemetry */}
        <StatCard
          title="Transmitting GPS"
          value={isLoading ? '...' : stats?.transmittingBuses ?? 0}
          subtitle="Broadcasting live (<90s)"
          icon={Radio}
          color="purple"
          linkTo="/admin/live-tracking"
          linkLabel="View Live Map"
          isLoading={isLoading}
        />

        {/* Active Routes */}
        <StatCard
          title="Active Routes"
          value={isLoading ? '...' : stats?.activeRoutes ?? 0}
          subtitle="Operational transit lines"
          icon={RouteIcon}
          color="indigo"
          linkTo="/admin/routes"
          linkLabel="Explore Routes"
          isLoading={isLoading}
        />

        {/* Active Drivers */}
        <StatCard
          title="Active Drivers"
          value={isLoading ? '...' : stats?.totalDrivers ?? 0}
          subtitle="Verified driver profiles"
          icon={Users}
          color="emerald"
          linkTo="/admin/drivers"
          linkLabel="Manage Drivers"
          isLoading={isLoading}
        />
      </div>

      {/* Secondary Metrics & Crowdsourcing Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        {/* Total Stops & Coverage */}
        <StatCard
          title="Total Bus Stops"
          value={isLoading ? '...' : stats?.totalStops ?? 0}
          subtitle={
            isLoading
              ? 'Calculating...'
              : `${stats?.stopsMissingCoordinates ?? 0} missing GPS coordinates (${coveragePercent}% mapped)`
          }
          icon={MapPin}
          color="emerald"
          linkTo="/admin/routes"
          linkLabel="View Stop Coverage"
          isLoading={isLoading}
        />

        {/* Pending Student Submissions */}
        <StatCard
          title="Pending Submissions"
          value={isLoading ? '...' : stats?.pendingStopSubmissions ?? 0}
          subtitle="Student stop coordinates"
          icon={MapPin}
          color="rose"
          linkTo="/admin/submissions"
          linkLabel="Review Submissions"
          isLoading={isLoading}
        />

        {/* Pending Driver Requests */}
        <StatCard
          title="Driver Requests"
          value={isLoading ? '...' : stats?.pendingDriverRequests ?? 0}
          subtitle="Proposed stops by drivers"
          icon={AlertTriangle}
          color="amber"
          linkTo="/admin/stop-requests"
          linkLabel="Review Requests"
          isLoading={isLoading}
        />
      </div>

      {/* Operational Highlights & Stop Coordinate Progress */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Coordinate Coverage Progress Card */}
        <div className="lg:col-span-7 bg-white rounded-2xl p-6 border border-slate-200/80 shadow-sm space-y-5">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-base font-bold text-slate-900 tracking-tight">
                Stop Coordinate Accuracy & Coverage
              </h3>
              <p className="text-xs text-slate-500 mt-0.5">
                Status of verified latitude/longitude coordinates across all active route stops
              </p>
            </div>
            <div className="p-2.5 rounded-xl bg-blue-50 text-blue-600 font-bold text-sm">
              {coveragePercent}%
            </div>
          </div>

          {/* Progress bar */}
          <div className="space-y-2">
            <div className="h-3 w-full bg-slate-100 rounded-full overflow-hidden flex">
              <div
                className="bg-emerald-500 transition-all duration-500"
                style={{ width: `${coveragePercent}%` }}
                title={`Mapped: ${stats?.stopsWithCoordinates ?? 0}`}
              />
              <div
                className="bg-amber-400 transition-all duration-500"
                style={{ width: `${100 - coveragePercent}%` }}
                title={`Missing: ${stats?.stopsMissingCoordinates ?? 0}`}
              />
            </div>

            <div className="flex items-center justify-between text-xs text-slate-500 font-medium pt-1">
              <span className="flex items-center gap-1.5">
                <span className="w-2.5 h-2.5 rounded-full bg-emerald-500" />
                <span>{stats?.stopsWithCoordinates ?? 0} Mapped Coordinates</span>
              </span>
              <span className="flex items-center gap-1.5">
                <span className="w-2.5 h-2.5 rounded-full bg-amber-400" />
                <span>{stats?.stopsMissingCoordinates ?? 0} Missing Coordinates</span>
              </span>
            </div>
          </div>

          <div className="p-4 rounded-xl bg-slate-50 border border-slate-200/60 text-xs text-slate-600 space-y-1.5">
            <p className="font-semibold text-slate-800 flex items-center gap-1.5">
              <CheckCircle2 className="w-4 h-4 text-emerald-600" />
              <span>Crowdsourcing Priority</span>
            </p>
            <p className="leading-relaxed">
              Stops lacking coordinates rely on approximate timing estimates. Students can submit coordinates via the public portal, which appear in <strong>Stop Submissions</strong> for admin review.
            </p>
          </div>
        </div>

        {/* Quick Action Hub */}
        <div className="lg:col-span-5 bg-white rounded-2xl p-6 border border-slate-200/80 shadow-sm flex flex-col justify-between space-y-4">
          <div>
            <h3 className="text-base font-bold text-slate-900 tracking-tight">
              Operational Shortcuts
            </h3>
            <p className="text-xs text-slate-500 mt-0.5">
              Direct access to active management modules
            </p>
          </div>

          <div className="space-y-2.5">
            <Link
              to="/admin/submissions"
              className="group flex items-center justify-between p-3.5 rounded-xl border border-slate-200/80 hover:border-blue-400 hover:bg-blue-50/40 transition-all"
            >
              <div className="flex items-center gap-3">
                <div className="p-2 rounded-lg bg-blue-50 text-blue-600 group-hover:scale-105 transition-transform">
                  <MapPin className="w-4 h-4" />
                </div>
                <div>
                  <p className="text-xs font-bold text-slate-900 group-hover:text-blue-600 transition-colors">
                    Coordinate Review Console
                  </p>
                  <p className="text-[11px] text-slate-500">
                    Approve or reject student submissions
                  </p>
                </div>
              </div>
              <ArrowRight className="w-4 h-4 text-slate-400 group-hover:text-blue-600 group-hover:translate-x-0.5 transition-transform" />
            </Link>

            <Link
              to="/admin/live-tracking"
              className="group flex items-center justify-between p-3.5 rounded-xl border border-slate-200/80 hover:border-emerald-400 hover:bg-emerald-50/40 transition-all"
            >
              <div className="flex items-center gap-3">
                <div className="p-2 rounded-lg bg-emerald-50 text-emerald-600 group-hover:scale-105 transition-transform">
                  <Radio className="w-4 h-4" />
                </div>
                <div>
                  <p className="text-xs font-bold text-slate-900 group-hover:text-emerald-600 transition-colors">
                    Live Fleet Tracking Map
                  </p>
                  <p className="text-[11px] text-slate-500">
                    Real-time GPS vehicle positions & heading
                  </p>
                </div>
              </div>
              <ArrowRight className="w-4 h-4 text-slate-400 group-hover:text-emerald-600 group-hover:translate-x-0.5 transition-transform" />
            </Link>

            <Link
              to="/admin/routes"
              className="group flex items-center justify-between p-3.5 rounded-xl border border-slate-200/80 hover:border-indigo-400 hover:bg-indigo-50/40 transition-all"
            >
              <div className="flex items-center gap-3">
                <div className="p-2 rounded-lg bg-indigo-50 text-indigo-600 group-hover:scale-105 transition-transform">
                  <RouteIcon className="w-4 h-4" />
                </div>
                <div>
                  <p className="text-xs font-bold text-slate-900 group-hover:text-indigo-600 transition-colors">
                    Routes & Stops Explorer
                  </p>
                  <p className="text-[11px] text-slate-500">
                    Inspect stop sequence and coordinates
                  </p>
                </div>
              </div>
              <ArrowRight className="w-4 h-4 text-slate-400 group-hover:text-indigo-600 group-hover:translate-x-0.5 transition-transform" />
            </Link>
          </div>

          <div className="pt-2 border-t border-slate-100 flex items-center justify-between text-[11px] text-slate-400">
            <span>FleetX Transport Management</span>
            <span className="font-mono">Admin Operations Console</span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default AdminDashboard;

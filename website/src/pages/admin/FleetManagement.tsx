import React, { useState, useEffect, useMemo } from 'react';
import {
  Bus,
  Search,
  Plus,
  Route as RouteIcon,
  Users,
  CheckCircle2,
  XCircle,
  AlertTriangle,
  RefreshCw,
  Power,
  Edit2,
  Eye,
  Trash2,
  UserPlus,
  X,
  AlertCircle,
  Loader2,
  ChevronLeft,
  ChevronRight
} from 'lucide-react';
import {
  subscribeToBuses,
  naturalBusSort,
  reconcileBusSync,
  assignBusDriver,
  unassignBusDriver
} from '../../services/fleetService';
import { subscribeToRoutes } from '../../services/routesService';
import { subscribeToDrivers } from '../../services/driverService';
import { BusRecord, DriverRecord, RtdbSyncStatus } from '../../types/fleet';
import { Route } from '../../types/admin';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { EmptyState } from '../../components/common/EmptyState';
import { ErrorState } from '../../components/common/ErrorState';
import { RegisterBusModal } from '../../components/admin/fleet/RegisterBusModal';
import { EditBusModal } from '../../components/admin/fleet/EditBusModal';
import { AssignRouteModal } from '../../components/admin/fleet/AssignRouteModal';
import { ToggleBusStatusModal } from '../../components/admin/fleet/ToggleBusStatusModal';
import { DeleteBusModal } from '../../components/admin/fleet/DeleteBusModal';
import { BusDetailsDrawer } from '../../components/admin/fleet/BusDetailsDrawer';

const ITEMS_PER_PAGE = 10;

export const FleetManagement: React.FC = () => {
  const [buses, setBuses] = useState<BusRecord[]>([]);
  const [routes, setRoutes] = useState<Route[]>([]);
  const [drivers, setDrivers] = useState<DriverRecord[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Filters & Search
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'ACTIVE' | 'INACTIVE'>('ALL');
  const [routeFilter, setRouteFilter] = useState<string>('ALL');
  const [driverFilter, setDriverFilter] = useState<'ALL' | 'ASSIGNED' | 'UNASSIGNED'>('ALL');
  const [syncFilter, setSyncFilter] = useState<string>('ALL');
  const [currentPage, setCurrentPage] = useState<number>(1);

  // Modals & Drawers state
  const [isRegisterOpen, setIsRegisterOpen] = useState(false);
  const [editBus, setEditBus] = useState<BusRecord | null>(null);
  const [assignRouteBus, setAssignRouteBus] = useState<BusRecord | null>(null);
  const [toggleStatusBus, setToggleStatusBus] = useState<BusRecord | null>(null);
  const [deleteBusTarget, setDeleteBusTarget] = useState<BusRecord | null>(null);
  const [selectedBusDetails, setSelectedBusDetails] = useState<BusRecord | null>(null);

  // Assign / Change Driver modal state
  const [assignDriverBus, setAssignDriverBus] = useState<BusRecord | null>(null);
  const [selectedDriverUid, setSelectedDriverUid] = useState<string>('');
  const [confirmDriverReplace, setConfirmDriverReplace] = useState<boolean>(false);
  const [isAssigningDriver, setIsAssigningDriver] = useState<boolean>(false);
  const [assignDriverError, setAssignDriverError] = useState<string | null>(null);

  // Notification Toast
  const [toast, setToast] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  const showToast = (type: 'success' | 'error', message: string) => {
    setToast({ type, message });
    setTimeout(() => {
      setToast(null);
    }, 4000);
  };

  // 1. Subscribe to buses
  useEffect(() => {
    setIsLoading(true);
    setErrorMessage(null);

    const unsubBuses = subscribeToBuses(
      (data) => {
        setBuses(data);
        setIsLoading(false);
      },
      (err) => {
        console.error('[FleetManagement] Bus subscription error:', err);
        setErrorMessage(err.message || 'Failed to stream buses.');
        setIsLoading(false);
      }
    );

    const unsubRoutes = subscribeToRoutes(
      (routeData) => {
        setRoutes(routeData);
      },
      (err) => {
        console.warn('[FleetManagement] Route subscription warning:', err);
      }
    );

    const unsubDrivers = subscribeToDrivers(
      (driverData) => {
        setDrivers(driverData);
      },
      (err) => {
        console.warn('[FleetManagement] Driver subscription warning:', err);
      }
    );

    return () => {
      unsubBuses();
      unsubRoutes();
      unsubDrivers();
    };
  }, []);

  // Update selected bus drawer if buses update
  useEffect(() => {
    if (selectedBusDetails) {
      const updated = buses.find((b) => b.busId === selectedBusDetails.busId);
      if (updated) setSelectedBusDetails(updated);
    }
  }, [buses, selectedBusDetails]);

  // Dashboard Metrics
  const metrics = useMemo(() => {
    let active = 0;
    let inactive = 0;
    let withRoute = 0;
    let withDriver = 0;
    let syncErrors = 0;

    for (const b of buses) {
      if (b.active) active++;
      else inactive++;

      if (b.routeId) withRoute++;
      if (b.driverId) withDriver++;
      if (b.rtdbSyncStatus === 'FAILED' || b.rtdbSyncStatus === 'PERMANENTLY_FAILED') syncErrors++;
    }

    return {
      total: buses.length,
      active,
      inactive,
      withRoute,
      withDriver,
      syncErrors
    };
  }, [buses]);

  // Search & Filtered Buses
  const filteredBuses = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();

    return buses
      .filter((b) => {
        // Search matching
        if (q) {
          const matchesBusNum = b.busNumber.toLowerCase().includes(q) || b.busId.toLowerCase().includes(q);
          const matchesReg = b.registrationNumber.toLowerCase().includes(q);
          const matchesDriver = b.driverName ? b.driverName.toLowerCase().includes(q) : false;
          if (!matchesBusNum && !matchesReg && !matchesDriver) return false;
        }

        // Status filter
        if (statusFilter === 'ACTIVE' && !b.active) return false;
        if (statusFilter === 'INACTIVE' && b.active) return false;

        // Route filter
        if (routeFilter !== 'ALL') {
          if (routeFilter === 'UNASSIGNED') {
            if (b.routeId) return false;
          } else if (b.routeId !== routeFilter) {
            return false;
          }
        }

        // Driver filter
        if (driverFilter === 'ASSIGNED' && !b.driverId) return false;
        if (driverFilter === 'UNASSIGNED' && b.driverId) return false;

        // Sync filter
        if (syncFilter !== 'ALL') {
          if (syncFilter === 'ERRORS') {
            if (b.rtdbSyncStatus !== 'FAILED' && b.rtdbSyncStatus !== 'PERMANENTLY_FAILED') return false;
          } else if (b.rtdbSyncStatus !== syncFilter) {
            return false;
          }
        }

        return true;
      })
      .sort(naturalBusSort);
  }, [buses, searchQuery, statusFilter, routeFilter, driverFilter, syncFilter]);

  // Pagination calculation
  const totalPages = Math.ceil(filteredBuses.length / ITEMS_PER_PAGE) || 1;
  const paginatedBuses = useMemo(() => {
    const start = (currentPage - 1) * ITEMS_PER_PAGE;
    return filteredBuses.slice(start, start + ITEMS_PER_PAGE);
  }, [filteredBuses, currentPage]);

  const handlePageChange = (page: number) => {
    if (page >= 1 && page <= totalPages) {
      setCurrentPage(page);
    }
  };

  const handleInlineReconcile = async (busId: string) => {
    try {
      const res = await reconcileBusSync(busId);
      showToast('success', `Bus ${busId} operational sync restored (Version ${res.assignmentVersion}).`);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Reconciliation failed.';
      showToast('error', msg);
    }
  };

  const openAssignDriverModal = (bus: BusRecord) => {
    setAssignDriverBus(bus);
    setSelectedDriverUid(bus.driverId || '');
    setConfirmDriverReplace(false);
    setAssignDriverError(null);
  };

  const handleAssignDriverSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!assignDriverBus) return;
    setAssignDriverError(null);

    const isUnassign = !selectedDriverUid;
    const isReplacingExisting = Boolean(
      !isUnassign && assignDriverBus.driverId && assignDriverBus.driverId !== selectedDriverUid
    );

    if (isReplacingExisting && !confirmDriverReplace) {
      setAssignDriverError('Please confirm replacing the currently assigned driver.');
      return;
    }

    setIsAssigningDriver(true);
    try {
      if (isUnassign) {
        if (!assignDriverBus.driverId) {
          setAssignDriverBus(null);
          return;
        }
        await unassignBusDriver(assignDriverBus.busId);
        showToast('success', `Unassigned driver from Bus ${assignDriverBus.busNumber}.`);
      } else {
        const res = await assignBusDriver(assignDriverBus.busId, selectedDriverUid, {
          confirmReplacement: isReplacingExisting ? true : undefined,
          expectedPreviousDriverId: assignDriverBus.driverId || null
        });
        showToast(
          'success',
          `Assigned ${res.driverName || 'driver'} to Bus ${assignDriverBus.busNumber} (v${res.assignmentVersion}).`
        );
      }
      setAssignDriverBus(null);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to update driver assignment.';
      setAssignDriverError(msg);
    } finally {
      setIsAssigningDriver(false);
    }
  };

  const getSyncStatusBadge = (status?: RtdbSyncStatus) => {
    switch (status) {
      case 'SYNCED':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-emerald-100 text-emerald-800">
            <CheckCircle2 className="w-3 h-3 text-emerald-600" />
            Synced
          </span>
        );
      case 'PENDING':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-amber-100 text-amber-800">
            <RefreshCw className="w-3 h-3 text-amber-600 animate-spin" />
            Pending
          </span>
        );
      case 'FAILED':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-rose-100 text-rose-800">
            <AlertTriangle className="w-3 h-3 text-rose-600" />
            Sync Error
          </span>
        );
      case 'PERMANENTLY_FAILED':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-slate-200 text-slate-800">
            <XCircle className="w-3 h-3 text-slate-600" />
            Permanent Fail
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-semibold bg-slate-100 text-slate-600">
            Unknown
          </span>
        );
    }
  };

  if (isLoading && buses.length === 0) {
    return (
      <div className="py-24">
        <LoadingSpinner label="Loading Fleet Management..." />
      </div>
    );
  }

  if (errorMessage && buses.length === 0) {
    return (
      <div className="py-12">
        <ErrorState
          title="Fleet Streaming Error"
          message={errorMessage}
          onRetry={() => window.location.reload()}
        />
      </div>
    );
  }

  return (
    <div className="space-y-6 animate-in fade-in duration-200">
      {/* Toast Notification */}
      {toast && (
        <div
          className={`fixed bottom-5 right-5 z-50 flex items-center gap-3 px-4 py-3 rounded-2xl shadow-xl text-xs font-semibold border transition-all animate-in slide-in-from-bottom duration-300 ${
            toast.type === 'success'
              ? 'bg-emerald-900 text-emerald-100 border-emerald-700 shadow-emerald-950/20'
              : 'bg-rose-900 text-rose-100 border-rose-700 shadow-rose-950/20'
          }`}
        >
          {toast.type === 'success' ? (
            <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
          ) : (
            <AlertTriangle className="w-4 h-4 text-rose-400 shrink-0" />
          )}
          <span>{toast.message}</span>
        </div>
      )}

      {/* Header Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">Fleet Management</h1>
          <p className="text-xs text-slate-500 mt-1">
            Register, configure, and manage college buses, route assignments, and synchronization
          </p>
        </div>
        <button
          onClick={() => setIsRegisterOpen(true)}
          className="inline-flex items-center justify-center gap-2 px-4 py-2.5 bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold rounded-xl shadow-sm transition-colors"
        >
          <Plus className="w-4 h-4" />
          <span>Register New Bus</span>
        </button>
      </div>

      {/* Metric Cards */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3 sm:gap-4">
        <div className="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm">
          <div className="flex items-center justify-between text-slate-500 mb-2">
            <span className="text-xs font-semibold">Total Fleet</span>
            <Bus className="w-4 h-4 text-blue-600" />
          </div>
          <p className="text-2xl font-bold text-slate-900">{metrics.total}</p>
          <p className="text-[11px] text-slate-400 mt-0.5">Registered vehicles</p>
        </div>

        <div className="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm">
          <div className="flex items-center justify-between text-slate-500 mb-2">
            <span className="text-xs font-semibold">Active</span>
            <CheckCircle2 className="w-4 h-4 text-emerald-600" />
          </div>
          <p className="text-2xl font-bold text-emerald-700">{metrics.active}</p>
          <p className="text-[11px] text-slate-400 mt-0.5">In operational service</p>
        </div>

        <div className="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm">
          <div className="flex items-center justify-between text-slate-500 mb-2">
            <span className="text-xs font-semibold">Inactive</span>
            <XCircle className="w-4 h-4 text-slate-400" />
          </div>
          <p className="text-2xl font-bold text-slate-700">{metrics.inactive}</p>
          <p className="text-[11px] text-slate-400 mt-0.5">Deactivated / Maintenance</p>
        </div>

        <div className="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm">
          <div className="flex items-center justify-between text-slate-500 mb-2">
            <span className="text-xs font-semibold">With Route</span>
            <RouteIcon className="w-4 h-4 text-indigo-600" />
          </div>
          <p className="text-2xl font-bold text-indigo-700">{metrics.withRoute}</p>
          <p className="text-[11px] text-slate-400 mt-0.5">Routes assigned</p>
        </div>

        <div className="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm">
          <div className="flex items-center justify-between text-slate-500 mb-2">
            <span className="text-xs font-semibold">With Driver</span>
            <Users className="w-4 h-4 text-blue-600" />
          </div>
          <p className="text-2xl font-bold text-blue-700">{metrics.withDriver}</p>
          <p className="text-[11px] text-slate-400 mt-0.5">Drivers assigned</p>
        </div>

        <div
          className={`p-4 rounded-2xl border shadow-sm ${
            metrics.syncErrors > 0
              ? 'bg-rose-50/50 border-rose-200 text-rose-900'
              : 'bg-white border-slate-200 text-slate-900'
          }`}
        >
          <div className="flex items-center justify-between mb-2">
            <span className="text-xs font-semibold">Sync Errors</span>
            <AlertTriangle
              className={`w-4 h-4 ${metrics.syncErrors > 0 ? 'text-rose-600' : 'text-slate-400'}`}
            />
          </div>
          <p className={`text-2xl font-bold ${metrics.syncErrors > 0 ? 'text-rose-600' : 'text-slate-900'}`}>
            {metrics.syncErrors}
          </p>
          <p className="text-[11px] text-slate-400 mt-0.5">RTDB synchronization</p>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="bg-white p-4 rounded-2xl border border-slate-200 shadow-sm space-y-3">
        <div className="flex flex-col lg:flex-row lg:items-center gap-3">
          {/* Search Input */}
          <div className="relative flex-1">
            <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              placeholder="Search by bus number, license plate, or driver..."
              value={searchQuery}
              onChange={(e) => {
                setSearchQuery(e.target.value);
                setCurrentPage(1);
              }}
              className="w-full pl-9 pr-4 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all"
            />
          </div>

          {/* Quick Filter Selects */}
          <div className="flex flex-wrap items-center gap-2.5">
            {/* Status Filter */}
            <select
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value as 'ALL' | 'ACTIVE' | 'INACTIVE');
                setCurrentPage(1);
              }}
              className="px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-700 font-medium focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="ALL">All Statuses</option>
              <option value="ACTIVE">Active Only</option>
              <option value="INACTIVE">Inactive Only</option>
            </select>

            {/* Route Filter */}
            <select
              value={routeFilter}
              onChange={(e) => {
                setRouteFilter(e.target.value);
                setCurrentPage(1);
              }}
              className="px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-700 font-medium focus:outline-none focus:ring-2 focus:ring-blue-500 max-w-[160px] truncate"
            >
              <option value="ALL">All Routes</option>
              <option value="UNASSIGNED">Unassigned Route</option>
              {routes.map((r) => (
                <option key={r.routeId} value={r.routeId}>
                  {r.routeId}
                </option>
              ))}
            </select>

            {/* Driver Filter */}
            <select
              value={driverFilter}
              onChange={(e) => {
                setDriverFilter(e.target.value as 'ALL' | 'ASSIGNED' | 'UNASSIGNED');
                setCurrentPage(1);
              }}
              className="px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-700 font-medium focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="ALL">All Drivers</option>
              <option value="ASSIGNED">Assigned Only</option>
              <option value="UNASSIGNED">Unassigned Only</option>
            </select>

            {/* Sync Status Filter */}
            <select
              value={syncFilter}
              onChange={(e) => {
                setSyncFilter(e.target.value);
                setCurrentPage(1);
              }}
              className="px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-700 font-medium focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="ALL">All Sync States</option>
              <option value="SYNCED">Synchronized</option>
              <option value="PENDING">Sync Pending</option>
              <option value="ERRORS">Sync Errors</option>
            </select>
          </div>
        </div>
      </div>

      {/* Bus Listing Table */}
      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
        {filteredBuses.length === 0 ? (
          <div className="py-16">
            <EmptyState
              icon={Bus}
              title="No buses match filter criteria"
              description="Try adjusting your search terms or filter selections."
            />
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="border-b border-slate-200/80 bg-slate-50/75 text-[11px] font-bold text-slate-500 uppercase tracking-wider">
                  <th className="py-3.5 px-4 sm:px-6">Bus</th>
                  <th className="py-3.5 px-4">Registration</th>
                  <th className="py-3.5 px-4">Route</th>
                  <th className="py-3.5 px-4">Driver</th>
                  <th className="py-3.5 px-4 text-center">Seats</th>
                  <th className="py-3.5 px-4 text-center">Status</th>
                  <th className="py-3.5 px-4 text-center">Sync</th>
                  <th className="py-3.5 px-4 sm:px-6 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-xs">
                {paginatedBuses.map((bus) => {
                  const assignedRoute = routes.find((r) => r.routeId === bus.routeId);

                  return (
                    <tr
                      key={bus.busId}
                      className="hover:bg-slate-50/70 transition-colors group"
                    >
                      {/* Bus Number & ID */}
                      <td className="py-3.5 px-4 sm:px-6">
                        <div className="flex items-center gap-3">
                          <div className="w-8 h-8 rounded-xl bg-blue-50 text-blue-700 flex items-center justify-center font-bold text-xs border border-blue-100">
                            {bus.busNumber}
                          </div>
                          <div>
                            <span className="font-bold text-slate-900 block leading-tight">
                              Bus {bus.busNumber}
                            </span>
                            <span className="text-[11px] font-mono text-slate-400">{bus.busId}</span>
                          </div>
                        </div>
                      </td>

                      {/* Registration Plate */}
                      <td className="py-3.5 px-4 font-mono font-medium text-slate-700">
                        {bus.registrationNumber}
                      </td>

                      {/* Route */}
                      <td className="py-3.5 px-4">
                        {assignedRoute ? (
                          <div className="flex flex-col">
                            <span className="font-semibold text-slate-900">{assignedRoute.name}</span>
                            <span className="text-[11px] text-slate-400 font-mono">
                              {assignedRoute.routeId}
                            </span>
                          </div>
                        ) : bus.routeId ? (
                          <span className="font-mono text-slate-600">{bus.routeId}</span>
                        ) : (
                          <span className="text-slate-400 italic">Unassigned</span>
                        )}
                      </td>

                      {/* Driver */}
                      <td className="py-3.5 px-4">
                        {bus.driverName ? (
                          <div className="flex items-center gap-1.5 font-medium text-slate-900">
                            <span className="w-2 h-2 rounded-full bg-emerald-500"></span>
                            <span>{bus.driverName}</span>
                          </div>
                        ) : (
                          <span className="text-slate-400 italic">Unassigned</span>
                        )}
                      </td>

                      {/* Capacity */}
                      <td className="py-3.5 px-4 text-center font-semibold text-slate-700">
                        {bus.capacity}
                      </td>

                      {/* Status */}
                      <td className="py-3.5 px-4 text-center">
                        <span
                          className={`inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-semibold ${
                            bus.active
                              ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                              : 'bg-slate-100 text-slate-600 border border-slate-200'
                          }`}
                        >
                          {bus.active ? 'Active' : 'Inactive'}
                        </span>
                      </td>

                      {/* Sync Status */}
                      <td className="py-3.5 px-4 text-center">
                        {getSyncStatusBadge(bus.rtdbSyncStatus)}
                      </td>

                      {/* Actions */}
                      <td className="py-3.5 px-4 sm:px-6 text-right">
                        <div className="flex items-center justify-end gap-1.5">
                          {/* Reconcile button if failed */}
                          {(bus.rtdbSyncStatus === 'FAILED' || bus.rtdbSyncStatus === 'PERMANENTLY_FAILED') && (
                            <button
                              onClick={() => handleInlineReconcile(bus.busId)}
                              title="Reconcile Operational Data"
                              className="p-1.5 text-rose-600 hover:text-rose-800 hover:bg-rose-50 rounded-lg transition-colors"
                            >
                              <RefreshCw className="w-4 h-4" />
                            </button>
                          )}

                          {/* View Details */}
                          <button
                            onClick={() => setSelectedBusDetails(bus)}
                            title="View Bus Details"
                            className="p-1.5 text-slate-500 hover:text-blue-600 hover:bg-slate-100 rounded-lg transition-colors"
                          >
                            <Eye className="w-4 h-4" />
                          </button>

                          {/* Quick Route Assignment */}
                          <button
                            onClick={() => setAssignRouteBus(bus)}
                            title="Assign Route"
                            className="p-1.5 text-slate-500 hover:text-indigo-600 hover:bg-slate-100 rounded-lg transition-colors"
                          >
                            <RouteIcon className="w-4 h-4" />
                          </button>

                          {/* Assign / Change Driver */}
                          <button
                            onClick={() => openAssignDriverModal(bus)}
                            title={bus.driverId ? 'Change / Unassign Driver' : 'Assign Driver'}
                            className="p-1.5 text-slate-500 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                          >
                            <UserPlus className="w-4 h-4" />
                          </button>

                          {/* Edit Details */}
                          <button
                            onClick={() => setEditBus(bus)}
                            title="Edit Bus Details"
                            className="p-1.5 text-slate-500 hover:text-slate-800 hover:bg-slate-100 rounded-lg transition-colors"
                          >
                            <Edit2 className="w-4 h-4" />
                          </button>

                          {/* Activate / Deactivate Toggle */}
                          <button
                            onClick={() => setToggleStatusBus(bus)}
                            title={bus.active ? 'Deactivate Bus' : 'Activate Bus'}
                            className={`p-1.5 rounded-lg transition-colors ${
                              bus.active
                                ? 'text-slate-400 hover:text-amber-600 hover:bg-amber-50'
                                : 'text-slate-400 hover:text-emerald-600 hover:bg-emerald-50'
                            }`}
                          >
                            <Power className="w-4 h-4" />
                          </button>

                          {/* Delete Bus */}
                          <button
                            onClick={() => setDeleteBusTarget(bus)}
                            title="Delete Bus"
                            className="p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition-colors"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination Footer */}
        {filteredBuses.length > ITEMS_PER_PAGE && (
          <div className="flex items-center justify-between px-6 py-3.5 border-t border-slate-200/80 bg-slate-50/50 text-xs">
            <span className="text-slate-500">
              Showing {(currentPage - 1) * ITEMS_PER_PAGE + 1} to{' '}
              {Math.min(currentPage * ITEMS_PER_PAGE, filteredBuses.length)} of {filteredBuses.length} buses
            </span>
            <div className="flex items-center gap-2">
              <button
                onClick={() => handlePageChange(currentPage - 1)}
                disabled={currentPage === 1}
                className="p-1.5 rounded-lg border border-slate-200 text-slate-600 hover:bg-slate-100 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <span className="font-semibold text-slate-700">
                Page {currentPage} of {totalPages}
              </span>
              <button
                onClick={() => handlePageChange(currentPage + 1)}
                disabled={currentPage === totalPages}
                className="p-1.5 rounded-lg border border-slate-200 text-slate-600 hover:bg-slate-100 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Modals & Drawers */}
      <RegisterBusModal
        isOpen={isRegisterOpen}
        onClose={() => setIsRegisterOpen(false)}
        routes={routes}
        onSuccess={(created) => {
          showToast('success', `Bus ${created.busNumber} (${created.registrationNumber}) registered successfully.`);
        }}
      />

      <EditBusModal
        isOpen={Boolean(editBus)}
        bus={editBus}
        routes={routes}
        onClose={() => setEditBus(null)}
        onSuccess={() => {
          showToast('success', 'Bus details updated successfully.');
        }}
      />

      <AssignRouteModal
        isOpen={Boolean(assignRouteBus)}
        bus={assignRouteBus}
        routes={routes}
        onClose={() => setAssignRouteBus(null)}
        onSuccess={() => {
          showToast('success', 'Route assignment updated successfully.');
        }}
      />

      <ToggleBusStatusModal
        isOpen={Boolean(toggleStatusBus)}
        bus={toggleStatusBus}
        onClose={() => setToggleStatusBus(null)}
        onSuccess={(newStatus) => {
          showToast(
            'success',
            `Bus ${newStatus ? 'activated' : 'deactivated'} successfully.`
          );
        }}
      />

      <DeleteBusModal
        isOpen={Boolean(deleteBusTarget)}
        bus={deleteBusTarget}
        onClose={() => setDeleteBusTarget(null)}
        onSuccess={(deleted) => {
          showToast('success', `Bus ${deleted.busNumber} (${deleted.registrationNumber}) deleted permanently.`);
        }}
      />

      {assignDriverBus && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/70 backdrop-blur-sm animate-in fade-in duration-200">
          <div className="relative w-full max-w-md bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden">
            <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 bg-slate-50/50">
              <div className="flex items-center gap-3">
                <div className="p-2 bg-blue-100 text-blue-700 rounded-xl">
                  <UserPlus className="w-5 h-5" />
                </div>
                <div>
                  <h2 className="text-lg font-bold text-slate-900">
                    {assignDriverBus.driverId ? 'Change / Unassign Driver' : 'Assign Driver'}
                  </h2>
                  <p className="text-xs text-slate-500">
                    Bus {assignDriverBus.busNumber} ({assignDriverBus.registrationNumber})
                  </p>
                </div>
              </div>
              <button
                onClick={() => setAssignDriverBus(null)}
                disabled={isAssigningDriver}
                className="p-1.5 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded-lg transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleAssignDriverSubmit} className="p-6 space-y-4">
              {assignDriverError && (
                <div className="flex items-start gap-3 p-3.5 bg-rose-50 border border-rose-200 rounded-xl text-rose-800 text-xs font-medium">
                  <AlertCircle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
                  <span>{assignDriverError}</span>
                </div>
              )}

              {!assignDriverBus.active && (
                <div className="flex items-start gap-2.5 p-3 bg-amber-50 border border-amber-200 rounded-xl text-amber-800 text-xs">
                  <AlertTriangle className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
                  <span>This bus is inactive. Activate the bus before assigning a driver.</span>
                </div>
              )}

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1.5">
                  Select Driver
                </label>
                <select
                  value={selectedDriverUid}
                  onChange={(e) => {
                    setSelectedDriverUid(e.target.value);
                    setConfirmDriverReplace(false);
                    setAssignDriverError(null);
                  }}
                  disabled={isAssigningDriver}
                  className="w-full px-3.5 py-2.5 bg-white border border-slate-300 rounded-xl text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                >
                  <option value="">-- Unassign Driver --</option>
                  {drivers
                    .filter((d) => d.active || d.uid === assignDriverBus.driverId)
                    .map((d) => (
                      <option key={d.uid} value={d.uid} disabled={!d.active}>
                        {d.name} ({d.customId})
                        {d.assignedBusId && d.assignedBusId !== assignDriverBus.busId
                          ? ` — Currently on ${d.assignedBusId}`
                          : ''}
                        {!d.active ? ' [Inactive]' : ''}
                      </option>
                    ))}
                </select>
              </div>

              {selectedDriverUid &&
                (() => {
                  const chosen = drivers.find((d) => d.uid === selectedDriverUid);
                  if (chosen && chosen.assignedBusId && chosen.assignedBusId !== assignDriverBus.busId) {
                    return (
                      <div className="p-3 bg-blue-50 border border-blue-200 rounded-xl text-xs text-blue-800">
                        <strong>Transfer Notice:</strong> {chosen.name} will be unassigned from{' '}
                        <span className="font-mono">{chosen.assignedBusId}</span> and assigned to Bus{' '}
                        {assignDriverBus.busNumber}.
                      </div>
                    );
                  }
                  return null;
                })()}

              {selectedDriverUid &&
                assignDriverBus.driverId &&
                assignDriverBus.driverId !== selectedDriverUid && (
                  <label className="flex items-start gap-2.5 p-3 bg-amber-50 border border-amber-200 rounded-xl text-xs text-amber-900 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={confirmDriverReplace}
                      onChange={(e) => setConfirmDriverReplace(e.target.checked)}
                      disabled={isAssigningDriver}
                      className="mt-0.5 rounded border-amber-400 text-blue-600 focus:ring-blue-500"
                    />
                    <span>
                      Confirm replacing current driver{' '}
                      <strong>{assignDriverBus.driverName || assignDriverBus.driverId}</strong>.
                    </span>
                  </label>
                )}

              <div className="flex items-center justify-end gap-3 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setAssignDriverBus(null)}
                  disabled={isAssigningDriver}
                  className="px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100 rounded-xl transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={
                    isAssigningDriver ||
                    (!assignDriverBus.active && Boolean(selectedDriverUid)) ||
                    selectedDriverUid === (assignDriverBus.driverId || '')
                  }
                  className="inline-flex items-center gap-2 px-5 py-2 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed rounded-xl shadow-sm transition-all"
                >
                  {isAssigningDriver ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" />
                      Saving...
                    </>
                  ) : selectedDriverUid ? (
                    'Save Assignment'
                  ) : (
                    'Unassign Driver'
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      <BusDetailsDrawer
        isOpen={Boolean(selectedBusDetails)}
        bus={selectedBusDetails}
        routes={routes}
        onClose={() => setSelectedBusDetails(null)}
        onOpenEdit={(b) => {
          setSelectedBusDetails(null);
          setEditBus(b);
        }}
        onOpenAssignRoute={(b) => {
          setSelectedBusDetails(null);
          setAssignRouteBus(b);
        }}
        onReconcileSuccess={() => {
          showToast('success', 'Operational data synchronized.');
        }}
      />
    </div>
  );
};

export default FleetManagement;

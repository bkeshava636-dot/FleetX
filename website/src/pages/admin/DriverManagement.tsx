import React, { useState, useEffect, useMemo } from 'react';
import {
  Users,
  Search,
  Plus,
  Bus,
  CheckCircle2,
  XCircle,
  AlertTriangle,
  Power,
  Edit2,
  Eye,
  Key,
  Trash2,
  ChevronLeft,
  ChevronRight,
  ShieldAlert,
  UserCheck,
  UserX
} from 'lucide-react';
import { subscribeToDrivers, naturalDriverSort } from '../../services/driverService';
import { subscribeToBuses } from '../../services/fleetService';
import { subscribeToRoutes } from '../../services/routesService';
import { DriverRecord, BusRecord } from '../../types/fleet';
import { Route } from '../../types/admin';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { EmptyState } from '../../components/common/EmptyState';
import { ErrorState } from '../../components/common/ErrorState';
import { RegisterDriverModal } from '../../components/admin/drivers/RegisterDriverModal';
import { EditDriverModal } from '../../components/admin/drivers/EditDriverModal';
import { ManageAssignmentModal } from '../../components/admin/drivers/ManageAssignmentModal';
import { ResetPasswordModal } from '../../components/admin/drivers/ResetPasswordModal';
import { ToggleDriverStatusModal } from '../../components/admin/drivers/ToggleDriverStatusModal';
import { DeleteDriverModal } from '../../components/admin/drivers/DeleteDriverModal';
import { DriverDetailsDrawer } from '../../components/admin/drivers/DriverDetailsDrawer';

const ITEMS_PER_PAGE = 10;

export const DriverManagement: React.FC = () => {
  const [drivers, setDrivers] = useState<DriverRecord[]>([]);
  const [buses, setBuses] = useState<BusRecord[]>([]);
  const [routes, setRoutes] = useState<Route[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Filters & Search
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'ACTIVE' | 'INACTIVE'>('ALL');
  const [assignmentFilter, setAssignmentFilter] = useState<'ALL' | 'ASSIGNED' | 'UNASSIGNED'>('ALL');
  const [busFilter, setBusFilter] = useState<string>('ALL');
  const [sortBy, setSortBy] = useState<'NAME' | 'STAFF_ID'>('NAME');
  const [sortDirection, setSortDirection] = useState<'ASC' | 'DESC'>('ASC');
  const [currentPage, setCurrentPage] = useState<number>(1);

  // Modals & Drawers state
  const [isRegisterOpen, setIsRegisterOpen] = useState(false);
  const [editDriver, setEditDriver] = useState<DriverRecord | null>(null);
  const [assignmentDriver, setAssignmentDriver] = useState<DriverRecord | null>(null);
  const [resetPasswordDriver, setResetPasswordDriver] = useState<DriverRecord | null>(null);
  const [toggleStatusDriver, setToggleStatusDriver] = useState<DriverRecord | null>(null);
  const [deleteDriver, setDeleteDriver] = useState<DriverRecord | null>(null);
  const [selectedDriverDetails, setSelectedDriverDetails] = useState<DriverRecord | null>(null);

  // Notification Toast
  const [toast, setToast] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  const showToast = (type: 'success' | 'error', message: string) => {
    setToast({ type, message });
    setTimeout(() => {
      setToast(null);
    }, 4500);
  };

  // Subscriptions setup
  useEffect(() => {
    setIsLoading(true);
    setErrorMessage(null);

    const unsubDrivers = subscribeToDrivers(
      (data) => {
        setDrivers(data);
        setIsLoading(false);
      },
      (err) => {
        console.error('[DriverManagement] Driver subscription error:', err);
        setErrorMessage(err.message || 'Failed to stream drivers.');
        setIsLoading(false);
      }
    );

    const unsubBuses = subscribeToBuses(
      (busData) => {
        setBuses(busData);
      },
      (err) => {
        console.warn('[DriverManagement] Bus subscription warning:', err);
      }
    );

    const unsubRoutes = subscribeToRoutes(
      (routeData) => {
        setRoutes(routeData);
      },
      (err) => {
        console.warn('[DriverManagement] Route subscription warning:', err);
      }
    );

    return () => {
      unsubDrivers();
      unsubBuses();
      unsubRoutes();
    };
  }, []);

  // Update selected driver in drawer if drivers update
  useEffect(() => {
    if (selectedDriverDetails) {
      const updated = drivers.find((d) => d.uid === selectedDriverDetails.uid);
      if (updated) setSelectedDriverDetails(updated);
    }
  }, [drivers, selectedDriverDetails]);

  // Dashboard Metrics
  const metrics = useMemo(() => {
    let active = 0;
    let inactive = 0;
    let assigned = 0;
    let unassigned = 0;
    let syncErrors = 0;

    const busSyncMap = new Map<string, string>();
    for (const b of buses) {
      busSyncMap.set(b.busId, b.rtdbSyncStatus || 'SYNCED');
    }

    for (const d of drivers) {
      if (d.active) active++;
      else inactive++;

      if (d.assignedBusId) {
        assigned++;
        const syncStatus = busSyncMap.get(d.assignedBusId);
        if (syncStatus === 'FAILED' || syncStatus === 'PERMANENTLY_FAILED') {
          syncErrors++;
        }
      } else {
        unassigned++;
      }
    }

    return {
      total: drivers.length,
      active,
      inactive,
      assigned,
      unassigned,
      syncErrors
    };
  }, [drivers, buses]);

  // Bus Lookup Map
  const busMap = useMemo(() => {
    const map = new Map<string, BusRecord>();
    for (const b of buses) {
      map.set(b.busId, b);
    }
    return map;
  }, [buses]);

  // Search & Filtered Drivers
  const filteredDrivers = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();

    const filtered = drivers.filter((d) => {
      // 1. Search filter
      if (q) {
        const matchesName = (d.name || '').toLowerCase().includes(q);
        const matchesStaffId = (d.customId || '').toLowerCase().includes(q);
        const matchesEmail = (d.email || '').toLowerCase().includes(q);
        const matchesPhone = (d.phoneNumber || '').includes(q);

        const assignedBus = d.assignedBusId ? busMap.get(d.assignedBusId) : null;
        const matchesBusNum = assignedBus
          ? assignedBus.busNumber.toLowerCase().includes(q) || assignedBus.registrationNumber.toLowerCase().includes(q)
          : false;

        if (!matchesName && !matchesStaffId && !matchesEmail && !matchesPhone && !matchesBusNum) {
          return false;
        }
      }

      // 2. Status filter
      if (statusFilter === 'ACTIVE' && !d.active) return false;
      if (statusFilter === 'INACTIVE' && d.active) return false;

      // 3. Assignment filter
      if (assignmentFilter === 'ASSIGNED' && !d.assignedBusId) return false;
      if (assignmentFilter === 'UNASSIGNED' && d.assignedBusId) return false;

      // 4. Bus filter
      if (busFilter !== 'ALL') {
        if (d.assignedBusId !== busFilter) return false;
      }

      return true;
    });

    // Sort
    return filtered.sort((a, b) => {
      let cmp = 0;
      if (sortBy === 'NAME') {
        cmp = naturalDriverSort(a, b);
      } else {
        cmp = (a.customId || '').localeCompare(b.customId || '');
      }
      return sortDirection === 'ASC' ? cmp : -cmp;
    });
  }, [drivers, searchQuery, statusFilter, assignmentFilter, busFilter, sortBy, sortDirection, busMap]);

  // Pagination Slice
  const totalPages = Math.max(1, Math.ceil(filteredDrivers.length / ITEMS_PER_PAGE));
  const paginatedDrivers = useMemo(() => {
    const start = (currentPage - 1) * ITEMS_PER_PAGE;
    return filteredDrivers.slice(start, start + ITEMS_PER_PAGE);
  }, [filteredDrivers, currentPage]);

  const handlePageChange = (newPage: number) => {
    if (newPage >= 1 && newPage <= totalPages) {
      setCurrentPage(newPage);
    }
  };

  const handleResetFilters = () => {
    setSearchQuery('');
    setStatusFilter('ALL');
    setAssignmentFilter('ALL');
    setBusFilter('ALL');
    setCurrentPage(1);
  };

  return (
    <div className="space-y-6">
      {/* Toast Alert */}
      {toast && (
        <div
          className={`fixed top-5 right-5 z-50 flex items-center gap-2 px-4 py-3 rounded-xl shadow-lg border text-sm font-medium animate-in slide-in-from-top-2 duration-200 ${
            toast.type === 'success'
              ? 'bg-emerald-50 text-emerald-800 border-emerald-200'
              : 'bg-red-50 text-red-800 border-red-200'
          }`}
        >
          {toast.type === 'success' ? (
            <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
          ) : (
            <AlertTriangle className="w-4 h-4 text-red-600 shrink-0" />
          )}
          <span>{toast.message}</span>
        </div>
      )}

      {/* Header and Action Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-black text-slate-900 tracking-tight flex items-center gap-2.5">
            <Users className="w-7 h-7 text-blue-600" />
            Driver Management
          </h1>
          <p className="text-sm text-slate-500 mt-0.5">
            Manage authenticated campus transit drivers, Staff IDs, and operational vehicle assignments.
          </p>
        </div>

        <button
          onClick={() => setIsRegisterOpen(true)}
          className="inline-flex items-center justify-center gap-2 px-4 py-2.5 bg-blue-600 hover:bg-blue-700 active:bg-blue-800 text-white font-semibold text-sm rounded-xl shadow-sm hover:shadow transition-all"
        >
          <Plus className="w-4 h-4" />
          Register Driver
        </button>
      </div>

      {/* Dashboard Metric Summary Cards */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
        {/* Total Drivers */}
        <div className="p-4 bg-white rounded-2xl border border-slate-200 shadow-sm flex flex-col justify-between">
          <div className="flex items-center justify-between text-slate-400 mb-2">
            <span className="text-xs font-bold uppercase tracking-wider">Total</span>
            <Users className="w-4 h-4 text-blue-600" />
          </div>
          <div>
            <span className="text-2xl font-black text-slate-900">{isLoading ? '...' : metrics.total}</span>
            <span className="text-[11px] text-slate-500 block mt-0.5">Registered accounts</span>
          </div>
        </div>

        {/* Active Drivers */}
        <div className="p-4 bg-white rounded-2xl border border-slate-200 shadow-sm flex flex-col justify-between">
          <div className="flex items-center justify-between text-slate-400 mb-2">
            <span className="text-xs font-bold uppercase tracking-wider">Active</span>
            <CheckCircle2 className="w-4 h-4 text-emerald-600" />
          </div>
          <div>
            <span className="text-2xl font-black text-emerald-600">{isLoading ? '...' : metrics.active}</span>
            <span className="text-[11px] text-slate-500 block mt-0.5">Operational drivers</span>
          </div>
        </div>

        {/* Inactive Drivers */}
        <div className="p-4 bg-white rounded-2xl border border-slate-200 shadow-sm flex flex-col justify-between">
          <div className="flex items-center justify-between text-slate-400 mb-2">
            <span className="text-xs font-bold uppercase tracking-wider">Inactive</span>
            <XCircle className="w-4 h-4 text-slate-400" />
          </div>
          <div>
            <span className="text-2xl font-black text-slate-600">{isLoading ? '...' : metrics.inactive}</span>
            <span className="text-[11px] text-slate-500 block mt-0.5">Suspended accounts</span>
          </div>
        </div>

        {/* Assigned Drivers */}
        <div className="p-4 bg-white rounded-2xl border border-slate-200 shadow-sm flex flex-col justify-between">
          <div className="flex items-center justify-between text-slate-400 mb-2">
            <span className="text-xs font-bold uppercase tracking-wider">Assigned</span>
            <UserCheck className="w-4 h-4 text-indigo-600" />
          </div>
          <div>
            <span className="text-2xl font-black text-indigo-600">{isLoading ? '...' : metrics.assigned}</span>
            <span className="text-[11px] text-slate-500 block mt-0.5">Assigned to buses</span>
          </div>
        </div>

        {/* Unassigned Drivers */}
        <div className="p-4 bg-white rounded-2xl border border-slate-200 shadow-sm flex flex-col justify-between">
          <div className="flex items-center justify-between text-slate-400 mb-2">
            <span className="text-xs font-bold uppercase tracking-wider">Unassigned</span>
            <UserX className="w-4 h-4 text-amber-600" />
          </div>
          <div>
            <span className="text-2xl font-black text-amber-600">{isLoading ? '...' : metrics.unassigned}</span>
            <span className="text-[11px] text-slate-500 block mt-0.5">Available for standby</span>
          </div>
        </div>

        {/* Sync Issues */}
        <div className="p-4 bg-white rounded-2xl border border-slate-200 shadow-sm flex flex-col justify-between">
          <div className="flex items-center justify-between text-slate-400 mb-2">
            <span className="text-xs font-bold uppercase tracking-wider">Sync Issues</span>
            <ShieldAlert className="w-4 h-4 text-red-500" />
          </div>
          <div>
            <span className="text-2xl font-black text-red-600">{isLoading ? '...' : metrics.syncErrors}</span>
            <span className="text-[11px] text-slate-500 block mt-0.5">RTDB sync delayed</span>
          </div>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="p-4 bg-white rounded-2xl border border-slate-200 shadow-sm space-y-3">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
          {/* Search Box */}
          <div className="relative md:col-span-2">
            <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => {
                setSearchQuery(e.target.value);
                setCurrentPage(1);
              }}
              placeholder="Search by Staff ID, name, email, phone, or bus number..."
              className="w-full pl-10 pr-4 py-2 text-sm bg-slate-50 border border-slate-200 rounded-xl focus:bg-white focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all placeholder:text-slate-400"
            />
          </div>

          {/* Status Filter */}
          <div>
            <select
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value as 'ALL' | 'ACTIVE' | 'INACTIVE');
                setCurrentPage(1);
              }}
              className="w-full px-3.5 py-2 text-sm bg-slate-50 border border-slate-200 rounded-xl focus:bg-white focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all font-medium text-slate-700"
            >
              <option value="ALL">All Account Statuses</option>
              <option value="ACTIVE">Active Drivers Only</option>
              <option value="INACTIVE">Inactive Drivers Only</option>
            </select>
          </div>

          {/* Assignment Filter */}
          <div>
            <select
              value={assignmentFilter}
              onChange={(e) => {
                setAssignmentFilter(e.target.value as 'ALL' | 'ASSIGNED' | 'UNASSIGNED');
                setCurrentPage(1);
              }}
              className="w-full px-3.5 py-2 text-sm bg-slate-50 border border-slate-200 rounded-xl focus:bg-white focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all font-medium text-slate-700"
            >
              <option value="ALL">All Assignment States</option>
              <option value="ASSIGNED">Assigned to a Bus</option>
              <option value="UNASSIGNED">Unassigned Drivers</option>
            </select>
          </div>
        </div>

        {/* Secondary Filter Line */}
        <div className="flex flex-wrap items-center justify-between gap-3 pt-2 border-t border-slate-100">
          <div className="flex items-center gap-3">
            {/* Bus Filter */}
            <div className="flex items-center gap-2">
              <span className="text-xs font-semibold text-slate-500">Filter Bus:</span>
              <select
                value={busFilter}
                onChange={(e) => {
                  setBusFilter(e.target.value);
                  setCurrentPage(1);
                }}
                className="px-2.5 py-1 text-xs bg-slate-50 border border-slate-200 rounded-lg text-slate-700 font-medium"
              >
                <option value="ALL">All Buses</option>
                {buses.map((b) => (
                  <option key={b.busId} value={b.busId}>
                    {b.busNumber} ({b.registrationNumber})
                  </option>
                ))}
              </select>
            </div>

            {/* Sort Toggle */}
            <div className="flex items-center gap-2">
              <span className="text-xs font-semibold text-slate-500">Sort by:</span>
              <button
                type="button"
                onClick={() => {
                  if (sortBy === 'NAME') {
                    setSortDirection(sortDirection === 'ASC' ? 'DESC' : 'ASC');
                  } else {
                    setSortBy('NAME');
                    setSortDirection('ASC');
                  }
                }}
                className={`px-2.5 py-1 text-xs font-semibold rounded-lg transition-colors ${
                  sortBy === 'NAME' ? 'bg-blue-100 text-blue-800' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                }`}
              >
                Name {sortBy === 'NAME' && (sortDirection === 'ASC' ? '↑' : '↓')}
              </button>
              <button
                type="button"
                onClick={() => {
                  if (sortBy === 'STAFF_ID') {
                    setSortDirection(sortDirection === 'ASC' ? 'DESC' : 'ASC');
                  } else {
                    setSortBy('STAFF_ID');
                    setSortDirection('ASC');
                  }
                }}
                className={`px-2.5 py-1 text-xs font-semibold rounded-lg transition-colors ${
                  sortBy === 'STAFF_ID' ? 'bg-blue-100 text-blue-800' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                }`}
              >
                Staff ID {sortBy === 'STAFF_ID' && (sortDirection === 'ASC' ? '↑' : '↓')}
              </button>
            </div>
          </div>

          {(searchQuery || statusFilter !== 'ALL' || assignmentFilter !== 'ALL' || busFilter !== 'ALL') && (
            <button
              onClick={handleResetFilters}
              className="text-xs font-semibold text-blue-600 hover:text-blue-800 transition-colors"
            >
              Reset Filters
            </button>
          )}
        </div>
      </div>

      {/* Main Content Area */}
      {isLoading ? (
        <LoadingSpinner label="Loading Driver Management..." />
      ) : errorMessage ? (
        <ErrorState
          title="Failed to Load Drivers"
          message={errorMessage}
          onRetry={() => window.location.reload()}
        />
      ) : filteredDrivers.length === 0 ? (
        <EmptyState
          icon={Users}
          title={drivers.length === 0 ? 'No Drivers Registered' : 'No Matching Drivers Found'}
          description={
            drivers.length === 0
              ? 'Get started by registering your first campus bus driver.'
              : 'Try clearing your search query or relaxing your status and assignment filters.'
          }
          actionLabel={drivers.length === 0 ? 'Register First Driver' : 'Clear Filters'}
          onAction={drivers.length === 0 ? () => setIsRegisterOpen(true) : handleResetFilters}
        />
      ) : (
        <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
          {/* Drivers Table */}
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="border-b border-slate-100 bg-slate-50/60 text-[11px] font-bold text-slate-500 uppercase tracking-wider">
                  <th className="py-3 px-4">Staff ID</th>
                  <th className="py-3 px-4">Driver Name</th>
                  <th className="py-3 px-4">Contact</th>
                  <th className="py-3 px-4">Assigned Bus</th>
                  <th className="py-3 px-4">Status</th>
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-xs text-slate-700">
                {paginatedDrivers.map((driver) => {
                  const assignedBus = driver.assignedBusId
                    ? busMap.get(driver.assignedBusId) || null
                    : null;

                  return (
                    <tr
                      key={driver.uid}
                      className="hover:bg-slate-50/70 transition-colors group cursor-pointer"
                      onClick={() => setSelectedDriverDetails(driver)}
                    >
                      {/* Staff ID */}
                      <td className="py-3.5 px-4">
                        <span className="font-mono font-bold text-slate-900 bg-slate-100 px-2 py-0.5 rounded border border-slate-200">
                          {driver.customId}
                        </span>
                      </td>

                      {/* Driver Name */}
                      <td className="py-3.5 px-4">
                        <div className="flex items-center gap-2.5">
                          <div className="w-7 h-7 rounded-lg bg-blue-100 text-blue-700 flex items-center justify-center font-bold text-xs shrink-0">
                            {driver.name.charAt(0).toUpperCase()}
                          </div>
                          <div>
                            <span className="font-semibold text-slate-900 block">{driver.name}</span>
                          </div>
                        </div>
                      </td>

                      {/* Contact */}
                      <td className="py-3.5 px-4 font-mono text-[11px]">
                        <span className="text-slate-800 block">{driver.email}</span>
                        <span className="text-slate-400 block mt-0.5">+91 {driver.phoneNumber}</span>
                      </td>

                      {/* Assigned Bus */}
                      <td className="py-3.5 px-4" onClick={(e) => e.stopPropagation()}>
                        {assignedBus ? (
                          <div className="flex items-center gap-2">
                            <span className="font-bold text-slate-900">{assignedBus.busNumber}</span>
                            <span className="font-mono text-[11px] text-blue-700 bg-blue-50 border border-blue-200/60 px-1.5 py-0.5 rounded">
                              {assignedBus.registrationNumber}
                            </span>
                            {assignedBus.rtdbSyncStatus === 'FAILED' && (
                              <span
                                title="RTDB sync failed"
                                className="w-2 h-2 rounded-full bg-red-500 animate-pulse"
                              />
                            )}
                          </div>
                        ) : (
                          <span className="text-xs text-slate-400 font-medium italic">Unassigned</span>
                        )}
                      </td>

                      {/* Status */}
                      <td className="py-3.5 px-4">
                        <span
                          className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-semibold ${
                            driver.active
                              ? 'bg-emerald-100 text-emerald-800'
                              : 'bg-slate-200 text-slate-700'
                          }`}
                        >
                          {driver.active ? (
                            <>
                              <CheckCircle2 className="w-3 h-3 text-emerald-600" />
                              Active
                            </>
                          ) : (
                            <>
                              <XCircle className="w-3 h-3 text-slate-500" />
                              Inactive
                            </>
                          )}
                        </span>
                      </td>

                      {/* Actions */}
                      <td
                        className="py-3.5 px-4 text-right space-x-1"
                        onClick={(e) => e.stopPropagation()}
                      >
                        {/* View Drawer */}
                        <button
                          type="button"
                          title="View Details"
                          onClick={() => setSelectedDriverDetails(driver)}
                          className="p-1.5 text-slate-500 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                        >
                          <Eye className="w-4 h-4" />
                        </button>

                        {/* Edit Profile */}
                        <button
                          type="button"
                          title="Edit Profile"
                          onClick={() => setEditDriver(driver)}
                          className="p-1.5 text-slate-500 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                        >
                          <Edit2 className="w-4 h-4" />
                        </button>

                        {/* Manage Bus Assignment */}
                        <button
                          type="button"
                          title="Manage Bus Assignment"
                          onClick={() => setAssignmentDriver(driver)}
                          className="p-1.5 text-slate-500 hover:text-indigo-600 hover:bg-indigo-50 rounded-lg transition-colors"
                        >
                          <Bus className="w-4 h-4" />
                        </button>

                        {/* Reset Password */}
                        <button
                          type="button"
                          title="Reset Password"
                          onClick={() => setResetPasswordDriver(driver)}
                          className="p-1.5 text-slate-500 hover:text-amber-600 hover:bg-amber-50 rounded-lg transition-colors"
                        >
                          <Key className="w-4 h-4" />
                        </button>

                        {/* Toggle Status */}
                        <button
                          type="button"
                          title={driver.active ? 'Deactivate Driver' : 'Activate Driver'}
                          onClick={() => setToggleStatusDriver(driver)}
                          className={`p-1.5 rounded-lg transition-colors ${
                            driver.active
                              ? 'text-slate-500 hover:text-amber-600 hover:bg-amber-50'
                              : 'text-slate-500 hover:text-emerald-600 hover:bg-emerald-50'
                          }`}
                        >
                          <Power className="w-4 h-4" />
                        </button>

                        {/* Delete Account */}
                        <button
                          type="button"
                          title="Delete Driver"
                          onClick={() => setDeleteDriver(driver)}
                          className="p-1.5 text-slate-500 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {/* Table Footer with Pagination */}
          <div className="flex flex-col sm:flex-row items-center justify-between gap-3 px-4 py-3 bg-slate-50/50 border-t border-slate-100 text-xs text-slate-500">
            <div>
              Showing{' '}
              <span className="font-semibold text-slate-800">
                {(currentPage - 1) * ITEMS_PER_PAGE + 1}
              </span>{' '}
              to{' '}
              <span className="font-semibold text-slate-800">
                {Math.min(currentPage * ITEMS_PER_PAGE, filteredDrivers.length)}
              </span>{' '}
              of <span className="font-semibold text-slate-800">{filteredDrivers.length}</span> drivers
            </div>

            <div className="flex items-center gap-1.5">
              <button
                type="button"
                onClick={() => handlePageChange(currentPage - 1)}
                disabled={currentPage === 1}
                className="p-1.5 border border-slate-200 rounded-lg hover:bg-white disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <span className="px-3 font-semibold text-slate-700">
                Page {currentPage} of {totalPages}
              </span>
              <button
                type="button"
                onClick={() => handlePageChange(currentPage + 1)}
                disabled={currentPage === totalPages}
                className="p-1.5 border border-slate-200 rounded-lg hover:bg-white disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Modals & Slide-over Drawer */}
      <RegisterDriverModal
        isOpen={isRegisterOpen}
        onClose={() => setIsRegisterOpen(false)}
        onSuccess={(created) => {
          showToast('success', `Driver ${created.customId} registered successfully.`);
        }}
        buses={buses}
      />

      <EditDriverModal
        isOpen={Boolean(editDriver)}
        onClose={() => setEditDriver(null)}
        onSuccess={(updated) => {
          showToast('success', `Driver ${updated.customId} profile updated successfully.`);
        }}
        driver={editDriver}
      />

      <ManageAssignmentModal
        isOpen={Boolean(assignmentDriver)}
        onClose={() => setAssignmentDriver(null)}
        onSuccess={(msg) => {
          showToast('success', msg);
        }}
        driver={assignmentDriver}
        buses={buses}
      />

      <ResetPasswordModal
        isOpen={Boolean(resetPasswordDriver)}
        onClose={() => setResetPasswordDriver(null)}
        onSuccess={(msg) => {
          showToast('success', msg);
        }}
        driver={resetPasswordDriver}
      />

      <ToggleDriverStatusModal
        isOpen={Boolean(toggleStatusDriver)}
        onClose={() => setToggleStatusDriver(null)}
        onSuccess={(newStatus) => {
          showToast(
            'success',
            `Driver account ${newStatus ? 'activated' : 'deactivated'} successfully.`
          );
        }}
        driver={toggleStatusDriver}
        buses={buses}
      />

      <DeleteDriverModal
        isOpen={Boolean(deleteDriver)}
        onClose={() => setDeleteDriver(null)}
        onSuccess={(name) => {
          showToast('success', `Driver account for ${name} deleted successfully.`);
        }}
        driver={deleteDriver}
        buses={buses}
      />

      <DriverDetailsDrawer
        isOpen={Boolean(selectedDriverDetails)}
        onClose={() => setSelectedDriverDetails(null)}
        driver={selectedDriverDetails}
        buses={buses}
        routes={routes}
        onOpenEdit={(d) => {
          setSelectedDriverDetails(null);
          setEditDriver(d);
        }}
        onOpenManageAssignment={(d) => {
          setSelectedDriverDetails(null);
          setAssignmentDriver(d);
        }}
        onOpenResetPassword={(d) => {
          setSelectedDriverDetails(null);
          setResetPasswordDriver(d);
        }}
        onOpenToggleStatus={(d) => {
          setSelectedDriverDetails(null);
          setToggleStatusDriver(d);
        }}
        onOpenDelete={(d) => {
          setSelectedDriverDetails(null);
          setDeleteDriver(d);
        }}
        onReconcileSuccess={() => {
          showToast('success', 'RTDB operational synchronization repaired successfully.');
        }}
      />
    </div>
  );
};

export default DriverManagement;

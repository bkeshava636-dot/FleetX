import React, { useEffect, useMemo, useState } from 'react';
import {
  Repeat,
  Plus,
  Search,
  CheckCircle2,
  XCircle,
  Clock,
  Bus,
  AlertTriangle,
} from 'lucide-react';
import { useAdminAuth } from '../../context/AdminAuthContext';
import { subscribeToBuses } from '../../services/fleetService';
import { subscribeToRoutes } from '../../services/routesService';
import {
  createBusSwap,
  subscribeToBusSwaps,
  updateBusSwapStatus,
} from '../../services/operationsService';
import { BusRecord } from '../../types/fleet';
import { Route } from '../../types/admin';
import { BusSwapRecord, BusSwapScope, BusSwapStatus } from '../../types/operations';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { EmptyState } from '../../components/common/EmptyState';
import { ErrorState } from '../../components/common/ErrorState';

export const BusSwaps: React.FC = () => {
  const { profile } = useAdminAuth();
  const [swaps, setSwaps] = useState<BusSwapRecord[]>([]);
  const [buses, setBuses] = useState<BusRecord[]>([]);
  const [routes, setRoutes] = useState<Route[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const [statusFilter, setStatusFilter] = useState<'ALL' | BusSwapStatus>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [isCreateOpen, setIsCreateOpen] = useState(false);

  // Form state
  const [originalBusId, setOriginalBusId] = useState('');
  const [replacementBusId, setReplacementBusId] = useState('');
  const [scopeType, setScopeType] = useState<BusSwapScope>('ALL_STOPS');
  const [selectedStopIds, setSelectedStopIds] = useState<string[]>([]);
  const [message, setMessage] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [actionBusyId, setActionBusyId] = useState<string | null>(null);
  const [pendingAction, setPendingAction] = useState<{
    swap: BusSwapRecord;
    status: 'RESOLVED' | 'CANCELLED';
  } | null>(null);
  const [toast, setToast] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const showToast = (type: 'success' | 'error', text: string) => {
    setToast({ type, text });
    setTimeout(() => setToast(null), 4500);
  };

  useEffect(() => {
    setIsLoading(true);
    const unsubSwaps = subscribeToBusSwaps(
      (data) => {
        setSwaps(data);
        setIsLoading(false);
      },
      (err) => {
        setErrorMessage(err.message || 'Failed to load bus swaps.');
        setIsLoading(false);
      }
    );
    const unsubBuses = subscribeToBuses(setBuses, () => {});
    const unsubRoutes = subscribeToRoutes(setRoutes, () => {});

    return () => {
      unsubSwaps();
      unsubBuses();
      unsubRoutes();
    };
  }, []);

  const busMap = useMemo(() => {
    const map = new Map<string, BusRecord>();
    buses.forEach((b) => map.set(b.busId, b));
    return map;
  }, [buses]);

  const routeMap = useMemo(() => {
    const map = new Map<string, Route>();
    routes.forEach((r) => map.set(r.routeId, r));
    return map;
  }, [routes]);

  const selectedOriginalBus = busMap.get(originalBusId) || null;
  const selectedRoute = selectedOriginalBus?.routeId
    ? routeMap.get(selectedOriginalBus.routeId) || null
    : null;

  const eligibleReplacementBuses = useMemo(() => {
    return buses.filter((b) => b.active && b.busId !== originalBusId);
  }, [buses, originalBusId]);

  const filteredSwaps = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    return swaps.filter((s) => {
      if (statusFilter !== 'ALL' && s.status !== statusFilter) return false;
      if (!q) return true;
      return (
        s.originalBusId.toLowerCase().includes(q) ||
        s.replacementBusId.toLowerCase().includes(q) ||
        s.routeId.toLowerCase().includes(q) ||
        s.driverName.toLowerCase().includes(q) ||
        s.message.toLowerCase().includes(q)
      );
    });
  }, [swaps, statusFilter, searchQuery]);

  const activeCount = swaps.filter((s) => s.status === 'ACTIVE').length;

  const handleToggleStop = (stopId: string) => {
    setSelectedStopIds((prev) =>
      prev.includes(stopId) ? prev.filter((id) => id !== stopId) : [...prev, stopId]
    );
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!originalBusId || !replacementBusId) {
      showToast('error', 'Select both the original bus and replacement bus.');
      return;
    }
    if (originalBusId === replacementBusId) {
      showToast('error', 'Original and replacement bus cannot be the same.');
      return;
    }
    if (scopeType === 'SELECTED_STOPS' && selectedStopIds.length === 0) {
      showToast('error', 'Select at least one affected stop or choose All Stops.');
      return;
    }

    setIsSubmitting(true);
    try {
      await createBusSwap({
        originalBusId,
        replacementBusId,
        routeId: selectedOriginalBus?.routeId || '',
        driverId: selectedOriginalBus?.driverId || profile?.uid || '',
        driverName: selectedOriginalBus?.driverName || profile?.name || 'Admin',
        scopeType,
        affectedStopIds: selectedStopIds,
        message: message || `Bus ${originalBusId} swapped with ${replacementBusId}`,
        createdByUid: profile?.uid || '',
      });
      showToast('success', `Bus swap created: ${originalBusId} → ${replacementBusId}`);
      setIsCreateOpen(false);
      setOriginalBusId('');
      setReplacementBusId('');
      setScopeType('ALL_STOPS');
      setSelectedStopIds([]);
      setMessage('');
    } catch (err) {
      showToast('error', err instanceof Error ? err.message : 'Failed to create bus swap.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleStatusChange = async (swapId: string, status: BusSwapStatus) => {
    setActionBusyId(swapId);
    try {
      await updateBusSwapStatus(swapId, status, profile?.uid || '');
      showToast('success', `Swap marked as ${status}.`);
      setPendingAction(null);
    } catch (err) {
      showToast('error', err instanceof Error ? err.message : 'Failed to update swap status.');
    } finally {
      setActionBusyId(null);
    }
  };

  return (
    <div className="space-y-6">
      {toast && (
        <div
          className={`fixed bottom-5 right-5 z-50 px-4 py-3 rounded-xl shadow-lg text-xs font-semibold flex items-center gap-2 border ${
            toast.type === 'success'
              ? 'bg-emerald-50 text-emerald-800 border-emerald-200'
              : 'bg-red-50 text-red-800 border-red-200'
          }`}
        >
          {toast.type === 'success' ? (
            <CheckCircle2 className="w-4 h-4 text-emerald-600" />
          ) : (
            <AlertTriangle className="w-4 h-4 text-red-600" />
          )}
          <span>{toast.text}</span>
        </div>
      )}

      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-slate-900 flex items-center gap-2">
            <Repeat className="w-5 h-5 text-blue-600" />
            <span>Bus Swaps</span>
          </h2>
          <p className="text-xs text-slate-500 mt-1">
            Manage vehicle substitutions and notify students when a route is served by another bus.
          </p>
        </div>
        <button
          onClick={() => setIsCreateOpen(true)}
          className="inline-flex items-center gap-2 px-4 py-2.5 bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold rounded-xl shadow-sm transition-colors"
        >
          <Plus className="w-4 h-4" />
          <span>New Bus Swap</span>
        </button>
      </div>

      {/* Summary Strip */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-white rounded-2xl border border-slate-200 p-4">
          <p className="text-xs font-medium text-slate-500">Active Swaps</p>
          <p className="text-2xl font-bold text-amber-600 mt-1">{activeCount}</p>
        </div>
        <div className="bg-white rounded-2xl border border-slate-200 p-4">
          <p className="text-xs font-medium text-slate-500">Total Swaps Recorded</p>
          <p className="text-2xl font-bold text-slate-900 mt-1">{swaps.length}</p>
        </div>
        <div className="bg-white rounded-2xl border border-slate-200 p-4">
          <p className="text-xs font-medium text-slate-500">Active Fleet Available</p>
          <p className="text-2xl font-bold text-blue-600 mt-1">
            {buses.filter((b) => b.active).length}
          </p>
        </div>
      </div>

      {/* Filters */}
      <div className="bg-white rounded-2xl border border-slate-200 p-4 flex flex-col sm:flex-row gap-3 items-stretch sm:items-center justify-between">
        <div className="relative flex-1 max-w-md">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search by bus, route, driver, or note..."
            className="w-full pl-9 pr-3 py-2 text-xs border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <div className="flex items-center gap-2">
          {(['ALL', 'ACTIVE', 'RESOLVED', 'CANCELLED'] as const).map((status) => (
            <button
              key={status}
              onClick={() => setStatusFilter(status)}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-colors ${
                statusFilter === status
                  ? 'bg-blue-600 text-white'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              {status}
            </button>
          ))}
        </div>
      </div>

      {errorMessage && <ErrorState title="Bus Swaps Error" message={errorMessage} />}

      {isLoading ? (
        <LoadingSpinner label="Loading bus swaps..." />
      ) : filteredSwaps.length === 0 ? (
        <EmptyState
          title="No bus swaps found"
          description="There are no bus swap records matching your current filters."
          icon={Repeat}
        />
      ) : (
        <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50 text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                  <th className="py-3 px-4">Swap</th>
                  <th className="py-3 px-4">Route & Scope</th>
                  <th className="py-3 px-4">Message</th>
                  <th className="py-3 px-4">Status</th>
                  <th className="py-3 px-4">Created</th>
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-xs">
                {filteredSwaps.map((swap) => (
                  <tr key={swap.swapId} className="hover:bg-slate-50/70">
                    <td className="py-3 px-4">
                      <div className="flex items-center gap-2 font-semibold text-slate-900">
                        <Bus className="w-4 h-4 text-slate-400" />
                        <span>{swap.originalBusId}</span>
                        <span className="text-slate-400">→</span>
                        <span className="text-blue-600">{swap.replacementBusId}</span>
                      </div>
                      <p className="text-[11px] text-slate-500 mt-0.5">
                        {swap.driverName || swap.driverId || 'Admin'}
                      </p>
                    </td>
                    <td className="py-3 px-4">
                      <p className="font-medium text-slate-800">{swap.routeId || 'N/A'}</p>
                      <p className="text-[11px] text-slate-500">
                        {swap.scopeType === 'SELECTED_STOPS'
                          ? `${swap.affectedStopIds.length} selected stops`
                          : 'All stops'}
                      </p>
                    </td>
                    <td className="py-3 px-4 max-w-xs">
                      <p className="text-slate-700 line-clamp-2">{swap.message || '—'}</p>
                    </td>
                    <td className="py-3 px-4">
                      <span
                        className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold ${
                          swap.status === 'ACTIVE'
                            ? 'bg-amber-100 text-amber-800'
                            : swap.status === 'RESOLVED'
                            ? 'bg-emerald-100 text-emerald-800'
                            : 'bg-slate-100 text-slate-600'
                        }`}
                      >
                        {swap.status}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-slate-500 whitespace-nowrap">
                      <div className="flex items-center gap-1">
                        <Clock className="w-3.5 h-3.5" />
                        <span>
                          {swap.createdAt
                            ? new Date(swap.createdAt).toLocaleString()
                            : swap.date || '—'}
                        </span>
                      </div>
                    </td>
                    <td className="py-3 px-4 text-right whitespace-nowrap">
                      {swap.status === 'ACTIVE' ? (
                        <div className="inline-flex items-center gap-2">
                          <button
                            disabled={actionBusyId === swap.swapId}
                            onClick={() => setPendingAction({ swap, status: 'RESOLVED' })}
                            className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-emerald-50 text-emerald-700 hover:bg-emerald-100 font-semibold disabled:opacity-50"
                          >
                            <CheckCircle2 className="w-3.5 h-3.5" />
                            <span>Resolve</span>
                          </button>
                          <button
                            disabled={actionBusyId === swap.swapId}
                            onClick={() => setPendingAction({ swap, status: 'CANCELLED' })}
                            className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-red-50 text-red-700 hover:bg-red-100 font-semibold disabled:opacity-50"
                          >
                            <XCircle className="w-3.5 h-3.5" />
                            <span>Cancel</span>
                          </button>
                        </div>
                      ) : (
                        <span className="text-slate-400 text-[11px]">Closed</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Create Swap Modal */}
      {isCreateOpen && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-lg w-full p-6 shadow-xl border border-slate-200 max-h-[90vh] overflow-y-auto">
            <h3 className="text-base font-bold text-slate-900">Create Bus Swap</h3>
            <p className="text-xs text-slate-500 mt-1">
              Assign a replacement bus for an active route. Students on affected stops will be notified.
            </p>

            <form onSubmit={handleCreate} className="mt-4 space-y-4 text-xs">
              <div>
                <label className="block font-semibold text-slate-700 mb-1">Original Bus</label>
                <select
                  required
                  value={originalBusId}
                  onChange={(e) => {
                    setOriginalBusId(e.target.value);
                    setSelectedStopIds([]);
                  }}
                  className="w-full border border-slate-200 rounded-xl px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="">Select original bus...</option>
                  {buses
                    .filter((b) => b.active)
                    .map((b) => (
                      <option key={b.busId} value={b.busId}>
                        {b.busNumber || b.busId} ({b.routeId || 'No route'})
                      </option>
                    ))}
                </select>
              </div>

              <div>
                <label className="block font-semibold text-slate-700 mb-1">Replacement Bus</label>
                <select
                  required
                  value={replacementBusId}
                  onChange={(e) => setReplacementBusId(e.target.value)}
                  className="w-full border border-slate-200 rounded-xl px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="">Select replacement bus...</option>
                  {eligibleReplacementBuses.map((b) => (
                    <option key={b.busId} value={b.busId}>
                      {b.busNumber || b.busId} ({b.registrationNumber || b.busId})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block font-semibold text-slate-700 mb-1">Affected Stops</label>
                <div className="flex gap-3">
                  <label className="inline-flex items-center gap-1.5">
                    <input
                      type="radio"
                      checked={scopeType === 'ALL_STOPS'}
                      onChange={() => setScopeType('ALL_STOPS')}
                    />
                    <span>All Stops</span>
                  </label>
                  <label className="inline-flex items-center gap-1.5">
                    <input
                      type="radio"
                      checked={scopeType === 'SELECTED_STOPS'}
                      onChange={() => setScopeType('SELECTED_STOPS')}
                    />
                    <span>Selected Stops</span>
                  </label>
                </div>
                {scopeType === 'SELECTED_STOPS' && selectedRoute && (
                  <div className="mt-2 max-h-36 overflow-y-auto border border-slate-200 rounded-xl p-2 space-y-1">
                    {selectedRoute.stops.map((stop) => (
                      <label key={stop.stopId} className="flex items-center gap-2 py-1 px-1 hover:bg-slate-50 rounded">
                        <input
                          type="checkbox"
                          checked={selectedStopIds.includes(stop.stopId)}
                          onChange={() => handleToggleStop(stop.stopId)}
                        />
                        <span>{stop.name}</span>
                      </label>
                    ))}
                  </div>
                )}
              </div>

              <div>
                <label className="block font-semibold text-slate-700 mb-1">Notice Message</label>
                <textarea
                  rows={3}
                  value={message}
                  onChange={(e) => setMessage(e.target.value)}
                  placeholder="Optional reason or instructions for students..."
                  className="w-full border border-slate-200 rounded-xl px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>

              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setIsCreateOpen(false)}
                  className="px-4 py-2 rounded-xl border border-slate-200 text-slate-600 font-semibold hover:bg-slate-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="px-4 py-2 rounded-xl bg-blue-600 hover:bg-blue-700 text-white font-semibold disabled:opacity-50"
                >
                  {isSubmitting ? 'Creating...' : 'Create Swap'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Confirm Resolve / Cancel Modal */}
      {pendingAction && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-xl border border-slate-200 space-y-4">
            <div className="flex items-start gap-3">
              <div
                className={`p-2.5 rounded-xl ${
                  pendingAction.status === 'RESOLVED'
                    ? 'bg-emerald-50 text-emerald-600'
                    : 'bg-red-50 text-red-600'
                }`}
              >
                {pendingAction.status === 'RESOLVED' ? (
                  <CheckCircle2 className="w-5 h-5" />
                ) : (
                  <AlertTriangle className="w-5 h-5" />
                )}
              </div>
              <div>
                <h3 className="text-base font-bold text-slate-900">
                  {pendingAction.status === 'RESOLVED' ? 'Resolve Bus Swap?' : 'Cancel Bus Swap?'}
                </h3>
                <p className="text-xs text-slate-500 mt-1">
                  {pendingAction.status === 'RESOLVED'
                    ? 'This will mark the bus swap as completed and close the active student notice.'
                    : 'This will cancel the active bus swap and record the cancellation in swap history.'}
                </p>
              </div>
            </div>

            <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 text-xs space-y-1">
              <p className="font-semibold text-slate-900">
                {pendingAction.swap.originalBusId} → {pendingAction.swap.replacementBusId}
              </p>
              <p className="text-slate-600">
                Route: {pendingAction.swap.routeId || 'N/A'}
              </p>
            </div>

            <div className="flex justify-end gap-2 pt-1 text-xs">
              <button
                type="button"
                disabled={actionBusyId === pendingAction.swap.swapId}
                onClick={() => setPendingAction(null)}
                className="px-4 py-2 rounded-xl border border-slate-200 text-slate-600 font-semibold hover:bg-slate-50 disabled:opacity-50"
              >
                Keep Active
              </button>
              <button
                type="button"
                disabled={actionBusyId === pendingAction.swap.swapId}
                onClick={() =>
                  handleStatusChange(pendingAction.swap.swapId, pendingAction.status)
                }
                className={`px-4 py-2 rounded-xl text-white font-semibold disabled:opacity-50 ${
                  pendingAction.status === 'RESOLVED'
                    ? 'bg-emerald-600 hover:bg-emerald-700'
                    : 'bg-red-600 hover:bg-red-700'
                }`}
              >
                {actionBusyId === pendingAction.swap.swapId
                  ? 'Updating...'
                  : pendingAction.status === 'RESOLVED'
                  ? 'Confirm Resolve'
                  : 'Confirm Cancel'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default BusSwaps;

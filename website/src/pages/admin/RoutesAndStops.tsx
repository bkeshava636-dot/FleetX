import React, { useEffect, useState, useMemo } from 'react';
import {
  Route as RouteIcon,
  Search,
  CheckCircle2,
  AlertTriangle,
  Bus,
  ChevronRight,
  Layers,
  Copy,
  Check,
  Plus,
  Pencil,
  Trash2,
  ArrowUp,
  ArrowDown,
  Map as MapIcon,
  Bell,
  X,
} from 'lucide-react';
import {
  subscribeToRoutes,
  checkStopHasCoordinates,
  reorderStopsList,
  saveRouteStops,
  deleteRoute,
  checkStopReferences,
  reindexStops,
} from '../../services/routesService';
import { subscribeToBuses } from '../../services/fleetService';
import { BusStop, Route } from '../../types/admin';
import { BusRecord } from '../../types/fleet';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { EmptyState } from '../../components/common/EmptyState';
import { ErrorState } from '../../components/common/ErrorState';
import { RouteFormModal } from '../../components/admin/routes/RouteFormModal';
import { StopFormModal } from '../../components/admin/routes/StopFormModal';
import { DeleteConfirmModal } from '../../components/admin/routes/DeleteConfirmModal';
import { RouteTimetablePdfCard } from '../../components/admin/routes/RouteTimetablePdfCard';
import { RouteStopsMapEditor } from '../../components/maps/RouteStopsMapEditor';

interface ToastState {
  type: 'success' | 'error';
  message: string;
}

export const RoutesAndStops: React.FC = () => {
  const [routes, setRoutes] = useState<Route[]>([]);
  const [buses, setBuses] = useState<BusRecord[]>([]);
  const [selectedRouteId, setSelectedRouteId] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [filterMode, setFilterMode] = useState<'ALL' | 'MISSING_ONLY' | 'ACTIVE_ONLY'>('ALL');
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [copiedStopId, setCopiedStopId] = useState<string | null>(null);
  const [toast, setToast] = useState<ToastState | null>(null);

  // Optimistic stop ordering per route
  const [optimisticStops, setOptimisticStops] = useState<{
    routeId: string;
    stops: BusStop[];
  } | null>(null);
  const [isReordering, setIsReordering] = useState<boolean>(false);

  // Modal states
  const [routeModal, setRouteModal] = useState<{
    isOpen: boolean;
    mode: 'create' | 'edit';
    route: Route | null;
  }>({ isOpen: false, mode: 'create', route: null });

  const [stopModal, setStopModal] = useState<{
    isOpen: boolean;
    mode: 'create' | 'edit';
    stop: BusStop | null;
  }>({ isOpen: false, mode: 'create', stop: null });

  const [deleteRouteModal, setDeleteRouteModal] = useState<Route | null>(null);
  const [deleteStopModal, setDeleteStopModal] = useState<{
    stop: BusStop;
    warningBanner: string | null;
  } | null>(null);
  const [checkingStopId, setCheckingStopId] = useState<string | null>(null);
  const [isMapEditorOpen, setIsMapEditorOpen] = useState<boolean>(false);

  const showToast = (type: 'success' | 'error', message: string) => {
    setToast({ type, message });
  };

  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(null), 4500);
    return () => clearTimeout(timer);
  }, [toast]);

  useEffect(() => {
    setIsLoading(true);
    setErrorMessage(null);

    const unsubscribeRoutes = subscribeToRoutes(
      (data) => {
        setRoutes(data);
        setIsLoading(false);
        setSelectedRouteId((prev) => {
          if (prev && data.some((r) => r.routeId === prev)) return prev;
          return data.length > 0 ? data[0].routeId : null;
        });
      },
      (err) => {
        console.error('[RoutesAndStops] Listener error:', err);
        setErrorMessage(err.message || 'Failed to fetch routes from Firestore.');
        setIsLoading(false);
      }
    );

    const unsubscribeBuses = subscribeToBuses(
      (busData) => {
        setBuses(busData);
      },
      (err) => {
        console.error('[RoutesAndStops] Buses listener error:', err);
      }
    );

    return () => {
      unsubscribeRoutes();
      unsubscribeBuses();
    };
  }, []);

  // Map routeId -> assigned buses
  const busesByRouteId = useMemo(() => {
    const map = new Map<string, BusRecord[]>();
    for (const bus of buses) {
      if (!bus.routeId) continue;
      const list = map.get(bus.routeId) || [];
      list.push(bus);
      map.set(bus.routeId, list);
    }
    return map;
  }, [buses]);

  const getAssignedBusLabels = (route: Route): string[] => {
    const assigned = busesByRouteId.get(route.routeId) || [];
    const labels = assigned.map((b) =>
      b.busNumber ? `Bus ${b.busNumber}` : b.busId
    );
    if (labels.length === 0 && route.busId) {
      labels.push(route.busId);
    }
    return labels;
  };

  // Filter routes based on search query and filter mode
  const filteredRoutes = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();

    return routes.filter((route) => {
      const assignedBuses = busesByRouteId.get(route.routeId) || [];
      const matchesBus = assignedBuses.some(
        (b) =>
          b.busId.toLowerCase().includes(q) ||
          b.busNumber.toLowerCase().includes(q)
      );

      const matchesSearch =
        !q ||
        route.routeId.toLowerCase().includes(q) ||
        route.name.toLowerCase().includes(q) ||
        route.busId.toLowerCase().includes(q) ||
        matchesBus ||
        route.stops.some(
          (s) =>
            s.name.toLowerCase().includes(q) ||
            s.stopId.toLowerCase().includes(q)
        );

      if (!matchesSearch) return false;

      if (filterMode === 'ACTIVE_ONLY') {
        return route.active;
      }
      if (filterMode === 'MISSING_ONLY') {
        return route.stops.some(
          (s) => s.active && s.visibleOnMap !== false && !checkStopHasCoordinates(s)
        );
      }

      return true;
    });
  }, [routes, busesByRouteId, searchQuery, filterMode]);

  // Selected route object
  const activeRoute = useMemo(() => {
    return (
      routes.find((r) => r.routeId === selectedRouteId) ||
      filteredRoutes[0] ||
      null
    );
  }, [routes, selectedRouteId, filteredRoutes]);

  // Effective stops list (applies optimistic reorder if active for selected route)
  const displayedStops = useMemo(() => {
    if (!activeRoute) return [];
    if (optimisticStops && optimisticStops.routeId === activeRoute.routeId) {
      return optimisticStops.stops;
    }
    return activeRoute.stops;
  }, [activeRoute, optimisticStops]);

  const handleCopy = (stopId: string) => {
    navigator.clipboard.writeText(stopId);
    setCopiedStopId(stopId);
    setTimeout(() => setCopiedStopId(null), 2000);
  };

  const handleMoveStop = async (fromIndex: number, toIndex: number) => {
    if (!activeRoute || isReordering) return;
    if (toIndex < 0 || toIndex >= displayedStops.length) return;

    const previousStops = displayedStops.map((s) => ({ ...s }));
    const reordered = reorderStopsList(previousStops, fromIndex, toIndex);

    setOptimisticStops({ routeId: activeRoute.routeId, stops: reordered });
    setIsReordering(true);

    try {
      await saveRouteStops(activeRoute.routeId, reordered);
      setOptimisticStops(null);
      showToast('success', 'Stop order updated.');
    } catch (err: unknown) {
      setOptimisticStops(null);
      const msg =
        err instanceof Error ? err.message : 'Failed to reorder stops.';
      showToast('error', `Reorder failed (rolled back): ${msg}`);
    } finally {
      setIsReordering(false);
    }
  };

  const handleRequestDeleteStop = async (stop: BusStop) => {
    if (!activeRoute) return;
    setCheckingStopId(stop.stopId);
    try {
      const refs = await checkStopReferences(activeRoute.routeId, stop.stopId);
      const parts: string[] = [];
      if (refs.pendingSubmissions > 0) {
        parts.push(
          `${refs.pendingSubmissions} pending location submission${
            refs.pendingSubmissions === 1 ? '' : 's'
          }`
        );
      }
      if (refs.pendingRequests > 0) {
        parts.push(
          `${refs.pendingRequests} pending stop request${
            refs.pendingRequests === 1 ? '' : 's'
          }`
        );
      }
      const warningBanner =
        parts.length > 0
          ? `Warning: This stop is referenced by ${parts.join(' and ')}. Deleting it may affect pending reviews.`
          : null;

      setDeleteStopModal({ stop, warningBanner });
    } catch (err: unknown) {
      const msg =
        err instanceof Error
          ? err.message
          : 'Could not check stop references before deletion.';
      showToast('error', msg);
    } finally {
      setCheckingStopId(null);
    }
  };

  // Calculate quick summary metrics for current view
  const summary = useMemo(() => {
    let totalStops = 0;
    let missingCoords = 0;
    routes.forEach((r) => {
      r.stops.forEach((s) => {
        if (s.active && s.visibleOnMap !== false) {
          totalStops++;
          if (!checkStopHasCoordinates(s)) missingCoords++;
        }
      });
    });
    return {
      totalRoutes: routes.length,
      totalStops,
      missingCoords,
      mappedCoords: totalStops - missingCoords,
    };
  }, [routes]);

  if (isLoading) {
    return (
      <div className="bg-white rounded-2xl p-12 border border-slate-200/80 shadow-sm text-center">
        <LoadingSpinner
          label="Connecting to Firestore routes and stop catalogs..."
          size="lg"
        />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Toast Notification */}
      {toast && (
        <div
          className={`fixed bottom-5 right-5 z-50 flex items-center gap-3 px-4 py-3 rounded-xl shadow-lg border text-xs font-semibold animate-in fade-in slide-in-from-bottom-2 duration-200 ${
            toast.type === 'success'
              ? 'bg-emerald-900 text-emerald-50 border-emerald-700'
              : 'bg-rose-900 text-rose-50 border-rose-700'
          }`}
        >
          {toast.type === 'success' ? (
            <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
          ) : (
            <AlertTriangle className="w-4 h-4 text-rose-400 shrink-0" />
          )}
          <span>{toast.message}</span>
          <button
            type="button"
            onClick={() => setToast(null)}
            className="p-0.5 hover:opacity-80"
            aria-label="Dismiss notification"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      )}

      {/* Header with Metrics Overview & Create Route CTA */}
      <div className="bg-white rounded-2xl p-6 border border-slate-200/80 shadow-sm flex flex-col lg:flex-row items-start lg:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-slate-500">
            <RouteIcon className="w-4 h-4 text-indigo-600" />
            <span>Transit Network Catalog</span>
          </div>
          <h2 className="text-xl sm:text-2xl font-extrabold text-slate-900 tracking-tight mt-1">
            Routes & Stop Sequence Manager
          </h2>
          <p className="text-xs text-slate-500 mt-1 max-w-xl">
            Manage canonical routes, ordered stops, pickup coordinates, and map alignments in Firestore.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2.5 w-full lg:w-auto justify-between lg:justify-end">
          {/* Real-time Summary Pills */}
          <div className="flex flex-wrap items-center gap-2">
            <div className="px-3 py-1.5 rounded-xl bg-slate-100 text-slate-800 text-xs font-semibold border border-slate-200">
              <span className="text-slate-500 font-normal mr-1">Routes:</span>
              {summary.totalRoutes}
            </div>
            <div className="px-3 py-1.5 rounded-xl bg-emerald-50 text-emerald-800 text-xs font-semibold border border-emerald-200">
              <span className="text-emerald-600 font-normal mr-1">Mapped:</span>
              {summary.mappedCoords}
            </div>
            <div className="px-3 py-1.5 rounded-xl bg-amber-50 text-amber-800 text-xs font-semibold border border-amber-200">
              <span className="text-amber-600 font-normal mr-1">Missing:</span>
              {summary.missingCoords}
            </div>
          </div>

          <button
            type="button"
            onClick={() =>
              setRouteModal({ isOpen: true, mode: 'create', route: null })
            }
            className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-xs font-semibold shadow-sm transition-colors"
          >
            <Plus className="w-4 h-4" />
            <span>Create Route</span>
          </button>
        </div>
      </div>

      {errorMessage && (
        <ErrorState
          title="Routes Loading Warning"
          message={errorMessage}
          onRetry={() => window.location.reload()}
        />
      )}

      {/* Search and Filters Bar */}
      <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-sm flex flex-col sm:flex-row items-center justify-between gap-3">
        <div className="relative w-full sm:w-80">
          <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400">
            <Search className="w-4 h-4" />
          </div>
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search by route, stop, or bus..."
            className="w-full pl-10 pr-4 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs sm:text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:bg-white transition-all"
          />
        </div>

        <div className="flex items-center gap-1.5 w-full sm:w-auto overflow-x-auto pb-1 sm:pb-0">
          <button
            type="button"
            onClick={() => setFilterMode('ALL')}
            className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-all whitespace-nowrap ${
              filterMode === 'ALL'
                ? 'bg-slate-900 text-white shadow-sm'
                : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
            }`}
          >
            All Routes ({routes.length})
          </button>
          <button
            type="button"
            onClick={() => setFilterMode('MISSING_ONLY')}
            className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-all whitespace-nowrap flex items-center gap-1 ${
              filterMode === 'MISSING_ONLY'
                ? 'bg-amber-500 text-white shadow-sm'
                : 'bg-amber-50 text-amber-700 hover:bg-amber-100'
            }`}
          >
            <AlertTriangle className="w-3 h-3" />
            <span>Needs Coordinates</span>
          </button>
          <button
            type="button"
            onClick={() => setFilterMode('ACTIVE_ONLY')}
            className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-all whitespace-nowrap ${
              filterMode === 'ACTIVE_ONLY'
                ? 'bg-blue-600 text-white shadow-sm'
                : 'bg-blue-50 text-blue-700 hover:bg-blue-100'
            }`}
          >
            Active Only
          </button>
        </div>
      </div>

      {/* Main Two-Column Explorer Layout */}
      {routes.length === 0 ? (
        <EmptyState
          icon={RouteIcon}
          title="No Routes Configured"
          description="Create your first transit route to start adding ordered stops and assigning buses."
          actionLabel="Create Route"
          onAction={() =>
            setRouteModal({ isOpen: true, mode: 'create', route: null })
          }
        />
      ) : filteredRoutes.length === 0 ? (
        <EmptyState
          icon={Search}
          title="No Matching Routes Found"
          description="Try adjusting your search query or switching the filter mode to show all routes."
          actionLabel="Clear Search"
          onAction={() => {
            setSearchQuery('');
            setFilterMode('ALL');
          }}
        />
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          {/* Left Column: Routes List */}
          <div className="lg:col-span-5 bg-white rounded-2xl border border-slate-200/80 shadow-sm overflow-hidden">
            <div className="p-4 border-b border-slate-100 bg-slate-50/70 flex items-center justify-between">
              <span className="text-xs font-bold text-slate-700 uppercase tracking-wider flex items-center gap-1.5">
                <Layers className="w-3.5 h-3.5 text-slate-500" />
                <span>Select Route ({filteredRoutes.length})</span>
              </span>
              <span className="text-[11px] text-slate-400">Click to manage</span>
            </div>

            <div className="divide-y divide-slate-100 max-h-[640px] overflow-y-auto custom-scrollbar">
              {filteredRoutes.map((route) => {
                const isSelected = activeRoute?.routeId === route.routeId;
                const missingCount = route.stops.filter(
                  (s) =>
                    s.active &&
                    s.visibleOnMap !== false &&
                    !checkStopHasCoordinates(s)
                ).length;
                const busLabels = getAssignedBusLabels(route);

                return (
                  <button
                    key={route.routeId}
                    type="button"
                    onClick={() => setSelectedRouteId(route.routeId)}
                    className={`w-full text-left p-4 transition-all flex items-center justify-between group ${
                      isSelected
                        ? 'bg-blue-50/70 border-l-4 border-blue-600'
                        : 'hover:bg-slate-50/90'
                    }`}
                  >
                    <div className="min-w-0 pr-3">
                      <div className="flex flex-wrap items-center gap-1.5 mb-1">
                        <span className="text-xs font-bold font-mono text-slate-900 bg-slate-100 px-2 py-0.5 rounded-md">
                          {route.routeId}
                        </span>
                        {busLabels.map((label) => (
                          <span
                            key={label}
                            className="text-[11px] text-slate-600 bg-slate-100 px-2 py-0.5 rounded-md flex items-center gap-1"
                          >
                            <Bus className="w-3 h-3 text-slate-500" />
                            <span>{label}</span>
                          </span>
                        ))}
                        {!route.active && (
                          <span className="text-[10px] text-slate-500 bg-slate-200 px-1.5 py-0.5 rounded">
                            Inactive
                          </span>
                        )}
                      </div>

                      <h4 className="text-sm font-bold text-slate-800 truncate group-hover:text-blue-600 transition-colors">
                        {route.name}
                      </h4>

                      <div className="flex items-center gap-3 text-xs text-slate-500 mt-1">
                        <span>{route.stops.length} stops</span>
                        {missingCount > 0 ? (
                          <span className="text-amber-600 font-semibold flex items-center gap-1 text-[11px]">
                            <AlertTriangle className="w-3 h-3" />
                            <span>{missingCount} missing GPS</span>
                          </span>
                        ) : (
                          <span className="text-emerald-600 font-semibold flex items-center gap-1 text-[11px]">
                            <CheckCircle2 className="w-3 h-3" />
                            <span>All mapped</span>
                          </span>
                        )}
                      </div>
                    </div>

                    <ChevronRight
                      className={`w-4 h-4 flex-shrink-0 transition-transform ${
                        isSelected
                          ? 'text-blue-600 translate-x-1'
                          : 'text-slate-300 group-hover:text-slate-500'
                      }`}
                    />
                  </button>
                );
              })}
            </div>
          </div>

          {/* Right Column: Ordered Stops & Actions for Selected Route */}
          <div className="lg:col-span-7 bg-white rounded-2xl border border-slate-200/80 shadow-sm overflow-hidden">
            {activeRoute ? (
              <div>
                {/* Route Header Info Card */}
                <div className="p-5 border-b border-slate-100 bg-slate-50/80 space-y-4">
                  <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-4">
                    <div>
                      <div className="flex flex-wrap items-center gap-2 mb-1.5">
                        <span className="text-xs font-bold font-mono text-blue-700 bg-blue-100 px-2.5 py-1 rounded-lg">
                          {activeRoute.routeId}
                        </span>
                        {getAssignedBusLabels(activeRoute).map((label) => (
                          <span
                            key={label}
                            className="text-xs font-semibold text-slate-700 bg-white border border-slate-200 px-2.5 py-1 rounded-lg flex items-center gap-1"
                          >
                            <Bus className="w-3.5 h-3.5 text-blue-600" />
                            <span>{label}</span>
                          </span>
                        ))}
                        <span
                          className={`text-xs font-semibold px-2.5 py-1 rounded-lg ${
                            activeRoute.active
                              ? 'bg-emerald-100 text-emerald-800'
                              : 'bg-slate-200 text-slate-700'
                          }`}
                        >
                          {activeRoute.active ? 'Active Transit Line' : 'Inactive'}
                        </span>
                      </div>
                      <h3 className="text-lg font-extrabold text-slate-900 tracking-tight">
                        {activeRoute.name}
                      </h3>
                      {activeRoute.description && (
                        <p className="text-xs text-slate-500 mt-1 leading-relaxed">
                          {activeRoute.description}
                        </p>
                      )}
                    </div>

                    <div className="flex items-center gap-2 shrink-0">
                      <button
                        type="button"
                        onClick={() =>
                          setRouteModal({
                            isOpen: true,
                            mode: 'edit',
                            route: activeRoute,
                          })
                        }
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-slate-700 bg-white border border-slate-200 hover:bg-slate-100 rounded-xl transition-colors"
                      >
                        <Pencil className="w-3.5 h-3.5" />
                        <span>Edit Route</span>
                      </button>
                      <button
                        type="button"
                        onClick={() => setDeleteRouteModal(activeRoute)}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-rose-700 bg-rose-50 border border-rose-200 hover:bg-rose-100 rounded-xl transition-colors"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                        <span>Delete</span>
                      </button>
                    </div>
                  </div>

                  {/* Stop Management Toolbar */}
                  <div className="flex flex-wrap items-center justify-between gap-2 pt-3 border-t border-slate-200/70">
                    <div className="text-xs text-slate-600">
                      <span className="font-bold text-slate-900">
                        {displayedStops.length} Stops
                      </span>{' '}
                      •{' '}
                      <span>
                        {displayedStops.filter((s) => checkStopHasCoordinates(s)).length}{' '}
                        mapped on GPS
                      </span>
                    </div>

                    <div className="flex flex-wrap items-center gap-2">
                      <button
                        type="button"
                        onClick={() => setIsMapEditorOpen(true)}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-indigo-700 bg-indigo-50 hover:bg-indigo-100 border border-indigo-200 rounded-xl transition-colors"
                      >
                        <MapIcon className="w-3.5 h-3.5" />
                        <span>Open Map Editor</span>
                      </button>
                      <button
                        type="button"
                        onClick={() =>
                          setStopModal({
                            isOpen: true,
                            mode: 'create',
                            stop: null,
                          })
                        }
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-xl shadow-sm transition-colors"
                      >
                        <Plus className="w-3.5 h-3.5" />
                        <span>Add Stop</span>
                      </button>
                    </div>
                  </div>
                </div>

                <div className="px-5 py-3 border-b border-slate-200 bg-slate-50/40">
                  <RouteTimetablePdfCard route={activeRoute} onToast={showToast} />
                </div>

                {/* Stops Table / Ordered List */}
                <div className="overflow-x-auto">
                  <table className="w-full text-left border-collapse">
                    <thead>
                      <tr className="border-b border-slate-200 bg-slate-50/60 text-[11px] font-bold text-slate-500 uppercase tracking-wider">
                        <th className="py-3 px-3 w-12 text-center">#</th>
                        <th className="py-3 px-3">Stop Name & ID</th>
                        <th className="py-3 px-3">Coordinates</th>
                        <th className="py-3 px-3 text-center">Status</th>
                        <th className="py-3 px-3 text-right">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100 text-xs">
                      {displayedStops.length === 0 ? (
                        <tr>
                          <td
                            colSpan={5}
                            className="py-10 text-center text-slate-400"
                          >
                            No stops configured for this route. Click &ldquo;Add Stop&rdquo; or &ldquo;Open Map Editor&rdquo; to add stops.
                          </td>
                        </tr>
                      ) : (
                        displayedStops.map((stop, idx) => {
                          const hasCoords = checkStopHasCoordinates(stop);
                          const isNotificationOnly = stop.visibleOnMap === false;

                          return (
                            <tr
                              key={stop.stopId || idx}
                              className="hover:bg-slate-50/70 transition-colors"
                            >
                              {/* Order number */}
                              <td className="py-3 px-3 text-center font-bold font-mono text-slate-500">
                                {stop.order || idx + 1}
                              </td>

                              {/* Stop Name & ID */}
                              <td className="py-3 px-3">
                                <div className="flex items-center gap-1.5">
                                  <span className="font-bold text-slate-900">
                                    {stop.name}
                                  </span>
                                  {!stop.active && (
                                    <span className="text-[10px] font-semibold bg-slate-200 text-slate-600 px-1.5 py-0.5 rounded">
                                      Inactive
                                    </span>
                                  )}
                                </div>
                                <div className="flex items-center gap-1.5 mt-0.5">
                                  <span className="font-mono text-[10px] text-slate-400 bg-slate-100 px-1.5 py-0.5 rounded">
                                    {stop.stopId}
                                  </span>
                                  <button
                                    type="button"
                                    onClick={() => handleCopy(stop.stopId)}
                                    title="Copy Stop ID"
                                    className="text-slate-400 hover:text-slate-600"
                                  >
                                    {copiedStopId === stop.stopId ? (
                                      <Check className="w-3 h-3 text-emerald-600" />
                                    ) : (
                                      <Copy className="w-3 h-3" />
                                    )}
                                  </button>
                                </div>
                              </td>

                              {/* Lat / Lng Display */}
                              <td className="py-3 px-3 font-mono text-[11px]">
                                {hasCoords ? (
                                  <span className="text-slate-700">
                                    {stop.latitude.toFixed(6)},{' '}
                                    {stop.longitude.toFixed(6)}
                                  </span>
                                ) : isNotificationOnly ? (
                                  <span className="text-indigo-600 font-sans italic">
                                    Hidden on map
                                  </span>
                                ) : (
                                  <span className="text-amber-600 font-semibold italic">
                                    Not Set (0.0, 0.0)
                                  </span>
                                )}
                              </td>

                              {/* Status Badge */}
                              <td className="py-3 px-3 text-center">
                                {isNotificationOnly ? (
                                  <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-indigo-700 bg-indigo-50 border border-indigo-200/60 px-2 py-0.5 rounded-full">
                                    <Bell className="w-3 h-3" />
                                    <span>Notification-only</span>
                                  </span>
                                ) : hasCoords ? (
                                  <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-emerald-700 bg-emerald-50 border border-emerald-200/60 px-2 py-0.5 rounded-full">
                                    <CheckCircle2 className="w-3 h-3" />
                                    <span>Mapped</span>
                                  </span>
                                ) : (
                                  <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-amber-800 bg-amber-50 border border-amber-200/60 px-2 py-0.5 rounded-full">
                                    <AlertTriangle className="w-3 h-3" />
                                    <span>Missing GPS</span>
                                  </span>
                                )}
                              </td>

                              {/* Row Actions */}
                              <td className="py-3 px-3 text-right">
                                <div className="inline-flex items-center gap-1">
                                  <button
                                    type="button"
                                    title="Move Up"
                                    disabled={idx === 0 || isReordering}
                                    onClick={() => handleMoveStop(idx, idx - 1)}
                                    className="p-1.5 text-slate-500 hover:text-slate-800 hover:bg-slate-100 disabled:opacity-30 disabled:pointer-events-none rounded-lg transition-colors"
                                  >
                                    <ArrowUp className="w-3.5 h-3.5" />
                                  </button>
                                  <button
                                    type="button"
                                    title="Move Down"
                                    disabled={
                                      idx === displayedStops.length - 1 ||
                                      isReordering
                                    }
                                    onClick={() => handleMoveStop(idx, idx + 1)}
                                    className="p-1.5 text-slate-500 hover:text-slate-800 hover:bg-slate-100 disabled:opacity-30 disabled:pointer-events-none rounded-lg transition-colors"
                                  >
                                    <ArrowDown className="w-3.5 h-3.5" />
                                  </button>
                                  <button
                                    type="button"
                                    title="Edit Stop"
                                    onClick={() =>
                                      setStopModal({
                                        isOpen: true,
                                        mode: 'edit',
                                        stop,
                                      })
                                    }
                                    className="p-1.5 text-slate-500 hover:text-blue-700 hover:bg-blue-50 rounded-lg transition-colors"
                                  >
                                    <Pencil className="w-3.5 h-3.5" />
                                  </button>
                                  <button
                                    type="button"
                                    title="Delete Stop"
                                    disabled={checkingStopId === stop.stopId}
                                    onClick={() => handleRequestDeleteStop(stop)}
                                    className="p-1.5 text-slate-500 hover:text-rose-700 hover:bg-rose-50 disabled:opacity-40 rounded-lg transition-colors"
                                  >
                                    <Trash2 className="w-3.5 h-3.5" />
                                  </button>
                                </div>
                              </td>
                            </tr>
                          );
                        })
                      )}
                    </tbody>
                  </table>
                </div>
              </div>
            ) : (
              <div className="p-12 text-center text-slate-400 text-sm">
                Select a route from the left panel to inspect and manage its stops.
              </div>
            )}
          </div>
        </div>
      )}

      {/* Create / Edit Route Modal */}
      <RouteFormModal
        isOpen={routeModal.isOpen}
        mode={routeModal.mode}
        initialRoute={routeModal.route}
        onClose={() =>
          setRouteModal({ isOpen: false, mode: 'create', route: null })
        }
        onSuccess={(msg, newRouteId) => {
          showToast('success', msg);
          if (newRouteId) setSelectedRouteId(newRouteId);
        }}
      />

      {/* Create / Edit Stop Modal */}
      {activeRoute && (
        <StopFormModal
          isOpen={stopModal.isOpen}
          mode={stopModal.mode}
          routeId={activeRoute.routeId}
          existingStops={displayedStops}
          initialStop={stopModal.stop}
          onClose={() =>
            setStopModal({ isOpen: false, mode: 'create', stop: null })
          }
          onSuccess={(msg) => showToast('success', msg)}
        />
      )}

      {/* Delete Route Confirmation Modal */}
      <DeleteConfirmModal
        isOpen={Boolean(deleteRouteModal)}
        title="Delete Route"
        itemName={deleteRouteModal?.name || ''}
        itemId={deleteRouteModal?.routeId}
        description="This will permanently delete the route document and all embedded stops from Firestore. Routes with assigned buses cannot be deleted."
        confirmLabel="Delete Route"
        onClose={() => setDeleteRouteModal(null)}
        onConfirm={async () => {
          if (!deleteRouteModal) return;
          const id = deleteRouteModal.routeId;
          await deleteRoute(id);
          showToast('success', `Route "${id}" deleted.`);
        }}
      />

      {/* Delete Stop Confirmation Modal */}
      <DeleteConfirmModal
        isOpen={Boolean(deleteStopModal)}
        title="Delete Stop"
        itemName={deleteStopModal?.stop.name || ''}
        itemId={deleteStopModal?.stop.stopId}
        description="This will remove the stop from the route and reindex the remaining stops sequentially."
        warningBanner={deleteStopModal?.warningBanner}
        confirmLabel="Delete Stop"
        onClose={() => setDeleteStopModal(null)}
        onConfirm={async () => {
          if (!activeRoute || !deleteStopModal) return;
          const targetId = deleteStopModal.stop.stopId;
          const nextStops = reindexStops(
            displayedStops.filter((s) => s.stopId !== targetId)
          );
          await saveRouteStops(activeRoute.routeId, nextStops);
          showToast(
            'success',
            `Stop "${deleteStopModal.stop.name}" removed from ${activeRoute.routeId}.`
          );
        }}
      />

      {/* Route Stops Map Editor */}
      {activeRoute && (
        <RouteStopsMapEditor
          isOpen={isMapEditorOpen}
          route={activeRoute}
          onClose={() => setIsMapEditorOpen(false)}
          onSuccess={(msg) => showToast('success', msg)}
        />
      )}
    </div>
  );
};

export default RoutesAndStops;

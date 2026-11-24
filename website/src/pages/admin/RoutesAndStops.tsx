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
} from 'lucide-react';
import { subscribeToRoutes, checkStopHasCoordinates } from '../../services/routesService';
import { Route } from '../../types/admin';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { EmptyState } from '../../components/common/EmptyState';
import { ErrorState } from '../../components/common/ErrorState';

export const RoutesAndStops: React.FC = () => {
  const [routes, setRoutes] = useState<Route[]>([]);
  const [selectedRouteId, setSelectedRouteId] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [filterMode, setFilterMode] = useState<'ALL' | 'MISSING_ONLY' | 'ACTIVE_ONLY'>('ALL');
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [copiedStopId, setCopiedStopId] = useState<string | null>(null);

  useEffect(() => {
    setIsLoading(true);
    setErrorMessage(null);

    const unsubscribe = subscribeToRoutes(
      (data) => {
        setRoutes(data);
        setIsLoading(false);
        // Select first route by default if none selected
        if (!selectedRouteId && data.length > 0) {
          setSelectedRouteId(data[0].routeId);
        }
      },
      (err) => {
        console.error('[RoutesAndStops] Listener error:', err);
        setErrorMessage(err.message || 'Failed to fetch routes from Firestore.');
        setIsLoading(false);
      }
    );

    return () => {
      unsubscribe();
    };
  }, []);

  // Filter routes based on search query and filter mode
  const filteredRoutes = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();

    return routes.filter((route) => {
      // Search matching
      const matchesSearch =
        !q ||
        route.routeId.toLowerCase().includes(q) ||
        route.name.toLowerCase().includes(q) ||
        route.busId.toLowerCase().includes(q) ||
        route.stops.some((s) => s.name.toLowerCase().includes(q) || s.stopId.toLowerCase().includes(q));

      if (!matchesSearch) return false;

      // Filter modes
      if (filterMode === 'ACTIVE_ONLY') {
        return route.active;
      }
      if (filterMode === 'MISSING_ONLY') {
        return route.stops.some((s) => s.active && !checkStopHasCoordinates(s));
      }

      return true;
    });
  }, [routes, searchQuery, filterMode]);

  // Selected route object
  const activeRoute = useMemo(() => {
    return routes.find((r) => r.routeId === selectedRouteId) || filteredRoutes[0] || null;
  }, [routes, selectedRouteId, filteredRoutes]);

  const handleCopy = (stopId: string) => {
    navigator.clipboard.writeText(stopId);
    setCopiedStopId(stopId);
    setTimeout(() => setCopiedStopId(null), 2000);
  };

  // Calculate quick summary metrics for current view
  const summary = useMemo(() => {
    let totalStops = 0;
    let missingCoords = 0;
    routes.forEach((r) => {
      r.stops.forEach((s) => {
        if (s.active) {
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
        <LoadingSpinner label="Connecting to Firestore routes and stop catalogs..." size="lg" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header with Metrics Overview */}
      <div className="bg-white rounded-2xl p-6 border border-slate-200/80 shadow-sm flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-slate-500">
            <RouteIcon className="w-4 h-4 text-indigo-600" />
            <span>Transit Network Catalog</span>
          </div>
          <h2 className="text-xl sm:text-2xl font-extrabold text-slate-900 tracking-tight mt-1">
            Routes & Stop Sequence Explorer
          </h2>
          <p className="text-xs text-slate-500 mt-1 max-w-xl">
            Inspect canonical stops embedded in Firestore route documents. Verify ordered stops, pickup coordinates, and identify unmapped stops.
          </p>
        </div>

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
        {/* Search input */}
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

        {/* Filter mode pill buttons */}
        <div className="flex items-center gap-1.5 w-full sm:w-auto overflow-x-auto pb-1 sm:pb-0">
          <button
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
      {filteredRoutes.length === 0 ? (
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
              <span className="text-[11px] text-slate-400">Click to view stops</span>
            </div>

            <div className="divide-y divide-slate-100 max-h-[600px] overflow-y-auto custom-scrollbar">
              {filteredRoutes.map((route) => {
                const isSelected = activeRoute?.routeId === route.routeId;
                const missingCount = route.stops.filter((s) => s.active && !checkStopHasCoordinates(s)).length;

                return (
                  <button
                    key={route.routeId}
                    onClick={() => setSelectedRouteId(route.routeId)}
                    className={`w-full text-left p-4 transition-all flex items-center justify-between group ${
                      isSelected
                        ? 'bg-blue-50/70 border-l-4 border-blue-600'
                        : 'hover:bg-slate-50/90'
                    }`}
                  >
                    <div className="min-w-0 pr-3">
                      <div className="flex items-center gap-2 mb-1">
                        <span className="text-xs font-bold font-mono text-slate-900 bg-slate-100 px-2 py-0.5 rounded-md">
                          {route.routeId}
                        </span>
                        {route.busId && (
                          <span className="text-[11px] text-slate-600 bg-slate-100 px-2 py-0.5 rounded-md flex items-center gap-1">
                            <Bus className="w-3 h-3 text-slate-500" />
                            <span>{route.busId}</span>
                          </span>
                        )}
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
                        isSelected ? 'text-blue-600 translate-x-1' : 'text-slate-300 group-hover:text-slate-500'
                      }`}
                    />
                  </button>
                );
              })}
            </div>
          </div>

          {/* Right Column: Ordered Stops for Selected Route */}
          <div className="lg:col-span-7 bg-white rounded-2xl border border-slate-200/80 shadow-sm overflow-hidden">
            {activeRoute ? (
              <div>
                {/* Route Header Info Card */}
                <div className="p-5 border-b border-slate-100 bg-slate-50/80">
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <div className="flex items-center gap-2 mb-1.5">
                        <span className="text-xs font-bold font-mono text-blue-700 bg-blue-100 px-2.5 py-1 rounded-lg">
                          {activeRoute.routeId}
                        </span>
                        {activeRoute.busId && (
                          <span className="text-xs font-semibold text-slate-700 bg-white border border-slate-200 px-2.5 py-1 rounded-lg flex items-center gap-1">
                            <Bus className="w-3.5 h-3.5 text-blue-600" />
                            <span>Assigned: {activeRoute.busId}</span>
                          </span>
                        )}
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

                    <div className="text-right flex-shrink-0">
                      <span className="text-xs font-bold text-slate-900 block">
                        {activeRoute.stops.length} Stops
                      </span>
                      <span className="text-[11px] text-slate-500">
                        {activeRoute.stops.filter((s) => checkStopHasCoordinates(s)).length} with coordinates
                      </span>
                    </div>
                  </div>
                </div>

                {/* Stops Table / Ordered List */}
                <div className="overflow-x-auto">
                  <table className="w-full text-left border-collapse">
                    <thead>
                      <tr className="border-b border-slate-200 bg-slate-50/60 text-[11px] font-bold text-slate-500 uppercase tracking-wider">
                        <th className="py-3 px-4 w-12 text-center">#</th>
                        <th className="py-3 px-4">Stop Name & ID</th>
                        <th className="py-3 px-4">Coordinates (Lat, Lng)</th>
                        <th className="py-3 px-4 text-center">Status</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100 text-xs">
                      {activeRoute.stops.length === 0 ? (
                        <tr>
                          <td colSpan={4} className="py-8 text-center text-slate-400">
                            No stops configured for this route document.
                          </td>
                        </tr>
                      ) : (
                        activeRoute.stops.map((stop, idx) => {
                          const hasCoords = checkStopHasCoordinates(stop);

                          return (
                            <tr key={stop.stopId || idx} className="hover:bg-slate-50/70 transition-colors">
                              {/* Order number */}
                              <td className="py-3.5 px-4 text-center font-bold font-mono text-slate-500">
                                {stop.order || idx + 1}
                              </td>

                              {/* Stop Name & ID */}
                              <td className="py-3.5 px-4">
                                <div className="font-bold text-slate-900">{stop.name}</div>
                                <div className="flex items-center gap-1.5 mt-0.5">
                                  <span className="font-mono text-[10px] text-slate-400 bg-slate-100 px-1.5 py-0.5 rounded">
                                    {stop.stopId}
                                  </span>
                                  <button
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
                              <td className="py-3.5 px-4 font-mono text-[11px]">
                                {hasCoords ? (
                                  <span className="text-slate-700">
                                    {stop.latitude.toFixed(6)}, {stop.longitude.toFixed(6)}
                                  </span>
                                ) : (
                                  <span className="text-amber-600 font-semibold italic">
                                    Not Set (0.0, 0.0)
                                  </span>
                                )}
                              </td>

                              {/* Status Badge */}
                              <td className="py-3.5 px-4 text-center">
                                {hasCoords ? (
                                  <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-emerald-700 bg-emerald-50 border border-emerald-200/60 px-2 py-0.5 rounded-full">
                                    <CheckCircle2 className="w-3 h-3" />
                                    <span>Mapped</span>
                                  </span>
                                ) : (
                                  <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-amber-800 bg-amber-50 border border-amber-200/60 px-2 py-0.5 rounded-full">
                                    <AlertTriangle className="w-3 h-3" />
                                    <span>Missing</span>
                                  </span>
                                )}
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
                Select a route from the left panel to inspect its stops sequence.
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

export default RoutesAndStops;

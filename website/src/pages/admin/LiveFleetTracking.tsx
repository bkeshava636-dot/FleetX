import React, { useState, useEffect, useMemo } from 'react';
import {
  Radio,
  Search,
  Bus as BusIcon,
  Navigation,
  Compass,
  Clock,
  AlertCircle,
  Gauge,
  Route as RouteIcon,
  X,
  Battery,
  Users as UsersIcon,
  Cpu,
} from 'lucide-react';
import { subscribeToLiveFleet } from '../../services/trackingService';
import { FleetBus, formatRelativeTime } from '../../types/tracking';
import { Route } from '../../types/admin';
import { FleetMap, getStatusColorHex } from '../../components/maps/FleetMap';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';

export const LiveFleetTracking: React.FC = () => {
  const [buses, setBuses] = useState<FleetBus[]>([]);
  const [routesMap, setRoutesMap] = useState<Map<string, Route>>(new Map());
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Search & Filter state
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [routeFilter, setRouteFilter] = useState<string>('ALL');

  // Selected Bus for Telemetry Inspection & Map Highlighting
  const [selectedBusId, setSelectedBusId] = useState<string | null>(null);

  // 1. Subscribe to Live Fleet RTDB & Firestore Data (Single Parent Listener)
  useEffect(() => {
    setLoading(true);
    const unsubscribe = subscribeToLiveFleet({
      onFleetUpdate: (updatedBuses, updatedRoutesMap) => {
        setBuses(updatedBuses);
        setRoutesMap(updatedRoutesMap);
        setLoading(false);
      },
      onError: (err) => {
        setError(err.message || 'Failed to connect to live tracking stream.');
        setLoading(false);
      },
    });

    return () => {
      unsubscribe();
    };
  }, []);

  // Filtered Buses List
  const filteredBuses = useMemo(() => {
    return buses.filter((bus) => {
      // Search match
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        const matchesQuery =
          bus.busNumber.toLowerCase().includes(q) ||
          bus.registrationNumber.toLowerCase().includes(q) ||
          bus.assignedRouteName?.toLowerCase().includes(q) ||
          bus.assignedDriverName?.toLowerCase().includes(q) ||
          bus.busId.toLowerCase().includes(q);
        if (!matchesQuery) return false;
      }

      // Status match
      if (statusFilter !== 'ALL' && bus.displayState !== statusFilter) {
        return false;
      }

      // Route match
      if (routeFilter !== 'ALL' && bus.assignedRouteId !== routeFilter) {
        return false;
      }

      return true;
    });
  }, [buses, searchQuery, statusFilter, routeFilter]);

  // Operational Counts
  const metrics = useMemo(() => {
    let active = 0;
    let arrived = 0;
    let stale = 0;
    let waiting = 0;
    let inactive = 0;

    buses.forEach((b) => {
      switch (b.displayState) {
        case 'ACTIVE':
          active++;
          break;
        case 'ARRIVED_AT_CAMPUS':
          arrived++;
          break;
        case 'STALE':
          stale++;
          break;
        case 'WAITING_FOR_GPS':
          waiting++;
          break;
        case 'INACTIVE':
        default:
          inactive++;
          break;
      }
    });

    return {
      total: buses.length,
      active,
      arrived,
      stale,
      waiting,
      inactive,
    };
  }, [buses]);

  // Selected Bus Detail
  const selectedBus = useMemo(() => {
    if (!selectedBusId) return null;
    return buses.find((b) => b.busId === selectedBusId) || null;
  }, [buses, selectedBusId]);

  // Selected Route Object
  const selectedRoute = useMemo(() => {
    if (!selectedBus || !selectedBus.assignedRouteId) return null;
    return routesMap.get(selectedBus.assignedRouteId) || null;
  }, [selectedBus, routesMap]);

  // Unique route list for filter dropdown
  const uniqueRoutes = useMemo(() => {
    const list: { id: string; name: string }[] = [];
    routesMap.forEach((r) => {
      list.push({ id: r.routeId, name: r.name });
    });
    list.sort((a, b) => a.name.localeCompare(b.name));
    return list;
  }, [routesMap]);

  return (
    <div className="space-y-5">
      {/* Top Banner & Operational Overview Bar */}
      <div className="bg-white p-5 rounded-3xl border border-slate-200/80 shadow-sm flex flex-col lg:flex-row lg:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2.5">
            <div className="p-2 bg-blue-50 text-blue-600 rounded-2xl flex items-center justify-center">
              <Radio className="w-5 h-5 animate-pulse" />
            </div>
            <div>
              <h1 className="text-xl font-bold text-slate-900 tracking-tight">
                Live Fleet Tracking
              </h1>
              <p className="text-xs text-slate-500">
                Real-time satellite GPS monitoring for college buses via Firebase Realtime Database
              </p>
            </div>
          </div>
        </div>

        {/* Live Status Counter Pills */}
        <div className="flex flex-wrap items-center gap-2 text-xs">
          <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-slate-100 text-slate-700 font-semibold">
            <span className="text-slate-400">Total:</span>
            <span>{metrics.total}</span>
          </div>

          <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-emerald-50 border border-emerald-200/80 text-emerald-800 font-bold">
            <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
            <span>Active: {metrics.active}</span>
          </div>

          <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-violet-50 border border-violet-200/80 text-violet-800 font-bold">
            <span className="w-2 h-2 rounded-full bg-violet-500" />
            <span>Arrived: {metrics.arrived}</span>
          </div>

          <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-amber-50 border border-amber-200/80 text-amber-800 font-bold">
            <span className="w-2 h-2 rounded-full bg-amber-500" />
            <span>Stale: {metrics.stale}</span>
          </div>

          <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-orange-50 border border-orange-200/80 text-orange-800 font-bold">
            <span className="w-2 h-2 rounded-full bg-orange-500" />
            <span>Waiting: {metrics.waiting}</span>
          </div>
        </div>
      </div>

      {error && (
        <div className="p-4 bg-red-50 border border-red-200 text-red-800 text-xs rounded-2xl flex items-center gap-3">
          <AlertCircle className="w-5 h-5 text-red-500 flex-shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Main Full-Height Operational Command Center */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5 items-stretch min-h-[640px]">
        {/* Left Column: Fleet Directory & Filters */}
        <div className="lg:col-span-4 bg-white rounded-3xl border border-slate-200/90 shadow-sm flex flex-col overflow-hidden max-h-[750px]">
          {/* Search & Filters Header */}
          <div className="p-4 border-b border-slate-100 space-y-3 bg-slate-50/50">
            {/* Search Box */}
            <div className="relative">
              <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none" />
              <input
                type="text"
                placeholder="Search bus #, plate, route, or driver..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full pl-9 pr-3 py-2 bg-white border border-slate-200 rounded-xl text-xs text-slate-800 placeholder:text-slate-400 focus:outline-none focus:border-blue-500"
              />
            </div>

            {/* Filter Dropdowns */}
            <div className="grid grid-cols-2 gap-2 text-xs">
              <div>
                <select
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value)}
                  className="w-full bg-white border border-slate-200 rounded-xl px-2.5 py-1.5 text-xs text-slate-700 font-medium focus:outline-none focus:border-blue-500"
                >
                  <option value="ALL">All GPS States</option>
                  <option value="ACTIVE">Active (Live GPS)</option>
                  <option value="ARRIVED_AT_CAMPUS">Arrived at Campus</option>
                  <option value="STALE">Stale (&gt;90s)</option>
                  <option value="WAITING_FOR_GPS">Waiting for Fix</option>
                  <option value="INACTIVE">Inactive</option>
                </select>
              </div>

              <div>
                <select
                  value={routeFilter}
                  onChange={(e) => setRouteFilter(e.target.value)}
                  className="w-full bg-white border border-slate-200 rounded-xl px-2.5 py-1.5 text-xs text-slate-700 font-medium focus:outline-none focus:border-blue-500"
                >
                  <option value="ALL">All Routes</option>
                  {uniqueRoutes.map((r) => (
                    <option key={r.id} value={r.id}>
                      {r.name}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          </div>

          {/* Bus Cards List */}
          <div className="flex-1 overflow-y-auto p-3 space-y-2">
            {loading ? (
              <div className="py-12 text-center">
                <LoadingSpinner label="Streaming fleet telemetry..." />
              </div>
            ) : filteredBuses.length === 0 ? (
              <div className="py-12 text-center text-xs text-slate-400">
                <BusIcon className="w-8 h-8 text-slate-300 mx-auto mb-2" />
                <p className="font-semibold text-slate-600">No vehicles match filters</p>
                <p className="mt-1">Try resetting the search or filter options.</p>
              </div>
            ) : (
              filteredBuses.map((bus) => {
                const isSelected = bus.busId === selectedBusId;
                const statusColor = getStatusColorHex(bus.displayState);

                return (
                  <div
                    key={bus.busId}
                    onClick={() => setSelectedBusId(bus.busId)}
                    className={`p-3.5 rounded-2xl border cursor-pointer transition text-xs space-y-2 ${
                      isSelected
                        ? 'bg-blue-50/40 border-blue-500 ring-2 ring-blue-500/20'
                        : 'bg-white border-slate-200/80 hover:border-slate-300 hover:bg-slate-50/60'
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span
                          className="w-2.5 h-2.5 rounded-full flex-shrink-0"
                          style={{ backgroundColor: statusColor }}
                        />
                        <span className="font-bold text-slate-900">{bus.busNumber}</span>
                      </div>
                      <span
                        className="text-[10px] font-bold px-2 py-0.5 rounded-full tracking-wider uppercase"
                        style={{
                          backgroundColor: `${statusColor}15`,
                          color: statusColor,
                        }}
                      >
                        {bus.displayState.replace(/_/g, ' ')}
                      </span>
                    </div>

                    <div className="flex items-center justify-between text-slate-500 text-[11px]">
                      <span className="truncate max-w-[170px]">{bus.assignedRouteName}</span>
                      <span className="font-mono text-slate-400">{bus.registrationNumber}</span>
                    </div>

                    <div className="flex items-center justify-between text-[11px] pt-1.5 border-t border-slate-100 text-slate-600">
                      <div className="flex items-center gap-2">
                        <span className="font-semibold">{bus.speedKmh} km/h</span>
                        {bus.bearingDegrees > 0 && (
                          <span className="text-[10px] text-slate-400 font-mono">
                            {bus.compassDirection} ({bus.bearingDegrees}°)
                          </span>
                        )}
                      </div>
                      <span className="text-[10px] text-slate-400">
                        {formatRelativeTime(bus.lastUpdatedMs)}
                      </span>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>

        {/* Right Column: Google Maps & Telemetry Inspection Panel */}
        <div className="lg:col-span-8 flex flex-col gap-4">
          {/* Google Map Operational View */}
          <div className="flex-1 min-h-[480px]">
            <FleetMap
              buses={buses}
              selectedBusId={selectedBusId}
              onSelectBus={(id) => setSelectedBusId(id)}
              selectedRoute={selectedRoute}
              className="h-full"
            />
          </div>

          {/* Telemetry Inspection Drawer (Visible when a bus is selected) */}
          {selectedBus ? (
            <div className="bg-white rounded-3xl border border-slate-200/90 shadow-sm p-5 space-y-4 animate-in fade-in slide-in-from-bottom-3 duration-200">
              <div className="flex items-start justify-between border-b border-slate-100 pb-3">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center font-bold">
                    <BusIcon className="w-5 h-5" />
                  </div>
                  <div>
                    <div className="flex items-center gap-2">
                      <h3 className="text-base font-bold text-slate-900">{selectedBus.busNumber}</h3>
                      <span
                        className="text-[10px] font-bold px-2 py-0.5 rounded-full uppercase"
                        style={{
                          backgroundColor: `${getStatusColorHex(selectedBus.displayState)}15`,
                          color: getStatusColorHex(selectedBus.displayState),
                        }}
                      >
                        {selectedBus.displayState.replace(/_/g, ' ')}
                      </span>
                    </div>
                    <span className="text-xs text-slate-400 font-mono">
                      Registration: {selectedBus.registrationNumber} • Bus ID: {selectedBus.busId}
                    </span>
                  </div>
                </div>

                <button
                  type="button"
                  onClick={() => setSelectedBusId(null)}
                  className="p-1.5 text-slate-400 hover:text-slate-600 rounded-lg hover:bg-slate-100 transition"
                  title="Close Inspector"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>

              {/* Real-time Telemetry Metrics Grid */}
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
                {/* Speed */}
                <div className="p-3 bg-slate-50 rounded-2xl border border-slate-100 space-y-1">
                  <span className="text-[10px] text-slate-400 flex items-center gap-1 font-semibold">
                    <Gauge className="w-3.5 h-3.5 text-blue-600" />
                    <span>Speed</span>
                  </span>
                  <div className="text-sm font-bold text-slate-900">
                    {selectedBus.speedKmh} <span className="text-[10px] font-normal text-slate-500">km/h</span>
                  </div>
                  <span className="text-[10px] text-slate-400 font-mono block">
                    {selectedBus.speedMps.toFixed(1)} m/s
                  </span>
                </div>

                {/* Heading / Compass */}
                <div className="p-3 bg-slate-50 rounded-2xl border border-slate-100 space-y-1">
                  <span className="text-[10px] text-slate-400 flex items-center gap-1 font-semibold">
                    <Compass className="w-3.5 h-3.5 text-blue-600" />
                    <span>Heading</span>
                  </span>
                  <div className="text-sm font-bold text-slate-900">
                    {selectedBus.compassDirection}
                  </div>
                  <span className="text-[10px] text-slate-400 font-mono block">
                    {selectedBus.bearingDegrees}° Azimuth
                  </span>
                </div>

                {/* Assigned Route */}
                <div className="p-3 bg-slate-50 rounded-2xl border border-slate-100 space-y-1">
                  <span className="text-[10px] text-slate-400 flex items-center gap-1 font-semibold">
                    <RouteIcon className="w-3.5 h-3.5 text-blue-600" />
                    <span>Assigned Route</span>
                  </span>
                  <div className="text-xs font-bold text-slate-900 truncate">
                    {selectedBus.assignedRouteName}
                  </div>
                  <span className="text-[10px] text-slate-400 font-mono block">
                    {selectedRoute ? `${selectedRoute.stops.length} stops` : 'No stops data'}
                  </span>
                </div>

                {/* Last GPS Heartbeat */}
                <div className="p-3 bg-slate-50 rounded-2xl border border-slate-100 space-y-1">
                  <span className="text-[10px] text-slate-400 flex items-center gap-1 font-semibold">
                    <Clock className="w-3.5 h-3.5 text-blue-600" />
                    <span>GPS Signal</span>
                  </span>
                  <div className="text-xs font-bold text-slate-900">
                    {formatRelativeTime(selectedBus.lastUpdatedMs)}
                  </div>
                  <span className="text-[10px] text-slate-400 font-mono block">
                    {selectedBus.lastUpdatedMs > 0
                      ? new Date(selectedBus.lastUpdatedMs).toLocaleTimeString()
                      : 'Never'}
                  </span>
                </div>
              </div>

              {/* Coordinates & Metadata Row */}
              <div className="p-3 bg-slate-900 text-white rounded-2xl flex flex-wrap items-center justify-between gap-3 text-xs font-mono">
                <div>
                  <span className="text-slate-400 block text-[10px]">CURRENT COORDINATES</span>
                  {selectedBus.telemetry && selectedBus.telemetry.latitude !== 0 ? (
                    <span className="font-bold text-emerald-400">
                      {selectedBus.telemetry.latitude.toFixed(5)}, {selectedBus.telemetry.longitude.toFixed(5)}
                    </span>
                  ) : (
                    <span className="text-amber-400">No active GPS coordinates</span>
                  )}
                </div>

                <div>
                  <span className="text-slate-400 block text-[10px]">DRIVER LINK</span>
                  <span className="text-slate-200">
                    {selectedBus.assignedDriverName || 'No permanent driver linked'}
                  </span>
                </div>

                <div>
                  <span className="text-slate-400 block text-[10px]">SOURCE & MODE</span>
                  <span className="text-blue-400">
                    {selectedBus.telemetry?.sourceType || 'PRIMARY_DRIVER'} ({selectedBus.telemetry?.mode || 'AUTO'})
                  </span>
                </div>
              </div>

              {/* Clearly Identified Unavailable Hardware Telemetry */}
              <div className="pt-2 border-t border-slate-100">
                <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block mb-2">
                  Hardware Sensor Diagnostics (Unsupported by Mobile Driver App)
                </span>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5 text-xs text-slate-500">
                  <div className="p-2.5 bg-slate-50 rounded-xl flex items-center gap-2">
                    <Battery className="w-4 h-4 text-slate-400" />
                    <div>
                      <span className="block font-medium text-slate-700">Battery Level</span>
                      <span className="text-[10px] text-slate-400">Unavailable (Not transmitted)</span>
                    </div>
                  </div>

                  <div className="p-2.5 bg-slate-50 rounded-xl flex items-center gap-2">
                    <UsersIcon className="w-4 h-4 text-slate-400" />
                    <div>
                      <span className="block font-medium text-slate-700">Passenger Count</span>
                      <span className="text-[10px] text-slate-400">Unavailable (No sensor)</span>
                    </div>
                  </div>

                  <div className="p-2.5 bg-slate-50 rounded-xl flex items-center gap-2">
                    <Cpu className="w-4 h-4 text-slate-400" />
                    <div>
                      <span className="block font-medium text-slate-700">Engine / CAN-Bus</span>
                      <span className="text-[10px] text-slate-400">Unavailable (GPS phone only)</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          ) : (
            <div className="p-4 bg-white rounded-2xl border border-slate-200/80 text-xs text-slate-500 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Navigation className="w-4 h-4 text-blue-600" />
                <span>Click any vehicle marker or sidebar card to view live telemetry and route overlay.</span>
              </div>
              <span className="text-[11px] text-slate-400">BITM Campus, Ballari</span>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default LiveFleetTracking;

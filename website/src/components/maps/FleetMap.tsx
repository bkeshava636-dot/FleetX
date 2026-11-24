import React, { useCallback, useMemo, useState, useEffect } from 'react';
import {
  GoogleMap,
  useJsApiLoader,
  MarkerF,
  PolylineF,
  InfoWindowF,
} from '@react-google-maps/api';
import {
  AlertCircle,
  Maximize2,
  Info,
  Route as RouteIcon,
} from 'lucide-react';
import { FleetBus, GpsDisplayState, DEFAULT_CAMPUS_LOCATION } from '../../types/tracking';
import { Route, BusStop } from '../../types/admin';
import { getRouteStopsPolylinePoints } from '../../services/trackingService';

interface FleetMapProps {
  buses: FleetBus[];
  selectedBusId: string | null;
  onSelectBus: (busId: string) => void;
  selectedRoute?: Route | null;
  className?: string;
}

const mapContainerStyle: React.CSSProperties = {
  width: '100%',
  height: '100%',
};

const mapOptions: google.maps.MapOptions = {
  disableDefaultUI: false,
  zoomControl: true,
  streetViewControl: false,
  mapTypeControl: true,
  fullscreenControl: true,
  gestureHandling: 'greedy',
};

const LIBRARIES: ('places' | 'geometry')[] = ['geometry'];

// Bus SVG Path (Directional arrow heading forward at 0 degrees / North)
const BUS_MARKER_SVG_PATH =
  'M12 2L4.5 20.29l.71.71L12 18l6.79 3 .71-.71z';

// Status Color Lookup
export function getStatusColorHex(state: GpsDisplayState): string {
  switch (state) {
    case 'ACTIVE':
      return '#10b981'; // Emerald
    case 'ARRIVED_AT_CAMPUS':
      return '#8b5cf6'; // Violet
    case 'WAITING_FOR_GPS':
      return '#f97316'; // Orange
    case 'STALE':
      return '#f59e0b'; // Amber
    case 'INACTIVE':
    default:
      return '#64748b'; // Slate
  }
}

export const FleetMap: React.FC<FleetMapProps> = ({
  buses,
  selectedBusId,
  onSelectBus,
  selectedRoute,
  className = '',
}) => {
  const apiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY || '';

  const { isLoaded, loadError } = useJsApiLoader({
    id: 'fleetx-google-maps-script',
    googleMapsApiKey: apiKey,
    libraries: LIBRARIES,
  });

  const [map, setMap] = useState<google.maps.Map | null>(null);
  const [activeStopTooltip, setActiveStopTooltip] = useState<BusStop | null>(null);

  // College Campus Coordinates fallback (BITM Ballari or env override)
  const campusCenter = useMemo(() => {
    const envLat = parseFloat(import.meta.env.VITE_COLLEGE_CAMPUS_LAT);
    const envLng = parseFloat(import.meta.env.VITE_COLLEGE_CAMPUS_LNG);
    if (!isNaN(envLat) && !isNaN(envLng)) {
      return { lat: envLat, lng: envLng };
    }
    return { lat: DEFAULT_CAMPUS_LOCATION.lat, lng: DEFAULT_CAMPUS_LOCATION.lng };
  }, []);

  // Selected Bus Object
  const selectedBus = useMemo(() => {
    return buses.find((b) => b.busId === selectedBusId) || null;
  }, [buses, selectedBusId]);

  // Transmitting buses with valid non-zero coordinates
  const transmittingBuses = useMemo(() => {
    return buses.filter(
      (b) =>
        b.telemetry &&
        b.telemetry.latitude !== 0 &&
        b.telemetry.longitude !== 0 &&
        !isNaN(b.telemetry.latitude) &&
        !isNaN(b.telemetry.longitude)
    );
  }, [buses]);

  // Route path points & stops
  const { path: routePath, stops: routeStops } = useMemo(() => {
    return getRouteStopsPolylinePoints(selectedRoute);
  }, [selectedRoute]);

  // Auto-pan to selected bus when explicitly selected
  useEffect(() => {
    if (map && selectedBus && selectedBus.telemetry) {
      const lat = selectedBus.telemetry.latitude;
      const lng = selectedBus.telemetry.longitude;
      if (lat !== 0 && lng !== 0) {
        map.panTo({ lat, lng });
        if (map.getZoom()! < 14) {
          map.setZoom(15);
        }
      }
    }
  }, [selectedBusId, map]);

  // Fit all fleet bounds
  const handleFitAllFleet = useCallback(() => {
    if (!map) return;
    const bounds = new google.maps.LatLngBounds();
    bounds.extend(campusCenter);

    let hasPoints = false;
    transmittingBuses.forEach((b) => {
      if (b.telemetry) {
        bounds.extend({ lat: b.telemetry.latitude, lng: b.telemetry.longitude });
        hasPoints = true;
      }
    });

    if (hasPoints) {
      map.fitBounds(bounds, { top: 60, bottom: 60, left: 60, right: 60 });
    } else {
      map.panTo(campusCenter);
      map.setZoom(13);
    }
  }, [map, campusCenter, transmittingBuses]);

  const onLoad = useCallback((m: google.maps.Map) => {
    setMap(m);
  }, []);

  const onUnmount = useCallback(() => {
    setMap(null);
  }, []);

  // Fallback 1: Missing API Key
  if (!apiKey) {
    return (
      <div className={`w-full h-full min-h-[500px] bg-slate-900 border border-slate-800 rounded-3xl p-6 flex flex-col items-center justify-center text-center relative overflow-hidden ${className}`}>
        <div className="p-4 bg-amber-500/10 text-amber-400 border border-amber-500/20 rounded-2xl mb-3">
          <AlertCircle className="w-8 h-8" />
        </div>
        <h3 className="text-base font-bold text-white">Google Maps Platform Inactive</h3>
        <p className="text-xs text-slate-400 max-w-md mt-1.5 leading-relaxed">
          <code className="text-amber-300 font-mono text-[11px] bg-slate-800 px-2 py-0.5 rounded">
            VITE_GOOGLE_MAPS_API_KEY
          </code>{' '}
          is not configured in this environment. Live telemetry streaming from Firebase Realtime Database continues below.
        </p>

        {/* Live Telemetry Stream Cards Fallback */}
        <div className="mt-6 w-full max-w-xl grid grid-cols-1 sm:grid-cols-2 gap-3 text-left">
          {buses.slice(0, 6).map((bus) => (
            <div
              key={bus.busId}
              onClick={() => onSelectBus(bus.busId)}
              className={`p-3.5 rounded-xl border cursor-pointer transition ${
                bus.busId === selectedBusId
                  ? 'bg-blue-950/60 border-blue-500 text-white'
                  : 'bg-slate-800/80 border-slate-700/80 text-slate-300 hover:bg-slate-800'
              }`}
            >
              <div className="flex items-center justify-between text-xs">
                <span className="font-bold text-white">{bus.busNumber}</span>
                <span
                  className="w-2.5 h-2.5 rounded-full"
                  style={{ backgroundColor: getStatusColorHex(bus.displayState) }}
                />
              </div>
              <div className="text-[11px] text-slate-400 mt-1 font-mono">
                {bus.telemetry
                  ? `${bus.telemetry.latitude.toFixed(4)}, ${bus.telemetry.longitude.toFixed(4)}`
                  : 'No coordinates'}
              </div>
              <div className="flex items-center justify-between text-[10px] text-slate-400 mt-2 pt-1 border-t border-slate-700/40">
                <span>{bus.speedKmh} km/h</span>
                <span>{bus.compassDirection} ({bus.bearingDegrees}°)</span>
              </div>
            </div>
          ))}
        </div>
      </div>
    );
  }

  // Fallback 2: Map Load Error
  if (loadError) {
    return (
      <div className={`w-full h-full min-h-[500px] bg-red-950/20 border border-red-500/20 rounded-3xl p-6 flex flex-col items-center justify-center text-center ${className}`}>
        <AlertCircle className="w-8 h-8 text-red-400 mb-2" />
        <h4 className="text-sm font-semibold text-red-200">Google Maps Failed to Load</h4>
        <p className="text-xs text-red-400/80 mt-1 max-w-sm">
          {loadError.message || 'Please check your internet connection or Google Maps Platform API key restrictions.'}
        </p>
      </div>
    );
  }

  // Fallback 3: Loading
  if (!isLoaded) {
    return (
      <div className={`w-full h-full min-h-[500px] bg-slate-900 border border-slate-800 rounded-3xl flex flex-col items-center justify-center text-center p-6 ${className}`}>
        <div className="w-10 h-10 border-3 border-blue-500/20 border-t-blue-500 rounded-full animate-spin mb-3" />
        <span className="text-xs font-medium text-slate-400">Loading FleetX Live Tracking Map...</span>
      </div>
    );
  }

  return (
    <div className={`w-full h-full relative rounded-3xl overflow-hidden border border-slate-800 shadow-2xl ${className}`}>
      <GoogleMap
        mapContainerStyle={mapContainerStyle}
        center={campusCenter}
        zoom={13}
        options={mapOptions}
        onLoad={onLoad}
        onUnmount={onUnmount}
      >
        {/* BITM College Campus Marker (Base Anchor) */}
        <MarkerF
          position={campusCenter}
          title={DEFAULT_CAMPUS_LOCATION.name}
          label={{
            text: 'Campus',
            className: 'bg-indigo-900 text-indigo-100 text-[10px] font-bold px-2 py-0.5 rounded shadow-md border border-indigo-500/40',
          }}
          icon={{
            path: google.maps.SymbolPath.CIRCLE,
            scale: 7,
            fillColor: '#4f46e5',
            fillOpacity: 1,
            strokeWeight: 2,
            strokeColor: '#ffffff',
          }}
        />

        {/* Selected Route Polyline Overlay (Approximate Stop-to-Stop Sequence) */}
        {routePath.length > 1 && (
          <PolylineF
            path={routePath}
            options={{
              strokeColor: '#3b82f6',
              strokeOpacity: 0.85,
              strokeWeight: 4,
              geodesic: true,
            }}
          />
        )}

        {/* Ordered Stop Markers Along Selected Route */}
        {routeStops.map((stop) => (
          <MarkerF
            key={stop.stopId}
            position={{ lat: stop.latitude, lng: stop.longitude }}
            title={`Stop #${stop.order}: ${stop.name}`}
            onClick={() => setActiveStopTooltip(stop)}
            label={{
              text: String(stop.order),
              color: '#ffffff',
              fontSize: '10px',
              fontWeight: 'bold',
            }}
            icon={{
              path: google.maps.SymbolPath.CIRCLE,
              scale: 10,
              fillColor: '#1e293b',
              fillOpacity: 1,
              strokeWeight: 2,
              strokeColor: '#38bdf8',
            }}
          />
        ))}

        {/* Active Stop Tooltip */}
        {activeStopTooltip && (
          <InfoWindowF
            position={{ lat: activeStopTooltip.latitude, lng: activeStopTooltip.longitude }}
            onCloseClick={() => setActiveStopTooltip(null)}
          >
            <div className="p-1 text-slate-900 text-xs max-w-xs">
              <span className="text-[10px] text-blue-600 font-mono font-bold block">
                STOP #{activeStopTooltip.order}
              </span>
              <strong className="text-xs font-bold block">{activeStopTooltip.name}</strong>
              <span className="text-[10px] text-slate-500 font-mono">
                {activeStopTooltip.latitude.toFixed(5)}, {activeStopTooltip.longitude.toFixed(5)}
              </span>
            </div>
          </InfoWindowF>
        )}

        {/* Live Fleet Bus Directional Markers */}
        {transmittingBuses.map((bus) => {
          if (!bus.telemetry) return null;
          const isSelected = bus.busId === selectedBusId;
          const color = getStatusColorHex(bus.displayState);

          return (
            <MarkerF
              key={bus.busId}
              position={{ lat: bus.telemetry.latitude, lng: bus.telemetry.longitude }}
              title={`${bus.busNumber} (${bus.speedKmh} km/h - ${bus.displayState})`}
              onClick={() => onSelectBus(bus.busId)}
              label={{
                text: bus.busNumber.replace(/bus\s*/i, ''),
                color: '#ffffff',
                fontSize: '10px',
                fontWeight: 'bold',
                className: isSelected ? 'ring-2 ring-white rounded' : '',
              }}
              icon={{
                path: BUS_MARKER_SVG_PATH,
                scale: isSelected ? 1.6 : 1.3,
                rotation: bus.bearingDegrees || 0,
                fillColor: color,
                fillOpacity: 1,
                strokeWeight: isSelected ? 2.5 : 1.5,
                strokeColor: '#ffffff',
                anchor: new google.maps.Point(12, 12),
              }}
            />
          );
        })}
      </GoogleMap>

      {/* Floating Map Controls & Overlays */}
      <div className="absolute top-4 right-4 z-10 flex flex-col gap-2">
        <button
          type="button"
          onClick={handleFitAllFleet}
          className="bg-slate-900/90 hover:bg-slate-800 text-slate-200 hover:text-white border border-slate-700/80 px-3 py-2 rounded-xl text-xs font-semibold shadow-lg backdrop-blur-md flex items-center gap-1.5 transition"
        >
          <Maximize2 className="w-3.5 h-3.5 text-blue-400" />
          <span>Fit All Fleet</span>
        </button>
      </div>

      {/* Route Disclaimer Banner Overlay */}
      {selectedRoute && routePath.length > 1 && (
        <div className="absolute top-4 left-4 z-10 bg-slate-900/90 backdrop-blur-md border border-slate-700/80 rounded-xl p-3 shadow-xl max-w-sm text-xs space-y-1">
          <div className="flex items-center gap-2">
            <RouteIcon className="w-4 h-4 text-blue-400" />
            <strong className="text-white font-semibold truncate">{selectedRoute.name}</strong>
          </div>
          <div className="flex items-start gap-1.5 text-[10px] text-slate-400 leading-tight">
            <Info className="w-3 h-3 text-blue-400 flex-shrink-0 mt-0.5" />
            <span>Approximate stop-to-stop sequence (straight-line visualization, not road-snapped).</span>
          </div>
        </div>
      )}

      {/* Map Legend Overlay */}
      <div className="absolute bottom-4 left-4 z-10 bg-slate-900/90 backdrop-blur-md border border-slate-700/80 rounded-xl px-3.5 py-2 shadow-xl flex flex-wrap items-center gap-3 text-[11px] text-slate-300">
        <div className="flex items-center gap-1.5">
          <div className="w-2.5 h-2.5 rounded-full bg-emerald-500 shadow-sm" />
          <span>Active</span>
        </div>
        <div className="flex items-center gap-1.5">
          <div className="w-2.5 h-2.5 rounded-full bg-violet-500 shadow-sm" />
          <span>Arrived</span>
        </div>
        <div className="flex items-center gap-1.5">
          <div className="w-2.5 h-2.5 rounded-full bg-amber-500 shadow-sm" />
          <span>Stale (&gt;90s)</span>
        </div>
        <div className="flex items-center gap-1.5">
          <div className="w-2.5 h-2.5 rounded-full bg-orange-500 shadow-sm" />
          <span>Waiting GPS</span>
        </div>
      </div>
    </div>
  );
};

export default FleetMap;

import React, { useCallback, useMemo, useState, useEffect } from 'react';
import { GoogleMap, useJsApiLoader, MarkerF, CircleF } from '@react-google-maps/api';
import { AlertCircle } from 'lucide-react';

export interface LatLng {
  lat: number;
  lng: number;
}

interface GoogleMapPickerProps {
  proposedPosition: LatLng | null;
  existingPosition?: LatLng | null;
  existingStopName?: string;
  accuracyMeters?: number | null;
  onPositionChange?: (pos: LatLng) => void;
  draggable?: boolean;
  interactive?: boolean;
  height?: string;
  className?: string;
}

// Default center: BITM Ballari campus coordinates
const DEFAULT_CENTER: LatLng = { lat: 15.1683, lng: 76.8317 };

const mapContainerStyle: React.CSSProperties = {
  width: '100%',
  height: '100%',
  borderRadius: '1rem',
};

const defaultOptions: google.maps.MapOptions = {
  disableDefaultUI: false,
  zoomControl: true,
  streetViewControl: false,
  mapTypeControl: true,
  fullscreenControl: true,
  gestureHandling: 'greedy',
};

const LIBRARIES: ('places' | 'geometry')[] = ['geometry'];

export const GoogleMapPicker: React.FC<GoogleMapPickerProps> = ({
  proposedPosition,
  existingPosition,
  existingStopName,
  accuracyMeters,
  onPositionChange,
  draggable = true,
  interactive = true,
  height = '420px',
  className = '',
}) => {
  const apiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY || '';

  const { isLoaded, loadError } = useJsApiLoader({
    id: 'fleetx-google-maps-script',
    googleMapsApiKey: apiKey,
    libraries: LIBRARIES,
  });

  const [map, setMap] = useState<google.maps.Map | null>(null);

  // Compute map center
  const center = useMemo<LatLng>(() => {
    if (proposedPosition && proposedPosition.lat !== 0 && proposedPosition.lng !== 0) {
      return proposedPosition;
    }
    if (existingPosition && existingPosition.lat !== 0 && existingPosition.lng !== 0) {
      return existingPosition;
    }
    return DEFAULT_CENTER;
  }, [proposedPosition, existingPosition]);

  // Pan to center when proposed position or center changes
  useEffect(() => {
    if (map && center) {
      map.panTo(center);
    }
  }, [map, center]);

  const onLoad = useCallback((m: google.maps.Map) => {
    setMap(m);
  }, []);

  const onUnmount = useCallback(() => {
    setMap(null);
  }, []);

  const handleMapClick = useCallback(
    (e: google.maps.MapMouseEvent) => {
      if (!interactive || !onPositionChange || !e.latLng) return;
      onPositionChange({
        lat: Number(e.latLng.lat().toFixed(6)),
        lng: Number(e.latLng.lng().toFixed(6)),
      });
    },
    [interactive, onPositionChange]
  );

  const handleMarkerDragEnd = useCallback(
    (e: google.maps.MapMouseEvent) => {
      if (!onPositionChange || !e.latLng) return;
      onPositionChange({
        lat: Number(e.latLng.lat().toFixed(6)),
        lng: Number(e.latLng.lng().toFixed(6)),
      });
    },
    [onPositionChange]
  );

  // Fallback 1: Missing API Key
  if (!apiKey) {
    return (
      <div
        style={{ height }}
        className={`w-full bg-slate-900 border border-slate-700/60 rounded-2xl p-6 flex flex-col items-center justify-center text-center relative overflow-hidden ${className}`}
      >
        <div className="absolute inset-0 bg-gradient-to-b from-blue-950/20 to-slate-900/80 pointer-events-none" />
        <div className="p-3 bg-amber-500/10 text-amber-400 rounded-2xl mb-3 border border-amber-500/20">
          <AlertCircle className="w-6 h-6" />
        </div>
        <h4 className="text-sm font-bold text-slate-200">Google Maps Platform Notice</h4>
        <p className="text-xs text-slate-400 max-w-md mt-1.5 leading-relaxed">
          <code className="text-amber-300 font-mono text-[11px] bg-slate-800 px-1.5 py-0.5 rounded">
            VITE_GOOGLE_MAPS_API_KEY
          </code>{' '}
          is not configured in this environment. Coordinate selection via GPS or manual coordinate entry remains fully active.
        </p>

        {proposedPosition && (
          <div className="mt-4 p-3 bg-slate-800/80 border border-slate-700 rounded-xl text-left w-full max-w-sm">
            <div className="text-[11px] font-mono text-blue-400 flex items-center justify-between">
              <span>Proposed Pin</span>
              <span className="font-bold">{proposedPosition.lat.toFixed(6)}, {proposedPosition.lng.toFixed(6)}</span>
            </div>
            {accuracyMeters && (
              <div className="text-[10px] text-slate-400 mt-1">Accuracy: ±{accuracyMeters}m</div>
            )}
          </div>
        )}
      </div>
    );
  }

  // Fallback 2: Map Load Error
  if (loadError) {
    return (
      <div
        style={{ height }}
        className={`w-full bg-red-950/20 border border-red-500/20 rounded-2xl p-6 flex flex-col items-center justify-center text-center ${className}`}
      >
        <AlertCircle className="w-8 h-8 text-red-400 mb-2" />
        <p className="text-sm font-semibold text-red-200">Google Maps Failed to Load</p>
        <p className="text-xs text-red-400/80 mt-1 max-w-sm">
          {loadError.message || 'Please check your internet connection or Google Maps Platform API key restrictions.'}
        </p>
      </div>
    );
  }

  // Fallback 3: Loading
  if (!isLoaded) {
    return (
      <div
        style={{ height }}
        className={`w-full bg-slate-900 border border-slate-800 rounded-2xl flex flex-col items-center justify-center text-center p-6 ${className}`}
      >
        <div className="w-8 h-8 border-2 border-blue-500/20 border-t-blue-500 rounded-full animate-spin mb-3" />
        <span className="text-xs font-medium text-slate-400">Loading Google Maps Platform...</span>
      </div>
    );
  }

  return (
    <div style={{ height }} className={`w-full relative rounded-2xl overflow-hidden shadow-inner border border-slate-200 ${className}`}>
      <GoogleMap
        mapContainerStyle={mapContainerStyle}
        center={center}
        zoom={proposedPosition ? 16 : 14}
        options={defaultOptions}
        onLoad={onLoad}
        onUnmount={onUnmount}
        onClick={handleMapClick}
      >
        {/* Existing Stop Location Marker (Comparison) */}
        {existingPosition && existingPosition.lat !== 0 && existingPosition.lng !== 0 && (
          <MarkerF
            position={existingPosition}
            title={existingStopName ? `Existing: ${existingStopName}` : 'Existing Stop Location'}
            label={{
              text: 'Current',
              className: 'bg-slate-800 text-white text-[10px] font-bold px-1.5 py-0.5 rounded shadow',
            }}
            icon={{
              path: google.maps.SymbolPath.BACKWARD_CLOSED_ARROW,
              scale: 5,
              fillColor: '#64748b',
              fillOpacity: 1,
              strokeWeight: 2,
              strokeColor: '#ffffff',
            }}
          />
        )}

        {/* Proposed Stop Location Marker (Draggable) */}
        {proposedPosition && proposedPosition.lat !== 0 && proposedPosition.lng !== 0 && (
          <MarkerF
            position={proposedPosition}
            draggable={draggable}
            onDragEnd={handleMarkerDragEnd}
            title="Proposed Stop Boarding Point (Drag to fine-tune)"
            animation={google.maps.Animation.DROP}
            icon={{
              path: google.maps.SymbolPath.CIRCLE,
              scale: 8,
              fillColor: '#2563eb',
              fillOpacity: 1,
              strokeWeight: 3,
              strokeColor: '#ffffff',
            }}
          />
        )}

        {/* Accuracy Circle around Proposed GPS Position */}
        {proposedPosition && accuracyMeters && accuracyMeters > 0 && (
          <CircleF
            center={proposedPosition}
            radius={accuracyMeters}
            options={{
              strokeColor: '#3b82f6',
              strokeOpacity: 0.6,
              strokeWeight: 1.5,
              fillColor: '#60a5fa',
              fillOpacity: 0.15,
              clickable: false,
            }}
          />
        )}
      </GoogleMap>

      {/* Map Legend Overlay (offset above Google watermark) */}
      <div className="absolute bottom-8 left-3 bg-white/95 backdrop-blur-sm border border-slate-200 rounded-xl px-3 py-2 shadow-md flex items-center gap-4 text-[11px] text-slate-600 select-none pointer-events-none">
        <div className="flex items-center gap-1.5">
          <div className="w-3 h-3 rounded-full bg-blue-600 border border-white shadow-sm" />
          <span className="font-semibold text-slate-800">Proposed Stop</span>
        </div>
        {existingPosition && existingPosition.lat !== 0 && (
          <div className="flex items-center gap-1.5">
            <div className="w-3 h-3 rounded-full bg-slate-500 border border-white shadow-sm" />
            <span>Current Stop</span>
          </div>
        )}
        {interactive && (
          <span className="text-[10px] text-slate-400 hidden sm:inline">
            Click map or drag pin to position
          </span>
        )}
      </div>
    </div>
  );
};

export default GoogleMapPicker;

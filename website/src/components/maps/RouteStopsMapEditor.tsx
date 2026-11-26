import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  GoogleMap,
  MarkerF,
  PolylineF,
  useJsApiLoader,
} from '@react-google-maps/api';
import {
  X,
  Save,
  MapPin,
  AlertCircle,
  AlertTriangle,
  Bell,
  Trash2,
  Loader2,
  MousePointerClick,
} from 'lucide-react';
import { BusStop, Route } from '../../types/admin';
import {
  checkStopHasCoordinates,
  generateNextStopId,
  reindexStops,
  saveRouteStops,
  validateStopInput,
} from '../../services/routesService';

interface RouteStopsMapEditorProps {
  isOpen: boolean;
  route: Route;
  onClose: () => void;
  onSuccess: (message: string) => void;
}

const DEFAULT_CENTER = { lat: 12.9716, lng: 77.5946 };
const LIBRARIES: ('places' | 'geometry')[] = ['geometry'];

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

function normalizeValidationResult(
  result: string | null | { valid: boolean; error?: string | null }
): string | null {
  if (typeof result === 'string') return result;
  if (result && typeof result === 'object' && 'valid' in result) {
    return result.valid ? null : result.error || 'Invalid stop input.';
  }
  return null;
}

export const RouteStopsMapEditor: React.FC<RouteStopsMapEditorProps> = ({
  isOpen,
  route,
  onClose,
  onSuccess,
}) => {
  const apiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY || '';

  const { isLoaded, loadError } = useJsApiLoader({
    id: 'fleetx-google-maps-script',
    googleMapsApiKey: apiKey,
    libraries: LIBRARIES,
  });

  const [map, setMap] = useState<google.maps.Map | null>(null);
  const [draftStops, setDraftStops] = useState<BusStop[]>([]);
  const [selectedStopId, setSelectedStopId] = useState<string | null>(null);
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  // Initialize draft stops strictly as a copy without altering coordinates
  useEffect(() => {
    if (!isOpen) return;
    const cloned = route.stops.map((s) => ({ ...s }));
    setDraftStops(cloned);
    setSelectedStopId(cloned[0]?.stopId ?? null);
    setHasUnsavedChanges(false);
    setSaveError(null);
  }, [isOpen, route]);

  const mappedStops = useMemo(
    () => draftStops.filter((s) => checkStopHasCoordinates(s)),
    [draftStops]
  );

  const polylinePath = useMemo(
    () =>
      mappedStops.map((s) => ({
        lat: s.latitude,
        lng: s.longitude,
      })),
    [mappedStops]
  );

  const selectedStop = useMemo(
    () => draftStops.find((s) => s.stopId === selectedStopId) || null,
    [draftStops, selectedStopId]
  );

  // Fit bounds once when map loads
  const onMapLoad = useCallback(
    (m: google.maps.Map) => {
      setMap(m);
      if (mappedStops.length === 1) {
        m.setCenter({ lat: mappedStops[0].latitude, lng: mappedStops[0].longitude });
        m.setZoom(15);
      } else if (mappedStops.length > 1 && typeof google !== 'undefined') {
        const bounds = new google.maps.LatLngBounds();
        mappedStops.forEach((s) =>
          bounds.extend({ lat: s.latitude, lng: s.longitude })
        );
        m.fitBounds(bounds, 60);
      }
    },
    [mappedStops]
  );

  const onMapUnmount = useCallback(() => {
    setMap(null);
  }, []);

  if (!isOpen) return null;

  const handleMapClick = (e: google.maps.MapMouseEvent) => {
    if (!e.latLng) return;
    const lat = Number(e.latLng.lat().toFixed(6));
    const lng = Number(e.latLng.lng().toFixed(6));
    const newStopId = generateNextStopId(route.routeId, draftStops);
    const nextOrder = draftStops.length + 1;

    const newStop: BusStop = {
      stopId: newStopId,
      name: `Stop ${nextOrder}`,
      latitude: lat,
      longitude: lng,
      order: nextOrder,
      active: true,
      visibleOnMap: true,
    };

    setDraftStops((prev) => reindexStops([...prev, newStop]));
    setSelectedStopId(newStopId);
    setHasUnsavedChanges(true);
    setSaveError(null);
  };

  const handleMarkerDragEnd = (
    stopId: string,
    e: google.maps.MapMouseEvent
  ) => {
    if (!e.latLng) return;
    const lat = Number(e.latLng.lat().toFixed(6));
    const lng = Number(e.latLng.lng().toFixed(6));

    setDraftStops((prev) =>
      prev.map((s) =>
        s.stopId === stopId ? { ...s, latitude: lat, longitude: lng } : s
      )
    );
    setSelectedStopId(stopId);
    setHasUnsavedChanges(true);
    setSaveError(null);
  };

  const updateSelectedStop = (patch: Partial<BusStop>) => {
    if (!selectedStopId) return;
    setDraftStops((prev) =>
      prev.map((s) => (s.stopId === selectedStopId ? { ...s, ...patch } : s))
    );
    setHasUnsavedChanges(true);
    setSaveError(null);
  };

  const handleRemoveStop = (stopId: string) => {
    setDraftStops((prev) => {
      const next = reindexStops(prev.filter((s) => s.stopId !== stopId));
      if (selectedStopId === stopId) {
        setSelectedStopId(next[0]?.stopId ?? null);
      }
      return next;
    });
    setHasUnsavedChanges(true);
    setSaveError(null);
  };

  const handleSelectStopFromList = (stop: BusStop) => {
    setSelectedStopId(stop.stopId);
    if (map && checkStopHasCoordinates(stop)) {
      map.panTo({ lat: stop.latitude, lng: stop.longitude });
    }
  };

  const handleSaveChanges = async () => {
    setSaveError(null);

    const normalized = reindexStops(draftStops);
    for (const stop of normalized) {
      const err = normalizeValidationResult(
        validateStopInput({
          name: stop.name.trim(),
          latitude: stop.latitude,
          longitude: stop.longitude,
          visibleOnMap: stop.visibleOnMap,
        })
      );
      if (err) {
        setSelectedStopId(stop.stopId);
        setSaveError(`Stop #${stop.order} (${stop.name || stop.stopId}): ${err}`);
        return;
      }
    }

    setIsSaving(true);
    try {
      const cleaned = normalized.map((s) => ({
        ...s,
        name: s.name.trim(),
      }));
      await saveRouteStops(route.routeId, cleaned);
      onSuccess(`Saved ${cleaned.length} stops for route ${route.routeId}.`);
      onClose();
    } catch (err: unknown) {
      const msg =
        err instanceof Error ? err.message : 'Failed to save route stops.';
      setSaveError(msg);
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-2 sm:p-6 bg-slate-950/75 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="w-full max-w-6xl h-[92vh] bg-white rounded-2xl shadow-2xl border border-slate-200 flex flex-col overflow-hidden">
        {/* Top Header */}
        <div className="flex flex-wrap items-center justify-between gap-3 px-5 py-3.5 border-b border-slate-200 bg-slate-50/90">
          <div className="flex items-center gap-3 min-w-0">
            <div className="p-2 bg-blue-100 text-blue-700 rounded-xl shrink-0">
              <MapPin className="w-5 h-5" />
            </div>
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <h2 className="text-base sm:text-lg font-extrabold text-slate-900 truncate">
                  Route Stops Map Editor
                </h2>
                <span className="text-xs font-mono font-bold text-blue-700 bg-blue-100 px-2 py-0.5 rounded-md">
                  {route.routeId}
                </span>
                {hasUnsavedChanges && (
                  <span className="text-[11px] font-semibold text-amber-800 bg-amber-100 px-2 py-0.5 rounded-full">
                    Unsaved changes
                  </span>
                )}
              </div>
              <p className="text-xs text-slate-500 flex items-center gap-1.5 truncate">
                <MousePointerClick className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                <span>
                  Click map to add stop • Drag markers to reposition • Edit details in side panel
                </span>
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={onClose}
              disabled={isSaving}
              className="px-3.5 py-2 text-xs font-semibold text-slate-700 bg-white border border-slate-300 hover:bg-slate-100 rounded-xl transition-colors"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={handleSaveChanges}
              disabled={isSaving}
              className="inline-flex items-center gap-1.5 px-4 py-2 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 disabled:opacity-60 rounded-xl shadow-sm transition-colors"
            >
              {isSaving ? (
                <Loader2 className="w-3.5 h-3.5 animate-spin" />
              ) : (
                <Save className="w-3.5 h-3.5" />
              )}
              <span>Save Changes</span>
            </button>
            <button
              type="button"
              onClick={onClose}
              disabled={isSaving}
              className="p-1.5 text-slate-400 hover:text-slate-700 hover:bg-slate-200/70 rounded-lg transition-colors"
              aria-label="Close map editor"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* Save Error Banner */}
        {saveError && (
          <div className="px-5 py-2.5 bg-rose-50 border-b border-rose-200 text-rose-800 text-xs font-medium flex items-center gap-2">
            <AlertCircle className="w-4 h-4 text-rose-600 shrink-0" />
            <span>{saveError}</span>
          </div>
        )}

        {/* Main Content: Map + Side Panel */}
        <div className="flex-1 min-h-0 grid grid-cols-1 lg:grid-cols-12">
          {/* Map Area */}
          <div className="lg:col-span-8 h-72 lg:h-full bg-slate-100 relative">
            {!apiKey ? (
              <div className="w-full h-full bg-slate-900 p-6 flex flex-col items-center justify-center text-center">
                <div className="p-3 bg-amber-500/10 text-amber-400 rounded-2xl mb-3 border border-amber-500/20">
                  <AlertCircle className="w-6 h-6" />
                </div>
                <h4 className="text-sm font-bold text-slate-200">
                  Google Maps API Key Missing
                </h4>
                <p className="text-xs text-slate-400 max-w-md mt-1.5 leading-relaxed">
                  <code className="text-amber-300 font-mono text-[11px] bg-slate-800 px-1.5 py-0.5 rounded">
                    VITE_GOOGLE_MAPS_API_KEY
                  </code>{' '}
                  is not configured. You can still inspect and edit stop metadata or coordinates in the side panel.
                </p>
              </div>
            ) : loadError ? (
              <div className="w-full h-full bg-rose-950/10 p-6 flex flex-col items-center justify-center text-center">
                <AlertCircle className="w-8 h-8 text-rose-500 mb-2" />
                <p className="text-sm font-semibold text-slate-800">
                  Google Maps Failed to Load
                </p>
                <p className="text-xs text-slate-500 mt-1 max-w-sm">
                  {loadError.message ||
                    'Please check your internet connection or Google Maps API key restrictions.'}
                </p>
              </div>
            ) : !isLoaded ? (
              <div className="w-full h-full flex flex-col items-center justify-center text-slate-500 gap-2">
                <Loader2 className="w-6 h-6 animate-spin text-blue-600" />
                <span className="text-xs font-medium">Loading Google Maps...</span>
              </div>
            ) : (
              <GoogleMap
                mapContainerStyle={mapContainerStyle}
                center={
                  mappedStops[0]
                    ? {
                        lat: mappedStops[0].latitude,
                        lng: mappedStops[0].longitude,
                      }
                    : DEFAULT_CENTER
                }
                zoom={13}
                options={mapOptions}
                onLoad={onMapLoad}
                onUnmount={onMapUnmount}
                onClick={handleMapClick}
              >
                {polylinePath.length >= 2 && (
                  <PolylineF
                    path={polylinePath}
                    options={{
                      geodesic: true,
                      strokeColor: '#2563EB',
                      strokeOpacity: 0.85,
                      strokeWeight: 4,
                    }}
                  />
                )}

                {mappedStops.map((stop) => {
                  const isSelected = stop.stopId === selectedStopId;
                  return (
                    <MarkerF
                      key={stop.stopId}
                      position={{ lat: stop.latitude, lng: stop.longitude }}
                      draggable
                      label={{
                        text: String(stop.order),
                        color: '#FFFFFF',
                        fontWeight: 'bold',
                        fontSize: '11px',
                      }}
                      zIndex={isSelected ? 999 : stop.order}
                      onClick={() => setSelectedStopId(stop.stopId)}
                      onDragEnd={(e) => handleMarkerDragEnd(stop.stopId, e)}
                    />
                  );
                })}
              </GoogleMap>
            )}
          </div>

          {/* Side Panel */}
          <div className="lg:col-span-4 border-t lg:border-t-0 lg:border-l border-slate-200 flex flex-col min-h-0 bg-white">
            {/* Selected Stop Editor */}
            {selectedStop ? (
              <div className="p-4 border-b border-slate-200 bg-slate-50/70 space-y-3">
                <div className="flex items-center justify-between">
                  <div>
                    <span className="text-[11px] font-bold uppercase tracking-wider text-blue-700">
                      Editing Stop #{selectedStop.order}
                    </span>
                    <p className="text-[11px] font-mono text-slate-500">
                      {selectedStop.stopId}
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => handleRemoveStop(selectedStop.stopId)}
                    className="inline-flex items-center gap-1 px-2.5 py-1 text-[11px] font-semibold text-rose-700 bg-rose-50 hover:bg-rose-100 border border-rose-200 rounded-lg transition-colors"
                  >
                    <Trash2 className="w-3 h-3" />
                    <span>Remove</span>
                  </button>
                </div>

                <div>
                  <label className="block text-[11px] font-semibold text-slate-700 mb-1">
                    Stop Name
                  </label>
                  <input
                    type="text"
                    value={selectedStop.name}
                    onChange={(e) => updateSelectedStop({ name: e.target.value })}
                    className="w-full px-3 py-1.5 bg-white border border-slate-300 rounded-lg text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>

                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className="block text-[11px] font-semibold text-slate-700 mb-1">
                      Latitude
                    </label>
                    <input
                      type="number"
                      step="any"
                      value={selectedStop.latitude}
                      onChange={(e) =>
                        updateSelectedStop({
                          latitude: Number(e.target.value) || 0,
                        })
                      }
                      className="w-full px-2.5 py-1.5 bg-white border border-slate-300 rounded-lg text-[11px] font-mono text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                  <div>
                    <label className="block text-[11px] font-semibold text-slate-700 mb-1">
                      Longitude
                    </label>
                    <input
                      type="number"
                      step="any"
                      value={selectedStop.longitude}
                      onChange={(e) =>
                        updateSelectedStop({
                          longitude: Number(e.target.value) || 0,
                        })
                      }
                      className="w-full px-2.5 py-1.5 bg-white border border-slate-300 rounded-lg text-[11px] font-mono text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>
                </div>

                <div className="flex items-center gap-4 pt-1">
                  <label className="flex items-center gap-1.5 text-xs text-slate-700 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={selectedStop.visibleOnMap}
                      onChange={(e) =>
                        updateSelectedStop({ visibleOnMap: e.target.checked })
                      }
                      className="w-3.5 h-3.5 text-blue-600 rounded border-slate-300"
                    />
                    <span>Visible on Map</span>
                  </label>

                  <label className="flex items-center gap-1.5 text-xs text-slate-700 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={selectedStop.active}
                      onChange={(e) =>
                        updateSelectedStop({ active: e.target.checked })
                      }
                      className="w-3.5 h-3.5 text-blue-600 rounded border-slate-300"
                    />
                    <span>Active</span>
                  </label>
                </div>
              </div>
            ) : (
              <div className="p-4 border-b border-slate-200 bg-slate-50/70 text-xs text-slate-500">
                Select a stop below or click anywhere on the map to add a new stop.
              </div>
            )}

            {/* Stops Sequence List */}
            <div className="px-4 py-2.5 border-b border-slate-100 bg-white flex items-center justify-between">
              <span className="text-[11px] font-bold uppercase tracking-wider text-slate-500">
                Stops Sequence ({draftStops.length})
              </span>
              <span className="text-[11px] text-slate-400">
                {mappedStops.length} mapped
              </span>
            </div>

            <div className="flex-1 overflow-y-auto divide-y divide-slate-100">
              {draftStops.length === 0 ? (
                <div className="p-6 text-center text-xs text-slate-400">
                  No stops yet. Click on the map to place the first stop.
                </div>
              ) : (
                draftStops.map((stop) => {
                  const isSelected = stop.stopId === selectedStopId;
                  const hasCoords = checkStopHasCoordinates(stop);
                  return (
                    <button
                      key={stop.stopId}
                      type="button"
                      onClick={() => handleSelectStopFromList(stop)}
                      className={`w-full text-left px-4 py-3 transition-colors flex items-center justify-between gap-2 ${
                        isSelected
                          ? 'bg-blue-50/80 border-l-4 border-blue-600'
                          : 'hover:bg-slate-50'
                      }`}
                    >
                      <div className="min-w-0">
                        <div className="flex items-center gap-1.5">
                          <span className="text-[11px] font-mono font-bold text-slate-500">
                            #{stop.order}
                          </span>
                          <span className="text-xs font-bold text-slate-900 truncate">
                            {stop.name}
                          </span>
                          {!stop.active && (
                            <span className="text-[10px] bg-slate-200 text-slate-600 px-1.5 py-0.5 rounded">
                              Inactive
                            </span>
                          )}
                        </div>
                        <div className="text-[10px] font-mono text-slate-400 mt-0.5">
                          {hasCoords
                            ? `${stop.latitude.toFixed(5)}, ${stop.longitude.toFixed(5)}`
                            : stop.visibleOnMap === false
                            ? 'Notification-only (hidden on map)'
                            : 'Missing coordinates (0, 0)'}
                        </div>
                      </div>

                      {!stop.visibleOnMap ? (
                        <Bell className="w-3.5 h-3.5 text-indigo-500 shrink-0" />
                      ) : !hasCoords ? (
                        <AlertTriangle className="w-3.5 h-3.5 text-amber-500 shrink-0" />
                      ) : (
                        <MapPin className="w-3.5 h-3.5 text-emerald-600 shrink-0" />
                      )}
                    </button>
                  );
                })
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

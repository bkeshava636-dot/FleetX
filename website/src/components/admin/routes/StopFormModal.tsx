import React, { useEffect, useMemo, useState } from 'react';
import { X, MapPin, AlertCircle, Loader2, Bell } from 'lucide-react';
import { BusStop } from '../../../types/admin';
import {
  generateNextStopId,
  reindexStops,
  saveRouteStops,
  validateStopInput,
} from '../../../services/routesService';

interface StopFormModalProps {
  isOpen: boolean;
  mode: 'create' | 'edit';
  routeId: string;
  existingStops: BusStop[];
  initialStop?: BusStop | null;
  onClose: () => void;
  onSuccess: (message: string) => void;
}

function normalizeValidationResult(
  result: string | null | { valid: boolean; error?: string | null }
): string | null {
  if (typeof result === 'string') return result;
  if (result && typeof result === 'object' && 'valid' in result) {
    return result.valid ? null : result.error || 'Invalid stop input.';
  }
  return null;
}

export const StopFormModal: React.FC<StopFormModalProps> = ({
  isOpen,
  mode,
  routeId,
  existingStops,
  initialStop,
  onClose,
  onSuccess,
}) => {
  const [name, setName] = useState('');
  const [latitude, setLatitude] = useState('');
  const [longitude, setLongitude] = useState('');
  const [visibleOnMap, setVisibleOnMap] = useState(true);
  const [active, setActive] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const generatedStopId = useMemo(() => {
    if (mode === 'edit' && initialStop) return initialStop.stopId;
    return generateNextStopId(routeId, existingStops);
  }, [mode, initialStop, routeId, existingStops]);

  useEffect(() => {
    if (!isOpen) return;
    setError(null);
    if (mode === 'edit' && initialStop) {
      setName(initialStop.name);
      setLatitude(String(initialStop.latitude));
      setLongitude(String(initialStop.longitude));
      setVisibleOnMap(initialStop.visibleOnMap);
      setActive(initialStop.active);
    } else {
      setName('');
      setLatitude('');
      setLongitude('');
      setVisibleOnMap(true);
      setActive(true);
    }
  }, [isOpen, mode, initialStop]);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    const cleanName = name.trim();
    const parsedLat =
      !visibleOnMap && latitude.trim() === '' ? 0 : Number(latitude.trim());
    const parsedLng =
      !visibleOnMap && longitude.trim() === '' ? 0 : Number(longitude.trim());

    if (visibleOnMap && (latitude.trim() === '' || longitude.trim() === '')) {
      setError('Latitude and longitude are required for map-visible stops.');
      return;
    }

    const validationErr = normalizeValidationResult(
      validateStopInput({
        name: cleanName,
        latitude: parsedLat,
        longitude: parsedLng,
        visibleOnMap,
      })
    );

    if (validationErr) {
      setError(validationErr);
      return;
    }

    const targetStop: BusStop = {
      stopId: generatedStopId,
      name: cleanName,
      latitude: parsedLat,
      longitude: parsedLng,
      order: mode === 'create' ? existingStops.length + 1 : initialStop!.order,
      active,
      visibleOnMap,
    };

    const nextStops =
      mode === 'create'
        ? reindexStops([...existingStops, targetStop])
        : reindexStops(
            existingStops.map((s) =>
              s.stopId === targetStop.stopId ? targetStop : s
            )
          );

    setIsSubmitting(true);
    try {
      await saveRouteStops(routeId, nextStops);
      onSuccess(
        mode === 'create'
          ? `Stop "${cleanName}" added to ${routeId}.`
          : `Stop "${cleanName}" updated.`
      );
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to save stop.';
      setError(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/70 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="relative w-full max-w-lg bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 bg-slate-50/70">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-blue-100 text-blue-700 rounded-xl">
              <MapPin className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900">
                {mode === 'create' ? 'Add Route Stop' : 'Edit Route Stop'}
              </h2>
              <p className="text-xs text-slate-500">
                Route <span className="font-mono font-semibold">{routeId}</span> • Stop ID{' '}
                <span className="font-mono font-semibold">{generatedStopId}</span>
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={isSubmitting}
            className="p-1.5 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded-lg transition-colors"
            aria-label="Close modal"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {error && (
            <div className="flex items-start gap-3 p-3.5 bg-rose-50 border border-rose-200 rounded-xl text-rose-800 text-xs font-medium">
              <AlertCircle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
              <span>{error}</span>
            </div>
          )}

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1.5">
              Stop Name <span className="text-rose-500">*</span>
            </label>
            <input
              type="text"
              required
              placeholder="e.g. Main Gate / Central Library"
              value={name}
              onChange={(e) => setName(e.target.value)}
              disabled={isSubmitting}
              className="w-full px-3.5 py-2.5 bg-white border border-slate-300 rounded-xl text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
            />
          </div>

          <label className="flex items-start justify-between gap-3 p-3.5 bg-slate-50 border border-slate-200 rounded-xl cursor-pointer">
            <div className="flex items-start gap-2.5">
              <Bell className="w-4 h-4 text-indigo-600 mt-0.5 shrink-0" />
              <div>
                <span className="text-xs font-bold text-slate-800 block">
                  Visible on Map
                </span>
                <span className="text-[11px] text-slate-500">
                  Uncheck for notification-only stops without a map pin (allows 0, 0 coordinates).
                </span>
              </div>
            </div>
            <input
              type="checkbox"
              checked={visibleOnMap}
              onChange={(e) => setVisibleOnMap(e.target.checked)}
              disabled={isSubmitting}
              className="w-4 h-4 mt-0.5 text-blue-600 rounded border-slate-300 focus:ring-blue-500"
            />
          </label>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1.5">
                Latitude {visibleOnMap && <span className="text-rose-500">*</span>}
              </label>
              <input
                type="number"
                step="any"
                placeholder={visibleOnMap ? 'e.g. 12.971600' : '0.0'}
                value={latitude}
                onChange={(e) => setLatitude(e.target.value)}
                disabled={isSubmitting}
                className="w-full px-3.5 py-2.5 bg-white border border-slate-300 rounded-xl text-sm font-mono text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1.5">
                Longitude {visibleOnMap && <span className="text-rose-500">*</span>}
              </label>
              <input
                type="number"
                step="any"
                placeholder={visibleOnMap ? 'e.g. 77.594600' : '0.0'}
                value={longitude}
                onChange={(e) => setLongitude(e.target.value)}
                disabled={isSubmitting}
                className="w-full px-3.5 py-2.5 bg-white border border-slate-300 rounded-xl text-sm font-mono text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
              />
            </div>
          </div>

          <label className="flex items-center justify-between p-3.5 bg-slate-50 border border-slate-200 rounded-xl cursor-pointer">
            <div>
              <span className="text-xs font-bold text-slate-800 block">Stop Active</span>
              <span className="text-[11px] text-slate-500">
                Inactive stops are hidden from active route schedules.
              </span>
            </div>
            <input
              type="checkbox"
              checked={active}
              onChange={(e) => setActive(e.target.checked)}
              disabled={isSubmitting}
              className="w-4 h-4 text-blue-600 rounded border-slate-300 focus:ring-blue-500"
            />
          </label>

          {/* Footer */}
          <div className="flex items-center justify-end gap-2.5 pt-2 border-t border-slate-100">
            <button
              type="button"
              onClick={onClose}
              disabled={isSubmitting}
              className="px-4 py-2 text-xs font-semibold text-slate-700 bg-slate-100 hover:bg-slate-200 rounded-xl transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="inline-flex items-center gap-2 px-4 py-2 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 disabled:opacity-60 rounded-xl shadow-sm transition-colors"
            >
              {isSubmitting && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
              <span>{mode === 'create' ? 'Add Stop' : 'Save Stop'}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

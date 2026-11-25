import React, { useState } from 'react';
import { X, Route as RouteIcon, AlertCircle, Loader2 } from 'lucide-react';
import { assignBusRoute } from '../../../services/fleetService';
import { Route } from '../../../types/admin';
import { BusRecord } from '../../../types/fleet';

interface AssignRouteModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  bus: BusRecord | null;
  routes: Route[];
}

export const AssignRouteModal: React.FC<AssignRouteModalProps> = ({
  isOpen,
  onClose,
  onSuccess,
  bus,
  routes
}) => {
  const [selectedRouteId, setSelectedRouteId] = useState<string>('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  React.useEffect(() => {
    if (bus) {
      setSelectedRouteId(bus.routeId || '');
      setError(null);
    }
  }, [bus]);

  if (!isOpen || !bus) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setIsSubmitting(true);

    try {
      const targetRoute = selectedRouteId.trim() ? selectedRouteId.trim() : null;
      await assignBusRoute(bus.busId, targetRoute);
      onSuccess();
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to assign route.';
      setError(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  const selectedRouteObj = routes.find((r) => r.routeId === selectedRouteId);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/70 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="relative w-full max-w-md bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 bg-slate-50/50">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-emerald-100 text-emerald-700 rounded-xl">
              <RouteIcon className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900">Assign Route</h2>
              <p className="text-xs text-slate-500">
                Bus {bus.busNumber} ({bus.registrationNumber})
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            disabled={isSubmitting}
            className="p-1.5 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded-lg transition-colors"
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
              Select Official Route
            </label>
            <select
              value={selectedRouteId}
              onChange={(e) => setSelectedRouteId(e.target.value)}
              disabled={isSubmitting}
              className="w-full px-3.5 py-2.5 bg-white border border-slate-300 rounded-xl text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-transparent transition-all"
            >
              <option value="">-- No Route Assigned (Unassign) --</option>
              {routes.map((r) => (
                <option key={r.routeId} value={r.routeId}>
                  {r.routeId} : {r.name} ({r.stops.length} stops)
                </option>
              ))}
            </select>
          </div>

          {selectedRouteObj && (
            <div className="p-3 bg-slate-50 border border-slate-200 rounded-xl space-y-1">
              <div className="flex items-center justify-between text-xs font-semibold text-slate-800">
                <span>{selectedRouteObj.name}</span>
                <span className="text-[11px] font-mono px-2 py-0.5 bg-white rounded border border-slate-200 text-slate-600">
                  {selectedRouteObj.routeId}
                </span>
              </div>
              <p className="text-[11px] text-slate-500">
                {selectedRouteObj.description || 'Standard transit schedule'} • {selectedRouteObj.stops.length} stops
              </p>
            </div>
          )}

          <div className="pt-3 border-t border-slate-100 flex items-center justify-end gap-3">
            <button
              type="button"
              onClick={onClose}
              disabled={isSubmitting}
              className="px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-100 rounded-xl transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="px-5 py-2 text-xs font-semibold text-white bg-emerald-600 hover:bg-emerald-700 disabled:bg-emerald-400 rounded-xl transition-colors flex items-center gap-2 shadow-sm"
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  <span>Updating...</span>
                </>
              ) : (
                <span>Save Route Assignment</span>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

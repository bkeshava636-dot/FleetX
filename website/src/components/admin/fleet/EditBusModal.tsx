import React, { useState, useEffect } from 'react';
import { X, Edit2, AlertCircle, Loader2 } from 'lucide-react';
import { updateBusDetails, validateBusInput } from '../../../services/fleetService';
import { Route } from '../../../types/admin';
import { BusRecord } from '../../../types/fleet';

interface EditBusModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  bus: BusRecord | null;
  routes: Route[];
}

export const EditBusModal: React.FC<EditBusModalProps> = ({
  isOpen,
  onClose,
  onSuccess,
  bus,
  routes
}) => {
  const [registrationNumber, setRegistrationNumber] = useState('');
  const [capacity, setCapacity] = useState('50');
  const [routeId, setRouteId] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (bus) {
      setRegistrationNumber(bus.registrationNumber || '');
      setCapacity(String(bus.capacity || 50));
      setRouteId(bus.routeId || '');
      setError(null);
    }
  }, [bus]);

  if (!isOpen || !bus) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    const validationErr = validateBusInput({
      registrationNumber,
      capacity: Number(capacity)
    });

    if (validationErr) {
      setError(validationErr);
      return;
    }

    setIsSubmitting(true);
    try {
      await updateBusDetails(bus.busId, {
        registrationNumber: registrationNumber.trim().toUpperCase(),
        capacity: Number(capacity),
        routeId: routeId.trim() ? routeId.trim() : null
      });

      onSuccess();
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to update bus details.';
      setError(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/70 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="relative w-full max-w-lg bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 bg-slate-50/50">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-indigo-100 text-indigo-700 rounded-xl">
              <Edit2 className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900">Edit Bus Details</h2>
              <p className="text-xs text-slate-500">
                Updating {bus.busNumber} ({bus.busId})
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
              Bus Number (Permanent Identifier)
            </label>
            <input
              type="text"
              disabled
              value={bus.busNumber}
              className="w-full px-3.5 py-2.5 bg-slate-100 border border-slate-200 rounded-xl text-sm font-medium text-slate-500 cursor-not-allowed"
            />
            <p className="text-[11px] text-slate-400 mt-1">
              Bus numbers and primary document IDs are immutable once registered.
            </p>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1.5">
              Registration / License Plate Number <span className="text-rose-500">*</span>
            </label>
            <input
              type="text"
              required
              value={registrationNumber}
              onChange={(e) => setRegistrationNumber(e.target.value.toUpperCase())}
              disabled={isSubmitting}
              className="w-full px-3.5 py-2.5 bg-white border border-slate-300 rounded-xl text-sm font-mono uppercase text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
            />
            <p className="text-[11px] text-slate-500 mt-1">
              Updating the plate automatically updates and rotates unique index documents.
            </p>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1.5">
                Seating Capacity <span className="text-rose-500">*</span>
              </label>
              <input
                type="number"
                min="1"
                max="120"
                required
                value={capacity}
                onChange={(e) => setCapacity(e.target.value)}
                disabled={isSubmitting}
                className="w-full px-3.5 py-2.5 bg-white border border-slate-300 rounded-xl text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1.5">
                Route Assignment
              </label>
              <select
                value={routeId}
                onChange={(e) => setRouteId(e.target.value)}
                disabled={isSubmitting}
                className="w-full px-3.5 py-2.5 bg-white border border-slate-300 rounded-xl text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
              >
                <option value="">No Route Assigned</option>
                {routes.map((r) => (
                  <option key={r.routeId} value={r.routeId}>
                    {r.routeId} - {r.name}
                  </option>
                ))}
              </select>
            </div>
          </div>

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
              className="px-5 py-2 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 disabled:bg-blue-400 rounded-xl transition-colors flex items-center gap-2 shadow-sm"
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  <span>Saving...</span>
                </>
              ) : (
                <span>Save Changes</span>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

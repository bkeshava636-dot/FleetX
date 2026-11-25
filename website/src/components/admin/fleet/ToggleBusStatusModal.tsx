import React, { useState } from 'react';
import { X, Power, AlertTriangle, AlertCircle, Loader2 } from 'lucide-react';
import { toggleBusStatus } from '../../../services/fleetService';
import { BusRecord } from '../../../types/fleet';

interface ToggleBusStatusModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (updatedStatus: boolean) => void;
  bus: BusRecord | null;
}

export const ToggleBusStatusModal: React.FC<ToggleBusStatusModalProps> = ({
  isOpen,
  onClose,
  onSuccess,
  bus
}) => {
  const [unassignDriverConfirmed, setUnassignDriverConfirmed] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  React.useEffect(() => {
    setUnassignDriverConfirmed(false);
    setError(null);
  }, [bus]);

  if (!isOpen || !bus) return null;

  const isDeactivating = bus.active;
  const hasAssignedDriver = Boolean(bus.driverId);

  const handleToggle = async () => {
    setError(null);
    if (isDeactivating && hasAssignedDriver && !unassignDriverConfirmed) {
      setError('You must confirm unassigning the assigned driver to deactivate this bus.');
      return;
    }

    setIsSubmitting(true);
    try {
      const targetActiveState = !bus.active;
      await toggleBusStatus(bus.busId, targetActiveState, unassignDriverConfirmed);
      onSuccess(targetActiveState);
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to update bus status.';
      setError(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/70 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="relative w-full max-w-md bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 bg-slate-50/50">
          <div className="flex items-center gap-3">
            <div
              className={`p-2 rounded-xl ${
                isDeactivating ? 'bg-amber-100 text-amber-700' : 'bg-emerald-100 text-emerald-700'
              }`}
            >
              <Power className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900">
                {isDeactivating ? 'Deactivate Bus' : 'Activate Bus'}
              </h2>
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

        {/* Modal Body */}
        <div className="p-6 space-y-4">
          {error && (
            <div className="flex items-start gap-3 p-3.5 bg-rose-50 border border-rose-200 rounded-xl text-rose-800 text-xs font-medium">
              <AlertCircle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
              <span>{error}</span>
            </div>
          )}

          {isDeactivating ? (
            <div className="space-y-3">
              <p className="text-sm text-slate-600">
                Are you sure you want to deactivate <strong className="text-slate-900">{bus.busNumber}</strong>?
                Deactivated buses will not broadcast live telemetry or appear in active transit schedules.
              </p>

              {hasAssignedDriver && (
                <div className="p-4 bg-amber-50 border border-amber-200 rounded-xl space-y-2">
                  <div className="flex items-center gap-2 text-amber-800 font-semibold text-xs">
                    <AlertTriangle className="w-4 h-4 text-amber-600 shrink-0" />
                    <span>Assigned Driver Warning</span>
                  </div>
                  <p className="text-xs text-amber-700">
                    This bus currently has an assigned driver (<strong>{bus.driverName || bus.driverId}</strong>).
                    Deactivating the bus will detach the driver and clear operational authorization in RTDB.
                  </p>
                  <label className="flex items-start gap-2.5 pt-1 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={unassignDriverConfirmed}
                      onChange={(e) => setUnassignDriverConfirmed(e.target.checked)}
                      disabled={isSubmitting}
                      className="mt-0.5 rounded border-slate-300 text-amber-600 focus:ring-amber-500"
                    />
                    <span className="text-xs font-medium text-amber-900">
                      I understand and confirm unassigning the driver to deactivate this bus.
                    </span>
                  </label>
                </div>
              )}
            </div>
          ) : (
            <p className="text-sm text-slate-600">
              Are you sure you want to activate <strong className="text-slate-900">{bus.busNumber}</strong>?
              Once activated, drivers may be assigned and the bus can operate on assigned transit routes.
            </p>
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
              type="button"
              onClick={handleToggle}
              disabled={isSubmitting || (isDeactivating && hasAssignedDriver && !unassignDriverConfirmed)}
              className={`px-5 py-2 text-xs font-semibold text-white rounded-xl transition-colors flex items-center gap-2 shadow-sm ${
                isDeactivating
                  ? 'bg-amber-600 hover:bg-amber-700 disabled:bg-amber-300'
                  : 'bg-emerald-600 hover:bg-emerald-700 disabled:bg-emerald-300'
              }`}
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  <span>Processing...</span>
                </>
              ) : isDeactivating ? (
                <span>Deactivate Bus</span>
              ) : (
                <span>Activate Bus</span>
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

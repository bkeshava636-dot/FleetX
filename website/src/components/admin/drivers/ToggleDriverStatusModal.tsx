import React, { useState, useEffect } from 'react';
import { X, Power, AlertTriangle, AlertCircle, Loader2 } from 'lucide-react';
import { toggleDriverStatus } from '../../../services/driverService';
import { DriverRecord, BusRecord } from '../../../types/fleet';

interface ToggleDriverStatusModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (updatedStatus: boolean) => void;
  driver: DriverRecord | null;
  buses: BusRecord[];
}

export const ToggleDriverStatusModal: React.FC<ToggleDriverStatusModalProps> = ({
  isOpen,
  onClose,
  onSuccess,
  driver,
  buses
}) => {
  const [unassignConfirmed, setUnassignConfirmed] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setUnassignConfirmed(false);
    setError(null);
  }, [driver]);

  if (!isOpen || !driver) return null;

  const isDeactivating = driver.active;
  const assignedBus = driver.assignedBusId
    ? buses.find((b) => b.busId === driver.assignedBusId) || null
    : null;
  const hasAssignedBus = Boolean(driver.assignedBusId);

  const handleToggle = async () => {
    setError(null);

    if (isDeactivating && hasAssignedBus && !unassignConfirmed) {
      setError('You must confirm unassigning the assigned bus to deactivate this driver.');
      return;
    }

    setIsSubmitting(true);
    try {
      const targetActiveState = !driver.active;
      await toggleDriverStatus(driver.uid, targetActiveState, unassignConfirmed);
      onSuccess(targetActiveState);
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to update driver status.';
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
                {isDeactivating ? 'Deactivate Driver' : 'Activate Driver'}
              </h2>
              <p className="text-xs text-slate-500">
                {driver.name} ({driver.customId})
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

        {/* Body */}
        <div className="p-6 space-y-4">
          {error && (
            <div className="p-3 bg-red-50 border border-red-200 rounded-xl flex items-start gap-2.5 text-red-700 text-xs">
              <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
              <span>{error}</span>
            </div>
          )}

          {isDeactivating ? (
            <div className="space-y-3">
              <p className="text-sm text-slate-600">
                Are you sure you want to deactivate driver <strong>{driver.name}</strong>?
              </p>

              {hasAssignedBus ? (
                <div className="p-3.5 bg-amber-50 border border-amber-200 rounded-xl space-y-2.5">
                  <div className="flex items-start gap-2.5 text-amber-800 text-xs">
                    <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5 text-amber-600" />
                    <div>
                      <p className="font-semibold">Driver is currently assigned to a bus</p>
                      <p className="mt-0.5 text-amber-700 text-[11px]">
                        Assigned to: <strong>{assignedBus?.busNumber || driver.assignedBusId}</strong>{' '}
                        {assignedBus?.registrationNumber ? `(${assignedBus.registrationNumber})` : ''}.
                        Deactivating this driver will automatically unassign them and revoke their RTDB operator authorization.
                      </p>
                    </div>
                  </div>
                  <label className="flex items-start gap-2 pt-1 text-xs text-slate-700 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={unassignConfirmed}
                      onChange={(e) => setUnassignConfirmed(e.target.checked)}
                      disabled={isSubmitting}
                      className="mt-0.5 rounded text-amber-600 focus:ring-amber-500"
                    />
                    <span className="font-medium text-slate-800">
                      I confirm unassigning this driver from their bus and revoking GPS privileges.
                    </span>
                  </label>
                </div>
              ) : (
                <div className="p-3 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-600">
                  This driver is currently unassigned. Deactivation will disable their login and prevent vehicle operation.
                </div>
              )}
            </div>
          ) : (
            <div className="p-3 bg-emerald-50 border border-emerald-200 rounded-xl text-xs text-emerald-800 space-y-1">
              <p className="font-semibold">Re-activate Driver Account</p>
              <p className="text-emerald-700">
                This driver will be re-enabled for login and will be eligible for bus assignments.
              </p>
            </div>
          )}

          {/* Footer Actions */}
          <div className="flex items-center justify-end gap-3 pt-3 border-t border-slate-100">
            <button
              type="button"
              onClick={onClose}
              disabled={isSubmitting}
              className="px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100 rounded-xl transition-colors"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={handleToggle}
              disabled={isSubmitting || (isDeactivating && hasAssignedBus && !unassignConfirmed)}
              className={`inline-flex items-center gap-2 px-5 py-2 text-sm font-semibold text-white rounded-xl shadow-sm transition-all disabled:opacity-50 disabled:cursor-not-allowed ${
                isDeactivating
                  ? 'bg-amber-600 hover:bg-amber-700 active:bg-amber-800'
                  : 'bg-emerald-600 hover:bg-emerald-700 active:bg-emerald-800'
              }`}
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Updating...
                </>
              ) : isDeactivating ? (
                'Deactivate Driver'
              ) : (
                'Activate Driver'
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

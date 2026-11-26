import React, { useState, useEffect } from 'react';
import { X, Trash2, AlertTriangle, AlertCircle, Loader2 } from 'lucide-react';
import { deleteBus } from '../../../services/fleetService';
import { BusRecord } from '../../../types/fleet';

interface DeleteBusModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (bus: BusRecord) => void;
  bus: BusRecord | null;
}

export const DeleteBusModal: React.FC<DeleteBusModalProps> = ({
  isOpen,
  onClose,
  onSuccess,
  bus
}) => {
  const [confirmInput, setConfirmInput] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setConfirmInput('');
    setError(null);
  }, [bus]);

  if (!isOpen || !bus) return null;

  const trimmedInput = confirmInput.trim().toLowerCase();
  const isConfirmed =
    trimmedInput.length > 0 &&
    (trimmedInput === bus.busNumber.trim().toLowerCase() ||
      trimmedInput === bus.busId.trim().toLowerCase());

  const handleDelete = async () => {
    if (!isConfirmed) {
      setError(`Please type "${bus.busNumber}" or "${bus.busId}" to confirm deletion.`);
      return;
    }

    setIsSubmitting(true);
    setError(null);

    try {
      await deleteBus(bus.busId);
      onSuccess(bus);
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to delete bus.';
      setError(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/70 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="relative w-full max-w-md bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 bg-red-50/50">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-red-100 text-red-700 rounded-xl">
              <Trash2 className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900">Delete Bus</h2>
              <p className="text-xs text-red-600 font-medium">Permanent and irreversible action</p>
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

        {/* Content */}
        <div className="p-6 space-y-4">
          {error && (
            <div className="p-3 bg-red-50 border border-red-200 rounded-xl flex items-start gap-2.5 text-red-700 text-xs">
              <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
              <span>{error}</span>
            </div>
          )}

          {/* Warning Banner */}
          <div className="p-4 bg-red-50 border border-red-200 rounded-xl space-y-2 text-xs text-red-800">
            <div className="flex items-center gap-2 font-bold text-sm text-red-900">
              <AlertTriangle className="w-4 h-4 text-red-600" />
              <span>Warning: Critical Deletion</span>
            </div>
            <p>
              You are about to permanently delete <strong>Bus {bus.busNumber}</strong> ({bus.registrationNumber},{' '}
              <span className="font-mono">{bus.busId}</span>).
            </p>
            <ul className="list-disc pl-4 space-y-1 text-[11px] text-red-700">
              <li>Firestore bus record, uniqueness indexes, and public contact doc will be removed.</li>
              <li>Realtime Database live location, sources, operator, and swap mappings will be wiped.</li>
              <li>
                Deletion is blocked if a driver is assigned, an active bus swap exists, or an active/upcoming
                temporary assignment exists.
              </li>
            </ul>
          </div>

          {bus.driverId && (
            <div className="p-3 bg-amber-50 border border-amber-200 rounded-xl flex items-start gap-2.5 text-amber-800 text-xs">
              <AlertTriangle className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
              <span>
                Driver <strong>{bus.driverName || bus.driverId}</strong> is currently assigned to this bus. Unassign the
                driver before deleting.
              </span>
            </div>
          )}

          {/* High Friction Confirmation Input */}
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              To confirm, type <span className="font-mono text-red-600 font-bold">{bus.busNumber}</span> or{' '}
              <span className="font-mono text-red-600 font-bold">{bus.busId}</span> below:
            </label>
            <input
              type="text"
              value={confirmInput}
              onChange={(e) => setConfirmInput(e.target.value)}
              placeholder={`${bus.busNumber} or ${bus.busId}`}
              className="w-full px-3.5 py-2 text-sm bg-white border border-slate-300 rounded-xl focus:ring-2 focus:ring-red-500/20 focus:border-red-500 font-mono transition-all"
              disabled={isSubmitting}
            />
          </div>

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
              onClick={handleDelete}
              disabled={isSubmitting || !isConfirmed || Boolean(bus.driverId)}
              className="inline-flex items-center gap-2 px-5 py-2 text-sm font-semibold text-white bg-red-600 hover:bg-red-700 active:bg-red-800 disabled:opacity-50 disabled:cursor-not-allowed rounded-xl shadow-sm transition-all"
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Deleting Bus...
                </>
              ) : (
                'Permanently Delete'
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

import React, { useState, useEffect } from 'react';
import { X, Bus, ArrowRight, AlertTriangle, AlertCircle, Loader2, CheckCircle2 } from 'lucide-react';
import { assignDriverToBus, unassignDriverFromBus } from '../../../services/driverService';
import { DriverRecord, BusRecord } from '../../../types/fleet';

interface ManageAssignmentModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (message: string) => void;
  driver: DriverRecord | null;
  buses: BusRecord[];
}

export const ManageAssignmentModal: React.FC<ManageAssignmentModalProps> = ({
  isOpen,
  onClose,
  onSuccess,
  driver,
  buses
}) => {
  const [selectedBusId, setSelectedBusId] = useState<string>('');
  const [activeTab, setActiveTab] = useState<'TRANSFER' | 'UNASSIGN'>('TRANSFER');
  const [transferConfirmed, setTransferConfirmed] = useState(false);
  const [unassignConfirmed, setUnassignConfirmed] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const currentBus = driver?.assignedBusId
    ? buses.find((b) => b.busId === driver.assignedBusId) || null
    : null;

  useEffect(() => {
    if (driver) {
      setSelectedBusId('');
      setActiveTab('TRANSFER');
      setTransferConfirmed(false);
      setUnassignConfirmed(false);
      setError(null);
    }
  }, [driver]);

  if (!isOpen || !driver) return null;

  const isCurrentlyAssigned = Boolean(driver.assignedBusId);
  const activeBuses = buses.filter((b) => b.active && b.busId !== driver.assignedBusId);
  const targetBus = selectedBusId ? buses.find((b) => b.busId === selectedBusId) || null : null;
  const isTargetBusOccupied = Boolean(targetBus?.driverId && targetBus.driverId !== driver.uid);

  const handleAssignOrTransfer = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!selectedBusId) {
      setError('Please select a destination bus.');
      return;
    }

    if (isTargetBusOccupied && !transferConfirmed) {
      setError('Please check the confirmation box to proceed with replacing the existing driver.');
      return;
    }

    setIsSubmitting(true);
    try {
      const res = await assignDriverToBus(driver.uid, selectedBusId, {
        confirmReplacement: isTargetBusOccupied ? transferConfirmed : undefined,
        expectedPreviousDriverId: targetBus?.driverId || null
      });
      const targetBusObj = buses.find((b) => b.busId === selectedBusId);
      const busDisplay = targetBusObj ? `${targetBusObj.busNumber} (${targetBusObj.registrationNumber})` : selectedBusId;

      const successMsg = isCurrentlyAssigned
        ? `Transferred ${driver.name} to ${busDisplay} (v${res.assignmentVersion}).`
        : `Assigned ${driver.name} to ${busDisplay} (v${res.assignmentVersion}).`;

      onSuccess(successMsg);
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to update bus assignment.';
      setError(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleUnassign = async () => {
    if (!driver.assignedBusId) return;
    setError(null);

    if (!unassignConfirmed) {
      setError('Please check the confirmation box to confirm unassignment.');
      return;
    }

    setIsSubmitting(true);
    try {
      await unassignDriverFromBus(driver.assignedBusId);
      onSuccess(`Unassigned ${driver.name} from bus successfully.`);
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to unassign driver.';
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
              <Bus className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900">Bus Assignment Management</h2>
              <p className="text-xs text-slate-500">
                Manage operational transit vehicle for {driver.name} ({driver.customId})
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

        {/* Current State Banner */}
        <div className="px-6 pt-4 pb-2">
          <div className="p-3.5 bg-slate-50 border border-slate-200 rounded-xl flex items-center justify-between">
            <div>
              <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider block mb-0.5">
                Current Operational Assignment
              </span>
              {currentBus ? (
                <div className="flex items-center gap-2">
                  <span className="text-sm font-bold text-slate-900">{currentBus.busNumber}</span>
                  <span className="text-xs font-mono px-2 py-0.5 bg-blue-50 text-blue-700 border border-blue-200/60 rounded-md">
                    {currentBus.registrationNumber}
                  </span>
                  {currentBus.routeId && (
                    <span className="text-xs text-slate-500">• Route: {currentBus.routeId}</span>
                  )}
                </div>
              ) : (
                <span className="text-xs font-semibold text-amber-700 bg-amber-50 px-2 py-0.5 rounded-md border border-amber-200">
                  Currently Unassigned
                </span>
              )}
            </div>
            <span
              className={`px-2.5 py-1 text-xs font-semibold rounded-full ${
                driver.active ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-200 text-slate-700'
              }`}
            >
              {driver.active ? 'Active Driver' : 'Inactive Driver'}
            </span>
          </div>
        </div>

        {/* Navigation Tabs (Only if driver is currently assigned) */}
        {isCurrentlyAssigned && (
          <div className="px-6 pt-2">
            <div className="flex border-b border-slate-200">
              <button
                type="button"
                onClick={() => {
                  setActiveTab('TRANSFER');
                  setError(null);
                }}
                className={`py-2 px-4 text-xs font-bold border-b-2 transition-colors ${
                  activeTab === 'TRANSFER'
                    ? 'border-indigo-600 text-indigo-700'
                    : 'border-transparent text-slate-500 hover:text-slate-800'
                }`}
              >
                Transfer to Another Bus
              </button>
              <button
                type="button"
                onClick={() => {
                  setActiveTab('UNASSIGN');
                  setError(null);
                }}
                className={`py-2 px-4 text-xs font-bold border-b-2 transition-colors ${
                  activeTab === 'UNASSIGN'
                    ? 'border-red-600 text-red-700'
                    : 'border-transparent text-slate-500 hover:text-slate-800'
                }`}
              >
                Unassign from Bus
              </button>
            </div>
          </div>
        )}

        {/* Error Alert */}
        {error && (
          <div className="mx-6 mt-3 p-3 bg-red-50 border border-red-200 rounded-xl flex items-start gap-2.5 text-red-700 text-xs">
            <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
            <span>{error}</span>
          </div>
        )}

        {/* TAB 1: Assign or Transfer */}
        {(!isCurrentlyAssigned || activeTab === 'TRANSFER') && (
          <form onSubmit={handleAssignOrTransfer} className="p-6 space-y-4">
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                {isCurrentlyAssigned ? 'Select Destination Bus' : 'Select Bus to Assign'}{' '}
                <span className="text-red-500">*</span>
              </label>
              <select
                value={selectedBusId}
                onChange={(e) => {
                  setSelectedBusId(e.target.value);
                  setTransferConfirmed(false);
                  setError(null);
                }}
                disabled={isSubmitting}
                className="w-full px-3.5 py-2 text-sm bg-white border border-slate-300 rounded-xl focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all"
                required
              >
                <option value="">-- Choose an active bus --</option>
                {activeBuses.map((b) => (
                  <option key={b.busId} value={b.busId}>
                    {b.busNumber} ({b.registrationNumber}){' '}
                    {b.driverId ? `• Assigned to ${b.driverName || 'Another Driver'}` : '• (Available)'}
                  </option>
                ))}
              </select>
            </div>

            {/* Transfer Visual Route */}
            {isCurrentlyAssigned && targetBus && (
              <div className="p-3.5 bg-indigo-50/60 border border-indigo-200/60 rounded-xl">
                <span className="text-[11px] font-semibold text-indigo-700 uppercase tracking-wider block mb-2">
                  Assignment Transfer Path
                </span>
                <div className="flex items-center gap-3">
                  <div className="flex-1 p-2 bg-white rounded-lg border border-slate-200 text-center">
                    <span className="text-[10px] text-slate-400 block font-medium">Source</span>
                    <span className="text-xs font-bold text-slate-800">{currentBus?.busNumber}</span>
                  </div>
                  <ArrowRight className="w-4 h-4 text-indigo-600 shrink-0" />
                  <div className="flex-1 p-2 bg-white rounded-lg border border-slate-200 text-center">
                    <span className="text-[10px] text-slate-400 block font-medium">Destination</span>
                    <span className="text-xs font-bold text-slate-800">{targetBus.busNumber}</span>
                  </div>
                </div>
              </div>
            )}

            {/* Target Bus Occupancy Warning */}
            {isTargetBusOccupied && targetBus && (
              <div className="p-3.5 bg-amber-50 border border-amber-200 rounded-xl space-y-2">
                <div className="flex items-start gap-2.5 text-amber-800 text-xs">
                  <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5 text-amber-600" />
                  <div>
                    <p className="font-semibold">Bus {targetBus.busNumber} is already occupied</p>
                    <p className="mt-0.5 text-amber-700 text-[11px]">
                      Currently assigned to: <span className="font-bold">{targetBus.driverName || 'Another Driver'}</span>.
                      Proceeding will unassign that driver and assign {driver.name} as the permanent operator.
                    </p>
                  </div>
                </div>
                <label className="flex items-start gap-2 pt-1 text-xs text-slate-700 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={transferConfirmed}
                    onChange={(e) => setTransferConfirmed(e.target.checked)}
                    disabled={isSubmitting}
                    className="mt-0.5 rounded text-indigo-600 focus:ring-indigo-500"
                  />
                  <span className="font-medium text-slate-800">
                    I confirm replacing the existing driver on {targetBus.busNumber}.
                  </span>
                </label>
              </div>
            )}

            {/* Actions */}
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
                type="submit"
                disabled={isSubmitting || !selectedBusId || (isTargetBusOccupied && !transferConfirmed)}
                className="inline-flex items-center gap-2 px-5 py-2 text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-700 active:bg-indigo-800 disabled:opacity-50 disabled:cursor-not-allowed rounded-xl shadow-sm transition-all"
              >
                {isSubmitting ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    Synchronizing...
                  </>
                ) : (
                  <>
                    <CheckCircle2 className="w-4 h-4" />
                    {isCurrentlyAssigned ? 'Confirm Transfer' : 'Assign to Bus'}
                  </>
                )}
              </button>
            </div>
          </form>
        )}

        {/* TAB 2: Unassign */}
        {isCurrentlyAssigned && activeTab === 'UNASSIGN' && (
          <div className="p-6 space-y-4">
            <div className="p-4 bg-red-50 border border-red-200 rounded-xl flex items-start gap-3 text-red-800 text-xs">
              <AlertTriangle className="w-5 h-5 shrink-0 text-red-600 mt-0.5" />
              <div className="space-y-1">
                <p className="font-bold text-sm">Unassign Driver from Bus</p>
                <p className="text-red-700 text-xs">
                  This action will detach <strong>{driver.name}</strong> from bus{' '}
                  <strong>{currentBus?.busNumber} ({currentBus?.registrationNumber})</strong>.
                </p>
                <ul className="list-disc pl-4 text-[11px] text-red-600 space-y-0.5 mt-1">
                  <li>Revokes driver GPS broadcasting authorization from RTDB.</li>
                  <li>Increments operational assignment version atomically.</li>
                  <li>Leaves bus {currentBus?.busNumber} open for future driver assignment.</li>
                </ul>
              </div>
            </div>

            <label className="flex items-start gap-2.5 p-3 bg-slate-50 border border-slate-200 rounded-xl cursor-pointer">
              <input
                type="checkbox"
                checked={unassignConfirmed}
                onChange={(e) => setUnassignConfirmed(e.target.checked)}
                disabled={isSubmitting}
                className="mt-0.5 rounded text-red-600 focus:ring-red-500"
              />
              <span className="text-xs font-semibold text-slate-800">
                I understand this will immediately detach the driver and revoke their vehicle operator privileges.
              </span>
            </label>

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
                onClick={handleUnassign}
                disabled={isSubmitting || !unassignConfirmed}
                className="inline-flex items-center gap-2 px-5 py-2 text-sm font-semibold text-white bg-red-600 hover:bg-red-700 active:bg-red-800 disabled:opacity-50 disabled:cursor-not-allowed rounded-xl shadow-sm transition-all"
              >
                {isSubmitting ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    Unassigning...
                  </>
                ) : (
                  'Confirm Unassignment'
                )}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

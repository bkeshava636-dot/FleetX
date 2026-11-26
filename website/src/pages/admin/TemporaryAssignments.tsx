import React, { useEffect, useMemo, useState } from 'react';
import {
  UserCheck,
  Plus,
  Search,
  XCircle,
  Clock,
  CheckCircle2,
  AlertTriangle,
} from 'lucide-react';
import { subscribeToBuses } from '../../services/fleetService';
import { subscribeToDrivers } from '../../services/driverService';
import {
  cancelTemporaryAssignmentCallable,
  createTemporaryAssignmentCallable,
  subscribeToTemporaryAssignments,
} from '../../services/operationsService';
import { BusRecord, DriverRecord } from '../../types/fleet';
import { TemporaryAssignmentRecord } from '../../types/operations';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { EmptyState } from '../../components/common/EmptyState';
import { ErrorState } from '../../components/common/ErrorState';

function toLocalDatetimeInput(ms: number): string {
  const d = new Date(ms);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(
    d.getHours()
  )}:${pad(d.getMinutes())}`;
}

export const TemporaryAssignments: React.FC = () => {
  const [assignments, setAssignments] = useState<TemporaryAssignmentRecord[]>([]);
  const [buses, setBuses] = useState<BusRecord[]>([]);
  const [drivers, setDrivers] = useState<DriverRecord[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const [statusFilter, setStatusFilter] = useState<'ALL' | 'ACTIVE' | 'EXPIRED' | 'CANCELLED'>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [isCreateOpen, setIsCreateOpen] = useState(false);

  // Create form
  const [busId, setBusId] = useState('');
  const [replacementDriverUid, setReplacementDriverUid] = useState('');
  const [startAtStr, setStartAtStr] = useState(() => toLocalDatetimeInput(Date.now()));
  const [endAtStr, setEndAtStr] = useState(() =>
    toLocalDatetimeInput(Date.now() + 8 * 60 * 60 * 1000)
  );
  const [reason, setReason] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [cancellingId, setCancellingId] = useState<string | null>(null);
  const [pendingCancel, setPendingCancel] = useState<TemporaryAssignmentRecord | null>(null);
  const [toast, setToast] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const showToast = (type: 'success' | 'error', text: string) => {
    setToast({ type, text });
    setTimeout(() => setToast(null), 4500);
  };

  useEffect(() => {
    setIsLoading(true);
    const unsubAssign = subscribeToTemporaryAssignments(
      (data) => {
        setAssignments(data);
        setIsLoading(false);
      },
      (err) => {
        setErrorMessage(err.message || 'Failed to load temporary assignments.');
        setIsLoading(false);
      }
    );
    const unsubBuses = subscribeToBuses(setBuses, () => {});
    const unsubDrivers = subscribeToDrivers(setDrivers, () => {});

    return () => {
      unsubAssign();
      unsubBuses();
      unsubDrivers();
    };
  }, []);

  const selectedBus = useMemo(
    () => buses.find((b) => b.busId === busId) || null,
    [buses, busId]
  );

  const eligibleDrivers = useMemo(() => {
    return drivers.filter((d) => d.active && d.uid !== selectedBus?.driverId);
  }, [drivers, selectedBus]);

  const filteredAssignments = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    return assignments.filter((a) => {
      if (statusFilter !== 'ALL' && a.status !== statusFilter) return false;
      if (!q) return true;
      return (
        a.busId.toLowerCase().includes(q) ||
        a.permanentDriverName.toLowerCase().includes(q) ||
        a.replacementDriverName.toLowerCase().includes(q) ||
        a.reason.toLowerCase().includes(q)
      );
    });
  }, [assignments, statusFilter, searchQuery]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    const startAt = new Date(startAtStr).getTime();
    const endAt = new Date(endAtStr).getTime();
    if (!busId || !replacementDriverUid) {
      showToast('error', 'Select both a bus and a replacement driver.');
      return;
    }
    if (isNaN(startAt) || isNaN(endAt) || startAt >= endAt) {
      showToast('error', 'Start time must be before end time.');
      return;
    }

    setIsSubmitting(true);
    try {
      await createTemporaryAssignmentCallable({
        busId,
        replacementDriverUid,
        startAt,
        endAt,
        reason,
      });
      showToast('success', `Temporary replacement assigned for ${busId}.`);
      setIsCreateOpen(false);
      setBusId('');
      setReplacementDriverUid('');
      setReason('');
    } catch (err) {
      showToast(
        'error',
        err instanceof Error ? err.message : 'Failed to create temporary assignment.'
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleCancel = async (assignmentId: string) => {
    setCancellingId(assignmentId);
    try {
      await cancelTemporaryAssignmentCallable(assignmentId);
      showToast('success', 'Temporary assignment cancelled and RTDB synced.');
      setPendingCancel(null);
    } catch (err) {
      showToast(
        'error',
        err instanceof Error ? err.message : 'Failed to cancel temporary assignment.'
      );
    } finally {
      setCancellingId(null);
    }
  };

  return (
    <div className="space-y-6">
      {toast && (
        <div
          className={`fixed bottom-5 right-5 z-50 px-4 py-3 rounded-xl shadow-lg text-xs font-semibold flex items-center gap-2 border ${
            toast.type === 'success'
              ? 'bg-emerald-50 text-emerald-800 border-emerald-200'
              : 'bg-red-50 text-red-800 border-red-200'
          }`}
        >
          {toast.type === 'success' ? (
            <CheckCircle2 className="w-4 h-4 text-emerald-600" />
          ) : (
            <AlertTriangle className="w-4 h-4 text-red-600" />
          )}
          <span>{toast.text}</span>
        </div>
      )}

      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-slate-900 flex items-center gap-2">
            <UserCheck className="w-5 h-5 text-blue-600" />
            <span>Temporary Driver Assignments</span>
          </h2>
          <p className="text-xs text-slate-500 mt-1">
            Assign temporary relief drivers with automatic conflict checking, RTDB operator sync, and student contact updates.
          </p>
        </div>
        <button
          onClick={() => setIsCreateOpen(true)}
          className="inline-flex items-center gap-2 px-4 py-2.5 bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold rounded-xl shadow-sm transition-colors"
        >
          <Plus className="w-4 h-4" />
          <span>Assign Relief Driver</span>
        </button>
      </div>

      {/* Filters */}
      <div className="bg-white rounded-2xl border border-slate-200 p-4 flex flex-col sm:flex-row gap-3 items-stretch sm:items-center justify-between">
        <div className="relative flex-1 max-w-md">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search by bus, permanent driver, relief driver, or reason..."
            className="w-full pl-9 pr-3 py-2 text-xs border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {(['ALL', 'ACTIVE', 'EXPIRED', 'CANCELLED'] as const).map((status) => (
            <button
              key={status}
              onClick={() => setStatusFilter(status)}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-colors ${
                statusFilter === status
                  ? 'bg-blue-600 text-white'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              {status}
            </button>
          ))}
        </div>
      </div>

      {errorMessage && <ErrorState title="Temporary Assignments Error" message={errorMessage} />}

      {isLoading ? (
        <LoadingSpinner label="Loading temporary assignments..." />
      ) : filteredAssignments.length === 0 ? (
        <EmptyState
          title="No temporary assignments"
          description="No driver replacement records match the selected filters."
          icon={UserCheck}
        />
      ) : (
        <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50 text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                  <th className="py-3 px-4">Bus</th>
                  <th className="py-3 px-4">Permanent → Relief Driver</th>
                  <th className="py-3 px-4">Window</th>
                  <th className="py-3 px-4">Reason</th>
                  <th className="py-3 px-4">Status</th>
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-xs">
                {filteredAssignments.map((a) => (
                  <tr key={a.assignmentId} className="hover:bg-slate-50/70">
                    <td className="py-3 px-4 font-semibold text-slate-900">{a.busId}</td>
                    <td className="py-3 px-4">
                      <span className="text-slate-600">{a.permanentDriverName || 'Unassigned'}</span>
                      <span className="mx-1.5 text-slate-400">→</span>
                      <span className="font-semibold text-blue-700">{a.replacementDriverName}</span>
                    </td>
                    <td className="py-3 px-4 text-slate-600">
                      <div className="flex items-center gap-1">
                        <Clock className="w-3.5 h-3.5 text-slate-400" />
                        <span>{new Date(a.startAt).toLocaleString()}</span>
                      </div>
                      <p className="text-[11px] text-slate-400 pl-4">
                        until {new Date(a.endAt).toLocaleString()}
                      </p>
                    </td>
                    <td className="py-3 px-4 text-slate-600 max-w-xs truncate">
                      {a.reason || '—'}
                    </td>
                    <td className="py-3 px-4">
                      <span
                        className={`inline-flex px-2 py-0.5 rounded-full text-[11px] font-semibold ${
                          a.status === 'ACTIVE'
                            ? 'bg-emerald-100 text-emerald-800'
                            : a.status === 'EXPIRED'
                            ? 'bg-slate-100 text-slate-600'
                            : 'bg-red-100 text-red-700'
                        }`}
                      >
                        {a.status}
                      </span>
                    </td>
                    <td className="py-3 px-4 text-right">
                      {a.status === 'ACTIVE' ? (
                        <button
                          disabled={cancellingId === a.assignmentId}
                          onClick={() => setPendingCancel(a)}
                          className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-red-50 text-red-700 hover:bg-red-100 font-semibold disabled:opacity-50"
                        >
                          <XCircle className="w-3.5 h-3.5" />
                          <span>{cancellingId === a.assignmentId ? 'Cancelling...' : 'Cancel'}</span>
                        </button>
                      ) : (
                        <span className="text-slate-400 text-[11px]">—</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Create Modal */}
      {isCreateOpen && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-lg w-full p-6 shadow-xl border border-slate-200">
            <h3 className="text-base font-bold text-slate-900">Assign Temporary Relief Driver</h3>
            <p className="text-xs text-slate-500 mt-1">
              Calls the backend Cloud Function to validate driver availability and update live permissions.
            </p>

            <form onSubmit={handleCreate} className="mt-4 space-y-4 text-xs">
              <div>
                <label className="block font-semibold text-slate-700 mb-1">Bus</label>
                <select
                  required
                  value={busId}
                  onChange={(e) => {
                    setBusId(e.target.value);
                    setReplacementDriverUid('');
                  }}
                  className="w-full border border-slate-200 rounded-xl px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="">Select bus...</option>
                  {buses
                    .filter((b) => b.active)
                    .map((b) => (
                      <option key={b.busId} value={b.busId}>
                        {b.busNumber || b.busId} — Permanent: {b.driverName || 'None'}
                      </option>
                    ))}
                </select>
              </div>

              <div>
                <label className="block font-semibold text-slate-700 mb-1">Replacement Driver</label>
                <select
                  required
                  value={replacementDriverUid}
                  onChange={(e) => setReplacementDriverUid(e.target.value)}
                  className="w-full border border-slate-200 rounded-xl px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="">Select relief driver...</option>
                  {eligibleDrivers.map((d) => (
                    <option key={d.uid} value={d.uid}>
                      {d.name} ({d.customId})
                    </option>
                  ))}
                </select>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block font-semibold text-slate-700 mb-1">Start Time</label>
                  <input
                    type="datetime-local"
                    required
                    value={startAtStr}
                    onChange={(e) => setStartAtStr(e.target.value)}
                    className="w-full border border-slate-200 rounded-xl px-3 py-2"
                  />
                </div>
                <div>
                  <label className="block font-semibold text-slate-700 mb-1">End Time</label>
                  <input
                    type="datetime-local"
                    required
                    value={endAtStr}
                    onChange={(e) => setEndAtStr(e.target.value)}
                    className="w-full border border-slate-200 rounded-xl px-3 py-2"
                  />
                </div>
              </div>

              <div>
                <label className="block font-semibold text-slate-700 mb-1">Reason</label>
                <input
                  type="text"
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  placeholder="e.g., Driver on medical leave"
                  className="w-full border border-slate-200 rounded-xl px-3 py-2"
                />
              </div>

              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setIsCreateOpen(false)}
                  className="px-4 py-2 rounded-xl border border-slate-200 text-slate-600 font-semibold hover:bg-slate-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="px-4 py-2 rounded-xl bg-blue-600 hover:bg-blue-700 text-white font-semibold disabled:opacity-50"
                >
                  {isSubmitting ? 'Assigning...' : 'Assign Driver'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Confirm Cancel Modal */}
      {pendingCancel && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-xl border border-slate-200 space-y-4">
            <div className="flex items-start gap-3">
              <div className="p-2.5 rounded-xl bg-red-50 text-red-600">
                <AlertTriangle className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-base font-bold text-slate-900">
                  Cancel Temporary Assignment?
                </h3>
                <p className="text-xs text-slate-500 mt-1">
                  This will end the relief driver assignment immediately and restore the permanent driver in RTDB.
                </p>
              </div>
            </div>

            <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 text-xs space-y-1">
              <p className="font-semibold text-slate-900">Bus: {pendingCancel.busId}</p>
              <p className="text-slate-600">
                {pendingCancel.permanentDriverName || 'Unassigned'} →{' '}
                {pendingCancel.replacementDriverName}
              </p>
            </div>

            <div className="flex justify-end gap-2 pt-1 text-xs">
              <button
                type="button"
                disabled={cancellingId === pendingCancel.assignmentId}
                onClick={() => setPendingCancel(null)}
                className="px-4 py-2 rounded-xl border border-slate-200 text-slate-600 font-semibold hover:bg-slate-50 disabled:opacity-50"
              >
                Keep Active
              </button>
              <button
                type="button"
                disabled={cancellingId === pendingCancel.assignmentId}
                onClick={() => handleCancel(pendingCancel.assignmentId)}
                className="px-4 py-2 rounded-xl bg-red-600 hover:bg-red-700 text-white font-semibold disabled:opacity-50"
              >
                {cancellingId === pendingCancel.assignmentId ? 'Cancelling...' : 'Confirm Cancel'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default TemporaryAssignments;

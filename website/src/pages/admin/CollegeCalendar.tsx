import React, { useEffect, useState } from 'react';
import {
  Calendar,
  Plus,
  Trash2,
  Clock,
  CheckCircle2,
  AlertTriangle,
  Save,
} from 'lucide-react';
import { useAdminAuth } from '../../context/AdminAuthContext';
import {
  deleteCalendarException,
  saveCalendarException,
  saveOperatingSchedule,
  subscribeToCalendarExceptions,
  subscribeToOperatingSchedule,
} from '../../services/operationsService';
import { RouteSchedule } from '../../types/admin';
import { CalendarExceptionRecord, CalendarExceptionType } from '../../types/operations';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { EmptyState } from '../../components/common/EmptyState';
import { ErrorState } from '../../components/common/ErrorState';
import { MasterTimetablePdfCard } from '../../components/admin/calendar/MasterTimetablePdfCard';
import { DeleteConfirmModal } from '../../components/admin/routes/DeleteConfirmModal';

export const CollegeCalendar: React.FC = () => {
  const { profile } = useAdminAuth();
  const [exceptions, setExceptions] = useState<CalendarExceptionRecord[]>([]);
  const [schedule, setSchedule] = useState<RouteSchedule>({
    morning: { startTime: '07:00', endTime: '09:00', enabled: true },
    evening: { startTime: '16:00', endTime: '18:00', enabled: true },
  });
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Exception form
  const [dateStr, setDateStr] = useState('');
  const [type, setType] = useState<CalendarExceptionType>('FULL_DAY_HOLIDAY');
  const [name, setName] = useState('');
  const [isSavingException, setIsSavingException] = useState(false);
  const [isSavingSchedule, setIsSavingSchedule] = useState(false);
  const [pendingDeleteException, setPendingDeleteException] =
    useState<CalendarExceptionRecord | null>(null);
  const [toast, setToast] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const showToast = (type: 'success' | 'error', text: string) => {
    setToast({ type, text });
    setTimeout(() => setToast(null), 4000);
  };

  useEffect(() => {
    setIsLoading(true);
    const unsubEx = subscribeToCalendarExceptions(
      (data) => {
        setExceptions(data);
        setIsLoading(false);
      },
      (err) => {
        setErrorMessage(err.message || 'Failed to load calendar exceptions.');
        setIsLoading(false);
      }
    );
    const unsubSched = subscribeToOperatingSchedule(setSchedule, () => {});
    return () => {
      unsubEx();
      unsubSched();
    };
  }, []);

  const handleSaveException = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!dateStr || !name.trim()) {
      showToast('error', 'Date and event name are required.');
      return;
    }
    setIsSavingException(true);
    try {
      await saveCalendarException({
        date: dateStr,
        type,
        name: name.trim(),
        updatedBy: profile?.name || profile?.email || 'Admin',
      });
      showToast('success', `Saved calendar exception for ${dateStr}.`);
      setDateStr('');
      setName('');
    } catch (err) {
      showToast('error', err instanceof Error ? err.message : 'Failed to save exception.');
    } finally {
      setIsSavingException(false);
    }
  };

  const handleDeleteException = async () => {
    if (!pendingDeleteException) return;
    await deleteCalendarException(pendingDeleteException.date);
    showToast('success', `Removed exception for ${pendingDeleteException.date}.`);
  };

  const handleSaveSchedule = async () => {
    setIsSavingSchedule(true);
    try {
      await saveOperatingSchedule(schedule, profile?.name || profile?.email || 'Admin');
      showToast('success', 'Default operating schedule saved.');
    } catch (err) {
      showToast('error', err instanceof Error ? err.message : 'Failed to save schedule.');
    } finally {
      setIsSavingSchedule(false);
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

      <div>
        <h2 className="text-xl font-bold text-slate-900 flex items-center gap-2">
          <Calendar className="w-5 h-5 text-blue-600" />
          <span>College Calendar & Operating Hours</span>
        </h2>
        <p className="text-xs text-slate-500 mt-1">
          Configure holidays, special working-day overrides, and global morning/evening bus operating windows.
        </p>
      </div>

      {errorMessage && <ErrorState title="Calendar Error" message={errorMessage} />}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Default Operating Schedule */}
        <div className="bg-white rounded-2xl border border-slate-200 p-5 space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2">
              <Clock className="w-4 h-4 text-blue-600" />
              <span>Daily Operating Windows</span>
            </h3>
          </div>

          <div className="space-y-3 text-xs">
            <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 space-y-2">
              <div className="flex items-center justify-between">
                <span className="font-semibold text-slate-800">Morning Pickup</span>
                <input
                  type="checkbox"
                  checked={schedule.morning.enabled}
                  onChange={(e) =>
                    setSchedule((s) => ({
                      ...s,
                      morning: { ...s.morning, enabled: e.target.checked },
                    }))
                  }
                />
              </div>
              <div className="grid grid-cols-2 gap-2">
                <input
                  type="time"
                  value={schedule.morning.startTime}
                  onChange={(e) =>
                    setSchedule((s) => ({
                      ...s,
                      morning: { ...s.morning, startTime: e.target.value },
                    }))
                  }
                  className="border border-slate-200 rounded-lg px-2 py-1.5 bg-white"
                />
                <input
                  type="time"
                  value={schedule.morning.endTime}
                  onChange={(e) =>
                    setSchedule((s) => ({
                      ...s,
                      morning: { ...s.morning, endTime: e.target.value },
                    }))
                  }
                  className="border border-slate-200 rounded-lg px-2 py-1.5 bg-white"
                />
              </div>
            </div>

            <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 space-y-2">
              <div className="flex items-center justify-between">
                <span className="font-semibold text-slate-800">Evening Drop</span>
                <input
                  type="checkbox"
                  checked={schedule.evening.enabled}
                  onChange={(e) =>
                    setSchedule((s) => ({
                      ...s,
                      evening: { ...s.evening, enabled: e.target.checked },
                    }))
                  }
                />
              </div>
              <div className="grid grid-cols-2 gap-2">
                <input
                  type="time"
                  value={schedule.evening.startTime}
                  onChange={(e) =>
                    setSchedule((s) => ({
                      ...s,
                      evening: { ...s.evening, startTime: e.target.value },
                    }))
                  }
                  className="border border-slate-200 rounded-lg px-2 py-1.5 bg-white"
                />
                <input
                  type="time"
                  value={schedule.evening.endTime}
                  onChange={(e) =>
                    setSchedule((s) => ({
                      ...s,
                      evening: { ...s.evening, endTime: e.target.value },
                    }))
                  }
                  className="border border-slate-200 rounded-lg px-2 py-1.5 bg-white"
                />
              </div>
            </div>

            <button
              onClick={handleSaveSchedule}
              disabled={isSavingSchedule}
              className="w-full inline-flex items-center justify-center gap-2 px-4 py-2.5 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded-xl disabled:opacity-50"
            >
              <Save className="w-4 h-4" />
              <span>{isSavingSchedule ? 'Saving...' : 'Save Operating Hours'}</span>
            </button>
          </div>
        </div>

        {/* Add Exception + Exceptions List */}
        <div className="lg:col-span-2 space-y-4">
          <form
            onSubmit={handleSaveException}
            className="bg-white rounded-2xl border border-slate-200 p-5 grid grid-cols-1 sm:grid-cols-4 gap-3 items-end text-xs"
          >
            <div>
              <label className="block font-semibold text-slate-700 mb-1">Date</label>
              <input
                type="date"
                required
                value={dateStr}
                onChange={(e) => setDateStr(e.target.value)}
                className="w-full border border-slate-200 rounded-xl px-3 py-2"
              />
            </div>
            <div>
              <label className="block font-semibold text-slate-700 mb-1">Type</label>
              <select
                value={type}
                onChange={(e) => setType(e.target.value as CalendarExceptionType)}
                className="w-full border border-slate-200 rounded-xl px-3 py-2"
              >
                <option value="FULL_DAY_HOLIDAY">Holiday</option>
                <option value="WORKING_DAY">Working Day Override</option>
              </select>
            </div>
            <div>
              <label className="block font-semibold text-slate-700 mb-1">Title / Reason</label>
              <input
                type="text"
                required
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="e.g., Republic Day"
                className="w-full border border-slate-200 rounded-xl px-3 py-2"
              />
            </div>
            <button
              type="submit"
              disabled={isSavingException}
              className="inline-flex items-center justify-center gap-1.5 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded-xl disabled:opacity-50"
            >
              <Plus className="w-4 h-4" />
              <span>{isSavingException ? 'Adding...' : 'Add Date'}</span>
            </button>
          </form>

          {isLoading ? (
            <LoadingSpinner label="Loading calendar exceptions..." />
          ) : exceptions.length === 0 ? (
            <EmptyState
              title="No calendar exceptions"
              description="Add holidays or special working-day overrides above."
              icon={Calendar}
            />
          ) : (
            <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className="border-b border-slate-200 bg-slate-50 text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                    <th className="py-3 px-4">Date</th>
                    <th className="py-3 px-4">Type</th>
                    <th className="py-3 px-4">Event</th>
                    <th className="py-3 px-4 text-right">Remove</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {exceptions.map((ex) => (
                    <tr key={ex.date} className="hover:bg-slate-50/70">
                      <td className="py-3 px-4 font-semibold text-slate-900">{ex.date}</td>
                      <td className="py-3 px-4">
                        <span
                          className={`inline-flex px-2 py-0.5 rounded-full text-[11px] font-semibold ${
                            ex.type === 'FULL_DAY_HOLIDAY'
                              ? 'bg-red-100 text-red-700'
                              : 'bg-emerald-100 text-emerald-800'
                          }`}
                        >
                          {ex.type === 'FULL_DAY_HOLIDAY' ? 'Holiday' : 'Working Day'}
                        </span>
                      </td>
                      <td className="py-3 px-4 text-slate-700">{ex.name}</td>
                      <td className="py-3 px-4 text-right">
                        <button
                          onClick={() => setPendingDeleteException(ex)}
                          className="p-1.5 text-red-600 hover:bg-red-50 rounded-lg"
                          title="Delete exception"
                          aria-label={`Delete exception for ${ex.date}`}
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>

      <MasterTimetablePdfCard
        schedule={schedule}
        updatedBy={profile?.name || profile?.email || 'Admin'}
        onToast={showToast}
      />

      <DeleteConfirmModal
        isOpen={Boolean(pendingDeleteException)}
        title="Delete Calendar Exception"
        itemName={pendingDeleteException?.name || ''}
        itemId={pendingDeleteException?.date}
        description="Are you sure you want to remove this calendar exception? Buses will revert to the default operating schedule for this date."
        confirmLabel="Delete Exception"
        onClose={() => setPendingDeleteException(null)}
        onConfirm={handleDeleteException}
      />
    </div>
  );
};

export default CollegeCalendar;

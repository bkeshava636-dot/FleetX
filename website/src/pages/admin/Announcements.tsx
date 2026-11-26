import React, { useEffect, useMemo, useState } from 'react';
import {
  Bell,
  Plus,
  Search,
  Archive,
  Trash2,
  Send,
  CheckCircle2,
  AlertTriangle,
} from 'lucide-react';
import { useAdminAuth } from '../../context/AdminAuthContext';
import { subscribeToBuses } from '../../services/fleetService';
import { subscribeToRoutes } from '../../services/routesService';
import {
  createAnnouncement,
  deleteAnnouncement,
  subscribeToAnnouncements,
  updateAnnouncementStatus,
} from '../../services/operationsService';
import { BusRecord } from '../../types/fleet';
import { Route } from '../../types/admin';
import {
  AnnouncementAudience,
  AnnouncementCategory,
  AnnouncementPriority,
  AnnouncementRecord,
  AnnouncementStatus,
} from '../../types/operations';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { EmptyState } from '../../components/common/EmptyState';
import { ErrorState } from '../../components/common/ErrorState';

const CATEGORIES: AnnouncementCategory[] = [
  'GENERAL',
  'TIMING_CHANGE',
  'BUS_DELAY',
  'BUS_CANCELLATION',
  'ROUTE_CHANGE',
  'BUS_SWAP',
  'HOLIDAY',
  'EMERGENCY',
];

export const Announcements: React.FC = () => {
  const { profile } = useAdminAuth();
  const [announcements, setAnnouncements] = useState<AnnouncementRecord[]>([]);
  const [buses, setBuses] = useState<BusRecord[]>([]);
  const [routes, setRoutes] = useState<Route[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const [statusFilter, setStatusFilter] = useState<'ALL' | AnnouncementStatus>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [isCreateOpen, setIsCreateOpen] = useState(false);

  // Create form
  const [titleEn, setTitleEn] = useState('');
  const [messageEn, setMessageEn] = useState('');
  const [titleKn, setTitleKn] = useState('');
  const [messageKn, setMessageKn] = useState('');
  const [category, setCategory] = useState<AnnouncementCategory>('GENERAL');
  const [priority, setPriority] = useState<AnnouncementPriority>('NORMAL');
  const [targetAudience, setTargetAudience] = useState<AnnouncementAudience>('ALL');
  const [targetId, setTargetId] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [toast, setToast] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const showToast = (type: 'success' | 'error', text: string) => {
    setToast({ type, text });
    setTimeout(() => setToast(null), 4000);
  };

  useEffect(() => {
    setIsLoading(true);
    const unsub = subscribeToAnnouncements(
      (data) => {
        setAnnouncements(data);
        setIsLoading(false);
      },
      (err) => {
        setErrorMessage(err.message || 'Failed to load announcements.');
        setIsLoading(false);
      }
    );
    const unsubBuses = subscribeToBuses(setBuses, () => {});
    const unsubRoutes = subscribeToRoutes(setRoutes, () => {});
    return () => {
      unsub();
      unsubBuses();
      unsubRoutes();
    };
  }, []);

  const filtered = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    return announcements.filter((a) => {
      if (statusFilter !== 'ALL' && a.status !== statusFilter) return false;
      if (!q) return true;
      return (
        a.titleEn.toLowerCase().includes(q) ||
        a.messageEn.toLowerCase().includes(q) ||
        a.category.toLowerCase().includes(q)
      );
    });
  }, [announcements, statusFilter, searchQuery]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!titleEn.trim() || !messageEn.trim()) {
      showToast('error', 'English title and message are required.');
      return;
    }
    setIsSubmitting(true);
    try {
      await createAnnouncement({
        titleEn,
        messageEn,
        titleKn,
        messageKn,
        category,
        priority,
        targetAudience,
        targetId,
        status: 'PUBLISHED',
        createdBy: profile?.uid || '',
        createdByName: profile?.name || profile?.email || 'Admin',
      });
      showToast('success', 'Announcement published and push notifications queued.');
      setIsCreateOpen(false);
      setTitleEn('');
      setMessageEn('');
      setTitleKn('');
      setMessageKn('');
      setTargetId('');
    } catch (err) {
      showToast('error', err instanceof Error ? err.message : 'Failed to publish announcement.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleArchive = async (id: string) => {
    try {
      await updateAnnouncementStatus(id, 'ARCHIVED');
      showToast('success', 'Announcement archived.');
    } catch (err) {
      showToast('error', err instanceof Error ? err.message : 'Failed to archive announcement.');
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await deleteAnnouncement(id);
      showToast('success', 'Announcement deleted.');
    } catch (err) {
      showToast('error', err instanceof Error ? err.message : 'Failed to delete announcement.');
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
            <Bell className="w-5 h-5 text-blue-600" />
            <span>Announcements</span>
          </h2>
          <p className="text-xs text-slate-500 mt-1">
            Broadcast campus transit alerts, timing updates, and emergency notices via FCM push notifications.
          </p>
        </div>
        <button
          onClick={() => setIsCreateOpen(true)}
          className="inline-flex items-center gap-2 px-4 py-2.5 bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold rounded-xl shadow-sm transition-colors"
        >
          <Plus className="w-4 h-4" />
          <span>Publish Announcement</span>
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
            placeholder="Search announcements..."
            className="w-full pl-9 pr-3 py-2 text-xs border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <div className="flex items-center gap-2">
          {(['ALL', 'PUBLISHED', 'ARCHIVED', 'DRAFT'] as const).map((status) => (
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

      {errorMessage && <ErrorState title="Announcements Error" message={errorMessage} />}

      {isLoading ? (
        <LoadingSpinner label="Loading announcements..." />
      ) : filtered.length === 0 ? (
        <EmptyState
          title="No announcements found"
          description="Publish your first campus transit announcement using the button above."
          icon={Bell}
        />
      ) : (
        <div className="grid grid-cols-1 gap-4">
          {filtered.map((a) => (
            <div
              key={a.id}
              className="bg-white rounded-2xl border border-slate-200 p-5 flex flex-col sm:flex-row sm:items-start justify-between gap-4"
            >
              <div className="space-y-1.5">
                <div className="flex flex-wrap items-center gap-2">
                  <span
                    className={`px-2 py-0.5 rounded-full text-[10px] font-bold uppercase ${
                      a.priority === 'URGENT'
                        ? 'bg-red-100 text-red-700'
                        : a.priority === 'IMPORTANT'
                        ? 'bg-amber-100 text-amber-800'
                        : 'bg-blue-100 text-blue-700'
                    }`}
                  >
                    {a.priority}
                  </span>
                  <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-slate-100 text-slate-700">
                    {a.category}
                  </span>
                  <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-slate-100 text-slate-600">
                    Audience: {a.targetAudience}
                    {a.targetId ? ` (${a.targetId})` : ''}
                  </span>
                  <span
                    className={`px-2 py-0.5 rounded-full text-[10px] font-semibold ${
                      a.status === 'PUBLISHED'
                        ? 'bg-emerald-100 text-emerald-700'
                        : 'bg-slate-200 text-slate-600'
                    }`}
                  >
                    {a.status}
                  </span>
                </div>
                <h3 className="text-sm font-bold text-slate-900">{a.titleEn}</h3>
                <p className="text-xs text-slate-600 whitespace-pre-line">{a.messageEn}</p>
                {a.titleKn && (
                  <p className="text-xs text-slate-500 pt-1">
                    <strong>KN:</strong> {a.titleKn} — {a.messageKn}
                  </p>
                )}
                <p className="text-[11px] text-slate-400 pt-1">
                  By {a.createdByName} • {new Date(a.createdAt).toLocaleString()}
                </p>
              </div>
              <div className="flex items-center gap-2 self-end sm:self-start">
                {a.status === 'PUBLISHED' && (
                  <button
                    onClick={() => handleArchive(a.id)}
                    className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-semibold"
                  >
                    <Archive className="w-3.5 h-3.5" />
                    <span>Archive</span>
                  </button>
                )}
                <button
                  onClick={() => handleDelete(a.id)}
                  className="p-2 rounded-lg text-red-600 hover:bg-red-50"
                  title="Delete announcement"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Create Modal */}
      {isCreateOpen && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-xl w-full p-6 shadow-xl border border-slate-200 max-h-[90vh] overflow-y-auto">
            <h3 className="text-base font-bold text-slate-900">Publish Announcement</h3>
            <p className="text-xs text-slate-500 mt-1">
              Publishing triggers `onAnnouncementWritten` to send FCM notifications to targeted devices.
            </p>

            <form onSubmit={handleCreate} className="mt-4 space-y-3 text-xs">
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div>
                  <label className="block font-semibold text-slate-700 mb-1">Category</label>
                  <select
                    value={category}
                    onChange={(e) => setCategory(e.target.value as AnnouncementCategory)}
                    className="w-full border border-slate-200 rounded-xl px-3 py-2"
                  >
                    {CATEGORIES.map((c) => (
                      <option key={c} value={c}>
                        {c}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block font-semibold text-slate-700 mb-1">Priority</label>
                  <select
                    value={priority}
                    onChange={(e) => setPriority(e.target.value as AnnouncementPriority)}
                    className="w-full border border-slate-200 rounded-xl px-3 py-2"
                  >
                    <option value="NORMAL">NORMAL</option>
                    <option value="IMPORTANT">IMPORTANT</option>
                    <option value="URGENT">URGENT</option>
                  </select>
                </div>
                <div>
                  <label className="block font-semibold text-slate-700 mb-1">Audience</label>
                  <select
                    value={targetAudience}
                    onChange={(e) => {
                      setTargetAudience(e.target.value as AnnouncementAudience);
                      setTargetId('');
                    }}
                    className="w-full border border-slate-200 rounded-xl px-3 py-2"
                  >
                    <option value="ALL">All Users</option>
                    <option value="STUDENTS_ONLY">Students Only</option>
                    <option value="DRIVERS_ONLY">Drivers Only</option>
                    <option value="SELECTED_BUS">Specific Bus</option>
                    <option value="SELECTED_ROUTE">Specific Route</option>
                  </select>
                </div>
              </div>

              {targetAudience === 'SELECTED_BUS' && (
                <div>
                  <label className="block font-semibold text-slate-700 mb-1">Target Bus</label>
                  <select
                    required
                    value={targetId}
                    onChange={(e) => setTargetId(e.target.value)}
                    className="w-full border border-slate-200 rounded-xl px-3 py-2"
                  >
                    <option value="">Select bus...</option>
                    {buses.map((b) => (
                      <option key={b.busId} value={b.busId}>
                        {b.busNumber || b.busId}
                      </option>
                    ))}
                  </select>
                </div>
              )}

              {targetAudience === 'SELECTED_ROUTE' && (
                <div>
                  <label className="block font-semibold text-slate-700 mb-1">Target Route</label>
                  <select
                    required
                    value={targetId}
                    onChange={(e) => setTargetId(e.target.value)}
                    className="w-full border border-slate-200 rounded-xl px-3 py-2"
                  >
                    <option value="">Select route...</option>
                    {routes.map((r) => (
                      <option key={r.routeId} value={r.routeId}>
                        {r.name} ({r.routeId})
                      </option>
                    ))}
                  </select>
                </div>
              )}

              <div>
                <label className="block font-semibold text-slate-700 mb-1">Title (English)</label>
                <input
                  type="text"
                  required
                  value={titleEn}
                  onChange={(e) => setTitleEn(e.target.value)}
                  placeholder="e.g., Morning Bus Schedule Update"
                  className="w-full border border-slate-200 rounded-xl px-3 py-2"
                />
              </div>

              <div>
                <label className="block font-semibold text-slate-700 mb-1">Message (English)</label>
                <textarea
                  rows={3}
                  required
                  value={messageEn}
                  onChange={(e) => setMessageEn(e.target.value)}
                  placeholder="Write the announcement details..."
                  className="w-full border border-slate-200 rounded-xl px-3 py-2"
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
                <div>
                  <label className="block font-semibold text-slate-600 mb-1">
                    Title (Kannada - Optional)
                  </label>
                  <input
                    type="text"
                    value={titleKn}
                    onChange={(e) => setTitleKn(e.target.value)}
                    className="w-full border border-slate-200 rounded-xl px-3 py-2"
                  />
                </div>
                <div>
                  <label className="block font-semibold text-slate-600 mb-1">
                    Message (Kannada - Optional)
                  </label>
                  <input
                    type="text"
                    value={messageKn}
                    onChange={(e) => setMessageKn(e.target.value)}
                    className="w-full border border-slate-200 rounded-xl px-3 py-2"
                  />
                </div>
              </div>

              <div className="flex justify-end gap-2 pt-3">
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
                  className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-blue-600 hover:bg-blue-700 text-white font-semibold disabled:opacity-50"
                >
                  <Send className="w-3.5 h-3.5" />
                  <span>{isSubmitting ? 'Publishing...' : 'Publish'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default Announcements;

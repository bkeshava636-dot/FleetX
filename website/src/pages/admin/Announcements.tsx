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
  Clock,
  Edit3,
  FileText,
  Calendar,
} from 'lucide-react';
import { useAdminAuth } from '../../context/AdminAuthContext';
import { subscribeToBuses } from '../../services/fleetService';
import { subscribeToRoutes } from '../../services/routesService';
import {
  buildBusTimingAnnouncementContent,
  createAnnouncement,
  deleteAnnouncement,
  ExtendedAnnouncementRecord,
  publishAnnouncement,
  subscribeToAnnouncements,
  updateAnnouncement,
  updateAnnouncementStatus,
  validateAnnouncementInput,
  validateTimingChangeInput,
} from '../../services/operationsService';
import { BusRecord } from '../../types/fleet';
import { Route } from '../../types/admin';
import {
  AnnouncementAudience,
  AnnouncementCategory,
  AnnouncementPriority,
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

const TIMING_CHANGE_TYPES = [
  'Morning departure time',
  'Evening departure time',
  'Mid-day departure time',
  'Full day schedule delay',
];

const TIMING_REASON_PRESETS = [
  'College examination schedule',
  'Road maintenance / Traffic diversion',
  'Inclement weather',
  'Special event / College function',
];

function toDatetimeLocalValue(ms?: number | null): string {
  if (!ms || !Number.isFinite(ms)) return '';
  const d = new Date(ms);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(
    d.getHours()
  )}:${pad(d.getMinutes())}`;
}

function defaultTodayFormatted(): string {
  return new Date().toLocaleDateString('en-GB', {
    day: '2-digit',
    month: 'long',
    year: 'numeric',
  });
}

export const Announcements: React.FC = () => {
  const { profile } = useAdminAuth();
  const [announcements, setAnnouncements] = useState<ExtendedAnnouncementRecord[]>([]);
  const [buses, setBuses] = useState<BusRecord[]>([]);
  const [routes, setRoutes] = useState<Route[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const [statusFilter, setStatusFilter] = useState<'ALL' | AnnouncementStatus>('ALL');
  const [searchQuery, setSearchQuery] = useState('');

  // Modal state (create or edit)
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingAnnouncement, setEditingAnnouncement] =
    useState<ExtendedAnnouncementRecord | null>(null);
  const [composeMode, setComposeMode] = useState<'STANDARD' | 'TIMING_CHANGE'>('STANDARD');

  // Standard form fields
  const [titleEn, setTitleEn] = useState('');
  const [messageEn, setMessageEn] = useState('');
  const [titleKn, setTitleKn] = useState('');
  const [messageKn, setMessageKn] = useState('');
  const [category, setCategory] = useState<AnnouncementCategory>('GENERAL');
  const [priority, setPriority] = useState<AnnouncementPriority>('NORMAL');
  const [targetAudience, setTargetAudience] = useState<AnnouncementAudience>('ALL');
  const [targetId, setTargetId] = useState('');
  const [expiresAtInput, setExpiresAtInput] = useState('');

  // Structured Bus Timing Change fields (matching BusTimingChangeDialog.kt)
  const [timingBusId, setTimingBusId] = useState('ALL');
  const [timingChangeType, setTimingChangeType] = useState(TIMING_CHANGE_TYPES[0]);
  const [previousTime, setPreviousTime] = useState('07:00');
  const [newTime, setNewTime] = useState('07:30');
  const [effectiveDate, setEffectiveDate] = useState(defaultTodayFormatted);
  const [endDate, setEndDate] = useState('');
  const [changeReason, setChangeReason] = useState(TIMING_REASON_PRESETS[0]);

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [toast, setToast] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  // Confirmation dialogs
  const [confirmPublishModal, setConfirmPublishModal] = useState<{
    source: 'FORM' | 'DRAFT_CARD';
    draftAnnouncement?: ExtendedAnnouncementRecord;
    title: string;
    audienceLabel: string;
  } | null>(null);
  const [confirmDeleteTarget, setConfirmDeleteTarget] =
    useState<ExtendedAnnouncementRecord | null>(null);

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
        a.category.toLowerCase().includes(q) ||
        (a.timingBusId || '').toLowerCase().includes(q)
      );
    });
  }, [announcements, statusFilter, searchQuery]);

  const timingPreview = useMemo(() => {
    return buildBusTimingAnnouncementContent({
      timingBusId,
      timingChangeType,
      previousTime: previousTime || '07:00',
      newTime: newTime || '07:30',
      effectiveDate: effectiveDate || defaultTodayFormatted(),
      endDate,
      changeReason: changeReason || 'Schedule update',
    });
  }, [
    timingBusId,
    timingChangeType,
    previousTime,
    newTime,
    effectiveDate,
    endDate,
    changeReason,
  ]);

  const resetForm = (mode: 'STANDARD' | 'TIMING_CHANGE' = 'STANDARD') => {
    setEditingAnnouncement(null);
    setComposeMode(mode);
    setTitleEn('');
    setMessageEn('');
    setTitleKn('');
    setMessageKn('');
    setCategory(mode === 'TIMING_CHANGE' ? 'TIMING_CHANGE' : 'GENERAL');
    setPriority(mode === 'TIMING_CHANGE' ? 'IMPORTANT' : 'NORMAL');
    setTargetAudience('ALL');
    setTargetId('');
    setExpiresAtInput('');
    setTimingBusId('ALL');
    setTimingChangeType(TIMING_CHANGE_TYPES[0]);
    setPreviousTime('07:00');
    setNewTime('07:30');
    setEffectiveDate(defaultTodayFormatted());
    setEndDate('');
    setChangeReason(TIMING_REASON_PRESETS[0]);
  };

  const openCreateModal = (mode: 'STANDARD' | 'TIMING_CHANGE' = 'STANDARD') => {
    resetForm(mode);
    setIsModalOpen(true);
  };

  const openEditModal = (a: ExtendedAnnouncementRecord) => {
    setEditingAnnouncement(a);
    const isTiming = a.category === 'TIMING_CHANGE' && Boolean(a.timingChangeType);
    setComposeMode(isTiming ? 'TIMING_CHANGE' : 'STANDARD');
    setTitleEn(a.titleEn);
    setMessageEn(a.messageEn);
    setTitleKn(a.titleKn || '');
    setMessageKn(a.messageKn || '');
    setCategory(a.category);
    setPriority(a.priority);
    setTargetAudience(a.targetAudience);
    setTargetId(a.targetId || '');
    setExpiresAtInput(toDatetimeLocalValue(a.expiresAt));
    setTimingBusId(a.timingBusId || (a.targetAudience === 'SELECTED_BUS' && a.targetId ? a.targetId : 'ALL'));
    setTimingChangeType(a.timingChangeType || TIMING_CHANGE_TYPES[0]);
    setPreviousTime(a.previousTime || '07:00');
    setNewTime(a.newTime || '07:30');
    setEffectiveDate(a.effectiveDate || defaultTodayFormatted());
    setEndDate(a.endDate || '');
    setChangeReason(a.changeReason || TIMING_REASON_PRESETS[0]);
    setIsModalOpen(true);
  };

  const parseExpiresAt = (): { value: number | null; error: string | null } => {
    if (!expiresAtInput.trim()) return { value: null, error: null };
    const parsed = Date.parse(expiresAtInput);
    if (!Number.isFinite(parsed)) {
      return { value: null, error: 'Invalid expiry date format.' };
    }
    if (parsed <= Date.now()) {
      return { value: null, error: 'Expiry date must be in the future.' };
    }
    return { value: parsed, error: null };
  };

  const buildPayloadFromForm = (targetStatus: AnnouncementStatus) => {
    const { value: expiresAt, error: expiryError } = parseExpiresAt();
    if (expiryError) {
      return { error: expiryError, payload: null };
    }

    if (composeMode === 'TIMING_CHANGE') {
      const timingErr = validateTimingChangeInput({
        previousTime,
        newTime,
        effectiveDate,
        changeReason,
      });
      if (timingErr) {
        return { error: timingErr, payload: null };
      }
      const isAll = !timingBusId || timingBusId === 'ALL';
      const busObj = buses.find((b) => b.busId === timingBusId);
      const effectiveAudience: AnnouncementAudience = isAll ? 'ALL' : 'SELECTED_BUS';
      const effectiveTargetId = isAll ? '' : timingBusId;
      const effectiveTargetName = isAll
        ? 'All Buses'
        : busObj?.busNumber || timingBusId;

      const resolvedExpiresAt =
        expiresAt !== null
          ? expiresAt
          : Date.now() + 7 * 86400 * 1000;

      const payload = {
        titleEn: timingPreview.titleEn,
        messageEn: timingPreview.messageEn,
        titleKn: timingPreview.titleKn,
        messageKn: timingPreview.messageKn,
        category: 'TIMING_CHANGE' as AnnouncementCategory,
        priority,
        targetAudience: effectiveAudience,
        targetId: effectiveTargetId,
        targetName: effectiveTargetName,
        status: targetStatus,
        expiresAt: resolvedExpiresAt,
        timingBusId: isAll ? 'ALL' : timingBusId,
        timingChangeType,
        previousTime: previousTime.trim(),
        newTime: newTime.trim(),
        effectiveDate: effectiveDate.trim(),
        endDate: endDate.trim() || null,
        changeReason: changeReason.trim(),
      };
      return { error: null, payload };
    }

    const validationError = validateAnnouncementInput({
      titleEn,
      messageEn,
      expiresAt,
      targetAudience,
      targetId,
    });
    if (validationError) {
      return { error: validationError, payload: null };
    }

    const payload = {
      titleEn,
      messageEn,
      titleKn,
      messageKn,
      category,
      priority,
      targetAudience,
      targetId,
      status: targetStatus,
      expiresAt,
    };
    return { error: null, payload };
  };

  const executeSaveForm = async (targetStatus: AnnouncementStatus) => {
    const { error, payload } = buildPayloadFromForm(targetStatus);
    if (error || !payload) {
      showToast('error', error || 'Invalid announcement input.');
      return;
    }

    setIsSubmitting(true);
    try {
      if (editingAnnouncement) {
        await updateAnnouncement(editingAnnouncement.id, payload);
        showToast(
          'success',
          targetStatus === 'PUBLISHED'
            ? 'Announcement updated and published.'
            : targetStatus === 'DRAFT'
            ? 'Draft updated.'
            : 'Announcement updated.'
        );
      } else {
        await createAnnouncement({
          ...payload,
          createdBy: profile?.uid || '',
          createdByName: profile?.name || profile?.email || 'Admin',
        });
        showToast(
          'success',
          targetStatus === 'PUBLISHED'
            ? 'Announcement published and push notifications queued.'
            : 'Announcement saved as draft.'
        );
      }
      setIsModalOpen(false);
      resetForm();
    } catch (err) {
      showToast('error', err instanceof Error ? err.message : 'Failed to save announcement.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleRequestPublishFromForm = (e: React.FormEvent) => {
    e.preventDefault();
    const { error, payload } = buildPayloadFromForm('PUBLISHED');
    if (error || !payload) {
      showToast('error', error || 'Invalid announcement input.');
      return;
    }
    setConfirmPublishModal({
      source: 'FORM',
      title: payload.titleEn,
      audienceLabel:
        payload.targetAudience + (payload.targetId ? ` (${payload.targetId})` : ''),
    });
  };

  const handleConfirmPublish = async () => {
    if (!confirmPublishModal) return;
    const modalData = confirmPublishModal;
    setConfirmPublishModal(null);

    if (modalData.source === 'FORM') {
      await executeSaveForm('PUBLISHED');
    } else if (modalData.source === 'DRAFT_CARD' && modalData.draftAnnouncement) {
      setIsSubmitting(true);
      try {
        await publishAnnouncement(modalData.draftAnnouncement.id);
        showToast('success', 'Draft published and push notifications queued.');
      } catch (err) {
        showToast('error', err instanceof Error ? err.message : 'Failed to publish draft.');
      } finally {
        setIsSubmitting(false);
      }
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

  const handleConfirmDelete = async () => {
    if (!confirmDeleteTarget) return;
    const target = confirmDeleteTarget;
    setConfirmDeleteTarget(null);
    try {
      await deleteAnnouncement(target.id);
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
            Broadcast campus transit alerts, timing updates, drafts, and emergency notices via FCM push notifications.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={() => openCreateModal('TIMING_CHANGE')}
            className="inline-flex items-center gap-2 px-3.5 py-2.5 bg-amber-50 hover:bg-amber-100 text-amber-800 border border-amber-200 text-xs font-semibold rounded-xl transition-colors"
          >
            <Clock className="w-4 h-4" />
            <span>Bus Timing Alert</span>
          </button>
          <button
            onClick={() => openCreateModal('STANDARD')}
            className="inline-flex items-center gap-2 px-4 py-2.5 bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold rounded-xl shadow-sm transition-colors"
          >
            <Plus className="w-4 h-4" />
            <span>New Announcement</span>
          </button>
        </div>
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
          {(['ALL', 'PUBLISHED', 'DRAFT', 'ARCHIVED'] as const).map((status) => (
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
          description="Create a draft or publish your first campus transit announcement using the button above."
          icon={Bell}
        />
      ) : (
        <div className="grid grid-cols-1 gap-4">
          {filtered.map((a) => {
            const isExpired =
              a.expiresAt != null && Number.isFinite(a.expiresAt) && a.expiresAt <= Date.now();
            return (
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
                          : a.status === 'DRAFT'
                          ? 'bg-amber-100 text-amber-800'
                          : 'bg-slate-200 text-slate-600'
                      }`}
                    >
                      {a.status}
                    </span>
                    {a.expiresAt && (
                      <span
                        className={`px-2 py-0.5 rounded-full text-[10px] font-semibold inline-flex items-center gap-1 ${
                          isExpired
                            ? 'bg-red-50 text-red-700'
                            : 'bg-slate-100 text-slate-600'
                        }`}
                      >
                        <Calendar className="w-3 h-3" />
                        {isExpired ? 'Expired: ' : 'Expires: '}
                        {new Date(a.expiresAt).toLocaleString()}
                      </span>
                    )}
                  </div>
                  <h3 className="text-sm font-bold text-slate-900">{a.titleEn}</h3>
                  <p className="text-xs text-slate-600 whitespace-pre-line">{a.messageEn}</p>
                  {a.timingChangeType && (
                    <div className="mt-1.5 inline-flex flex-wrap items-center gap-2 px-2.5 py-1 rounded-lg bg-amber-50 border border-amber-200 text-[11px] text-amber-900">
                      <Clock className="w-3.5 h-3.5 text-amber-700" />
                      <span>
                        <strong>{a.timingBusId === 'ALL' ? 'All Buses' : `Bus ${a.timingBusId}`}</strong>
                        {' • '}
                        {a.timingChangeType}: {a.previousTime} → {a.newTime}
                        {a.effectiveDate ? ` (From ${a.effectiveDate})` : ''}
                      </span>
                    </div>
                  )}
                  {a.titleKn && (
                    <p className="text-xs text-slate-500 pt-1">
                      <strong>KN:</strong> {a.titleKn} — {a.messageKn}
                    </p>
                  )}
                  <p className="text-[11px] text-slate-400 pt-1">
                    By {a.createdByName} • {new Date(a.createdAt).toLocaleString()}
                  </p>
                </div>
                <div className="flex flex-wrap items-center gap-2 self-end sm:self-start">
                  {a.status === 'DRAFT' && (
                    <button
                      onClick={() =>
                        setConfirmPublishModal({
                          source: 'DRAFT_CARD',
                          draftAnnouncement: a,
                          title: a.titleEn,
                          audienceLabel:
                            a.targetAudience + (a.targetId ? ` (${a.targetId})` : ''),
                        })
                      }
                      className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold"
                    >
                      <Send className="w-3.5 h-3.5" />
                      <span>Publish</span>
                    </button>
                  )}
                  {a.status !== 'ARCHIVED' && (
                    <button
                      onClick={() => openEditModal(a)}
                      className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-semibold"
                    >
                      <Edit3 className="w-3.5 h-3.5" />
                      <span>Edit</span>
                    </button>
                  )}
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
                    onClick={() => setConfirmDeleteTarget(a)}
                    className="p-2 rounded-lg text-red-600 hover:bg-red-50"
                    title="Delete announcement"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Create / Edit Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-2xl w-full p-6 shadow-xl border border-slate-200 max-h-[90vh] overflow-y-auto">
            <div className="flex items-start justify-between gap-4">
              <div>
                <h3 className="text-base font-bold text-slate-900">
                  {editingAnnouncement ? 'Edit Announcement' : 'Create Announcement'}
                </h3>
                <p className="text-xs text-slate-500 mt-1">
                  Publishing triggers FCM notifications to targeted devices. Save as draft to review before sending.
                </p>
              </div>
              {!editingAnnouncement && (
                <div className="inline-flex rounded-xl bg-slate-100 p-1 text-xs font-semibold">
                  <button
                    type="button"
                    onClick={() => {
                      setComposeMode('STANDARD');
                      if (category === 'TIMING_CHANGE') setCategory('GENERAL');
                    }}
                    className={`px-3 py-1.5 rounded-lg ${
                      composeMode === 'STANDARD'
                        ? 'bg-white text-slate-900 shadow-sm'
                        : 'text-slate-600'
                    }`}
                  >
                    Standard
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setComposeMode('TIMING_CHANGE');
                      setCategory('TIMING_CHANGE');
                      setPriority('IMPORTANT');
                    }}
                    className={`px-3 py-1.5 rounded-lg inline-flex items-center gap-1 ${
                      composeMode === 'TIMING_CHANGE'
                        ? 'bg-white text-amber-800 shadow-sm'
                        : 'text-slate-600'
                    }`}
                  >
                    <Clock className="w-3.5 h-3.5" />
                    <span>Bus Timing Change</span>
                  </button>
                </div>
              )}
            </div>

            <form onSubmit={handleRequestPublishFromForm} className="mt-4 space-y-3 text-xs">
              {composeMode === 'TIMING_CHANGE' ? (
                <>
                  <div className="p-3 rounded-xl bg-amber-50 border border-amber-200 text-amber-900 text-[11px]">
                    Communicates structured bus timing adjustments to students and drivers without modifying the underlying operating schedule.
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div>
                      <label className="block font-semibold text-slate-700 mb-1">
                        Affected Bus
                      </label>
                      <select
                        value={timingBusId}
                        onChange={(e) => setTimingBusId(e.target.value)}
                        className="w-full border border-slate-200 rounded-xl px-3 py-2"
                      >
                        <option value="ALL">All Buses</option>
                        {buses.map((b) => (
                          <option key={b.busId} value={b.busId}>
                            {b.busNumber || b.busId} ({b.busId})
                          </option>
                        ))}
                      </select>
                    </div>
                    <div>
                      <label className="block font-semibold text-slate-700 mb-1">
                        Timing Change Type
                      </label>
                      <select
                        value={timingChangeType}
                        onChange={(e) => setTimingChangeType(e.target.value)}
                        className="w-full border border-slate-200 rounded-xl px-3 py-2"
                      >
                        {TIMING_CHANGE_TYPES.map((t) => (
                          <option key={t} value={t}>
                            {t}
                          </option>
                        ))}
                      </select>
                    </div>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div>
                      <label className="block font-semibold text-slate-700 mb-1">
                        Previous Time
                      </label>
                      <input
                        type="text"
                        required
                        value={previousTime}
                        onChange={(e) => setPreviousTime(e.target.value)}
                        placeholder="e.g., 07:00"
                        className="w-full border border-slate-200 rounded-xl px-3 py-2"
                      />
                    </div>
                    <div>
                      <label className="block font-semibold text-slate-700 mb-1">
                        New Time
                      </label>
                      <input
                        type="text"
                        required
                        value={newTime}
                        onChange={(e) => setNewTime(e.target.value)}
                        placeholder="e.g., 07:30"
                        className="w-full border border-slate-200 rounded-xl px-3 py-2"
                      />
                    </div>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div>
                      <label className="block font-semibold text-slate-700 mb-1">
                        Effective Date
                      </label>
                      <input
                        type="text"
                        required
                        value={effectiveDate}
                        onChange={(e) => setEffectiveDate(e.target.value)}
                        placeholder="e.g., 26 November 2026"
                        className="w-full border border-slate-200 rounded-xl px-3 py-2"
                      />
                    </div>
                    <div>
                      <label className="block font-semibold text-slate-700 mb-1">
                        End Date (Optional)
                      </label>
                      <input
                        type="text"
                        value={endDate}
                        onChange={(e) => setEndDate(e.target.value)}
                        placeholder="e.g., 30 November 2026"
                        className="w-full border border-slate-200 rounded-xl px-3 py-2"
                      />
                    </div>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div>
                      <label className="block font-semibold text-slate-700 mb-1">
                        Reason for Change
                      </label>
                      <input
                        type="text"
                        required
                        list="timing-reason-presets"
                        value={changeReason}
                        onChange={(e) => setChangeReason(e.target.value)}
                        className="w-full border border-slate-200 rounded-xl px-3 py-2"
                      />
                      <datalist id="timing-reason-presets">
                        {TIMING_REASON_PRESETS.map((r) => (
                          <option key={r} value={r} />
                        ))}
                      </datalist>
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
                  </div>

                  {/* Live Preview */}
                  <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 space-y-1">
                    <div className="text-[11px] font-bold text-blue-700 uppercase">
                      Bilingual Alert Preview
                    </div>
                    <div className="font-bold text-slate-900">{timingPreview.titleEn}</div>
                    <div className="text-slate-600">{timingPreview.messageEn}</div>
                    <div className="font-bold text-slate-900 pt-1">{timingPreview.titleKn}</div>
                    <div className="text-slate-600">{timingPreview.messageKn}</div>
                  </div>
                </>
              ) : (
                <>
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
                    <label className="block font-semibold text-slate-700 mb-1">
                      Title (English)
                    </label>
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
                    <label className="block font-semibold text-slate-700 mb-1">
                      Message (English)
                    </label>
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
                </>
              )}

              <div>
                <label className="block font-semibold text-slate-600 mb-1">
                  Expiry Date (Optional — must be in the future)
                </label>
                <input
                  type="datetime-local"
                  value={expiresAtInput}
                  onChange={(e) => setExpiresAtInput(e.target.value)}
                  className="w-full border border-slate-200 rounded-xl px-3 py-2"
                />
              </div>

              <div className="flex flex-wrap justify-end gap-2 pt-3">
                <button
                  type="button"
                  onClick={() => {
                    setIsModalOpen(false);
                    resetForm();
                  }}
                  className="px-4 py-2 rounded-xl border border-slate-200 text-slate-600 font-semibold hover:bg-slate-50"
                >
                  Cancel
                </button>
                {(!editingAnnouncement || editingAnnouncement.status === 'DRAFT') && (
                  <button
                    type="button"
                    disabled={isSubmitting}
                    onClick={() => executeSaveForm('DRAFT')}
                    className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 font-semibold disabled:opacity-50"
                  >
                    <FileText className="w-3.5 h-3.5" />
                    <span>Save as Draft</span>
                  </button>
                )}
                {editingAnnouncement && editingAnnouncement.status === 'PUBLISHED' ? (
                  <button
                    type="button"
                    disabled={isSubmitting}
                    onClick={() => executeSaveForm('PUBLISHED')}
                    className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-blue-600 hover:bg-blue-700 text-white font-semibold disabled:opacity-50"
                  >
                    <CheckCircle2 className="w-3.5 h-3.5" />
                    <span>{isSubmitting ? 'Saving...' : 'Save Changes'}</span>
                  </button>
                ) : (
                  <button
                    type="submit"
                    disabled={isSubmitting}
                    className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-blue-600 hover:bg-blue-700 text-white font-semibold disabled:opacity-50"
                  >
                    <Send className="w-3.5 h-3.5" />
                    <span>{isSubmitting ? 'Publishing...' : 'Publish'}</span>
                  </button>
                )}
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Publish Confirmation Modal */}
      {confirmPublishModal && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-xl border border-slate-200 space-y-4">
            <h3 className="text-base font-bold text-slate-900 flex items-center gap-2">
              <Send className="w-4 h-4 text-blue-600" />
              <span>Confirm Publish Announcement</span>
            </h3>
            <p className="text-xs text-slate-600">
              Are you sure you want to publish <strong>{confirmPublishModal.title}</strong> to{' '}
              <strong>{confirmPublishModal.audienceLabel}</strong>? This will immediately trigger
              FCM push notifications.
            </p>
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setConfirmPublishModal(null)}
                className="px-4 py-2 rounded-xl border border-slate-200 text-xs font-semibold text-slate-600 hover:bg-slate-50"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleConfirmPublish}
                className="px-4 py-2 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold"
              >
                Confirm & Publish
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Delete Confirmation Modal */}
      {confirmDeleteTarget && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-xl border border-slate-200 space-y-4">
            <h3 className="text-base font-bold text-slate-900 flex items-center gap-2">
              <Trash2 className="w-4 h-4 text-red-600" />
              <span>Delete Announcement</span>
            </h3>
            <p className="text-xs text-slate-600">
              Permanently delete <strong>{confirmDeleteTarget.titleEn}</strong>? This action cannot
              be undone.
            </p>
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setConfirmDeleteTarget(null)}
                className="px-4 py-2 rounded-xl border border-slate-200 text-xs font-semibold text-slate-600 hover:bg-slate-50"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleConfirmDelete}
                className="px-4 py-2 rounded-xl bg-red-600 hover:bg-red-700 text-white text-xs font-semibold"
              >
                Delete
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default Announcements;

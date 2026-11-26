import {
  collection,
  doc,
  onSnapshot,
  setDoc,
  updateDoc,
  deleteDoc,
  query,
  orderBy,
  limit,
  writeBatch,
  Unsubscribe,
} from 'firebase/firestore';
import { httpsCallable } from 'firebase/functions';
import { db, functions } from '../lib/firebase';
import { RouteSchedule } from '../types/admin';
import {
  AnnouncementRecord,
  AnnouncementStatus,
  BusSwapHistoryRecord,
  BusSwapRecord,
  BusSwapStatus,
  CalendarExceptionRecord,
  CreateAnnouncementInput,
  CreateBusSwapInput,
  CreateTemporaryAssignmentInput,
  DailyAnalyticsRecord,
  DriverReplacementHistoryRecord,
  HistoryAuditLogRecord,
  StudentFeedbackRecord,
  StudentFeedbackStatus,
  StudentInstallationRecord,
  TemporaryAssignmentRecord,
} from '../types/operations';

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

// ---------------------------------------------------------------------------
// 1. Bus Swaps
// ---------------------------------------------------------------------------

export function subscribeToBusSwaps(
  onData: (items: BusSwapRecord[]) => void,
  onError: (err: Error) => void
): Unsubscribe {
  const q = query(collection(db, 'bus_swaps'), orderBy('createdAt', 'desc'), limit(100));
  return onSnapshot(
    q,
    (snap) => {
      const items: BusSwapRecord[] = snap.docs.map((d) => {
        const data = d.data();
        return {
          swapId: data.swapId || d.id,
          originalBusId: data.originalBusId || '',
          replacementBusId: data.replacementBusId || '',
          routeId: data.routeId || '',
          driverId: data.driverId || '',
          driverName: data.driverName || '',
          scopeType: data.scopeType === 'SELECTED_STOPS' ? 'SELECTED_STOPS' : 'ALL_STOPS',
          affectedStopIds: Array.isArray(data.affectedStopIds) ? data.affectedStopIds : [],
          message: data.message || '',
          status: (data.status as BusSwapStatus) || 'ACTIVE',
          date: data.date || '',
          createdAt: Number(data.createdAt) || 0,
          updatedAt: Number(data.updatedAt) || 0,
          cancelledAt: Number(data.cancelledAt) || 0,
          cancelledByUid: data.cancelledByUid || '',
          createdByUid: data.createdByUid || '',
        };
      });
      onData(items);
    },
    (err) => onError(err as Error)
  );
}

export async function createBusSwap(input: CreateBusSwapInput): Promise<string> {
  const swapRef = doc(collection(db, 'bus_swaps'));
  const now = Date.now();
  const dateStr = todayIso();
  const payload = {
    swapId: swapRef.id,
    originalBusId: input.originalBusId,
    replacementBusId: input.replacementBusId,
    routeId: input.routeId,
    driverId: input.driverId,
    driverName: input.driverName,
    scopeType: input.scopeType,
    affectedStopIds: input.scopeType === 'SELECTED_STOPS' ? input.affectedStopIds : [],
    message: input.message.trim(),
    status: 'ACTIVE',
    date: dateStr,
    createdAt: now,
    updatedAt: now,
    createdByUid: input.createdByUid,
  };
  await setDoc(swapRef, payload);

  // Also record in bus_swap_history
  await setDoc(doc(db, 'bus_swap_history', swapRef.id), {
    historyId: swapRef.id,
    swapId: swapRef.id,
    originalBusId: input.originalBusId,
    replacementBusId: input.replacementBusId,
    routeId: input.routeId,
    date: dateStr,
    reason: input.message.trim(),
    changedByUid: input.createdByUid,
    changedByName: input.driverName || 'Admin',
    effectiveFrom: now,
    effectiveUntil: 0,
    status: 'ACTIVE',
    createdAt: now,
    cleared: false,
  });

  return swapRef.id;
}

export async function updateBusSwapStatus(
  swapId: string,
  status: BusSwapStatus,
  adminUid: string
): Promise<void> {
  const now = Date.now();
  const updates: Record<string, unknown> = {
    status,
    updatedAt: now,
  };
  if (status === 'CANCELLED') {
    updates.cancelledAt = now;
    updates.cancelledByUid = adminUid;
  }
  await updateDoc(doc(db, 'bus_swaps', swapId), updates);
  try {
    await updateDoc(doc(db, 'bus_swap_history', swapId), {
      status: status === 'RESOLVED' ? 'COMPLETED' : status,
      updatedAt: now,
      ...(status === 'CANCELLED' ? { cancelledAt: now, cancelledBy: adminUid } : {}),
    });
  } catch {
    // History doc may not exist for older swaps
  }
}

// ---------------------------------------------------------------------------
// 2. Temporary Assignments (via Cloud Functions)
// ---------------------------------------------------------------------------

export function subscribeToTemporaryAssignments(
  onData: (items: TemporaryAssignmentRecord[]) => void,
  onError: (err: Error) => void
): Unsubscribe {
  const q = query(
    collection(db, 'temporary_assignments'),
    orderBy('createdAt', 'desc'),
    limit(100)
  );
  return onSnapshot(
    q,
    (snap) => {
      const now = Date.now();
      const items: TemporaryAssignmentRecord[] = snap.docs.map((d) => {
        const data = d.data();
        const cancelledAt = data.cancelledAt != null ? Number(data.cancelledAt) : null;
        const endAt = Number(data.endAt) || 0;
        let status: TemporaryAssignmentRecord['status'] = 'ACTIVE';
        if (cancelledAt != null || data.status === 'CANCELLED') {
          status = 'CANCELLED';
        } else if (endAt > 0 && endAt <= now) {
          status = 'EXPIRED';
        }
        return {
          assignmentId: data.assignmentId || d.id,
          busId: data.busId || '',
          permanentDriverId: data.permanentDriverId || '',
          permanentDriverUid: data.permanentDriverUid || '',
          permanentDriverName: data.permanentDriverName || '',
          replacementDriverId: data.replacementDriverId || '',
          replacementDriverUid: data.replacementDriverUid || '',
          replacementDriverName: data.replacementDriverName || '',
          reason: data.reason || '',
          startAt: Number(data.startAt) || 0,
          endAt,
          createdByUid: data.createdByUid || '',
          createdByRole: data.createdByRole || '',
          createdAt: Number(data.createdAt) || 0,
          updatedAt: Number(data.updatedAt) || 0,
          cancelledAt,
          cancelledByUid: data.cancelledByUid || null,
          cancelledByRole: data.cancelledByRole || null,
          status,
        };
      });
      onData(items);
    },
    (err) => onError(err as Error)
  );
}

export async function createTemporaryAssignmentCallable(
  input: CreateTemporaryAssignmentInput
): Promise<{ success: boolean; assignment: TemporaryAssignmentRecord }> {
  const callable = httpsCallable<
    CreateTemporaryAssignmentInput,
    { success: boolean; assignment: TemporaryAssignmentRecord }
  >(functions, 'createTemporaryAssignment');
  const res = await callable(input);
  return res.data;
}

export async function cancelTemporaryAssignmentCallable(
  assignmentId: string
): Promise<{ success: boolean; assignmentId: string }> {
  const callable = httpsCallable<
    { assignmentId: string },
    { success: boolean; assignmentId: string }
  >(functions, 'cancelTemporaryAssignment');
  const res = await callable({ assignmentId });
  return res.data;
}

// ---------------------------------------------------------------------------
// 3. College Calendar & Operating Schedule
// ---------------------------------------------------------------------------

export function subscribeToCalendarExceptions(
  onData: (items: CalendarExceptionRecord[]) => void,
  onError: (err: Error) => void
): Unsubscribe {
  const q = query(collection(db, 'calendar_exceptions'), orderBy('date', 'desc'));
  return onSnapshot(
    q,
    (snap) => {
      const items: CalendarExceptionRecord[] = snap.docs.map((d) => {
        const data = d.data();
        return {
          date: data.date || d.id,
          type: data.type === 'WORKING_DAY' ? 'WORKING_DAY' : 'FULL_DAY_HOLIDAY',
          name: data.name || '',
          updatedAt: Number(data.updatedAt) || 0,
          updatedBy: data.updatedBy || '',
        };
      });
      onData(items);
    },
    (err) => onError(err as Error)
  );
}

export async function saveCalendarException(
  record: Omit<CalendarExceptionRecord, 'updatedAt'>
): Promise<void> {
  await setDoc(doc(db, 'calendar_exceptions', record.date), {
    date: record.date,
    type: record.type,
    name: record.name.trim(),
    updatedAt: Date.now(),
    updatedBy: record.updatedBy,
  });
}

export async function deleteCalendarException(dateStr: string): Promise<void> {
  await deleteDoc(doc(db, 'calendar_exceptions', dateStr));
}

export function subscribeToOperatingSchedule(
  onData: (schedule: RouteSchedule) => void,
  onError: (err: Error) => void
): Unsubscribe {
  return onSnapshot(
    doc(db, 'operating_schedule', 'default'),
    (snap) => {
      if (snap.exists()) {
        const data = snap.data();
        onData({
          morning: {
            startTime: data.morning?.startTime || '07:00',
            endTime: data.morning?.endTime || '09:00',
            enabled: data.morning?.enabled ?? true,
          },
          evening: {
            startTime: data.evening?.startTime || '16:00',
            endTime: data.evening?.endTime || '18:00',
            enabled: data.evening?.enabled ?? true,
          },
        });
      } else {
        onData({
          morning: { startTime: '07:00', endTime: '09:00', enabled: true },
          evening: { startTime: '16:00', endTime: '18:00', enabled: true },
        });
      }
    },
    (err) => onError(err as Error)
  );
}

export async function saveOperatingSchedule(
  schedule: RouteSchedule,
  updatedBy: string
): Promise<void> {
  await setDoc(
    doc(db, 'operating_schedule', 'default'),
    {
      ...schedule,
      updatedAt: Date.now(),
      updatedBy,
    },
    { merge: true }
  );
}

// ---------------------------------------------------------------------------
// 4. Announcements
// ---------------------------------------------------------------------------

export function subscribeToAnnouncements(
  onData: (items: AnnouncementRecord[]) => void,
  onError: (err: Error) => void
): Unsubscribe {
  const q = query(collection(db, 'announcements'), orderBy('createdAt', 'desc'), limit(100));
  return onSnapshot(
    q,
    (snap) => {
      const items: AnnouncementRecord[] = snap.docs.map((d) => {
        const data = d.data();
        return {
          id: data.id || d.id,
          titleEn: data.titleEn || data.title || '',
          messageEn: data.messageEn || data.contentEn || data.content || '',
          titleKn: data.titleKn || '',
          messageKn: data.messageKn || data.contentKn || '',
          category: data.category || 'GENERAL',
          priority: data.priority || 'NORMAL',
          targetAudience: data.targetAudience || 'ALL',
          targetId: data.targetId || '',
          targetName: data.targetName || '',
          targetBusId: data.targetBusId || '',
          targetRouteId: data.targetRouteId || '',
          status: data.status || 'PUBLISHED',
          createdBy: data.createdBy || '',
          createdByName: data.createdByName || 'Admin',
          createdAt: Number(data.createdAt) || 0,
          updatedAt: Number(data.updatedAt) || 0,
          expiresAt: data.expiresAt != null ? Number(data.expiresAt) : null,
          notified: Boolean(data.notified),
        };
      });
      onData(items);
    },
    (err) => onError(err as Error)
  );
}

export async function createAnnouncement(input: CreateAnnouncementInput): Promise<string> {
  const ref = doc(collection(db, 'announcements'));
  const now = Date.now();
  const payload: Record<string, unknown> = {
    id: ref.id,
    titleEn: input.titleEn.trim(),
    messageEn: input.messageEn.trim(),
    titleKn: (input.titleKn || '').trim(),
    messageKn: (input.messageKn || '').trim(),
    category: input.category,
    priority: input.priority,
    targetAudience: input.targetAudience,
    targetId: input.targetId || '',
    targetName: input.targetName || '',
    targetBusId: input.targetAudience === 'SELECTED_BUS' ? input.targetId || '' : '',
    targetRouteId: input.targetAudience === 'SELECTED_ROUTE' ? input.targetId || '' : '',
    status: input.status,
    createdBy: input.createdBy,
    createdByName: input.createdByName,
    createdAt: now,
    updatedAt: now,
    expiresAt: input.expiresAt ?? null,
    notified: false,
  };
  await setDoc(ref, payload);
  return ref.id;
}

export async function updateAnnouncementStatus(
  id: string,
  status: AnnouncementStatus
): Promise<void> {
  await updateDoc(doc(db, 'announcements', id), {
    status,
    updatedAt: Date.now(),
  });
}

export async function deleteAnnouncement(id: string): Promise<void> {
  await deleteDoc(doc(db, 'announcements', id));
}

// ---------------------------------------------------------------------------
// 5. Student Feedback & Installations
// ---------------------------------------------------------------------------

export function subscribeToStudentFeedback(
  onData: (items: StudentFeedbackRecord[]) => void,
  onError: (err: Error) => void
): Unsubscribe {
  const q = query(collection(db, 'student_feedback'), orderBy('timestamp', 'desc'), limit(150));
  return onSnapshot(
    q,
    (snap) => {
      const items: StudentFeedbackRecord[] = snap.docs.map((d) => {
        const data = d.data();
        return {
          feedbackId: data.feedbackId || d.id,
          feedbackType: data.feedbackType === 'APP' ? 'APP' : 'BUS',
          busId: data.busId || '',
          routeId: data.routeId || '',
          category: data.category || 'General Feedback',
          subject: data.subject || '',
          description: data.description || '',
          studentName: data.studentName || 'Anonymous',
          studentContact: data.studentContact || '',
          timestamp: Number(data.timestamp) || 0,
          status: (data.status as StudentFeedbackStatus) || 'NEW',
        };
      });
      onData(items);
    },
    (err) => onError(err as Error)
  );
}

export async function updateStudentFeedbackStatus(
  feedbackId: string,
  status: StudentFeedbackStatus
): Promise<void> {
  await updateDoc(doc(db, 'student_feedback', feedbackId), { status });
}

export async function deleteStudentFeedback(feedbackId: string): Promise<void> {
  await deleteDoc(doc(db, 'student_feedback', feedbackId));
}

export function subscribeToStudentInstallations(
  onData: (items: StudentInstallationRecord[]) => void,
  onError: (err: Error) => void
): Unsubscribe {
  const q = query(collection(db, 'student_installations'), orderBy('updatedAt', 'desc'), limit(500));
  return onSnapshot(
    q,
    (snap) => {
      const items: StudentInstallationRecord[] = snap.docs.map((d) => {
        const data = d.data();
        return {
          installationId: data.installationId || d.id,
          appVersion: data.appVersion || 'unknown',
          language: data.language || 'en',
          lastActiveDate: data.lastActiveDate || '',
          updatedAt: Number(data.updatedAt) || 0,
        };
      });
      onData(items);
    },
    (err) => onError(err as Error)
  );
}

// ---------------------------------------------------------------------------
// 6. Analytics & Reports
// ---------------------------------------------------------------------------

export function subscribeToDailyAnalytics(
  onData: (items: DailyAnalyticsRecord[]) => void,
  onError: (err: Error) => void
): Unsubscribe {
  const q = query(collection(db, 'student_analytics_daily'), orderBy('date', 'desc'), limit(30));
  return onSnapshot(
    q,
    (snap) => {
      const items: DailyAnalyticsRecord[] = snap.docs.map((d) => {
        const data = d.data();
        return {
          date: data.date || d.id,
          totalEvents: Number(data.totalEvents) || 0,
          dauCount: Number(data.dauCount) || 0,
          newInstallations: Number(data.newInstallations) || 0,
          returningUsers: Number(data.returningUsers) || 0,
          events: (data.events as Record<string, number>) || {},
          languages: (data.languages as Record<string, number>) || {},
          versions: (data.versions as Record<string, number>) || {},
          updatedAt: Number(data.updatedAt) || 0,
        };
      });
      onData(items);
    },
    (err) => onError(err as Error)
  );
}

// ---------------------------------------------------------------------------
// 7. Audit Logs & History
// ---------------------------------------------------------------------------

export function subscribeToHistoryAuditLogs(
  onData: (items: HistoryAuditLogRecord[]) => void,
  onError: (err: Error) => void
): Unsubscribe {
  const q = query(collection(db, 'history_audit_logs'), orderBy('timestamp', 'desc'), limit(100));
  return onSnapshot(
    q,
    (snap) => {
      const items: HistoryAuditLogRecord[] = snap.docs.map((d) => {
        const data = d.data();
        return {
          auditId: data.auditId || d.id,
          action: data.action || '',
          adminUid: data.adminUid || '',
          adminName: data.adminName || 'Admin',
          timestamp: Number(data.timestamp) || 0,
          dateFormatted: data.dateFormatted || '',
          timeFormatted: data.timeFormatted || '',
          clearedCount: Number(data.clearedCount) || 0,
        };
      });
      onData(items);
    },
    (err) => onError(err as Error)
  );
}

export function subscribeToBusSwapHistory(
  onData: (items: BusSwapHistoryRecord[]) => void,
  onError: (err: Error) => void
): Unsubscribe {
  const q = query(collection(db, 'bus_swap_history'), orderBy('createdAt', 'desc'), limit(150));
  return onSnapshot(
    q,
    (snap) => {
      const items: BusSwapHistoryRecord[] = snap.docs.map((d) => {
        const data = d.data();
        return {
          historyId: data.historyId || d.id,
          swapId: data.swapId || '',
          originalBusId: data.originalBusId || '',
          replacementBusId: data.replacementBusId || '',
          routeId: data.routeId || '',
          date: data.date || '',
          reason: data.reason || '',
          changedByUid: data.changedByUid || '',
          changedByName: data.changedByName || '',
          status: data.status || 'ACTIVE',
          createdAt: Number(data.createdAt) || 0,
          cleared: Boolean(data.cleared),
        };
      });
      onData(items);
    },
    (err) => onError(err as Error)
  );
}

export function subscribeToDriverReplacementHistory(
  onData: (items: DriverReplacementHistoryRecord[]) => void,
  onError: (err: Error) => void
): Unsubscribe {
  const q = query(
    collection(db, 'driver_replacement_history'),
    orderBy('createdAt', 'desc'),
    limit(150)
  );
  return onSnapshot(
    q,
    (snap) => {
      const items: DriverReplacementHistoryRecord[] = snap.docs.map((d) => {
        const data = d.data();
        return {
          historyId: data.historyId || d.id,
          assignmentId: data.assignmentId || '',
          previousDriverUid: data.previousDriverUid || '',
          previousDriverName: data.previousDriverName || '',
          replacementDriverUid: data.replacementDriverUid || '',
          replacementDriverName: data.replacementDriverName || '',
          busId: data.busId || '',
          reason: data.reason || '',
          startAt: Number(data.startAt) || 0,
          endAt: Number(data.endAt) || 0,
          changedByUid: data.changedByUid || '',
          changedByName: data.changedByName || '',
          status: data.status || 'ACTIVE',
          createdAt: Number(data.createdAt) || 0,
          cleared: Boolean(data.cleared),
        };
      });
      onData(items);
    },
    (err) => onError(err as Error)
  );
}

export async function clearHistoryWithAudit(
  type: 'CLEAR_BUS_SWAP_HISTORY' | 'CLEAR_DRIVER_REPLACEMENT_HISTORY',
  historyIds: string[],
  adminUid: string,
  adminName: string
): Promise<void> {
  if (historyIds.length === 0) return;
  const colName =
    type === 'CLEAR_BUS_SWAP_HISTORY' ? 'bus_swap_history' : 'driver_replacement_history';

  const batch = writeBatch(db);
  historyIds.forEach((id) => {
    batch.update(doc(db, colName, id), { cleared: true });
  });

  const now = new Date();
  const auditRef = doc(collection(db, 'history_audit_logs'));
  batch.set(auditRef, {
    auditId: auditRef.id,
    action: type,
    adminUid,
    adminName,
    timestamp: now.getTime(),
    dateFormatted: now.toISOString().slice(0, 10),
    timeFormatted: now.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' }),
    clearedCount: historyIds.length,
  });

  await batch.commit();
}

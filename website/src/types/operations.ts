/**
 * Domain types for Phase 5–7 FleetX Admin Console modules:
 * Bus Swaps, Temporary Assignments, College Calendar, Announcements,
 * Student Feedback & Installations, Analytics, and Audit Logs.
 */

export type BusSwapStatus = 'ACTIVE' | 'RESOLVED' | 'CANCELLED';
export type BusSwapScope = 'ALL_STOPS' | 'SELECTED_STOPS';

export interface BusSwapRecord {
  swapId: string;
  originalBusId: string;
  replacementBusId: string;
  routeId: string;
  driverId: string;
  driverName: string;
  scopeType: BusSwapScope;
  affectedStopIds: string[];
  message: string;
  status: BusSwapStatus;
  date: string;
  createdAt: number;
  updatedAt?: number;
  cancelledAt?: number;
  cancelledByUid?: string;
  createdByUid?: string;
}

export interface CreateBusSwapInput {
  originalBusId: string;
  replacementBusId: string;
  routeId: string;
  driverId: string;
  driverName: string;
  scopeType: BusSwapScope;
  affectedStopIds: string[];
  message: string;
  createdByUid: string;
}

export interface TemporaryAssignmentRecord {
  assignmentId: string;
  busId: string;
  permanentDriverId: string;
  permanentDriverUid: string;
  permanentDriverName: string;
  replacementDriverId: string;
  replacementDriverUid: string;
  replacementDriverName: string;
  reason: string;
  startAt: number;
  endAt: number;
  createdByUid: string;
  createdByRole: string;
  createdAt: number;
  updatedAt: number;
  cancelledAt: number | null;
  cancelledByUid: string | null;
  cancelledByRole: string | null;
  status: 'ACTIVE' | 'CANCELLED' | 'EXPIRED';
}

export interface CreateTemporaryAssignmentInput {
  busId: string;
  replacementDriverUid: string;
  startAt: number;
  endAt: number;
  reason?: string;
}

export type CalendarExceptionType = 'FULL_DAY_HOLIDAY' | 'WORKING_DAY';

export interface CalendarExceptionRecord {
  date: string; // YYYY-MM-DD
  type: CalendarExceptionType;
  name: string;
  updatedAt: number;
  updatedBy: string;
}

export type AnnouncementStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
export type AnnouncementPriority = 'NORMAL' | 'IMPORTANT' | 'URGENT';
export type AnnouncementCategory =
  | 'GENERAL'
  | 'TIMING_CHANGE'
  | 'BUS_DELAY'
  | 'BUS_CANCELLATION'
  | 'ROUTE_CHANGE'
  | 'BUS_SWAP'
  | 'HOLIDAY'
  | 'EMERGENCY';
export type AnnouncementAudience =
  | 'ALL'
  | 'STUDENTS_ONLY'
  | 'DRIVERS_ONLY'
  | 'SELECTED_BUS'
  | 'SELECTED_ROUTE';

export interface AnnouncementRecord {
  id: string;
  titleEn: string;
  messageEn: string;
  titleKn?: string;
  messageKn?: string;
  category: AnnouncementCategory;
  priority: AnnouncementPriority;
  targetAudience: AnnouncementAudience;
  targetId?: string;
  targetName?: string;
  targetBusId?: string;
  targetRouteId?: string;
  status: AnnouncementStatus;
  createdBy: string;
  createdByName: string;
  createdAt: number;
  updatedAt: number;
  expiresAt?: number | null;
  notified?: boolean;
}

export interface CreateAnnouncementInput {
  titleEn: string;
  messageEn: string;
  titleKn?: string;
  messageKn?: string;
  category: AnnouncementCategory;
  priority: AnnouncementPriority;
  targetAudience: AnnouncementAudience;
  targetId?: string;
  targetName?: string;
  status: AnnouncementStatus;
  createdBy: string;
  createdByName: string;
  expiresAt?: number | null;
}

export type StudentFeedbackStatus = 'NEW' | 'REVIEWED' | 'RESOLVED';
export type StudentFeedbackType = 'APP' | 'BUS';

export interface StudentFeedbackRecord {
  feedbackId: string;
  feedbackType: StudentFeedbackType;
  busId: string;
  routeId: string;
  category: string;
  subject: string;
  description: string;
  studentName: string;
  studentContact: string;
  timestamp: number;
  status: StudentFeedbackStatus;
}

export interface StudentInstallationRecord {
  installationId: string;
  appVersion: string;
  language: string;
  lastActiveDate: string;
  updatedAt: number;
}

export interface DailyAnalyticsRecord {
  date: string;
  totalEvents: number;
  dauCount: number;
  newInstallations: number;
  returningUsers: number;
  events: Record<string, number>;
  languages: Record<string, number>;
  versions: Record<string, number>;
  updatedAt: number;
}

export interface HistoryAuditLogRecord {
  auditId: string;
  action: 'CLEAR_BUS_SWAP_HISTORY' | 'CLEAR_DRIVER_REPLACEMENT_HISTORY' | string;
  adminUid: string;
  adminName: string;
  timestamp: number;
  dateFormatted: string;
  timeFormatted: string;
  clearedCount: number;
}

export interface BusSwapHistoryRecord {
  historyId: string;
  swapId: string;
  originalBusId: string;
  replacementBusId: string;
  routeId: string;
  date: string;
  reason: string;
  changedByUid: string;
  changedByName: string;
  status: string;
  createdAt: number;
  cleared: boolean;
}

export interface DriverReplacementHistoryRecord {
  historyId: string;
  assignmentId: string;
  previousDriverUid: string;
  previousDriverName: string;
  replacementDriverUid: string;
  replacementDriverName: string;
  busId: string;
  reason: string;
  startAt: number;
  endAt: number;
  changedByUid: string;
  changedByName: string;
  status: string;
  createdAt: number;
  cleared: boolean;
}

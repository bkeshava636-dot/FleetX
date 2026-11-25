/**
 * Fleet & Driver Management Domain Models and Type Definitions
 * Milestone 5: Fleet & Driver Management
 */

export type RtdbSyncStatus = 'SYNCED' | 'PENDING' | 'FAILED' | 'PERMANENTLY_FAILED';

export interface BusRecord {
  busId: string;
  busNumber: string;
  registrationNumber: string;
  capacity: number;
  active: boolean;
  routeId: string | null;
  driverId: string | null;
  driverName: string | null;
  assignmentVersion?: number;
  assignmentEpoch?: number;
  rtdbSyncStatus?: RtdbSyncStatus;
  rtdbSyncError?: string | null;
  rtdbSyncLastAttempt?: number | null;
  rtdbSyncRetryCount?: number;
  createdAt?: number;
  updatedAt?: number;
}

export interface DriverRecord {
  uid: string;
  customId: string; // Staff ID, e.g., 'drv_01'
  name: string;
  phoneNumber: string;
  email: string;
  role: 'DRIVER';
  active: boolean;
  assignedBusId: string | null;
  createdAt?: number;
  updatedAt?: number;
}

export interface CreateBusInput {
  busNumber: string;
  registrationNumber: string;
  capacity: number;
  routeId?: string | null;
}

export interface UpdateBusDetailsInput {
  busNumber?: string;
  registrationNumber?: string;
  capacity?: number;
  routeId?: string | null;
}

export interface CreateDriverInput {
  name: string;
  staffId: string;
  phoneNumber: string;
  password?: string;
  initialBusId?: string | null;
}

export interface UpdateDriverProfileInput {
  driverUid: string;
  name: string;
  staffId: string;
  phoneNumber: string;
}

export interface SetBusDriverAssignmentInput {
  busId: string;
  driverUid: string | null;
  confirmReplacement?: boolean;
  expectedPreviousDriverId?: string | null;
}

export interface SetBusDriverAssignmentResponse {
  success: boolean;
  busId: string;
  driverUid: string | null;
  driverName: string | null;
  assignmentVersion: number;
}

export interface ToggleBusActiveResponse {
  success: boolean;
  busId: string;
  active: boolean;
  unassignedDriverUid?: string | null;
}

export interface ToggleDriverActiveResponse {
  success: boolean;
  driverUid: string;
  active: boolean;
  unassignedBusId?: string | null;
}

export interface ReconcileBusRtdbResponse {
  success: boolean;
  busId: string;
  rtdbSyncStatus: RtdbSyncStatus;
  assignmentVersion: number;
}

export interface RegisterBusResponse {
  success: boolean;
  busId: string;
  busNumber: string;
  registrationNumber: string;
  capacity: number;
  active: boolean;
  routeId: string | null;
}

export interface UpdateBusDetailsResponse {
  success: boolean;
  busId: string;
}

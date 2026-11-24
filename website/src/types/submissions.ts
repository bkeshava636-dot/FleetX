export type SubmissionStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export type SubmissionMethod = 'GPS' | 'MANUAL';

export interface StopLocationSubmission {
  submissionId: string;
  stopId: string;
  stopName: string;
  routeId: string;
  routeName: string;
  busId: string;
  studentIdentifier?: string;
  latitude: number;
  longitude: number;
  accuracyMeters: number | null;
  submissionMethod: SubmissionMethod;
  status: SubmissionStatus;
  submittedAt: number; // Unix epoch ms
  reviewedAt?: number | null;
  reviewedBy?: string | null;
  adminNote?: string | null;
}

export interface SubmissionFilterOptions {
  status: SubmissionStatus | 'ALL';
  routeId?: string;
  searchQuery?: string;
}

export type SubmissionStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export type LocationSource = 'GPS' | 'MANUAL';

export interface StopCoordinateSubmission {
  id: string;
  routeId: string;
  routeName: string;
  stopId: string;
  stopName: string;
  stopOrder: number;
  proposedLatitude: number;
  proposedLongitude: number;
  accuracyMeters: number | null;
  source: LocationSource;
  status: SubmissionStatus;
  submittedAt: number; // UTC timestamp ms
  existingLatitude?: number | null;
  existingLongitude?: number | null;
  studentNotes?: string | null;
  reviewedBy?: string | null;
  reviewedAt?: number | null;
  reviewNotes?: string | null;
  finalLatitude?: number | null;
  finalLongitude?: number | null;
}

export interface SubmitCoordinatesInput {
  routeId: string;
  routeName: string;
  stopId: string;
  stopName: string;
  stopOrder: number;
  proposedLatitude: number;
  proposedLongitude: number;
  accuracyMeters: number | null;
  source: LocationSource;
  existingLatitude?: number | null;
  existingLongitude?: number | null;
  studentNotes?: string | null;
}

export interface ReviewSubmissionInput {
  submissionId: string;
  action: 'APPROVE' | 'REJECT';
  adjustedLatitude?: number;
  adjustedLongitude?: number;
  reviewNotes?: string;
}

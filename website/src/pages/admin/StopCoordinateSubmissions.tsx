import React, { useState, useEffect, useMemo } from 'react';
import {
  MapPin,
  Search,
  CheckCircle2,
  Clock,
  Check,
  X,
  AlertTriangle,
  Sliders,
  Layers,
} from 'lucide-react';
import { useAdminAuth } from '../../context/AdminAuthContext';
import {
  listenToSubmissions,
  reviewSubmission,
  calculateDistanceMeters,
} from '../../services/submissionService';
import {
  StopCoordinateSubmission,
  SubmissionStatus,
} from '../../types/submissions';
import { GoogleMapPicker, LatLng } from '../../components/maps/GoogleMapPicker';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { EmptyState } from '../../components/common/EmptyState';

export const StopCoordinateSubmissions: React.FC = () => {
  const { currentUser } = useAdminAuth();

  // Submissions State
  const [submissions, setSubmissions] = useState<StopCoordinateSubmission[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Filters State
  const [statusFilter, setStatusFilter] = useState<SubmissionStatus | 'ALL'>('PENDING');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [selectedSubmissionId, setSelectedSubmissionId] = useState<string | null>(null);

  // Review Drawer / Modal Action State
  const [adjustedLat, setAdjustedLat] = useState<number>(0);
  const [adjustedLng, setAdjustedLng] = useState<number>(0);
  const [adminNotes, setAdminNotes] = useState<string>('');
  const [processingAction, setProcessingAction] = useState<boolean>(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);

  // 1. Subscribe to Submissions
  useEffect(() => {
    setLoading(true);
    const unsubscribe = listenToSubmissions(
      { status: statusFilter },
      (data) => {
        setSubmissions(data);
        setLoading(false);
      },
      (err) => {
        setError(err.message || 'Failed to stream submissions.');
        setLoading(false);
      }
    );
    return () => unsubscribe();
  }, [statusFilter]);

  // Status Metrics
  const metrics = useMemo(() => {
    let pending = 0;
    let approved = 0;
    let rejected = 0;
    for (const sub of submissions) {
      if (sub.status === 'PENDING') pending++;
      else if (sub.status === 'APPROVED') approved++;
      else if (sub.status === 'REJECTED') rejected++;
    }
    return { pending, approved, rejected, total: submissions.length };
  }, [submissions]);

  // Filtered List
  const filteredSubmissions = useMemo(() => {
    if (!searchQuery.trim()) return submissions;
    const q = searchQuery.toLowerCase();
    return submissions.filter(
      (s) =>
        s.routeId.toLowerCase().includes(q) ||
        s.routeName.toLowerCase().includes(q) ||
        s.stopName.toLowerCase().includes(q) ||
        s.stopId.toLowerCase().includes(q) ||
        (s.studentNotes && s.studentNotes.toLowerCase().includes(q))
    );
  }, [submissions, searchQuery]);

  // Selected Submission Detail
  const selectedSubmission = useMemo(() => {
    if (!selectedSubmissionId) return null;
    return submissions.find((s) => s.id === selectedSubmissionId) || null;
  }, [submissions, selectedSubmissionId]);

  // Multi-submission group: other submissions for the exact same stop
  const relatedSubmissionsForStop = useMemo(() => {
    if (!selectedSubmission) return [];
    return submissions.filter(
      (s) =>
        s.routeId === selectedSubmission.routeId &&
        s.stopId === selectedSubmission.stopId &&
        s.id !== selectedSubmission.id
    );
  }, [submissions, selectedSubmission]);

  // Synchronize adjusted coordinates when selected submission changes
  useEffect(() => {
    if (selectedSubmission) {
      setAdjustedLat(selectedSubmission.proposedLatitude);
      setAdjustedLng(selectedSubmission.proposedLongitude);
      setAdminNotes(selectedSubmission.reviewNotes || '');
      setActionError(null);
      setActionSuccess(null);
    }
  }, [selectedSubmissionId]);

  // Handle Approve
  const handleApprove = async () => {
    if (!selectedSubmission || !currentUser) return;
    setProcessingAction(true);
    setActionError(null);
    setActionSuccess(null);

    try {
      await reviewSubmission(
        {
          submissionId: selectedSubmission.id,
          action: 'APPROVE',
          adjustedLatitude: adjustedLat,
          adjustedLongitude: adjustedLng,
          reviewNotes: adminNotes.trim() || undefined,
        },
        currentUser.uid
      );

      setActionSuccess(`Coordinates approved and written to ${selectedSubmission.routeId} successfully!`);
      setProcessingAction(false);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Approval failed.';
      setActionError(msg);
      setProcessingAction(false);
    }
  };

  // Handle Reject
  const handleReject = async () => {
    if (!selectedSubmission || !currentUser) return;
    setProcessingAction(true);
    setActionError(null);
    setActionSuccess(null);

    try {
      await reviewSubmission(
        {
          submissionId: selectedSubmission.id,
          action: 'REJECT',
          reviewNotes: adminNotes.trim() || undefined,
        },
        currentUser.uid
      );

      setActionSuccess('Submission rejected. Route coordinates remain unmodified.');
      setProcessingAction(false);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Rejection failed.';
      setActionError(msg);
      setProcessingAction(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header Banner */}
      <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="p-2 bg-blue-50 text-blue-600 rounded-xl">
              <MapPin className="w-5 h-5" />
            </span>
            <h2 className="text-xl font-bold text-slate-900 tracking-tight">
              Stop Coordinate Submissions
            </h2>
          </div>
          <p className="text-xs text-slate-500 mt-1 max-w-2xl">
            Crowdsourced bus stop coordinates submitted by students. Review proposals on Google Maps, fine-tune the pin if necessary, and approve to update the route stop data in real-time.
          </p>
        </div>

        {/* Status Filter Tabs */}
        <div className="flex items-center gap-1.5 bg-slate-100 p-1 rounded-xl self-start md:self-auto text-xs font-semibold">
          {(['PENDING', 'APPROVED', 'REJECTED', 'ALL'] as const).map((tab) => {
            const isActive = statusFilter === tab;
            return (
              <button
                key={tab}
                type="button"
                onClick={() => {
                  setStatusFilter(tab);
                  setSelectedSubmissionId(null);
                }}
                className={`px-3 py-1.5 rounded-lg transition capitalize flex items-center gap-1.5 ${
                  isActive
                    ? 'bg-white text-slate-900 shadow-sm'
                    : 'text-slate-500 hover:text-slate-800'
                }`}
              >
                <span>{tab.toLowerCase()}</span>
                {tab === 'PENDING' && metrics.pending > 0 && (
                  <span className="px-1.5 py-0.2 bg-blue-600 text-white rounded-full text-[10px]">
                    {metrics.pending}
                  </span>
                )}
              </button>
            );
          })}
        </div>
      </div>

      {/* Search Bar */}
      <div className="relative">
        <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
        <input
          type="text"
          placeholder="Filter submissions by stop name, route ID, or student notes..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="w-full pl-10 pr-4 py-2.5 bg-white border border-slate-200 rounded-xl text-xs text-slate-800 placeholder:text-slate-400 focus:outline-none focus:border-blue-500 shadow-sm"
        />
      </div>

      {/* Main Split Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        {/* Left Column: Submissions List */}
        <div className="lg:col-span-5 space-y-3">
          {loading ? (
            <div className="p-8 bg-white rounded-2xl border border-slate-200 text-center">
              <LoadingSpinner label="Streaming submissions..." />
            </div>
          ) : error ? (
            <div className="p-4 bg-red-50 border border-red-200 text-red-700 text-xs rounded-2xl">
              {error}
            </div>
          ) : filteredSubmissions.length === 0 ? (
            <EmptyState
              icon={MapPin}
              title={`No ${statusFilter === 'ALL' ? '' : statusFilter.toLowerCase()} submissions found`}
              description="No crowdsourced stop proposals match your filter criteria."
            />
          ) : (
            filteredSubmissions.map((sub) => {
              const isSelected = sub.id === selectedSubmissionId;
              const hasExisting = sub.existingLatitude !== null && sub.existingLongitude !== null;
              const dist =
                hasExisting && sub.existingLatitude && sub.existingLongitude
                  ? calculateDistanceMeters(
                      sub.existingLatitude,
                      sub.existingLongitude,
                      sub.proposedLatitude,
                      sub.proposedLongitude
                    )
                  : null;

              return (
                <div
                  key={sub.id}
                  onClick={() => setSelectedSubmissionId(sub.id)}
                  className={`p-4 bg-white rounded-2xl border cursor-pointer transition shadow-sm ${
                    isSelected
                      ? 'border-blue-500 ring-2 ring-blue-500/20 bg-blue-50/20'
                      : 'border-slate-200 hover:border-slate-300 hover:bg-slate-50/50'
                  }`}
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="font-mono text-[11px] font-bold text-blue-600 bg-blue-50 px-2 py-0.5 rounded-md">
                          {sub.routeId}
                        </span>
                        <span className="text-xs text-slate-400 font-mono">
                          Stop #{sub.stopOrder}
                        </span>
                      </div>
                      <h4 className="text-sm font-bold text-slate-900 truncate mt-1">
                        {sub.stopName}
                      </h4>
                    </div>

                    {/* Status Badge */}
                    <span
                      className={`text-[10px] font-bold px-2 py-0.5 rounded-full uppercase tracking-wider ${
                        sub.status === 'PENDING'
                          ? 'bg-amber-100 text-amber-800'
                          : sub.status === 'APPROVED'
                          ? 'bg-emerald-100 text-emerald-800'
                          : 'bg-rose-100 text-rose-800'
                      }`}
                    >
                      {sub.status}
                    </span>
                  </div>

                  <div className="mt-3 flex items-center justify-between text-xs text-slate-500 border-t border-slate-100 pt-2.5">
                    <div className="flex items-center gap-2">
                      <span className="font-mono text-[11px] text-slate-700">
                        {sub.proposedLatitude.toFixed(4)}, {sub.proposedLongitude.toFixed(4)}
                      </span>
                      {sub.source === 'GPS' && sub.accuracyMeters && (
                        <span className="text-[10px] bg-slate-100 px-1.5 py-0.2 rounded text-slate-600 font-mono">
                          ±{sub.accuracyMeters}m
                        </span>
                      )}
                    </div>

                    <div className="flex items-center gap-1 text-[11px] text-slate-400">
                      <Clock className="w-3 h-3" />
                      <span>{new Date(sub.submittedAt).toLocaleDateString()}</span>
                    </div>
                  </div>

                  {dist !== null && (
                    <div className="mt-2 text-[10px] text-slate-400 flex items-center justify-between">
                      <span>Shift from current:</span>
                      <span className="font-mono font-semibold text-slate-600">{dist}m</span>
                    </div>
                  )}

                  {sub.studentNotes && (
                    <div className="mt-2 p-2 bg-slate-50 rounded-lg text-[11px] text-slate-600 italic">
                      "{sub.studentNotes}"
                    </div>
                  )}
                </div>
              );
            })
          )}
        </div>

        {/* Right Column: Google Maps & Review Action Panel */}
        <div className="lg:col-span-7">
          {selectedSubmission ? (
            <div className="bg-white rounded-2xl border border-slate-200/90 shadow-sm p-6 space-y-5 sticky top-20">
              {/* Header */}
              <div className="flex items-start justify-between">
                <div>
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-mono font-bold text-blue-600 bg-blue-50 px-2 py-0.5 rounded-md">
                      {selectedSubmission.routeId}
                    </span>
                    <span className="text-xs text-slate-400 font-mono">
                      Stop ID: {selectedSubmission.stopId}
                    </span>
                  </div>
                  <h3 className="text-lg font-bold text-slate-900 mt-1">
                    {selectedSubmission.stopName}
                  </h3>
                  <p className="text-xs text-slate-500">
                    Route: <span className="font-medium text-slate-700">{selectedSubmission.routeName}</span>
                  </p>
                </div>

                <div className="text-right">
                  <span className="text-[11px] text-slate-400 block">Submitted At</span>
                  <span className="text-xs font-medium text-slate-700 font-mono">
                    {new Date(selectedSubmission.submittedAt).toLocaleString()}
                  </span>
                </div>
              </div>

              {/* Feedback Toasts */}
              {actionSuccess && (
                <div className="p-3 bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs rounded-xl flex items-center gap-2">
                  <CheckCircle2 className="w-4 h-4 text-emerald-600 flex-shrink-0" />
                  <span>{actionSuccess}</span>
                </div>
              )}
              {actionError && (
                <div className="p-3 bg-red-50 border border-red-200 text-red-800 text-xs rounded-xl flex items-center gap-2">
                  <AlertTriangle className="w-4 h-4 text-red-600 flex-shrink-0" />
                  <span>{actionError}</span>
                </div>
              )}

              {/* Multiple Submissions for Same Stop Alert */}
              {relatedSubmissionsForStop.length > 0 && (
                <div className="p-3 bg-blue-50/80 border border-blue-200/80 rounded-xl text-xs text-blue-900 space-y-2">
                  <div className="flex items-center gap-2 font-semibold">
                    <Layers className="w-4 h-4 text-blue-600 flex-shrink-0" />
                    <span>
                      {relatedSubmissionsForStop.length} alternative submission(s) exist for this stop
                    </span>
                  </div>
                  <div className="space-y-1 pl-6">
                    {relatedSubmissionsForStop.map((rel) => (
                      <div
                        key={rel.id}
                        onClick={() => setSelectedSubmissionId(rel.id)}
                        className="text-[11px] text-blue-700 hover:text-blue-900 hover:underline cursor-pointer flex items-center justify-between"
                      >
                        <span>
                          {rel.source} proposal ({rel.proposedLatitude.toFixed(5)}, {rel.proposedLongitude.toFixed(5)})
                        </span>
                        <span className="font-mono text-[10px] text-blue-500">
                          {new Date(rel.submittedAt).toLocaleDateString()}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Google Map Preview */}
              <div className="space-y-2">
                <div className="flex items-center justify-between text-xs text-slate-600">
                  <span className="font-semibold flex items-center gap-1.5">
                    <Sliders className="w-3.5 h-3.5 text-blue-600" />
                    Interactive Map Review
                  </span>
                  <span className="text-[11px] text-slate-400">
                    {selectedSubmission.status === 'PENDING'
                      ? 'Drag blue pin to fine-tune coords'
                      : 'Coordinates read-only'}
                  </span>
                </div>

                <GoogleMapPicker
                  proposedPosition={{ lat: adjustedLat, lng: adjustedLng }}
                  existingPosition={
                    selectedSubmission.existingLatitude && selectedSubmission.existingLongitude
                      ? {
                          lat: selectedSubmission.existingLatitude,
                          lng: selectedSubmission.existingLongitude,
                        }
                      : null
                  }
                  existingStopName={selectedSubmission.stopName}
                  accuracyMeters={selectedSubmission.accuracyMeters}
                  onPositionChange={(pos: LatLng) => {
                    if (selectedSubmission.status === 'PENDING') {
                      setAdjustedLat(pos.lat);
                      setAdjustedLng(pos.lng);
                    }
                  }}
                  draggable={selectedSubmission.status === 'PENDING'}
                  interactive={selectedSubmission.status === 'PENDING'}
                  height="320px"
                />
              </div>

              {/* Fine-Tuning Inputs */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
                <div>
                  <label className="text-[11px] font-semibold text-slate-700 block mb-1">
                    Latitude
                  </label>
                  <input
                    type="number"
                    step="0.000001"
                    disabled={selectedSubmission.status !== 'PENDING'}
                    value={adjustedLat}
                    onChange={(e) => setAdjustedLat(parseFloat(e.target.value) || 0)}
                    className="w-full bg-slate-50 border border-slate-200 rounded-lg px-2.5 py-1.5 font-mono text-slate-800 disabled:bg-slate-100 disabled:text-slate-500"
                  />
                </div>

                <div>
                  <label className="text-[11px] font-semibold text-slate-700 block mb-1">
                    Longitude
                  </label>
                  <input
                    type="number"
                    step="0.000001"
                    disabled={selectedSubmission.status !== 'PENDING'}
                    value={adjustedLng}
                    onChange={(e) => setAdjustedLng(parseFloat(e.target.value) || 0)}
                    className="w-full bg-slate-50 border border-slate-200 rounded-lg px-2.5 py-1.5 font-mono text-slate-800 disabled:bg-slate-100 disabled:text-slate-500"
                  />
                </div>
              </div>

              {/* Review Notes */}
              <div>
                <label className="text-[11px] font-semibold text-slate-700 block mb-1">
                  Administrator Notes
                </label>
                <textarea
                  rows={2}
                  disabled={selectedSubmission.status !== 'PENDING'}
                  placeholder="Optional review reason or adjustment details (e.g. Moved 3m to roadside bus bay)..."
                  value={adminNotes}
                  onChange={(e) => setAdminNotes(e.target.value)}
                  className="w-full bg-slate-50 border border-slate-200 rounded-lg p-2.5 text-xs text-slate-800 placeholder:text-slate-400 focus:outline-none focus:border-blue-500 disabled:bg-slate-100 disabled:text-slate-500"
                />
              </div>

              {/* If already reviewed, display audit information */}
              {selectedSubmission.status !== 'PENDING' && (
                <div className="p-3 bg-slate-50 rounded-xl border border-slate-200 text-xs space-y-1.5 text-slate-600">
                  <div className="flex justify-between">
                    <span className="text-slate-400">Reviewed By</span>
                    <span className="font-mono text-slate-700">{selectedSubmission.reviewedBy || 'Admin'}</span>
                  </div>
                  {selectedSubmission.reviewedAt && (
                    <div className="flex justify-between">
                      <span className="text-slate-400">Reviewed At</span>
                      <span className="font-mono text-slate-700">
                        {new Date(selectedSubmission.reviewedAt).toLocaleString()}
                      </span>
                    </div>
                  )}
                  {selectedSubmission.finalLatitude && (
                    <div className="flex justify-between">
                      <span className="text-slate-400">Final Coordinates</span>
                      <span className="font-mono text-slate-700">
                        {selectedSubmission.finalLatitude.toFixed(6)}, {selectedSubmission.finalLongitude?.toFixed(6)}
                      </span>
                    </div>
                  )}
                </div>
              )}

              {/* Action Buttons */}
              {selectedSubmission.status === 'PENDING' && (
                <div className="flex items-center gap-3 pt-2">
                  <button
                    type="button"
                    disabled={processingAction}
                    onClick={handleApprove}
                    className="flex-1 inline-flex items-center justify-center gap-2 px-4 py-2.5 bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 text-white text-xs font-bold rounded-xl transition shadow-sm"
                  >
                    <Check className="w-4 h-4" />
                    <span>{processingAction ? 'Updating Route...' : 'Approve & Update Route'}</span>
                  </button>

                  <button
                    type="button"
                    disabled={processingAction}
                    onClick={handleReject}
                    className="inline-flex items-center justify-center gap-2 px-4 py-2.5 bg-rose-50 hover:bg-rose-100 text-rose-700 border border-rose-200 text-xs font-bold rounded-xl transition"
                  >
                    <X className="w-4 h-4" />
                    <span>Reject</span>
                  </button>
                </div>
              )}
            </div>
          ) : (
            <div className="p-12 text-center bg-white rounded-2xl border border-slate-200/80 text-slate-400 text-xs flex flex-col items-center justify-center min-h-[360px]">
              <MapPin className="w-8 h-8 text-slate-300 mb-2" />
              <p className="font-semibold text-slate-600">Select a submission from the list</p>
              <p className="mt-1 max-w-xs">
                View the proposed bus stop coordinates on Google Maps alongside the existing location.
              </p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default StopCoordinateSubmissions;

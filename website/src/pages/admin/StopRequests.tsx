import React, { useEffect, useMemo, useState } from 'react';
import {
  MapPin,
  Search,
  Check,
  X,
  Clock,
  CheckCircle2,
  AlertTriangle,
  AlertCircle,
  Loader2,
  Bus,
  Route as RouteIcon,
  User,
} from 'lucide-react';
import { useAdminAuth } from '../../context/AdminAuthContext';
import {
  StopRequest,
  StopRequestStatus,
  subscribeToStopRequests,
  approveStopRequest,
  rejectStopRequest,
  validateStopRequestApprovalInput,
} from '../../services/stopRequestsService';
import { GoogleMapPicker } from '../../components/maps/GoogleMapPicker';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { EmptyState } from '../../components/common/EmptyState';

type StatusTab = StopRequestStatus | 'ALL';
type ConfirmActionType = 'APPROVE' | 'REJECT' | null;

export const StopRequests: React.FC = () => {
  const { currentUser } = useAdminAuth();

  const [requests, setRequests] = useState<StopRequest[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const [statusFilter, setStatusFilter] = useState<StatusTab>('PENDING');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [selectedRequestId, setSelectedRequestId] = useState<string | null>(null);

  // Editable review fields
  const [finalStopName, setFinalStopName] = useState<string>('');
  const [finalLat, setFinalLat] = useState<string>('');
  const [finalLng, setFinalLng] = useState<string>('');
  const [adminNote, setAdminNote] = useState<string>('');
  const [rejectionReason, setRejectionReason] = useState<string>('');

  // Confirmation modal & action feedback state
  const [confirmAction, setConfirmAction] = useState<ConfirmActionType>(null);
  const [processingAction, setProcessingAction] = useState<boolean>(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    const unsubscribe = subscribeToStopRequests(
      (data) => {
        setRequests(data);
        setLoading(false);
        setError(null);
      },
      (err) => {
        setError(err.message || 'Failed to stream driver stop requests.');
        setLoading(false);
      }
    );
    return () => unsubscribe();
  }, []);

  const metrics = useMemo(() => {
    let pending = 0;
    let approved = 0;
    let rejected = 0;
    for (const req of requests) {
      if (req.status === 'PENDING') pending++;
      else if (req.status === 'APPROVED') approved++;
      else if (req.status === 'REJECTED') rejected++;
    }
    return { pending, approved, rejected, total: requests.length };
  }, [requests]);

  const filteredRequests = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    return requests.filter((req) => {
      if (statusFilter !== 'ALL' && req.status !== statusFilter) {
        return false;
      }
      if (!q) return true;
      return (
        req.proposedStopName.toLowerCase().includes(q) ||
        (req.finalStopName && req.finalStopName.toLowerCase().includes(q)) ||
        req.driverName.toLowerCase().includes(q) ||
        req.driverId.toLowerCase().includes(q) ||
        req.driverUid.toLowerCase().includes(q) ||
        req.busId.toLowerCase().includes(q) ||
        req.routeId.toLowerCase().includes(q) ||
        req.requestId.toLowerCase().includes(q)
      );
    });
  }, [requests, statusFilter, searchQuery]);

  const selectedRequest = useMemo(() => {
    if (!selectedRequestId) return null;
    return requests.find((r) => r.requestId === selectedRequestId) || null;
  }, [requests, selectedRequestId]);

  useEffect(() => {
    if (selectedRequest) {
      setFinalStopName(selectedRequest.finalStopName ?? selectedRequest.proposedStopName);
      setFinalLat(String(selectedRequest.finalLatitude ?? selectedRequest.latitude));
      setFinalLng(String(selectedRequest.finalLongitude ?? selectedRequest.longitude));
      setAdminNote(selectedRequest.adminNote ?? '');
      setRejectionReason(selectedRequest.rejectionReason ?? '');
      setActionError(null);
      setActionSuccess(null);
      setConfirmAction(null);
    }
  }, [selectedRequestId]);

  const parsedLat = Number(finalLat);
  const parsedLng = Number(finalLng);
  const approvalValidation = validateStopRequestApprovalInput(
    finalStopName,
    parsedLat,
    parsedLng
  );

  const openConfirmModal = (action: 'APPROVE' | 'REJECT') => {
    setActionError(null);
    setActionSuccess(null);
    if (action === 'APPROVE' && !approvalValidation.valid) {
      setActionError(approvalValidation.reason || 'Invalid stop name or coordinates.');
      return;
    }
    setConfirmAction(action);
  };

  const handleConfirmSubmit = async () => {
    if (!selectedRequest || !currentUser || !confirmAction) return;
    setProcessingAction(true);
    setActionError(null);
    setActionSuccess(null);

    try {
      if (confirmAction === 'APPROVE') {
        const res = await approveStopRequest({
          requestId: selectedRequest.requestId,
          finalStopName: finalStopName.trim(),
          finalLatitude: parsedLat,
          finalLongitude: parsedLng,
          adminNote: adminNote.trim() || null,
          reviewedBy: currentUser.uid,
        });
        setActionSuccess(
          `Approved! Stop "${finalStopName.trim()}" (${res.stopId}) appended to route ${res.routeId}.`
        );
      } else {
        await rejectStopRequest({
          requestId: selectedRequest.requestId,
          rejectionReason: rejectionReason.trim() || null,
          reviewedBy: currentUser.uid,
        });
        setActionSuccess('Stop request rejected. No route stops were modified.');
      }
      setConfirmAction(null);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Action failed.';
      setActionError(msg);
      setConfirmAction(null);
    } finally {
      setProcessingAction(false);
    }
  };

  const formatDateTime = (ts: number | null) => {
    if (!ts) return '—';
    return new Date(ts).toLocaleString();
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
              Driver Stop Requests
            </h2>
          </div>
          <p className="text-xs text-slate-500 mt-1 max-w-2xl">
            Review new bus stop proposals submitted by drivers. Verify location coordinates, adjust stop details if needed, and approve to append the stop directly to the route.
          </p>
        </div>

        {/* Status Filter Tabs */}
        <div className="flex items-center gap-1.5 bg-slate-100 p-1 rounded-xl self-start md:self-auto text-xs font-semibold">
          {(['PENDING', 'APPROVED', 'REJECTED', 'ALL'] as const).map((tab) => {
            const isActive = statusFilter === tab;
            const count =
              tab === 'PENDING'
                ? metrics.pending
                : tab === 'APPROVED'
                ? metrics.approved
                : tab === 'REJECTED'
                ? metrics.rejected
                : metrics.total;

            return (
              <button
                key={tab}
                type="button"
                onClick={() => {
                  setStatusFilter(tab);
                  setSelectedRequestId(null);
                }}
                className={`px-3 py-1.5 rounded-lg transition capitalize flex items-center gap-1.5 ${
                  isActive
                    ? 'bg-white text-slate-900 shadow-sm'
                    : 'text-slate-500 hover:text-slate-800'
                }`}
              >
                <span>{tab.toLowerCase()}</span>
                <span
                  className={`px-1.5 py-0.2 rounded-full text-[10px] ${
                    tab === 'PENDING' && count > 0
                      ? 'bg-blue-600 text-white'
                      : 'bg-slate-200 text-slate-600'
                  }`}
                >
                  {count}
                </span>
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
          placeholder="Search by stop name, driver name or ID, bus ID, or route ID..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="w-full pl-10 pr-4 py-2.5 bg-white border border-slate-200 rounded-xl text-xs text-slate-800 placeholder:text-slate-400 focus:outline-none focus:border-blue-500 shadow-sm"
        />
      </div>

      {/* Main Split Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        {/* Left Column: Requests List */}
        <div className="lg:col-span-5 space-y-3">
          {loading ? (
            <div className="p-8 bg-white rounded-2xl border border-slate-200 text-center">
              <LoadingSpinner label="Streaming stop requests..." />
            </div>
          ) : error ? (
            <div className="p-4 bg-red-50 border border-red-200 text-red-700 text-xs rounded-2xl">
              {error}
            </div>
          ) : filteredRequests.length === 0 ? (
            <EmptyState
              icon={MapPin}
              title={`No ${statusFilter === 'ALL' ? '' : statusFilter.toLowerCase()} stop requests`}
              description="No driver stop proposals match your current filter or search query."
            />
          ) : (
            filteredRequests.map((req) => {
              const isSelected = req.requestId === selectedRequestId;
              return (
                <div
                  key={req.requestId}
                  onClick={() => setSelectedRequestId(req.requestId)}
                  className={`p-4 bg-white rounded-2xl border cursor-pointer transition shadow-sm ${
                    isSelected
                      ? 'border-blue-500 ring-2 ring-blue-500/20 bg-blue-50/20'
                      : 'border-slate-200 hover:border-slate-300 hover:bg-slate-50/50'
                  }`}
                >
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="font-mono text-[11px] font-bold text-blue-600 bg-blue-50 px-2 py-0.5 rounded-md">
                          {req.routeId || 'NO-ROUTE'}
                        </span>
                        <span className="font-mono text-[11px] text-slate-600 bg-slate-100 px-2 py-0.5 rounded-md">
                          {req.busId || 'NO-BUS'}
                        </span>
                      </div>
                      <h4 className="text-sm font-bold text-slate-900 truncate mt-1.5">
                        {req.finalStopName || req.proposedStopName}
                      </h4>
                      <p className="text-xs text-slate-500 truncate mt-0.5">
                        Driver: {req.driverName || 'Unknown'}{' '}
                        {req.driverId ? `(${req.driverId})` : ''}
                      </p>
                    </div>

                    <span
                      className={`text-[10px] font-bold px-2 py-0.5 rounded-full uppercase tracking-wider shrink-0 ${
                        req.status === 'PENDING'
                          ? 'bg-amber-100 text-amber-800'
                          : req.status === 'APPROVED'
                          ? 'bg-emerald-100 text-emerald-800'
                          : 'bg-rose-100 text-rose-800'
                      }`}
                    >
                      {req.status}
                    </span>
                  </div>

                  <div className="mt-3 flex items-center justify-between text-xs text-slate-500 border-t border-slate-100 pt-2.5">
                    <span className="font-mono text-[11px] text-slate-700">
                      {req.latitude.toFixed(5)}, {req.longitude.toFixed(5)}
                    </span>
                    <div className="flex items-center gap-1 text-[11px] text-slate-400">
                      <Clock className="w-3 h-3" />
                      <span>
                        {req.createdAt
                          ? new Date(req.createdAt).toLocaleDateString()
                          : '—'}
                      </span>
                    </div>
                  </div>
                </div>
              );
            })
          )}
        </div>

        {/* Right Column: Detail & Review Panel */}
        <div className="lg:col-span-7">
          {!selectedRequest ? (
            <div className="bg-white rounded-2xl border border-slate-200 p-10 text-center">
              <EmptyState
                icon={MapPin}
                title="Select a stop request"
                description="Choose a driver stop request from the list on the left to inspect its coordinates and approve or reject it."
              />
            </div>
          ) : (
            <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6 space-y-5">
              {/* Top Header */}
              <div className="flex flex-wrap items-start justify-between gap-3 border-b border-slate-100 pb-4">
                <div>
                  <span className="text-[11px] font-mono text-slate-400">
                    Request ID: {selectedRequest.requestId}
                  </span>
                  <h3 className="text-lg font-bold text-slate-900 mt-0.5">
                    {selectedRequest.proposedStopName}
                  </h3>
                </div>
                <span
                  className={`text-xs font-bold px-3 py-1 rounded-full uppercase tracking-wider ${
                    selectedRequest.status === 'PENDING'
                      ? 'bg-amber-100 text-amber-800'
                      : selectedRequest.status === 'APPROVED'
                      ? 'bg-emerald-100 text-emerald-800'
                      : 'bg-rose-100 text-rose-800'
                  }`}
                >
                  {selectedRequest.status}
                </span>
              </div>

              {/* Metadata Grid */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div className="p-3 bg-slate-50 rounded-xl border border-slate-100">
                  <div className="flex items-center gap-1.5 text-[11px] font-semibold text-slate-500">
                    <User className="w-3.5 h-3.5" />
                    <span>Driver</span>
                  </div>
                  <p className="text-xs font-bold text-slate-900 mt-1">
                    {selectedRequest.driverName || 'Unknown'}
                  </p>
                  <p className="text-[11px] font-mono text-slate-500">
                    {selectedRequest.driverId || selectedRequest.driverUid || '—'}
                  </p>
                </div>

                <div className="p-3 bg-slate-50 rounded-xl border border-slate-100">
                  <div className="flex items-center gap-1.5 text-[11px] font-semibold text-slate-500">
                    <Bus className="w-3.5 h-3.5" />
                    <span>Bus ID</span>
                  </div>
                  <p className="text-xs font-bold font-mono text-slate-900 mt-1">
                    {selectedRequest.busId || '—'}
                  </p>
                  <p className="text-[11px] text-slate-500">
                    Submitted {formatDateTime(selectedRequest.createdAt)}
                  </p>
                </div>

                <div className="p-3 bg-slate-50 rounded-xl border border-slate-100">
                  <div className="flex items-center gap-1.5 text-[11px] font-semibold text-slate-500">
                    <RouteIcon className="w-3.5 h-3.5" />
                    <span>Route ID</span>
                  </div>
                  <p className="text-xs font-bold font-mono text-blue-700 mt-1">
                    {selectedRequest.routeId || '—'}
                  </p>
                  <p className="text-[11px] font-mono text-slate-500">
                    Proposed: {selectedRequest.latitude.toFixed(5)},{' '}
                    {selectedRequest.longitude.toFixed(5)}
                  </p>
                </div>
              </div>

              {/* Map Preview */}
              <GoogleMapPicker
                proposedPosition={
                  Number.isFinite(parsedLat) &&
                  Number.isFinite(parsedLng) &&
                  parsedLat !== 0 &&
                  parsedLng !== 0
                    ? { lat: parsedLat, lng: parsedLng }
                    : { lat: selectedRequest.latitude, lng: selectedRequest.longitude }
                }
                draggable={selectedRequest.status === 'PENDING'}
                interactive={selectedRequest.status === 'PENDING'}
                onPositionChange={(pos) => {
                  if (selectedRequest.status === 'PENDING') {
                    setFinalLat(pos.lat.toFixed(6));
                    setFinalLng(pos.lng.toFixed(6));
                  }
                }}
                height="260px"
              />

              {/* Editable Fields (or Read-Only Summary if already reviewed) */}
              {selectedRequest.status === 'PENDING' ? (
                <div className="space-y-4 pt-2">
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 mb-1">
                      Final Stop Name
                    </label>
                    <input
                      type="text"
                      value={finalStopName}
                      onChange={(e) => setFinalStopName(e.target.value)}
                      placeholder="Enter final stop name"
                      className="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:border-blue-500"
                    />
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs font-semibold text-slate-700 mb-1">
                        Final Latitude
                      </label>
                      <input
                        type="number"
                        step="any"
                        value={finalLat}
                        onChange={(e) => setFinalLat(e.target.value)}
                        className="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl text-xs font-mono text-slate-900 focus:outline-none focus:border-blue-500"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-slate-700 mb-1">
                        Final Longitude
                      </label>
                      <input
                        type="number"
                        step="any"
                        value={finalLng}
                        onChange={(e) => setFinalLng(e.target.value)}
                        className="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl text-xs font-mono text-slate-900 focus:outline-none focus:border-blue-500"
                      />
                    </div>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs font-semibold text-slate-700 mb-1">
                        Admin Note (for approval)
                      </label>
                      <textarea
                        rows={2}
                        value={adminNote}
                        onChange={(e) => setAdminNote(e.target.value)}
                        placeholder="Optional note recorded on approval..."
                        className="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:border-blue-500 resize-none"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-slate-700 mb-1">
                        Rejection Reason (for rejection)
                      </label>
                      <textarea
                        rows={2}
                        value={rejectionReason}
                        onChange={(e) => setRejectionReason(e.target.value)}
                        placeholder="Optional reason shown to driver if rejected..."
                        className="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:border-rose-500 resize-none"
                      />
                    </div>
                  </div>

                  {actionError && (
                    <div className="flex items-start gap-2 p-3 bg-rose-50 border border-rose-200 rounded-xl text-xs text-rose-800">
                      <AlertCircle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
                      <span>{actionError}</span>
                    </div>
                  )}

                  {actionSuccess && (
                    <div className="flex items-start gap-2 p-3 bg-emerald-50 border border-emerald-200 rounded-xl text-xs text-emerald-800">
                      <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0 mt-0.5" />
                      <span>{actionSuccess}</span>
                    </div>
                  )}

                  <div className="flex flex-wrap items-center justify-end gap-3 pt-2 border-t border-slate-100">
                    <button
                      type="button"
                      disabled={processingAction}
                      onClick={() => openConfirmModal('REJECT')}
                      className="px-4 py-2 rounded-xl border border-rose-200 bg-rose-50 text-rose-700 hover:bg-rose-100 text-xs font-semibold flex items-center gap-1.5 transition disabled:opacity-50"
                    >
                      <X className="w-4 h-4" />
                      Reject Request
                    </button>
                    <button
                      type="button"
                      disabled={processingAction}
                      onClick={() => openConfirmModal('APPROVE')}
                      className="px-4 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-semibold flex items-center gap-1.5 shadow-sm transition disabled:opacity-50"
                    >
                      <Check className="w-4 h-4" />
                      Approve &amp; Add Stop
                    </button>
                  </div>
                </div>
              ) : (
                <div className="space-y-3 pt-2 border-t border-slate-100 text-xs">
                  {actionSuccess && (
                    <div className="flex items-start gap-2 p-3 bg-emerald-50 border border-emerald-200 rounded-xl text-xs text-emerald-800">
                      <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0 mt-0.5" />
                      <span>{actionSuccess}</span>
                    </div>
                  )}
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 bg-slate-50 p-4 rounded-xl border border-slate-100">
                    <div>
                      <span className="text-slate-500">Reviewed By:</span>{' '}
                      <span className="font-mono font-semibold text-slate-800">
                        {selectedRequest.reviewedBy || '—'}
                      </span>
                    </div>
                    <div>
                      <span className="text-slate-500">Reviewed At:</span>{' '}
                      <span className="font-semibold text-slate-800">
                        {formatDateTime(selectedRequest.reviewedAt)}
                      </span>
                    </div>
                    {selectedRequest.status === 'APPROVED' && (
                      <>
                        <div>
                          <span className="text-slate-500">Final Stop Name:</span>{' '}
                          <span className="font-semibold text-slate-900">
                            {selectedRequest.finalStopName || '—'}
                          </span>
                        </div>
                        <div>
                          <span className="text-slate-500">Final Coordinates:</span>{' '}
                          <span className="font-mono font-semibold text-slate-900">
                            {selectedRequest.finalLatitude?.toFixed(6)},{' '}
                            {selectedRequest.finalLongitude?.toFixed(6)}
                          </span>
                        </div>
                        {selectedRequest.adminNote && (
                          <div className="sm:col-span-2">
                            <span className="text-slate-500">Admin Note:</span>{' '}
                            <span className="text-slate-800">{selectedRequest.adminNote}</span>
                          </div>
                        )}
                      </>
                    )}
                    {selectedRequest.status === 'REJECTED' && selectedRequest.rejectionReason && (
                      <div className="sm:col-span-2">
                        <span className="text-slate-500">Rejection Reason:</span>{' '}
                        <span className="text-rose-700 font-medium">
                          {selectedRequest.rejectionReason}
                        </span>
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Confirmation Dialog */}
      {confirmAction && selectedRequest && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/70 backdrop-blur-sm">
          <div className="relative w-full max-w-md bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden">
            <div
              className={`flex items-center justify-between px-6 py-4 border-b border-slate-100 ${
                confirmAction === 'APPROVE' ? 'bg-emerald-50/60' : 'bg-rose-50/60'
              }`}
            >
              <div className="flex items-center gap-3">
                <div
                  className={`p-2 rounded-xl ${
                    confirmAction === 'APPROVE'
                      ? 'bg-emerald-100 text-emerald-700'
                      : 'bg-rose-100 text-rose-700'
                  }`}
                >
                  {confirmAction === 'APPROVE' ? (
                    <Check className="w-5 h-5" />
                  ) : (
                    <AlertTriangle className="w-5 h-5" />
                  )}
                </div>
                <div>
                  <h3 className="text-base font-bold text-slate-900">
                    {confirmAction === 'APPROVE'
                      ? 'Confirm Stop Approval'
                      : 'Confirm Stop Rejection'}
                  </h3>
                  <p className="text-[11px] font-mono text-slate-500">
                    {selectedRequest.requestId}
                  </p>
                </div>
              </div>
              <button
                type="button"
                disabled={processingAction}
                onClick={() => setConfirmAction(null)}
                className="p-1.5 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded-lg transition-colors"
                aria-label="Close confirmation dialog"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="p-6 space-y-3 text-xs text-slate-600">
              {confirmAction === 'APPROVE' ? (
                <>
                  <p>
                    You are about to approve this request and append a new bus stop to route{' '}
                    <span className="font-mono font-bold text-slate-900">
                      {selectedRequest.routeId}
                    </span>
                    :
                  </p>
                  <div className="p-3 bg-slate-50 rounded-xl border border-slate-200 space-y-1">
                    <p className="font-bold text-slate-900">{finalStopName.trim()}</p>
                    <p className="font-mono text-slate-600">
                      {parsedLat.toFixed(6)}, {parsedLng.toFixed(6)}
                    </p>
                    {adminNote.trim() && (
                      <p className="text-slate-500 italic">Note: {adminNote.trim()}</p>
                    )}
                  </div>
                </>
              ) : (
                <>
                  <p>
                    You are about to reject the stop request{' '}
                    <span className="font-bold text-slate-900">
                      "{selectedRequest.proposedStopName}"
                    </span>{' '}
                    from driver{' '}
                    <span className="font-semibold text-slate-900">
                      {selectedRequest.driverName || selectedRequest.driverId}
                    </span>
                    . Route stops will not be modified.
                  </p>
                  {rejectionReason.trim() && (
                    <div className="p-3 bg-rose-50 rounded-xl border border-rose-200 text-rose-800">
                      Reason: {rejectionReason.trim()}
                    </div>
                  )}
                </>
              )}
            </div>

            <div className="px-6 py-4 bg-slate-50 border-t border-slate-100 flex items-center justify-end gap-2.5">
              <button
                type="button"
                disabled={processingAction}
                onClick={() => setConfirmAction(null)}
                className="px-4 py-2 rounded-xl border border-slate-200 bg-white text-slate-700 hover:bg-slate-100 text-xs font-semibold transition disabled:opacity-50"
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={processingAction}
                onClick={handleConfirmSubmit}
                className={`px-4 py-2 rounded-xl text-white text-xs font-semibold flex items-center gap-1.5 transition disabled:opacity-50 ${
                  confirmAction === 'APPROVE'
                    ? 'bg-emerald-600 hover:bg-emerald-700'
                    : 'bg-rose-600 hover:bg-rose-700'
                }`}
              >
                {processingAction && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
                <span>
                  {confirmAction === 'APPROVE' ? 'Confirm Approve' : 'Confirm Reject'}
                </span>
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default StopRequests;

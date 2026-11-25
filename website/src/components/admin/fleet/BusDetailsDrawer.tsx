import React, { useState } from 'react';
import {
  X,
  Bus,
  Route as RouteIcon,
  User,
  Activity,
  RefreshCw,
  CheckCircle2,
  Clock,
  AlertTriangle,
  XCircle,
  AlertCircle,
  Edit2
} from 'lucide-react';
import { reconcileBusSync } from '../../../services/fleetService';
import { BusRecord, RtdbSyncStatus } from '../../../types/fleet';
import { Route } from '../../../types/admin';

interface BusDetailsDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  bus: BusRecord | null;
  routes: Route[];
  onOpenEdit: (bus: BusRecord) => void;
  onOpenAssignRoute: (bus: BusRecord) => void;
  onReconcileSuccess: () => void;
}

export const BusDetailsDrawer: React.FC<BusDetailsDrawerProps> = ({
  isOpen,
  onClose,
  bus,
  routes,
  onOpenEdit,
  onOpenAssignRoute,
  onReconcileSuccess
}) => {
  const [isReconciling, setIsReconciling] = useState(false);
  const [reconcileError, setReconcileError] = useState<string | null>(null);
  const [reconcileSuccess, setReconcileSuccess] = useState<string | null>(null);

  if (!isOpen || !bus) return null;

  const assignedRoute = routes.find((r) => r.routeId === bus.routeId);

  const handleReconcile = async () => {
    setReconcileError(null);
    setReconcileSuccess(null);
    setIsReconciling(true);

    try {
      const res = await reconcileBusSync(bus.busId);
      setReconcileSuccess(`Operational data synchronized (Version ${res.assignmentVersion}).`);
      onReconcileSuccess();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to reconcile RTDB synchronization.';
      setReconcileError(msg);
    } finally {
      setIsReconciling(false);
    }
  };

  const getSyncBadge = (status?: RtdbSyncStatus) => {
    switch (status) {
      case 'SYNCED':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-800">
            <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />
            Synchronized
          </span>
        );
      case 'PENDING':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-amber-100 text-amber-800">
            <Clock className="w-3.5 h-3.5 text-amber-600 animate-pulse" />
            Sync Pending
          </span>
        );
      case 'FAILED':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-rose-100 text-rose-800">
            <AlertTriangle className="w-3.5 h-3.5 text-rose-600" />
            Sync Failed
          </span>
        );
      case 'PERMANENTLY_FAILED':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-slate-100 text-slate-800">
            <XCircle className="w-3.5 h-3.5 text-slate-600" />
            Permanent Failure
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-slate-100 text-slate-600">
            <Activity className="w-3.5 h-3.5" />
            Unknown
          </span>
        );
    }
  };

  const formatTimestamp = (ts?: number | null) => {
    if (!ts) return 'Never';
    return new Date(ts).toLocaleString('en-IN', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    });
  };

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-slate-950/70 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="relative w-full max-w-md bg-white h-full shadow-2xl flex flex-col border-l border-slate-200 overflow-hidden animate-in slide-in-from-right duration-300">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 bg-slate-50/70">
          <div className="flex items-center gap-3">
            <div className="p-2.5 bg-blue-100 text-blue-700 rounded-xl">
              <Bus className="w-5 h-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-lg font-bold text-slate-900">Bus {bus.busNumber}</h2>
                <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-slate-200 text-slate-700 font-medium">
                  {bus.busId}
                </span>
              </div>
              <p className="text-xs font-mono text-slate-500">{bus.registrationNumber}</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded-lg transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content Body */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {/* Notifications */}
          {reconcileSuccess && (
            <div className="flex items-start gap-2.5 p-3.5 bg-emerald-50 border border-emerald-200 rounded-xl text-emerald-800 text-xs font-medium">
              <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0 mt-0.5" />
              <span>{reconcileSuccess}</span>
            </div>
          )}

          {reconcileError && (
            <div className="flex items-start gap-2.5 p-3.5 bg-rose-50 border border-rose-200 rounded-xl text-rose-800 text-xs font-medium">
              <AlertCircle className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
              <span>{reconcileError}</span>
            </div>
          )}

          {/* Quick Status Bar */}
          <div className="grid grid-cols-2 gap-3">
            <div className="p-3.5 bg-slate-50 border border-slate-200/80 rounded-xl">
              <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider block mb-1">
                Fleet Status
              </span>
              <span
                className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold ${
                  bus.active ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-200 text-slate-700'
                }`}
              >
                {bus.active ? 'Active' : 'Inactive'}
              </span>
            </div>

            <div className="p-3.5 bg-slate-50 border border-slate-200/80 rounded-xl">
              <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider block mb-1">
                Seating Capacity
              </span>
              <span className="text-sm font-bold text-slate-900">{bus.capacity} seats</span>
            </div>
          </div>

          {/* Route Assignment Section */}
          <div className="p-4 bg-slate-50 border border-slate-200/80 rounded-2xl space-y-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2 text-xs font-bold text-slate-700 uppercase tracking-wider">
                <RouteIcon className="w-4 h-4 text-indigo-600" />
                <span>Assigned Route</span>
              </div>
              <button
                onClick={() => onOpenAssignRoute(bus)}
                className="text-xs font-semibold text-indigo-600 hover:text-indigo-800 flex items-center gap-1"
              >
                <Edit2 className="w-3 h-3" />
                <span>Change</span>
              </button>
            </div>

            {assignedRoute ? (
              <div className="p-3 bg-white border border-slate-200 rounded-xl space-y-1">
                <div className="flex items-center justify-between">
                  <span className="text-sm font-semibold text-slate-900">{assignedRoute.name}</span>
                  <span className="text-xs font-mono px-2 py-0.5 bg-indigo-50 text-indigo-700 rounded font-semibold">
                    {assignedRoute.routeId}
                  </span>
                </div>
                <p className="text-xs text-slate-500">
                  {assignedRoute.stops.length} stops • {assignedRoute.active ? 'Active Route' : 'Inactive Route'}
                </p>
              </div>
            ) : (
              <div className="p-3 bg-white border border-dashed border-slate-300 rounded-xl text-center">
                <p className="text-xs text-slate-500 font-medium">No route currently assigned.</p>
              </div>
            )}
          </div>

          {/* Driver Assignment Section */}
          <div className="p-4 bg-slate-50 border border-slate-200/80 rounded-2xl space-y-3">
            <div className="flex items-center gap-2 text-xs font-bold text-slate-700 uppercase tracking-wider">
              <User className="w-4 h-4 text-blue-600" />
              <span>Assigned Driver</span>
            </div>

            {bus.driverId ? (
              <div className="p-3 bg-white border border-slate-200 rounded-xl space-y-1">
                <div className="flex items-center justify-between">
                  <span className="text-sm font-semibold text-slate-900">{bus.driverName || 'Assigned Driver'}</span>
                  <span className="text-xs px-2 py-0.5 bg-blue-50 text-blue-700 rounded font-medium">Assigned</span>
                </div>
                <p className="text-xs font-mono text-slate-400">UID: {bus.driverId}</p>
              </div>
            ) : (
              <div className="p-3 bg-white border border-dashed border-slate-300 rounded-xl text-center">
                <p className="text-xs text-slate-500 font-medium">No permanent driver assigned.</p>
              </div>
            )}
          </div>

          {/* RTDB Synchronization Diagnostics */}
          <div className="p-4 bg-slate-50 border border-slate-200/80 rounded-2xl space-y-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2 text-xs font-bold text-slate-700 uppercase tracking-wider">
                <Activity className="w-4 h-4 text-amber-600" />
                <span>RTDB Synchronization</span>
              </div>
              {getSyncBadge(bus.rtdbSyncStatus)}
            </div>

            <div className="p-3 bg-white border border-slate-200 rounded-xl space-y-2 text-xs">
              <div className="flex items-center justify-between text-slate-600">
                <span>Assignment Version</span>
                <span className="font-mono font-semibold text-slate-900">v{bus.assignmentVersion || 0}</span>
              </div>

              <div className="flex items-center justify-between text-slate-600">
                <span>Last Sync Attempt</span>
                <span className="text-slate-700">{formatTimestamp(bus.rtdbSyncLastAttempt)}</span>
              </div>

              {bus.rtdbSyncRetryCount !== undefined && bus.rtdbSyncRetryCount > 0 && (
                <div className="flex items-center justify-between text-slate-600">
                  <span>Recovery Retry Count</span>
                  <span className="font-semibold text-amber-600">{bus.rtdbSyncRetryCount}</span>
                </div>
              )}

              {bus.rtdbSyncError && (
                <div className="pt-2 border-t border-slate-100 text-rose-700 bg-rose-50/50 p-2 rounded-lg">
                  <span className="font-semibold block mb-0.5">Sync Error:</span>
                  <span className="font-mono text-[11px] break-all">{bus.rtdbSyncError}</span>
                </div>
              )}
            </div>

            <button
              onClick={handleReconcile}
              disabled={isReconciling}
              className="w-full py-2.5 px-4 bg-white hover:bg-slate-100 disabled:bg-slate-50 border border-slate-300 text-slate-700 font-semibold text-xs rounded-xl transition-colors flex items-center justify-center gap-2 shadow-sm"
            >
              <RefreshCw className={`w-3.5 h-3.5 text-slate-600 ${isReconciling ? 'animate-spin' : ''}`} />
              <span>{isReconciling ? 'Reconciling...' : 'Reconcile Operational RTDB'}</span>
            </button>
          </div>
        </div>

        {/* Footer Actions */}
        <div className="p-4 border-t border-slate-100 bg-slate-50/50 flex items-center gap-3">
          <button
            onClick={() => onOpenEdit(bus)}
            className="flex-1 py-2.5 px-4 bg-blue-600 hover:bg-blue-700 text-white font-semibold text-xs rounded-xl transition-colors flex items-center justify-center gap-2 shadow-sm"
          >
            <Edit2 className="w-3.5 h-3.5" />
            <span>Edit Bus Details</span>
          </button>
          <button
            onClick={onClose}
            className="py-2.5 px-4 text-xs font-semibold text-slate-600 hover:bg-slate-200/60 rounded-xl transition-colors"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
};

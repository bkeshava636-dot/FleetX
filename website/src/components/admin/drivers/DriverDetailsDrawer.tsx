import React, { useState } from 'react';
import {
  X,
  User,
  Bus,
  Route as RouteIcon,
  Phone,
  Mail,
  Key,
  Power,
  Trash2,
  Edit2,
  RefreshCw,
  CheckCircle2,
  Clock,
  AlertTriangle,
  XCircle,
  AlertCircle
} from 'lucide-react';
import { reconcileBusSync } from '../../../services/fleetService';
import { DriverRecord, BusRecord, RtdbSyncStatus } from '../../../types/fleet';
import { Route } from '../../../types/admin';

interface DriverDetailsDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  driver: DriverRecord | null;
  buses: BusRecord[];
  routes: Route[];
  onOpenEdit: (driver: DriverRecord) => void;
  onOpenManageAssignment: (driver: DriverRecord) => void;
  onOpenResetPassword: (driver: DriverRecord) => void;
  onOpenToggleStatus: (driver: DriverRecord) => void;
  onOpenDelete: (driver: DriverRecord) => void;
  onReconcileSuccess: () => void;
}

export const DriverDetailsDrawer: React.FC<DriverDetailsDrawerProps> = ({
  isOpen,
  onClose,
  driver,
  buses,
  routes,
  onOpenEdit,
  onOpenManageAssignment,
  onOpenResetPassword,
  onOpenToggleStatus,
  onOpenDelete,
  onReconcileSuccess
}) => {
  const [isReconciling, setIsReconciling] = useState(false);
  const [reconcileError, setReconcileError] = useState<string | null>(null);
  const [reconcileSuccess, setReconcileSuccess] = useState<string | null>(null);

  if (!isOpen || !driver) return null;

  const assignedBus = driver.assignedBusId
    ? buses.find((b) => b.busId === driver.assignedBusId) || null
    : null;

  const assignedRoute = assignedBus?.routeId
    ? routes.find((r) => r.routeId === assignedBus.routeId) || null
    : null;

  const handleReconcile = async () => {
    if (!assignedBus) return;
    setReconcileError(null);
    setReconcileSuccess(null);
    setIsReconciling(true);

    try {
      const res = await reconcileBusSync(assignedBus.busId);
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
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-blue-100 text-blue-800">
            <Clock className="w-3.5 h-3.5 text-blue-600 animate-pulse" />
            Pending Sync
          </span>
        );
      case 'FAILED':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-amber-100 text-amber-800">
            <AlertTriangle className="w-3.5 h-3.5 text-amber-600" />
            Sync Delayed
          </span>
        );
      case 'PERMANENTLY_FAILED':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-red-100 text-red-800">
            <XCircle className="w-3.5 h-3.5 text-red-600" />
            Sync Failed
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium bg-slate-100 text-slate-600">
            Unknown
          </span>
        );
    }
  };

  return (
    <div className="fixed inset-0 z-50 overflow-hidden bg-slate-950/60 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="absolute inset-0" onClick={onClose} />

      <div className="fixed inset-y-0 right-0 max-w-full flex pl-10">
        <div className="w-screen max-w-md bg-white shadow-2xl flex flex-col border-l border-slate-200">
          {/* Header */}
          <div className="px-6 py-5 border-b border-slate-100 bg-slate-50/50 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-blue-100 text-blue-700 flex items-center justify-center font-bold text-base">
                {driver.name.charAt(0).toUpperCase()}
              </div>
              <div>
                <h2 className="text-lg font-bold text-slate-900 leading-tight">{driver.name}</h2>
                <div className="flex items-center gap-2 mt-0.5">
                  <span className="font-mono text-xs px-2 py-0.5 bg-slate-100 text-slate-700 font-semibold rounded">
                    {driver.customId}
                  </span>
                  <span
                    className={`inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-semibold ${
                      driver.active ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-200 text-slate-700'
                    }`}
                  >
                    {driver.active ? 'Active' : 'Inactive'}
                  </span>
                </div>
              </div>
            </div>
            <button
              onClick={onClose}
              className="p-2 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded-lg transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          {/* Drawer Body */}
          <div className="flex-1 overflow-y-auto p-6 space-y-6">
            {/* Reconciliation Alerts */}
            {reconcileSuccess && (
              <div className="p-3 bg-emerald-50 border border-emerald-200 rounded-xl flex items-center gap-2 text-xs text-emerald-800">
                <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
                <span>{reconcileSuccess}</span>
              </div>
            )}
            {reconcileError && (
              <div className="p-3 bg-red-50 border border-red-200 rounded-xl flex items-center gap-2 text-xs text-red-800">
                <AlertCircle className="w-4 h-4 text-red-600 shrink-0" />
                <span>{reconcileError}</span>
              </div>
            )}

            {/* Profile Overview Card */}
            <div className="space-y-3">
              <h3 className="text-xs font-bold text-slate-400 uppercase tracking-wider flex items-center gap-1.5">
                <User className="w-3.5 h-3.5" />
                Driver Profile
              </h3>
              <div className="bg-slate-50 border border-slate-200/80 rounded-2xl p-4 space-y-3">
                <div className="flex items-center justify-between text-xs">
                  <span className="text-slate-500 flex items-center gap-1.5">
                    <Mail className="w-3.5 h-3.5 text-slate-400" />
                    Login Email
                  </span>
                  <span className="font-mono font-medium text-slate-800 break-all">{driver.email}</span>
                </div>
                <div className="flex items-center justify-between text-xs">
                  <span className="text-slate-500 flex items-center gap-1.5">
                    <Phone className="w-3.5 h-3.5 text-slate-400" />
                    Mobile Phone
                  </span>
                  <span className="font-mono font-medium text-slate-800">+91 {driver.phoneNumber}</span>
                </div>
                <div className="flex items-center justify-between text-xs">
                  <span className="text-slate-500">System Role</span>
                  <span className="font-semibold text-slate-800">{driver.role}</span>
                </div>
                <div className="flex items-center justify-between text-xs">
                  <span className="text-slate-500">Firebase UID</span>
                  <span className="font-mono text-[11px] text-slate-600 truncate max-w-[180px]">{driver.uid}</span>
                </div>
                {driver.createdAt && (
                  <div className="flex items-center justify-between text-xs pt-1 border-t border-slate-200/60">
                    <span className="text-slate-400">Registered</span>
                    <span className="text-slate-600">{new Date(driver.createdAt).toLocaleDateString()}</span>
                  </div>
                )}
              </div>
            </div>

            {/* Operational Bus Assignment Card */}
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <h3 className="text-xs font-bold text-slate-400 uppercase tracking-wider flex items-center gap-1.5">
                  <Bus className="w-3.5 h-3.5" />
                  Assigned Vehicle & RTDB Sync
                </h3>
                <button
                  type="button"
                  onClick={() => onOpenManageAssignment(driver)}
                  className="text-xs font-semibold text-blue-600 hover:text-blue-800 transition-colors"
                >
                  Manage
                </button>
              </div>

              {assignedBus ? (
                <div className="bg-slate-50 border border-slate-200/80 rounded-2xl p-4 space-y-3">
                  <div className="flex items-center justify-between">
                    <div>
                      <span className="text-sm font-bold text-slate-900 block">{assignedBus.busNumber}</span>
                      <span className="font-mono text-xs text-blue-700 bg-blue-50 border border-blue-200/60 px-2 py-0.5 rounded">
                        {assignedBus.registrationNumber}
                      </span>
                    </div>
                    {getSyncBadge(assignedBus.rtdbSyncStatus)}
                  </div>

                  {assignedRoute && (
                    <div className="flex items-center gap-2 pt-2 border-t border-slate-200/60 text-xs">
                      <RouteIcon className="w-3.5 h-3.5 text-slate-400" />
                      <span className="text-slate-500">Route:</span>
                      <span className="font-semibold text-slate-800">
                        {assignedRoute.name || assignedRoute.routeId}
                      </span>
                    </div>
                  )}

                  <div className="flex items-center justify-between text-xs pt-2 border-t border-slate-200/60">
                    <span className="text-slate-500">Assignment Version</span>
                    <span className="font-mono font-bold text-slate-800">
                      v{assignedBus.assignmentVersion ?? 0}
                    </span>
                  </div>

                  {assignedBus.rtdbSyncLastAttempt && (
                    <div className="flex items-center justify-between text-xs">
                      <span className="text-slate-500">Last RTDB Sync Attempt</span>
                      <span className="text-slate-600">
                        {new Date(assignedBus.rtdbSyncLastAttempt).toLocaleTimeString()}
                      </span>
                    </div>
                  )}

                  {assignedBus.rtdbSyncError && (
                    <div className="p-2.5 bg-red-50 border border-red-200 rounded-lg text-xs text-red-700 space-y-1">
                      <span className="font-semibold block">Sync Error:</span>
                      <span className="font-mono text-[11px] block">{assignedBus.rtdbSyncError}</span>
                    </div>
                  )}

                  {/* Reconcile Button if sync is failed */}
                  {(assignedBus.rtdbSyncStatus === 'FAILED' ||
                    assignedBus.rtdbSyncStatus === 'PERMANENTLY_FAILED') && (
                    <button
                      type="button"
                      onClick={handleReconcile}
                      disabled={isReconciling}
                      className="w-full mt-2 inline-flex items-center justify-center gap-2 px-3 py-2 text-xs font-semibold text-white bg-amber-600 hover:bg-amber-700 active:bg-amber-800 rounded-xl transition-all shadow-sm"
                    >
                      <RefreshCw className={`w-3.5 h-3.5 ${isReconciling ? 'animate-spin' : ''}`} />
                      {isReconciling ? 'Reconciling...' : 'Reconcile RTDB Sync Now'}
                    </button>
                  )}
                </div>
              ) : (
                <div className="bg-slate-50 border border-dashed border-slate-300 rounded-2xl p-5 text-center space-y-2">
                  <p className="text-xs text-slate-500">No operational bus currently assigned to this driver.</p>
                  <button
                    type="button"
                    onClick={() => onOpenManageAssignment(driver)}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-blue-600 hover:text-blue-800 bg-blue-50 hover:bg-blue-100 rounded-lg transition-colors"
                  >
                    <Bus className="w-3.5 h-3.5" />
                    Assign Bus Now
                  </button>
                </div>
              )}
            </div>

            {/* Quick Actions List */}
            <div className="space-y-3 pt-2">
              <h3 className="text-xs font-bold text-slate-400 uppercase tracking-wider">Management Actions</h3>
              <div className="grid grid-cols-2 gap-2">
                <button
                  type="button"
                  onClick={() => onOpenEdit(driver)}
                  className="flex items-center gap-2 p-3 bg-slate-50 hover:bg-slate-100 border border-slate-200/80 rounded-xl text-left transition-colors text-xs font-semibold text-slate-700"
                >
                  <Edit2 className="w-4 h-4 text-blue-600" />
                  <span>Edit Profile</span>
                </button>
                <button
                  type="button"
                  onClick={() => onOpenManageAssignment(driver)}
                  className="flex items-center gap-2 p-3 bg-slate-50 hover:bg-slate-100 border border-slate-200/80 rounded-xl text-left transition-colors text-xs font-semibold text-slate-700"
                >
                  <Bus className="w-4 h-4 text-indigo-600" />
                  <span>Bus Assignment</span>
                </button>
                <button
                  type="button"
                  onClick={() => onOpenResetPassword(driver)}
                  className="flex items-center gap-2 p-3 bg-slate-50 hover:bg-slate-100 border border-slate-200/80 rounded-xl text-left transition-colors text-xs font-semibold text-slate-700"
                >
                  <Key className="w-4 h-4 text-amber-600" />
                  <span>Reset Password</span>
                </button>
                <button
                  type="button"
                  onClick={() => onOpenToggleStatus(driver)}
                  className="flex items-center gap-2 p-3 bg-slate-50 hover:bg-slate-100 border border-slate-200/80 rounded-xl text-left transition-colors text-xs font-semibold text-slate-700"
                >
                  <Power className="w-4 h-4 text-emerald-600" />
                  <span>{driver.active ? 'Deactivate' : 'Activate'}</span>
                </button>
              </div>

              {/* Danger Zone: Delete */}
              <div className="pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => onOpenDelete(driver)}
                  className="w-full flex items-center justify-center gap-2 p-2.5 bg-red-50 hover:bg-red-100 border border-red-200 rounded-xl text-xs font-semibold text-red-700 transition-colors"
                >
                  <Trash2 className="w-4 h-4 text-red-600" />
                  <span>Delete Driver Account</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

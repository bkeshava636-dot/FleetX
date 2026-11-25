import React, { useState } from 'react';
import { X, UserPlus, AlertCircle, Loader2, Key, Eye, EyeOff, Shuffle } from 'lucide-react';
import { createDriver, assignDriverToBus, validateDriverInputs, normalizePhoneNumber } from '../../../services/driverService';
import { BusRecord } from '../../../types/fleet';

interface RegisterDriverModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (driverInfo: { uid: string; customId: string; email: string; assignedBusId?: string | null }) => void;
  buses: BusRecord[];
}

export const RegisterDriverModal: React.FC<RegisterDriverModalProps> = ({
  isOpen,
  onClose,
  onSuccess,
  buses
}) => {
  const [staffId, setStaffId] = useState('');
  const [name, setName] = useState('');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [selectedBusId, setSelectedBusId] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isOpen) return null;

  const derivedEmail = staffId.trim()
    ? `${staffId.trim().toLowerCase()}@college.edu`
    : 'staff_id@college.edu';

  const handleClose = () => {
    setPassword('');
    setShowPassword(false);
    setError(null);
    onClose();
  };

  const handleGeneratePassword = () => {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%';
    const array = new Uint32Array(10);
    window.crypto.getRandomValues(array);
    let generated = '';
    for (let i = 0; i < array.length; i++) {
      generated += chars.charAt(array[i] % chars.length);
    }
    setPassword(generated);
    setShowPassword(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    const validationErr = validateDriverInputs(name, staffId, phoneNumber);
    if (validationErr) {
      setError(validationErr);
      return;
    }

    if (!password || password.length < 6) {
      setError('Initial password must be at least 6 characters long.');
      return;
    }

    setIsSubmitting(true);
    try {
      const cleanStaffId = staffId.trim().toLowerCase();
      const cleanPhone = normalizePhoneNumber(phoneNumber);
      const targetBusId = selectedBusId.trim() ? selectedBusId.trim() : null;

      // 1. Create Driver Account via Cloud Function
      const created = await createDriver({
        name: name.trim(),
        staffId: cleanStaffId,
        phoneNumber: cleanPhone,
        password,
        initialBusId: targetBusId
      });

      // 2. If a bus was chosen, ensure authoritative assignment via setBusDriverAssignment
      if (targetBusId) {
        try {
          await assignDriverToBus(created.uid, targetBusId);
        } catch (assignErr) {
          console.warn('[RegisterDriverModal] Driver created but bus assignment encountered delayed sync:', assignErr);
        }
      }

      // Reset form
      setStaffId('');
      setName('');
      setPhoneNumber('');
      setPassword('');
      setSelectedBusId('');
      onSuccess({ ...created, assignedBusId: targetBusId });
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to register driver account.';
      setError(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  const activeBuses = buses.filter((b) => b.active);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/70 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="relative w-full max-w-lg bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 bg-slate-50/50">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-blue-100 text-blue-700 rounded-xl">
              <UserPlus className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900">Register New Driver</h2>
              <p className="text-xs text-slate-500">Create driver account with Firebase Auth credentials</p>
            </div>
          </div>
          <button
            onClick={handleClose}
            disabled={isSubmitting}
            className="p-1.5 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded-lg transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {error && (
            <div className="p-3 bg-red-50 border border-red-200 rounded-xl flex items-start gap-2.5 text-red-700 text-xs">
              <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
              <span>{error}</span>
            </div>
          )}

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {/* Staff ID */}
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Staff ID <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                value={staffId}
                onChange={(e) => setStaffId(e.target.value.toLowerCase().replace(/[^a-z0-9_]/g, ''))}
                placeholder="e.g. drv_01"
                className="w-full px-3.5 py-2 text-sm bg-white border border-slate-300 rounded-xl focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all font-mono"
                required
                disabled={isSubmitting}
              />
              <p className="text-[11px] text-slate-400 mt-0.5">3-20 lowercase alphanumeric characters</p>
            </div>

            {/* Full Name */}
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                Full Name <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="e.g. Suresh Gowda"
                className="w-full px-3.5 py-2 text-sm bg-white border border-slate-300 rounded-xl focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all"
                required
                disabled={isSubmitting}
              />
              <p className="text-[11px] text-slate-400 mt-0.5">Official driver name</p>
            </div>
          </div>

          {/* Derived Login Email Preview */}
          <div className="p-3 bg-slate-50 border border-slate-200/80 rounded-xl">
            <span className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider block mb-0.5">
              Login Email (System Managed)
            </span>
            <span className="text-xs font-mono font-medium text-slate-800 break-all">
              {derivedEmail}
            </span>
          </div>

          {/* Phone Number */}
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              Mobile Phone Number <span className="text-red-500">*</span>
            </label>
            <div className="relative">
              <span className="absolute left-3.5 top-1/2 -translate-y-1/2 text-xs font-semibold text-slate-400">
                +91
              </span>
              <input
                type="tel"
                value={phoneNumber}
                onChange={(e) => setPhoneNumber(e.target.value.replace(/\D/g, '').slice(0, 10))}
                placeholder="9876543210"
                className="w-full pl-12 pr-3.5 py-2 text-sm bg-white border border-slate-300 rounded-xl focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all font-mono"
                required
                disabled={isSubmitting}
              />
            </div>
            <p className="text-[11px] text-slate-400 mt-0.5">10-digit Indian mobile number</p>
          </div>

          {/* Initial Password */}
          <div>
            <div className="flex items-center justify-between mb-1">
              <label className="block text-xs font-semibold text-slate-700">
                Initial Password <span className="text-red-500">*</span>
              </label>
              <button
                type="button"
                onClick={handleGeneratePassword}
                disabled={isSubmitting}
                className="text-[11px] text-blue-600 hover:text-blue-800 font-medium flex items-center gap-1 transition-colors"
              >
                <Shuffle className="w-3 h-3" />
                Generate Secure
              </button>
            </div>
            <div className="relative">
              <input
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Minimum 6 characters"
                className="w-full pl-3.5 pr-10 py-2 text-sm bg-white border border-slate-300 rounded-xl focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all font-mono"
                required
                disabled={isSubmitting}
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
              >
                {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>
            <p className="text-[11px] text-slate-400 mt-0.5">Provide this credential to the driver for first-time login</p>
          </div>

          {/* Optional Bus Assignment */}
          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              Assign to Bus <span className="text-slate-400 font-normal">(Optional)</span>
            </label>
            <select
              value={selectedBusId}
              onChange={(e) => setSelectedBusId(e.target.value)}
              disabled={isSubmitting}
              className="w-full px-3.5 py-2 text-sm bg-white border border-slate-300 rounded-xl focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 transition-all"
            >
              <option value="">No immediate bus assignment (Unassigned)</option>
              {activeBuses.map((bus) => (
                <option key={bus.busId} value={bus.busId}>
                  {bus.busNumber} ({bus.registrationNumber}) {bus.driverId ? `• Current: ${bus.driverName || 'Occupied'}` : '• Available'}
                </option>
              ))}
            </select>
            {selectedBusId && (
              <p className="text-[11px] text-amber-600 mt-1">
                {buses.find((b) => b.busId === selectedBusId)?.driverId
                  ? '⚠️ Note: This bus is currently assigned. Registering will transfer the bus to this driver.'
                  : '✓ Bus is currently unassigned.'}
              </p>
            )}
          </div>

          {/* Footer Actions */}
          <div className="flex items-center justify-end gap-3 pt-3 border-t border-slate-100">
            <button
              type="button"
              onClick={handleClose}
              disabled={isSubmitting}
              className="px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100 rounded-xl transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="inline-flex items-center gap-2 px-5 py-2 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 active:bg-blue-800 disabled:opacity-50 disabled:cursor-not-allowed rounded-xl shadow-sm transition-all"
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Creating Account...
                </>
              ) : (
                <>
                  <Key className="w-4 h-4" />
                  Register Driver
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

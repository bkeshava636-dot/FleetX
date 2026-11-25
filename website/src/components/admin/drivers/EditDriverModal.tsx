import React, { useState, useEffect } from 'react';
import { X, Edit2, AlertCircle, Loader2, Save } from 'lucide-react';
import { updateDriverProfile, validateDriverInputs, normalizePhoneNumber } from '../../../services/driverService';
import { DriverRecord } from '../../../types/fleet';

interface EditDriverModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (updated: { driverUid: string; customId: string; name: string; phoneNumber: string }) => void;
  driver: DriverRecord | null;
}

export const EditDriverModal: React.FC<EditDriverModalProps> = ({
  isOpen,
  onClose,
  onSuccess,
  driver
}) => {
  const [name, setName] = useState('');
  const [staffId, setStaffId] = useState('');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (driver) {
      setName(driver.name || '');
      setStaffId(driver.customId || '');
      setPhoneNumber(normalizePhoneNumber(driver.phoneNumber || ''));
      setError(null);
    }
  }, [driver]);

  if (!isOpen || !driver) return null;

  const derivedEmail = staffId.trim()
    ? `${staffId.trim().toLowerCase()}@college.edu`
    : driver.email;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    const validationErr = validateDriverInputs(name, staffId, phoneNumber);
    if (validationErr) {
      setError(validationErr);
      return;
    }

    setIsSubmitting(true);
    try {
      const cleanStaffId = staffId.trim().toLowerCase();
      const cleanPhone = normalizePhoneNumber(phoneNumber);

      const res = await updateDriverProfile({
        driverUid: driver.uid,
        name: name.trim(),
        staffId: cleanStaffId,
        phoneNumber: cleanPhone
      });

      onSuccess({
        driverUid: driver.uid,
        customId: res.customId,
        name: name.trim(),
        phoneNumber: cleanPhone
      });
      onClose();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to update driver profile.';
      setError(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/70 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="relative w-full max-w-lg bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 bg-slate-50/50">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-blue-100 text-blue-700 rounded-xl">
              <Edit2 className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900">Edit Driver Profile</h2>
              <p className="text-xs text-slate-500">Update staff details, phone, or staff ID</p>
            </div>
          </div>
          <button
            onClick={onClose}
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

          {/* UID Badge (Immutable) */}
          <div className="p-3 bg-slate-50 border border-slate-200/80 rounded-xl flex items-center justify-between">
            <div>
              <span className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider block">
                Firebase Driver UID
              </span>
              <span className="text-xs font-mono font-medium text-slate-700 break-all">
                {driver.uid}
              </span>
            </div>
            <span className="px-2 py-0.5 text-[10px] font-semibold bg-slate-200 text-slate-700 rounded-md">
              Immutable
            </span>
          </div>

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
              Associated Login Email
            </span>
            <span className="text-xs font-mono font-medium text-slate-800 break-all">
              {derivedEmail}
            </span>
            {staffId.trim() !== driver.customId && (
              <p className="text-[11px] text-amber-600 mt-1">
                ⚠️ Changing Staff ID will automatically update the driver's Auth login email.
              </p>
            )}
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

          {/* Footer Actions */}
          <div className="flex items-center justify-end gap-3 pt-3 border-t border-slate-100">
            <button
              type="button"
              onClick={onClose}
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
                  Updating...
                </>
              ) : (
                <>
                  <Save className="w-4 h-4" />
                  Save Changes
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

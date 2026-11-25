import React, { useState, useEffect } from 'react';
import { X, Key, AlertCircle, Loader2, Eye, EyeOff, Shuffle, Check, Copy } from 'lucide-react';
import { resetDriverPassword } from '../../../services/driverService';
import { DriverRecord } from '../../../types/fleet';

interface ResetPasswordModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (message: string) => void;
  driver: DriverRecord | null;
}

export const ResetPasswordModal: React.FC<ResetPasswordModalProps> = ({
  isOpen,
  onClose,
  onSuccess,
  driver
}) => {
  const [newPassword, setNewPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isSuccess, setIsSuccess] = useState(false);
  const [copied, setCopied] = useState(false);

  const handleClose = () => {
    setNewPassword('');
    setShowPassword(false);
    setError(null);
    setIsSuccess(false);
    setCopied(false);
    onClose();
  };

  useEffect(() => {
    if (driver) {
      setNewPassword('');
      setShowPassword(false);
      setError(null);
      setIsSuccess(false);
      setCopied(false);
    }
    return () => {
      setNewPassword('');
      setShowPassword(false);
    };
  }, [driver]);

  if (!isOpen || !driver) return null;

  const handleGeneratePassword = () => {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%';
    const array = new Uint32Array(10);
    window.crypto.getRandomValues(array);
    let generated = '';
    for (let i = 0; i < array.length; i++) {
      generated += chars.charAt(array[i] % chars.length);
    }
    setNewPassword(generated);
    setShowPassword(true);
  };

  const handleCopyCredentials = () => {
    const text = `FleetX Driver Login:\nEmail: ${driver.email}\nPassword: ${newPassword}`;
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 3000);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!newPassword || newPassword.length < 6) {
      setError('Password must be at least 6 characters long.');
      return;
    }

    setIsSubmitting(true);
    try {
      await resetDriverPassword(driver.uid, newPassword);
      setIsSuccess(true);
      onSuccess(`Password for ${driver.name} has been reset.`);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to reset driver password.';
      setError(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/70 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="relative w-full max-w-md bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 bg-slate-50/50">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-amber-100 text-amber-700 rounded-xl">
              <Key className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900">Reset Driver Password</h2>
              <p className="text-xs text-slate-500">Update Firebase Auth login credential</p>
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

        {/* Content */}
        {!isSuccess ? (
          <form onSubmit={handleSubmit} className="p-6 space-y-4">
            {error && (
              <div className="p-3 bg-red-50 border border-red-200 rounded-xl flex items-start gap-2.5 text-red-700 text-xs">
                <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                <span>{error}</span>
              </div>
            )}

            {/* Target Driver Info */}
            <div className="p-3 bg-slate-50 border border-slate-200 rounded-xl space-y-1">
              <div className="flex items-center justify-between text-xs">
                <span className="text-slate-500">Driver Name:</span>
                <span className="font-semibold text-slate-900">{driver.name}</span>
              </div>
              <div className="flex items-center justify-between text-xs">
                <span className="text-slate-500">Staff ID:</span>
                <span className="font-mono font-medium text-slate-800">{driver.customId}</span>
              </div>
              <div className="flex items-center justify-between text-xs">
                <span className="text-slate-500">Login Email:</span>
                <span className="font-mono text-slate-700 break-all">{driver.email}</span>
              </div>
            </div>

            {/* Password Input */}
            <div>
              <div className="flex items-center justify-between mb-1">
                <label className="block text-xs font-semibold text-slate-700">
                  New Password <span className="text-red-500">*</span>
                </label>
                <button
                  type="button"
                  onClick={handleGeneratePassword}
                  disabled={isSubmitting}
                  className="text-[11px] text-blue-600 hover:text-blue-800 font-medium flex items-center gap-1 transition-colors"
                >
                  <Shuffle className="w-3 h-3" />
                  Generate Random
                </button>
              </div>
              <div className="relative">
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  placeholder="Minimum 6 characters"
                  className="w-full pl-3.5 pr-10 py-2 text-sm bg-white border border-slate-300 rounded-xl focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500 transition-all font-mono"
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
              <p className="text-[11px] text-slate-400 mt-0.5">
                Password must be at least 6 characters. Never share administrator passwords.
              </p>
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
                disabled={isSubmitting || !newPassword || newPassword.length < 6}
                className="inline-flex items-center gap-2 px-5 py-2 text-sm font-semibold text-white bg-amber-600 hover:bg-amber-700 active:bg-amber-800 disabled:opacity-50 disabled:cursor-not-allowed rounded-xl shadow-sm transition-all"
              >
                {isSubmitting ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    Resetting...
                  </>
                ) : (
                  'Reset Password'
                )}
              </button>
            </div>
          </form>
        ) : (
          /* Post-Reset Credential Card */
          <div className="p-6 space-y-4">
            <div className="p-4 bg-emerald-50 border border-emerald-200 rounded-xl space-y-2">
              <p className="text-sm font-bold text-emerald-800">Password Reset Successfully</p>
              <p className="text-xs text-emerald-700">
                The driver's password in Firebase Authentication has been updated. Please securely provide these credentials to the driver.
              </p>
              <div className="p-3 bg-white rounded-lg border border-emerald-200/80 font-mono text-xs space-y-1">
                <div>
                  <span className="text-slate-400">Email: </span>
                  <span className="font-semibold text-slate-800">{driver.email}</span>
                </div>
                <div>
                  <span className="text-slate-400">New Password: </span>
                  <span className="font-bold text-slate-900">{newPassword}</span>
                </div>
              </div>
            </div>

            <div className="flex items-center justify-between pt-2">
              <button
                type="button"
                onClick={handleCopyCredentials}
                className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-semibold text-slate-700 hover:text-slate-900 bg-slate-100 hover:bg-slate-200 rounded-xl transition-colors"
              >
                {copied ? <Check className="w-4 h-4 text-emerald-600" /> : <Copy className="w-4 h-4" />}
                {copied ? 'Copied to Clipboard' : 'Copy Credentials'}
              </button>
              <button
                type="button"
                onClick={handleClose}
                className="px-5 py-2 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-xl transition-colors"
              >
                Done
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

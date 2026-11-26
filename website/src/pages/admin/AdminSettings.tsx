import React, { useState } from 'react';
import { Settings, ShieldCheck, KeyRound, RefreshCw, CheckCircle2, AlertTriangle } from 'lucide-react';
import { httpsCallable } from 'firebase/functions';
import { useAdminAuth } from '../../context/AdminAuthContext';
import { functions } from '../../lib/firebase';

export const AdminSettings: React.FC = () => {
  const { profile, refreshProfile } = useAdminAuth();
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [isSendingReset, setIsSendingReset] = useState(false);
  const [toast, setToast] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const showToast = (type: 'success' | 'error', text: string) => {
    setToast({ type, text });
    setTimeout(() => setToast(null), 4500);
  };

  const handleRefreshProfile = async () => {
    setIsRefreshing(true);
    try {
      await refreshProfile();
      showToast('success', 'Administrator profile refreshed from Firestore.');
    } catch (err) {
      showToast('error', err instanceof Error ? err.message : 'Failed to refresh profile.');
    } finally {
      setIsRefreshing(false);
    }
  };

  const handleSendPasswordReset = async () => {
    const identifier = profile?.email || profile?.customId;
    if (!identifier) {
      showToast('error', 'No email or admin ID found on profile.');
      return;
    }
    setIsSendingReset(true);
    try {
      const callable = httpsCallable<{ identifier: string }, { success: boolean; message?: string }>(
        functions,
        'requestAdminPasswordReset'
      );
      await callable({ identifier });
      showToast('success', `Password reset email requested for ${profile?.email || identifier}.`);
    } catch (err) {
      showToast(
        'error',
        err instanceof Error ? err.message : 'Failed to request password reset.'
      );
    } finally {
      setIsSendingReset(false);
    }
  };

  return (
    <div className="space-y-6">
      {toast && (
        <div
          className={`fixed bottom-5 right-5 z-50 px-4 py-3 rounded-xl shadow-lg text-xs font-semibold flex items-center gap-2 border ${
            toast.type === 'success'
              ? 'bg-emerald-50 text-emerald-800 border-emerald-200'
              : 'bg-red-50 text-red-800 border-red-200'
          }`}
        >
          {toast.type === 'success' ? (
            <CheckCircle2 className="w-4 h-4 text-emerald-600" />
          ) : (
            <AlertTriangle className="w-4 h-4 text-red-600" />
          )}
          <span>{toast.text}</span>
        </div>
      )}

      <div>
        <h2 className="text-xl font-bold text-slate-900 flex items-center gap-2">
          <Settings className="w-5 h-5 text-blue-600" />
          <span>Console Settings</span>
        </h2>
        <p className="text-xs text-slate-500 mt-1">
          Administrator account details, security actions, and connected Firebase environment info.
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Profile Card */}
        <div className="bg-white rounded-2xl border border-slate-200 p-6 space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2">
              <ShieldCheck className="w-4 h-4 text-blue-600" />
              <span>Administrator Profile</span>
            </h3>
            <button
              onClick={handleRefreshProfile}
              disabled={isRefreshing}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-semibold disabled:opacity-50"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isRefreshing ? 'animate-spin' : ''}`} />
              <span>Sync</span>
            </button>
          </div>

          <dl className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs">
            <div className="p-3 rounded-xl bg-slate-50 border border-slate-200">
              <dt className="text-slate-500">Full Name</dt>
              <dd className="font-bold text-slate-900 mt-0.5">{profile?.name || '—'}</dd>
            </div>
            <div className="p-3 rounded-xl bg-slate-50 border border-slate-200">
              <dt className="text-slate-500">Admin ID</dt>
              <dd className="font-bold text-slate-900 mt-0.5">{profile?.customId || '—'}</dd>
            </div>
            <div className="p-3 rounded-xl bg-slate-50 border border-slate-200">
              <dt className="text-slate-500">Email</dt>
              <dd className="font-semibold text-slate-900 mt-0.5 break-all">
                {profile?.email || '—'}
              </dd>
            </div>
            <div className="p-3 rounded-xl bg-slate-50 border border-slate-200">
              <dt className="text-slate-500">Role & Status</dt>
              <dd className="font-bold text-emerald-700 mt-0.5">
                {profile?.role || 'ADMIN'} • {profile?.active ? 'Active' : 'Inactive'}
              </dd>
            </div>
          </dl>

          <div className="pt-2 border-t border-slate-100">
            <button
              onClick={handleSendPasswordReset}
              disabled={isSendingReset}
              className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 text-white text-xs font-semibold disabled:opacity-50"
            >
              <KeyRound className="w-3.5 h-3.5" />
              <span>
                {isSendingReset ? 'Sending Reset Link...' : 'Send Password Reset Email'}
              </span>
            </button>
          </div>
        </div>

        {/* System Environment */}
        <div className="bg-white rounded-2xl border border-slate-200 p-6 space-y-4 text-xs">
          <h3 className="text-sm font-bold text-slate-900">Connected Infrastructure</h3>
          <div className="space-y-2.5">
            <div className="flex items-center justify-between p-3 rounded-xl bg-slate-50 border border-slate-200">
              <span className="text-slate-600 font-medium">Firebase Project</span>
              <span className="font-mono font-semibold text-slate-900">
                {import.meta.env.VITE_FIREBASE_PROJECT_ID || 'college-bus-tracker-prod'}
              </span>
            </div>
            <div className="flex items-center justify-between p-3 rounded-xl bg-slate-50 border border-slate-200">
              <span className="text-slate-600 font-medium">Functions Region</span>
              <span className="font-mono font-semibold text-slate-900">asia-south1</span>
            </div>
            <div className="flex items-center justify-between p-3 rounded-xl bg-slate-50 border border-slate-200">
              <span className="text-slate-600 font-medium">App Check</span>
              <span className="font-semibold text-emerald-700">reCAPTCHA Enterprise</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default AdminSettings;

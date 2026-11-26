import React, { useEffect, useMemo, useState } from 'react';
import {
  GraduationCap,
  Search,
  MessageSquare,
  Smartphone,
  CheckCircle2,
  Trash2,
  Eye,
  AlertTriangle,
} from 'lucide-react';
import {
  deleteStudentFeedback,
  subscribeToStudentFeedback,
  subscribeToStudentInstallations,
  updateStudentFeedbackStatus,
} from '../../services/operationsService';
import {
  StudentFeedbackRecord,
  StudentFeedbackStatus,
  StudentInstallationRecord,
} from '../../types/operations';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { EmptyState } from '../../components/common/EmptyState';
import { ErrorState } from '../../components/common/ErrorState';

export const StudentManagement: React.FC = () => {
  const [feedbackList, setFeedbackList] = useState<StudentFeedbackRecord[]>([]);
  const [installations, setInstallations] = useState<StudentInstallationRecord[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const [activeTab, setActiveTab] = useState<'FEEDBACK' | 'DEVICES'>('FEEDBACK');
  const [statusFilter, setStatusFilter] = useState<'ALL' | StudentFeedbackStatus>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [toast, setToast] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const showToast = (type: 'success' | 'error', text: string) => {
    setToast({ type, text });
    setTimeout(() => setToast(null), 4000);
  };

  useEffect(() => {
    setIsLoading(true);
    const unsubFb = subscribeToStudentFeedback(
      (data) => {
        setFeedbackList(data);
        setIsLoading(false);
      },
      (err) => {
        setErrorMessage(err.message || 'Failed to load student feedback.');
        setIsLoading(false);
      }
    );
    const unsubInst = subscribeToStudentInstallations(setInstallations, () => {});
    return () => {
      unsubFb();
      unsubInst();
    };
  }, []);

  const filteredFeedback = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    return feedbackList.filter((f) => {
      if (statusFilter !== 'ALL' && f.status !== statusFilter) return false;
      if (!q) return true;
      return (
        f.subject.toLowerCase().includes(q) ||
        f.description.toLowerCase().includes(q) ||
        f.category.toLowerCase().includes(q) ||
        f.busId.toLowerCase().includes(q) ||
        f.studentName.toLowerCase().includes(q)
      );
    });
  }, [feedbackList, statusFilter, searchQuery]);

  const versionBreakdown = useMemo(() => {
    const counts = new Map<string, number>();
    installations.forEach((inst) => {
      counts.set(inst.appVersion, (counts.get(inst.appVersion) || 0) + 1);
    });
    return Array.from(counts.entries()).sort((a, b) => b[1] - a[1]);
  }, [installations]);

  const handleStatusChange = async (id: string, status: StudentFeedbackStatus) => {
    try {
      await updateStudentFeedbackStatus(id, status);
      showToast('success', `Feedback marked as ${status}.`);
    } catch (err) {
      showToast('error', err instanceof Error ? err.message : 'Failed to update feedback.');
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await deleteStudentFeedback(id);
      showToast('success', 'Feedback deleted.');
    } catch (err) {
      showToast('error', err instanceof Error ? err.message : 'Failed to delete feedback.');
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

      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-slate-900 flex items-center gap-2">
            <GraduationCap className="w-5 h-5 text-blue-600" />
            <span>Student Feedback & App Installations</span>
          </h2>
          <p className="text-xs text-slate-500 mt-1">
            Review student bus issue reports, app feedback, and anonymous device adoption (students use FleetX without login).
          </p>
        </div>

        <div className="inline-flex rounded-xl bg-slate-100 p-1 text-xs font-semibold">
          <button
            onClick={() => setActiveTab('FEEDBACK')}
            className={`px-3 py-1.5 rounded-lg inline-flex items-center gap-1.5 ${
              activeTab === 'FEEDBACK' ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-600'
            }`}
          >
            <MessageSquare className="w-3.5 h-3.5" />
            <span>Feedback ({feedbackList.length})</span>
          </button>
          <button
            onClick={() => setActiveTab('DEVICES')}
            className={`px-3 py-1.5 rounded-lg inline-flex items-center gap-1.5 ${
              activeTab === 'DEVICES' ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-600'
            }`}
          >
            <Smartphone className="w-3.5 h-3.5" />
            <span>Installations ({installations.length})</span>
          </button>
        </div>
      </div>

      {errorMessage && <ErrorState title="Student Module Error" message={errorMessage} />}

      {activeTab === 'FEEDBACK' ? (
        <>
          <div className="bg-white rounded-2xl border border-slate-200 p-4 flex flex-col sm:flex-row gap-3 items-stretch sm:items-center justify-between">
            <div className="relative flex-1 max-w-md">
              <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search feedback by subject, bus, category, or student..."
                className="w-full pl-9 pr-3 py-2 text-xs border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <div className="flex items-center gap-2">
              {(['ALL', 'NEW', 'REVIEWED', 'RESOLVED'] as const).map((status) => (
                <button
                  key={status}
                  onClick={() => setStatusFilter(status)}
                  className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-colors ${
                    statusFilter === status
                      ? 'bg-blue-600 text-white'
                      : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                  }`}
                >
                  {status}
                </button>
              ))}
            </div>
          </div>

          {isLoading ? (
            <LoadingSpinner label="Loading student feedback..." />
          ) : filteredFeedback.length === 0 ? (
            <EmptyState
              title="No student feedback"
              description="No feedback or bus issue reports match your current filters."
              icon={MessageSquare}
            />
          ) : (
            <div className="grid grid-cols-1 gap-4">
              {filteredFeedback.map((fb) => (
                <div
                  key={fb.feedbackId}
                  className="bg-white rounded-2xl border border-slate-200 p-5 flex flex-col sm:flex-row sm:items-start justify-between gap-4"
                >
                  <div className="space-y-1.5">
                    <div className="flex flex-wrap items-center gap-2">
                      <span
                        className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                          fb.feedbackType === 'BUS'
                            ? 'bg-indigo-100 text-indigo-700'
                            : 'bg-purple-100 text-purple-700'
                        }`}
                      >
                        {fb.feedbackType}
                      </span>
                      <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-slate-100 text-slate-700">
                        {fb.category}
                      </span>
                      {fb.busId && (
                        <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-blue-50 text-blue-700">
                          Bus: {fb.busId}
                        </span>
                      )}
                      <span
                        className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                          fb.status === 'NEW'
                            ? 'bg-amber-100 text-amber-800'
                            : fb.status === 'REVIEWED'
                            ? 'bg-blue-100 text-blue-700'
                            : 'bg-emerald-100 text-emerald-700'
                        }`}
                      >
                        {fb.status}
                      </span>
                    </div>
                    <h3 className="text-sm font-bold text-slate-900">
                      {fb.subject || 'No subject'}
                    </h3>
                    <p className="text-xs text-slate-600 whitespace-pre-line">{fb.description}</p>
                    <p className="text-[11px] text-slate-400">
                      From: {fb.studentName || 'Anonymous'}
                      {fb.studentContact ? ` (${fb.studentContact})` : ''} •{' '}
                      {new Date(fb.timestamp).toLocaleString()}
                    </p>
                  </div>
                  <div className="flex items-center gap-2 self-end sm:self-start">
                    {fb.status === 'NEW' && (
                      <button
                        onClick={() => handleStatusChange(fb.feedbackId, 'REVIEWED')}
                        className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg bg-blue-50 text-blue-700 hover:bg-blue-100 text-xs font-semibold"
                      >
                        <Eye className="w-3.5 h-3.5" />
                        <span>Mark Reviewed</span>
                      </button>
                    )}
                    {fb.status !== 'RESOLVED' && (
                      <button
                        onClick={() => handleStatusChange(fb.feedbackId, 'RESOLVED')}
                        className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg bg-emerald-50 text-emerald-700 hover:bg-emerald-100 text-xs font-semibold"
                      >
                        <CheckCircle2 className="w-3.5 h-3.5" />
                        <span>Resolve</span>
                      </button>
                    )}
                    <button
                      onClick={() => handleDelete(fb.feedbackId)}
                      className="p-2 rounded-lg text-red-600 hover:bg-red-50"
                      title="Delete feedback"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      ) : (
        <div className="space-y-4">
          <div className="bg-white rounded-2xl border border-slate-200 p-5">
            <h3 className="text-sm font-bold text-slate-900 mb-3">
              App Version Distribution ({installations.length} tracked installs)
            </h3>
            {versionBreakdown.length === 0 ? (
              <p className="text-xs text-slate-500">No installation pings recorded yet.</p>
            ) : (
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                {versionBreakdown.map(([ver, count]) => (
                  <div
                    key={ver}
                    className="p-3 rounded-xl bg-slate-50 border border-slate-200 flex items-center justify-between"
                  >
                    <span className="text-xs font-bold text-slate-800">v{ver}</span>
                    <span className="text-xs font-semibold text-blue-600">{count}</span>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50 text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                  <th className="py-3 px-4">Installation ID</th>
                  <th className="py-3 px-4">App Version</th>
                  <th className="py-3 px-4">Language</th>
                  <th className="py-3 px-4">Last Active Date</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {installations.slice(0, 50).map((inst) => (
                  <tr key={inst.installationId} className="hover:bg-slate-50/70">
                    <td className="py-3 px-4 font-mono text-[11px] text-slate-600">
                      {inst.installationId.slice(0, 12)}...
                    </td>
                    <td className="py-3 px-4 font-semibold text-slate-900">v{inst.appVersion}</td>
                    <td className="py-3 px-4 uppercase text-slate-600">{inst.language}</td>
                    <td className="py-3 px-4 text-slate-600">{inst.lastActiveDate || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
};

export default StudentManagement;

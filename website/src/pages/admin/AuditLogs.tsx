import React, { useEffect, useState } from 'react';
import { FileText, ShieldCheck, Trash2, Repeat, UserCheck } from 'lucide-react';
import { useAdminAuth } from '../../context/AdminAuthContext';
import {
  clearHistoryWithAudit,
  subscribeToBusSwapHistory,
  subscribeToDriverReplacementHistory,
  subscribeToHistoryAuditLogs,
} from '../../services/operationsService';
import {
  BusSwapHistoryRecord,
  DriverReplacementHistoryRecord,
  HistoryAuditLogRecord,
} from '../../types/operations';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { EmptyState } from '../../components/common/EmptyState';
import { ErrorState } from '../../components/common/ErrorState';

export const AuditLogs: React.FC = () => {
  const { profile } = useAdminAuth();
  const [auditLogs, setAuditLogs] = useState<HistoryAuditLogRecord[]>([]);
  const [swapHistory, setSwapHistory] = useState<BusSwapHistoryRecord[]>([]);
  const [replacementHistory, setReplacementHistory] = useState<DriverReplacementHistoryRecord[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [tab, setTab] = useState<'AUDIT' | 'SWAP_HISTORY' | 'REPLACEMENT_HISTORY'>('AUDIT');
  const [isClearing, setIsClearing] = useState(false);

  useEffect(() => {
    setIsLoading(true);
    const unsubAudit = subscribeToHistoryAuditLogs(
      (data) => {
        setAuditLogs(data);
        setIsLoading(false);
      },
      (err) => {
        setErrorMessage(err.message || 'Failed to load audit logs.');
        setIsLoading(false);
      }
    );
    const unsubSwap = subscribeToBusSwapHistory(setSwapHistory, () => {});
    const unsubRep = subscribeToDriverReplacementHistory(setReplacementHistory, () => {});
    return () => {
      unsubAudit();
      unsubSwap();
      unsubRep();
    };
  }, []);

  const unclearedSwaps = swapHistory.filter((s) => !s.cleared);
  const unclearedReplacements = replacementHistory.filter((r) => !r.cleared);

  const handleClear = async (
    type: 'CLEAR_BUS_SWAP_HISTORY' | 'CLEAR_DRIVER_REPLACEMENT_HISTORY'
  ) => {
    const ids =
      type === 'CLEAR_BUS_SWAP_HISTORY'
        ? unclearedSwaps.map((s) => s.historyId)
        : unclearedReplacements.map((r) => r.historyId);
    if (ids.length === 0) return;

    setIsClearing(true);
    try {
      await clearHistoryWithAudit(
        type,
        ids,
        profile?.uid || '',
        profile?.name || profile?.email || 'Admin'
      );
    } finally {
      setIsClearing(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-slate-900 flex items-center gap-2">
            <FileText className="w-5 h-5 text-blue-600" />
            <span>Audit Logs & Operational History</span>
          </h2>
          <p className="text-xs text-slate-500 mt-1">
            Immutable admin clear-history audit logs alongside bus swap and driver replacement trails.
          </p>
        </div>

        <div className="inline-flex rounded-xl bg-slate-100 p-1 text-xs font-semibold">
          <button
            onClick={() => setTab('AUDIT')}
            className={`px-3 py-1.5 rounded-lg inline-flex items-center gap-1.5 ${
              tab === 'AUDIT' ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-600'
            }`}
          >
            <ShieldCheck className="w-3.5 h-3.5" />
            <span>Audit ({auditLogs.length})</span>
          </button>
          <button
            onClick={() => setTab('SWAP_HISTORY')}
            className={`px-3 py-1.5 rounded-lg inline-flex items-center gap-1.5 ${
              tab === 'SWAP_HISTORY' ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-600'
            }`}
          >
            <Repeat className="w-3.5 h-3.5" />
            <span>Swaps ({unclearedSwaps.length})</span>
          </button>
          <button
            onClick={() => setTab('REPLACEMENT_HISTORY')}
            className={`px-3 py-1.5 rounded-lg inline-flex items-center gap-1.5 ${
              tab === 'REPLACEMENT_HISTORY'
                ? 'bg-white text-slate-900 shadow-sm'
                : 'text-slate-600'
            }`}
          >
            <UserCheck className="w-3.5 h-3.5" />
            <span>Replacements ({unclearedReplacements.length})</span>
          </button>
        </div>
      </div>

      {errorMessage && <ErrorState title="Audit Log Error" message={errorMessage} />}

      {isLoading ? (
        <LoadingSpinner label="Loading audit trails..." />
      ) : tab === 'AUDIT' ? (
        auditLogs.length === 0 ? (
          <EmptyState
            title="No audit events yet"
            description="History clear events and administrative audit records will appear here."
            icon={ShieldCheck}
          />
        ) : (
          <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50 text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                  <th className="py-3 px-4">Action</th>
                  <th className="py-3 px-4">Admin</th>
                  <th className="py-3 px-4">Records Cleared</th>
                  <th className="py-3 px-4">Timestamp</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {auditLogs.map((log) => (
                  <tr key={log.auditId} className="hover:bg-slate-50/70">
                    <td className="py-3 px-4 font-semibold text-slate-900">{log.action}</td>
                    <td className="py-3 px-4 text-slate-700">{log.adminName}</td>
                    <td className="py-3 px-4 font-semibold text-blue-600">{log.clearedCount}</td>
                    <td className="py-3 px-4 text-slate-500">
                      {log.timestamp
                        ? new Date(log.timestamp).toLocaleString()
                        : `${log.dateFormatted} ${log.timeFormatted}`}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )
      ) : tab === 'SWAP_HISTORY' ? (
        <div className="space-y-3">
          {unclearedSwaps.length > 0 && (
            <div className="flex justify-end">
              <button
                disabled={isClearing}
                onClick={() => handleClear('CLEAR_BUS_SWAP_HISTORY')}
                className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-red-50 text-red-700 hover:bg-red-100 text-xs font-semibold disabled:opacity-50"
              >
                <Trash2 className="w-3.5 h-3.5" />
                <span>Archive Visible Swap History</span>
              </button>
            </div>
          )}
          {unclearedSwaps.length === 0 ? (
            <EmptyState
              title="No active bus swap history"
              description="All bus swap history entries are archived."
              icon={Repeat}
            />
          ) : (
            <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className="border-b border-slate-200 bg-slate-50 text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                    <th className="py-3 px-4">Swap</th>
                    <th className="py-3 px-4">Route</th>
                    <th className="py-3 px-4">Changed By</th>
                    <th className="py-3 px-4">Status</th>
                    <th className="py-3 px-4">Date</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {unclearedSwaps.map((h) => (
                    <tr key={h.historyId}>
                      <td className="py-3 px-4 font-semibold text-slate-900">
                        {h.originalBusId} → {h.replacementBusId}
                      </td>
                      <td className="py-3 px-4 text-slate-600">{h.routeId || '—'}</td>
                      <td className="py-3 px-4 text-slate-600">{h.changedByName || '—'}</td>
                      <td className="py-3 px-4 font-semibold text-slate-700">{h.status}</td>
                      <td className="py-3 px-4 text-slate-500">
                        {h.createdAt ? new Date(h.createdAt).toLocaleString() : h.date}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      ) : (
        <div className="space-y-3">
          {unclearedReplacements.length > 0 && (
            <div className="flex justify-end">
              <button
                disabled={isClearing}
                onClick={() => handleClear('CLEAR_DRIVER_REPLACEMENT_HISTORY')}
                className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-red-50 text-red-700 hover:bg-red-100 text-xs font-semibold disabled:opacity-50"
              >
                <Trash2 className="w-3.5 h-3.5" />
                <span>Archive Visible Replacement History</span>
              </button>
            </div>
          )}
          {unclearedReplacements.length === 0 ? (
            <EmptyState
              title="No active driver replacement history"
              description="All driver replacement history entries are archived."
              icon={UserCheck}
            />
          ) : (
            <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className="border-b border-slate-200 bg-slate-50 text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                    <th className="py-3 px-4">Bus</th>
                    <th className="py-3 px-4">Driver Change</th>
                    <th className="py-3 px-4">Changed By</th>
                    <th className="py-3 px-4">Status</th>
                    <th className="py-3 px-4">Created</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {unclearedReplacements.map((r) => (
                    <tr key={r.historyId}>
                      <td className="py-3 px-4 font-semibold text-slate-900">{r.busId}</td>
                      <td className="py-3 px-4 text-slate-700">
                        {r.previousDriverName} → <strong>{r.replacementDriverName}</strong>
                      </td>
                      <td className="py-3 px-4 text-slate-600">{r.changedByName || '—'}</td>
                      <td className="py-3 px-4 font-semibold text-slate-700">{r.status}</td>
                      <td className="py-3 px-4 text-slate-500">
                        {r.createdAt ? new Date(r.createdAt).toLocaleString() : '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default AuditLogs;

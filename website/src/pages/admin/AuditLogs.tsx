import React, { useEffect, useMemo, useState } from 'react';
import {
  FileText,
  ShieldCheck,
  Trash2,
  Repeat,
  UserCheck,
  Search,
  CheckCircle2,
  AlertTriangle,
} from 'lucide-react';
import { useAdminAuth } from '../../context/AdminAuthContext';
import {
  clearHistoryWithAudit,
  filterEligibleHistoryRecords,
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

type DateRangeFilter = 'ALL' | 'TODAY' | '7D' | '30D';
type StatusFilter = 'ALL' | 'ACTIVE' | 'CANCELLED' | 'COMPLETED';

function getDateRangeCutoff(range: DateRangeFilter, now: number = Date.now()): number | null {
  if (range === 'ALL') return null;
  if (range === 'TODAY') {
    const start = new Date(now);
    start.setHours(0, 0, 0, 0);
    return start.getTime();
  }
  const days = range === '7D' ? 7 : 30;
  return now - days * 86400 * 1000;
}

function matchesStatusFilter(rawStatus: string, filter: StatusFilter): boolean {
  if (filter === 'ALL') return true;
  const upper = (rawStatus || '').toUpperCase();
  if (filter === 'COMPLETED') {
    return upper === 'COMPLETED' || upper === 'RESOLVED' || upper === 'EXPIRED';
  }
  return upper === filter;
}

export const AuditLogs: React.FC = () => {
  const { profile } = useAdminAuth();
  const [auditLogs, setAuditLogs] = useState<HistoryAuditLogRecord[]>([]);
  const [swapHistory, setSwapHistory] = useState<BusSwapHistoryRecord[]>([]);
  const [replacementHistory, setReplacementHistory] = useState<DriverReplacementHistoryRecord[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [tab, setTab] = useState<'AUDIT' | 'SWAP_HISTORY' | 'REPLACEMENT_HISTORY'>('AUDIT');
  const [isClearing, setIsClearing] = useState(false);

  // Filters
  const [searchQuery, setSearchQuery] = useState('');
  const [dateRange, setDateRange] = useState<DateRangeFilter>('ALL');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');

  // Confirmation modal & toast
  const [confirmClearType, setConfirmClearType] = useState<
    'CLEAR_BUS_SWAP_HISTORY' | 'CLEAR_DRIVER_REPLACEMENT_HISTORY' | null
  >(null);
  const [toast, setToast] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const showToast = (type: 'success' | 'error', text: string) => {
    setToast({ type, text });
    setTimeout(() => setToast(null), 4000);
  };

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

  const unclearedSwaps = useMemo(
    () => swapHistory.filter((s) => !s.cleared),
    [swapHistory]
  );
  const unclearedReplacements = useMemo(
    () => replacementHistory.filter((r) => !r.cleared),
    [replacementHistory]
  );

  // Eligible records for clearing (excludes ACTIVE unless expired)
  const eligibleSwaps = useMemo(
    () => filterEligibleHistoryRecords(unclearedSwaps),
    [unclearedSwaps]
  );
  const eligibleReplacements = useMemo(
    () => filterEligibleHistoryRecords(unclearedReplacements),
    [unclearedReplacements]
  );

  const cutoffMs = useMemo(() => getDateRangeCutoff(dateRange), [dateRange]);
  const normalizedQuery = searchQuery.trim().toLowerCase();

  const filteredAuditLogs = useMemo(() => {
    return auditLogs.filter((log) => {
      if (cutoffMs !== null && log.timestamp && log.timestamp < cutoffMs) return false;
      if (!normalizedQuery) return true;
      return (
        log.action.toLowerCase().includes(normalizedQuery) ||
        log.adminName.toLowerCase().includes(normalizedQuery) ||
        log.dateFormatted.toLowerCase().includes(normalizedQuery)
      );
    });
  }, [auditLogs, cutoffMs, normalizedQuery]);

  const filteredSwaps = useMemo(() => {
    return unclearedSwaps.filter((h) => {
      if (cutoffMs !== null && h.createdAt && h.createdAt < cutoffMs) return false;
      if (!matchesStatusFilter(h.status, statusFilter)) return false;
      if (!normalizedQuery) return true;
      return (
        h.originalBusId.toLowerCase().includes(normalizedQuery) ||
        h.replacementBusId.toLowerCase().includes(normalizedQuery) ||
        (h.routeId || '').toLowerCase().includes(normalizedQuery) ||
        (h.changedByName || '').toLowerCase().includes(normalizedQuery) ||
        (h.reason || '').toLowerCase().includes(normalizedQuery)
      );
    });
  }, [unclearedSwaps, cutoffMs, statusFilter, normalizedQuery]);

  const filteredReplacements = useMemo(() => {
    return unclearedReplacements.filter((r) => {
      if (cutoffMs !== null && r.createdAt && r.createdAt < cutoffMs) return false;
      if (!matchesStatusFilter(r.status, statusFilter)) return false;
      if (!normalizedQuery) return true;
      return (
        r.busId.toLowerCase().includes(normalizedQuery) ||
        r.previousDriverName.toLowerCase().includes(normalizedQuery) ||
        r.replacementDriverName.toLowerCase().includes(normalizedQuery) ||
        (r.changedByName || '').toLowerCase().includes(normalizedQuery) ||
        (r.reason || '').toLowerCase().includes(normalizedQuery)
      );
    });
  }, [unclearedReplacements, cutoffMs, statusFilter, normalizedQuery]);

  const handleConfirmClear = async () => {
    if (!confirmClearType) return;
    const type = confirmClearType;
    setConfirmClearType(null);

    const eligible =
      type === 'CLEAR_BUS_SWAP_HISTORY' ? eligibleSwaps : eligibleReplacements;
    const ids = eligible.map((item) => item.historyId);
    if (ids.length === 0) {
      showToast('error', 'No eligible non-active history records to archive.');
      return;
    }

    setIsClearing(true);
    try {
      const clearedCount = await clearHistoryWithAudit(
        type,
        ids,
        profile?.uid || '',
        profile?.name || profile?.email || 'Admin'
      );
      showToast(
        'success',
        `Archived ${clearedCount} completed/cancelled history record${
          clearedCount === 1 ? '' : 's'
        }.`
      );
    } catch (err) {
      showToast(
        'error',
        err instanceof Error ? err.message : 'Failed to archive history records.'
      );
    } finally {
      setIsClearing(false);
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

      {/* Search, Date Range, and Status Filters */}
      <div className="bg-white rounded-2xl border border-slate-200 p-4 flex flex-col lg:flex-row gap-3 items-stretch lg:items-center justify-between">
        <div className="relative flex-1 max-w-md">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search by bus, driver, route, admin, or action..."
            className="w-full pl-9 pr-3 py-2 text-xs border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-1.5">
            {(['ALL', 'TODAY', '7D', '30D'] as const).map((range) => (
              <button
                key={range}
                onClick={() => setDateRange(range)}
                className={`px-2.5 py-1.5 rounded-lg text-xs font-semibold transition-colors ${
                  dateRange === range
                    ? 'bg-blue-600 text-white'
                    : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                }`}
              >
                {range}
              </button>
            ))}
          </div>

          {tab !== 'AUDIT' && (
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value as StatusFilter)}
              className="border border-slate-200 rounded-xl px-3 py-1.5 text-xs font-semibold text-slate-700 bg-white"
              aria-label="Status filter"
            >
              <option value="ALL">All Statuses</option>
              <option value="ACTIVE">ACTIVE</option>
              <option value="CANCELLED">CANCELLED</option>
              <option value="COMPLETED">COMPLETED</option>
            </select>
          )}
        </div>
      </div>

      {errorMessage && <ErrorState title="Audit Log Error" message={errorMessage} />}

      {isLoading ? (
        <LoadingSpinner label="Loading audit trails..." />
      ) : tab === 'AUDIT' ? (
        filteredAuditLogs.length === 0 ? (
          <EmptyState
            title="No audit events found"
            description="History clear events and administrative audit records matching your filters will appear here."
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
                {filteredAuditLogs.map((log) => (
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
          {eligibleSwaps.length > 0 && (
            <div className="flex items-center justify-between gap-2">
              <span className="text-[11px] text-slate-500">
                {eligibleSwaps.length} completed/cancelled record{eligibleSwaps.length === 1 ? '' : 's'} eligible to archive (active swaps are preserved).
              </span>
              <button
                disabled={isClearing}
                onClick={() => setConfirmClearType('CLEAR_BUS_SWAP_HISTORY')}
                className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-red-50 text-red-700 hover:bg-red-100 text-xs font-semibold disabled:opacity-50"
              >
                <Trash2 className="w-3.5 h-3.5" />
                <span>Archive Eligible Swap History ({eligibleSwaps.length})</span>
              </button>
            </div>
          )}
          {filteredSwaps.length === 0 ? (
            <EmptyState
              title="No matching bus swap history"
              description="No bus swap history entries match the current filters."
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
                  {filteredSwaps.map((h) => (
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
          {eligibleReplacements.length > 0 && (
            <div className="flex items-center justify-between gap-2">
              <span className="text-[11px] text-slate-500">
                {eligibleReplacements.length} completed/cancelled/expired record{eligibleReplacements.length === 1 ? '' : 's'} eligible to archive (active replacements are preserved).
              </span>
              <button
                disabled={isClearing}
                onClick={() => setConfirmClearType('CLEAR_DRIVER_REPLACEMENT_HISTORY')}
                className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-red-50 text-red-700 hover:bg-red-100 text-xs font-semibold disabled:opacity-50"
              >
                <Trash2 className="w-3.5 h-3.5" />
                <span>Archive Eligible Replacement History ({eligibleReplacements.length})</span>
              </button>
            </div>
          )}
          {filteredReplacements.length === 0 ? (
            <EmptyState
              title="No matching driver replacement history"
              description="No driver replacement history entries match the current filters."
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
                  {filteredReplacements.map((r) => (
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

      {/* Confirmation Modal before clearing eligible history */}
      {confirmClearType && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-xl border border-slate-200 space-y-4">
            <h3 className="text-base font-bold text-slate-900 flex items-center gap-2">
              <Trash2 className="w-4 h-4 text-red-600" />
              <span>Archive Eligible History</span>
            </h3>
            <p className="text-xs text-slate-600">
              Archive{' '}
              <strong>
                {confirmClearType === 'CLEAR_BUS_SWAP_HISTORY'
                  ? eligibleSwaps.length
                  : eligibleReplacements.length}
              </strong>{' '}
              non-active{' '}
              {confirmClearType === 'CLEAR_BUS_SWAP_HISTORY'
                ? 'bus swap'
                : 'driver replacement'}{' '}
              history record(s)? Active records will remain untouched, and this action will be
              recorded in the immutable audit log.
            </p>
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setConfirmClearType(null)}
                className="px-4 py-2 rounded-xl border border-slate-200 text-xs font-semibold text-slate-600 hover:bg-slate-50"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleConfirmClear}
                className="px-4 py-2 rounded-xl bg-red-600 hover:bg-red-700 text-white text-xs font-semibold"
              >
                Confirm Archive
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AuditLogs;

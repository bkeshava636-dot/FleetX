import React, { useEffect, useMemo, useState } from 'react';
import { BarChart3, Download, Users, Activity, Sparkles } from 'lucide-react';
import {
  subscribeToDailyAnalytics,
  subscribeToStudentInstallations,
} from '../../services/operationsService';
import { DailyAnalyticsRecord, StudentInstallationRecord } from '../../types/operations';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { EmptyState } from '../../components/common/EmptyState';
import { ErrorState } from '../../components/common/ErrorState';

export const AnalyticsReports: React.FC = () => {
  const [dailyStats, setDailyStats] = useState<DailyAnalyticsRecord[]>([]);
  const [installations, setInstallations] = useState<StudentInstallationRecord[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    setIsLoading(true);
    const unsubDaily = subscribeToDailyAnalytics(
      (data) => {
        setDailyStats(data);
        setIsLoading(false);
      },
      (err) => {
        setErrorMessage(err.message || 'Failed to load analytics.');
        setIsLoading(false);
      }
    );
    const unsubInst = subscribeToStudentInstallations(setInstallations, () => {});
    return () => {
      unsubDaily();
      unsubInst();
    };
  }, []);

  const totals = useMemo(() => {
    const sumEvents = dailyStats.reduce((acc, d) => acc + d.totalEvents, 0);
    const avgDau =
      dailyStats.length > 0
        ? Math.round(dailyStats.reduce((acc, d) => acc + d.dauCount, 0) / dailyStats.length)
        : 0;
    const newInstalls = dailyStats.reduce((acc, d) => acc + d.newInstallations, 0);
    return { sumEvents, avgDau, newInstalls };
  }, [dailyStats]);

  const handleExportCsv = () => {
    const header = 'date,dauCount,newInstallations,returningUsers,totalEvents\n';
    const rows = dailyStats
      .map((d) => `${d.date},${d.dauCount},${d.newInstallations},${d.returningUsers},${d.totalEvents}`)
      .join('\n');
    const blob = new Blob([header + rows], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `fleetx-analytics-${new Date().toISOString().slice(0, 10)}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-slate-900 flex items-center gap-2">
            <BarChart3 className="w-5 h-5 text-blue-600" />
            <span>Analytics & Reports</span>
          </h2>
          <p className="text-xs text-slate-500 mt-1">
            Privacy-safe daily active student metrics, feature usage events, and CSV export.
          </p>
        </div>
        <button
          onClick={handleExportCsv}
          disabled={dailyStats.length === 0}
          className="inline-flex items-center gap-2 px-4 py-2.5 bg-slate-900 hover:bg-slate-800 text-white text-xs font-semibold rounded-xl disabled:opacity-50"
        >
          <Download className="w-4 h-4" />
          <span>Export CSV</span>
        </button>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-4 gap-4">
        <div className="bg-white rounded-2xl border border-slate-200 p-4">
          <div className="flex items-center justify-between text-xs text-slate-500 font-medium">
            <span>Avg Daily Active</span>
            <Users className="w-4 h-4 text-blue-500" />
          </div>
          <p className="text-2xl font-bold text-slate-900 mt-1">{totals.avgDau}</p>
        </div>
        <div className="bg-white rounded-2xl border border-slate-200 p-4">
          <div className="flex items-center justify-between text-xs text-slate-500 font-medium">
            <span>30-Day Events</span>
            <Activity className="w-4 h-4 text-indigo-500" />
          </div>
          <p className="text-2xl font-bold text-slate-900 mt-1">{totals.sumEvents}</p>
        </div>
        <div className="bg-white rounded-2xl border border-slate-200 p-4">
          <div className="flex items-center justify-between text-xs text-slate-500 font-medium">
            <span>New Installs (30d)</span>
            <Sparkles className="w-4 h-4 text-emerald-500" />
          </div>
          <p className="text-2xl font-bold text-emerald-600 mt-1">{totals.newInstalls}</p>
        </div>
        <div className="bg-white rounded-2xl border border-slate-200 p-4">
          <div className="flex items-center justify-between text-xs text-slate-500 font-medium">
            <span>Total Tracked Devices</span>
            <BarChart3 className="w-4 h-4 text-amber-500" />
          </div>
          <p className="text-2xl font-bold text-slate-900 mt-1">{installations.length}</p>
        </div>
      </div>

      {errorMessage && <ErrorState title="Analytics Error" message={errorMessage} />}

      {isLoading ? (
        <LoadingSpinner label="Loading daily analytics..." />
      ) : dailyStats.length === 0 ? (
        <EmptyState
          title="No daily analytics yet"
          description="Daily student usage aggregates will appear here once student apps report activity."
          icon={BarChart3}
        />
      ) : (
        <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50 text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                  <th className="py-3 px-4">Date</th>
                  <th className="py-3 px-4">DAU</th>
                  <th className="py-3 px-4">New Installs</th>
                  <th className="py-3 px-4">Returning</th>
                  <th className="py-3 px-4">Total Events</th>
                  <th className="py-3 px-4">Top Languages</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {dailyStats.map((d) => (
                  <tr key={d.date} className="hover:bg-slate-50/70">
                    <td className="py-3 px-4 font-semibold text-slate-900">{d.date}</td>
                    <td className="py-3 px-4 font-bold text-blue-600">{d.dauCount}</td>
                    <td className="py-3 px-4 text-emerald-700 font-semibold">{d.newInstallations}</td>
                    <td className="py-3 px-4 text-slate-700">{d.returningUsers}</td>
                    <td className="py-3 px-4 text-slate-700">{d.totalEvents}</td>
                    <td className="py-3 px-4 text-slate-500">
                      {Object.entries(d.languages)
                        .map(([lang, cnt]) => `${lang.toUpperCase()}: ${cnt}`)
                        .join(', ') || '—'}
                    </td>
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

export default AnalyticsReports;

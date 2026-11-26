import React, { useEffect, useMemo, useState } from 'react';
import {
  BarChart3,
  Download,
  Users,
  Activity,
  Sparkles,
  Calendar,
  Smartphone,
  Layers,
} from 'lucide-react';
import {
  subscribeToDailyAnalytics,
  subscribeToStudentInstallations,
} from '../../services/operationsService';
import { DailyAnalyticsRecord, StudentInstallationRecord } from '../../types/operations';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { EmptyState } from '../../components/common/EmptyState';
import { ErrorState } from '../../components/common/ErrorState';

export type AnalyticsRangeDays = 7 | 14 | 30;

const EVENT_LABELS: Record<string, string> = {
  app_opened: 'App Opened',
  live_tracking_opened: 'Live Tracking Map',
  bus_details_viewed: 'Bus Details Sheet',
  stop_selected: 'Bus Stop Selected',
  announcement_viewed: 'Campus Announcement Viewed',
  bus_swap_alert_viewed: 'Bus Swap Alert Viewed',
  settings_opened: 'Settings Opened',
  update_available: 'Update Prompt Viewed',
  update_installed: 'Update Installed',
};

export function formatAnalyticsEventName(eventKey: string): string {
  if (EVENT_LABELS[eventKey]) return EVENT_LABELS[eventKey];
  return eventKey
    .split('_')
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

export function getIsoDateDaysAgo(days: number, nowMs: number = Date.now()): string {
  const d = new Date(nowMs - days * 24 * 60 * 60 * 1000);
  return d.toISOString().slice(0, 10);
}

export function computeActiveUsersSince(
  installations: Pick<StudentInstallationRecord, 'lastActiveDate'>[],
  days: number,
  nowMs: number = Date.now()
): { count: number; cutoffDate: string } {
  const cutoffDate = getIsoDateDaysAgo(days, nowMs);
  const count = installations.filter((inst) => {
    const date = (inst.lastActiveDate || '').trim();
    return /^\d{4}-\d{2}-\d{2}$/.test(date) && date >= cutoffDate;
  }).length;
  return { count, cutoffDate };
}

export function aggregateFeatureEvents(
  dailyStats: Pick<DailyAnalyticsRecord, 'events'>[]
): Array<{ event: string; label: string; count: number }> {
  const counts = new Map<string, number>();
  dailyStats.forEach((daily) => {
    if (!daily.events) return;
    Object.entries(daily.events).forEach(([event, rawCount]) => {
      const n = Number(rawCount) || 0;
      if (n <= 0) return;
      counts.set(event, (counts.get(event) || 0) + n);
    });
  });

  return Array.from(counts.entries())
    .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
    .map(([event, count]) => ({
      event,
      label: formatAnalyticsEventName(event),
      count,
    }));
}

export interface VersionDistributionEntry {
  version: string;
  deviceCount: number;
  devicePercent: number;
  activePings: number;
}

export function computeVersionDistribution(
  installations: Pick<StudentInstallationRecord, 'appVersion'>[],
  dailyStats: Pick<DailyAnalyticsRecord, 'versions'>[]
): VersionDistributionEntry[] {
  const deviceCounts = new Map<string, number>();
  installations.forEach((inst) => {
    const ver = (inst.appVersion || 'unknown').trim() || 'unknown';
    deviceCounts.set(ver, (deviceCounts.get(ver) || 0) + 1);
  });

  const pingCounts = new Map<string, number>();
  dailyStats.forEach((daily) => {
    if (!daily.versions) return;
    Object.entries(daily.versions).forEach(([ver, rawCount]) => {
      const cleanVer = (ver || 'unknown').trim() || 'unknown';
      const n = Number(rawCount) || 0;
      if (n <= 0) return;
      pingCounts.set(cleanVer, (pingCounts.get(cleanVer) || 0) + n);
    });
  });

  const allVersions = new Set<string>([...deviceCounts.keys(), ...pingCounts.keys()]);
  const totalDevices = installations.length;

  return Array.from(allVersions)
    .map((version) => {
      const deviceCount = deviceCounts.get(version) || 0;
      const activePings = pingCounts.get(version) || 0;
      const devicePercent =
        totalDevices > 0 ? Math.round((deviceCount / totalDevices) * 100) : 0;
      return { version, deviceCount, devicePercent, activePings };
    })
    .sort(
      (a, b) =>
        b.deviceCount - a.deviceCount ||
        b.activePings - a.activePings ||
        a.version.localeCompare(b.version)
    );
}

export function formatEventsSummary(events: Record<string, number> | undefined): string {
  if (!events) return '';
  return Object.entries(events)
    .filter(([, cnt]) => Number(cnt) > 0)
    .sort((a, b) => Number(b[1]) - Number(a[1]) || a[0].localeCompare(b[0]))
    .map(([ev, cnt]) => `${ev}:${Number(cnt)}`)
    .join('; ');
}

function escapeCsvField(value: string | number): string {
  const str = String(value);
  if (str.includes(',') || str.includes('"') || str.includes('\n')) {
    return `"${str.replace(/"/g, '""')}"`;
  }
  return str;
}

export const AnalyticsReports: React.FC = () => {
  const [dailyStats, setDailyStats] = useState<DailyAnalyticsRecord[]>([]);
  const [installations, setInstallations] = useState<StudentInstallationRecord[]>([]);
  const [rangeDays, setRangeDays] = useState<AnalyticsRangeDays>(30);
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

  const filteredDailyStats = useMemo(() => {
    const cutoff = getIsoDateDaysAgo(rangeDays);
    return dailyStats.filter((d) => d.date >= cutoff);
  }, [dailyStats, rangeDays]);

  const wau = useMemo(() => computeActiveUsersSince(installations, 7), [installations]);
  const mau = useMemo(() => computeActiveUsersSince(installations, 30), [installations]);

  const totals = useMemo(() => {
    const sumEvents = filteredDailyStats.reduce((acc, d) => acc + d.totalEvents, 0);
    const avgDau =
      filteredDailyStats.length > 0
        ? Math.round(
            filteredDailyStats.reduce((acc, d) => acc + d.dauCount, 0) /
              filteredDailyStats.length
          )
        : 0;
    const newInstalls = filteredDailyStats.reduce((acc, d) => acc + d.newInstallations, 0);
    return { sumEvents, avgDau, newInstalls };
  }, [filteredDailyStats]);

  const aggregatedEvents = useMemo(
    () => aggregateFeatureEvents(filteredDailyStats),
    [filteredDailyStats]
  );

  const versionDistribution = useMemo(
    () => computeVersionDistribution(installations, filteredDailyStats),
    [installations, filteredDailyStats]
  );

  const handleExportCsv = () => {
    const header = 'date,dauCount,newInstallations,returningUsers,totalEvents,eventsSummary\n';
    const rows = filteredDailyStats
      .map((d) =>
        [
          d.date,
          d.dauCount,
          d.newInstallations,
          d.returningUsers,
          d.totalEvents,
          escapeCsvField(formatEventsSummary(d.events)),
        ].join(',')
      )
      .join('\n');
    const blob = new Blob([header + rows], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `fleetx-analytics-${rangeDays}d-${new Date().toISOString().slice(0, 10)}.csv`;
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
            Anonymous student app telemetry, active device cohorts, feature usage, and CSV export.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <div className="inline-flex items-center bg-white border border-slate-200 rounded-xl p-1">
            <Calendar className="w-3.5 h-3.5 text-slate-400 ml-2 mr-1.5" />
            {([7, 14, 30] as const).map((days) => (
              <button
                key={days}
                type="button"
                onClick={() => setRangeDays(days)}
                className={`px-2.5 py-1 rounded-lg text-xs font-semibold transition-colors ${
                  rangeDays === days
                    ? 'bg-blue-600 text-white'
                    : 'text-slate-600 hover:bg-slate-100'
                }`}
              >
                {days}D
              </button>
            ))}
          </div>

          <button
            onClick={handleExportCsv}
            disabled={filteredDailyStats.length === 0}
            className="inline-flex items-center gap-2 px-4 py-2.5 bg-slate-900 hover:bg-slate-800 text-white text-xs font-semibold rounded-xl disabled:opacity-50"
          >
            <Download className="w-4 h-4" />
            <span>Export CSV ({rangeDays}D)</span>
          </button>
        </div>
      </div>

      {/* Core Metric Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6 gap-4">
        <div className="bg-white rounded-2xl border border-slate-200 p-4">
          <div className="flex items-center justify-between text-xs text-slate-500 font-medium">
            <span>Avg DAU ({rangeDays}D)</span>
            <Users className="w-4 h-4 text-blue-500" />
          </div>
          <p className="text-2xl font-bold text-slate-900 mt-1">{totals.avgDau}</p>
          <p className="text-[11px] text-slate-400 mt-0.5">Mean daily opens</p>
        </div>

        <div className="bg-white rounded-2xl border border-slate-200 p-4">
          <div className="flex items-center justify-between text-xs text-slate-500 font-medium">
            <span>7-Day WAU</span>
            <Users className="w-4 h-4 text-violet-500" />
          </div>
          <p className="text-2xl font-bold text-violet-700 mt-1">{wau.count}</p>
          <p className="text-[11px] text-slate-400 mt-0.5" title={`lastActiveDate >= ${wau.cutoffDate}`}>
            Active since {wau.cutoffDate}
          </p>
        </div>

        <div className="bg-white rounded-2xl border border-slate-200 p-4">
          <div className="flex items-center justify-between text-xs text-slate-500 font-medium">
            <span>30-Day MAU</span>
            <Users className="w-4 h-4 text-amber-500" />
          </div>
          <p className="text-2xl font-bold text-amber-600 mt-1">{mau.count}</p>
          <p className="text-[11px] text-slate-400 mt-0.5" title={`lastActiveDate >= ${mau.cutoffDate}`}>
            Active since {mau.cutoffDate}
          </p>
        </div>

        <div className="bg-white rounded-2xl border border-slate-200 p-4">
          <div className="flex items-center justify-between text-xs text-slate-500 font-medium">
            <span>Events ({rangeDays}D)</span>
            <Activity className="w-4 h-4 text-indigo-500" />
          </div>
          <p className="text-2xl font-bold text-slate-900 mt-1">{totals.sumEvents}</p>
          <p className="text-[11px] text-slate-400 mt-0.5">Total feature interactions</p>
        </div>

        <div className="bg-white rounded-2xl border border-slate-200 p-4">
          <div className="flex items-center justify-between text-xs text-slate-500 font-medium">
            <span>New Installs ({rangeDays}D)</span>
            <Sparkles className="w-4 h-4 text-emerald-500" />
          </div>
          <p className="text-2xl font-bold text-emerald-600 mt-1">{totals.newInstalls}</p>
          <p className="text-[11px] text-slate-400 mt-0.5">First-time app opens</p>
        </div>

        <div className="bg-white rounded-2xl border border-slate-200 p-4">
          <div className="flex items-center justify-between text-xs text-slate-500 font-medium">
            <span>Tracked Devices</span>
            <Smartphone className="w-4 h-4 text-slate-500" />
          </div>
          <p className="text-2xl font-bold text-slate-900 mt-1">{installations.length}</p>
          <p className="text-[11px] text-slate-400 mt-0.5">Observed UUIDs</p>
        </div>
      </div>

      {errorMessage && <ErrorState title="Analytics Error" message={errorMessage} />}

      {isLoading ? (
        <LoadingSpinner label="Loading daily analytics..." />
      ) : (
        <>
          {/* Feature Usage Events & App Version Distribution */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div className="bg-white rounded-2xl border border-slate-200 p-5 space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2">
                    <Layers className="w-4 h-4 text-blue-600" />
                    <span>Feature Usage Events ({rangeDays}D)</span>
                  </h3>
                  <p className="text-[11px] text-slate-500 mt-0.5">
                    Aggregated feature interactions sorted by frequency
                  </p>
                </div>
                <span className="text-xs font-semibold text-slate-500">
                  {aggregatedEvents.length} types
                </span>
              </div>

              {aggregatedEvents.length === 0 ? (
                <p className="text-xs text-slate-400 py-4">
                  No feature telemetry collected for the selected {rangeDays}-day window.
                </p>
              ) : (
                <div className="space-y-3">
                  {aggregatedEvents.map((item) => {
                    const maxCount = aggregatedEvents[0]?.count || 1;
                    const pct = Math.min(100, Math.round((item.count / maxCount) * 100));
                    return (
                      <div key={item.event} className="space-y-1">
                        <div className="flex items-center justify-between text-xs">
                          <span className="font-medium text-slate-800" title={item.event}>
                            {item.label}
                          </span>
                          <span className="font-semibold text-slate-600">
                            {item.count.toLocaleString()} events
                          </span>
                        </div>
                        <div className="h-2 w-full bg-slate-100 rounded-full overflow-hidden">
                          <div
                            className="h-full bg-blue-600 rounded-full transition-all"
                            style={{ width: `${pct}%` }}
                          />
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            <div className="bg-white rounded-2xl border border-slate-200 p-5 space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2">
                    <Smartphone className="w-4 h-4 text-indigo-600" />
                    <span>App Version Distribution</span>
                  </h3>
                  <p className="text-[11px] text-slate-500 mt-0.5">
                    Installed devices (student_installations) and {rangeDays}D daily active pings
                  </p>
                </div>
                <span className="text-xs font-semibold text-slate-500">
                  {versionDistribution.length} versions
                </span>
              </div>

              {versionDistribution.length === 0 ? (
                <p className="text-xs text-slate-400 py-4">
                  No app version telemetry recorded yet.
                </p>
              ) : (
                <div className="divide-y divide-slate-100">
                  {versionDistribution.map((v) => (
                    <div
                      key={v.version}
                      className="py-2.5 flex items-center justify-between text-xs"
                    >
                      <div>
                        <span className="font-semibold text-slate-900">FleetX v{v.version}</span>
                        {v.activePings > 0 && (
                          <span className="ml-2 text-[11px] text-slate-500">
                            ({v.activePings.toLocaleString()} {rangeDays}D pings)
                          </span>
                        )}
                      </div>
                      <div className="text-right">
                        <span className="font-bold text-indigo-600">
                          {v.deviceCount} {v.deviceCount === 1 ? 'device' : 'devices'}
                        </span>
                        {installations.length > 0 && (
                          <span className="ml-1.5 text-[11px] text-slate-500">
                            ({v.devicePercent}%)
                          </span>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>

          {filteredDailyStats.length === 0 ? (
            <EmptyState
              title="No daily analytics in this range"
              description={`No daily student usage aggregates found in the last ${rangeDays} days.`}
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
                    {filteredDailyStats.map((d) => (
                      <tr key={d.date} className="hover:bg-slate-50/70">
                        <td className="py-3 px-4 font-semibold text-slate-900">{d.date}</td>
                        <td className="py-3 px-4 font-bold text-blue-600">{d.dauCount}</td>
                        <td className="py-3 px-4 text-emerald-700 font-semibold">
                          {d.newInstallations}
                        </td>
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
        </>
      )}
    </div>
  );
};

export default AnalyticsReports;

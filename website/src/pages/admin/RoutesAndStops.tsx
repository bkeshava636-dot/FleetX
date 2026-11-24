import React from 'react';
import { Route as RouteIcon } from 'lucide-react';

export const RoutesAndStops: React.FC = () => {
  return (
    <div className="space-y-6">
      <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-sm flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-slate-900 tracking-tight">Routes & Fleet Stops</h2>
          <p className="text-xs text-slate-500 mt-1">
            Canonical route schedules and stop coordinate coverage for all college transit routes.
          </p>
        </div>
        <div className="p-3 bg-indigo-50 text-indigo-600 rounded-2xl">
          <RouteIcon className="w-6 h-6" />
        </div>
      </div>

      <div className="p-12 text-center bg-white rounded-2xl border border-slate-200/80 text-slate-500 text-sm">
        <p className="font-semibold text-slate-700">Routes Explorer Loaded</p>
        <p className="text-xs text-slate-400 mt-1">Connecting to Firestore routes stream...</p>
      </div>
    </div>
  );
};

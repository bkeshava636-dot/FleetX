import React from 'react';
import { MapPin } from 'lucide-react';

export const StopCoordinateSubmissions: React.FC = () => {
  return (
    <div className="space-y-6">
      <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-sm flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-slate-900 tracking-tight">Stop Coordinate Submissions</h2>
          <p className="text-xs text-slate-500 mt-1">
            Crowdsourced bus stop coordinates submitted by students. Review on Google Maps and approve to update the route stop data.
          </p>
        </div>
        <div className="p-3 bg-blue-50 text-blue-600 rounded-2xl">
          <MapPin className="w-6 h-6" />
        </div>
      </div>

      <div className="p-12 text-center bg-white rounded-2xl border border-slate-200/80 text-slate-500 text-sm">
        <p className="font-semibold text-slate-700">Submissions Review Console Loaded</p>
        <p className="text-xs text-slate-400 mt-1">Connecting to Firestore submissions stream...</p>
      </div>
    </div>
  );
};

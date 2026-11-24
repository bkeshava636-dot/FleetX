import React from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft, MapPin, Navigation } from 'lucide-react';
import logoUrl from '../../assets/logo/fleetx-logo.png';

export const SubmitStopCoordinates: React.FC = () => {
  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      {/* Top Navbar */}
      <header className="bg-white border-b border-slate-200 sticky top-0 z-30">
        <div className="max-w-4xl mx-auto px-4 h-16 flex items-center justify-between">
          <Link to="/" className="flex items-center gap-2 text-xs font-semibold text-slate-600 hover:text-slate-900">
            <ArrowLeft className="w-4 h-4" />
            <span>Back to FleetX</span>
          </Link>

          <div className="flex items-center gap-2">
            <img src={logoUrl} alt="FleetX Logo" className="w-7 h-7 rounded-lg" />
            <span className="text-sm font-bold text-slate-900">
              Fleet<span className="text-blue-600">X</span> Stop Portal
            </span>
          </div>

          <div className="text-xs text-slate-400 font-medium">Student Crowdsourcing</div>
        </div>
      </header>

      {/* Main Container */}
      <main className="flex-1 max-w-4xl w-full mx-auto p-4 sm:p-6 lg:p-8 space-y-6">
        <div className="bg-white rounded-2xl p-6 border border-slate-200/80 shadow-sm">
          <div className="flex items-start gap-4">
            <div className="p-3 bg-blue-50 text-blue-600 rounded-2xl flex-shrink-0">
              <MapPin className="w-6 h-6" />
            </div>
            <div>
              <h1 className="text-xl font-bold text-slate-900 tracking-tight">Submit Bus Stop Location</h1>
              <p className="text-xs text-slate-500 mt-1.5 leading-relaxed">
                Help improve transit accuracy for everyone. Select your college bus stop and mark the exact roadside pickup point on Google Maps. All submissions are reviewed by the transport administrator before going live.
              </p>
            </div>
          </div>
        </div>

        <div className="bg-white rounded-2xl p-8 border border-slate-200/80 text-center text-slate-500 text-sm">
          <Navigation className="w-8 h-8 text-blue-500 mx-auto mb-2 animate-bounce" />
          <p className="font-semibold text-slate-700">Stop Submission Interface Initialized</p>
          <p className="text-xs text-slate-400 mt-1">Google Maps stop selector loading...</p>
        </div>
      </main>
    </div>
  );
};

export default SubmitStopCoordinates;

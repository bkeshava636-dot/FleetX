import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { ExternalLink, Shield, MapPin } from 'lucide-react';
import logoUrl from '../assets/logo/fleetx-logo.png';
import { ReleaseInfo } from '../types/github';

interface FooterProps {
  latestRelease: ReleaseInfo | null;
}

export const Footer: React.FC<FooterProps> = ({ latestRelease }) => {
  const [privacyModalOpen, setPrivacyModalOpen] = useState(false);

  return (
    <footer className="bg-slate-900 text-slate-300 py-14 border-t border-slate-800">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 md:grid-cols-12 gap-10 pb-12 border-b border-slate-800">
          
          {/* Brand Col */}
          <div className="md:col-span-5 space-y-4">
            <div className="flex items-center gap-3">
              <img
                src={logoUrl}
                alt="FleetX Logo"
                className="w-10 h-10 rounded-xl bg-white p-0.5 object-cover"
              />
              <span className="text-xl font-bold tracking-tight text-white">
                Fleet<span className="text-blue-400">X</span>
              </span>
            </div>
            <p className="text-xs text-slate-400 max-w-sm leading-relaxed">
              Official real-time campus bus tracking and transit intelligence platform. Dedicated to providing dependable arrival estimates and route clarity for college commuters.
            </p>
            {latestRelease && (
              <div className="inline-flex items-center gap-2 text-[11px] bg-slate-800 text-slate-300 px-3 py-1 rounded-lg border border-slate-700">
                <span className="w-2 h-2 rounded-full bg-emerald-400" />
                <span>Current Release: v{latestRelease.version}</span>
              </div>
            )}
          </div>

          {/* Navigation Links */}
          <div className="md:col-span-3 space-y-3">
            <h4 className="text-xs font-bold uppercase tracking-wider text-white">
              Navigation
            </h4>
            <ul className="space-y-2 text-xs">
              <li>
                <a href="#home" className="hover:text-white transition-colors">
                  Home
                </a>
              </li>
              <li>
                <a href="#features" className="hover:text-white transition-colors">
                  Features
                </a>
              </li>
              <li>
                <a href="#how-it-works" className="hover:text-white transition-colors">
                  How It Works
                </a>
              </li>
              <li>
                <a href="#preview" className="hover:text-white transition-colors">
                  App Preview
                </a>
              </li>
              <li>
                <a href="#download" className="hover:text-white transition-colors">
                  Download Latest APK
                </a>
              </li>
              <li>
                <a href="#releases" className="hover:text-white transition-colors">
                  Previous Releases
                </a>
              </li>
              <li>
                <Link to="/submit-stop" className="text-blue-400 hover:text-blue-300 font-medium transition-colors flex items-center gap-1.5">
                  <MapPin className="w-3 h-3" />
                  <span>Submit Bus Stop Location</span>
                </Link>
              </li>
              <li>
                <Link to="/admin/login" className="text-slate-400 hover:text-white transition-colors flex items-center gap-1.5">
                  <Shield className="w-3 h-3 text-slate-500" />
                  <span>Admin Management Console</span>
                </Link>
              </li>
            </ul>
          </div>

          {/* Resources & Open Source */}
          <div className="md:col-span-4 space-y-3">
            <h4 className="text-xs font-bold uppercase tracking-wider text-white">
              Official Resources
            </h4>
            <ul className="space-y-2 text-xs">
              <li>
                <a
                  href="https://github.com/bkeshava636-dot/FleetX"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1.5 hover:text-white transition-colors"
                >
                  <span>GitHub Repository</span>
                  <ExternalLink className="w-3 h-3 text-slate-400" />
                </a>
              </li>
              <li>
                <a
                  href="https://github.com/bkeshava636-dot/FleetX/releases"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1.5 hover:text-white transition-colors"
                >
                  <span>All GitHub Releases</span>
                  <ExternalLink className="w-3 h-3 text-slate-400" />
                </a>
              </li>
              <li>
                <button
                  type="button"
                  onClick={() => setPrivacyModalOpen(true)}
                  className="hover:text-white transition-colors text-left"
                >
                  Privacy Policy & Disclaimers
                </button>
              </li>
              <li className="pt-2 text-[11px] text-slate-500">
                <span>Support & Transportation Inquiries:</span>
                <span className="block text-slate-400 mt-0.5">Please contact your college transportation administrator.</span>
              </li>
              <li>
                <Link to="/submit-stop" className="text-blue-400 hover:text-blue-300 font-medium transition-colors flex items-center gap-1.5">
                  <MapPin className="w-3 h-3" />
                  <span>Submit Bus Stop Location</span>
                </Link>
              </li>
              <li>
                <Link to="/admin/login" className="text-slate-400 hover:text-white transition-colors flex items-center gap-1.5">
                  <Shield className="w-3 h-3 text-slate-500" />
                  <span>Admin Management Console</span>
                </Link>
              </li>
            </ul>
          </div>

        </div>

        {/* Bottom copyright */}
        <div className="pt-8 flex flex-col sm:flex-row items-center justify-between gap-4 text-xs text-slate-500">
          <p>© {new Date().getFullYear()} FleetX. Built for College Transit.</p>
          <p className="text-[11px]">
            Direct GitHub Release distribution • Android is a trademark of Google LLC.
          </p>
        </div>
      </div>

      {/* Privacy Policy Modal */}
      {privacyModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-in fade-in">
          <div className="bg-white text-college-on-surface max-w-lg w-full p-6 sm:p-8 rounded-3xl shadow-2xl border border-slate-200 space-y-4 max-h-[85vh] overflow-y-auto">
            <div className="flex items-center gap-3">
              <div className="p-2 bg-blue-50 text-college-primary rounded-xl">
                <Shield className="w-5 h-5" />
              </div>
              <h3 className="text-lg font-bold">Privacy Policy & Transit Notices</h3>
            </div>

            <div className="space-y-3 text-xs text-college-on-surface-variant leading-relaxed">
              <p>
                <strong>Student Privacy:</strong> FleetX does not require student account registration or personal profiling to track campus transit. Live bus locations are broadcast publicly for college students.
              </p>
              <p>
                <strong>Location Data:</strong> Student background location is only collected if you explicitly enable the optional "Automatic Ride Contributor" feature to assist crowd-sourced bus tracking during transit windows.
              </p>
              <p>
                <strong>ETA Disclaimers:</strong> Estimated arrival times are mathematical approximations calculated from live coordinates and historical transit speed. Traffic, weather, detours, and road conditions may affect actual arrival.
              </p>
              <p>
                <strong>Official Distribution:</strong> The FleetX Android APK is distributed exclusively through verified GitHub Releases.
              </p>
            </div>

            <div className="pt-4 border-t border-slate-200 flex justify-end">
              <button
                type="button"
                onClick={() => setPrivacyModalOpen(false)}
                className="bg-college-primary hover:bg-college-primary-dark text-white text-xs font-semibold px-4 py-2 rounded-xl transition-colors"
              >
                Close Notice
              </button>
            </div>
          </div>
        </div>
      )}
    </footer>
  );
};

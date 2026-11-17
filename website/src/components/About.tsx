import React from 'react';
import { ShieldCheck, Zap, Lock, MapPin } from 'lucide-react';

export const About: React.FC = () => {
  return (
    <section id="about" className="py-20 bg-white border-t border-slate-200/80">
      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Section Header */}
        <div className="text-center max-w-3xl mx-auto mb-14">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-college-primary-container text-college-primary-dark text-xs font-semibold uppercase tracking-wider mb-4">
            About The Project
          </div>
          <h2 className="text-3xl sm:text-4xl font-extrabold text-college-on-surface tracking-tight">
            About FleetX
          </h2>
          <p className="mt-4 text-base sm:text-lg text-college-on-surface-variant leading-relaxed">
            FleetX is designed to make college transportation more convenient and transparent. It helps students stay informed about their bus location, routes, estimated arrival times and important transportation updates.
          </p>
        </div>

        {/* Guiding Principles Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 text-left">
          <div className="bg-college-surface p-6 rounded-2xl border border-slate-200/90">
            <div className="w-10 h-10 rounded-xl bg-blue-50 text-college-primary flex items-center justify-center mb-4">
              <Zap className="w-5 h-5" />
            </div>
            <h3 className="text-base font-bold text-college-on-surface mb-2">
              Low-Latency Real-Time Engine
            </h3>
            <p className="text-xs text-college-on-surface-variant leading-relaxed">
              Powered by Google Firebase Realtime Database and Fused Location Provider, delivering continuous coordinate updates within milliseconds while preserving driver battery life.
            </p>
          </div>

          <div className="bg-college-surface p-6 rounded-2xl border border-slate-200/90">
            <div className="w-10 h-10 rounded-xl bg-emerald-50 text-status-green flex items-center justify-center mb-4">
              <ShieldCheck className="w-5 h-5" />
            </div>
            <h3 className="text-base font-bold text-college-on-surface mb-2">
              Privacy-First Transit Architecture
            </h3>
            <p className="text-xs text-college-on-surface-variant leading-relaxed">
              Students can view live routes and transit timetables without forced personal data collection. Driver accounts are strictly managed and role-authenticated.
            </p>
          </div>

          <div className="bg-college-surface p-6 rounded-2xl border border-slate-200/90">
            <div className="w-10 h-10 rounded-xl bg-purple-50 text-purple-600 flex items-center justify-center mb-4">
              <Lock className="w-5 h-5" />
            </div>
            <h3 className="text-base font-bold text-college-on-surface mb-2">
              Safe & Direct APK Distribution
            </h3>
            <p className="text-xs text-college-on-surface-variant leading-relaxed">
              Every build is compiled directly through GitHub Actions with automated verification checksums, ensuring complete release integrity from source code to device.
            </p>
          </div>

          <div className="bg-college-surface p-6 rounded-2xl border border-slate-200/90">
            <div className="w-10 h-10 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center mb-4">
              <MapPin className="w-5 h-5" />
            </div>
            <h3 className="text-base font-bold text-college-on-surface mb-2">
              Campus-Focused Routing
            </h3>
            <p className="text-xs text-college-on-surface-variant leading-relaxed">
              Specifically calibrated for college routes and campus geography (BITM Bellary), keeping students reliably on schedule for morning and evening shifts.
            </p>
          </div>
        </div>

      </div>
    </section>
  );
};

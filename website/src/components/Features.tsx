import React from 'react';
import {
  Navigation,
  Clock,
  MapPin,
  UserCheck,
  RefreshCw,
  BellRing,
  AlertCircle
} from 'lucide-react';

export const Features: React.FC = () => {
  const features = [
    {
      id: 'live-tracking',
      title: 'Live Bus Tracking',
      description:
        'Follow your bus in real time on an interactive Google Map with sub-second position updates streamed directly from the driver’s on-board tracker.',
      highlight: 'Real-time GPS stream',
      icon: Navigation,
      iconBg: 'bg-blue-50 text-college-primary',
      badge: 'Real-Time'
    },
    {
      id: 'eta-calculation',
      title: 'Estimated Arrival Time (ETA)',
      description:
        'Check dynamic arrival estimates for your designated stop, computed using live travel speed, distance remaining, and route milestones.',
      highlight: 'Stop-by-stop countdown',
      icon: Clock,
      iconBg: 'bg-emerald-50 text-status-green',
      badge: 'Live Engine',
      disclaimer: 'ETA is approximate and subject to road traffic and transit conditions.'
    },
    {
      id: 'routes-and-stops',
      title: 'Bus Routes & Stops',
      description:
        'Explore complete college route paths, ordered stop sequences, and campus terminal points with clear distance markers for every scheduled stop.',
      highlight: 'Complete route overview',
      icon: MapPin,
      iconBg: 'bg-indigo-50 text-indigo-600',
      badge: 'Full Directory'
    },
    {
      id: 'driver-info',
      title: 'Driver Information',
      description:
        'Access verified driver contact details, registered bus numbers, and phone reachability to resolve commute inquiries smoothly and safely.',
      highlight: 'Direct verified contact',
      icon: UserCheck,
      iconBg: 'bg-amber-50 text-amber-600',
      badge: 'Verified'
    },
    {
      id: 'swap-alerts',
      title: 'Bus Swap Alerts',
      description:
        'Stay immediately informed whenever maintenance or schedule shifts require a temporary bus or driver replacement for your stop.',
      highlight: 'Instant change notifications',
      icon: RefreshCw,
      iconBg: 'bg-purple-50 text-purple-600',
      badge: 'Targeted FCM'
    },
    {
      id: 'announcements',
      title: 'Campus Announcements',
      description:
        'Receive official transportation advisories, holiday schedules, weather detours, and emergency notifications published by transit administrators.',
      highlight: 'Bilingual alerts (English & Kannada)',
      icon: BellRing,
      iconBg: 'bg-rose-50 text-rose-600',
      badge: 'Official Notices'
    }
  ];

  return (
    <section id="features" className="py-20 bg-white border-y border-slate-200/80">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Section Header */}
        <div className="text-center max-w-3xl mx-auto mb-16">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-college-primary-container text-college-primary-dark text-xs font-semibold uppercase tracking-wider mb-4">
            Core Capabilities
          </div>
          <h2 className="text-3xl sm:text-4xl font-extrabold text-college-on-surface tracking-tight">
            Engineered for Everyday College Commutes
          </h2>
          <p className="mt-4 text-base sm:text-lg text-college-on-surface-variant font-normal leading-relaxed">
            Every feature in FleetX is built to give students complete peace of mind, reliable arrival times, and transparent route visibility.
          </p>
        </div>

        {/* Feature Cards Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 sm:gap-8">
          {features.map(f => {
            const IconComponent = f.icon;
            return (
              <div
                key={f.id}
                className="group relative flex flex-col justify-between bg-college-surface p-7 rounded-2xl border border-slate-200/90 shadow-card hover:shadow-card-hover hover:border-college-primary/40 transition-all duration-300"
              >
                <div>
                  {/* Card Top Row: Icon + Badge */}
                  <div className="flex items-center justify-between mb-5">
                    <div
                      className={`w-12 h-12 rounded-xl flex items-center justify-center ${f.iconBg} shadow-sm group-hover:scale-110 transition-transform duration-200`}
                    >
                      <IconComponent className="w-6 h-6" />
                    </div>
                    <span className="text-[11px] font-semibold tracking-wide text-college-on-surface-variant bg-white px-2.5 py-1 rounded-lg border border-slate-200">
                      {f.badge}
                    </span>
                  </div>

                  {/* Title */}
                  <h3 className="text-xl font-bold text-college-on-surface mb-2.5">
                    {f.title}
                  </h3>

                  {/* Description */}
                  <p className="text-sm text-college-on-surface-variant leading-relaxed mb-4">
                    {f.description}
                  </p>
                </div>

                {/* Optional Disclaimer (for ETA card) or Key Highlight */}
                <div className="pt-4 border-t border-slate-200/80 mt-2">
                  {f.disclaimer ? (
                    <div className="flex items-start gap-1.5 text-xs text-amber-700 bg-amber-50/80 p-2.5 rounded-lg border border-amber-200/60">
                      <AlertCircle className="w-3.5 h-3.5 flex-shrink-0 mt-0.5" />
                      <span className="leading-tight">{f.disclaimer}</span>
                    </div>
                  ) : (
                    <div className="text-xs font-medium text-college-primary flex items-center gap-1">
                      <span>✓</span>
                      <span>{f.highlight}</span>
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>

      </div>
    </section>
  );
};

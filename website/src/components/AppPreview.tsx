import React, { useState } from 'react';
import mapScreenImg from '../assets/screenshots/screen-map-view.jpg';
import studentHomeImg from '../assets/screenshots/screen-student-home.jpg';
import scheduleStopsImg from '../assets/screenshots/screen-schedule-stops.jpg';
import busPassImg from '../assets/screenshots/screen-bus-pass.jpg';

interface ScreenItem {
  id: string;
  title: string;
  subtitle: string;
  description: string;
  badge: string;
  image: string;
}

export const AppPreview: React.FC = () => {
  const screens: ScreenItem[] = [
    {
      id: 'screen-map',
      title: 'Live Interactive Map',
      subtitle: 'Real-Time GPS Marker & Route Progress',
      description:
        'Students watch the bus moving smoothly in real time across calibrated college route paths with live orientation and current road location.',
      badge: 'Live Map View',
      image: mapScreenImg
    },
    {
      id: 'screen-student',
      title: 'Student Dashboard & Driver Info',
      subtitle: 'Selected Bus & Dynamic Stop Countdowns',
      description:
        'Instant status badges, estimated arrival times, assigned bus details, and one-tap direct calling for your verified college driver.',
      badge: 'Track Tab',
      image: studentHomeImg
    },
    {
      id: 'screen-schedule',
      title: 'Operating Windows & Stops',
      subtitle: 'Morning & Evening Transit Hours',
      description:
        'Complete view of daily operating shifts (07:00–09:00 & 16:20–17:45) and sequence of all 8 route stops from M.G. Point to BITM.',
      badge: 'Schedule Tab',
      image: scheduleStopsImg
    },
    {
      id: 'screen-pass',
      title: 'My Bus Pass Wallet',
      subtitle: 'Digital Card Scanning & Storage',
      description:
        'Scan both sides of your official college pass to show the driver whenever boarding without carrying physical cards.',
      badge: 'Bus Pass Tab',
      image: busPassImg
    }
  ];

  const [activeScreenIndex, setActiveScreenIndex] = useState(0);

  return (
    <section id="preview" className="py-20 bg-white border-t border-slate-200/80">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Section Header */}
        <div className="text-center max-w-3xl mx-auto mb-16">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-college-primary-container text-college-primary-dark text-xs font-semibold uppercase tracking-wider mb-4">
            Actual Application Interface
          </div>
          <h2 className="text-3xl sm:text-4xl font-extrabold text-college-on-surface tracking-tight">
            Designed for Simplicity and Precision
          </h2>
          <p className="mt-4 text-base sm:text-lg text-college-on-surface-variant leading-relaxed">
            Direct captures from the FleetX Android application running in live college transit environments.
          </p>
        </div>

        {/* Horizontal Scroll on Mobile / Grid on Desktop */}
        <div className="flex md:grid md:grid-cols-2 lg:grid-cols-4 gap-6 overflow-x-auto pb-6 pt-2 snap-x snap-mandatory scrollbar-thin scrollbar-thumb-slate-300">
          {screens.map((item, idx) => (
            <div
              key={item.id}
              onClick={() => setActiveScreenIndex(idx)}
              className={`flex-shrink-0 w-[280px] sm:w-[300px] md:w-auto snap-center flex flex-col items-center bg-college-surface p-4 rounded-3xl border transition-all duration-300 cursor-pointer ${
                activeScreenIndex === idx
                  ? 'border-college-primary shadow-lg ring-2 ring-college-primary/20'
                  : 'border-slate-200/90 hover:border-slate-300 shadow-sm'
              }`}
            >
              {/* Phone Frame */}
              <div className="relative w-full max-w-[240px] rounded-[36px] p-2.5 bg-slate-900 shadow-phone border-2 border-slate-800 mb-4">
                {/* Punch Hole Camera */}
                <div className="absolute top-4 left-1/2 -translate-x-1/2 w-3 h-3 bg-black rounded-full border border-slate-800 z-20 flex items-center justify-center">
                  <div className="w-1 h-1 rounded-full bg-blue-950/80" />
                </div>

                {/* Screenshot inside frame */}
                <div className="rounded-[28px] overflow-hidden bg-white aspect-[9/19.5] relative">
                  <img
                    src={item.image}
                    alt={item.title}
                    className="w-full h-full object-cover select-none pointer-events-none"
                    loading="lazy"
                  />
                </div>
              </div>

              {/* Text Info */}
              <div className="text-center px-2 w-full">
                <span className="inline-block text-[10px] font-bold text-college-primary uppercase tracking-wider bg-college-primary-container px-2 py-0.5 rounded-full mb-2">
                  {item.badge}
                </span>
                <h3 className="text-base font-bold text-college-on-surface mb-1">
                  {item.title}
                </h3>
                <p className="text-xs font-medium text-slate-500 mb-2">
                  {item.subtitle}
                </p>
                <p className="text-[11px] text-college-on-surface-variant leading-relaxed line-clamp-3">
                  {item.description}
                </p>
              </div>
            </div>
          ))}
        </div>

        {/* Caption Banner */}
        <div className="mt-8 text-center text-xs text-slate-500 flex items-center justify-center gap-2">
          <span>📱</span>
          <span>Actual screenshots captured on Android devices running FleetX v1.0.45</span>
        </div>

      </div>
    </section>
  );
};

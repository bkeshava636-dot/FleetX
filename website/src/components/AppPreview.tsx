import React, { useState } from 'react';
import { Smartphone } from 'lucide-react';
import { useScrollStagger } from '@/lib/useMotionReveal';
import mapScreenImg from '../assets/screenshots/screen-map-view.jpg';
import studentHomeImg from '../assets/screenshots/screen-student-home.jpg';
import scheduleStopsImg from '../assets/screenshots/screen-schedule-stops.jpg';
import busPassImg from '../assets/screenshots/screen-bus-pass.jpg';
import { Badge } from './ui/badge';

interface ScreenItem {
  id: string;
  title: string;
  subtitle: string;
  description: string;
  badge: string;
  image: string;
}

const SCREENS: ScreenItem[] = [
  {
    id: 'screen-map',
    title: 'Live Interactive Map',
    subtitle: 'Real-Time GPS & Route Path',
    description: 'Watch your assigned bus move along calibrated route paths with live orientation.',
    badge: 'Live Map',
    image: mapScreenImg,
  },
  {
    id: 'screen-student',
    title: 'Student Tracking View',
    subtitle: 'Assigned Bus & Driver Info',
    description: 'Clear tracking status, assigned driver details, and quick bus overview in one tap.',
    badge: 'Track Tab',
    image: studentHomeImg,
  },
  {
    id: 'screen-schedule',
    title: 'Operating Hours & Stops',
    subtitle: 'Morning & Evening Shifts',
    description: 'Inspect campus operating windows and the ordered sequence of route stops.',
    badge: 'Schedule',
    image: scheduleStopsImg,
  },
  {
    id: 'screen-pass',
    title: 'Digital Bus Pass',
    subtitle: 'Boarding Card Storage',
    description: 'Store both sides of your college bus pass inside the app for quick boarding checks.',
    badge: 'Bus Pass',
    image: busPassImg,
  },
];

export const AppPreview: React.FC = () => {
  const [activeIndex, setActiveIndex] = useState(0);
  const gridRef = useScrollStagger<HTMLDivElement>('[data-reveal]', 55);

  return (
    <section id="preview" className="py-16 sm:py-20 bg-college-surface border-t border-slate-200/80">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="text-center max-w-2xl mx-auto mb-12">
          <Badge className="mb-3 uppercase tracking-wider text-[11px]">Product Preview</Badge>
          <h2 className="text-2xl sm:text-4xl font-extrabold text-slate-900 tracking-tight">
            Designed for Clarity on the Move
          </h2>
          <p className="mt-3 text-sm sm:text-base text-slate-600 leading-relaxed">
            Actual captures from the FleetX Android application in live campus transit environments.
          </p>
        </div>

        <div ref={gridRef} className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5 sm:gap-6">
          {SCREENS.map((item, idx) => {
            const isActive = activeIndex === idx;
            return (
              <button
                key={item.id}
                data-reveal
                type="button"
                onClick={() => setActiveIndex(idx)}
                className={`text-left flex flex-col items-center bg-white p-4 rounded-2xl border transition-all duration-200 hover:-translate-y-1 motion-reduce:hover:translate-y-0 focus:outline-none focus-visible:ring-2 focus-visible:ring-ring ${
                  isActive
                    ? 'border-college-primary shadow-md ring-1 ring-college-primary/20'
                    : 'border-slate-200/90 hover:border-slate-300 shadow-card'
                }`}
              >
                <div className="relative w-full max-w-[210px] rounded-[30px] p-2 bg-slate-900 shadow-phone border-2 border-slate-800 mb-4">
                  <div className="rounded-[22px] overflow-hidden bg-white aspect-[9/19.5]">
                    <img
                      src={item.image}
                      alt={item.title}
                      className="w-full h-full object-cover"
                      loading="lazy"
                    />
                  </div>
                </div>

                <div className="text-center px-1">
                  <Badge className="mb-2 text-[10px]">{item.badge}</Badge>
                  <h3 className="text-sm font-bold text-slate-900">{item.title}</h3>
                  <p className="text-xs font-medium text-slate-500 mt-0.5 mb-1.5">{item.subtitle}</p>
                  <p className="text-xs text-slate-600 leading-relaxed">{item.description}</p>
                </div>
              </button>
            );
          })}
        </div>

        <div className="mt-8 flex items-center justify-center gap-2 text-xs text-slate-500">
          <Smartphone className="w-3.5 h-3.5 text-slate-400" />
          <span>Captured on Android devices running FleetX</span>
        </div>
      </div>
    </section>
  );
};

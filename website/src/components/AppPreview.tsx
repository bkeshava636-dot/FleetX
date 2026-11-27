import React, { useEffect, useRef, useState } from 'react';
import { animate } from 'animejs';
import { Smartphone } from 'lucide-react';
import { useScrollStagger } from '@/lib/useMotionReveal';
import mapScreenImg from '../assets/screenshots/screen-map-view.jpg';
import studentHomeImg from '../assets/screenshots/screen-student-home.jpg';
import scheduleStopsImg from '../assets/screenshots/screen-schedule-stops.jpg';
import busPassImg from '../assets/screenshots/screen-bus-pass.jpg';
import { CoordinateTag, MapGridBackdrop } from './MobilityPrimitives';

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
    description:
      'Watch your assigned bus move along calibrated route paths with live orientation and campus terminal markers.',
    badge: 'Live Map',
    image: mapScreenImg,
  },
  {
    id: 'screen-student',
    title: 'Student Tracking View',
    subtitle: 'Assigned Bus & Driver Info',
    description:
      'Clear tracking status, assigned driver details, and quick bus overview in one tap.',
    badge: 'Track Tab',
    image: studentHomeImg,
  },
  {
    id: 'screen-schedule',
    title: 'Operating Hours & Stops',
    subtitle: 'Morning & Evening Shifts',
    description:
      'Inspect campus operating windows and the ordered sequence of route stops.',
    badge: 'Schedule',
    image: scheduleStopsImg,
  },
  {
    id: 'screen-pass',
    title: 'Digital Bus Pass',
    subtitle: 'Boarding Card Storage',
    description:
      'Store both sides of your college bus pass inside the app for quick boarding checks.',
    badge: 'Bus Pass',
    image: busPassImg,
  },
];

export const AppPreview: React.FC = () => {
  const [activeIndex, setActiveIndex] = useState(0);
  const gridRef = useScrollStagger<HTMLDivElement>('[data-reveal]', 50);
  const screenRef = useRef<HTMLImageElement>(null);
  const activeScreen = SCREENS[activeIndex];

  useEffect(() => {
    const img = screenRef.current;
    if (!img) return;
    const prefersReduced =
      typeof window !== 'undefined' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (prefersReduced) return;

    animate(img, {
      opacity: [0.35, 1],
      scale: [0.97, 1],
      duration: 280,
      ease: 'outExpo',
    });
  }, [activeIndex]);

  return (
    <section
      id="preview"
      className="relative py-20 sm:py-28 bg-fleet-navy text-fleet-offwhite overflow-hidden border-y border-white/10"
    >
      <MapGridBackdrop dark />

      <div className="relative z-10 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex flex-col md:flex-row md:items-end md:justify-between gap-4 mb-12">
          <div className="max-w-2xl">
            <CoordinateTag label="INTERACTIVE PRODUCT SHOWCASE" className="mb-3" />
            <h2 className="font-display text-3xl sm:text-4xl font-bold tracking-tight">
              Designed for Clarity on the Move
            </h2>
          </div>
          <p className="text-sm sm:text-base text-fleet-muted max-w-md leading-relaxed">
            Select any screen tab below to inspect actual captures from the FleetX Android application.
          </p>
        </div>

        {/* Asymmetric Split Composition: Larger Featured Phone + Tabbed Selector Cards */}
        <div ref={gridRef} className="grid grid-cols-1 lg:grid-cols-12 gap-8 lg:gap-12 items-center">
          {/* Featured Larger Phone Frame */}
          <div data-reveal className="lg:col-span-5 flex flex-col items-center">
            <div className="w-full max-w-[295px] sm:max-w-[325px] rounded-[42px] p-3 bg-slate-950 border-2 border-white/15 shadow-2xl">
              <div className="rounded-[32px] overflow-hidden bg-white aspect-[9/19.5]">
                <img
                  ref={screenRef}
                  key={activeScreen.id}
                  src={activeScreen.image}
                  alt={activeScreen.title}
                  className="w-full h-full object-cover"
                  loading="lazy"
                />
              </div>
            </div>
            <div className="mt-4 flex items-center gap-2 text-xs sm:text-sm text-fleet-muted">
              <Smartphone className="w-4 h-4 text-fleet-cyan" />
              <span>{activeScreen.badge} · Android App Capture</span>
            </div>
          </div>

          {/* Selector Cards */}
          <div className="lg:col-span-7 grid grid-cols-1 sm:grid-cols-2 gap-4">
            {SCREENS.map((item, idx) => {
              const isActive = activeIndex === idx;
              return (
                <button
                  key={item.id}
                  data-reveal
                  type="button"
                  aria-pressed={isActive}
                  onClick={() => setActiveIndex(idx)}
                  className={`text-left rounded-2xl border p-5 sm:p-6 transition-all duration-200 focus:outline-none focus-visible:ring-2 focus-visible:ring-fleet-cyan ${
                    isActive
                      ? 'bg-fleet-deep border-fleet-cyan shadow-lg ring-1 ring-fleet-cyan/30'
                      : 'bg-fleet-deep/50 border-white/10 hover:border-white/25 hover:bg-fleet-deep/85'
                  }`}
                >
                  <div className="flex items-center justify-between mb-3">
                    <span
                      className={`font-mono text-[11px] uppercase tracking-wider px-2.5 py-0.5 rounded-full ${
                        isActive
                          ? 'bg-fleet-cyan/20 text-fleet-cyan font-semibold'
                          : 'bg-white/5 text-fleet-muted'
                      }`}
                    >
                      {item.badge}
                    </span>
                    <span
                      className={`h-2.5 w-2.5 rounded-full transition-colors ${
                        isActive ? 'bg-fleet-lime' : 'bg-white/20'
                      }`}
                    />
                  </div>
                  <h3 className="font-display text-lg font-bold text-fleet-offwhite">
                    {item.title}
                  </h3>
                  <p className="text-xs font-medium text-fleet-cyan/90 mt-1 mb-2">
                    {item.subtitle}
                  </p>
                  <p className="text-sm text-fleet-muted leading-relaxed">{item.description}</p>
                </button>
              );
            })}
          </div>
        </div>
      </div>
    </section>
  );
};

import React, { useEffect, useRef, useState } from 'react';
import { animate } from 'animejs';
import { Bell, Bus, ClipboardCheck, Navigation, Route as RouteIcon } from 'lucide-react';
import { useScrollStagger } from '@/lib/useMotionReveal';
import { CoordinateTag, MapGridBackdrop } from './MobilityPrimitives';

const TIMELINE_STEPS = [
  {
    step: '01',
    title: 'Manage buses and drivers',
    description: 'Register fleet vehicles and assign verified drivers directly from the Admin Console.',
    icon: Bus,
  },
  {
    step: '02',
    title: 'Configure routes and stops',
    description: 'Build ordered stop sequences, adjust pins on Google Maps, and publish timetable PDFs.',
    icon: RouteIcon,
  },
  {
    step: '03',
    title: 'Track buses in real time',
    description: 'Drivers stream live GPS telemetry while students follow their bus on the interactive map.',
    icon: Navigation,
  },
  {
    step: '04',
    title: 'Keep students informed',
    description: 'Publish timing updates, temporary bus swaps, and official campus announcements.',
    icon: Bell,
  },
  {
    step: '05',
    title: 'Manage transportation operations',
    description: 'Review driver stop requests, audit logs, and usage analytics from one dashboard.',
    icon: ClipboardCheck,
  },
];

export const HowItWorks: React.FC = () => {
  const listRef = useScrollStagger<HTMLOListElement>('[data-reveal]', 55);
  const railRef = useRef<HTMLDivElement>(null);
  const [activeStage, setActiveStage] = useState(0);

  useEffect(() => {
    const rail = railRef.current;
    if (!rail) return;
    const progress =
      activeStage === 0 ? 0.02 : activeStage / (TIMELINE_STEPS.length - 1);
    const prefersReduced =
      typeof window !== 'undefined' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (prefersReduced) {
      rail.style.transform = `scaleY(${progress})`;
      return;
    }

    animate(rail, {
      scaleY: progress,
      duration: 420,
      ease: 'outExpo',
    });
  }, [activeStage]);

  return (
    <section
      id="how-it-works"
      className="relative py-20 sm:py-28 bg-fleet-navy text-fleet-offwhite overflow-hidden border-y border-white/10"
    >
      <MapGridBackdrop dark />

      <div className="relative z-10 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-10 lg:gap-14 items-start">
          {/* Left Sticky Editorial Column */}
          <div className="lg:col-span-5 lg:sticky lg:top-28 space-y-5">
            <CoordinateTag label="TRANSIT WORKFLOW · 5 STAGES" />
            <h2 className="font-display text-3xl sm:text-4xl font-bold tracking-tight leading-tight">
              From Fleet Setup to Live Student Tracking
            </h2>
            <p className="text-sm sm:text-base text-fleet-muted leading-relaxed">
              A connected five-stage operational loop linking transport administrators, drivers, and campus commuters from morning dispatch to evening return.
            </p>
            <div className="hidden lg:flex items-center gap-3 pt-2 text-xs font-mono text-fleet-cyan">
              <span className="h-2 w-2 rounded-full bg-fleet-lime" />
              <span>Continuous end-to-end campus transit loop</span>
            </div>
          </div>

          {/* Right Animated Route Timeline */}
          <div className="lg:col-span-7 relative">
            {/* Subtle animated vertical progress rail */}
            <div
              aria-hidden="true"
              className="absolute left-[11px] top-9 bottom-14 w-0.5 bg-white/10"
            >
              <div
                ref={railRef}
                className="h-full w-full origin-top bg-gradient-to-b from-fleet-cyan via-fleet-lime to-fleet-cyan/40"
              />
            </div>

            <ol ref={listRef} className="relative space-y-5 pl-9">
              {TIMELINE_STEPS.map((item, idx) => {
                const Icon = item.icon;
                const isSelected = activeStage === idx;
                const isCompleted = idx < activeStage;
                return (
                  <li
                    key={item.step}
                    data-reveal
                    onClick={() => setActiveStage(idx)}
                    className={`relative cursor-pointer rounded-2xl border p-5 sm:p-6 transition-all duration-200 ${
                      isSelected
                        ? 'border-fleet-cyan bg-fleet-deep shadow-lg shadow-fleet-cyan/10'
                        : isCompleted
                        ? 'border-fleet-lime/35 bg-fleet-deep/85 hover:border-fleet-cyan/50 hover:bg-fleet-deep'
                        : 'border-white/10 bg-fleet-deep/50 hover:border-fleet-cyan/40 hover:bg-fleet-deep/85'
                    }`}
                  >
                    {/* Numbered Stage Node */}
                    <span
                      aria-hidden="true"
                      className={`absolute -left-[37px] top-6 flex h-6 w-6 items-center justify-center rounded-full border-2 font-mono text-[10px] font-bold shadow-sm transition-colors duration-200 ${
                        isSelected
                          ? 'border-fleet-cyan bg-fleet-cyan text-fleet-navy'
                          : isCompleted
                          ? 'border-fleet-lime bg-fleet-navy text-fleet-lime'
                          : 'border-white/25 bg-fleet-navy text-fleet-muted'
                      }`}
                    >
                      {item.step}
                    </span>

                    <div className="flex items-start gap-4">
                      <div
                        className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-xl border transition-colors duration-200 ${
                          isSelected
                            ? 'border-fleet-cyan/40 bg-fleet-cyan/15 text-fleet-cyan'
                            : isCompleted
                            ? 'border-fleet-lime/30 bg-fleet-navy text-fleet-lime'
                            : 'border-white/10 bg-fleet-navy text-fleet-muted'
                        }`}
                      >
                        <Icon className="w-5 h-5" />
                      </div>
                      <div className="space-y-1">
                        <span
                          className={`font-mono text-[11px] font-semibold uppercase tracking-wider transition-colors duration-200 ${
                            isSelected || isCompleted ? 'text-fleet-lime' : 'text-fleet-muted'
                          }`}
                        >
                          Stage {item.step}
                        </span>
                        <h3 className="font-display text-lg font-bold text-fleet-offwhite">
                          {item.title}
                        </h3>
                        <p className="text-sm text-fleet-muted leading-relaxed">
                          {item.description}
                        </p>
                      </div>
                    </div>
                  </li>
                );
              })}
            </ol>
          </div>
        </div>
      </div>
    </section>
  );
};

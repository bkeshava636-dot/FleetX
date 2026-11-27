import React, { useEffect, useRef, useState } from 'react';
import { animate } from 'animejs';
import { BarChart3, BellRing, Bus, MapPin, Navigation, Route as RouteIcon, UserCheck } from 'lucide-react';
import { useScrollStagger } from '@/lib/useMotionReveal';
import { Badge } from './ui/badge';

const CORRIDOR_STOPS = [
  { id: 0, label: 'Royal Circle', x: 20, y: 82 },
  { id: 1, label: 'SP Circle', x: 135, y: 45 },
  { id: 2, label: 'Cantonment', x: 235, y: 57 },
  { id: 3, label: 'BITM Campus', x: 340, y: 32 },
];

const COMPACT_CARDS = [
  {
    title: 'Smart Fleet Management',
    description:
      'Register buses, toggle active status, and handle temporary bus swaps without disrupting student route visibility.',
    icon: Bus,
    tag: 'Fleet Registry',
  },
  {
    title: 'Driver Management',
    description:
      'Maintain verified driver rosters, direct bus assignments, and temporary driver replacement coverage.',
    icon: UserCheck,
    tag: 'Crew Roster',
  },
  {
    title: 'Routes & Stops',
    description:
      'Configure ordered 1..N stop sequences, edit coordinates on Google Maps, and publish timetable PDFs.',
    icon: RouteIcon,
    tag: 'Corridors',
  },
  {
    title: 'Announcements',
    description:
      'Publish draft or scheduled campus notices and bus timing change alerts directly to students.',
    icon: BellRing,
    tag: 'Notices',
  },
  {
    title: 'Analytics',
    description:
      'Track 7-day WAU, 30-day MAU, feature usage events, and Android app version adoption.',
    icon: BarChart3,
    tag: 'Telemetry',
  },
];

export const KeyBenefits: React.FC = () => {
  const gridRef = useScrollStagger<HTMLDivElement>('[data-reveal]', 45);
  const [activeStopIdx, setActiveStopIdx] = useState(2);
  const markerRingRef = useRef<SVGCircleElement>(null);
  const markerDotRef = useRef<SVGCircleElement>(null);

  useEffect(() => {
    const stop = CORRIDOR_STOPS[activeStopIdx];
    const ring = markerRingRef.current;
    const dot = markerDotRef.current;
    if (!ring || !dot) return;

    const prefersReduced =
      typeof window !== 'undefined' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    if (prefersReduced) {
      ring.setAttribute('cx', String(stop.x));
      ring.setAttribute('cy', String(stop.y));
      dot.setAttribute('cx', String(stop.x));
      dot.setAttribute('cy', String(stop.y));
      return;
    }

    animate([ring, dot], {
      cx: stop.x,
      cy: stop.y,
      duration: 420,
      ease: 'outExpo',
    });
  }, [activeStopIdx]);

  return (
    <section id="benefits" aria-labelledby="benefits-heading" className="py-16 sm:py-24 bg-fleet-offwhite">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex flex-col md:flex-row md:items-end md:justify-between gap-4 mb-12">
          <div className="max-w-2xl">
            <Badge variant="secondary" className="mb-3 font-mono uppercase tracking-wider text-[11px]">
              Feature Architecture
            </Badge>
            <h2
              id="benefits-heading"
              className="font-display text-3xl sm:text-4xl font-bold text-fleet-navy tracking-tight"
            >
              Built for Connected Campus Mobility
            </h2>
          </div>
          <p className="text-sm sm:text-base text-slate-600 max-w-md leading-relaxed">
            Every layer of campus transport — vehicles, drivers, routes, and student updates — synchronized in one system.
          </p>
        </div>

        {/* Asymmetric Bento Grid: Largest Hero Card (Real-Time Bus Tracking + Map Visual) + 5 Cards */}
        <div ref={gridRef} className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 sm:gap-5">
          {/* Largest Feature Card: Real-Time Bus Tracking */}
          <div
            data-reveal
            className="md:col-span-2 lg:col-span-2 lg:row-span-2 flex flex-col justify-between rounded-3xl border border-fleet-navy bg-fleet-navy text-fleet-offwhite p-6 sm:p-8 shadow-md"
          >
            <div>
              <div className="flex items-center justify-between mb-5">
                <div className="w-11 h-11 rounded-xl bg-fleet-cyan/15 border border-fleet-cyan/30 text-fleet-cyan flex items-center justify-center">
                  <Navigation className="w-5 h-5" />
                </div>
                <span className="font-mono text-[11px] uppercase tracking-wider text-fleet-cyan">
                  RTDB Live Stream
                </span>
              </div>

              <h3 className="font-display text-2xl font-bold mb-2.5">
                Real-Time Bus Tracking
              </h3>
              <p className="text-sm sm:text-base text-fleet-muted leading-relaxed">
                Continuous GPS coordinates streamed from driver devices to an interactive campus map with clear Active, Stale, Waiting for GPS, and Arrived states.
              </p>
            </div>

            {/* Interactive Map-related Visual */}
            <div className="mt-6 rounded-2xl border border-white/10 bg-[#111C2E] p-4 overflow-hidden">
              <div className="flex items-center justify-between text-[11px] font-mono text-fleet-muted mb-2">
                <span className="flex items-center gap-1.5">
                  <MapPin className="w-3.5 h-3.5 text-fleet-cyan" />
                  {CORRIDOR_STOPS[activeStopIdx].label}
                </span>
                <span className="text-fleet-lime">TAP STOP NODE</span>
              </div>
              <svg viewBox="0 0 360 110" className="w-full h-auto" fill="none">
                <path
                  d="M 20 82 Q 110 22, 195 55 T 340 32"
                  stroke="#45D6E8"
                  strokeOpacity="0.35"
                  strokeWidth="5"
                  strokeLinecap="round"
                />
                <path
                  d="M 20 82 Q 110 22, 195 55 T 340 32"
                  stroke="#45D6E8"
                  strokeWidth="2"
                  strokeDasharray="5 5"
                />
                {CORRIDOR_STOPS.map((s) => (
                  <g
                    key={s.id}
                    onClick={() => setActiveStopIdx(s.id)}
                    className="cursor-pointer"
                    role="button"
                    tabIndex={0}
                    aria-label={`Inspect stop ${s.label}`}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        setActiveStopIdx(s.id);
                      }
                    }}
                  >
                    <circle cx={s.x} cy={s.y} r="12" fill="transparent" />
                    <circle
                      cx={s.x}
                      cy={s.y}
                      r="5"
                      fill="#0B1220"
                      stroke="#45D6E8"
                      strokeWidth="2"
                    />
                  </g>
                ))}
                <circle
                  ref={markerRingRef}
                  cx={CORRIDOR_STOPS[2].x}
                  cy={CORRIDOR_STOPS[2].y}
                  r="10"
                  fill="#C5F36A"
                  fillOpacity="0.25"
                  className="pointer-events-none"
                />
                <circle
                  ref={markerDotRef}
                  cx={CORRIDOR_STOPS[2].x}
                  cy={CORRIDOR_STOPS[2].y}
                  r="5"
                  fill="#C5F36A"
                  className="pointer-events-none"
                />
              </svg>
            </div>
          </div>

          {/* 5 Supporting Bento Cards */}
          {COMPACT_CARDS.map((item, idx) => {
            const Icon = item.icon;
            return (
              <div
                key={item.title}
                data-reveal
                className={`group rounded-2xl border border-slate-200 bg-white text-fleet-navy p-6 shadow-sm transition-all duration-200 hover:-translate-y-1 hover:shadow-md hover:border-slate-300 motion-reduce:hover:translate-y-0 ${
                  idx === 4 ? 'md:col-span-2 lg:col-span-4' : ''
                }`}
              >
                <div className="flex items-center justify-between mb-4">
                  <div className="w-10 h-10 rounded-xl bg-slate-100 border border-slate-200 text-fleet-navy flex items-center justify-center transition-transform duration-200 group-hover:scale-105 motion-reduce:group-hover:scale-100">
                    <Icon className="w-5 h-5" />
                  </div>
                  <span className="font-mono text-[10px] uppercase tracking-wider text-slate-500">
                    {item.tag}
                  </span>
                </div>
                <h3 className="font-display text-lg font-bold mb-1.5">{item.title}</h3>
                <p className="text-sm text-slate-600 leading-relaxed">{item.description}</p>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
};

import React from 'react';
import { Bell, Bus, ClipboardCheck, Navigation, Route as RouteIcon } from 'lucide-react';
import { useScrollStagger } from '@/lib/useMotionReveal';
import { Badge } from './ui/badge';

const STEPS = [
  {
    step: '01',
    title: 'Manage buses and drivers',
    description: 'Register fleet vehicles and assign verified drivers from the Admin Console.',
    icon: Bus,
  },
  {
    step: '02',
    title: 'Configure routes and stops',
    description: 'Define ordered stops, map coordinates, and publish route timetable PDFs.',
    icon: RouteIcon,
  },
  {
    step: '03',
    title: 'Track buses in real time',
    description: 'Drivers stream live GPS telemetry while students follow their bus on the map.',
    icon: Navigation,
  },
  {
    step: '04',
    title: 'Keep students informed',
    description: 'Publish timing updates, bus swaps, and campus notices directly to riders.',
    icon: Bell,
  },
  {
    step: '05',
    title: 'Manage operations',
    description: 'Review driver stop requests, audit logs, and daily engagement analytics.',
    icon: ClipboardCheck,
  },
];

export const HowItWorks: React.FC = () => {
  const stepsRef = useScrollStagger<HTMLDivElement>('[data-reveal]', 50);

  return (
    <section id="how-it-works" className="py-16 sm:py-20 bg-college-surface">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="text-center max-w-2xl mx-auto mb-12">
          <Badge className="mb-3 uppercase tracking-wider text-[11px]">Workflow</Badge>
          <h2 className="text-2xl sm:text-4xl font-extrabold text-slate-900 tracking-tight">
            How FleetX Works
          </h2>
          <p className="mt-3 text-sm sm:text-base text-slate-600 leading-relaxed">
            A connected five-step workflow linking transport administrators, drivers, and students.
          </p>
        </div>

        <div ref={stepsRef} className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4 sm:gap-5">
          {STEPS.map((item) => {
            const Icon = item.icon;
            return (
              <div
                key={item.step}
                data-reveal
                className="flex flex-col justify-between bg-white p-5 rounded-2xl border border-slate-200/90 shadow-card transition-all duration-200 hover:border-college-primary/30"
              >
                <div>
                  <div className="flex items-center justify-between mb-4">
                    <div className="w-9 h-9 rounded-xl bg-college-primary-container/70 text-college-primary flex items-center justify-center">
                      <Icon className="w-4 h-4" />
                    </div>
                    <span className="text-xl font-black text-slate-200 select-none">
                      {item.step}
                    </span>
                  </div>
                  <h3 className="text-sm font-bold text-slate-900 mb-1.5">{item.title}</h3>
                  <p className="text-xs text-slate-600 leading-relaxed">{item.description}</p>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
};

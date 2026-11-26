import React from 'react';
import { Bus, BellRing, Eye, Navigation, Route as RouteIcon, UserCheck } from 'lucide-react';
import { useScrollStagger } from '@/lib/useMotionReveal';
import { Badge } from './ui/badge';
import { Card, CardContent } from './ui/card';

const BENEFITS = [
  {
    title: 'Real-Time Bus Tracking',
    description: 'Continuous GPS coordinates streamed from the driver device to an interactive campus map.',
    icon: Navigation,
  },
  {
    title: 'Smart Fleet Management',
    description: 'Centralized bus registry, active status control, and temporary bus swap handling.',
    icon: Bus,
  },
  {
    title: 'Driver Coordination',
    description: 'Verified driver profiles, direct bus assignments, and temporary replacement workflows.',
    icon: UserCheck,
  },
  {
    title: 'Route Management',
    description: 'Ordered stop sequences, map-based stop editing, and route-specific timetable PDFs.',
    icon: RouteIcon,
  },
  {
    title: 'Student Communication',
    description: 'Official announcements, bus timing change notices, and instant service alerts.',
    icon: BellRing,
  },
  {
    title: 'Operational Visibility',
    description: 'Live fleet telemetry, audit logs, and usage analytics in one connected console.',
    icon: Eye,
  },
];

export const KeyBenefits: React.FC = () => {
  const gridRef = useScrollStagger<HTMLDivElement>('[data-reveal]', 45);

  return (
    <section aria-labelledby="benefits-heading" className="py-16 sm:py-20 bg-white border-y border-slate-200/80">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="text-center max-w-2xl mx-auto mb-12">
          <Badge className="mb-3 uppercase tracking-wider text-[11px]">Why FleetX</Badge>
          <h2 id="benefits-heading" className="text-2xl sm:text-4xl font-extrabold text-slate-900 tracking-tight">
            One Platform for Every Campus Commute
          </h2>
          <p className="mt-3 text-sm sm:text-base text-slate-600 leading-relaxed">
            Designed to keep students informed and transport administrators in complete control.
          </p>
        </div>

        <div ref={gridRef} className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5 sm:gap-6">
          {BENEFITS.map((item) => {
            const Icon = item.icon;
            return (
              <Card
                key={item.title}
                data-reveal
                className="group hover:border-college-primary/40 hover:-translate-y-1 hover:shadow-card-hover motion-reduce:hover:translate-y-0"
              >
                <CardContent className="p-6">
                  <div className="w-10 h-10 rounded-xl bg-college-primary-container/70 text-college-primary flex items-center justify-center mb-4 transition-transform duration-200 group-hover:scale-110 motion-reduce:group-hover:scale-100">
                    <Icon className="w-5 h-5" />
                  </div>
                  <h3 className="text-base font-bold text-slate-900 mb-1.5">{item.title}</h3>
                  <p className="text-sm text-slate-600 leading-relaxed">{item.description}</p>
                </CardContent>
              </Card>
            );
          })}
        </div>
      </div>
    </section>
  );
};

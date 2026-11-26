import React, { useState } from 'react';
import {
  BarChart3,
  BellRing,
  Bus,
  Calendar,
  FileText,
  MapPin,
  Navigation,
  Radio,
  RefreshCw,
  Route as RouteIcon,
  UserCheck,
  Users,
} from 'lucide-react';
import { useScrollStagger } from '@/lib/useMotionReveal';
import { Badge } from './ui/badge';
import { Card, CardContent } from './ui/card';
import { Tabs, TabsContent, TabsList, TabsTrigger } from './ui/tabs';

interface FeatureGroup {
  id: string;
  label: string;
  summary: string;
  items: Array<{
    title: string;
    description: string;
    icon: React.ComponentType<{ className?: string }>;
  }>;
}

const FEATURE_GROUPS: FeatureGroup[] = [
  {
    id: 'tracking',
    label: 'Live Tracking',
    summary: 'Real-time GPS telemetry streamed via Firebase Realtime Database with clear freshness states.',
    items: [
      {
        title: 'Real-Time Bus Location',
        description: 'View live bus coordinates updated continuously as drivers operate their assigned route.',
        icon: Navigation,
      },
      {
        title: 'Map-Based Tracking',
        description: 'Interactive Google Maps view showing bus heading, speed, and campus terminal markers.',
        icon: MapPin,
      },
      {
        title: 'GPS Tracking Lifecycle',
        description: 'Clear Active, Stale, Waiting for GPS, Arrived at Campus, and Inactive status indicators.',
        icon: Radio,
      },
    ],
  },
  {
    id: 'fleet',
    label: 'Fleet Operations',
    summary: 'Complete day-to-day control over vehicles, driver rosters, swaps, and temporary coverage.',
    items: [
      {
        title: 'Bus Management',
        description: 'Register buses, manage capacity and active status, and safely decommission retired units.',
        icon: Bus,
      },
      {
        title: 'Driver Assignments',
        description: 'Assign or unassign verified drivers directly to buses with automatic RTDB sync tracking.',
        icon: UserCheck,
      },
      {
        title: 'Swaps & Temp Replacements',
        description: 'Handle temporary driver replacements and bus swaps without disrupting student route visibility.',
        icon: RefreshCw,
      },
    ],
  },
  {
    id: 'routes',
    label: 'Route Management',
    summary: 'Structured routes, crowdsourced stop accuracy, driver stop requests, and printable timetables.',
    items: [
      {
        title: 'Route & Stop Editor',
        description: 'Create routes, reorder stops 1..N, and place stop coordinates directly on Google Maps.',
        icon: RouteIcon,
      },
      {
        title: 'Stop Coordinate Submissions',
        description: 'Review and approve student-submitted or driver-requested stop coordinates cleanly.',
        icon: MapPin,
      },
      {
        title: 'Route Timetable PDFs',
        description: 'Upload route-specific and master all-routes timetable PDFs for student access.',
        icon: FileText,
      },
    ],
  },
  {
    id: 'communication',
    label: 'Communication',
    summary: 'Keep students informed of schedule changes, bus swaps, and official transport advisories.',
    items: [
      {
        title: 'Official Announcements',
        description: 'Draft, schedule expiry, edit, and publish transport notices from the Admin Console.',
        icon: BellRing,
      },
      {
        title: 'Bus Timing Updates',
        description: 'Publish targeted timing change alerts so students never miss an adjusted departure.',
        icon: RefreshCw,
      },
      {
        title: 'Student Notifications',
        description: 'Deliver timely service updates and swap alerts to active student app installations.',
        icon: Users,
      },
    ],
  },
  {
    id: 'admin',
    label: 'Administration',
    summary: 'Operational visibility, governance, and campus calendar management built for transit teams.',
    items: [
      {
        title: 'Usage Analytics',
        description: 'Monitor 7-day WAU, 30-day MAU, feature usage events, and app version distribution.',
        icon: BarChart3,
      },
      {
        title: 'Audit Logs',
        description: 'Searchable operational history across swaps, assignments, and admin actions.',
        icon: FileText,
      },
      {
        title: 'College Operating Calendar',
        description: 'Configure operating windows, holidays, and exceptions aligned with campus schedules.',
        icon: Calendar,
      },
    ],
  },
];

export const Features: React.FC = () => {
  const [activeTab, setActiveTab] = useState('tracking');
  const cardsRef = useScrollStagger<HTMLDivElement>('[data-reveal]', 55, [activeTab]);

  return (
    <section id="features" className="py-16 sm:py-20 bg-white border-y border-slate-200/80">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="text-center max-w-2xl mx-auto mb-10">
          <Badge className="mb-3 uppercase tracking-wider text-[11px]">Feature Showcase</Badge>
          <h2 className="text-2xl sm:text-4xl font-extrabold text-slate-900 tracking-tight">
            Built Around Real Campus Operations
          </h2>
          <p className="mt-3 text-sm sm:text-base text-slate-600 leading-relaxed">
            Explore the connected capabilities powering FleetX across student devices and the transport console.
          </p>
        </div>

        <Tabs value={activeTab} onValueChange={setActiveTab} className="w-full">
          <div className="flex justify-center">
            <TabsList aria-label="FleetX feature categories">
              {FEATURE_GROUPS.map((group) => (
                <TabsTrigger key={group.id} value={group.id}>
                  {group.label}
                </TabsTrigger>
              ))}
            </TabsList>
          </div>

          <div ref={cardsRef}>
            {FEATURE_GROUPS.map((group) => (
              <TabsContent key={group.id} value={group.id}>
                <p className="text-center text-sm text-slate-600 max-w-2xl mx-auto mb-8">
                  {group.summary}
                </p>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-5 sm:gap-6">
                  {group.items.map((item) => {
                    const Icon = item.icon;
                    return (
                      <Card
                        key={item.title}
                        data-reveal
                        className="group bg-college-surface/60 hover:border-college-primary/40 hover:-translate-y-1 hover:shadow-card-hover motion-reduce:hover:translate-y-0"
                      >
                        <CardContent className="p-6">
                          <div className="w-10 h-10 rounded-xl bg-white border border-slate-200 text-college-primary flex items-center justify-center mb-4 shadow-sm transition-transform duration-200 group-hover:scale-110 motion-reduce:group-hover:scale-100">
                            <Icon className="w-5 h-5" />
                          </div>
                          <h3 className="text-base font-bold text-slate-900 mb-1.5">{item.title}</h3>
                          <p className="text-sm text-slate-600 leading-relaxed">{item.description}</p>
                        </CardContent>
                      </Card>
                    );
                  })}
                </div>
              </TabsContent>
            ))}
          </div>
        </Tabs>
      </div>
    </section>
  );
};

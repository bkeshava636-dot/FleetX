import React, { useState } from 'react';
import {
  CheckCircle2,
  Compass,
  Navigation,
  ShieldCheck,
  Smartphone,
  UserCheck,
  Wrench,
} from 'lucide-react';
import { useScrollStagger } from '@/lib/useMotionReveal';
import { Badge } from './ui/badge';
import { Tabs, TabsContent, TabsList, TabsTrigger } from './ui/tabs';

interface RoleMetric {
  label: string;
  value: string;
}

interface ShowcaseBlock {
  id: string;
  label: string;
  title: string;
  description: string;
  bullets: string[];
  icon: React.ComponentType<{ className?: string }>;
  codeTag: string;
  highlights: [RoleMetric, RoleMetric];
  statusLabel: string;
}

const SHOWCASE_BLOCKS: ShowcaseBlock[] = [
  {
    id: 'students',
    label: 'For Students',
    title: 'Zero-Guesswork Daily Commute',
    description:
      'Built for fast morning and evening checks — open the app, confirm your bus status, inspect route timings, and keep your digital bus pass ready.',
    bullets: [
      'Instant bus status: Active, Stale, Waiting for GPS, or Arrived',
      'Morning and evening shift timetables with ordered stop lists',
      'In-app front & back digital bus pass storage',
    ],
    icon: Smartphone,
    codeTag: 'Android · Student View',
    highlights: [
      { label: 'Boarding', value: 'Digital Pass' },
      { label: 'Schedules', value: 'AM / PM Shifts' },
    ],
    statusLabel: 'Read-Only Live Access',
  },
  {
    id: 'drivers',
    label: 'For Drivers',
    title: 'Focused Trip Broadcasting & Stop Requests',
    description:
      'Designed to stay out of the driver’s way — start location streaming for your assigned vehicle and submit stop coordinate corrections when needed.',
    bullets: [
      'Foreground GPS broadcasting via Fused Location Provider',
      'Clear assigned bus and active route confirmation',
      'Submit stop pin updates directly to the admin review queue',
    ],
    icon: Navigation,
    codeTag: 'Android · Driver Mode',
    highlights: [
      { label: 'Telemetry', value: 'Fused GPS' },
      { label: 'Field Input', value: 'Stop Pin Requests' },
    ],
    statusLabel: 'Verified Driver Auth',
  },
  {
    id: 'admins',
    label: 'For Transport Admins',
    title: 'Centralized Fleet & Route Governance',
    description:
      'Manage the entire BITM campus transport network from the web console — handle daily bus swaps, map stops, notices, and operational audits.',
    bullets: [
      'Live fleet overview with driver roster & temporary swaps',
      'Google Maps stop coordinate editor & timetable PDF publishing',
      'Searchable audit logs and 7-day / 30-day usage telemetry',
    ],
    icon: ShieldCheck,
    codeTag: 'Web · Admin Console',
    highlights: [
      { label: 'Operations', value: 'Bus & Driver Swaps' },
      { label: 'Governance', value: 'Audit & Analytics' },
    ],
    statusLabel: 'Role-Protected Console',
  },
];

export const Features: React.FC = () => {
  const [activeTab, setActiveTab] = useState('students');
  const panelRef = useScrollStagger<HTMLDivElement>('[data-reveal]', 50, [activeTab]);

  return (
    <section id="features" className="py-16 sm:py-24 bg-fleet-offwhite">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="text-center max-w-2xl mx-auto mb-10">
          <Badge variant="secondary" className="mb-3 font-mono uppercase tracking-wider text-[11px]">
            Role-Based Experience
          </Badge>
          <h2 className="font-display text-3xl sm:text-4xl font-bold text-fleet-navy tracking-tight">
            Built for Every Role on Campus
          </h2>
          <p className="mt-3 text-sm sm:text-base text-slate-600 leading-relaxed">
            Switch between Student, Driver, and Transport Admin perspectives to see how each role interacts with FleetX.
          </p>
        </div>

        <Tabs value={activeTab} onValueChange={setActiveTab} className="w-full">
          <div className="flex justify-center">
            <TabsList aria-label="FleetX role capabilities" className="bg-white border border-slate-200">
              {SHOWCASE_BLOCKS.map((b) => (
                <TabsTrigger key={b.id} value={b.id}>
                  {b.label}
                </TabsTrigger>
              ))}
            </TabsList>
          </div>

          <div ref={panelRef} className="mt-8">
            {SHOWCASE_BLOCKS.map((block) => {
              const Icon = block.icon;
              return (
                <TabsContent key={block.id} value={block.id}>
                  <div
                    data-reveal
                    className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-center rounded-3xl border border-slate-200 bg-white p-6 sm:p-10 shadow-sm"
                  >
                    <div className="lg:col-span-7 space-y-4">
                      <span className="inline-flex items-center gap-2 rounded-lg bg-slate-100 px-3 py-1 font-mono text-[11px] font-semibold text-slate-700">
                        <Icon className="w-3.5 h-3.5 text-college-primary" />
                        {block.codeTag}
                      </span>
                      <h3 className="font-display text-2xl sm:text-3xl font-bold text-fleet-navy tracking-tight">
                        {block.title}
                      </h3>
                      <p className="text-sm sm:text-base text-slate-600 leading-relaxed">
                        {block.description}
                      </p>
                      <ul className="space-y-2.5 pt-2">
                        {block.bullets.map((bullet) => (
                          <li key={bullet} className="flex items-start gap-2.5 text-sm text-slate-700">
                            <CheckCircle2 className="w-4 h-4 text-status-green shrink-0 mt-0.5" />
                            <span>{bullet}</span>
                          </li>
                        ))}
                      </ul>
                    </div>

                    {/* Dynamic Role Summary Panel */}
                    <div className="lg:col-span-5 rounded-2xl bg-fleet-navy text-fleet-offwhite p-6 border border-white/10 space-y-4">
                      <div className="flex items-center justify-between border-b border-white/10 pb-3">
                        <span className="font-mono text-xs text-fleet-cyan">{block.label}</span>
                        <span className="h-2 w-2 rounded-full bg-fleet-lime" />
                      </div>
                      <div className="grid grid-cols-2 gap-3">
                        <div className="rounded-xl bg-fleet-deep p-3.5 border border-white/5">
                          <Compass className="w-4 h-4 text-fleet-cyan mb-2" />
                          <p className="text-xs font-semibold">{block.highlights[0].label}</p>
                          <p className="text-[11px] text-fleet-muted mt-0.5">{block.highlights[0].value}</p>
                        </div>
                        <div className="rounded-xl bg-fleet-deep p-3.5 border border-white/5">
                          <Wrench className="w-4 h-4 text-fleet-lime mb-2" />
                          <p className="text-xs font-semibold">{block.highlights[1].label}</p>
                          <p className="text-[11px] text-fleet-muted mt-0.5">{block.highlights[1].value}</p>
                        </div>
                        <div className="col-span-2 rounded-xl bg-fleet-deep p-3.5 border border-white/5 flex items-center justify-between">
                          <div className="flex items-center gap-2.5">
                            <UserCheck className="w-4 h-4 text-fleet-cyan" />
                            <span className="text-xs font-semibold">{block.statusLabel}</span>
                          </div>
                          <span className="font-mono text-[10px] text-fleet-lime">ACTIVE</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </TabsContent>
              );
            })}
          </div>
        </Tabs>
      </div>
    </section>
  );
};

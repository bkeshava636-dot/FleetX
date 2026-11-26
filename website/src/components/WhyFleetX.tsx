import React from 'react';
import { Lock, MapPin, ShieldCheck, Zap } from 'lucide-react';
import { useScrollStagger } from '@/lib/useMotionReveal';
import { Badge } from './ui/badge';
import { Card, CardContent } from './ui/card';

const PILLARS = [
  {
    title: 'Low-Latency Real-Time Engine',
    description:
      'Powered by Firebase Realtime Database and Fused Location Provider for continuous coordinate updates.',
    icon: Zap,
  },
  {
    title: 'Role-Separated Security',
    description:
      'Students view routes and live buses cleanly while driver and admin operations remain role-authenticated.',
    icon: ShieldCheck,
  },
  {
    title: 'Verified APK Distribution',
    description:
      'Builds are compiled through GitHub Actions with SHA-256 verification checksums for release integrity.',
    icon: Lock,
  },
  {
    title: 'Campus-Calibrated Routing',
    description:
      'Tailored for college routes and BITM Bellary morning and evening operating windows.',
    icon: MapPin,
  },
];

export const WhyFleetX: React.FC = () => {
  const pillarsRef = useScrollStagger<HTMLDivElement>('[data-reveal]', 55);

  return (
    <section id="about" className="py-16 sm:py-20 bg-white border-t border-slate-200/80">
      <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="text-center max-w-2xl mx-auto mb-12">
          <Badge className="mb-3 uppercase tracking-wider text-[11px]">Connected Platform</Badge>
          <h2 className="text-2xl sm:text-4xl font-extrabold text-slate-900 tracking-tight">
            Why FleetX?
          </h2>
          <p className="mt-3 text-sm sm:text-base text-slate-600 leading-relaxed">
            Instead of scattered WhatsApp updates and static spreadsheets, FleetX unifies live tracking, route management, and student communication in one dependable system.
          </p>
        </div>

        <div ref={pillarsRef} className="grid grid-cols-1 md:grid-cols-2 gap-5 sm:gap-6">
          {PILLARS.map((item) => {
            const Icon = item.icon;
            return (
              <Card
                key={item.title}
                data-reveal
                className="group bg-college-surface/60 hover:border-college-primary/40 hover:-translate-y-1 hover:shadow-card-hover motion-reduce:hover:translate-y-0"
              >
                <CardContent className="p-6 flex items-start gap-4">
                  <div className="w-10 h-10 rounded-xl bg-white border border-slate-200 text-college-primary flex items-center justify-center shrink-0 shadow-sm">
                    <Icon className="w-5 h-5" />
                  </div>
                  <div>
                    <h3 className="text-base font-bold text-slate-900 mb-1">{item.title}</h3>
                    <p className="text-sm text-slate-600 leading-relaxed">{item.description}</p>
                  </div>
                </CardContent>
              </Card>
            );
          })}
        </div>
      </div>
    </section>
  );
};

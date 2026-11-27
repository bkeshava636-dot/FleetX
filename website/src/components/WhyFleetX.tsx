import React from 'react';
import { Lock, MapPin, ShieldCheck, Zap } from 'lucide-react';
import { useScrollStagger } from '@/lib/useMotionReveal';
import { CoordinateTag } from './MobilityPrimitives';

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
  const pillarsRef = useScrollStagger<HTMLDivElement>('[data-reveal]', 50);

  return (
    <section id="about" className="py-16 sm:py-24 bg-fleet-deep text-fleet-offwhite border-b border-white/10">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-10 items-start">
          {/* Editorial Typography Left */}
          <div className="lg:col-span-5 space-y-5">
            <CoordinateTag label="WHY FLEETX" />
            <h2 className="font-display text-3xl sm:text-4xl font-bold tracking-tight leading-tight">
              One connected system instead of scattered updates.
            </h2>
            <p className="text-sm sm:text-base text-fleet-muted leading-relaxed">
              Campus transit fails when route sheets, driver rosters, and student updates live in separate places. FleetX unifies live telemetry, route governance, and announcements into one dependable platform.
            </p>
          </div>

          {/* Architectural Pillars Right */}
          <div ref={pillarsRef} className="lg:col-span-7 grid grid-cols-1 sm:grid-cols-2 gap-4">
            {PILLARS.map((item) => {
              const Icon = item.icon;
              return (
                <div
                  key={item.title}
                  data-reveal
                  className="rounded-2xl border border-white/10 bg-fleet-navy/80 p-5 transition-colors hover:border-fleet-cyan/35"
                >
                  <div className="w-9 h-9 rounded-xl bg-fleet-cyan/10 border border-fleet-cyan/20 text-fleet-cyan flex items-center justify-center mb-3.5">
                    <Icon className="w-4 h-4" />
                  </div>
                  <h3 className="font-display text-base font-bold text-fleet-offwhite mb-1.5">
                    {item.title}
                  </h3>
                  <p className="text-xs sm:text-sm text-fleet-muted leading-relaxed">
                    {item.description}
                  </p>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </section>
  );
};

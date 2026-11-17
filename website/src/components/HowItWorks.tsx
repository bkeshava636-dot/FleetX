import React from 'react';
import { Download, Smartphone, MapPin, CheckCircle, Navigation } from 'lucide-react';

export const HowItWorks: React.FC = () => {
  const steps = [
    {
      step: '01',
      title: 'Download FleetX',
      description: 'Grab the latest verified APK directly from our official GitHub release portal.',
      icon: Download,
    },
    {
      step: '02',
      title: 'Open the Application',
      description: 'Launch FleetX on your Android phone. No lengthy registration forms required for students.',
      icon: Smartphone,
    },
    {
      step: '03',
      title: 'Explore Buses & Routes',
      description: 'Browse the college transit directory to inspect routes, ordered stops, and timetable timings.',
      icon: MapPin,
    },
    {
      step: '04',
      title: 'Select Your Bus & Stop',
      description: 'Choose your regular bus number and primary pickup stop to personalize your tracking view.',
      icon: CheckCircle,
    },
    {
      step: '05',
      title: 'Track Live & Check ETAs',
      description: 'Watch your bus move along the route in real time and catch it right on schedule.',
      icon: Navigation,
    },
  ];

  return (
    <section id="how-it-works" className="py-20 bg-college-surface">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Section Header */}
        <div className="text-center max-w-3xl mx-auto mb-16">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-college-primary-container text-college-primary-dark text-xs font-semibold uppercase tracking-wider mb-4">
            Simple 5-Step Process
          </div>
          <h2 className="text-3xl sm:text-4xl font-extrabold text-college-on-surface tracking-tight">
            How FleetX Works for Students
          </h2>
          <p className="mt-4 text-base sm:text-lg text-college-on-surface-variant leading-relaxed">
            From downloading the app to seeing your bus arriving live — designed to be effortless and intuitive.
          </p>
        </div>

        {/* Steps Grid */}
        <div className="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-5 gap-6 relative">
          {steps.map((item, idx) => {
            const IconComponent = item.icon;
            return (
              <div
                key={item.step}
                className="relative flex flex-col items-start bg-white p-6 rounded-2xl border border-slate-200/90 shadow-sm hover:shadow-md transition-shadow duration-200"
              >
                {/* Step Number Badge */}
                <div className="flex items-center justify-between w-full mb-4">
                  <div className="w-10 h-10 rounded-xl bg-college-primary-container/60 text-college-primary flex items-center justify-center font-bold text-sm">
                    <IconComponent className="w-5 h-5" />
                  </div>
                  <span className="text-2xl font-black text-slate-200 select-none">
                    {item.step}
                  </span>
                </div>

                {/* Step Title */}
                <h3 className="text-base font-bold text-college-on-surface mb-2">
                  {item.title}
                </h3>

                {/* Step Description */}
                <p className="text-xs text-college-on-surface-variant leading-relaxed">
                  {item.description}
                </p>

                {/* Arrow connector for desktop */}
                {idx < steps.length - 1 && (
                  <div className="hidden lg:block absolute -right-3 top-1/2 -translate-y-1/2 z-10 text-slate-300">
                    →
                  </div>
                )}
              </div>
            );
          })}
        </div>

      </div>
    </section>
  );
};

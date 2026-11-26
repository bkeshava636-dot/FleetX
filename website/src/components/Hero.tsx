import React, { useEffect, useRef, useState } from 'react';
import { animate, stagger } from 'animejs';
import { ArrowRight, CheckCircle2, Compass, ShieldCheck, Smartphone } from 'lucide-react';
import { ReleaseInfo } from '../types/github';
import logoUrl from '../assets/logo/fleetx-logo.png';
import heroTrackScreenshot from '../assets/screenshots/hero-track-screen.jpg';
import { Badge } from './ui/badge';
import { Button } from './ui/button';

interface HeroProps {
  latestRelease: ReleaseInfo | null;
  onDownloadClick: () => void;
}

export const Hero: React.FC<HeroProps> = ({ latestRelease, onDownloadClick }) => {
  const [phoneView, setPhoneView] = useState<'app' | 'splash'>('splash');
  const heroLeftRef = useRef<HTMLDivElement>(null);
  const phoneRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const prefersReduced =
      typeof window !== 'undefined' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (prefersReduced) return;

    if (heroLeftRef.current) {
      const targets = heroLeftRef.current.querySelectorAll('[data-hero-item]');
      animate(targets, {
        opacity: [0, 1],
        translateY: [14, 0],
        duration: 340,
        delay: stagger(50),
        ease: 'outExpo',
      });
    }

    if (phoneRef.current) {
      animate(phoneRef.current, {
        opacity: [0, 1],
        translateY: [16, 0],
        scale: [0.97, 1],
        duration: 380,
        ease: 'outExpo',
      });
    }
  }, []);

  return (
    <section id="home" className="relative pt-24 pb-16 sm:pt-28 sm:pb-20 lg:pt-32 lg:pb-24 overflow-hidden">
      {/* Subtle blue glow matching previous Hero */}
      <div className="absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[380px] bg-college-primary-container/40 rounded-full blur-3xl pointer-events-none -z-10" />
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 lg:gap-10 items-center">
          {/* Left Column */}
          <div ref={heroLeftRef} className="lg:col-span-7 flex flex-col items-start text-left">
            <div data-hero-item className="mb-5">
              <Badge variant="success" className="px-3 py-1">
                <span className="relative flex h-2 w-2">
                  <span className="animate-ping motion-reduce:animate-none absolute inline-flex h-full w-full rounded-full bg-emerald-500 opacity-75" />
                  <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-600" />
                </span>
                <span>Live Campus Transit Platform</span>
                {latestRelease && (
                  <span className="text-emerald-800/80 font-mono">
                    · v{latestRelease.version}
                  </span>
                )}
              </Badge>
            </div>

            <h1
              data-hero-item
              className="text-3xl sm:text-5xl lg:text-[3.25rem] font-extrabold text-slate-900 tracking-tight leading-[1.12] mb-5"
            >
              College Transportation,{' '}
              <span className="text-college-primary">Smarter and Simpler.</span>
            </h1>

            <p
              data-hero-item
              className="text-base sm:text-lg text-slate-600 max-w-2xl leading-relaxed mb-8"
            >
              Track buses in real time, manage routes, coordinate drivers and keep students informed — all from one connected platform.
            </p>

            <div
              data-hero-item
              className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3 w-full sm:w-auto mb-8"
            >
              <Button size="lg" onClick={onDownloadClick} className="shadow-md">
                <span>Explore FleetX</span>
                <ArrowRight className="w-4 h-4" />
              </Button>

              <Button size="lg" variant="outline" asChild>
                <a href="#features">
                  <Compass className="w-4 h-4 text-college-primary" />
                  <span>Discover Features</span>
                </a>
              </Button>
            </div>

            {/* Clean Trust Bar */}
            <div
              data-hero-item
              className="grid grid-cols-1 sm:grid-cols-3 gap-3 sm:gap-5 pt-5 border-t border-slate-200/90 w-full text-xs font-medium text-slate-600"
            >
              <div className="flex items-center gap-2">
                <CheckCircle2 className="w-4 h-4 text-status-green shrink-0" />
                <span>Real-time RTDB GPS tracking</span>
              </div>
              <div className="flex items-center gap-2">
                <Smartphone className="w-4 h-4 text-college-primary shrink-0" />
                <span>Android 8.0+ Student & Driver App</span>
              </div>
              <div className="flex items-center gap-2">
                <ShieldCheck className="w-4 h-4 text-status-green shrink-0" />
                <span>Verified GitHub Release APKs</span>
              </div>
            </div>
          </div>

          {/* Right Column: Clean Product Visual */}
          <div className="lg:col-span-5 flex flex-col items-center lg:items-end">
            <div className="inline-flex items-center gap-1 p-1 bg-slate-100 rounded-xl border border-slate-200 mb-4">
              <button
                type="button"
                onClick={() => setPhoneView('splash')}
                className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all duration-150 ${
                  phoneView === 'splash'
                    ? 'bg-white text-slate-900 shadow-sm'
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                App Identity
              </button>
              <button
                type="button"
                onClick={() => setPhoneView('app')}
                className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all duration-150 ${
                  phoneView === 'app'
                    ? 'bg-white text-slate-900 shadow-sm'
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                Live Student View
              </button>
            </div>

            <div ref={phoneRef} className="relative mx-auto w-full max-w-[290px] sm:max-w-[310px]">
              <div className="relative rounded-[42px] p-2.5 bg-slate-900 shadow-phone border-4 border-slate-800">
                <div className="absolute top-5 left-1/2 -translate-x-1/2 w-14 h-1 bg-slate-700 rounded-full z-20" />
                <div
                  onClick={() => setPhoneView((v) => (v === 'app' ? 'splash' : 'app'))}
                  role="button"
                  tabIndex={0}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      setPhoneView((v) => (v === 'app' ? 'splash' : 'app'));
                    }
                  }}
                  aria-label="Toggle phone screen preview"
                  className="relative rounded-[32px] overflow-hidden bg-white cursor-pointer aspect-[9/19.5] select-none focus:outline-none focus-visible:ring-2 focus-visible:ring-college-primary"
                >
                  {phoneView === 'app' ? (
                    <img
                      src={heroTrackScreenshot}
                      alt="FleetX Live Student Bus Tracking Interface"
                      className="w-full h-full object-cover"
                      loading="eager"
                    />
                  ) : (
                    <div className="w-full h-full bg-slate-50 flex flex-col items-center justify-center p-6 text-center">
                      <div className="relative mb-4">
                        <div className="w-20 h-20 rounded-2xl bg-white shadow-md border border-slate-200 flex items-center justify-center p-3">
                          <img
                            src={logoUrl}
                            alt="FleetX Logo"
                            className="w-full h-full object-contain"
                          />
                        </div>
                        <div className="absolute inset-0 rounded-2xl bg-college-primary/15 animate-ping motion-reduce:animate-none pointer-events-none" />
                      </div>
                      <p className="text-2xl font-extrabold text-slate-900 tracking-tight">FleetX</p>
                      <p className="text-[11px] font-bold text-college-primary uppercase tracking-widest mt-1">
                        Campus Transit Portal
                      </p>
                      <span className="mt-4 px-3 py-1 rounded-full bg-college-primary-container text-college-primary-dark text-xs font-semibold">
                        BITM Ballari
                      </span>
                    </div>
                  )}
                </div>
              </div>

              {/* Clean caption below phone (zero overlap) */}
              <div className="mt-3 flex items-center justify-center gap-2 text-xs text-slate-500">
                <span className="h-2 w-2 rounded-full bg-status-green" />
                <span>Actual Android app capture · Tap screen to switch view</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};

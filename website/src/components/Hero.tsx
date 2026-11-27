import React, { useEffect, useRef, useState } from 'react';
import { animate, stagger } from 'animejs';
import { ArrowRight, Bus, CheckCircle2, Compass, Radio, ShieldCheck, Smartphone } from 'lucide-react';
import { ReleaseInfo } from '../types/github';
import logoUrl from '../assets/logo/fleetx-logo.png';
import heroTrackScreenshot from '../assets/screenshots/hero-track-screen.jpg';
import { useRoutePathDraw } from '@/lib/useMotionReveal';
import { CoordinateTag, MapGridBackdrop } from './MobilityPrimitives';

interface HeroProps {
  latestRelease: ReleaseInfo | null;
  onDownloadClick: () => void;
}

export const Hero: React.FC<HeroProps> = ({ latestRelease, onDownloadClick }) => {
  const [phoneView, setPhoneView] = useState<'app' | 'splash'>('splash');
  const heroLeftRef = useRef<HTMLDivElement>(null);
  const phoneRef = useRef<HTMLDivElement>(null);
  const routePathRef = useRef<SVGPathElement>(null);

  useRoutePathDraw(routePathRef);

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
        delay: stagger(45),
        ease: 'outExpo',
      });
    }

    if (phoneRef.current) {
      animate(phoneRef.current, {
        opacity: [0, 1],
        translateY: [18, 0],
        scale: [0.97, 1],
        duration: 420,
        ease: 'outExpo',
      });
    }
  }, []);

  return (
    <section
      id="home"
      className="relative bg-fleet-navy text-fleet-offwhite pt-28 pb-16 sm:pt-32 sm:pb-24 lg:pt-36 lg:pb-28 overflow-hidden border-b border-white/10"
    >
      <MapGridBackdrop dark />

      <div
        aria-hidden="true"
        className="pointer-events-none absolute -top-24 right-0 h-[440px] w-[540px] rounded-full bg-fleet-cyan/10 blur-3xl"
      />

      {/* Signature animated route path behind product composition */}
      <svg
        aria-hidden="true"
        viewBox="0 0 1200 420"
        className="pointer-events-none hidden md:block absolute inset-x-0 top-12 mx-auto w-full max-w-7xl opacity-45"
        fill="none"
      >
        <path
          ref={routePathRef}
          d="M 520 350 C 690 340, 740 175, 910 175 C 1030 175, 1080 95, 1170 95"
          stroke="#45D6E8"
          strokeWidth="2"
          strokeLinecap="round"
        />
        <circle cx="520" cy="350" r="4.5" fill="#45D6E8" />
        <circle cx="910" cy="175" r="4.5" fill="#C5F36A" />
        <circle cx="1170" cy="95" r="5" fill="#45D6E8" />
      </svg>

      <div className="relative z-10 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 lg:gap-10 items-center">
          {/* Left Column */}
          <div ref={heroLeftRef} className="lg:col-span-6 flex flex-col items-start text-left">
            <div data-hero-item className="flex flex-wrap items-center gap-2.5 mb-6">
              <span className="inline-flex items-center gap-2 rounded-full border border-fleet-cyan/30 bg-fleet-cyan/10 px-3 py-1 text-xs font-semibold text-fleet-cyan">
                <span className="relative flex h-2 w-2">
                  <span className="animate-ping motion-reduce:animate-none absolute inline-flex h-full w-full rounded-full bg-fleet-lime opacity-75" />
                  <span className="relative inline-flex rounded-full h-2 w-2 bg-fleet-lime" />
                </span>
                <span>Live GPS Tracking</span>
                {latestRelease && (
                  <span className="font-mono text-[11px] text-fleet-offwhite/85">
                    · v{latestRelease.version}
                  </span>
                )}
              </span>
              <CoordinateTag />
            </div>

            <h1
              data-hero-item
              className="font-display text-4xl sm:text-5xl lg:text-[3.5rem] font-bold text-fleet-offwhite tracking-tight leading-[1.08] mb-5"
            >
              Moving campuses.{' '}
              <span className="text-fleet-cyan block sm:inline">Connecting journeys.</span>
            </h1>

            <p
              data-hero-item
              className="text-base sm:text-lg text-fleet-muted max-w-xl leading-relaxed mb-8"
            >
              One connected platform to track buses, manage drivers and simplify college transportation.
            </p>

            <div
              data-hero-item
              className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3 w-full sm:w-auto mb-9"
            >
              <button
                type="button"
                onClick={onDownloadClick}
                className="inline-flex items-center justify-center gap-2.5 rounded-xl bg-fleet-lime px-6 py-3.5 text-sm sm:text-base font-bold text-fleet-navy shadow-md hover:bg-fleet-lime/90 transition-all duration-150 active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-fleet-lime focus-visible:ring-offset-2 focus-visible:ring-offset-fleet-navy"
              >
                <span>Explore FleetX</span>
                <ArrowRight className="w-4 h-4" />
              </button>

              <a
                href="#features"
                className="inline-flex items-center justify-center gap-2 rounded-xl border border-white/15 bg-fleet-deep/80 px-6 py-3.5 text-sm sm:text-base font-semibold text-fleet-offwhite hover:bg-white/10 transition-colors"
              >
                <Compass className="w-4 h-4 text-fleet-cyan" />
                <span>Discover Features</span>
              </a>
            </div>

            <div
              data-hero-item
              className="grid grid-cols-1 sm:grid-cols-3 gap-3 sm:gap-5 pt-5 border-t border-white/10 w-full text-xs sm:text-sm font-medium text-fleet-muted"
            >
              <div className="flex items-center gap-2">
                <CheckCircle2 className="w-4 h-4 text-fleet-lime shrink-0" />
                <span>Real-time RTDB GPS</span>
              </div>
              <div className="flex items-center gap-2">
                <Smartphone className="w-4 h-4 text-fleet-cyan shrink-0" />
                <span>Android 8.0+ App</span>
              </div>
              <div className="flex items-center gap-2">
                <ShieldCheck className="w-4 h-4 text-fleet-lime shrink-0" />
                <span>Verified GitHub APKs</span>
              </div>
            </div>
          </div>

          {/* Right Column: Live Tracking Product Composition */}
          <div className="lg:col-span-6 flex flex-col items-center lg:items-end">
            <div className="inline-flex items-center gap-1 p-1 bg-fleet-deep rounded-xl border border-white/10 mb-4">
              <button
                type="button"
                onClick={() => setPhoneView('splash')}
                className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all duration-150 ${
                  phoneView === 'splash'
                    ? 'bg-fleet-navy text-fleet-offwhite shadow-sm border border-white/10'
                    : 'text-fleet-muted hover:text-fleet-offwhite'
                }`}
              >
                App Identity
              </button>
              <button
                type="button"
                onClick={() => setPhoneView('app')}
                className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all duration-150 ${
                  phoneView === 'app'
                    ? 'bg-fleet-navy text-fleet-offwhite shadow-sm border border-white/10'
                    : 'text-fleet-muted hover:text-fleet-offwhite'
                }`}
              >
                Live Student View
              </button>
            </div>

            <div ref={phoneRef} className="relative mx-auto lg:mx-0 w-full max-w-[305px] sm:max-w-[335px]">
              {/* Large Smartphone Mockup */}
              <div className="relative rounded-[44px] p-3 bg-slate-950 shadow-2xl border-2 border-white/15">
                <div className="absolute top-5 left-1/2 -translate-x-1/2 w-14 h-1 bg-slate-700 rounded-full z-20" />
                <div
                  onClick={() => setPhoneView((v) => (v === 'splash' ? 'app' : 'splash'))}
                  role="button"
                  tabIndex={0}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      setPhoneView((v) => (v === 'splash' ? 'app' : 'splash'));
                    }
                  }}
                  aria-label="Toggle phone screen preview"
                  className="relative rounded-[34px] overflow-hidden bg-white cursor-pointer aspect-[9/19.5] select-none focus:outline-none focus-visible:ring-2 focus-visible:ring-fleet-cyan"
                >
                  {phoneView === 'app' ? (
                    <img
                      src={heroTrackScreenshot}
                      alt="FleetX Live Student Bus Tracking Interface"
                      className="w-full h-full object-cover"
                      loading="lazy"
                    />
                  ) : (
                    <div className="fleetx-phone-screen w-full h-full bg-gradient-to-b from-[#0B1220] via-[#111C2E] to-[#0B1220] text-white flex flex-col justify-between p-6 text-center">
                      <div className="flex items-center justify-between text-[10px] font-mono text-slate-400 pt-2">
                        <span>FLEETX OS</span>
                        <span className="text-[#C5F36A]">ONLINE</span>
                      </div>

                      <div className="flex flex-col items-center justify-center my-auto">
                        <div className="relative mb-5">
                          <div className="w-20 h-20 rounded-2xl bg-white shadow-xl border border-white/20 flex items-center justify-center p-3">
                            <img
                              src={logoUrl}
                              alt="FleetX Logo"
                              className="w-full h-full object-contain"
                            />
                          </div>
                          <div className="absolute inset-0 rounded-2xl bg-[#45D6E8]/25 animate-ping motion-reduce:animate-none pointer-events-none" />
                        </div>
                        <p className="font-display text-2xl font-bold text-white tracking-tight">
                          Fleet<span className="text-[#45D6E8]">X</span>
                        </p>
                        <p className="font-mono text-[10px] font-semibold text-[#45D6E8] uppercase tracking-widest mt-1">
                          Campus Transit Portal
                        </p>
                        <span className="mt-4 px-3 py-1 rounded-full bg-white/10 border border-white/15 text-slate-200 text-xs font-semibold">
                          BITM Ballari
                        </span>
                      </div>

                      <div className="rounded-xl border border-white/10 bg-white/[0.04] px-3 py-2 text-[11px] text-slate-300">
                        Tap screen for <span className="text-[#C5F36A] font-semibold">Live Student View</span>
                      </div>                    </div>
                  )}
                </div>
              </div>

              {/* Floating Bus Info Card (positioned completely left of phone screen on desktop) */}
              <div className="hidden lg:flex items-center gap-3 rounded-2xl border border-white/15 bg-[#111C2E]/95 backdrop-blur-md px-4 py-3 shadow-2xl absolute -left-56 bottom-28 z-30">
                <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-fleet-cyan/15 border border-fleet-cyan/30 text-fleet-cyan shrink-0">
                  <Bus className="w-5 h-5" />
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <span className="font-display text-xs font-bold text-white">
                      Campus Bus Route
                    </span>
                    <span className="inline-flex items-center gap-1 rounded-full bg-fleet-lime/15 px-2 py-0.5 font-mono text-[10px] font-semibold text-fleet-lime">
                      <Radio className="w-2.5 h-2.5" />
                      RTDB
                    </span>
                  </div>
                  <p className="text-[11px] text-fleet-muted mt-0.5">
                    Live telemetry &amp; driver info
                  </p>
                </div>
              </div>

              <div className="mt-3 flex items-center justify-center gap-2 text-xs text-fleet-muted">
                <span className="h-2 w-2 rounded-full bg-fleet-lime" />
                <span>
                  {phoneView === 'splash'
                    ? 'FleetX App Identity · Tap to open Live Student View'
                    : 'Actual Android live tracking capture · Tap to switch'}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};

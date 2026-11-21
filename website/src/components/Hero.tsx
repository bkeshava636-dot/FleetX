import React, { useState } from 'react';
import { Download, Compass, Smartphone, ShieldCheck, CheckCircle2 } from 'lucide-react';
import { ReleaseInfo } from '../types/github';
import logoUrl from '../assets/logo/fleetx-logo.png';
import heroTrackScreenshot from '../assets/screenshots/hero-track-screen.jpg';

interface HeroProps {
  latestRelease: ReleaseInfo | null;
  onDownloadClick: () => void;
}

export const Hero: React.FC<HeroProps> = ({ latestRelease, onDownloadClick }) => {
  const [phoneView, setPhoneView] = useState<'splash' | 'app'>('splash');

  return (
    <section id="home" className="relative pt-28 pb-16 md:pt-36 md:pb-24 overflow-hidden">
      {/* Subtle background glow matching CollegePrimaryContainer */}
      <div className="absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[380px] bg-college-primary-container/40 rounded-full blur-3xl pointer-events-none -z-10" />

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 lg:gap-8 items-center">
          
          {/* Left Column: Headline and Call to Actions */}
          <div className="lg:col-span-7 flex flex-col items-start text-left">
            {/* Live Status Pill */}
            <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-status-green-bg/80 border border-status-green/20 mb-6">
              <span className="relative flex h-2 w-2">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-status-green opacity-75"></span>
                <span className="relative inline-flex rounded-full h-2 w-2 bg-status-green"></span>
              </span>
              <span className="text-xs font-semibold text-status-green tracking-wide">
                Live Campus Transit Tracking
              </span>
              <span className="text-slate-300">|</span>
              <span className="text-xs font-medium text-slate-700">
                {latestRelease ? `v${latestRelease.version} Active` : 'Android'}
              </span>
            </div>

            {/* Main Headline */}
            <h1 className="text-4xl sm:text-5xl lg:text-6xl font-extrabold text-college-on-surface tracking-tight leading-[1.15] mb-6">
              Your College Bus.{' '}
              <span className="text-college-primary block sm:inline">Always in Sight.</span>
            </h1>

            {/* Supporting Text */}
            <p className="text-lg sm:text-xl text-college-on-surface-variant max-w-2xl font-normal leading-relaxed mb-8">
              Track your college bus in real time, check estimated arrival times, view driver information and stay updated with important transportation announcements.
            </p>

            {/* Action Buttons */}
            <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-4 w-full sm:w-auto mb-8">
              <button
                type="button"
                onClick={onDownloadClick}
                className="inline-flex items-center justify-center gap-3 bg-college-primary hover:bg-college-primary-dark active:bg-blue-800 text-white text-base font-semibold px-6 py-3.5 rounded-xl shadow-md hover:shadow-lg transition-all duration-200 focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-college-primary"
              >
                <Download className="w-5 h-5" />
                <span>Download for Android</span>
                {latestRelease?.apkAsset && (
                  <span className="text-xs bg-white/20 px-2 py-0.5 rounded-md font-mono">
                    {latestRelease.apkAsset.formattedSize}
                  </span>
                )}
              </button>

              <a
                href="#features"
                className="inline-flex items-center justify-center gap-2 bg-white hover:bg-slate-50 text-college-on-surface border border-slate-200 text-base font-medium px-6 py-3.5 rounded-xl shadow-sm hover:shadow transition-all duration-200"
              >
                <Compass className="w-5 h-5 text-college-primary" />
                <span>Explore Features</span>
              </a>
            </div>

            {/* Trust Markers */}
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-4 pt-4 border-t border-slate-200/80 w-full text-xs text-college-on-surface-variant">
              <div className="flex items-center gap-2">
                <CheckCircle2 className="w-4 h-4 text-status-green flex-shrink-0" />
                <span>Zero Subscription Cost</span>
              </div>
              <div className="flex items-center gap-2">
                <Smartphone className="w-4 h-4 text-college-primary flex-shrink-0" />
                <span>Android 8.0 (API 26)+</span>
              </div>
              <div className="flex items-center gap-2 col-span-2 sm:col-span-1">
                <ShieldCheck className="w-4 h-4 text-status-green flex-shrink-0" />
                <span>Verified Direct APK</span>
              </div>
            </div>
          </div>

          {/* Right Column: Realistic Android Mockup */}
          <div className="lg:col-span-5 flex flex-col items-center lg:items-end">
            
            {/* View Switcher Toggle */}
            <div className="inline-flex items-center gap-1 p-1 bg-slate-100 rounded-full border border-slate-200/80 mb-3 shadow-inner">
              <button
                type="button"
                onClick={() => setPhoneView('splash')}
                className={`px-3.5 py-1 rounded-full text-xs font-semibold transition-all duration-200 ${
                  phoneView === 'splash'
                    ? 'bg-college-primary text-white shadow-sm'
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                App Opening Logo
              </button>
              <button
                type="button"
                onClick={() => setPhoneView('app')}
                className={`px-3.5 py-1 rounded-full text-xs font-semibold transition-all duration-200 ${
                  phoneView === 'app'
                    ? 'bg-college-primary text-white shadow-sm'
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                Live Student View
              </button>
            </div>

            <div className="relative mx-auto w-full max-w-[320px] sm:max-w-[340px]">
              {/* Outer Phone Shell */}
              <div className="relative rounded-[44px] p-3 bg-slate-900 shadow-phone border-4 border-slate-800">
                {/* Speaker Ear-piece */}
                <div className="absolute top-6 left-1/2 -translate-x-1/2 w-16 h-1 bg-slate-700 rounded-full z-20" />
                {/* Front Camera Punch-hole */}
                <div className="absolute top-9 left-1/2 -translate-x-1/2 w-3.5 h-3.5 bg-black rounded-full border border-slate-800 z-20 flex items-center justify-center">
                  <div className="w-1 h-1 rounded-full bg-blue-950/80" />
                </div>

                {/* Screen Content Wrapper */}
                <div
                  onClick={() => setPhoneView(phoneView === 'splash' ? 'app' : 'splash')}
                  className="relative rounded-[34px] overflow-hidden bg-white border border-slate-900/10 cursor-pointer aspect-[9/19.5] select-none"
                  title="Click to toggle between App Opening Logo and Live Student View"
                >
                  {phoneView === 'splash' ? (
                    /* App Opening Splash Screen with Official Logo */
                    <div className="w-full h-full bg-gradient-to-b from-[#F8F9FA] via-white to-[#EEF2F6] flex flex-col justify-between p-6">
                      {/* Top Android Status Bar */}
                      <div className="w-full flex items-center justify-between text-[11px] text-slate-400 font-medium pt-3 px-1">
                        <span>19:20</span>
                        <div className="flex items-center gap-1.5 text-[10px]">
                          <span>VoLTE</span>
                          <span>100%</span>
                        </div>
                      </div>

                      {/* Center App Opening Logo */}
                      <div className="flex-1 flex flex-col items-center justify-center text-center -mt-4">
                        <div className="relative mb-5 group">
                          <div className="w-24 h-24 sm:w-28 sm:h-28 rounded-3xl bg-white shadow-xl border border-slate-100 flex items-center justify-center p-4">
                            <img
                              src={logoUrl}
                              alt="FleetX Opening Logo"
                              className="w-full h-full object-contain"
                            />
                          </div>
                          <div className="absolute inset-0 rounded-3xl bg-college-primary/10 animate-ping pointer-events-none" />
                        </div>

                        <h2 className="text-3xl font-extrabold text-college-on-surface tracking-tight">
                          FleetX
                        </h2>
                        <p className="text-xs font-bold text-college-primary uppercase tracking-widest mt-1">
                          Campus Transit Portal
                        </p>
                        <div className="mt-4 px-3.5 py-1 rounded-full bg-college-primary-container text-college-primary-dark text-xs font-semibold">
                          BITM Ballari
                        </div>
                      </div>

                      {/* Bottom Loading / Sync Indicator */}
                      <div className="w-full flex flex-col items-center pb-4 text-center">
                        <div className="flex items-center gap-1.5 mb-2">
                          <span className="w-1.5 h-1.5 rounded-full bg-college-primary animate-bounce [animation-delay:-0.3s]"></span>
                          <span className="w-1.5 h-1.5 rounded-full bg-college-primary animate-bounce [animation-delay:-0.15s]"></span>
                          <span className="w-1.5 h-1.5 rounded-full bg-college-primary animate-bounce"></span>
                        </div>
                        <span className="text-[11px] text-slate-400 font-medium">
                          {latestRelease ? `v${latestRelease.version}` : 'v1.0.44'} • Tap screen to view app
                        </span>
                      </div>
                    </div>
                  ) : (
                    /* Real Live Student View Screenshot */
                    <img
                      src={heroTrackScreenshot}
                      alt="FleetX Real Student Transit Portal on Android"
                      className="w-full h-full object-cover select-none pointer-events-none"
                      loading="eager"
                    />
                  )}
                </div>

                {/* Floating ETA Live Card Overlay */}
                <div className="absolute -bottom-4 -left-6 sm:-left-8 bg-white/95 backdrop-blur-md p-3.5 rounded-2xl border border-slate-200/90 shadow-xl flex items-center gap-3 z-30 max-w-[250px]">
                  <div className="w-10 h-10 rounded-xl bg-college-primary-container flex items-center justify-center text-college-primary flex-shrink-0">
                    <span className="text-xl">🚌</span>
                  </div>
                  <div className="flex flex-col">
                    <span className="text-xs font-bold text-college-on-surface">
                      {phoneView === 'splash' ? 'FleetX Transit Active' : 'Bus 01 • En Route'}
                    </span>
                    <span className="text-[11px] font-semibold text-status-green">
                      {phoneView === 'splash' ? 'Campus Service Online' : 'Assigned Driver: Ramesh'}
                    </span>
                    <span className="text-[9px] text-slate-400">
                      BITM • Ballari Transit Network
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>

        </div>
      </div>
    </section>
  );
};

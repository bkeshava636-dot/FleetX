import React, { useState } from 'react';
import {
  AlertCircle,
  Calendar,
  Check,
  ChevronDown,
  Copy,
  Download,
  FileCode,
  HelpCircle,
  Layers,
  ShieldCheck,
  Smartphone,
} from 'lucide-react';
import logoUrl from '../assets/logo/fleetx-logo.png';
import { ReleaseNotesContent } from './common/ReleaseNotesContent';
import studentHomeImg from '../assets/screenshots/screen-student-home.jpg';
import { ReleaseInfo } from '../types/github';
import { Badge } from './ui/badge';

interface DownloadSectionProps {
  latestRelease: ReleaseInfo | null;
  isLoading: boolean;
  isError: boolean;
  errorMessage: string | null;
}

export const DownloadSection: React.FC<DownloadSectionProps> = ({
  latestRelease,
  isLoading,
  isError,
  errorMessage,
}) => {
  const [copiedChecksum, setCopiedChecksum] = useState(false);
  const [showInstallGuide, setShowInstallGuide] = useState(false);

  const handleCopyChecksum = (checksum: string) => {
    navigator.clipboard.writeText(checksum);
    setCopiedChecksum(true);
    setTimeout(() => setCopiedChecksum(false), 2500);
  };

  const handleDownload = () => {
    if (latestRelease?.apkAsset?.downloadUrl) {
      window.location.href = latestRelease.apkAsset.downloadUrl;
      setShowInstallGuide(true);
    }
  };

  return (
    <section id="download" className="py-20 sm:py-28 bg-fleet-offwhite border-t border-slate-200/80">
      <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="text-center max-w-2xl mx-auto mb-12">
          <Badge variant="secondary" className="mb-3 font-mono uppercase tracking-wider text-[11px]">
            Official Android Release
          </Badge>
          <h2 className="font-display text-3xl sm:text-4xl font-bold text-fleet-navy tracking-tight">
            Get FleetX on Your Android Device
          </h2>
          <p className="mt-3 text-sm sm:text-base text-slate-600 leading-relaxed">
            Download the verified FleetX APK directly from our official GitHub release feed.
          </p>
        </div>

        {isLoading ? (
          <div className="bg-white p-12 rounded-3xl border border-slate-200 shadow-sm flex flex-col items-center justify-center text-center">
            <div className="w-10 h-10 border-4 border-college-primary border-t-transparent rounded-full animate-spin mb-4" />
            <p className="text-sm font-semibold text-fleet-navy">
              Checking GitHub Releases for latest APK...
            </p>
            <p className="text-xs text-slate-500 mt-1">Connecting to official repository</p>
          </div>
        ) : isError || !latestRelease || !latestRelease.apkAsset ? (
          <div className="bg-white p-10 rounded-3xl border border-amber-200 shadow-sm text-center max-w-2xl mx-auto">
            <div className="w-12 h-12 mx-auto mb-4 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center">
              <AlertCircle className="w-6 h-6" />
            </div>
            <h3 className="font-display text-lg font-bold text-fleet-navy mb-2">
              App download is temporarily unavailable. Please check back later.
            </h3>
            <p className="text-sm text-slate-600 max-w-md mx-auto mb-6">
              {errorMessage || 'Unable to retrieve the latest APK asset from GitHub releases at this moment.'}
            </p>
            <a
              href="https://github.com/bkeshava636-dot/FleetX/releases"
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-2 text-sm font-semibold text-college-primary hover:underline"
            >
              <span>View releases directly on GitHub</span>
              <span>↗</span>
            </a>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Premium Main Download Showcase Card */}
            <div className="rounded-3xl bg-fleet-navy text-fleet-offwhite border border-white/10 shadow-xl overflow-hidden">
              <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 p-6 sm:p-10 items-center">
                {/* Left Branding + Version + Prominent CTA */}
                <div className="lg:col-span-7 space-y-6">
                  <div className="flex items-center gap-3">
                    <img
                      src={logoUrl}
                      alt="FleetX Logo"
                      className="w-11 h-11 rounded-xl bg-white p-0.5 object-cover border border-white/15"
                    />
                    <div>
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-display text-xl font-bold tracking-tight">
                          Fleet<span className="text-fleet-cyan">X</span> for Android
                        </span>
                        <span className="rounded-full bg-fleet-cyan/15 border border-fleet-cyan/30 px-2.5 py-0.5 font-mono text-xs font-semibold text-fleet-cyan">
                          v{latestRelease.version}
                        </span>
                      </div>
                      <p className="text-xs text-fleet-muted mt-0.5 flex items-center gap-1.5">
                        <Calendar className="w-3.5 h-3.5" />
                        <span>Released {latestRelease.formattedDate}</span>
                      </p>
                    </div>
                  </div>

                  <p className="text-sm sm:text-base text-fleet-muted leading-relaxed">
                    {latestRelease.whatsNewSummary ||
                      'Track campus buses in real time, inspect ordered route stops, and receive official transport notices.'}
                  </p>

                  {/* Compatibility & Size Pills */}
                  <div className="flex flex-wrap gap-3 text-xs">
                    <div className="inline-flex items-center gap-2 rounded-xl border border-white/10 bg-fleet-deep px-3.5 py-2">
                      <Smartphone className="w-4 h-4 text-fleet-cyan" />
                      <span>Android 8.0 (API 26)+</span>
                    </div>
                    <div className="inline-flex items-center gap-2 rounded-xl border border-white/10 bg-fleet-deep px-3.5 py-2 font-mono">
                      <span>{latestRelease.apkAsset.formattedSize}</span>
                    </div>
                    <div className="inline-flex items-center gap-2 rounded-xl border border-white/10 bg-fleet-deep px-3.5 py-2 text-fleet-lime">
                      <ShieldCheck className="w-4 h-4" />
                      <span>Verified Direct APK</span>
                    </div>
                  </div>

                  <div className="pt-1 flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
                    <button
                      type="button"
                      onClick={handleDownload}
                      className="inline-flex items-center justify-center gap-2.5 rounded-xl bg-fleet-lime px-6 py-3.5 text-sm sm:text-base font-bold text-fleet-navy shadow-md hover:bg-fleet-lime/90 transition-all duration-150 active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-fleet-lime"
                    >
                      <Download className="w-5 h-5" />
                      <span>Download APK (v{latestRelease.version})</span>
                    </button>

                    <span className="font-mono text-xs text-fleet-muted text-center sm:text-left">
                      {latestRelease.apkAsset.name}
                    </span>
                  </div>

                  {showInstallGuide && (
                    <div className="p-3.5 bg-fleet-lime/10 border border-fleet-lime/30 rounded-xl text-fleet-lime text-xs flex items-center justify-between">
                      <span>Download started! Expand the Installation Guide below if you need setup steps.</span>
                      <button
                        type="button"
                        onClick={() => setShowInstallGuide(false)}
                        className="font-bold px-2 hover:opacity-80"
                        aria-label="Dismiss message"
                      >
                        ✕
                      </button>
                    </div>
                  )}
                </div>

                {/* Right Phone Mockup */}
                <div className="lg:col-span-5 flex justify-center lg:justify-end">
                  <div className="w-full max-w-[220px] sm:max-w-[240px] rounded-[34px] p-2 bg-slate-950 border-2 border-white/15 shadow-2xl">
                    <div className="rounded-[26px] overflow-hidden bg-white aspect-[9/19.5]">
                      <img
                        src={studentHomeImg}
                        alt="FleetX Student Android Application"
                        className="w-full h-full object-cover"
                        loading="lazy"
                      />
                    </div>
                  </div>
                </div>
              </div>
            </div>

            {/* Expandable Technical Details (Checksum, Release Notes, Install Guide) */}
            <div className="fleetx-faq grid grid-cols-1 gap-3">
              {latestRelease.sha256 && (
                <details className="group rounded-2xl border border-slate-200 bg-white shadow-sm overflow-hidden">
                  <summary className="flex cursor-pointer list-none items-center justify-between gap-4 px-5 py-4 text-sm font-bold text-fleet-navy hover:bg-slate-50/80 transition-colors [&::-webkit-details-marker]:hidden">
                    <span className="flex items-center gap-2.5">
                      <FileCode className="w-4 h-4 text-college-primary" />
                      <span>SHA-256 Verification Checksum</span>
                    </span>
                    <ChevronDown className="w-4 h-4 text-slate-500 transition-transform duration-200 group-open:rotate-180" />
                  </summary>
                  <div className="px-5 pb-4 pt-2 border-t border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                    <code className="text-xs font-mono text-slate-800 break-all select-all">
                      {latestRelease.sha256}
                    </code>
                    <button
                      type="button"
                      onClick={() => handleCopyChecksum(latestRelease.sha256!)}
                      className="shrink-0 inline-flex items-center gap-1.5 text-xs font-semibold bg-slate-100 hover:bg-slate-200 text-slate-800 px-3 py-2 rounded-lg transition-colors"
                    >
                      {copiedChecksum ? (
                        <>
                          <Check className="w-3.5 h-3.5 text-status-green" />
                          <span className="text-status-green">Copied!</span>
                        </>
                      ) : (
                        <>
                          <Copy className="w-3.5 h-3.5" />
                          <span>Copy Hash</span>
                        </>
                      )}
                    </button>
                  </div>
                </details>
              )}

              {latestRelease.releaseNotes && (
                <details className="group rounded-2xl border border-slate-200 bg-white shadow-sm overflow-hidden">
                  <summary className="flex cursor-pointer list-none items-center justify-between gap-4 px-5 py-4 text-sm font-bold text-fleet-navy hover:bg-slate-50/80 transition-colors [&::-webkit-details-marker]:hidden">
                    <span className="flex items-center gap-2.5">
                      <Layers className="w-4 h-4 text-college-primary" />
                      <span>Full Release Notes (v{latestRelease.version})</span>
                    </span>
                    <ChevronDown className="w-4 h-4 text-slate-500 transition-transform duration-200 group-open:rotate-180" />
                  </summary>
                  <div className="px-5 pb-4 pt-2 border-t border-slate-100">
                    <ReleaseNotesContent markdown={latestRelease.releaseNotes} />
                  </div>
                </details>
              )}

              <details className="group rounded-2xl border border-slate-200 bg-white shadow-sm overflow-hidden">
                <summary className="flex cursor-pointer list-none items-center justify-between gap-4 px-5 py-4 text-sm font-bold text-fleet-navy hover:bg-slate-50/80 transition-colors [&::-webkit-details-marker]:hidden">
                  <span className="flex items-center gap-2.5">
                    <HelpCircle className="w-4 h-4 text-college-primary" />
                    <span>Android APK Installation &amp; Update Guide</span>
                  </span>
                  <ChevronDown className="w-4 h-4 text-slate-500 transition-transform duration-200 group-open:rotate-180" />
                </summary>
                <div className="px-5 pb-5 pt-3 border-t border-slate-100 space-y-4">
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 text-sm text-slate-600">
                    <div className="p-4 bg-fleet-offwhite rounded-xl border border-slate-200/70">
                      <span className="font-bold text-fleet-navy block mb-1">1. Download</span>
                      <span>Tap <strong>Download APK</strong> to save the verified build to your Android Downloads folder.</span>
                    </div>
                    <div className="p-4 bg-fleet-offwhite rounded-xl border border-slate-200/70">
                      <span className="font-bold text-fleet-navy block mb-1">2. Allow Install</span>
                      <span>When prompted by Android, allow your browser to install apps from this source.</span>
                    </div>
                    <div className="p-4 bg-fleet-offwhite rounded-xl border border-slate-200/70">
                      <span className="font-bold text-fleet-navy block mb-1">3. Launch</span>
                      <span>Tap <strong>Install</strong>, open FleetX, and select your campus bus route.</span>
                    </div>
                  </div>
                  <p className="text-xs text-slate-500">
                    Updating from an older version? Download the latest release above and confirm the APK update prompt on your device.
                  </p>
                </div>
              </details>
            </div>
          </div>
        )}
      </div>
    </section>
  );
};

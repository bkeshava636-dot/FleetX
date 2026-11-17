import React, { useState } from 'react';
import {
  Download,
  Copy,
  Check,
  ShieldCheck,
  AlertCircle,
  HelpCircle,
  FileCode,
  Calendar,
  Layers,
  ChevronDown,
  ChevronUp
} from 'lucide-react';
import { ReleaseInfo } from '../types/github';

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
  errorMessage
}) => {
  const [copiedChecksum, setCopiedChecksum] = useState(false);
  const [showInstallGuide, setShowInstallGuide] = useState(false);
  const [showNotesExpanded, setShowNotesExpanded] = useState(false);

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
    <section id="download" className="py-20 bg-gradient-to-b from-college-surface to-white border-t border-slate-200/80">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Section Header */}
        <div className="text-center max-w-3xl mx-auto mb-14">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-college-primary-container text-college-primary-dark text-xs font-semibold uppercase tracking-wider mb-4">
            Official Android Portal
          </div>
          <h2 className="text-3xl sm:text-4xl font-extrabold text-college-on-surface tracking-tight">
            Get Started with FleetX
          </h2>
          <p className="mt-4 text-base sm:text-lg text-college-on-surface-variant font-normal leading-relaxed">
            Download the latest version of FleetX and stay connected with your college transportation.
          </p>
        </div>

        {/* Main Download Card */}
        <div className="max-w-4xl mx-auto">
          {isLoading ? (
            <div className="bg-white p-12 rounded-3xl border border-slate-200/90 shadow-card flex flex-col items-center justify-center text-center">
              <div className="w-12 h-12 border-4 border-college-primary border-t-transparent rounded-full animate-spin mb-4" />
              <p className="text-sm font-semibold text-college-on-surface">
                Checking GitHub Releases for latest APK...
              </p>
              <p className="text-xs text-slate-400 mt-1">Connecting to official repository</p>
            </div>
          ) : isError || !latestRelease || !latestRelease.apkAsset ? (
            <div className="bg-white p-10 rounded-3xl border border-amber-200 shadow-card text-center">
              <div className="w-12 h-12 mx-auto mb-4 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center">
                <AlertCircle className="w-6 h-6" />
              </div>
              <h3 className="text-lg font-bold text-college-on-surface mb-2">
                App download is temporarily unavailable. Please check back later.
              </h3>
              <p className="text-xs text-slate-500 max-w-md mx-auto mb-6">
                {errorMessage || 'Unable to retrieve the latest APK asset from GitHub releases at this moment.'}
              </p>
              <a
                href="https://github.com/bkeshava636-dot/FleetX/releases"
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-2 text-xs font-semibold text-college-primary hover:underline"
              >
                <span>View releases directly on GitHub</span>
                <span>↗</span>
              </a>
            </div>
          ) : (
            <div className="bg-white rounded-3xl border border-slate-200 shadow-lg overflow-hidden">
              {/* Card Banner */}
              <div className="bg-college-primary text-white p-6 sm:p-8 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
                <div className="flex flex-col">
                  <div className="flex items-center gap-2 mb-1.5">
                    <span className="text-xs font-semibold uppercase tracking-wider bg-white/20 px-2.5 py-0.5 rounded-full">
                      Latest Release
                    </span>
                    <span className="text-xs text-blue-100 flex items-center gap-1">
                      <Calendar className="w-3.5 h-3.5" />
                      {latestRelease.formattedDate}
                    </span>
                  </div>
                  <h3 className="text-2xl sm:text-3xl font-extrabold tracking-tight">
                    FleetX v{latestRelease.version}
                  </h3>
                </div>

                <div className="flex items-center gap-3">
                  <button
                    type="button"
                    onClick={handleDownload}
                    className="inline-flex items-center gap-2.5 bg-white text-college-primary hover:bg-slate-50 active:bg-blue-50 font-bold text-sm px-6 py-3.5 rounded-xl shadow-md transition-all duration-200 focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-white"
                  >
                    <Download className="w-5 h-5 text-college-primary" />
                    <span>Download APK</span>
                    <span className="text-xs bg-slate-100 text-college-primary px-2 py-0.5 rounded font-mono">
                      {latestRelease.apkAsset.formattedSize}
                    </span>
                  </button>
                </div>
              </div>

              {/* Release Details Body */}
              <div className="p-6 sm:p-8 space-y-6">
                {/* Meta Specs Grid */}
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 bg-college-surface p-4 rounded-2xl border border-slate-200/80 text-left">
                  <div>
                    <span className="text-[11px] font-medium text-slate-500 uppercase tracking-wider block">
                      Target OS
                    </span>
                    <span className="text-sm font-bold text-college-on-surface">
                      Android 8.0+
                    </span>
                  </div>
                  <div>
                    <span className="text-[11px] font-medium text-slate-500 uppercase tracking-wider block">
                      File Size
                    </span>
                    <span className="text-sm font-bold text-college-on-surface">
                      {latestRelease.apkAsset.formattedSize}
                    </span>
                  </div>
                  <div>
                    <span className="text-[11px] font-medium text-slate-500 uppercase tracking-wider block">
                      Asset Name
                    </span>
                    <span className="text-sm font-semibold text-college-on-surface font-mono truncate block" title={latestRelease.apkAsset.name}>
                      {latestRelease.apkAsset.name}
                    </span>
                  </div>
                  <div>
                    <span className="text-[11px] font-medium text-slate-500 uppercase tracking-wider block">
                      Distribution
                    </span>
                    <span className="text-sm font-bold text-status-green flex items-center gap-1">
                      <ShieldCheck className="w-4 h-4" />
                      Direct APK
                    </span>
                  </div>
                </div>

                {/* What's New Box */}
                <div className="bg-slate-50/70 p-5 rounded-2xl border border-slate-200/80">
                  <div className="flex items-center justify-between mb-2">
                    <h4 className="text-sm font-bold text-college-on-surface flex items-center gap-2">
                      <Layers className="w-4 h-4 text-college-primary" />
                      <span>What's New in v{latestRelease.version}</span>
                    </h4>
                    {latestRelease.releaseNotes && (
                      <button
                        type="button"
                        onClick={() => setShowNotesExpanded(!showNotesExpanded)}
                        className="text-xs text-college-primary hover:underline flex items-center gap-1 font-medium"
                      >
                        <span>{showNotesExpanded ? 'Show Less' : 'Full Release Notes'}</span>
                        {showNotesExpanded ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
                      </button>
                    )}
                  </div>
                  <p className="text-xs sm:text-sm text-college-on-surface-variant leading-relaxed">
                    {latestRelease.whatsNewSummary}
                  </p>

                  {/* Expanded Release Notes if clicked */}
                  {showNotesExpanded && latestRelease.releaseNotes && (
                    <div className="mt-4 pt-4 border-t border-slate-200">
                      <pre className="text-xs text-slate-600 bg-white p-4 rounded-xl border border-slate-200 font-mono whitespace-pre-wrap overflow-x-auto max-h-60 leading-normal">
                        {latestRelease.releaseNotes}
                      </pre>
                    </div>
                  )}
                </div>

                {/* SHA-256 Checksum Card */}
                {latestRelease.sha256 && (
                  <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 bg-white p-4 rounded-2xl border border-slate-200/90 shadow-sm text-left">
                    <div className="flex items-start gap-3 overflow-hidden">
                      <div className="p-2 bg-slate-100 rounded-lg text-slate-600 flex-shrink-0 mt-0.5">
                        <FileCode className="w-4 h-4" />
                      </div>
                      <div className="overflow-hidden">
                        <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider block">
                          SHA-256 Verified Checksum
                        </span>
                        <code className="text-xs font-mono text-slate-800 break-all select-all block">
                          {latestRelease.sha256}
                        </code>
                      </div>
                    </div>
                    <button
                      type="button"
                      onClick={() => handleCopyChecksum(latestRelease.sha256!)}
                      className="flex-shrink-0 inline-flex items-center gap-1.5 text-xs font-medium bg-slate-100 hover:bg-slate-200 text-slate-700 px-3 py-1.5 rounded-lg transition-colors"
                      title="Copy SHA-256 hash"
                    >
                      {copiedChecksum ? (
                        <>
                          <Check className="w-3.5 h-3.5 text-status-green" />
                          <span className="text-status-green font-semibold">Copied!</span>
                        </>
                      ) : (
                        <>
                          <Copy className="w-3.5 h-3.5" />
                          <span>Copy Hash</span>
                        </>
                      )}
                    </button>
                  </div>
                )}

                {/* Platform availability note */}
                <div className="text-center text-xs text-slate-500 pt-2">
                  <p className="font-medium text-slate-600">
                    FleetX is currently available for Android.
                  </p>
                </div>
              </div>
            </div>
          )}

          {/* Installation Instructions Helper */}
          <div className="mt-8 bg-white p-6 sm:p-8 rounded-3xl border border-slate-200 shadow-sm">
            {showInstallGuide && (
              <div className="mb-4 p-3.5 bg-emerald-50 border border-emerald-200 rounded-xl text-emerald-800 text-xs flex items-center justify-between animate-in fade-in">
                <span className="font-medium">✓ Download started! Follow the 3 steps below on your Android device to install.</span>
                <button
                  type="button"
                  onClick={() => setShowInstallGuide(false)}
                  className="text-emerald-700 hover:text-emerald-900 font-bold text-sm px-2"
                  aria-label="Dismiss message"
                >
                  ✕
                </button>
              </div>
            )}
            <h4 className="text-base font-bold text-college-on-surface mb-4 flex items-center gap-2">
              <HelpCircle className="w-5 h-5 text-college-primary" />
              <span>Android APK Installation Guide</span>
            </h4>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 text-xs text-college-on-surface-variant">
              <div className="p-3.5 bg-college-surface rounded-xl border border-slate-100">
                <span className="font-bold text-college-primary block mb-1">Step 1: Download</span>
                <span>Click <strong>Download APK</strong>. The file will save to your Android device's Downloads folder.</span>
              </div>
              <div className="p-3.5 bg-college-surface rounded-xl border border-slate-100">
                <span className="font-bold text-college-primary block mb-1">Step 2: Allow Install</span>
                <span>When prompted by Android, permit your browser to install applications from this source.</span>
              </div>
              <div className="p-3.5 bg-college-surface rounded-xl border border-slate-100">
                <span className="font-bold text-college-primary block mb-1">Step 3: Launch</span>
                <span>Tap <strong>Install</strong>, then open FleetX to choose your bus and begin real-time tracking!</span>
              </div>
            </div>
          </div>

          {/* Update Information Subsection */}
          <div className="mt-6 p-5 rounded-2xl bg-blue-50/70 border border-blue-200/70 text-xs sm:text-sm text-blue-950 flex items-start gap-3">
            <div className="p-1.5 bg-blue-100 rounded-lg text-college-primary flex-shrink-0 mt-0.5">
              <ShieldCheck className="w-4 h-4" />
            </div>
            <div>
              <p className="font-bold text-college-on-surface mb-0.5">
                Update Information
              </p>
              <p className="text-xs text-college-on-surface-variant leading-relaxed">
                Using an older version of FleetX? Download the latest release to access the newest improvements and fixes. Android requires you to download and confirm installation of APK updates manually.
              </p>
            </div>
          </div>
        </div>

      </div>
    </section>
  );
};

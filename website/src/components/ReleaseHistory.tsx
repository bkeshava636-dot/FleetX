import React, { useState } from 'react';
import { Download, History, ExternalLink, Calendar, ChevronDown, ChevronUp } from 'lucide-react';
import { ReleaseInfo } from '../types/github';

interface ReleaseHistoryProps {
  previousReleases: ReleaseInfo[];
  isLoading: boolean;
}

export const ReleaseHistory: React.FC<ReleaseHistoryProps> = ({
  previousReleases,
  isLoading
}) => {
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [showAll, setShowAll] = useState(false);

  const displayedReleases = showAll ? previousReleases : previousReleases.slice(0, 5);

  const toggleExpand = (id: number) => {
    setExpandedId(prev => (prev === id ? null : id));
  };

  if (isLoading && previousReleases.length === 0) {
    return null;
  }

  if (previousReleases.length === 0) {
    return null;
  }

  return (
    <section id="releases" className="py-20 bg-college-surface border-t border-slate-200/80">
      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8">
        
        {/* Section Header */}
        <div className="flex flex-col sm:flex-row items-start sm:items-end justify-between gap-4 mb-10">
          <div>
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-slate-200/80 text-slate-700 text-xs font-semibold uppercase tracking-wider mb-3">
              Version Archive
            </div>
            <h2 className="text-2xl sm:text-3xl font-extrabold text-college-on-surface tracking-tight">
              Previous Releases
            </h2>
            <p className="mt-1 text-sm text-college-on-surface-variant font-normal">
              Review release changelogs and archived stable builds.
            </p>
          </div>

          <a
            href="https://github.com/bkeshava636-dot/FleetX/releases"
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-1.5 text-xs font-semibold text-college-primary hover:text-college-primary-dark transition-colors"
          >
            <span>View All on GitHub</span>
            <ExternalLink className="w-3.5 h-3.5" />
          </a>
        </div>

        {/* Releases List */}
        <div className="space-y-4">
          {displayedReleases.map(release => {
            const isExpanded = expandedId === release.id;
            return (
              <div
                key={release.id}
                className="bg-white rounded-2xl border border-slate-200/90 shadow-sm overflow-hidden transition-all duration-200"
              >
                <div className="p-5 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
                  {/* Left: Version + Date */}
                  <div className="flex items-start sm:items-center gap-4">
                    <div className="w-10 h-10 rounded-xl bg-slate-100 flex items-center justify-center text-slate-700 flex-shrink-0">
                      <History className="w-5 h-5 text-slate-600" />
                    </div>
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="text-base font-bold text-college-on-surface">
                          v{release.version}
                        </span>
                        <span className="text-xs text-slate-400">•</span>
                        <span className="text-xs text-slate-500 flex items-center gap-1">
                          <Calendar className="w-3 h-3" />
                          {release.formattedDate}
                        </span>
                      </div>
                      <p className="text-xs text-college-on-surface-variant mt-0.5 line-clamp-1 max-w-lg">
                        {release.whatsNewSummary}
                      </p>
                    </div>
                  </div>

                  {/* Right: Actions */}
                  <div className="flex items-center gap-3 w-full sm:w-auto justify-end pt-2 sm:pt-0 border-t sm:border-t-0 border-slate-100">
                    <button
                      type="button"
                      onClick={() => toggleExpand(release.id)}
                      className="inline-flex items-center gap-1 text-xs text-slate-600 hover:text-college-primary px-3 py-1.5 rounded-lg hover:bg-slate-50 font-medium"
                    >
                      <span>{isExpanded ? 'Hide Notes' : 'Notes'}</span>
                      {isExpanded ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
                    </button>

                    {release.apkAsset ? (
                      <a
                        href={release.apkAsset.downloadUrl}
                        className="inline-flex items-center gap-1.5 bg-slate-100 hover:bg-college-primary hover:text-white text-slate-800 text-xs font-semibold px-3.5 py-1.5 rounded-lg transition-colors"
                      >
                        <Download className="w-3.5 h-3.5" />
                        <span>APK ({release.apkAsset.formattedSize})</span>
                      </a>
                    ) : (
                      <span className="text-xs text-slate-400 italic">No APK</span>
                    )}
                  </div>
                </div>

                {/* Collapsible Release Notes Body */}
                {isExpanded && release.releaseNotes && (
                  <div className="px-5 pb-5 pt-1 border-t border-slate-100 bg-slate-50/50">
                    <pre className="text-xs text-slate-700 bg-white p-4 rounded-xl border border-slate-200 font-mono whitespace-pre-wrap max-h-56 overflow-y-auto leading-relaxed">
                      {release.releaseNotes}
                    </pre>
                  </div>
                )}
              </div>
            );
          })}
        </div>

        {/* View All / Collapse Button if more than 5 */}
        {previousReleases.length > 5 && (
          <div className="text-center mt-6">
            <button
              type="button"
              onClick={() => setShowAll(!showAll)}
              className="inline-flex items-center gap-2 text-xs font-bold text-college-primary hover:text-college-primary-dark px-4 py-2 bg-white rounded-xl border border-slate-200 shadow-sm"
            >
              <span>{showAll ? 'Show Latest 5 Only' : `View All ${previousReleases.length} Previous Releases`}</span>
              {showAll ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
            </button>
          </div>
        )}

      </div>
    </section>
  );
};

import React from 'react';
import { ArrowRight } from 'lucide-react';
import { useScrollStagger } from '@/lib/useMotionReveal';
import { CoordinateTag, MapGridBackdrop } from './MobilityPrimitives';

interface FinalCtaProps {
  onExploreClick: () => void;
}

export const FinalCta: React.FC<FinalCtaProps> = ({ onExploreClick }) => {
  const ctaRef = useScrollStagger<HTMLDivElement>('[data-reveal]', 0);

  return (
    <section className="relative py-16 sm:py-24 bg-fleet-navy text-fleet-offwhite overflow-hidden border-t border-white/10">
      <MapGridBackdrop dark />

      <div ref={ctaRef} className="relative z-10 max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
        <div
          data-reveal
          className="rounded-3xl border border-white/10 bg-fleet-deep/90 px-6 py-12 sm:px-12 sm:py-16 shadow-2xl"
        >
          <CoordinateTag label="READY FOR DISPATCH" className="mb-4" />
          <h2 className="font-display text-3xl sm:text-5xl font-bold tracking-tight">
            Make College Transportation Smarter.
          </h2>
          <p className="mt-4 text-sm sm:text-base text-fleet-muted max-w-xl mx-auto leading-relaxed">
            Download the verified FleetX Android application or submit stop coordinates to keep your campus transit directory accurate.
          </p>
          <div className="mt-8 flex flex-col sm:flex-row items-center justify-center gap-3">
            <button
              type="button"
              onClick={onExploreClick}
              className="w-full sm:w-auto inline-flex items-center justify-center gap-2.5 rounded-xl bg-fleet-lime px-7 py-3.5 text-sm sm:text-base font-bold text-fleet-navy shadow-md hover:bg-fleet-lime/90 transition-all duration-150 active:scale-[0.98]"
            >
              <span>Explore FleetX</span>
              <ArrowRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>
    </section>
  );
};

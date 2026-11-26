import React from 'react';
import { ArrowRight } from 'lucide-react';
import { useScrollStagger } from '@/lib/useMotionReveal';
import { Button } from './ui/button';

interface FinalCtaProps {
  onExploreClick: () => void;
}

export const FinalCta: React.FC<FinalCtaProps> = ({ onExploreClick }) => {
  const ctaRef = useScrollStagger<HTMLDivElement>('[data-reveal]', 0);

  return (
    <section className="py-16 sm:py-20 bg-white border-t border-slate-200/80">
      <div ref={ctaRef} className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8">
        <div
          data-reveal
          className="rounded-3xl bg-slate-900 text-white px-6 py-12 sm:px-12 sm:py-14 text-center shadow-lg"
        >
          <h2 className="text-2xl sm:text-4xl font-extrabold tracking-tight">
            Make College Transportation Smarter.
          </h2>
          <p className="mt-3 text-sm sm:text-base text-slate-300 max-w-xl mx-auto leading-relaxed">
            Download the verified FleetX Android application or submit stop coordinates to keep your campus transit directory accurate.
          </p>
          <div className="mt-7 flex flex-col sm:flex-row items-center justify-center gap-3">
            <Button size="lg" onClick={onExploreClick} className="w-full sm:w-auto">
              <span>Explore FleetX</span>
              <ArrowRight className="w-4 h-4" />
            </Button>
          </div>
        </div>
      </div>
    </section>
  );
};
